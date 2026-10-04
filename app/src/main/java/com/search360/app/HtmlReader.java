package com.search360.app;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reduces a web page to readable, safe HTML (headings, paragraphs, lists, links). Pure Java, no Android deps. */
final class HtmlReader {
    static final class Result { String title = ""; String html = ""; int textLen; }

    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern JUNK = Pattern.compile("(?is)<(script|style|noscript|svg|iframe|form|nav|footer|aside|button|select|template|head|canvas|video|audio|object|embed|dialog)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern ARTICLE = Pattern.compile("(?is)<article\\b[^>]*>(.*)</article>");
    private static final Pattern MAIN = Pattern.compile("(?is)<main\\b[^>]*>(.*)</main>");
    private static final Pattern BODY = Pattern.compile("(?is)<body\\b[^>]*>(.*)</body>");
    private static final Pattern TAG = Pattern.compile("(?is)<(/?)([a-z0-9]+)\\b([^>]*)>");
    private static final Pattern HREF = Pattern.compile("(?is)\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");

    static Result extract(String raw, String baseUrl) {
        Result r = new Result();
        if (raw == null) return r;
        String h = COMMENT.matcher(raw).replaceAll("");
        Matcher tm = TITLE.matcher(h);
        if (tm.find()) r.title = decode(tm.group(1).replaceAll("(?s)<[^>]+>", "")).trim();
        h = JUNK.matcher(h).replaceAll(" ");
        Matcher m;
        String core = null;
        if ((m = ARTICLE.matcher(h)).find() && textOf(m.group(1)) > 400) core = m.group(1);
        else if ((m = MAIN.matcher(h)).find() && textOf(m.group(1)) > 400) core = m.group(1);
        else if ((m = BODY.matcher(h)).find()) core = m.group(1);
        else core = h;

        StringBuilder out = new StringBuilder();
        Matcher t = TAG.matcher(core);
        int last = 0;
        while (t.find()) {
            out.append(escapeText(core.substring(last, t.start())));
            last = t.end();
            boolean close = !t.group(1).isEmpty();
            String name = t.group(2).toLowerCase();
            switch (name) {
                case "p": case "div": case "section": case "tr": out.append(close ? "<br>" : "<br>"); break;
                case "br": out.append("<br>"); break;
                case "h1": case "h2": case "h3": case "h4": case "h5": case "h6":
                    out.append(close ? "</h3><br>" : "<br><h3>"); break;
                case "b": case "strong": out.append(close ? "</b>" : "<b>"); break;
                case "i": case "em": out.append(close ? "</i>" : "<i>"); break;
                case "ul": case "ol": out.append("<br>"); break;
                case "li": out.append(close ? "" : "<br>\u2022 "); break;
                case "blockquote": out.append(close ? "</i><br>" : "<br><i>"); break;
                case "pre": case "code": out.append(close ? "</tt>" : "<tt>"); break;
                case "a":
                    if (close) out.append("</a>");
                    else {
                        String href = href(t.group(3), baseUrl);
                        out.append(href == null ? "<a>" : "<a href=\"" + href.replace("\"", "%22") + "\">");
                    }
                    break;
                default: break;
            }
        }
        out.append(escapeText(core.substring(last)));
        String res = out.toString().replaceAll("(?:\\s*<br>\\s*){3,}", "<br><br>").replaceAll("^(?:\\s*<br>)+", "").replaceAll("[ \\t\\u00A0]{2,}", " ");
        res = res.replaceAll("<a>\\s*</a>", "").replaceAll("<h3>\\s*</h3>", "");
        r.html = res.trim();
        r.textLen = textOf(res);
        return r;
    }

    static int textOf(String html) { return decode(html.replaceAll("(?s)<[^>]+>", " ")).replaceAll("\\s+", " ").trim().length(); }

    /** Escape bare text but keep entities the page already encoded. */
    private static String escapeText(String s) {
        return s.replaceAll("&(?!(?:#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]{2,8});)", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replaceAll("[\\r\\n\\t]+", " ");
    }
    static String decode(String s) {
        return s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
    }
    private static String href(String attrs, String base) {
        Matcher m = HREF.matcher(attrs == null ? "" : attrs);
        if (!m.find()) return null;
        String v = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
        v = decode(v.trim());
        if (v.isEmpty() || v.startsWith("#") || v.toLowerCase().startsWith("javascript:") || v.toLowerCase().startsWith("mailto:")) return null;
        try {
            URI u = new URI(base).resolve(v.replace(" ", "%20"));
            String s = u.getScheme();
            return "http".equalsIgnoreCase(s) || "https".equalsIgnoreCase(s) ? u.toString() : null;
        } catch (Exception e) { return null; }
    }
}
