# MulticastChat — G-version (UDP + Multicast)

Detta paket innehåller en färdig Java-klient som uppfyller G-uppgiften:
- Swing-GUI
- UDP + Multicast (obegränsat antal användare)
- Meddelanden med användarnamn
- Lista över anslutna medlemmar (JOIN/PRESENT/LEAVE)
- “Koppla ner”-knapp

## Krav
- Java 8+ (javac, java i PATH)
- Alla klienter **måste** använda samma Multicast-IP och port.

## Kompilera
```bash
javac MulticastChat.java
```

## Köra
```bash
# Rekommenderat med IPv4-stack
java -Djava.net.preferIPv4Stack=true MulticastChat <användarnamn> [multicastIP] [port] [ttl]
```

Exempel (kör flera instanser/datorer med samma IP/port):
```bash
java -Djava.net.preferIPv4Stack=true MulticastChat Alex 230.0.0.1 4446 1
```

## Snabbstart-skript
- Windows: dubbelklicka `run.bat` och följ dialogerna.
- macOS/Linux: ge exekveringsrättigheter `chmod +x run.sh` och kör `./run.sh`.

## Vanliga tips
- Om skolnätet blockerar multicast: testa via mobil hotspot.
- TTL=1 räcker ofta i lokalt nät.
- Vill du ändra UI/beteende? Öppna koden och kör igen.
