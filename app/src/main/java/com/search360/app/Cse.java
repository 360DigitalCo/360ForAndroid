package com.search360.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One search result, whichever backend produced it. */
final class Hit {
    String title = "", url = "", visible = "", crumb = "", snippet = "", thumb = "", kind = "web", source = "cse";
    boolean from360;
    double score;

    String host() { return Rows.hostOf(url); }

    /** Hit from the 360 `search` edge function (index or fallback web results). */
    static Hit fromEdge(JSONObject o) {
        String u = J.s(o, "url");
        if (u.isEmpty()) return null;
        Hit h = new Hit();
        h.url = u; h.source = "edge";
        h.title = Rows.stripTags(J.s(o, "title"));
        h.snippet = Cse.escapeForHtml(Rows.stripTags(J.s(o, "desc", J.s(o, "snippet"))));
        h.thumb = Cse.https(J.s(o, "thumb"));
        h.visible = J.s(o, "displayUrl", Rows.hostOf(u));
        h.crumb = Cse.crumbOf(u);
        h.from360 = o.optBoolean("_from360");
        h.score = o.optDouble("_score", 0);
        h.kind = Cse.kindOf(u, "");
        return h;
    }
}

/**
 * Native front-end for the website's Google Programmable Search engine.
 * The website renders Google's JavaScript widget; the widget itself just calls the "element" endpoint with a
 * short-lived token that cse.js hands out. We do the same two calls and render the results ourselves.
 * This endpoint is undocumented, so callers must always have a fallback (see SearchScreen).
 */
final class Cse {
    static final String CX = "e003eb0834b6b4be8";
    private static final String PAGE = "https://360-search.com/search.html";
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";

    static final class Page {
        List<Hit> hits = new ArrayList<>();
        String count = "", time = "";
        int maxStart = 0;
        boolean viaFn;
    }

    private static String token, ver;
    private static long tokenAt;

