package com.search360.app;

import android.text.Html;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

final class Fmt {
    /** "2026-09-30T12:34:56.123+00:00" -> "5m", "3h", "Sep 30". */
    static String rel(String iso) {
        Date d = parse(iso);
        if (d == null) return "";
        long diff = System.currentTimeMillis() - d.getTime();
        if (diff < 60000) return "now";
        if (diff < 3600000) return (diff / 60000) + "m";
        if (diff < 86400000) return (diff / 3600000) + "h";
        if (diff < 604800000) return (diff / 86400000) + "d";
        return new SimpleDateFormat("MMM d", Locale.getDefault()).format(d);
    }
    static String full(String iso) {
        Date d = parse(iso);
        return d == null ? "" : new SimpleDateFormat("EEE, MMM d, yyyy h:mm a", Locale.getDefault()).format(d);
    }
    static Date parse(String iso) {
        try {
            if (iso == null || iso.length() < 19) return null;
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(iso.substring(0, 19));
        } catch (Exception e) { return null; }
    }
    static String initials(String s) {
        String t = s == null ? "" : s.replaceAll("<.*?>", "").trim();
        int at = t.indexOf('@'); if (at > 0) t = t.substring(0, at);
        return t.isEmpty() ? "?" : t.substring(0, Math.min(2, t.length())).toUpperCase(Locale.US);
    }
}

final class Mail {
    /** Same rule as the website: profile.mail_address, else username@360-search.com, else the login email. */
    static String resolveAddress(String mailAddress, String username, String loginEmail) {
        if (mailAddress != null && !mailAddress.trim().isEmpty() && !mailAddress.equals("null")) return mailAddress.trim();
        if (username != null && !username.trim().isEmpty() && !username.equals("null")) return username.trim().toLowerCase(java.util.Locale.US).replaceAll("\\s+", "") + "@360-search.com";
        return loginEmail == null || loginEmail.isEmpty() ? null : loginEmail.toLowerCase(java.util.Locale.US);
    }

    /** The signed-in user's mailbox address (cached). */
    static void address(final Screen s, final Http.Cb<String> cb) {
        final String cached = Store.get("mailAddr", null);
        if (cached != null && !cached.isEmpty()) { cb.done(cached, null); return; }
        s.async(() -> {
            String ma = null, un = null;
            try {
                JSONArray r = Api.rest("GET", "profiles?select=mail_address,username&id=eq." + Http.enc(Auth.userId) + "&limit=1", null, true);
                if (r.length() > 0) { JSONObject p = r.getJSONObject(0); ma = J.s(p, "mail_address"); un = J.s(p, "username"); }
            } catch (Exception ignored) { /* profile not readable: fall back to the login email like the website */ }
            String a = resolveAddress(ma, un, Auth.email);
            if (a != null) Store.put("mailAddr", a);
            return a;
        }, cb);
    }
    static boolean encrypted(JSONObject m) {
        for (String k : new String[]{"subject", "body_html", "body_text"}) {
            String v = m.optString(k, "");
            if (v.startsWith("e2ee:") || v.startsWith("e2ee2:")) return true;
        }
        return false;
    }
    static final Pattern PHISH = Pattern.compile("(?i)(verify.{0,20}(account|identity|password)|unusual.{0,20}(sign.?in|activity)|(suspended|disabled|limited).{0,20}account|congratulations.{0,30}(won|prize|lottery)|click here.{0,20}(confirm|verify|update)|urgent.{0,20}(action|response))");
    static final Pattern EXEC = Pattern.compile("(?i)\\.(exe|scr|bat|cmd|com|vbs|js|jar|msi|ps1|dmg|pkg|sh|apk)$");
    /** 0 safe, 1 suspicious, 2 dangerous. */
    static int threat(JSONObject m) {
        if (m.optBoolean("virus_detected")) return 2;
        int score = 0;
        String text = J.s(m, "subject") + " " + J.s(m, "body_text");
        if (PHISH.matcher(text).find()) score += 30;
        JSONArray at = m.optJSONArray("attachments");
        if (at != null) for (int i = 0; i < at.length(); i++) { JSONObject a = at.optJSONObject(i); if (a != null && EXEC.matcher(J.s(a, "filename")).find()) score += 60; }
        if (m.optBoolean("spf_fail") || m.optBoolean("dkim_fail") || m.optBoolean("dmarc_fail")) score += 25;
        return score >= 60 ? 2 : score >= 25 ? 1 : 0;
    }
}

