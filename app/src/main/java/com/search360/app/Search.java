package com.search360.app;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
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
        b.addView(Ui.text(s.c, o.optString("title"), 15, Ui.TXT, true));
        String src = o.optString("source"), age = o.optString("age");
        String meta = src + (src.isEmpty() || age.isEmpty() ? "" : "  \u2022  ") + age;
        if (!meta.isEmpty()) b.addView(Ui.text(s.c, meta, 12, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        String d = o.optString("desc");
        if (!d.isEmpty()) b.addView(clamp(Ui.text(s.c, d, 13, Ui.MUT, false), 3), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        r.addView(b, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        if (!o.optString("thumb").isEmpty()) r.addView(thumb(s, o.optString("thumb"), 84));
        r.setOnClickListener(v -> s.a.push(new ReaderScreen(o.optString("url"), o.optString("title"))));
        return r;
    }

    static View web(final Screen s, final JSONObject o) {
        LinearLayout r = rowShell(s);
        LinearLayout b = Ui.vbox(s.c);
        String url = o.optString("url");
        b.addView(Ui.text(s.c, Auth.firstNonEmpty(o.optString("displayUrl"), hostOf(url)), 12, Ui.MUT, false));
        TextView t = Ui.text(s.c, stripTags(o.optString("title")), 16, Ui.ACC, true);
        b.addView(t, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 3, 0, 0));
        String d = stripTags(Auth.firstNonEmpty(o.optString("desc"), o.optString("snippet")));
        if (!d.isEmpty()) b.addView(clamp(Ui.text(s.c, d, 13, Ui.MUT, false), 3), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        r.addView(b, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        if (!o.optString("thumb").isEmpty()) r.addView(thumb(s, o.optString("thumb"), 76));
        r.setOnClickListener(v -> s.a.push(new ReaderScreen(url, stripTags(o.optString("title")))));
        return r;
    }

    static View product(final Screen s, final JSONObject o) {
        LinearLayout r = rowShell(s);
        if (!o.optString("thumb").isEmpty()) {
            ImageView iv = thumb(s, o.optString("thumb"), 84);
            ((LinearLayout.LayoutParams) iv.getLayoutParams()).setMargins(0, 0, Ui.dp(12), 0);
            r.addView(iv);
        }
        LinearLayout b = Ui.vbox(s.c);
        b.addView(clamp(Ui.text(s.c, o.optString("title"), 14.5f, Ui.TXT, true), 2));
        if (!o.optString("price").isEmpty()) b.addView(Ui.text(s.c, o.optString("price"), 17, Ui.TXT, true), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        String meta = o.optString("source");
        if (o.has("rating") && !o.isNull("rating") && !String.valueOf(o.opt("rating")).isEmpty())
            meta += (meta.isEmpty() ? "" : "  \u2022  ") + o.opt("rating") + " stars" + (o.optString("reviews").isEmpty() ? "" : " (" + o.optString("reviews") + ")");
        if (!meta.isEmpty()) b.addView(Ui.text(s.c, meta, 12, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 3, 0, 0));
        r.addView(b, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        r.setOnClickListener(v -> { if (!o.optString("url").isEmpty()) s.a.push(new ReaderScreen(o.optString("url"), o.optString("title"))); });
        return r;
    }

    static String stripTags(String s) { return HtmlReader.decode(s == null ? "" : s.replaceAll("(?s)<[^>]+>", "")).trim(); }
    static String hostOf(String u) {
        try { String h = new java.net.URI(u).getHost(); return h == null ? "" : h.replaceFirst("^www\\.", ""); } catch (Exception e) { return ""; }
    }
}

final class SearchScreen extends Screen {
    static String pendingQuery;
    private static final String[][] TABS = {{"web", "Web"}, {"images", "Images"}, {"news", "News"}, {"shopping", "Shopping"}, {"ai", "AI"}};
    private EditText box;
    private LinearLayout results, sugg, tabRow;
    private String tab = "web", query = "";
    private int token = 0, csePage = 1;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Runnable pendingSugg;
    private boolean suppressSugg;

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
        } else if (query.isEmpty()) { box.requestFocus(); }
    }

    private void suggest(final String q) {
        async(() -> Api.fn("autocomplete", "GET", "q=" + Http.enc(q), null), (j, e) -> {
            sugg.removeAllViews();
            if (e != null || j == null || !box.getText().toString().trim().equals(q)) return;
            JSONArray arr = j.optJSONArray("suggestions");
            if (arr == null) arr = j.optJSONArray("items");
            if (arr == null) arr = j.optJSONArray("results");
            if (arr == null || arr.length() == 0) return;
            LinearLayout list = Ui.vbox(c);
            list.setBackground(Ui.shape(Ui.CARD, 12, Ui.LINE));
            list.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
            for (int i = 0; i < Math.min(6, arr.length()); i++) {
                Object o = arr.opt(i);
                final String s = o instanceof JSONObject ? ((JSONObject) o).optString("text") : String.valueOf(o);
                if (s.isEmpty()) continue;
                LinearLayout row = Ui.hbox(c);
                row.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
                row.setBackground(Ui.ripple(null)); row.setClickable(true);
                row.addView(Ui.icon(c, "search", 16, Ui.MUT), Ui.lp(Ui.dp(16), Ui.dp(16), 0, 0, 12, 0));
                row.addView(Ui.text(c, s, 14.5f, Ui.TXT, false));
                row.setOnClickListener(v -> { suppressSugg = true; box.setText(s); suppressSugg = false; run(s); });
                list.addView(row);
            }
            sugg.addView(list);
        });
    }

    private void run(String q) {
        if (q.isEmpty()) return;
        query = q; csePage = 1;
        sugg.removeAllViews(); a.hideKeyboard();
        results.removeAllViews(); results.addView(Ui.loading(c));
        final int my = ++token;
        switch (tab) {
            case "images": images(q, my); break;
            case "news": articles(q, "news", my); break;
            case "shopping": shopping(q, my); break;
            case "ai": ai(q, my); break;
            default: web(q, my);
        }
    }

    /* ── Web: 360 index first, then Programmable Search (cse-search function), then the rest ── */
    private void web(final String q, final int my) {
        lastError = null;
        final JSONArray[] edge = {null}, cse = {null};
        final int[] pending = {2};
        final Runnable fin = () -> {
            if (--pending[0] > 0 || my != token) return;
            renderWeb(merge(edge[0], cse[0]), cse[0] != null && cse[0].length() > 0);
        };
        async(() -> Api.fn("search", "POST", null, new JSONObject().put("q", q).put("tab", "web").put("safe", Store.get("safe", "moderate"))), (j, e) -> {
            if (j != null) edge[0] = j.optJSONArray("web");
            if (e != null && my == token) lastError = e;
            fin.run();
        });
        async(() -> Api.fn("cse-search", "GET", "q=" + Http.enc(q) + "&start=1", null), (j, e) -> {
            if (j != null) cse[0] = j.optJSONArray("web");
            fin.run();
        });
    }
    private Exception lastError;

    static List<JSONObject> merge(JSONArray edge, JSONArray cse) {
        List<JSONObject> idx = new ArrayList<>(), rest = new ArrayList<>(), out = new ArrayList<>();
        if (edge != null) for (int i = 0; i < edge.length(); i++) {
            JSONObject o = edge.optJSONObject(i);
            if (o == null || o.optString("url").isEmpty()) continue;
            if (o.optBoolean("_from360")) idx.add(o); else rest.add(o);
        }
        Collections.sort(idx, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject x, JSONObject y) { return Double.compare(y.optDouble("_score", 0), x.optDouble("_score", 0)); }
        });
        Set<String> seen = new HashSet<>();
        List<JSONObject> cseL = new ArrayList<>();
        if (cse != null) for (int i = 0; i < cse.length(); i++) { JSONObject o = cse.optJSONObject(i); if (o != null && !o.optString("url").isEmpty()) cseL.add(o); }
        for (List<JSONObject> group : new List[]{idx, cseL, rest}) {
            for (JSONObject o : group) if (seen.add(norm(o.optString("url")))) out.add(o);
        }
        return out;
    }
    static String norm(String u) { return u.toLowerCase().replaceFirst("^https?://(www\\.)?", "").replaceFirst("[/#?]+$", ""); }

    private void renderWeb(List<JSONObject> list, boolean canMore) {
        results.removeAllViews();
        if (list.isEmpty()) {
            results.addView(Ui.state(c, lastError != null ? "alert" : "search", lastError != null ? "Couldn't search" : "No results", lastError != null ? msg(lastError) : "Try different keywords."));
            return;
        }
        for (JSONObject o : list) results.addView(Rows.web(this, o));
        if (canMore) {
            final TextView more = Ui.button(c, "More results", 1);
            more.setOnClickListener(v -> loadMore(more));
            results.addView(more);
        }
    }

    private void loadMore(final TextView btn) {
        csePage++;
        final int start = (csePage - 1) * 10 + 1, my = token;
        btn.setEnabled(false); btn.setText("Loading\u2026");
        async(() -> Api.fn("cse-search", "GET", "q=" + Http.enc(query) + "&start=" + start, null), (j, e) -> {
            if (my != token) return;
            results.removeView(btn);
            JSONArray arr = j == null ? null : j.optJSONArray("web");
            if (arr == null || arr.length() == 0) return;
            for (int i = 0; i < arr.length(); i++) if (arr.optJSONObject(i) != null) results.addView(Rows.web(this, arr.optJSONObject(i)));
            TextView more = Ui.button(c, "More results", 1);
            more.setOnClickListener(v -> loadMore(more));
            results.addView(more);
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
        final String safe = Store.get("safe", "moderate").equals("off") ? "off" : "active";
        async(() -> {
            JSONArray all = new JSONArray(); Set<String> seen = new HashSet<>();
            for (int start : new int[]{1, 11}) {
                try {
                    JSONObject j = Api.fn("image-search", "GET", "q=" + Http.enc(q) + "&start=" + start + "&safe=" + safe, null);
                    JSONArray it = j.optJSONArray("items");
                    if (it != null) for (int i = 0; i < it.length(); i++) {
                        JSONObject o = it.optJSONObject(i);
                        if (o != null && !o.optString("src").isEmpty() && seen.add(o.optString("src"))) all.put(o);
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
                iv.setContentDescription(o.optString("alt"));
                iv.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
                iv.setOnClickListener(v -> a.push(new ImageViewerScreen(o)));
                col[i % 2].addView(iv);
                Img.load(o.optString("src"), iv);
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
            for (String k : new String[]{"reply", "response", "content", "text", "message", "answer"}) if (!j.optString(k).isEmpty()) { ans = j.optString(k); break; }
            LinearLayout card = Ui.card(c);
            card.addView(Ui.text(c, "AI ANSWER", 11, Ui.ACC, true));
            TextView t = Ui.text(c, ans.isEmpty() ? "No answer returned." : ans, 15, Ui.TXT, false);
            t.setLineSpacing(0, 1.3f);
            t.setTextIsSelectable(true);
            card.addView(t, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 8, 0, 0));
            results.addView(card);
            results.addView(Ui.text(c, "AI can make mistakes. Check important information.", 11.5f, Ui.MUT, false));
        });
    }
}

/** Full-screen image with a way into its source page. */
final class ImageViewerScreen extends Screen {
    private final JSONObject o;
    ImageViewerScreen(JSONObject o) { this.o = o; }
    @Override String title() { return Auth.firstNonEmpty(o.optString("displayUrl"), Rows.hostOf(o.optString("href")), "Image"); }
    @Override View build() {
        LinearLayout l = Ui.vbox(c);
        l.setBackgroundColor(0xFF000000);
        l.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        l.addView(iv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        Img.load(o.optString("src"), iv);
        TextView cap = Ui.text(c, o.optString("alt"), 13, 0xFFFFFFFF, false);
        cap.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
        l.addView(cap);
        if (!o.optString("href").isEmpty()) {
            TextView b = Ui.button(c, "View source page", 0);
            b.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 16, 0, 16, 16));
            b.setOnClickListener(v -> a.push(new ReaderScreen(o.optString("href"), o.optString("alt"))));
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
