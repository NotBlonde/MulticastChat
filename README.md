# MulticastChat (G‑uppgiften) – UDP + Multicast

Detta är en **gruppchatt-klient i Java** som uppfyller alla krav för **G‑uppgiften** i kursen *Nätverksprogrammering* (Nackademin). Programmet använder **UDP** och **Multicast** för att låta ett obegränsat antal användare chatta på samma nät.

---

## 🚀 Snabbstart

**Krav:** Java 8+ (eller senare). Alla klienter måste använda **samma** multicast‑IP och port.

```bash
# 1) Kompilera
javac MulticastChat.java

# 2) Kör (IPv4-rekommenderas för multicast)
java -Djava.net.preferIPv4Stack=true MulticastChat <användarnamn> [multicastIP] [port] [ttl]

# Exempel
java -Djava.net.preferIPv4Stack=true MulticastChat Alex 230.0.0.1 4446 1
```

Du kan köra flera instanser på samma dator (med olika användarnamn) eller på flera datorer i samma nät.

---

## 🧭 Så här funkar programmet (översikt)

* **GUI (Swing):** Ett stort chattfönster till vänster, en **medlemslista** till höger och ett **textfält** + **“Koppla ner”** längst ner.
* **Multicast:** Alla klienter går med i samma **multicast‑grupp** (`230.0.0.1:4446` som default) och skickar/lyssnar på UDP‑datagram.
* **Protokoll (enkelt och textbaserat):**

  * `JOIN|<user>` – skickas när klienten startar.
  * `PRESENT|<user>` – svar från anslutna klienter för att hjälpa nya att bygga medlemslistan.
  * `MSG|<user>|<text>` – chattmeddelande.
  * `LEAVE|<user>` – skickas när klienten stänger med **Koppla ner**.
* **Trådning:** En **mottagartråd** lyssnar på nätverket. GUI‑uppdateringar sker på Swing‑tråden (EDT) via `SwingUtilities.invokeLater(...)`.

---

## 🧩 Arkitektur i korthet

```text
+---------------------------+           UDP Multicast (230.0.0.1:4446)
|  Swing GUI (EDT)          |  <---------------------------------------->
|  - ChatArea               |           +---------------------------+
|  - UserList               | <-------> |  MulticastSocket          |
|  - InputField/Disconnect  |           |  - receiverThread         |
+---------------------------+           +---------------------------+
```

* **Sender:** När du trycker Enter:

  1. Meddelandet visas lokalt direkt ("Du: ...").
  2. UDP‑paket med `MSG|user|text` skickas till gruppen.
* **Receiver:** Tar emot alla paket i gruppen:

  * `MSG` → visas i chatt och avsändare läggs till i medlemslistan (om saknas).
  * `JOIN` → lägg till användaren i listan och besvara med vårt `PRESENT|mittNamn`.
  * `PRESENT` → säkerställer att de som redan är med syns i listan hos den nya.
  * `LEAVE` → ta bort den användaren från listan och logga händelsen.

---

## 📜 Hur kraven uppfylls

* **Grafiskt gränssnitt:** Byggt i **Swing** (`JFrame`, `JTextArea`, `JList`, `JButton`, `JTextField`).
* **UDP + Multicast:** Använder `MulticastSocket`, `joinGroup(...)`, `DatagramPacket`.
* **Obegränsat antal användare:** Alla som går med i samma multicast‑grupp kan chatta.
* **Meddelanden med namn:** `MSG|<user>|<text>` → visas hos alla med avsändarens namn.
* **Koppla ner:** Knappen skickar `LEAVE|<user>` och stänger programmet kontrollerat.
* **Medlemslista:** Underhålls i ett `Set<String>` + `DefaultListModel` för JList. Uppdateras vid JOIN/LEAVE/PRESENT.
* **Köra på separata datorer:** Ja – så länge de når varandra nätverksmässigt och använder samma multicast‑IP/port.

---

## ⚙️ Parametrar & default

* **Multicast IP:** `230.0.0.1` (kan ändras vid start)
* **Port:** `4446` (kan ändras vid start)
* **TTL:** `1` (räcker inom lokalt nät). Kan höjas om du förstår konsekvenserna i större nät.
* **IPv4:** Flaggan `-Djava.net.preferIPv4Stack=true` minskar IPv6‑strul med multicast på vissa plattformar.

