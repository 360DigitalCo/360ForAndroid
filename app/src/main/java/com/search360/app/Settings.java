package com.search360.app;

import android.content.res.ColorStateList;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns an HTTP status + body into something a person can act on. Pure, so it is unit-tested. */
final class Diag {
    static boolean ok(int code) { return code >= 200 && code < 300; }

    static String detail(int code, String body) {
        if (code == 0) return "No connection";
        JSONObject j = Api.parse(body);
        String m = Api.errorOf(j);
        if (m == null) m = J.s(j, "message", J.s(j, "msg", J.s(j, "hint", "")));
        if (m.startsWith("<")) m = "";
        if (m.length() > 90) m = m.substring(0, 90) + "\u2026";
        if (ok(code)) return "OK";
        String tail = m.isEmpty() ? "" : ": " + m;
        if (code == 401 || code == 403) return "Blocked (" + code + ")" + (m.isEmpty() ? ", needs sign-in or permission" : tail);
        if (code == 404) return "Not found (404)" + tail;
        if (code == 400) return "Rejected (400)" + tail;
        if (code == 429) return "Rate limited (429)";
        if (code >= 500) return "Server error (" + code + ")" + tail;
        return "HTTP " + code + tail;
    }
}

final class SettingsScreen extends Screen {
    @Override String title() { return "Settings"; }

    @Override View build() {
        LinearLayout l = col();

        l.addView(Ui.label(c, "Account"));
        l.addView(row(Auth.signedIn() ? Auth.email : "Sign in or create an account", Auth.signedIn() ? "Manage your 360 account" : "Sync mail, notes, chat and more", () -> a.push(new AccountScreen())));

        l.addView(Ui.label(c, "Appearance"));
        l.addView(seg(new String[][]{{"system", "System"}, {"light", "Light"}, {"dark", "Dark"}}, Store.get("theme", "system"), v -> { Store.put("theme", v); a.recreate(); }));

        l.addView(Ui.label(c, "Units"));
        l.addView(seg(new String[][]{{"F", "\u00B0F"}, {"C", "\u00B0C"}}, Weather.unit(), v -> { Store.put("unit", v); toast("Units set to \u00B0" + v); }));

        l.addView(Ui.label(c, "Search"));
        LinearLayout sc = Ui.card(c);
        sc.addView(Ui.text(c, "Safe search", 14.5f, Ui.TXT, true));
        sc.addView(Ui.text(c, "Filters explicit results in web and image search.", 12, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 0, 0, 8));
        sc.addView(seg(new String[][]{{"moderate", "On"}, {"off", "Off"}}, Store.get("safe", "moderate"), v -> Store.put("safe", v)));
        sc.addView(Ui.text(c, "Open results in", 14.5f, Ui.TXT, true), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 6, 0, 8));
        sc.addView(seg(new String[][]{{"reader", "Reader (in app)"}, {"browser", "Browser"}}, Store.get("open", "reader"), v -> Store.put("open", v)));
        l.addView(sc);

        l.addView(Ui.label(c, "Privacy and data"));
        LinearLayout pv = Ui.card(c);
        pv.addView(toggle("Public fallback services", "If a 360 service is unreachable, use a public one (currently the translator). Your text is then sent to that service.", !"off".equals(Store.get("fallbacks", "on")), v -> Store.put("fallbacks", v ? "on" : "off")));
        l.addView(pv);
        l.addView(row("Clear AI chat history", "Removes saved conversations from this device", () -> new android.app.AlertDialog.Builder(a).setTitle("Clear AI chat history?").setNegativeButton("Cancel", null)
            .setPositiveButton("Clear", (d, w) -> { AiScreen.clearHistory(); toast("Cleared"); }).show()));
        l.addView(row("Clear image and mailbox cache", "Frees memory; everything reloads when needed", () -> { Img.cache.evictAll(); Store.remove("mailAddr"); Chat.me = null; toast("Cache cleared"); }));
        l.addView(row("Reset weather location", "Go back to using your device location", () -> { Store.remove("wx_lat"); Store.remove("wx_lon"); Store.remove("wx_name"); Store.remove("wx_devname"); toast("Weather location reset"); }));

