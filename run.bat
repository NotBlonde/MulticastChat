@echo off
if not exist MulticastChat.java (
  echo Saknar MulticastChat.java i aktuell katalog.
  exit /b 1
)
javac MulticastChat.java
echo Startar (du kan ange parametrar i dialogerna)...
java -Djava.net.preferIPv4Stack=true MulticastChat