final class MailScreen extends Screen {
    private FrameLayout holder;
    private LinearLayout list;
    private final List<JSONObject> all = new ArrayList<>();
    private String folder = "inbox", addr;
    private EditText query;
    private boolean loaded;

    @Override View build() {
        holder = new FrameLayout(c);
        render();
        return holder;
    }
    @Override void onAuth() { if (holder != null) { loaded = false; Store.remove("mailAddr"); render(); } }
    @Override void onShow() { if (Auth.signedIn() && loaded) load(false); }

    private void render() {
        holder.removeAllViews();
        if (!Auth.signedIn()) {
            LinearLayout l = col();
            l.addView(AuthView.build(this, "Sign in to open your 360Mail inbox."));
            holder.addView(scroll(l));
            a.setMailBadge(0);
            return;
        }
        LinearLayout root = Ui.vbox(c);
        LinearLayout top = Ui.hbox(c);
        top.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(10), Ui.dp(4));
        final String[][] f = {{"inbox", "Inbox"}, {"sent", "Sent"}, {"starred", "Starred"}, {"scheduled", "Scheduled"}};
        final LinearLayout chips = Ui.hbox(c);
        for (final String[] x : f) {
            TextView ch = Ui.chip(c, x[1], x[0].equals(folder));
            ch.setOnClickListener(v -> {
                folder = x[0];
                for (int k = 0; k < chips.getChildCount(); k++) { TextView t = (TextView) chips.getChildAt(k); boolean on = f[k][0].equals(folder); t.setTextColor(on ? 0xFFFFFFFF : Ui.TXT); t.setBackground(Ui.ripple(on ? Ui.shape(Ui.ACC, 20, 0) : Ui.shape(Ui.CARD, 20, Ui.LINE))); }
                paint();
            });
            chips.addView(ch);
        }
        android.widget.HorizontalScrollView chipScroll = new android.widget.HorizontalScrollView(c);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.addView(chips);
        top.addView(chipScroll, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        top.addView(Ui.iconButton(c, "refresh", v -> load(true)));
        root.addView(top);
        query = Ui.input(c, "Search mail");
        query.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 14, 4, 14, 6));
        query.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence x, int a1, int b1, int c1) { }
            public void onTextChanged(CharSequence x, int a1, int b1, int c1) { if (loaded) paint(); }
            public void afterTextChanged(android.text.Editable x) { }
        });
        root.addView(query);
        list = Ui.vbox(c);
        list.setPadding(Ui.dp(10), 0, Ui.dp(10), Ui.dp(90));
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.setFillViewport(true);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        holder.addView(root);

        FrameLayout fab = new FrameLayout(c);
        fab.setBackground(Ui.ripple(Ui.gradient(28)));
        fab.setClickable(true);
        fab.setContentDescription("Compose");
        fab.addView(Ui.icon(c, "edit", 22, 0xFFFFFFFF), new FrameLayout.LayoutParams(Ui.dp(22), Ui.dp(22), Gravity.CENTER));
        fab.setElevation(Ui.dp(6));
        fab.setOnClickListener(v -> a.push(new ComposeScreen("", "", "")));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(Ui.dp(56), Ui.dp(56), Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, Ui.dp(18), Ui.dp(18));
        holder.addView(fab, fp);
        load(true);
    }

    private void load(boolean showSpinner) {
        if (showSpinner || !loaded) { list.removeAllViews(); list.addView(Ui.loading(c)); }
        Mail.address(this, (ad, e) -> {
            if (ad == null) {
                list.removeAllViews();
                list.addView(errorState(e != null ? "Couldn't load your mailbox" : "No mailbox found", e != null ? msg(e) : "Your account has no email address on file."));
                return;
            }
            addr = ad;
            async(() -> Api.rest("GET", "inbox_readable?select=*&owner_email=eq." + Http.enc(addr) + "&order=received_at.desc&limit=100", null, true), (rows, err) -> {
                if (err != null) { list.removeAllViews(); list.addView(errorState("Couldn't load mail", msg(err))); return; }
                all.clear();
                for (int i = 0; i < rows.length(); i++) all.add(rows.optJSONObject(i));
                loaded = true;
                paint();
            });
        });
    }

    private void paint() {
        list.removeAllViews();
        int unread = 0;
        for (JSONObject m : all) if (!m.optBoolean("read") && "in".equals(J.s(m, "direction"))) unread++;
        a.setMailBadge(unread);
        int shown = 0;
        for (final JSONObject m : all) {
            boolean in = "in".equals(J.s(m, "direction")), sched = "scheduled".equals(J.s(m, "status"));
            boolean ok = folder.equals("inbox") ? in : folder.equals("sent") ? (!in && !sched) : folder.equals("scheduled") ? (!in && sched) : m.optBoolean("starred");
            if (!ok) continue;
            if (!matches(m)) continue;
            list.addView(row(m, in));
            shown++;
        }
        if (shown == 0) list.addView(Ui.state(c, folder.equals("starred") ? "star" : "inbox", query != null && query.getText().length() > 0 ? "No matches" : folder.equals("inbox") ? "Inbox is empty" : "Nothing here", folder.equals("inbox") ? "New messages appear here." : ""));
    }

    private boolean matches(JSONObject m) {
        String q = query == null ? "" : query.getText().toString().trim().toLowerCase(java.util.Locale.US);
        if (q.isEmpty() || Mail.encrypted(m)) return q.isEmpty();
        return (J.s(m, "subject") + " " + J.s(m, "from_addr") + " " + J.s(m, "to_addr") + " " + J.s(m, "body_text")).toLowerCase(java.util.Locale.US).contains(q);
    }

    private View errorState(String title, String msg) {
        LinearLayout l = Ui.vbox(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.addView(Ui.state(c, "alert", title, msg));
        TextView retry = Ui.button(c, "Try again", 1);
        retry.setLayoutParams(Ui.lp(Ui.WRAP, Ui.WRAP, 0, 0, 0, 0));
        retry.setOnClickListener(v -> { Store.remove("mailAddr"); load(true); });
        l.addView(retry);
        return l;
    }

    private View row(final JSONObject m, boolean in) {
        boolean unread = !m.optBoolean("read") && in;
        boolean enc = Mail.encrypted(m);
        LinearLayout r = Ui.hbox(c);
        r.setGravity(Gravity.TOP);
        r.setPadding(Ui.dp(6), Ui.dp(12), Ui.dp(6), Ui.dp(12));
        r.setBackground(Ui.ripple(null));
        r.setClickable(true);
        String who = in ? J.s(m, "from_addr") : J.s(m, "to_addr");
        TextView av = Ui.text(c, Fmt.initials(who), 14, 0xFFFFFFFF, true);
        av.setGravity(Gravity.CENTER);
        av.setBackground(Ui.gradient(20));
        r.addView(av, Ui.lp(Ui.dp(40), Ui.dp(40), 0, 0, 12, 0));
        LinearLayout b = Ui.vbox(c);
        LinearLayout t = Ui.hbox(c);
        TextView from = Ui.text(c, who, 14.5f, Ui.TXT, true);
        from.setSingleLine(true); from.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (!unread) from.setTypeface(android.graphics.Typeface.DEFAULT);
        t.addView(from, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        if (m.optBoolean("starred")) t.addView(Ui.icon(c, "star-fill", 14, 0xFFF59E0B), Ui.lp(Ui.dp(14), Ui.dp(14), 4, 0, 4, 0));
        t.addView(Ui.text(c, Fmt.rel(J.s(m, "received_at")), 11.5f, Ui.MUT, false));
        b.addView(t);
        String subj = enc ? "Encrypted message" : Auth.firstNonEmpty(J.s(m, "subject"), "(no subject)");
        TextView sj = Ui.text(c, subj, 13.5f, Ui.TXT, unread);
        sj.setSingleLine(true); sj.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.addView(sj);
        String pv = enc ? "End-to-end encrypted" : Rows.stripTags(Auth.firstNonEmpty(J.s(m, "body_text"), J.s(m, "body_html")));
        TextView p = Ui.text(c, pv, 12.5f, Ui.MUT, false);
        p.setSingleLine(true); p.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.addView(p);
        r.addView(b, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        r.setOnClickListener(v -> a.push(new MailReadScreen(m, this)));
        if (unread) { View dot = new View(c); dot.setBackground(Ui.shape(Ui.ACC, 4, 0)); r.addView(dot, Ui.lp(Ui.dp(8), Ui.dp(8), 8, 6, 0, 0)); }
        return r;
    }

    void changed() { if (list != null) paint(); }
    void remove(JSONObject m) { all.remove(m); changed(); }
}

final class MailReadScreen extends Screen {
    private final JSONObject m;
    private final MailScreen owner;
    MailReadScreen(JSONObject m, MailScreen owner) { this.m = m; this.owner = owner; }
    @Override String title() { return "Message"; }

    @Override View[] actions() {
        final FrameLayout star = Ui.iconButton(a, m.optBoolean("starred") ? "star-fill" : "star", null);
        star.setOnClickListener(v -> toggleStar(star));
        FrameLayout del = Ui.iconButton(a, "trash", v -> confirmDelete());
        return new View[]{star, del};
    }

    private void toggleStar(final FrameLayout btn) {
        final boolean now = !m.optBoolean("starred");
        try { m.put("starred", now); } catch (Exception ignored) { }
        ((android.widget.ImageView) btn.getChildAt(0)).setImageDrawable(Icons.drawable(c, now ? "star-fill" : "star", now ? 0xFFF59E0B : Ui.TXT));
        owner.changed();
        async(() -> Api.rest("PATCH", "inbox?id=eq." + Http.enc(J.s(m, "id")), new JSONObject().put("starred", now), false), (r, e) -> { if (e != null) toast("Couldn't update star."); });
    }

    private void confirmDelete() {
        new android.app.AlertDialog.Builder(a).setTitle("Delete message?").setMessage("This permanently removes the message.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", (d, w) -> async(() -> Api.rest("DELETE", "inbox?id=eq." + Http.enc(J.s(m, "id")), null, false), (r, e) -> {
                if (e != null) { toast("Couldn't delete."); return; }
                owner.remove(m);
                a.onBackPressed();
            })).show();
    }

    @Override View build() {
        LinearLayout l = col();
        final boolean in = "in".equals(J.s(m, "direction"));
        final boolean enc = Mail.encrypted(m);
        TextView subj = Ui.text(c, enc ? "Encrypted message" : Auth.firstNonEmpty(J.s(m, "subject"), "(no subject)"), 21, Ui.TXT, true);
        subj.setLineSpacing(0, 1.1f);
        l.addView(subj);
        LinearLayout who = Ui.hbox(c);
        who.setPadding(0, Ui.dp(14), 0, Ui.dp(14));
        TextView av = Ui.text(c, Fmt.initials(in ? J.s(m, "from_addr") : J.s(m, "to_addr")), 15, 0xFFFFFFFF, true);
        av.setGravity(Gravity.CENTER); av.setBackground(Ui.gradient(22));
        who.addView(av, Ui.lp(Ui.dp(44), Ui.dp(44), 0, 0, 12, 0));
        LinearLayout nm = Ui.vbox(c);
        nm.addView(Ui.text(c, in ? J.s(m, "from_addr") : "To: " + J.s(m, "to_addr"), 14.5f, Ui.TXT, true));
        nm.addView(Ui.text(c, Fmt.full(J.s(m, "received_at")), 12, Ui.MUT, false));
        who.addView(nm);
        l.addView(who);

        LinearLayout pills = Ui.hbox(c);
        int th = Mail.threat(m);
        if (enc) pills.addView(pill("Encrypted", true));
        if (th == 2) pills.addView(pill("Dangerous: do not open links", false));
        else if (th == 1) pills.addView(pill("Suspicious content", false));
        if (m.optBoolean("self_destruct")) pills.addView(pill("Self-destructs after reading", false));
        if (pills.getChildCount() > 0) l.addView(pills, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 0, 0, 12));

        if (enc) {
            l.addView(Ui.state(c, "lock", "Encrypted on another device", "This message is end-to-end encrypted with a key that lives on the device where you set up encryption. Open it there to read it."));
        } else {
            String html = J.s(m, "body_html"), text = J.s(m, "body_text");
            TextView body = Ui.text(c, "", 15.5f, Ui.TXT, false);
            body.setLineSpacing(0, 1.35f);
            body.setLinkTextColor(Ui.ACC);
            if (!html.isEmpty() && !"null".equals(html)) {
                HtmlReader.Result res = HtmlReader.extract("<body>" + html + "</body>", "https://360-search.com/");
                body.setText(Html.fromHtml(res.textLen > 0 ? res.html : Html.escapeHtml(text)));
            } else body.setText(text.isEmpty() || "null".equals(text) ? "No message body." : text);
            body.setMovementMethod(LinkMovementMethod.getInstance());
            l.addView(body);
            l.addView(Ui.text(c, "Remote images are never loaded, so senders can't track when you open this.", 11.5f, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 16, 0, 0));
            JSONArray at = m.optJSONArray("attachments");
            if (at != null && at.length() > 0) {
                l.addView(Ui.label(c, "Attachments"));
                for (int i = 0; i < at.length(); i++) { JSONObject x = at.optJSONObject(i); if (x != null) l.addView(Ui.text(c, J.s(x, "filename") + (Mail.EXEC.matcher(J.s(x, "filename")).find() ? "  (executable: be careful)" : ""), 13.5f, Ui.TXT, false)); }
                l.addView(Ui.text(c, "Download attachments from the 360 website.", 11.5f, Ui.MUT, false));
            }
        }
        LinearLayout acts = Ui.hbox(c);
        acts.setPadding(0, Ui.dp(20), 0, 0);
        TextView reply = Ui.button(c, "Reply", 1), fwd = Ui.button(c, "Forward", 1);
        ((LinearLayout.LayoutParams) reply.getLayoutParams()).weight = 1f; ((LinearLayout.LayoutParams) reply.getLayoutParams()).width = 0; ((LinearLayout.LayoutParams) reply.getLayoutParams()).rightMargin = Ui.dp(8);
        ((LinearLayout.LayoutParams) fwd.getLayoutParams()).weight = 1f; ((LinearLayout.LayoutParams) fwd.getLayoutParams()).width = 0;
        final String subjText = J.s(m, "subject");
        reply.setOnClickListener(v -> a.push(new ComposeScreen(in ? J.s(m, "from_addr") : J.s(m, "to_addr"), "Re: " + subjText, "")));
        fwd.setOnClickListener(v -> a.push(new ComposeScreen("", "Fwd: " + subjText, "\n\n--- Forwarded ---\nFrom: " + J.s(m, "from_addr") + "\n\n" + J.s(m, "body_text"))));
        if (!enc) { acts.addView(reply); acts.addView(fwd); l.addView(acts); }

        if (in && m.optBoolean("self_destruct")) {
            l.addView(Ui.text(c, "This message was set to self-destruct. It has been removed from your mailbox and will disappear when you leave.", 12, Ui.WARN, true), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 16, 0, 0));
            async(() -> Api.rest("DELETE", "inbox?id=eq." + Http.enc(J.s(m, "id")), null, false), (r, e) -> { if (e == null) owner.remove(m); });
        }
        if (in && !m.optBoolean("read")) {
            try { m.put("read", true); } catch (Exception ignored) { }
            owner.changed();
            async(() -> Api.rest("PATCH", "inbox?id=eq." + Http.enc(J.s(m, "id")), new JSONObject().put("read", true), false), (r, e) -> { });
        }
        return scroll(l);
    }

    private View pill(String s, boolean ok) {
        TextView t = Ui.text(c, s, 11.5f, ok ? Ui.OK : Ui.WARN, true);
        t.setBackground(Ui.shape(ok ? 0x2216A34A : 0x22D97706, 12, 0));
        t.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), Ui.dp(4));
        t.setLayoutParams(Ui.lp(Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        return t;
    }
}

