#!/bin/sh
# run.sh — запуск TextBrowser
# Использование:  ./run.sh [URL]
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
    echo "ERROR: jsoup.jar не найден. Запустите ./build.sh сначала."
    exit 1
fi

# Classpath: предпочитаем каталог с .class-файлами (там все внутренние классы
# TextBrowser$1..$4 точно присутствуют), JAR — только как fallback.
CP=""
if [ -f TextBrowser.class ]; then
    CP=".:$JSOUP"
elif [ -f TextBrowser.jar ]; then
    CP="TextBrowser.jar:$JSOUP"
else
    echo "ERROR: ни TextBrowser.class, ни TextBrowser.jar не найдены."
    echo "Запустите ./build.sh сначала."
    exit 1
fi

# Флаги сглаживания шрифтов. Дублируют то, что main() ставит через
# System.setProperty, но флаги командной строки надёжнее — они
# срабатывают ДО загрузки любого AWT-класса.
#
# awt.useSystemAAFontSettings=on — системное антиалиасинг-правило из fontconfig
# swing.aatext=true              — AA для текста в Swing
# sun.java2d.xrender=true        — использовать XRender pipeline на Linux (быстрее, плавнее)
JAVA_OPTS="-Dawt.useSystemAAFontSettings=on -Dswing.aatext=true -Dsun.java2d.xrender=true"

exec java $JAVA_OPTS -cp "$CP" TextBrowser "$@"
