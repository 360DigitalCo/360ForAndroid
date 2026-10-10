package com.search360.app;

import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Same profanity filter the website applies before a message is sent (word list + leetspeak + separators). */
final class ChatFilter {
    private static final Pattern[] PATTERNS;
    static {
        String[] words = {"fuck", "shit", "bitch", "cunt", "bastard"};
        String[] extra = {"\\bd[i1!][ck]+\\b", "\\bn[i1!][g9]{2}[e3]r\\b", "\\bn[i1!][g9]{2}[a4]\\b", "\\bf[a4][g9]{2}[o0][t7]\\b", "\\bputa\\b", "\\bkurwa\\b", "\\bmerda\\b", "\\bcazzo\\b"};
        PATTERNS = new Pattern[words.length + extra.length];
        int i = 0;
        for (String w : words) PATTERNS[i++] = Pattern.compile("(?<![a-z])" + leet(w) + "(?![a-z])", Pattern.CASE_INSENSITIVE);
        for (String e : extra) PATTERNS[i++] = Pattern.compile(e, Pattern.CASE_INSENSITIVE);
    }
    private static String leet(String w) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.length(); i++) {
            String cls;
            switch (w.charAt(i)) {
                case 'a': cls = "[a4@]"; break; case 'b': cls = "[b8]"; break; case 'c': cls = "[ck(]"; break; case 'e': cls = "[e3]"; break;
                case 'g': cls = "[g9]"; break; case 'h': cls = "[h#]"; break; case 'i': cls = "[i1!|]"; break; case 'k': cls = "[kc]"; break;
                case 'l': cls = "[l1|]"; break; case 'o': cls = "[o0]"; break; case 's': cls = "[s5$]"; break; case 't': cls = "[t7+]"; break;
                case 'u': cls = "[uv]"; break; case 'v': cls = "[vu]"; break;
                default: cls = String.valueOf(w.charAt(i));
            }
            if (i > 0) sb.append("[\\s_.\\-*]*");
            sb.append(cls);
        }
        return sb.toString();
    }
    static String filter(String t) {
        if (t == null || t.isEmpty()) return t;
        String o = t;
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(o);
            StringBuffer sb = new StringBuffer();
            while (m.find()) { StringBuilder st = new StringBuilder(); for (int i = 0; i < m.group().length(); i++) st.append('*'); m.appendReplacement(sb, Matcher.quoteReplacement(st.toString())); }
            m.appendTail(sb);
            o = sb.toString();
        }
        return o;
    }
}

final class Chat {
    static JSONObject me;           // cached profile row

    /** Null if the user may post, otherwise the reason (mirrors the website's checks). */
    static String blockReason(JSONObject p) {
        if (p == null) return null;
        if (p.optBoolean("banned")) return "Your account is banned from chat.";
        String mu = J.s(p, "muted_until");
        java.util.Date d = Fmt.parse(mu);
        if (d != null && d.getTime() > System.currentTimeMillis()) return "You are muted until " + Fmt.full(mu) + ".";
        if (p.has("age_verified") && !p.isNull("age_verified") && !p.optBoolean("age_verified", true)) return "Verify your age on the 360 website to use chat.";
        return null;
    }

    static void profile(final Screen s, final Http.Cb<JSONObject> cb) {
        if (me != null) { cb.done(me, null); return; }
        s.async(() -> {
            JSONArray r = Api.rest("GET", "profiles?select=*&id=eq." + Http.enc(Auth.userId) + "&limit=1", null, true);
            JSONObject p = r.length() > 0 ? r.getJSONObject(0) : new JSONObject();
            me = p;
            return p;
        }, cb);
    }
    static String displayName(JSONObject p) {
        String n = J.s(p, "username");
        if (n.isEmpty() && Auth.email != null) n = Auth.email.contains("@") ? Auth.email.substring(0, Auth.email.indexOf('@')) : Auth.email;
        return n.isEmpty() ? "user" : n;
    }
}

final class ChatHomeScreen extends Screen {
    private FrameLayout holder;
    @Override String title() { return "Chat"; }
    @Override View build() { holder = new FrameLayout(c); render(); return holder; }
    @Override void onAuth() { Chat.me = null; if (holder != null) render(); }