    /* ── pure helpers (unit-tested) ─────────────────────────── */
    static String[] parseConfig(String js) {
        if (js == null) return null;
        String t = grab(js, "cse_token", "cse_tok"), v = grab(js, "cselibVersion", "cselibv");
        return t == null || v == null ? null : new String[]{t, v};
    }
    private static String grab(String js, String... keys) {
        for (String k : keys) {
            Matcher m = Pattern.compile("\"" + k + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(js);
            if (m.find()) return unescape(m.group(1));
        }
        return null;
    }
    static String unescape(String raw) {
        if (raw.indexOf('\\') < 0) return raw;
        try { return new JSONObject("{\"v\":\"" + raw + "\"}").getString("v"); } catch (Exception e) { return raw; }
    }
    /** Accepts plain JSON or JSONP (JSONP wrapper: google.search.cse.api123({...});). */
    static String unwrap(String body) {
        if (body == null) return "";
        int a = body.indexOf('{'), b = body.lastIndexOf('}');
        return a >= 0 && b > a ? body.substring(a, b + 1) : "";
    }

    static Page parse(String json) throws Exception {
        JSONObject d = new JSONObject(json);
        Page p = new Page();
        JSONObject cur = d.optJSONObject("cursor");
        if (cur != null) {
            p.count = J.s(cur, "resultCount");
            p.time = J.s(cur, "searchResultTime");
            JSONArray pg = cur.optJSONArray("pages");
            if (pg != null) for (int i = 0; i < pg.length(); i++) {
                JSONObject o = pg.optJSONObject(i);
                if (o == null) continue;
                try { p.maxStart = Math.max(p.maxStart, Integer.parseInt(J.s(o, "start", "0"))); } catch (Exception ignored) { }
            }
        }
        JSONArray rs = d.optJSONArray("results");
        if (rs != null) for (int i = 0; i < rs.length(); i++) {
            JSONObject r = rs.optJSONObject(i);
            if (r == null) continue;
            String url = J.s(r, "unescapedUrl", J.s(r, "url"));
            if (!url.startsWith("http")) continue;
            Hit h = new Hit();
            h.url = url;
            h.title = J.s(r, "titleNoFormatting");
            if (h.title.isEmpty()) h.title = Rows.stripTags(J.s(r, "title"));
            h.visible = J.s(r, "visibleUrl", Rows.hostOf(url));
            h.snippet = cleanSnippet(J.s(r, "content", J.s(r, "contentNoFormatting")));
            JSONObject rich = r.optJSONObject("richSnippet");
            String th = "";
            if (rich != null) {
                JSONObject t = rich.optJSONObject("cseThumbnail"), im = rich.optJSONObject("cseImage");
                th = t != null ? J.s(t, "src") : im != null ? J.s(im, "src") : "";
            }
            if (th.isEmpty()) { JSONObject ti = r.optJSONObject("thumbnailImage"); if (ti != null) th = J.s(ti, "url"); }
            h.thumb = https(th);
            h.crumb = crumbOf(r, url);
            h.kind = kindOf(url, J.s(r, "fileFormat"));
            p.hits.add(h);
        }
        return p;
    }

    /** Keep only <b>, drop every other tag, collapse whitespace. Entities stay encoded for Html.fromHtml. */
    static String cleanSnippet(String html) {
        String s = html == null ? "" : html.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("(?i)<(?!/?b>)[^>]*>", "");
        return s.replaceAll("\\s+", " ").trim();
    }
    static String escapeForHtml(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    static String https(String u) { return u == null || u.isEmpty() ? "" : u.startsWith("http://") ? "https://" + u.substring(7) : u.startsWith("https://") ? u : ""; }

    static String crumbOf(JSONObject r, String url) {
        JSONObject b = r.optJSONObject("breadcrumbUrl");
        if (b != null) {
            JSONArray c = b.optJSONArray("crumbs");
            StringBuilder sb = new StringBuilder(J.s(b, "host", Rows.hostOf(url)));
            if (c != null) for (int i = 0; i < Math.min(3, c.length()); i++) sb.append("  \u203A  ").append(c.optString(i));
            return sb.toString();
        }
        return crumbOf(url);
    }
    static String crumbOf(String url) {
        try {
            java.net.URI u = new java.net.URI(url);
            StringBuilder sb = new StringBuilder(u.getHost() == null ? "" : u.getHost().replaceFirst("^www\\.", ""));
            String path = u.getPath() == null ? "" : u.getPath();
            int n = 0;
            for (String seg : path.split("/")) { if (seg.isEmpty() || n >= 3) continue; sb.append("  \u203A  ").append(java.net.URLDecoder.decode(seg, "UTF-8").replace('-', ' ').replace('_', ' ')); n++; }
            return sb.toString();
        } catch (Exception e) { return Rows.hostOf(url); }
    }
    static String kindOf(String url, String fileFormat) {
        String h = Rows.hostOf(url).toLowerCase(Locale.US), u = url.toLowerCase(Locale.US);
        if (fileFormat != null && fileFormat.toLowerCase(Locale.US).contains("pdf") || u.matches(".*\\.pdf([?#].*)?$")) return "pdf";
        if (h.endsWith("youtube.com") || h.equals("youtu.be") || h.endsWith("vimeo.com") || h.endsWith("tiktok.com") || h.endsWith("dailymotion.com") || h.endsWith("twitch.tv")) return "video";
        return "web";
    }

    /* ── network ─────────────────────────────────────────────── */
    private static Map<String, String> hdr() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Referer", PAGE);
        h.put("Accept-Language", Locale.getDefault().toLanguageTag());
        return h;
    }

    private static synchronized void ensureToken(boolean force) throws Exception {
        if (!force && token != null && System.currentTimeMillis() - tokenAt < 20 * 60 * 1000L) return;
        Http.Resp r = Http.request("GET", "https://cse.google.com/cse.js?cx=" + CX, hdr(), null);
        String[] cfg = r.code == 200 ? parseConfig(r.body) : null;
        if (cfg == null) throw new Exception("Google search is unavailable right now");
        token = cfg[0]; ver = cfg[1]; tokenAt = System.currentTimeMillis();
    }

    /** Blocking. Retries once with a fresh token. */
    static Page search(String q, int start, boolean safeOff) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                ensureToken(attempt > 0);
                String url = "https://cse.google.com/cse/element/v1?rsz=filtered_cse&num=10&hl=en&source=gcsc&cselibv=" + Http.enc(ver)
                        + "&cx=" + CX + "&q=" + Http.enc(q) + "&safe=" + (safeOff ? "off" : "active")
                        + "&cse_tok=" + Http.enc(token) + "&sort=&exp=cc&start=" + start
                        + "&callback=google.search.cse.api1&rurl=" + Http.enc(PAGE);
                Http.Resp r = Http.request("GET", url, hdr(), null);
                String j = unwrap(r.body);
                if (r.code == 200 && !j.isEmpty()) return parse(j);
                last = new Exception("Google search returned " + r.code);
            } catch (Exception e) { last = e; }
        }
        throw last != null ? last : new Exception("Google search is unavailable right now");
    }

    /** Native element endpoint first; if Google changes it, fall back to the optional server function. */
    static Page searchAny(String q, int start, boolean safeOff, boolean preferFn) throws Exception {
        Exception first = null;
        if (!preferFn) { try { return search(q, start, safeOff); } catch (Exception e) { first = e; } }
        try {
            JSONObject j = Api.fn("cse-search", "GET", "q=" + Http.enc(q) + "&start=" + (start + 1), null);
            JSONArray w = j.optJSONArray("web");
            if (w == null) throw new Exception("no results");
            Page p = new Page();
            p.viaFn = true;
            p.maxStart = 90;
            for (int i = 0; i < w.length(); i++) {
                JSONObject o = w.optJSONObject(i);
                if (o == null) continue;
                Hit h = Hit.fromEdge(o);
                if (h != null) { h.source = "cse"; p.hits.add(h); }
            }
            if (!p.hits.isEmpty()) return p;
            throw new Exception("empty");
        } catch (Exception e2) { throw first != null ? first : e2; }
    }
}
