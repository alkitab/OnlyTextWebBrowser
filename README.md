# TextBrowser — текстовый браузер на Java

Минималистичный графический веб‑браузер на **Java + Swing**. Отображает
только текстовое содержимое HTML‑страниц и кликабельные ссылки.
Поддерживает корректную отрисовку **русского** и **арабского** текста
(включая двунаправленный RTL‑вывод).

## Что вырезается

| Категория | Что удаляется |
|---|---|
| JavaScript | все теги `<script>`, `<noscript>`, атрибуты `onclick`, `onload` и т. п. |
| Изображения | `<img>`, `<picture>`, `<source>`, `<svg>`, `<canvas>`, `<map>`, `<area>` |
| Видео/аудио | `<video>`, `<audio>`, `<track>`, `<iframe>`, `<object>`, `<embed>`, `<applet>` |
| Стили | `<style>`, `<link>`, inline‑атрибут `style=` у всех элементов |
| Формы | `<form>`, `<input>`, `<button>`, `<select>`, `<textarea>` |
| Переадресация | HTTP‑редиректы (3xx) и `<meta http-equiv="refresh">` **не выполняются**; целевой URL показывается ссылкой для ручного перехода |
| Реклама/баннеры | элементы, в `class`/`id` которых есть слова‑маркеры: `ad`, `ads`, `advert`, `banner`, `promo`, `sponsor`, `cookie`, `popup`, `social`, `share`, `modal`, `sidebar`, `newsletter`, `subscribe`, `gdpr`, `consent`, `paywall` |
| Метаданные | `<base>`, `<template>`, `<slot>`, атрибуты `data-*`, `target="_blank"`, `rel=` |

## Что сохраняется

* Текст и структура: заголовки, абзацы, списки, таблицы, цитаты, `<pre>`/`<code>`.
* Гиперссылки `<a href>` — кликабельны, открываются в том же окне.
* Кодировка — автоопределение (UTF‑8, Windows‑1251 и т. д.) через jsoup.
* Направление текста — автоопределение RTL для арабского текста (`<body dir="rtl">`).

## История и управление

* Кнопки **← Назад** / **Вперёд →** / **Обновить** / **Домой**.
* `Ctrl+L` — фокус на адресную строку.
* Если в адресной строке введён текст с пробелами (или без точки) — он
  отправляется как поисковый запрос в DuckDuckGo Lite.

---

## Установка в Debian (12 Bookworm / 13 Trixie и новее)

### 1. Системные пакеты

```sh
sudo apt-get update
sudo apt-get install -y default-jdk libjsoup-java fonts-noto-core
```

| Пакет | Зачем |
|---|---|
| `default-jdk` | компилятор `javac` и среда `java` (OpenJDK 17 или новее) |
| `libjsoup-java` | библиотека jsoup (HTML‑парсер) — ставит JAR в `/usr/share/java/jsoup.jar` |
| `fonts-noto-core` | шрифты Noto Sans (кириллица + латиница) и Noto Sans Arabic |

Если `fonts-noto-core` мало — поставьте полный набор:

```sh
sudo apt-get install -y fonts-noto fonts-noto-cjk fonts-noto-ui-core
```

Если Noto недоступен, в коде есть fallback на `DejaVu Sans` (кириллица)
и `FreeSerif` (универсальный — есть и кириллица, и арабский). Пакет:

```sh
sudo apt-get install -y fonts-dejavu fonts-freefont-ttf
```

Проверить наличие арабского шрифта:

```sh
fc-list | grep -iE 'noto.*arab|dejavu.*arab'
fc-list :lang=ar | head
fc-list :lang=ru | head
```

### 2. Скачать исходники

Положите файлы `TextBrowser.java`, `build.sh`, `run.sh`, `MANIFEST.MF`
в одну папку, например `~/textbrowser/`:

```sh
mkdir -p ~/textbrowser
cd ~/textbrowser
# скопируйте сюда TextBrowser.java, build.sh, run.sh, MANIFEST.MF
```

### 3. Собрать

```sh
./build.sh
```

Скрипт найдёт `jsoup.jar` (рядом или в `/usr/share/java/`), откомпилирует
`TextBrowser.class` и соберёт `TextBrowser.jar`.

Альтернативная ручная сборка:

