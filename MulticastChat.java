/*
 * MulticastChat.java — Nackademin IoT – Nätverksprogrammering (G‑uppgiften)
 *
 * En komplett gruppchatt-klient i Java som använder UDP + Multicast.
 * Uppfyller kraven:
 *  - Grafiskt gränssnitt (Swing)
 *  - UDP + Multicast för kommunikation (obegränsat antal användare)
 *  - Meddelanden med användarnamn visas hos alla
 *  - "Koppla ner"-knapp som stänger programmet och skickar LEAVE
 *  - Lista med anslutna medlemmar som uppdateras när folk går med/lämnar
 *  - Kan köras på separata datorer (ange samma multicast-IP och port)
 *
 * Körning (terminal):
 *   javac MulticastChat.java
 *   java -Djava.net.preferIPv4Stack=true MulticastChat <användarnamn> [multicastIP] [port] [ttl]
 *
 * Exempel:
 *   java -Djava.net.preferIPv4Stack=true MulticastChat Alex 230.0.0.1 4446 1
 *
 * Tips:
 *  - Om skolnätet blockerar multicast: dela internet via mobil och anslut datorerna där.
 *  - Alla klienter måste använda SAMMA multicast-IP och port för att se varandra.
 */

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MulticastChat extends JFrame {
    // --- Protokoll ---
    private static final String MSG = "MSG";       // MSG|<user>|<text>
    private static final String JOIN = "JOIN";     // JOIN|<user>
    private static final String LEAVE = "LEAVE";   // LEAVE|<user>
    private static final String PRESENT = "PRESENT"; // PRESENT|<user>

    // --- Standardvärden ---
    private static final String DEFAULT_GROUP = "230.0.0.1"; // IPv4 multicast-adress
    private static final int DEFAULT_PORT = 4446;
    private static final int DEFAULT_TTL = 1; // räcker inom samma nät

    // --- Nätverk ---
    private final String username;
    private final InetAddress group;
       private final int port;
    private final int ttl;
    private MulticastSocket socket;
    private NetworkInterface boundInterface; // valda nätverkskortet (om hittat)
    private volatile boolean running = false;
    private Thread receiverThread;

    // --- UI ---
    private final JTextArea chatArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final DefaultListModel<String> userListModel = new DefaultListModel<>();
    private final JList<String> userList = new JList<>(userListModel);
    private final JButton disconnectBtn = new JButton("Koppla ner");
    private final JLabel statusLabel = new JLabel();

    // --- Tillstånd ---
    private final Set<String> online = Collections.synchronizedSet(new LinkedHashSet<>());
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    public MulticastChat(String username, String groupIp, int port, int ttl) throws IOException {
        super("Multicast Gruppchatt — " + username);
        this.username = Objects.requireNonNull(username);
        this.group = InetAddress.getByName(groupIp);
        this.port = port;
        this.ttl = ttl;

        buildUi();
        initNetworking();
        startReceiver();

        // Lägg till oss själva i listan lokalt och annonsera JOIN
        addUser(username);
        systemLine("Du är ansluten som \"" + username + "\"");
        sendRaw(JOIN + "|" + username);
    }

    // --- UI-bygge ---
    private void buildUi() {
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { disconnectAndExit(); }
        });

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);

        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(180, 0));

        inputField.addActionListener(e -> onSend());
        disconnectBtn.addActionListener(e -> disconnectAndExit());

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.add(inputField, BorderLayout.CENTER);
        bottom.add(disconnectBtn, BorderLayout.EAST);
        bottom.setBorder(new EmptyBorder(8, 8, 8, 8));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScroll, userScroll);
        split.setResizeWeight(0.8);

        JPanel root = new JPanel(new BorderLayout());
        root.add(split, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        root.add(statusLabel, BorderLayout.NORTH);
        statusLabel.setBorder(new EmptyBorder(6, 8, 6, 8));

        setContentPane(root);
        setSize(800, 500);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // --- Nätverksinit ---
    private void initNetworking() throws IOException {
        socket = new MulticastSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
        socket.setTimeToLive(ttl);

        // Försök välja ett icke-loopback, multicast-kapabelt interface
        boundInterface = pickNetworkInterface();
        try {
            if (boundInterface != null) {
                socket.setNetworkInterface(boundInterface);
                socket.joinGroup(new InetSocketAddress(group, port), boundInterface);
                setStatus("Ansluten till " + group.getHostAddress() + ":" + port +
                        " via " + boundInterface.getDisplayName() + " (TTL=" + ttl + ")");
            } else {
                socket.joinGroup(group); // fallback (äldre API)
                setStatus("Ansluten till " + group.getHostAddress() + ":" + port + " (default interface, TTL=" + ttl + ")");
            }
        } catch (IOException ex) {
            // Sista utväg: prova legacy-join
            socket.joinGroup(group);
            setStatus("Ansluten (legacy join) till " + group.getHostAddress() + ":" + port + " (TTL=" + ttl + ")");
        }
        running = true;
    }

    private NetworkInterface pickNetworkInterface() throws SocketException {
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        while (nics.hasMoreElements()) {
            NetworkInterface ni = nics.nextElement();
            try {
                if (ni.isUp() && ni.supportsMulticast() && !ni.isLoopback() && !ni.isVirtual()) {
                    return ni;
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    // --- Mottagartråd ---
    private void startReceiver() {
        receiverThread = new Thread(() -> {
            byte[] buf = new byte[8192];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String s = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                    handleIncoming(s);
                } catch (SocketException se) {
                    // förväntat vid stängning
                    break;
                } catch (IOException e) {
                    systemLine("[Nätverksfel] " + e.getMessage());
                }
            }
        }, "receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    private void handleIncoming(String s) {
        if (s.isEmpty()) return;
        String[] parts = s.split("\\|", 3);
        String type = parts[0];
        if (type.equals(MSG)) {
            if (parts.length >= 3) {
                String from = parts[1];
                String text = parts[2];
                // Visa inte dublett av vårt eget meddelande (vi visar lokalt vid sändning)
                if (!from.equals(username)) {
                    chatLine(from + ": " + text);
                    addUser(from); // säkerställ att avsändaren finns i listan
                }
            }
        } else if (type.equals(JOIN)) {
            if (parts.length >= 2) {
                String who = parts[1];
                if (!who.equals(username)) {
                    if (addUser(who)) {
                        systemLine(who + " anslöt till chatten.");
                    }
                    // Hjälp den nya klienten att få en lista på alla som redan är här
                    sendRaw(PRESENT + "|" + username);
                }
            }
        } else if (type.equals(PRESENT)) {
            if (parts.length >= 2) {
                String who = parts[1];
                addUser(who);
            }
        } else if (type.equals(LEAVE)) {
            if (parts.length >= 2) {
                String who = parts[1];
                if (!who.equals(username)) {
                    if (removeUser(who)) {
                        systemLine(who + " lämnade chatten.");
                    }
                }
            }
        }
    }

    // --- Sändning ---
    private void onSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.setText("");
        chatLine("Du: " + text); // lokal echo direkt
        sendRaw(MSG + "|" + username + "|" + text);
    }

    private synchronized void sendRaw(String payload) {
        try {
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            DatagramPacket p = new DatagramPacket(data, data.length, group, port);
            socket.send(p);
        } catch (IOException e) {
            systemLine("[Kunde inte skicka] " + e.getMessage());
        }
    }

    // --- UI-hjälpare ---
    private void chatLine(String line) {
        String stamp = LocalTime.now().format(timeFmt);
        SwingUtilities.invokeLater(() -> {
            chatArea.append("[" + stamp + "] " + line + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private void systemLine(String line) {
        chatLine("* " + line);
    }

    private void setStatus(String s) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(s));
    }

    private boolean addUser(String name) {
        synchronized (online) {
            if (online.add(name)) {
                SwingUtilities.invokeLater(() -> userListModel.addElement(name));
                return true;
            }
        }
        return false;
    }

    private boolean removeUser(String name) {
        synchronized (online) {
            if (online.remove(name)) {
                SwingUtilities.invokeLater(() -> userListModel.removeElement(name));
                return true;
            }
        }
        return false;
    }

    // --- Stängning ---
    private void disconnectAndExit() {
        // Skicka LEAVE och stäng ner allt
        try { sendRaw(LEAVE + "|" + username); } catch (Exception ignored) {}
        running = false;
        try { if (receiverThread != null) receiverThread.interrupt(); } catch (Exception ignored) {}
        try {
            if (socket != null) {
                try {
                    if (boundInterface != null) {
                        socket.leaveGroup(new InetSocketAddress(group, port), boundInterface);
                    } else {
                        socket.leaveGroup(group);
                    }
                } catch (IOException ignored) {}
                socket.close();
            }
        } finally {
            dispose();
            System.exit(0);
        }
    }

    // --- main ---
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                String userArg = args.length >= 1 ? args[0] : prompt("Användarnamn:");
                if (userArg == null || userArg.trim().isEmpty()) return;
                String groupArg = args.length >= 2 ? args[1] : promptDefault("Multicast IP:", DEFAULT_GROUP);
                String portArg = args.length >= 3 ? args[2] : promptDefault("Port:", String.valueOf(DEFAULT_PORT));
                String ttlArg = args.length >= 4 ? args[3] : promptDefault("TTL (1=lokalt nät):", String.valueOf(DEFAULT_TTL));

                String user = userArg.trim();
                String groupIp = groupArg.trim();
                int port = Integer.parseInt(portArg.trim());
                int ttl = Integer.parseInt(ttlArg.trim());

                new MulticastChat(user, groupIp, port, ttl);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Kunde inte starta: " + e.getMessage(),
                        "Fel", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        });
    }

    private static String prompt(String title) {
        return JOptionPane.showInputDialog(null, title, "Multicast Gruppchatt", JOptionPane.QUESTION_MESSAGE);
    }

    private static String promptDefault(String title, String def) {
        JTextField tf = new JTextField(def);
        Object[] msg = { title, tf };
        int res = JOptionPane.showConfirmDialog(null, msg, "Multicast Gruppchatt", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) return tf.getText();
        return null;
    }
}
