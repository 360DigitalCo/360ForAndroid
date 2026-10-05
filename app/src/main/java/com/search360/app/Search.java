package com.search360.app;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared result-row builders. */
final class Rows {
    static ImageView thumb(Screen s, String url, int dp) {
        ImageView iv = new ImageView(s.c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackground(Ui.shape(Ui.LINE, 10, 0));
        iv.setClipToOutline(true);
        iv.setLayoutParams(Ui.lp(Ui.dp(dp), Ui.dp(dp), 12, 0, 0, 0));
        Img.load(url, iv);
        return iv;
    }
    static LinearLayout rowShell(Screen s) {
        LinearLayout r = Ui.hbox(s.c);
        r.setGravity(Gravity.TOP);
        r.setBackground(Ui.ripple(Ui.shape(Ui.CARD, 14, Ui.LINE)));
        r.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        r.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 9));
        return r;
    }
    static TextView clamp(TextView t, int lines) { t.setMaxLines(lines); t.setEllipsize(android.text.TextUtils.TruncateAt.END); return t; }

    static View article(final Screen s, final JSONObject o) {
        LinearLayout r = rowShell(s);
        LinearLayout b = Ui.vbox(s.c);
        b.addView(Ui.text(s.c, J.s(o, "title"), 15, Ui.TXT, true));
        String src = J.s(o, "source"), age = J.s(o, "age");
        String meta = src + (src.isEmpty() || age.isEmpty() ? "" : "  \u2022  ") + age;
        if (!meta.isEmpty()) b.addView(Ui.text(s.c, meta, 12, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        String d = J.s(o, "desc");
        if (!d.isEmpty()) b.addView(clamp(Ui.text(s.c, d, 13, Ui.MUT, false), 3), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        r.addView(b, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        if (!J.s(o, "thumb").isEmpty()) r.addView(thumb(s, J.s(o, "thumb"), 84));
        r.setOnClickListener(v -> s.a.push(new ReaderScreen(J.s(o, "url"), J.s(o, "title"))));
        return r;
    }

    static View product(final Screen s, final JSONObject o) {
        LinearLayout r = rowShell(s);
        if (!J.s(o, "thumb").isEmpty()) {
            ImageView iv = thumb(s, J.s(o, "thumb"), 84);
            ((LinearLayout.LayoutParams) iv.getLayoutParams()).setMargins(0, 0, Ui.dp(12), 0);
            r.addView(iv);
        }
        LinearLayout b = Ui.vbox(s.c);
        b.addView(clamp(Ui.text(s.c, J.s(o, "title"), 14.5f, Ui.TXT, true), 2));
        if (!J.s(o, "price").isEmpty()) b.addView(Ui.text(s.c, J.s(o, "price"), 17, Ui.TXT, true), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        String meta = J.s(o, "source");
        if (o.has("rating") && !o.isNull("rating") && !String.valueOf(o.opt("rating")).isEmpty())
            meta += (meta.isEmpty() ? "" : "  \u2022  ") + o.opt("rating") + " stars" + (J.s(o, "reviews").isEmpty() ? "" : " (" + J.s(o, "reviews") + ")");
        if (!meta.isEmpty()) b.addView(Ui.text(s.c, meta, 12, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 3, 0, 0));
        r.addView(b, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        r.setOnClickListener(v -> { if (!J.s(o, "url").isEmpty()) s.a.push(new ReaderScreen(J.s(o, "url"), J.s(o, "title"))); });
        return r;
    }

    static String stripTags(String s) { return HtmlReader.decode(s == null ? "" : s.replaceAll("(?s)<[^>]+>", "")).trim(); }
    static String hostOf(String u) {
        try { String h = new java.net.URI(u).getHost(); return h == null ? "" : h.replaceFirst("^www\\.", ""); } catch (Exception e) { return ""; }
    }
}


/** Custom result cards. */
final class ResultUi {
    static String siteName(String host) {
        String h = host == null ? "" : host.toLowerCase(java.util.Locale.US).replaceFirst("^www\\.", "");
        if (h.isEmpty()) return "";
        String[] p = h.split("\\.");
        if (p.length <= 1) return cap(h);
        int idx = p.length - 2;
        if (p.length >= 3 && p[p.length - 1].length() == 2) {
            String m = p[idx];
            if (m.equals("co") || m.equals("com") || m.equals("org") || m.equals("net") || m.equals("gov") || m.equals("ac") || m.equals("edu")) idx = p.length - 3;
        }
        return cap(p[idx]);
    }
    private static String cap(String s) {
        s = s.replace('-', ' ');
        if (s.length() <= 3) return s.toUpperCase(java.util.Locale.US);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    static int avatarColor(String host) {
        return android.graphics.Color.HSVToColor(new float[]{Math.abs((host == null ? "" : host).hashCode()) % 360, 0.55f, Ui.dark ? 0.82f : 0.72f});
    }
    static TextView avatar(Screen s, String host, int dp) {
        String n = siteName(host);
        TextView t = Ui.text(s.c, n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase(java.util.Locale.US), dp * 0.46f, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.shape(avatarColor(host), dp / 2f, 0));
        t.setLayoutParams(Ui.lp(Ui.dp(dp), Ui.dp(dp), 0, 0, 10, 0));
        return t;
    }

    /** Snippet with the query terms (the <b> runs) emphasised. */
    static CharSequence snippet(String html) {
        SpannableStringBuilder sb = new SpannableStringBuilder(android.text.Html.fromHtml(html == null ? "" : html));
        for (android.text.style.StyleSpan sp : sb.getSpans(0, sb.length(), android.text.style.StyleSpan.class)) {
            if (sp.getStyle() == Typeface.BOLD)
                sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.TXT), sb.getSpanStart(sp), sb.getSpanEnd(sp), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private static TextView badge(Screen s, String label, int color) {
        TextView t = Ui.text(s.c, label, 10.5f, color, true);
        t.setBackground(Ui.shape((color & 0x00FFFFFF) | 0x22000000, 8, 0));
        t.setPadding(Ui.dp(8), Ui.dp(3), Ui.dp(8), Ui.dp(3));
        t.setLayoutParams(Ui.lp(Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        return t;
    }

    static View card(final Screen s, final Hit h) {
        LinearLayout card = Ui.vbox(s.c);
        card.setBackground(Ui.ripple(Ui.shape(Ui.CARD, 16, Ui.LINE)));
        card.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        card.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        card.setClickable(true);

        LinearLayout top = Ui.hbox(s.c);
        top.addView(avatar(s, h.host(), 28));
        LinearLayout who = Ui.vbox(s.c);
        who.addView(Ui.text(s.c, siteName(h.host()), 13, Ui.TXT, true));
        TextView crumb = Ui.text(s.c, h.crumb.isEmpty() ? h.host() : h.crumb, 11.5f, Ui.MUT, false);
        crumb.setSingleLine(true); crumb.setEllipsize(android.text.TextUtils.TruncateAt.END);
        who.addView(crumb);
        top.addView(who, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        FrameLayout more = new FrameLayout(s.c);
        more.setClickable(true);
        more.setContentDescription("More options");
        more.addView(Ui.icon(s.c, "sliders", 16, Ui.MUT), new FrameLayout.LayoutParams(Ui.dp(16), Ui.dp(16), Gravity.CENTER));
        more.setOnClickListener(v -> menu(s, h));
        top.addView(more, Ui.lp(Ui.dp(34), Ui.dp(34)));
        card.addView(top);

        LinearLayout mid = Ui.hbox(s.c);
        mid.setGravity(Gravity.TOP);
        LinearLayout text = Ui.vbox(s.c);
        TextView title = Ui.text(s.c, h.title.isEmpty() ? h.url : h.title, 17, Ui.ACC, true);
        title.setLineSpacing(0, 1.08f);
        title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(title, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 8, 0, 0));
        if (!h.snippet.isEmpty()) {
            TextView sn = Ui.text(s.c, "", 13.5f, Ui.MUT, false);
            sn.setText(snippet(h.snippet));
            sn.setLineSpacing(0, 1.22f);
            sn.setMaxLines(4); sn.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.addView(sn, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 5, 0, 0));
        }
        mid.addView(text, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        if (!h.thumb.isEmpty()) mid.addView(Rows.thumb(s, h.thumb, 80));
        card.addView(mid);

        LinearLayout badges = Ui.hbox(s.c);
        if (h.from360) badges.addView(badge(s, "360 INDEX", Ui.ACC));
        if (h.kind.equals("video")) badges.addView(badge(s, "VIDEO", 0xFFEF4444));
        if (h.kind.equals("pdf")) badges.addView(badge(s, "PDF", 0xFFD97706));
        if (badges.getChildCount() > 0) card.addView(badges, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 8, 0, 0));

        card.setOnClickListener(v -> open(s, h));
        card.setOnLongClickListener(v -> { menu(s, h); return true; });
        return card;
    }

    static View video(final Screen s, final Hit h) {
        LinearLayout card = Ui.vbox(s.c);
        card.setBackground(Ui.ripple(Ui.shape(Ui.CARD, 16, Ui.LINE)));
        card.setClipToOutline(true);
        card.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        card.setClickable(true);
        FrameLayout thumb = new FrameLayout(s.c);
        thumb.setBackgroundColor(0xFF000000);
        ImageView iv = new ImageView(s.c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.addView(iv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        if (!h.thumb.isEmpty()) Img.load(h.thumb, iv);
        FrameLayout play = new FrameLayout(s.c);
        play.setBackground(Ui.shape(0x99000000, 26, 0));
        play.addView(Ui.icon(s.c, "play", 24, 0xFFFFFFFF), new FrameLayout.LayoutParams(Ui.dp(24), Ui.dp(24), Gravity.CENTER));
        thumb.addView(play, new FrameLayout.LayoutParams(Ui.dp(52), Ui.dp(52), Gravity.CENTER));
        card.addView(thumb, new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(188)));
        LinearLayout meta = Ui.vbox(s.c);
        meta.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(12));
        TextView t = Ui.text(s.c, h.title, 15, Ui.TXT, true);
        t.setMaxLines(2); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        meta.addView(t);
        meta.addView(Ui.text(s.c, siteName(h.host()) + "  \u2022  opens outside the app", 12, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        card.addView(meta);
        card.setOnClickListener(v -> s.a.openExternal(h.url));
        card.setOnLongClickListener(v -> { menu(s, h); return true; });
        return card;
    }

    static void open(Screen s, Hit h) {
        if (h.kind.equals("video")) s.a.openExternal(h.url);
        else s.a.push(new ReaderScreen(h.url, h.title));
    }

    static void menu(final Screen s, final Hit h) {
        new android.app.AlertDialog.Builder(s.a).setTitle(h.title.isEmpty() ? h.host() : h.title)
            .setItems(new String[]{"Read here", "Open in browser", "Copy link", "Share link"}, (d, i) -> {
                if (i == 0) s.a.push(new ReaderScreen(h.url, h.title));
                else if (i == 1) s.a.openExternal(h.url);
                else if (i == 2) { TranslatorScreen.copyText(s.c, h.url); s.a.toast("Link copied"); }
                else { android.content.Intent it = new android.content.Intent(android.content.Intent.ACTION_SEND); it.setType("text/plain"); it.putExtra(android.content.Intent.EXTRA_TEXT, h.url); s.a.startActivity(android.content.Intent.createChooser(it, "Share link")); }
            }).show();
    }
}

/** Instant answer shown above web results (Wikipedia summary via the same ddg-instant function the website uses). */
final class Knowledge {
    String title = "", desc = "", source = "", image = "";
    List<String> related = new ArrayList<>();
    interface Pick { void on(String q); }

    /** The panel is only relevant when the entity name shares a real word with the query. */
    static boolean relevant(String q, String title) {
        java.util.Set<String> qa = words(q), ta = words(title);
        for (String w : ta) if (qa.contains(w)) return true;
        return false;
    }
    private static java.util.Set<String> words(String s) {
        java.util.Set<String> out = new HashSet<>();
        for (String w : (s == null ? "" : s).toLowerCase(java.util.Locale.US).split("[^\\p{L}\\p{N}]+")) if (w.length() >= 3) out.add(w);
        return out;
    }

    static Knowledge fetch(String q) throws Exception {
        JSONObject d = Api.fn("ddg-instant", "GET", "q=" + Http.enc(q), null);
        String heading = J.s(d, "Heading"), abs = J.s(d, "AbstractText"), src = J.s(d, "AbstractURL"), img = J.s(d, "Image");
        Knowledge k = new Knowledge();
        JSONArray rt = d.optJSONArray("RelatedTopics");
        if (rt != null) for (int i = 0; i < rt.length() && k.related.size() < 5; i++) {
            JSONObject o = rt.optJSONObject(i);
            if (o == null) continue;
            String t = J.s(o, "Text");
            if (t.isEmpty() && o.optJSONArray("Topics") != null && o.optJSONArray("Topics").length() > 0) t = J.s(o.optJSONArray("Topics").optJSONObject(0), "Text");
            int dash = t.indexOf(" - ");
            if (dash > 0) t = t.substring(0, dash);
            if (!t.isEmpty() && t.length() < 60) k.related.add(t);
        }
        k.title = heading; k.desc = abs; k.source = src;
        k.image = img.startsWith("http") ? Cse.https(img) : img.isEmpty() ? "" : "https://duckduckgo.com" + img;
        if (!heading.isEmpty()) {
            try {
                Http.Resp r = Http.request("GET", "https://en.wikipedia.org/api/rest_v1/page/summary/" + Http.enc(heading.replace(' ', '_')), null, null);
                if (r.code == 200) {
                    JSONObject w = new JSONObject(r.body);
                    if (!"disambiguation".equals(J.s(w, "type"))) {
                        if (!J.s(w, "extract").isEmpty()) k.desc = J.s(w, "extract");
                        JSONObject th = w.optJSONObject("thumbnail");
                        if (th != null && !J.s(th, "source").isEmpty()) k.image = Cse.https(J.s(th, "source"));
                        JSONObject cu = w.optJSONObject("content_urls"), dk = cu == null ? null : cu.optJSONObject("desktop");
                        if (dk != null && !J.s(dk, "page").isEmpty()) k.source = J.s(dk, "page");
                    }
                }
            } catch (Exception ignored) { }
        }
        if (k.title.isEmpty() || k.desc.isEmpty()) return null;
        if (!relevant(q, k.title)) return null;
        return k;
    }

    View card(final Screen s, final Pick pick) {
        LinearLayout c = Ui.vbox(s.c);
        c.setBackground(Ui.shape(Ui.CARD, 18, Ui.LINE));
        c.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        c.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        LinearLayout row = Ui.hbox(s.c);
        row.setGravity(Gravity.TOP);
        LinearLayout t = Ui.vbox(s.c);
        t.addView(Ui.text(s.c, "INSTANT ANSWER", 10.5f, Ui.ACC, true));
        t.addView(Ui.text(s.c, title, 21, Ui.TXT, true), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 3, 0, 0));
        row.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        if (!image.isEmpty()) row.addView(Rows.thumb(s, image, 76));
        c.addView(row);
        TextView d = Ui.text(s.c, desc, 14, Ui.TXT, false);
        d.setLineSpacing(0, 1.28f);
        d.setMaxLines(6); d.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(d, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 8, 0, 0));
        if (!source.isEmpty()) {
            TextView more = Ui.text(s.c, "Read more on " + ResultUi.siteName(Rows.hostOf(source)), 13, Ui.ACC, true);
            more.setPadding(0, Ui.dp(10), 0, Ui.dp(2));
            more.setClickable(true);
            more.setOnClickListener(v -> s.a.push(new ReaderScreen(source, title)));
            c.addView(more);
        }
        if (!related.isEmpty()) {
            c.addView(Ui.text(s.c, "People also search for", 11.5f, Ui.MUT, true), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 8, 0, 6));
            LinearLayout chips = Ui.hbox(s.c);
            for (final String r : related) { TextView ch = Ui.chip(s.c, r, false); ch.setOnClickListener(v -> pick.on(r)); chips.addView(ch); }
            HorizontalScrollView hs = new HorizontalScrollView(s.c);
            hs.setHorizontalScrollBarEnabled(false); hs.addView(chips);
            c.addView(hs);
        }
        return c;
    }
}

final class SearchScreen extends Screen {
    static String pendingQuery;
    private static final String[][] TABS = {{"web", "Web"}, {"images", "Images"}, {"videos", "Videos"}, {"news", "News"}, {"shopping", "Shopping"}, {"ai", "AI"}};
    private EditText box;
    private LinearLayout results, sugg, tabRow;
    private String tab = "web", query = "";
    private int token = 0;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Runnable pendingSugg, paintLater;
    private boolean suppressSugg;

    // web-tab state
    private List<Hit> cseHits = new ArrayList<>(), edgeHits = new ArrayList<>();
    private boolean cseDone, edgeDone, cseViaFn, cseMore;
    private String count = "", time = "";
    private int cseNext;
    private Knowledge kp;
    private List<String> related = new ArrayList<>();
    private Exception webError;

    @Override View build() {
        LinearLayout l = col();
        box = Ui.input(c, "Search the web");
        box.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.setCompoundDrawablesWithIntrinsicBounds(Icons.drawable(c, "search", Ui.MUT), null, null, null);
        box.setCompoundDrawablePadding(Ui.dp(10));
        box.setOnEditorActionListener((v, id, e) -> { run(box.getText().toString().trim()); return true; });
        box.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a1, int b, int c1) { }
            public void onTextChanged(CharSequence s, int a1, int b, int c1) { }
            public void afterTextChanged(android.text.Editable s) {
                if (suppressSugg) return;
                if (pendingSugg != null) h.removeCallbacks(pendingSugg);
                final String t = s.toString().trim();
                if (t.length() < 2) { sugg.removeAllViews(); return; }
                pendingSugg = () -> suggest(t);
                h.postDelayed(pendingSugg, 250);
            }
        });
        l.addView(box);
        sugg = Ui.vbox(c);
        l.addView(sugg);

        tabRow = Ui.hbox(c);
        for (int i = 0; i < TABS.length; i++) {
            final String id = TABS[i][0];
            TextView ch = Ui.chip(c, TABS[i][1], id.equals(tab));
            ch.setOnClickListener(v -> { tab = id; paintTabs(); if (!query.isEmpty()) run(query); });
            tabRow.addView(ch);
        }
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(tabRow);
        l.addView(hs, Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));

        results = Ui.vbox(c);
        results.addView(Ui.state(c, "search", "Search 360", "Private, ad-free results. Pages open in the built-in reader."));
        l.addView(results);
        return scroll(l);
    }

    private void paintTabs() {
        for (int i = 0; i < tabRow.getChildCount(); i++) {
            TextView t = (TextView) tabRow.getChildAt(i);
            boolean on = TABS[i][0].equals(tab);
            t.setTextColor(on ? 0xFFFFFFFF : Ui.TXT);
            t.setBackground(Ui.ripple(on ? Ui.shape(Ui.ACC, 20, 0) : Ui.shape(Ui.CARD, 20, Ui.LINE)));
        }
    }

    @Override void onShow() {
        if (pendingQuery != null) {
            String q = pendingQuery; pendingQuery = null;
            suppressSugg = true; box.setText(q); suppressSugg = false;
            run(q);
        } else if (query.isEmpty()) box.requestFocus();
    }

    private void suggest(final String q) {
        async(() -> Api.fn("autocomplete", "GET", "q=" + Http.enc(q), null), (j, e) -> {
            sugg.removeAllViews();
            if (e != null || j == null || !box.getText().toString().trim().equals(q)) return;
            List<String> list = suggestionsOf(j);
            if (list.isEmpty()) return;
            LinearLayout box2 = Ui.vbox(c);
            box2.setBackground(Ui.shape(Ui.CARD, 12, Ui.LINE));
            box2.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
            for (final String s : list.subList(0, Math.min(6, list.size()))) {
                LinearLayout row = Ui.hbox(c);
                row.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
                row.setBackground(Ui.ripple(null)); row.setClickable(true);
                row.addView(Ui.icon(c, "search", 16, Ui.MUT), Ui.lp(Ui.dp(16), Ui.dp(16), 0, 0, 12, 0));
                row.addView(Ui.text(c, s, 14.5f, Ui.TXT, false));
                row.setOnClickListener(v -> { suppressSugg = true; box.setText(s); suppressSugg = false; run(s); });
                box2.addView(row);
            }
            sugg.addView(box2);
        });
    }

    /** Accepts {suggestions|items|results: [string | {text}|{phrase}]}. */
    static List<String> suggestionsOf(JSONObject j) {
        List<String> out = new ArrayList<>();
        JSONArray arr = j.optJSONArray("suggestions");
        if (arr == null) arr = j.optJSONArray("items");
        if (arr == null) arr = j.optJSONArray("results");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            Object o = arr.opt(i);
            String s = o instanceof JSONObject ? J.s((JSONObject) o, "text", J.s((JSONObject) o, "phrase")) : (o == null || o == JSONObject.NULL ? "" : String.valueOf(o));
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private void run(String q) {
        if (q.isEmpty()) return;
        query = q;
        sugg.removeAllViews(); a.hideKeyboard();
        results.removeAllViews(); results.addView(Ui.loading(c));
        final int my = ++token;
        switch (tab) {
            case "images": images(q, my); break;
            case "videos": videos(q, my); break;
            case "news": articles(q, "news", my); break;
            case "shopping": shopping(q, my); break;
            case "ai": ai(q, my); break;
            default: web(q, my);
        }
    }

    private boolean safeOff() { return Store.get("safe", "moderate").equals("off"); }

    /* ── Web: native CSE + 360 index + instant answer + related, painted progressively ── */
    private void web(final String q, final int my) {
        cseHits = new ArrayList<>(); edgeHits = new ArrayList<>();
        cseDone = edgeDone = cseViaFn = cseMore = false;
        count = time = ""; cseNext = 10; kp = null; related = new ArrayList<>(); webError = null;
        if (paintLater != null) h.removeCallbacks(paintLater);

        async(() -> Cse.searchAny(q, 0, safeOff(), false), (p, e) -> {
            if (my != token) return;
            cseDone = true;
            if (p != null) { cseHits = p.hits; count = p.count; time = p.time; cseViaFn = p.viaFn; cseMore = p.maxStart >= 10 && !p.hits.isEmpty(); }
            else webError = e;
            webArrived(my);
        });
        async(() -> Api.fn("search", "POST", null, new JSONObject().put("q", q).put("tab", "web").put("safe", Store.get("safe", "moderate"))), (j, e) -> {
            if (my != token) return;
            edgeDone = true;
            if (j != null) {
                JSONArray arr = j.optJSONArray("web");
                if (arr != null) for (int i = 0; i < arr.length(); i++) { Hit x = Hit.fromEdge(arr.optJSONObject(i)); if (x != null) edgeHits.add(x); }
            } else if (webError == null) webError = e;
            webArrived(my);
        });
        async(() -> Knowledge.fetch(q), (k, e) -> { if (my != token) return; kp = k; if (cseDone || edgeDone) paintWeb(); });
        async(() -> Api.fn("autocomplete", "GET", "q=" + Http.enc(q), null), (j, e) -> {
            if (my != token || j == null) return;
            for (String s : suggestionsOf(j)) if (!s.equalsIgnoreCase(q) && related.size() < 6) related.add(s);
            if (cseDone && edgeDone) paintWeb();
        });
    }

    /** Paint when both sources are in, or 1.8 s after the first (so 360 index hits can still rank first). */
    private void webArrived(final int my) {
        if (cseDone && edgeDone) { if (paintLater != null) h.removeCallbacks(paintLater); paintWeb(); return; }
        if (paintLater == null || true) {
            if (paintLater != null) h.removeCallbacks(paintLater);
            paintLater = () -> { if (my == token) paintWeb(); };
            h.postDelayed(paintLater, 1800);
        }
    }

    /** 360 index first (best score first), then Google CSE, then remaining edge results; duplicates removed. */
    static List<Hit> merge(List<Hit> edge, List<Hit> cse) {
        List<Hit> idx = new ArrayList<>(), rest = new ArrayList<>(), out = new ArrayList<>();
        for (Hit x : edge) (x.from360 ? idx : rest).add(x);
        Collections.sort(idx, new Comparator<Hit>() { @Override public int compare(Hit x, Hit y) { return Double.compare(y.score, x.score); } });
        Set<String> seen = new HashSet<>();
        for (List<Hit> g : java.util.Arrays.asList(idx, cse, rest)) for (Hit x : g) if (seen.add(norm(x.url))) out.add(x);
        return out;
    }
    static String norm(String u) { return u.toLowerCase(java.util.Locale.US).replaceFirst("^https?://(www\\.)?", "").replaceFirst("[/#?]+$", ""); }

    private void paintWeb() {
        results.removeAllViews();
        List<Hit> merged = merge(edgeHits, cseHits);
        if (kp != null) results.addView(kp.card(this, s -> { suppressSugg = true; box.setText(s); suppressSugg = false; run(s); }));
        if (!count.isEmpty()) results.addView(Ui.text(c, "About " + count + " results" + (time.isEmpty() ? "" : "  \u2022  " + time + " s"), 12, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 2, 0, 0, 8));
        if (merged.isEmpty() && cseDone && edgeDone) {
            results.addView(Ui.state(c, webError != null ? "alert" : "search", webError != null ? "Couldn't search" : "No results", webError != null ? msg(webError) : "Try different keywords."));
        }
        for (Hit x : merged) results.addView(ResultUi.card(this, x));
        if (!(cseDone && edgeDone)) results.addView(Ui.loading(c));
        if (cseDone && cseMore) {
            final TextView more = Ui.button(c, "More results", 1);
            more.setOnClickListener(v -> loadMore(more));
            results.addView(more);
        }
        if (cseDone && edgeDone && !related.isEmpty()) {
            results.addView(Ui.label(c, "Related searches"));
            for (final String r : related) {
                LinearLayout row = Ui.hbox(c);
                row.setBackground(Ui.ripple(Ui.shape(Ui.CARD, 12, Ui.LINE)));
                row.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
                row.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));
                row.setClickable(true);
                row.addView(Ui.icon(c, "search", 16, Ui.MUT), Ui.lp(Ui.dp(16), Ui.dp(16), 0, 0, 12, 0));
                row.addView(Ui.text(c, r, 14.5f, Ui.TXT, false));
                row.setOnClickListener(v -> { suppressSugg = true; box.setText(r); suppressSugg = false; run(r); });
                results.addView(row);
            }
        }
    }

    private void loadMore(final TextView btn) {
        final int my = token, start = cseNext;
        btn.setEnabled(false); btn.setText("Loading\u2026");
        async(() -> Cse.searchAny(query, start, safeOff(), cseViaFn), (p, e) -> {
            if (my != token) return;
            if (p == null || p.hits.isEmpty()) cseMore = false;
            else { cseHits.addAll(p.hits); cseNext += 10; cseMore = p.maxStart >= cseNext || p.viaFn; }
            paintWeb();
        });
    }

    private void videos(final String q, final int my) {
        async(() -> Cse.searchAny(q + " (site:youtube.com OR site:vimeo.com OR site:tiktok.com)", 0, safeOff(), false), (p, e) -> {
            if (my != token) return;
            results.removeAllViews();
            if (e != null || p == null) { results.addView(Ui.state(c, "alert", "Couldn't load videos", msg(e))); return; }
            if (p.hits.isEmpty()) { results.addView(Ui.state(c, "video", "No videos found", "Try different keywords.")); return; }
            for (Hit x : p.hits) results.addView(ResultUi.video(this, x));
        });
    }

    private void articles(final String q, final String key, final int my) {
        async(() -> Api.fn("search", "POST", null, new JSONObject().put("q", q).put("tab", key)), (j, e) -> {
            if (my != token) return;
            results.removeAllViews();
            if (e != null) { results.addView(Ui.state(c, "alert", "Couldn't load results", msg(e))); return; }
            JSONArray arr = j.optJSONArray(key);
            if (arr == null || arr.length() == 0) { results.addView(Ui.state(c, "news", "No results", "Try different keywords.")); return; }
            for (int i = 0; i < arr.length(); i++) if (arr.optJSONObject(i) != null) results.addView(Rows.article(this, arr.optJSONObject(i)));
        });
    }

    private void shopping(final String q, final int my) {
        async(() -> Api.fn("search", "POST", null, new JSONObject().put("q", q).put("tab", "shopping")), (j, e) -> {
            if (my != token) return;
            results.removeAllViews();
            if (e != null) { results.addView(Ui.state(c, "alert", "Couldn't load results", msg(e))); return; }
            JSONArray arr = j.optJSONArray("shopping");
            if (arr == null || arr.length() == 0) { results.addView(Ui.state(c, "bag", "No products found", "Try different keywords.")); return; }
            for (int i = 0; i < arr.length(); i++) if (arr.optJSONObject(i) != null) results.addView(Rows.product(this, arr.optJSONObject(i)));
        });
    }

    private void images(final String q, final int my) {
        final String safe = safeOff() ? "off" : "active";
        async(() -> {
            JSONArray all = new JSONArray(); Set<String> seen = new HashSet<>();
            for (int start : new int[]{1, 11}) {
                try {
                    JSONObject j = Api.fn("image-search", "GET", "q=" + Http.enc(q) + "&start=" + start + "&safe=" + safe, null);
                    JSONArray it = j.optJSONArray("items");
                    if (it != null) for (int i = 0; i < it.length(); i++) {
                        JSONObject o = it.optJSONObject(i);
                        if (o != null && !J.s(o, "src").isEmpty() && seen.add(J.s(o, "src"))) all.put(o);
                    }
                } catch (Exception ex) { if (start == 1) throw ex; }
            }
            return all;
        }, (arr, e) -> {
            if (my != token) return;
            results.removeAllViews();
            if (e != null) { results.addView(Ui.state(c, "alert", "Couldn't load images", msg(e))); return; }
            if (arr.length() == 0) { results.addView(Ui.state(c, "image", "No images found", "Try different keywords.")); return; }
            LinearLayout cols = Ui.hbox(c);
            cols.setGravity(Gravity.TOP);
            LinearLayout[] col = {Ui.vbox(c), Ui.vbox(c)};
            for (int k = 0; k < 2; k++) cols.addView(col[k], Ui.lp(0, Ui.WRAP, k == 0 ? 0 : 4, 0, k == 0 ? 4 : 0, 0));
            for (int k = 0; k < 2; k++) ((LinearLayout.LayoutParams) col[k].getLayoutParams()).weight = 1f;
            for (int i = 0; i < arr.length(); i++) {
                final JSONObject o = arr.optJSONObject(i);
                ImageView iv = new ImageView(c);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setBackground(Ui.shape(Ui.LINE, 11, 0));
                iv.setClipToOutline(true);
                iv.setMinimumHeight(Ui.dp(90));
                iv.setContentDescription(J.s(o, "alt"));
                iv.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
                iv.setOnClickListener(v -> a.push(new ImageViewerScreen(o)));
                col[i % 2].addView(iv);
                Img.load(J.s(o, "src"), iv);
            }
            results.addView(cols);
        });
    }

    private void ai(final String q, final int my) {
        async(() -> Api.fn("search-ai", "POST", null, new JSONObject().put("message", q)), (j, e) -> {
            if (my != token) return;
            results.removeAllViews();
            if (e != null) { results.addView(Ui.state(c, "alert", "AI answer unavailable", msg(e))); return; }
            String ans = "";
            for (String k : new String[]{"reply", "response", "content", "text", "message", "answer"}) if (!J.s(j, k).isEmpty()) { ans = J.s(j, k); break; }
            LinearLayout card = Ui.card(c);
            card.addView(Ui.text(c, "AI ANSWER", 11, Ui.ACC, true));
            LinearLayout body = Ui.vbox(c);
            body.setPadding(0, Ui.dp(8), 0, 0);
            MdView.render(this, body, ans.isEmpty() ? "No answer returned." : ans, Ui.TXT, 15);
            card.addView(body);
            results.addView(card);
            results.addView(Ui.text(c, "AI can make mistakes. Check important information.", 11.5f, Ui.MUT, false));
        });
    }
}

/** Full-screen image with a way into its source page. */
final class ImageViewerScreen extends Screen {
    private final JSONObject o;
    ImageViewerScreen(JSONObject o) { this.o = o; }
    @Override String title() { return Auth.firstNonEmpty(J.s(o, "displayUrl"), Rows.hostOf(J.s(o, "href")), "Image"); }
    @Override View build() {
        LinearLayout l = Ui.vbox(c);
        l.setBackgroundColor(0xFF000000);
        l.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        l.addView(iv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        Img.load(J.s(o, "src"), iv);
        TextView cap = Ui.text(c, J.s(o, "alt"), 13, 0xFFFFFFFF, false);
        cap.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
        l.addView(cap);
        if (!J.s(o, "href").isEmpty()) {
            TextView b = Ui.button(c, "View source page", 0);
            b.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 16, 0, 16, 16));
            b.setOnClickListener(v -> a.push(new ReaderScreen(J.s(o, "href"), J.s(o, "alt"))));
            l.addView(b);
        }
        return l;
    }
}

/** Reader mode: fetches the page through the anonymous viewer and renders it as native text. */
final class ReaderScreen extends Screen {
    private final String url, ttl;
    private LinearLayout body;
    ReaderScreen(String url, String title) { this.url = url; this.ttl = title == null ? "" : title; }
    @Override String title() { return Rows.hostOf(url); }
    @Override View[] actions() {
        return new View[]{Ui.iconButton(a, "external", v -> a.openExternal(url))};
    }

    @Override View build() {
        LinearLayout l = col();
        body = Ui.vbox(c);
        l.addView(body);
        body.addView(Ui.loading(c));
        async(() -> {
            Http.Resp r = Http.request("GET", Api.SB + "/functions/v1/web-viewer?url=" + Http.enc(url), Api.headers(false), null);
            if (r.code >= 400) throw new Exception("The viewer couldn't load this page (" + r.code + ").");
            return HtmlReader.extract(r.body, url);
        }, (res, e) -> {
            body.removeAllViews();
            if (e != null || res == null || res.textLen < 120) {
                body.addView(Ui.state(c, "alert", "Can't show this page here", e != null ? msg(e) : "It needs a full browser (apps, logins or heavy scripts)."));
                TextView b = Ui.button(c, "Open in browser", 0);
                b.setOnClickListener(v -> a.openExternal(url));
                body.addView(b);
                return;
            }
            String title = Auth.firstNonEmpty(res.title, ttl);
            if (!title.isEmpty()) { TextView t = Ui.text(c, title, 22, Ui.TXT, true); t.setLineSpacing(0, 1.1f); body.addView(t, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 0, 0, 4)); }
            body.addView(Ui.text(c, Rows.hostOf(url) + "  \u2022  anonymous reader view", 12, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 0, 0, 16));
            TextView tv = Ui.text(c, "", 16, Ui.TXT, false);
            tv.setLineSpacing(0, 1.35f);
            tv.setLinkTextColor(Ui.ACC);
            tv.setText(linkify(Html.fromHtml(res.html)));
            tv.setMovementMethod(LinkMovementMethod.getInstance());
            tv.setTextIsSelectable(false);
            body.addView(tv);
        });
        return scroll(l);
    }

    /** Links open in this reader, not an external browser. */
    private CharSequence linkify(CharSequence src) {
        SpannableStringBuilder sb = new SpannableStringBuilder(src);
        for (final URLSpan u : sb.getSpans(0, sb.length(), URLSpan.class)) {
            int s = sb.getSpanStart(u), e = sb.getSpanEnd(u), f = sb.getSpanFlags(u);
            final String href = u.getURL();
            sb.removeSpan(u);
            sb.setSpan(new ClickableSpan() {
                @Override public void onClick(View v) { a.push(new ReaderScreen(href, "")); }
            }, s, e, f);
        }
        return sb;
    }
}