```sh
javac -cp /usr/share/java/jsoup.jar -encoding UTF-8 TextBrowser.java
jar cfm TextBrowser.jar MANIFEST.MF *.class
```

### 4. Запустить

```sh
./run.sh
# или
./run.sh https://news.ycombinator.com
# или вручную
java -cp .:/usr/share/java/jsoup.jar TextBrowser
java -cp .:/usr/share/java/jsoup.jar TextBrowser https://ria.ru/
```

### 5. (Опционально) Пункт меню и ярлык

Создайте `~/.local/share/applications/textbrowser.desktop`:

```ini
[Desktop Entry]
Type=Application
Name=TextBrowser
Comment=Текстовый веб-браузер
Exec=/home/ВЫ/textbrowser/run.sh %U
Icon=text-html
Terminal=false
Categories=Network;WebBrowser;
```

После этого браузер появится в меню приложений GNOME/KDE/XFCE.

---

## Установка в Arch Linux

В Arch пакеты называются иначе, чем в Debian.

### 1. Системные пакеты

```sh
sudo pacman -Syu --needed jdk-openjdk fontconfig
# шрифты с кириллицей и арабским:
sudo pacman -S --needed noto-fonts noto-fonts-cjk ttf-dejavu ttf-freefont
```

| Пакет Arch | Зачем |
|---|---|
| `jdk-openjdk` | `javac` + `java` (OpenJDK 21) |
| `noto-fonts` | Noto Sans (кириллица + латиница) и Noto Sans Arabic |
| `ttf-dejavu` | запасной шрифт с кириллицей |
| `ttf-freefont` | запасной шрифт с кириллицей и арабским (FreeSerif) |

### 2. jsoup

jsoup в официальном репозитории Arch **отсутствует**. Два варианта:

**Вариант A — скачать JAR напрямую (проще всего):**

```sh
cd ~/textbrowser
curl -L -o jsoup.jar https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar
```

**Вариант B — через AUR** (если у вас стоит `yay` / `pikaur`):

```sh
yay -S java-jsoup
# JAR окажется в /usr/share/java/jsoup.jar
```

Проверка:

```sh
fc-list :lang=ar | head        # должен показать Noto Sans Arabic
fc-list :lang=ru | head        # Noto Sans / DejaVu Sans
ls -la jsoup.jar               # или /usr/share/java/jsoup.jar
```

### 3. Собрать и запустить

```sh
./build.sh
./run.sh
./run.sh https://www.bbc.com/arabic
```

### Если `./run.sh` падает с `NoClassDefFoundError: TextBrowser$2`

Это значит, что JAR собран без внутренних классов (старый `build.sh`).
Обновите `build.sh` из этого дистрибутива и пересоберите:

```sh
rm -f *.class TextBrowser.jar
./build.sh
./run.sh
```

В новом `build.sh` используется `*.class` (glob), поэтому все
`TextBrowser$1.class`, `TextBrowser$2.class`, `TextBrowser$3.class`,
`TextBrowser$4.class` гарантированно попадают в JAR. Дополнительно
`run.sh` теперь предпочитает каталог с `.class`‑файлами — даже если
JAR битый, браузер запустится.

---

## Использование

1. Запустите `./run.sh` — откроется стартовая страница со ссылками.
2. Введите URL в адресной строке и нажмите `Enter` (или кнопку **Перейти**).
3. Кликайте по ссылкам в тексте — они работают.
4. Если сервер ответил переадресацией — увидите страницу со ссылкой
   «целевой адрес». Кликните по ней, чтобы перейти.
5. Если введён текст без `http://` и без точки — он уйдёт в поиск
   DuckDuckGo Lite.

### Где смотреть арабский и русский

* `https://www.aljazeera.net/` — арабский новостной сайт (RTL).
* `https://ria.ru/` — РИА Новости (кириллица).
* `https://lite.duckduckgo.com/lite/` — текстовый поиск.

---

## Технические детали

### Почему текст рисуется корректно

