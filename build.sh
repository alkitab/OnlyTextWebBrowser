#!/bin/sh
# build.sh — сборка TextBrowser
# Использование:  ./build.sh
# Нужны: JDK (javac) и jsoup.jar (рядом или в системе).
set -e
cd "$(dirname "$0")"

# Поиск jsoup.jar: рядом, потом в типичных системных путях (Debian/Arch/Fedora)
JSOUP=""
for c in \
    ./jsoup.jar \
    /usr/share/java/jsoup.jar \
    /usr/share/java/jsoup-1.17.2.jar \
    /usr/share/java/jsoup/jsoup.jar \
    /usr/lib/jsoup.jar
do
    if [ -f "$c" ]; then JSOUP="$c"; break; fi
done
if [ -z "$JSOUP" ]; then
    echo "ERROR: jsoup.jar не найден."
    echo "Debian/Ubuntu: sudo apt-get install -y libjsoup-java"
    echo "Arch:          sudo pacman -S --needed jdk-openjdk && pikaur -S java-jsoup  (или см. ниже)"
    echo "               либо: curl -L -o jsoup.jar https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar"
    echo "Fedora:        sudo dnf install -y jsoup"
    exit 1
fi

echo "Использую jsoup: $JSOUP"
# -Xlint:none убирает шум про deprecated; ключи совместимы с Java 11+
javac -cp "$JSOUP" -encoding UTF-8 TextBrowser.java
echo "Сборка OK -> TextBrowser*.class"

# Сборка исполняемого JAR.
# ВАЖНО: использовать *.class (glob), иначе анонимные внутренние классы
# TextBrowser$1, $2, $3, $4 не попадут в JAR — будет NoClassDefFoundError
# при клике на ссылку или кнопку.
rm -f TextBrowser.jar
jar cfm TextBrowser.jar MANIFEST.MF *.class
echo "JAR:           -> TextBrowser.jar"

# Проверка: вывести содержимое JAR
echo "--- содержимое JAR ---"
jar tf TextBrowser.jar | grep -E '\.class$' | sort
echo "----------------------"