final class ComposeScreen extends Screen {
    private final String to0, subj0, body0;
    ComposeScreen(String to, String subj, String body) { to0 = to; subj0 = subj; body0 = body; }
    @Override String title() { return "New message"; }

    @Override View build() {
        LinearLayout l = col();
        final EditText to = Ui.input(c, "To");
        to.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        to.setText(to0);
        final EditText sub = Ui.input(c, "Subject");
        sub.setText(subj0);
        final EditText body = Ui.input(c, "Write your message");
        body.setSingleLine(false); body.setMinLines(8); body.setGravity(Gravity.TOP);
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setText(body0);
        l.addView(to); l.addView(sub); l.addView(body);
        l.addView(Ui.text(c, "Messages sent from the app are not end-to-end encrypted yet. Use the 360 website for encrypted mail to other 360 users.", 11.5f, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 0, 0, 12));
        final TextView err = Ui.text(c, "", 13, Ui.BAD, false);
        final TextView send = Ui.button(c, "Send", 0);
        send.setOnClickListener(v -> {
            final String t = to.getText().toString().trim(), s = sub.getText().toString().trim(), b = body.getText().toString();
            if (!t.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) { err.setText("Enter a valid recipient address."); return; }
            if (s.isEmpty() && b.trim().isEmpty()) { err.setText("Add a subject or a message."); return; }
            err.setText(""); send.setEnabled(false); send.setText("Sending\u2026"); a.hideKeyboard();
            async(() -> {
                String html = Html.escapeHtml(b).replace("\n", "<br>");
                return Api.fn("send-email", "POST", null, new JSONObject().put("to", t).put("subject", s).put("html", html).put("text", b).put("e2ee", false).put("attachments", new JSONArray()));
            }, (j, e) -> {
                send.setEnabled(true); send.setText("Send");
                if (e != null) { err.setText(msg(e)); return; }
                toast("Message sent");
                a.onBackPressed();
            });
        });
        l.addView(send);
        l.addView(err);
        return scroll(l);
    }
}