    private void render() {
        holder.removeAllViews();
        LinearLayout l = col();
        if (!Auth.signedIn()) { l.addView(AuthView.build(this, "Sign in to join conversations.")); holder.addView(scroll(l)); return; }
        l.addView(roomRow("General", "Everyone on 360", "hash", () -> a.push(new ChatRoomScreen("General", null, null))));
        final LinearLayout list = Ui.vbox(c);
        l.addView(Ui.label(c, "Your servers"));
        l.addView(list);
        list.addView(Ui.loading(c));
        holder.addView(scroll(l));
        async(() -> {
            JSONArray mem = Api.rest("GET", "server_members?select=server_id&user_id=eq." + Http.enc(Auth.userId), null, true);
            if (mem.length() == 0) return new JSONArray();
            StringBuilder ids = new StringBuilder();
            for (int i = 0; i < mem.length(); i++) { if (i > 0) ids.append(','); ids.append(mem.getJSONObject(i).getString("server_id")); }
            return Api.rest("GET", "servers?select=*&id=in.(" + ids + ")", null, true);
        }, (rows, e) -> {
            list.removeAllViews();
            if (e != null) { list.addView(Ui.state(c, "alert", "Couldn't load servers", msg(e))); return; }
            if (rows.length() == 0) { list.addView(Ui.state(c, "users", "No servers yet", "Join or create servers on the 360 website. They appear here.")); return; }
            for (int i = 0; i < rows.length(); i++) {
                final JSONObject o = rows.optJSONObject(i);
                if (o == null) continue;
                final String name = J.s(o, "name", "Server"), id = J.s(o, "id");
                list.addView(roomRow(name, J.s(o, "description"), "users", () -> a.push(new ServerScreen(id, name))));
            }
        });
    }

    private View roomRow(String name, String sub, String icon, final Runnable go) {
        LinearLayout r = Ui.hbox(c);
        r.setBackground(Ui.ripple(Ui.shape(Ui.CARD, 14, Ui.LINE)));
        r.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        r.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 9));
        r.setClickable(true);
        TextView av = Ui.text(c, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.US), 17, 0xFFFFFFFF, true);
        av.setGravity(Gravity.CENTER); av.setBackground(Ui.shape(ResultUi.avatarColor(name), 14, 0));
        r.addView(av, Ui.lp(Ui.dp(42), Ui.dp(42), 0, 0, 12, 0));
        LinearLayout t = Ui.vbox(c);
        t.addView(Ui.text(c, name, 15.5f, Ui.TXT, true));
        if (!sub.isEmpty()) t.addView(Rows.clamp(Ui.text(c, sub, 12.5f, Ui.MUT, false), 1));
        r.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        r.addView(Ui.icon(c, icon, 18, Ui.MUT));
        r.setOnClickListener(v -> go.run());
        return r;
    }
}

final class ServerScreen extends Screen {
    private final String id, name;
    ServerScreen(String id, String name) { this.id = id; this.name = name; }
    @Override String title() { return name; }
    @Override View build() {
        LinearLayout l = col();
        final LinearLayout list = Ui.vbox(c);
        l.addView(list);
        list.addView(Ui.loading(c));
        async(() -> Api.rest("GET", "channels?select=*&server_id=eq." + Http.enc(id), null, true), (rows, e) -> {
            list.removeAllViews();
            if (e != null) { list.addView(Ui.state(c, "alert", "Couldn't load channels", msg(e))); return; }
            List<JSONObject> chans = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) { JSONObject o = rows.optJSONObject(i); if (o != null && !J.s(o, "type").equalsIgnoreCase("voice")) chans.add(o); }
            Collections.sort(chans, new Comparator<JSONObject>() {
                @Override public int compare(JSONObject x, JSONObject y) {
                    int c1 = Double.compare(x.optDouble("position", 1e9), y.optDouble("position", 1e9));
                    return c1 != 0 ? c1 : J.s(x, "name").compareToIgnoreCase(J.s(y, "name"));
                }
            });
            if (chans.isEmpty()) { a.push(new ChatRoomScreen(name, null, id)); return; }   // servers without channels chat at server level
            for (final JSONObject o : chans) {
                LinearLayout r = Ui.hbox(c);
                r.setBackground(Ui.ripple(Ui.shape(Ui.CARD, 14, Ui.LINE)));
                r.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
                r.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
                r.setClickable(true);
                r.addView(Ui.icon(c, "hash", 18, Ui.ACC), Ui.lp(Ui.dp(18), Ui.dp(18), 0, 0, 12, 0));
                r.addView(Ui.text(c, J.s(o, "name", "channel"), 15.5f, Ui.TXT, true));
                r.setOnClickListener(v -> a.push(new ChatRoomScreen("#" + J.s(o, "name", "channel"), J.s(o, "id"), id)));
                list.addView(r);
            }
        });
        return scroll(l);
    }
}