        l.addView(Ui.label(c, "Timer and notifications"));
        l.addView(row("Timer settings", "Durations, vibration, sound and the persistent notification", () -> a.push(new TimerScreen())));
        if (Build.VERSION.SDK_INT >= 33) l.addView(row("Allow notifications", "Needed for timer alerts and the persistent timer notification", () -> a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 8)));

        l.addView(Ui.label(c, "Help"));
        l.addView(row("Connection check", "Test every 360 service this app uses and copy a report", () -> a.push(new DiagnosticsScreen())));

        l.addView(Ui.label(c, "About"));
        LinearLayout ab = Ui.card(c);
        ab.addView(Ui.text(c, "360 for Android 4.2.0 (native)", 14.5f, Ui.TXT, true));
        ab.addView(Ui.text(c, "No ads, no analytics, no tracking. Pages from search open through the anonymous reader.", 12.5f, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        l.addView(ab);
        return scroll(l);
    }

    private View row(String title, String sub, final Runnable go) {
        LinearLayout r = Ui.hbox(c);
        r.setBackground(Ui.ripple(Ui.shape(Ui.CARD, 14, Ui.LINE)));
        r.setPadding(Ui.dp(14), Ui.dp(13), Ui.dp(14), Ui.dp(13));
        r.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
        r.setClickable(true);
        LinearLayout t = Ui.vbox(c);
        t.addView(Ui.text(c, title == null ? "" : title, 15, Ui.TXT, true));
        t.addView(Ui.text(c, sub, 12, Ui.MUT, false));
        r.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        r.addView(Ui.icon(c, "down", 16, Ui.MUT));
        r.setOnClickListener(v -> go.run());
        return r;
    }

    interface StrCb { void on(String v); }
    interface BoolCb { void on(boolean v); }

    private View seg(final String[][] opts, String current, final StrCb cb) {
        final LinearLayout box = Ui.hbox(c);
        box.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
        for (int i = 0; i < opts.length; i++) {
            final int idx = i;
            TextView ch = Ui.chip(c, opts[i][1], opts[i][0].equals(current));
            ch.setOnClickListener(v -> {
                for (int k = 0; k < box.getChildCount(); k++) {
                    TextView t = (TextView) box.getChildAt(k);
                    boolean on = k == idx;
                    t.setTextColor(on ? 0xFFFFFFFF : Ui.TXT);
                    t.setBackground(Ui.ripple(on ? Ui.shape(Ui.ACC, 20, 0) : Ui.shape(Ui.CARD, 20, Ui.LINE)));
                }
                cb.on(opts[idx][0]);
            });
            box.addView(ch);
        }
        return box;
    }

    private View toggle(String label, String sub, boolean on, final BoolCb cb) {
        LinearLayout r = Ui.hbox(c);
        LinearLayout t = Ui.vbox(c);
        t.addView(Ui.text(c, label, 14.5f, Ui.TXT, true));
        t.addView(Ui.text(c, sub, 12, Ui.MUT, false));
        r.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        Switch sw = new Switch(c);
        sw.setChecked(on);
        sw.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.ACC, 0xFFB0B7C3}));
        sw.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{(Ui.ACC & 0x00FFFFFF) | 0x66000000, 0x44808896}));
        sw.setOnCheckedChangeListener((b, v) -> cb.on(v));
        r.addView(sw);
        return r;
    }
}

/** Tests each function and table the app depends on and shows exactly what is reachable. */
final class DiagnosticsScreen extends Screen {
    private LinearLayout list;
    private TextView summary;
    private final List<String> report = new ArrayList<>();
    private int okCount, total, gen;

    @Override String title() { return "Connection check"; }

