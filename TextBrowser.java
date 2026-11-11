import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.html.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.*;

import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

/**
 * TextBrowser — текстовый веб-браузер на Java/Swing.
 *
 * Свойства:
 *   • отображает только текст и ссылки HTML;
 *   • нет JavaScript, нет изображений, нет видео/аудио, нет iframe/object/embed;
 *   • нет баннеров и рекламы (фильтрация по class/id и типичным тегам);
 *   • нет автоматической переадресации (HTTP 3xx и meta refresh подавляются,
 *     целевой URL показывается ссылкой для ручного перехода);
 *   • корректная отрисовка кириллицы и арабского (bidi, RTL, Noto Sans);
 *   • ссылки кликабельны, история назад/вперёд.
 *
 * Зависимости: jsoup (org.jsoup:jsoup).
 * Сборка:  javac -cp jsoup.jar TextBrowser.java
 * Запуск:  java  -cp .:jsoup.jar TextBrowser [URL]
 */
public class TextBrowser extends JFrame {

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; TextBrowser/1.0; +text-only; no-js)";

    private final JEditorPane editorPane = new JEditorPane();
    private final JTextField urlField = new JTextField();
    private final JButton backButton  = new JButton("← Назад");
    private final JButton fwdButton   = new JButton("Вперёд →");
    private final JButton goButton    = new JButton("Перейти");
    private final JButton homeButton  = new JButton("Домой");
    private final JButton reloadButton = new JButton("Обновить");
    private final JLabel statusLabel  = new JLabel("Готов");

    private final Deque<String> backStack = new ArrayDeque<>();
    private final Deque<String> fwdStack  = new ArrayDeque<>();
    private String currentUrl = null;

    public TextBrowser() {
        super("TextBrowser — текстовый браузер");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 750);
        setLocationRelativeTo(null);
        buildUi();
        showHome();
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        buttons.add(backButton);
        buttons.add(fwdButton);
        buttons.add(reloadButton);
        buttons.add(homeButton);
        buttons.add(goButton);

        urlField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        top.add(buttons, BorderLayout.WEST);
        top.add(urlField, BorderLayout.CENTER);

        editorPane.setEditable(false);
        editorPane.setContentType("text/html; charset=UTF-8");

        // Шрифт по умолчанию — Noto Sans (есть кириллица и арабский),
        // иначе системный sans-serif. Java делает font fallback по глифам.
        Font base = pickFont();
        editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        editorPane.setFont(base);

        editorPane.addHyperlinkListener(this::onHyperlink);

        JScrollPane sp = new JScrollPane(editorPane);
        sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));

        add(top, BorderLayout.NORTH);
        add(sp, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        ActionListener go = e -> loadUrl(urlField.getText());
        goButton.addActionListener(go);
        urlField.addActionListener(go);
        backButton.addActionListener(e -> goBack());
        fwdButton.addActionListener(e -> goForward());
        homeButton.addActionListener(e -> loadUrl("about:home"));
        reloadButton.addActionListener(e -> {
            if (currentUrl != null) reload();
        });

        // Ctrl+L — фокус на адресную строку
        KeyStroke ctrlL = KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlL, "focusUrl");
        getRootPane().getActionMap().put("focusUrl", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { urlField.requestFocusInWindow();
                urlField.selectAll(); }
        });
    }

    private Font pickFont() {
        // Noto Sans covers Latin+Cyrillic+Arabic.
        // DejaVu Sans — Latin+Cyrillic (нет арабского → fallback на FreeSerif).
        // FreeSerif — Latin+Cyrillic+Arabic (универсальный запасной).
        String[] candidates = {
            "Noto Sans", "Noto Sans Arabic",
            "DejaVu Sans", "Liberation Sans",
            "FreeSerif", "SansSerif"
        };
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (String n : candidates) {
            Font f = new Font(n, Font.PLAIN, 16);
            // Только если семейство действительно найдено в системе
            boolean found = false;
            for (String fn : ge.getAvailableFontFamilyNames()) {
                if (fn.equals(n)) { found = true; break; }
            }
            if (found) return f;
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 16);
    }

    private void onHyperlink(HyperlinkEvent e) {
        HyperlinkEvent.EventType t = e.getEventType();
        if (t == HyperlinkEvent.EventType.ACTIVATED) {
            URL u = e.getURL();
            loadUrl(u != null ? u.toString() : e.getDescription());
        } else if (t == HyperlinkEvent.EventType.ENTERED) {
            URL u = e.getURL();
            statusLabel.setText(u != null ? u.toString()
                                          : (e.getDescription() != null ? e.getDescription() : ""));
        } else if (t == HyperlinkEvent.EventType.EXITED) {
            statusLabel.setText(currentUrl != null ? currentUrl : "Готов");
        }
    }

    // -------------------------------------------------------------- HOME

    private void showHome() {
        String home =
            "<html><head><style>"
          + "body{font-family:'Noto Sans','DejaVu Sans','FreeSerif',sans-serif;padding:18px;line-height:1.5;}"
          + "h1{color:#336;margin:0 0 4px 0;} .v{color:#888;margin:0 0 16px 0;}"
          + "a{color:#06c;} h3{margin-top:18px;color:#333;}"
          + "ul{margin-top:6px;}"
          + "</style></head><body dir=\"ltr\">"
          + "<h1>TextBrowser</h1>"
          + "<p class=\"v\">Текстовый браузер без JS, изображений, видео, баннеров и рекламы.</p>"
          + "<p>Введите адрес в строке выше или выберите ссылку:</p>"
          + "<h3>Текстовые поисковики и новости</h3>"
          + "<ul>"
          + "<li><a href=\"https://lite.duckduckgo.com/lite/\">DuckDuckGo Lite</a></li>"
          + "<li><a href=\"https://text.npr.org/\">NPR text-only</a></li>"
          + "<li><a href=\"https://news.ycombinator.com/\">Hacker News</a></li>"
          + "<li><a href=\"https://lobste.rs/\">Lobsters</a></li>"
          + "<li><a href=\"https://www.bbc.com/news\">BBC News</a></li>"
          + "<li><a href=\"https://www.aljazeera.net/\">Al Jazeera (العربية)</a></li>"
          + "<li><a href=\"https://ria.ru/\">РИА Новости (русский)</a></li>"
          + "</ul>"
          + "<h3>Подсказки</h3>"
          + "<ul>"
          + "<li>Ctrl+L — фокус на адресную строку</li>"
          + "<li>Клик по ссылке — переход</li>"
          + "<li>Переадресации подавляются, целевой URL показывается ссылкой</li>"
          + "</ul>"
          + "</body></html>";
        editorPane.setText(home);
        editorPane.setCaretPosition(0);
    }

    // ------------------------------------------------------------- NAV

    public void loadUrl(String url) {
        if (url == null) return;
        url = url.trim();
        if (url.isEmpty()) return;

        if ("about:home".equalsIgnoreCase(url) || "about:blank".equalsIgnoreCase(url)) {
            pushHistory(url);
            currentUrl = url;
            urlField.setText(url);
            showHome();
            statusLabel.setText("Домашняя страница");
            return;
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // не похоже на URL? возможно, поисковый запрос — отправим в DDG Lite
            if (url.contains(" ") || !url.contains(".")) {
                try {
                    url = "https://lite.duckduckgo.com/lite/?q=" +
                          URLEncoder.encode(url, "UTF-8");
                } catch (UnsupportedEncodingException ignored) {}
            } else {
                url = "https://" + url;
            }
        }

        urlField.setText(url);
        statusLabel.setText("Загрузка: " + url);
        final String target = url;
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return fetchAndClean(target);
            }
            @Override protected void done() {
                try {
                    String html = get();
                    editorPane.setText(html);
                    editorPane.setCaretPosition(0);
                    pushHistory(target);
                    currentUrl = target;
                    fwdStack.clear();
                    statusLabel.setText(currentUrl);
                } catch (Exception ex) {
                    String err = "<html><body dir=\"ltr\"><h2>Ошибка загрузки</h2><pre>"
                               + escapeHtml(String.valueOf(ex))
                               + "</pre></body></html>";
                    editorPane.setText(err);
                    statusLabel.setText("Ошибка: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void reload() {
        if (currentUrl == null) return;
        if ("about:home".equals(currentUrl)) { showHome(); return; }
        statusLabel.setText("Обновление: " + currentUrl);
        final String u = currentUrl;
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception { return fetchAndClean(u); }
            @Override protected void done() {
                try {
                    editorPane.setText(get());
                    editorPane.setCaretPosition(0);
                    statusLabel.setText(u);
                } catch (Exception ex) {
                    editorPane.setText("Ошибка: " + ex);
                }
            }
        }.execute();
    }

    private void goBack() {
        if (backStack.isEmpty()) return;
        if (currentUrl != null) fwdStack.push(currentUrl);
        currentUrl = backStack.pop();
        urlField.setText(currentUrl);
        if ("about:home".equals(currentUrl)) showHome(); else reloadQuiet(currentUrl);
        statusLabel.setText(currentUrl);
    }

    private void goForward() {
        if (fwdStack.isEmpty()) return;
        if (currentUrl != null) backStack.push(currentUrl);
        currentUrl = fwdStack.pop();
        urlField.setText(currentUrl);
        if ("about:home".equals(currentUrl)) showHome(); else reloadQuiet(currentUrl);
        statusLabel.setText(currentUrl);
    }

    private void reloadQuiet(String url) {
        final String u = url;
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception { return fetchAndClean(u); }
            @Override protected void done() {
                try { editorPane.setText(get()); editorPane.setCaretPosition(0); }
                catch (Exception ex) { editorPane.setText("Ошибка: " + ex); }
            }
        }.execute();
    }

    private void pushHistory(String url) {
        if (currentUrl != null && !currentUrl.equals(url)) backStack.push(currentUrl);
    }

    // --------------------------------------------------------- FETCH/CLEAN

    private String fetchAndClean(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);          // НЕТ авто-переадресации
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.1");
        conn.setRequestProperty("Accept-Language", "ru,en,ar;q=0.5");

        int status = conn.getResponseCode();

        // HTTP-переадресация — подавляем, показываем ссылку вручную
        if (status >= 300 && status < 400) {
            String loc = conn.getHeaderField("Location");
            if (loc != null) {
                // Resolve relative redirect
                try { loc = new URL(url, loc).toString(); } catch (Exception ignored) {}
            } else {
                loc = "(нет заголовка Location)";
            }
            return "<html><body dir=\"ltr\">"
                 + "<h2>Переадресация отключена</h2>"
                 + "<p>Сервер ответил статусом HTTP " + status + ".</p>"
                 + "<p>Целевой адрес: <a href=\"" + escapeHtml(loc) + "\">"
                 + escapeHtml(loc) + "</a></p>"
                 + "<p>Нажмите на ссылку, чтобы перейти вручную.</p>"
                 + "</body></html>";
        }

        if (status != 200) {
            return "<html><body dir=\"ltr\"><h2>HTTP " + status + "</h2><p>"
                 + escapeHtml(safe(conn.getResponseMessage())) + "</p></body></html>";
        }

        String contentType = conn.getContentType();
        if (contentType != null
                && !contentType.toLowerCase().contains("html")
                && !contentType.toLowerCase().contains("xml")) {
            return "<html><body dir=\"ltr\"><h2>Не HTML</h2>"
                 + "<p>Тип содержимого: " + escapeHtml(contentType) + "</p>"
                 + "<p>TextBrowser отображает только текстовые HTML-страницы.</p>"
                 + "</body></html>";
        }

        byte[] bytes;
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            bytes = bos.toByteArray();
        }

        // jsoup сам определяет кодировку по meta/BOM
        Document doc = Jsoup.parse(new ByteArrayInputStream(bytes), null, urlStr);

        // 1) Удаляем всё нетекстовое: скрипты, стили, медиа, встраиваемое
        doc.select(
            "script, style, noscript, iframe, object, embed, applet, param, "
          + "img, picture, source, video, audio, track, canvas, svg, map, area, "
          + "link, base, "
          + "meta[http-equiv=refresh], "                  // нет meta-redirect
          + "form, input, button, select, textarea, "    // нет форм/JS-контролов
          + "template, slot"
        ).remove();

        // 2) Удаляем рекламу/баннеры по типичным признакам в class/id
        doc.select(buildAdSelector()).remove();

        // 3) Снимаем обработчики событий и JS-атрибуты со всех ссылок
        for (Element a : doc.select("a")) {
            a.removeAttr("onclick");
            a.removeAttr("onmouseover");
            a.removeAttr("onmousedown");
            a.removeAttr("onmouseup");
            a.removeAttr("onkeydown");
            a.removeAttr("onkeyup");
            a.removeAttr("onload");
            a.removeAttr("onerror");
            // target="_blank" тоже убираем — открываем в этом же окне
            a.removeAttr("target");
            a.removeAttr("rel");
        }

        // 4) Заголовок страницы
        String title = doc.title();
        if (title != null && title.isEmpty()) title = null;
        if (title != null) setTitle(title + " — TextBrowser");

        // 5) Определяем основное направление текста (LTR/RTL) по доле арабских букв
        boolean rtl = false;
        String text = doc.body() != null ? doc.body().text() : "";
        if (!text.isEmpty()) {
            int arabic = 0, latin = 0;
            int sample = Math.min(text.length(), 1000);
            for (int i = 0; i < sample; i++) {
                char c = text.charAt(i);
                if (c >= 0x0600 && c <= 0x06FF) arabic++;        // арабский блок
                else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) latin++;
            }
            if (arabic > latin && arabic > 0) rtl = true;
        }

        // 6) Очищаем inline-стили и классы, чтобы HTMLEditorKit не подавился
        for (Element el : doc.getAllElements()) {
            el.removeAttr("style");
            el.removeAttr("class");
            el.removeAttr("id");
            el.removeAttr("data-*");
        }

        // 7) Собираем итоговый HTML с CSS-стилем, пригодным для HTMLEditorKit
        String bodyHtml = doc.body() != null ? doc.body().html() : "";

        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><style>")
          .append("body{font-family:'Noto Sans','DejaVu Sans','FreeSerif',sans-serif;")
          .append("font-size:15pt;line-height:1.5;color:#222;background:#fff;padding:14px;")
          // Для RTL выравниваем текст по правому краю, для LTR — по левому.
          // dir="rtl" на <body> переключает порядок символов (bidi),
          // но text-align в HTMLEditorKit по умолчанию остаётся left —
          // поэтому задаём его явно.
          .append("text-align:").append(rtl ? "right" : "left").append(";}")
          // Дублируем через селектор по атрибуту dir — на случай, если
          // у отдельных блоков задан свой dir.
          .append("body[dir=rtl]{text-align:right;}")
          .append("body[dir=ltr]{text-align:left;}")
          .append("[dir=rtl]{text-align:right;}")
          .append("[dir=ltr]{text-align:left;}")
          .append("a{color:#0066cc;text-decoration:underline;}")
          .append("a:visited{color:#663399;}")
          .append("h1{font-size:22pt;margin:8px 0 4px 0;color:#111;}")
          .append("h2{font-size:18pt;margin:14px 0 4px 0;color:#222;}")
          .append("h3{font-size:15pt;margin:12px 0 4px 0;color:#333;}")
          .append("h4,h5,h6{color:#333;margin:10px 0 4px 0;}")
          .append("p{margin:6px 0;}")
          .append("ul,ol{margin:6px 0 6px 24px;}")
          .append("pre{background:#f5f5f5;padding:8px;font-family:'DejaVu Sans Mono',monospace;}")
          .append("code{font-family:'DejaVu Sans Mono',monospace;}")
          .append("blockquote{margin:6px 0 6px 16px;color:#555;border-left:3px solid #ccc;padding-left:8px;}")
          .append("hr{border:none;border-top:1px solid #ccc;margin:10px 0;}")
          .append("table{border-collapse:collapse;margin:6px 0;}")
          .append("th,td{border:1px solid #ccc;padding:4px 8px;}")
          .append("</style></head><body dir=\"").append(rtl ? "rtl" : "ltr").append("\">");

        if (title != null) {
            sb.append("<h1>").append(escapeHtml(title)).append("</h1><hr>");
        }
        sb.append(bodyHtml);
        sb.append("</body></html>");
        return sb.toString();
    }

    private static final Pattern AD_PATTERN = Pattern.compile(
        // По словам ad/ads/advert/banner/promo/sponsor/cookie/popup/social/share/modal/sidebar
        "\\b(ad|ads|advert|banner|promo|sponsor|cookie|popup|social|share|modal|sidebar|newsletter|subscribe|gdpr|consent|dismiss|paywall)\\b",
        Pattern.CASE_INSENSITIVE);

    private String buildAdSelector() {
        // Селектор по совпадению class или id со словами-маркерами рекламы.
        // Jsoup не поддерживает regex-атрибуты напрямую, поэтому используем
        // множественные селекторы contains/startsWith/endsWith.
        StringBuilder sb = new StringBuilder();
        String[] markers = {"ad", "ads", "advert", "banner", "promo", "sponsor",
                            "cookie", "popup", "social", "share", "modal",
                            "sidebar", "newsletter", "subscribe", "gdpr", "consent",
                            "paywall"};
        for (String m : markers) {
            if (sb.length() > 0) sb.append(",");
            sb.append("[class~=(?i).*\\b").append(m).append("\\b.*]")
              .append(",[id~=(?i).*\\b").append(m).append("\\b.*]");
        }
        // Плюс типичные рекламные теги
        sb.append(",ins.adsbygoogle,aside,nav.ad");
        return sb.toString();
    }

    // ----------------------------------------------------------- utils

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String safe(String s) { return s == null ? "" : s; }

    // ----------------------------------------------------------------- main

    public static void main(String[] args) {
        // --- Сглаживание шрифтов ---
        // Эти свойства должны быть установлены ДО загрузки любого AWT/Swing
        // класса, поэтому ставим их первой инструкцией main().
        //
        // awt.useSystemAAFontSettings=on — использовать системные настройки
        //   антиалиасинга шрифтов (fontconfig / Xft).
        // swing.aatext=true              — включить AA для текста в Swing
        //   (устаревшее свойство, но всё ещё работает на JDK 11–21).
        //
        // Если свойства уже заданы в командной строке (-D...), не переопределяем.
        setIfAbsent("awt.useSystemAAFontSettings", "on");
        setIfAbsent("swing.aatext",                "true");
        // subpixel anti-aliasing — на LCD-экранах даёт более чёткие контуры
        setIfAbsent("sun.java2d.xrender",          "true");

        SwingUtilities.invokeLater(() -> {
            try {
                // Системный LAF: на Linux — GTK, на Windows — Windows,
                // на macOS — Aqua. Это даёт нативные шрифты и сглаживание.
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { }

            TextBrowser b = new TextBrowser();
            b.setVisible(true);
            if (args.length > 0) b.loadUrl(args[0]);
        });
    }

    private static void setIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
