#!/usr/bin/env bash
set -e
if [ ! -f MulticastChat.java ]; then
  echo "Saknar MulticastChat.java i aktuell katalog."
  exit 1
fi
javac MulticastChat.java
echo "Startar (du kan ange parametrar eller trycka OK för default i dialogerna)..."
java -Djava.net.preferIPv4Stack=true MulticastChat