    @Override View build() {
        LinearLayout l = col();
        summary = Ui.text(c, "Testing\u2026", 14, Ui.MUT, true);
        l.addView(summary, Ui.lp(Ui.WRAP, Ui.WRAP, 2, 0, 0, 10));
        list = Ui.vbox(c);
        l.addView(list);
        LinearLayout btns = Ui.hbox(c);
        TextView again = Ui.button(c, "Run again", 1), copy = Ui.button(c, "Copy report", 0);
        for (TextView t : new TextView[]{again, copy}) { LinearLayout.LayoutParams p = Ui.lp(0, Ui.WRAP, 4, 6, 4, 10); p.weight = 1f; t.setLayoutParams(p); btns.addView(t); }
        again.setOnClickListener(v -> run());
        copy.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder("360 connection check\n" + okCount + " of " + total + " OK\n\n");
            for (String line : report) sb.append(line).append('\n');
            TranslatorScreen.copyText(c, sb.toString());
            toast("Report copied");
        });
        l.addView(btns);
        l.addView(Ui.text(c, "Database schema in use: " + Api.SCHEMA + ". Anything marked Not found means that function or table does not exist on the server this app is talking to. Blocked on a table means it needs you to be signed in.", 12, Ui.MUT, false));
        run();
        return scroll(l);
    }

    private static final class Probe { String name, kind; String method = "GET", path; JSONObject body; boolean needsAuth, optional; }

    private static Probe fn(String name, String method, String query, JSONObject body, boolean optional) {
        Probe p = new Probe(); p.name = name; p.kind = "function"; p.method = method; p.path = name + (query == null ? "" : "?" + query); p.body = body; p.optional = optional; return p;
    }
    private static Probe table(String t) { Probe p = new Probe(); p.name = t; p.kind = "table"; p.path = t + "?select=*&limit=1"; p.needsAuth = true; return p; }

    private void run() {
        final int my = ++gen;
        list.removeAllViews(); report.clear(); okCount = 0; total = 0;
        summary.setText("Testing\u2026");
        final List<Probe> probes = new ArrayList<>();
        try {
            probes.add(fn("search", "POST", null, new JSONObject().put("q", "test").put("tab", "web"), false));
            probes.add(fn("autocomplete", "GET", "q=test", null, false));
            probes.add(fn("image-search", "GET", "q=cat&start=1&safe=active", null, true));
            probes.add(fn("ddg-instant", "GET", "q=cat", null, true));
            probes.add(fn("web-viewer", "GET", "url=" + Http.enc("https://example.com"), null, false));
            probes.add(fn("stock-data", "GET", "symbol=AAPL&range=1d", null, false));
            probes.add(fn("dynamic-endpoint", "POST", null, new JSONObject().put("text", "hello").put("from", "English").put("to", "Spanish"), false));
            probes.add(fn("cse-search", "GET", "q=test", null, true));
        } catch (Exception ignored) { }
        for (String t : new String[]{"profiles", "inbox_readable", "notes", "docs", "todo_lists", "todo_items", "drawings", "servers", "server_members", "channels", "messages", "direct_messages", "dm_participants", "dm_messages"}) probes.add(table(t));

        Http.POOL.execute(() -> {
            try { Auth.refreshIfNeeded(); } catch (Exception ignored) { }
            // Google CSE directly (what search and images rely on)
            String cse = cseProbe();
            post(my, "Google CSE (web + images)", "service", cse, cse.equals("OK"));
            for (Probe p : probes) {
                if (p.needsAuth && !Auth.signedIn()) { post(my, p.name, p.kind, "Skipped: sign in to test this table", true); continue; }
                String detail; boolean ok;
                try {
                    String url = Api.SB + (p.kind.equals("function") ? "/functions/v1/" : "/rest/v1/") + p.path;
                    Map<String, String> hd = Api.headers(p.body != null);
                    if (p.kind.equals("table")) Api.profile(hd);
                    Http.Resp r = Http.request(p.method, url, hd, p.body == null ? null : p.body.toString());
                    ok = Diag.ok(r.code); detail = Diag.detail(r.code, r.body);
                } catch (Exception e) { ok = false; detail = "No connection"; }
                post(my, p.name, p.kind + (p.optional ? ", optional" : ""), detail, ok || (p.optional && detail.startsWith("Not found")));
            }
            Http.MAIN.post(() -> { if (alive && my == gen) summary.setText(okCount + " of " + total + " reachable" + (okCount == total ? ". Everything the app needs is available." : ". Items in red need attention.")); });
        });
    }

    private String cseProbe() {
        try { Cse.search("test", 0, false); return "OK"; } catch (Exception e) { return "Failed: " + (e.getMessage() == null ? "unknown" : e.getMessage()); }
    }

    private void post(final int my, final String name, final String kind, final String detail, final boolean good) {
        Http.MAIN.post(() -> {
            if (!alive || my != gen) return;
            total++; if (good && !detail.startsWith("Skipped")) okCount++;
            report.add((good ? "[ok]   " : "[FAIL] ") + kind + " " + name + " - " + detail);
            LinearLayout r = Ui.hbox(c);
            r.setBackground(Ui.shape(Ui.CARD, 12, Ui.LINE));
            r.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
            r.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));
            boolean skipped = detail.startsWith("Skipped");
            r.addView(Ui.icon(c, good ? "check" : skipped ? "clock" : "alert", 18, good ? Ui.OK : skipped ? Ui.MUT : Ui.BAD), Ui.lp(Ui.dp(18), Ui.dp(18), 0, 0, 10, 0));
            LinearLayout t = Ui.vbox(c);
            t.addView(Ui.text(c, name, 14, Ui.TXT, true));
            t.addView(Ui.text(c, kind + "  \u2022  " + detail, 12, good ? Ui.MUT : skipped ? Ui.MUT : Ui.BAD, false));
            r.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            list.addView(r);
            summary.setText("Testing\u2026 " + total + " done");
        });
    }
}