* Java Swing использует `java.awt.font.TextLayout` с алгоритмом
  Bidi (UAX #9). Он автоматически расставляет арабские глифы в правильном
  порядке, применяет лигатуры и зеркальное отображение.
* `JEditorPane` с `HTMLEditorKit` honour `dir="rtl"` на `<body>` —
  весь абзац выводится справа налево.
* Дополнительно в CSS задано `body[dir=rtl]{text-align:right;}` и
  `[dir=rtl]{text-align:right;}` — иначе `HTMLEditorKit` по умолчанию
  оставляет `text-align:left`, и арабский параграф «прилипает» к левому
  краю, хотя символы внутри идут RTL. Теперь весь блок выровнен по
  правому краю. При проверке всё было по левому краю у страницы с
  '<html dir="rtl">'
* Шрифт `Noto Sans` содержит глифы для латиницы, кириллицы и арабского.
  Java автоматически делает font‑fallback: если в основном шрифте нет
  глифа, берётся из следующего установленного.

### Сглаживание шрифтов

В `main()` и в `run.sh` заданы три системных свойства:

| Свойство | Значение | Что делает |
|---|---|---|
| `awt.useSystemAAFontSettings` | `on` | использует системные настройки AA из `fontconfig` (те же, что у GTK/Qt приложений) |
| `swing.aatext` | `true` | включает антиалиасинг для текста в Swing |
| `sun.java2d.xrender` | `true` | использует XRender pipeline на Linux — быстрее и плавнее, чем старый X11 |

Эти свойства нужно установить **до** загрузки любого AWT‑класса.
В `main()` они ставятся первой инструкцией через `System.setProperty`,
в `run.sh` — через `-D…` в командной строке `java`. На случай если
свойство уже задано пользователем, переопределения не происходит.

Если сглаживание всё равно не устраивает — добавьте в `~/.bashrc`:

```sh
export _JAVA_OPTIONS="-Dawt.useSystemAAFontSettings=on -Dswing.aatext=true"
```

или запустите вручную:

```sh
java -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true \
     -Dsun.java2d.xrender=true -cp .:jsoup.jar TextBrowser
```

### Почему нет JS

* Все теги `<script>` удаляются jsoup‑селектором до того, как HTML
  попадает в `JEditorPane`.
* `JEditorPane` сам по себе не выполняет JavaScript (в отличие от
  `JFXWebView`), поэтому даже оставшийся скрипт не запустится.
* Атрибуты `onclick`, `onload`, `onerror` и т. п. принудительно
  снимаются со всех элементов.

### Почему нет переадресаций

* `HttpURLConnection.setInstanceFollowRedirects(false)` — HTTP 3xx
  обрабатывается вручную, и вместо перехода показывается ссылка.
* `<meta http-equiv="refresh">` удаляется из DOM.

### Почему нет изображений/видео

* Теги `<img>`, `<picture>`, `<source>`, `<video>`, `<audio>`,
  `<iframe>`, `<object>`, `<embed>`, `<svg>`, `<canvas>` удаляются.
* В `JEditorPane` не загружаются внешние ресурсы (CSS‑картинки,
  фавиконки и т. п.) — у него нет для этого механизма.

---

## Структура проекта

```
textbrowser/
├── TextBrowser.java   # исходник (~420 строк, один класс)
├── build.sh           # сборка (javac + jar)
├── run.sh             # запуск
└── MANIFEST.MF        # манифест для JAR
```

Зависимости: `jsoup ≥ 1.15`, `Java ≥ 11` (рекомендуется 17+).

---

## Возможные проблемы

| Симптом | Решение |
|---|---|
| `Error: jsoup.jar не найден` | `sudo apt-get install -y libjsoup-java` |
| `javac: command not found` | `sudo apt-get install -y default-jdk` |
| Квадратики вместо арабского | `sudo apt-get install -y fonts-noto-core` и перезапуск |
| Квадратики вместо кириллицы | проверить `fc-list :lang=ru`; поставить `fonts-dejavu` или `fonts-liberation` |
| Сайт отдаёт 403/418 | UA‑строка уже Mozilla‑совместима; некоторые сайты всё равно блокируют — попробуйте другой URL |
| Пустая страница | сайт мог вернуть не HTML (PDF, JSON) — посмотрите статус в нижней строке |
| SSL‑ошибки на старых сайтах | обновите `ca-certificates`: `sudo apt-get install -y ca-certificates` |

---

## Лицензия

Исходник распространяется свободно. jsoup — лицензия MIT.