final class ChatRoomScreen extends Screen {
    private final String roomTitle, channelId, serverId;
    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private ScrollView sv;
    private EditText input;
    private String lastKey = "";
    private boolean first = true, sending;
    private JSONObject profile;

    ChatRoomScreen(String title, String channelId, String serverId) { this.roomTitle = title; this.channelId = channelId; this.serverId = serverId; }
    @Override String title() { return roomTitle; }

    private final Runnable poll = new Runnable() {
        @Override public void run() { if (!alive) return; load(); h.postDelayed(this, 4000); }
    };

    @Override View build() {
        LinearLayout root = Ui.vbox(c);
        sv = new ScrollView(c);
        list = Ui.vbox(c);
        list.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(6));
        list.addView(Ui.loading(c));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        LinearLayout bar = Ui.hbox(c);
        bar.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(10));
        input = Ui.input(c, "Message " + roomTitle);
        input.setSingleLine(false); input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setLayoutParams(Ui.lp(0, Ui.WRAP, 0, 0, 8, 0));
        ((LinearLayout.LayoutParams) input.getLayoutParams()).weight = 1f;
        bar.addView(input);
        FrameLayout send = new FrameLayout(c);
        send.setBackground(Ui.ripple(Ui.gradient(23)));
        send.setClickable(true); send.setContentDescription("Send");
        send.addView(Ui.icon(c, "send", 20, 0xFFFFFFFF), new FrameLayout.LayoutParams(Ui.dp(20), Ui.dp(20), Gravity.CENTER));
        send.setOnClickListener(v -> send());
        bar.addView(send, Ui.lp(Ui.dp(46), Ui.dp(46)));
        root.addView(bar);

        Chat.profile(this, (p, e) -> { profile = p; });
        h.post(poll);
        return root;
    }

    /** Newest 60 messages for this room. Falls back to simpler filters if a column is missing. */
    private JSONArray fetch() throws Exception {
        String scope = channelId != null ? "channel_id=eq." + Http.enc(channelId)
            : serverId != null ? "server_id=eq." + Http.enc(serverId) + "&channel_id=is.null"
            : "channel_id=is.null&server_id=is.null";
        String[] attempts = {
            "messages?select=*&" + scope + "&thread_id=is.null&deleted_at=is.null&order=created_at.desc&limit=60",
            "messages?select=*&" + scope + "&deleted_at=is.null&order=created_at.desc&limit=60",
            "messages?select=*&" + scope + "&order=created_at.desc&limit=60",
        };
        Exception last = null;
        for (String path : attempts) {
            try { return Api.rest("GET", path, null, true); }
            catch (Api.ApiError e) { last = e; if (e.code != 400) throw e; }
        }
        throw last;
    }

    private void load() {
        async(() -> fetch(), (rows, e) -> {
            if (e != null) {
                if (first) { list.removeAllViews(); list.addView(Ui.state(c, "alert", "Couldn't load messages", msg(e))); }
                return;
            }
            JSONObject top = rows.length() > 0 ? rows.optJSONObject(0) : null;
            String key = rows.length() + ":" + (top == null ? "" : J.s(top, "id"));
            if (!first && key.equals(lastKey)) return;
            lastKey = key;
            boolean stick = first || sv.getScrollY() + sv.getHeight() >= list.getHeight() - Ui.dp(80);
            first = false;
            list.removeAllViews();
            if (rows.length() == 0) { list.addView(Ui.state(c, "chat", "No messages yet", "Say hello.")); return; }
            for (int i = rows.length() - 1; i >= 0; i--) if (rows.optJSONObject(i) != null) list.addView(bubble(rows.optJSONObject(i)));
            if (stick) sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
        });
    }

    private View bubble(final JSONObject m) {
        final boolean mine = Auth.userId != null && Auth.userId.equals(J.s(m, "user_id"));
        LinearLayout row = Ui.hbox(c);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, Ui.dp(6), 0, Ui.dp(6));
        String name = J.s(m, "username", "user");
        View av;
        if (!J.s(m, "avatar_url").startsWith("http")) {
            TextView t = Ui.text(c, name.substring(0, 1).toUpperCase(java.util.Locale.US), 15, 0xFFFFFFFF, true);
            t.setGravity(Gravity.CENTER); t.setBackground(Ui.shape(ResultUi.avatarColor(name), 18, 0)); av = t;
        } else {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP); iv.setBackground(Ui.shape(Ui.LINE, 18, 0)); iv.setClipToOutline(true);
            Img.load(J.s(m, "avatar_url"), iv); av = iv;
        }
        row.addView(av, Ui.lp(Ui.dp(36), Ui.dp(36), 0, 2, 10, 0));
        LinearLayout body = Ui.vbox(c);
        LinearLayout head = Ui.hbox(c);
        head.addView(Ui.text(c, name, 14, mine ? Ui.ACC : Ui.TXT, true));
        String role = J.s(m, "role");
        if (role.equals("admin") || role.equals("mod") || role.equals("moderator") || role.equals("owner")) {
            TextView rb = Ui.text(c, role.toUpperCase(java.util.Locale.US), 9.5f, Ui.WARN, true);
            rb.setBackground(Ui.shape(0x22D97706, 6, 0)); rb.setPadding(Ui.dp(6), Ui.dp(1), Ui.dp(6), Ui.dp(1));
            head.addView(rb, Ui.lp(Ui.WRAP, Ui.WRAP, 6, 0, 0, 0));
        }
        head.addView(Ui.text(c, "  " + Fmt.rel(J.s(m, "created_at")), 11.5f, Ui.MUT, false));
        body.addView(head);
        String text = J.s(m, "text");
        if (!text.isEmpty()) {
            LinearLayout md = Ui.vbox(c);
            MdView.render(this, md, text, Ui.TXT, 14.5f);
            body.addView(md);
        }
        String file = J.s(m, "file_url");
        if (file.startsWith("http") && file.matches("(?i).*\\.(png|jpe?g|gif|webp)(\\?.*)?$")) {
            ImageView iv = new ImageView(c);
            iv.setAdjustViewBounds(true); iv.setScaleType(ImageView.ScaleType.FIT_START);
            iv.setBackground(Ui.shape(Ui.LINE, 10, 0)); iv.setClipToOutline(true);
            iv.setLayoutParams(Ui.lp(Ui.dp(220), Ui.WRAP, 0, 6, 0, 0));
            iv.setMaxHeight(Ui.dp(260));
            Img.load(file, iv);
            body.addView(iv);
        } else if (file.startsWith("http")) {
            TextView f = Ui.text(c, "Attachment", 13, Ui.ACC, true);
            f.setPadding(0, Ui.dp(6), 0, 0);
            f.setOnClickListener(v -> a.openExternal(file));
            body.addView(f);
        }
        row.addView(body, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        if (mine) row.setOnLongClickListener(v -> {
            new android.app.AlertDialog.Builder(a).setItems(new String[]{"Delete message", "Copy text"}, (d, i) -> {
                if (i == 1) { TranslatorScreen.copyText(c, J.s(m, "text")); toast("Copied"); return; }
                async(() -> Api.rest("PATCH", "messages?id=eq." + Http.enc(J.s(m, "id")) + "&user_id=eq." + Http.enc(Auth.userId), new JSONObject().put("deleted_at", NoteTime.iso()), false), (r, e) -> {
                    if (e != null) toast("Couldn't delete: " + msg(e)); else { lastKey = ""; load(); }
                });
            }).show();
            return true;
        });
        return row;
    }

    private void send() {
        final String raw = input.getText().toString().trim();
        if (raw.isEmpty() || sending) return;
        if (profile == null) { toast("Still loading your profile. Try again in a moment."); return; }
        String why = Chat.blockReason(profile);
        if (why != null) { toast(why); return; }
        final String text = ChatFilter.filter(raw);
        sending = true; input.setText("");
        final JSONObject p = profile;
        async(() -> {
            JSONObject row = new JSONObject().put("user_id", Auth.userId).put("username", Chat.displayName(p))
                .put("avatar_url", p.isNull("avatar_url") ? JSONObject.NULL : J.s(p, "avatar_url", null))
                .put("tag", J.s(p, "tag", "")).put("role", J.s(p, "role", "user")).put("text", text);
            if (channelId != null) row.put("channel_id", channelId);
            if (serverId != null) row.put("server_id", serverId);
            Api.rest("POST", "messages", row, false);
            return Boolean.TRUE;
        }, (r, e) -> {
            sending = false;
            if (e != null) { input.setText(raw); toast("Couldn't send: " + msg(e)); return; }
            lastKey = ""; load();
        });
    }

    @Override void onDestroy() { h.removeCallbacks(poll); super.onDestroy(); }
}

/** Shared ISO timestamp for soft deletes. */
final class NoteTime {
    static String iso() {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(new java.util.Date());
    }
}
