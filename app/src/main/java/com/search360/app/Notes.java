package com.search360.app;

import android.text.Html;
import android.text.InputType;
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

/** Notes: same `notes` table as the website (user_id, title, body, is_html, is_favorite, deleted_at). */
final class NotesScreen extends Screen {
    private FrameLayout holder;
    private LinearLayout list;
    private EditText search;
    private final List<JSONObject> all = new ArrayList<>();
    @Override String title() { return "Notes"; }

    @Override View build() { holder = new FrameLayout(c); render(); return holder; }
    @Override void onAuth() { if (holder != null) render(); }
    @Override void onShow() { if (Auth.signedIn() && list != null) load(); }

    private void render() {
        holder.removeAllViews();
        if (!Auth.signedIn()) { LinearLayout l = col(); l.addView(AuthView.build(this, "Sign in to see your notes.")); holder.addView(scroll(l)); return; }
        LinearLayout root = Ui.vbox(c);
        search = Ui.input(c, "Search notes");
        search.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 14, 12, 14, 4));
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a1, int b, int c1) { }
            public void onTextChanged(CharSequence s, int a1, int b, int c1) { paint(); }
            public void afterTextChanged(android.text.Editable s) { }
        });
        root.addView(search);
        list = Ui.vbox(c);
        list.setPadding(Ui.dp(14), Ui.dp(6), Ui.dp(14), Ui.dp(90));
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.setFillViewport(true); sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        holder.addView(root);
        FrameLayout fab = new FrameLayout(c);
        fab.setBackground(Ui.ripple(Ui.gradient(28)));
        fab.setClickable(true); fab.setContentDescription("New note");
        fab.addView(Ui.icon(c, "plus", 24, 0xFFFFFFFF), new FrameLayout.LayoutParams(Ui.dp(24), Ui.dp(24), Gravity.CENTER));
        fab.setElevation(Ui.dp(6));
        fab.setOnClickListener(v -> a.push(new NoteEditScreen(null)));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(Ui.dp(56), Ui.dp(56), Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, Ui.dp(18), Ui.dp(18));
        holder.addView(fab, fp);
        load();
    }

    private void load() {
        if (all.isEmpty()) { list.removeAllViews(); list.addView(Ui.loading(c)); }
        async(() -> Api.rest("GET", "notes?select=*&user_id=eq." + Http.enc(Auth.userId) + "&deleted_at=is.null&order=updated_at.desc&limit=200", null, true), (rows, e) -> {
            if (e != null) { list.removeAllViews(); list.addView(Ui.state(c, "alert", "Couldn't load notes", msg(e))); return; }
            all.clear();
            for (int i = 0; i < rows.length(); i++) all.add(rows.optJSONObject(i));
            paint();
        });
    }

    private void paint() {
        if (list == null) return;
        list.removeAllViews();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.US);
        int n = 0;
        for (final JSONObject o : all) {
            String body = o.optBoolean("is_html") ? Rows.stripTags(o.optString("body")) : o.optString("body");
            if (!q.isEmpty() && !(o.optString("title") + " " + body).toLowerCase(Locale.US).contains(q)) continue;
            LinearLayout r = Ui.vbox(c);
            r.setBackground(Ui.ripple(Ui.shape(Ui.CARD, 14, Ui.LINE)));
            r.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
            r.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 9));
            r.setClickable(true);
            LinearLayout t = Ui.hbox(c);
            TextView ti = Ui.text(c, Auth.firstNonEmpty(o.optString("title"), "Untitled"), 15.5f, Ui.TXT, true);
            ti.setSingleLine(true); ti.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.addView(ti, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            if (o.optBoolean("is_favorite")) t.addView(Ui.icon(c, "star-fill", 14, 0xFFF59E0B));
            r.addView(t);
            if (!body.isEmpty()) r.addView(Rows.clamp(Ui.text(c, body, 13, Ui.MUT, false), 2), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 3, 0, 0));
            r.addView(Ui.text(c, Fmt.rel(o.optString("updated_at")), 11, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 6, 0, 0));
            r.setOnClickListener(v -> a.push(new NoteEditScreen(o)));
            list.addView(r);
            n++;
        }
        if (n == 0) list.addView(Ui.state(c, "note", q.isEmpty() ? "No notes yet" : "No matches", q.isEmpty() ? "Tap + to write your first note." : ""));
    }
}

final class NoteEditScreen extends Screen {
    private final JSONObject note;
    private EditText title, body;
    private boolean fav, dirty, saving;
    NoteEditScreen(JSONObject n) { note = n; }
    @Override String title() { return note == null ? "New note" : "Edit note"; }

    @Override View[] actions() {
        List<View> v = new ArrayList<>();
        v.add(Ui.iconButton(a, "star", x -> { fav = !fav; dirty = true; ((android.widget.ImageView) ((FrameLayout) x).getChildAt(0)).setImageDrawable(Icons.drawable(c, fav ? "star-fill" : "star", fav ? 0xFFF59E0B : Ui.TXT)); }));
        if (note != null) v.add(Ui.iconButton(a, "trash", x -> new android.app.AlertDialog.Builder(a).setTitle("Move to trash?").setNegativeButton("Cancel", null)
            .setPositiveButton("Trash", (d, w) -> async(() -> Api.rest("PATCH", "notes?id=eq." + Http.enc(note.optString("id")), new JSONObject().put("deleted_at", iso()), false), (r, e) -> {
                if (e != null) toast("Couldn't delete."); else { dirty = false; a.onBackPressed(); }
            })).show()));
        v.add(Ui.iconButton(a, "check", x -> save(true)));
        return v.toArray(new View[0]);
    }