---

## 🧪 Demo-upplägg att visa läraren

1. Starta **tre instanser** lokalt med olika användarnamn (eller två datorer + en lokal instans).
2. Skriv ett meddelande i en instans – visa hur det **dyker upp hos alla**.
3. Starta en **ny** instans → visa att den **omedelbart** får listan via `PRESENT` och syns i **medlemslistan** hos övriga.
4. Tryck **Koppla ner** i en instans → visa att `LEAVE` plockar bort den användaren ur listan hos alla andra.
5. Peka ut i koden **var** JOIN/PRESENT/MSG/LEAVE hanteras och hur GUI\:t uppdateras på EDT.

---

## 🔍 Kodens huvuddelar

* **`main(...)`** – startar GUI\:t, läser argument (användarnamn, IP, port, TTL) eller visar dialoger.
* **`initNetworking()`** – skapar `MulticastSocket`, väljer multicast‑kapabelt nätverkskort om möjligt och `joinGroup(...)`.
* **`startReceiver()`** – startar mottagartråden (blockerande `socket.receive(...)`).
* **`handleIncoming(String s)`** – parserar protokollet och uppdaterar UI/tillstånd.
* **`onSend()`** – skickar
  `MSG|user|text` och visar lokal echo.
* **`disconnectAndExit()`** – skickar `LEAVE`, lämnar gruppen och stänger socket/GUI.

---

## 🧯 Felsökning & vanliga hinder

* **Inget syns mellan datorer:**

  * Kontrollera att båda använder **samma** IP/port.
  * Vissa campusnät **blockerar multicast**. Testa via mobil hotspot.
  * Brandväggar kan blockera UDP. Tillåt Java/porten temporärt.
* **Endast ena vägen:** TTL för lågt/högt, fel nätverksinterface, eller blandning av IPv4/IPv6.
* **Stänger via fönsterkrysset:** Uppgiften kräver inte hantering av det, men programmet fångar det och stänger kontrollerat via samma metod som knappen.

---

## 🧱 Avgränsningar (enligt uppgift)

* Ingen hantering av troll/konstiga beteenden eller dublettnamn.
* Trådning sköts delvis implicit (Swing EDT) + en explicit mottagartråd.
* Ingen **garanti** att listan uppdateras om man dödar processen hårt; det är därför knappen **Koppla ner** ska användas.

---

## 📦 Förslag på repo‑struktur

```text
/ (rot)
├─ MulticastChat.java
├─ README.md
├─ run.sh
└─ run.bat
```

---

## 🔐 Kort om säkerhet (utbildningssyfte)

* Ingen kryptering eller autentisering – allt går i klartext i LAN\:et.
* För verklig produktion bör man använda säkrare transport (t.ex. TLS över TCP) och serverbaserad arkitektur.

---

## ✅ Varför detta når G

* **GUI:** Ja (Swing).
* **UDP + Multicast:** Ja (`MulticastSocket`).
* **Obegränsat antal användare:** Ja (alla i gruppen får samma paket).
* **Meddelanden med namn:** Ja (`MSG|user|text`).
* **Koppla ner:** Ja (`LEAVE` + stängning).
* **Medlemslista:** Ja (JOIN/PRESENT/LEAVE).
* **Körning på separata datorer:** Ja, om nätet tillåter multicast.

---

## 📘 Litet manus till redovisningen

1. **Syfte:** Visa multicast‑baserad chatt där alla klienter är likvärdiga.
2. **Teknikval:** Java + Swing, UDP/Multicast för distribution till alla.
3. **Protokoll:** JOIN/PRESENT/MSG/LEAVE (enkelt och läsbart).
4. **Trådar:** En mottagartråd + GUI på EDT; inga blockeringar i GUI.
5. **Demo:** 3 klienter, meddelanden och realtidsuppdaterad medlemslista.
6. **Avslut:** Koppla ner → LEAVE → listan uppdateras hos övriga.

Lycka till med redovisningen! 🎉