    private static String iso() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    @Override View build() {
        LinearLayout l = col();
        title = Ui.input(c, "Title");
        title.setTextSize(20); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        body = Ui.input(c, "Start writing");
        body.setSingleLine(false); body.setMinLines(14); body.setGravity(Gravity.TOP);
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (note != null) {
            title.setText(note.optString("title"));
            fav = note.optBoolean("is_favorite");
            String b = note.optString("body");
            if (note.optBoolean("is_html")) {
                body.setText(Html.fromHtml(b.replace("</p>", "</p><br>")).toString().trim());
                l.addView(Ui.text(c, "This note has formatting from the web editor. It is shown as plain text here and saving will simplify it.", 11.5f, Ui.WARN, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 0, 0, 8));
            } else body.setText(b);
        }
        android.text.TextWatcher w = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a1, int b1, int c1) { }
            public void onTextChanged(CharSequence s, int a1, int b1, int c1) { dirty = true; }
            public void afterTextChanged(android.text.Editable s) { }
        };
        title.addTextChangedListener(w); body.addTextChangedListener(w);
        l.addView(title); l.addView(body);
        return scroll(l);
    }

    private void save(final boolean close) {
        if (saving) return;
        final String t = title.getText().toString().trim(), b = body.getText().toString();
        if (t.isEmpty() && b.trim().isEmpty()) { if (close) a.onBackPressed(); return; }
        saving = true;
        async(() -> {
            JSONObject row = new JSONObject().put("title", t.isEmpty() ? "Untitled" : t).put("body", b).put("is_html", false).put("is_favorite", fav);
            if (note == null) { row.put("user_id", Auth.userId); Api.rest("POST", "notes", row, false); }
            else { row.put("updated_at", iso()); Api.rest("PATCH", "notes?id=eq." + Http.enc(note.optString("id")), row, false); }
            return Boolean.TRUE;
        }, (r, e) -> {
            saving = false;
            if (e != null) { toast("Couldn't save: " + msg(e)); return; }
            dirty = false; toast("Saved");
            if (close) a.onBackPressed();
        });
    }
}

/** Docs (read-only): the website's paged editor format is not safe to write from here. */
final class DocsScreen extends Screen {
    private FrameLayout holder;
    @Override String title() { return "Docs"; }
    @Override View build() { holder = new FrameLayout(c); render(); return holder; }
    @Override void onAuth() { if (holder != null) render(); }

    private void render() {
        holder.removeAllViews();
        LinearLayout l = col();
        if (!Auth.signedIn()) { l.addView(AuthView.build(this, "Sign in to see your documents.")); holder.addView(scroll(l)); return; }
        final LinearLayout list = Ui.vbox(c);
        list.addView(Ui.loading(c));
        l.addView(list);
        holder.addView(scroll(l));
        async(() -> Api.rest("GET", "docs?select=id,title,plain_text,updated_at&owner_id=eq." + Http.enc(Auth.userId) + "&order=updated_at.desc&limit=100", null, true), (rows, e) -> {
            list.removeAllViews();
            if (e != null) { list.addView(Ui.state(c, "alert", "Couldn't load documents", msg(e))); return; }
            if (rows.length() == 0) { list.addView(Ui.state(c, "doc", "No documents yet", "Create documents on the 360 website. You can read them here.")); return; }
            for (int i = 0; i < rows.length(); i++) {
                final JSONObject o = rows.optJSONObject(i);
                LinearLayout r = Ui.vbox(c);
                r.setBackground(Ui.ripple(Ui.shape(Ui.CARD, 14, Ui.LINE)));
                r.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
                r.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 9));
                r.setClickable(true);
                r.addView(Ui.text(c, Auth.firstNonEmpty(o.optString("title"), "Untitled"), 15.5f, Ui.TXT, true));
                String pt = o.optString("plain_text");
                if (!pt.isEmpty() && !"null".equals(pt)) r.addView(Rows.clamp(Ui.text(c, pt, 13, Ui.MUT, false), 2), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 3, 0, 0));
                r.addView(Ui.text(c, Fmt.rel(o.optString("updated_at")), 11, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 6, 0, 0));
                r.setOnClickListener(v -> a.push(new DocViewScreen(o)));
                list.addView(r);
            }
        });
    }
}

final class DocViewScreen extends Screen {
    private final JSONObject d;
    DocViewScreen(JSONObject d) { this.d = d; }
    @Override String title() { return Auth.firstNonEmpty(d.optString("title"), "Document"); }
    @Override View build() {
        LinearLayout l = col();
        TextView t = Ui.text(c, d.optString("plain_text").isEmpty() || "null".equals(d.optString("plain_text")) ? "This document has no text content." : d.optString("plain_text"), 15.5f, Ui.TXT, false);
        t.setLineSpacing(0, 1.4f);
        t.setTextIsSelectable(true);
        l.addView(t);
        return scroll(l);
    }
}
