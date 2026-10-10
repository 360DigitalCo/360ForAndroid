package com.search360.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Sketch canvas. Reads/writes the website's `drawings.data` action list ({type:'brush'|'eraser', color, size, points:[{x,y}]}).
 * Actions of any other type (shapes drawn on the website) are kept in the list untouched, so saving never drops them.
 * New strokes are stored in a 1000-unit-wide logical canvas; existing drawings are auto-fitted to the screen.
 */
final class DrawView extends View {
    final List<JSONObject> actions = new ArrayList<>(), redo = new ArrayList<>();
    int color = 0xFF111827;
    float size = 6f;
    boolean eraser;

    private Bitmap layer;
    private Canvas lc;
    private float scale = 1f, ox = 0f, oy = 0f;
    private boolean fitted;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), bg = new Paint();
    private final List<float[]> cur = new ArrayList<>();
    private float lastX, lastY;

    DrawView(Context c) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeJoin(Paint.Join.ROUND);
        bg.setColor(0xFFFFFFFF);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (w <= 0 || h <= 0) return;
        layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        lc = new Canvas(layer);
        fit(w, h);
        renderAll();
    }

    /** Fit loaded content into the view; blank drawings use a 1000-unit-wide canvas. */
    private void fit(int w, int h) {
        double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9; int n = 0;
        for (JSONObject a : actions) {
            JSONArray pts = a.optJSONArray("points");
            if (pts == null) continue;
            for (int i = 0; i < pts.length(); i++) {
                JSONObject p = pts.optJSONObject(i);
                if (p == null) continue;
                double x = p.optDouble("x"), y = p.optDouble("y");
                minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); n++;
            }
        }
        if (n < 2 || maxX - minX < 1 || maxY - minY < 1) { scale = w / 1000f; ox = 0; oy = 0; return; }
        float pad = Ui.dp(24);
        float s = (float) Math.min((w - 2 * pad) / (maxX - minX), (h - 2 * pad) / (maxY - minY));
        scale = Math.min(s, w / 400f);
        ox = (float) (pad + ((w - 2 * pad) - (maxX - minX) * scale) / 2 - minX * scale);
        oy = (float) (pad + ((h - 2 * pad) - (maxY - minY) * scale) / 2 - minY * scale);
    }

    void setActions(List<JSONObject> list) { actions.clear(); actions.addAll(list); redo.clear(); if (getWidth() > 0) { fit(getWidth(), getHeight()); renderAll(); } invalidate(); }

    private static int parse(String c, int def) { try { return Color.parseColor(c); } catch (Exception e) { return def; } }
    static String hex(int c) { return String.format("#%06x", c & 0xFFFFFF); }

    private void apply(Paint p, boolean isEraser, int col, double sz) {
        p.setColor(isEraser ? 0xFF000000 : col);
        p.setStrokeWidth((float) Math.max(1, sz * scale));
        p.setXfermode(isEraser ? new PorterDuffXfermode(PorterDuff.Mode.CLEAR) : null);
    }

    private void drawAction(Canvas cv, JSONObject a) {
        String t = a.optString("type");
        if (!t.equals("brush") && !t.equals("eraser")) return;
        JSONArray pts = a.optJSONArray("points");
        if (pts == null || pts.length() == 0) return;
        Paint p = new Paint(stroke);
        apply(p, t.equals("eraser"), parse(a.optString("color", "#111827"), 0xFF111827), a.optDouble("size", 6));
        if (pts.length() == 1) {
            JSONObject q = pts.optJSONObject(0);
            Paint dot = new Paint(p); dot.setStyle(Paint.Style.FILL);
            cv.drawCircle((float) (q.optDouble("x") * scale + ox), (float) (q.optDouble("y") * scale + oy), p.getStrokeWidth() / 2, dot);
            return;
        }
        Path path = new Path();
        JSONObject f = pts.optJSONObject(0);
        path.moveTo((float) (f.optDouble("x") * scale + ox), (float) (f.optDouble("y") * scale + oy));
        for (int i = 1; i < pts.length(); i++) {
            JSONObject q = pts.optJSONObject(i);
            path.lineTo((float) (q.optDouble("x") * scale + ox), (float) (q.optDouble("y") * scale + oy));
        }
        cv.drawPath(path, p);
    }

    void renderAll() {
        if (lc == null) return;
        layer.eraseColor(0);
        for (JSONObject a : actions) drawAction(lc, a);
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        cv.drawRect(0, 0, getWidth(), getHeight(), bg);
        if (layer != null) cv.drawBitmap(layer, 0, 0, null);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (lc == null) return false;
        getParent().requestDisallowInterceptTouchEvent(true);
        float x = e.getX(), y = e.getY();
        Paint p = new Paint(stroke);
        apply(p, eraser, color, size);
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                cur.clear(); cur.add(new float[]{x, y}); lastX = x; lastY = y;
                Paint dot = new Paint(p); dot.setStyle(Paint.Style.FILL);
                lc.drawCircle(x, y, p.getStrokeWidth() / 2, dot);
                invalidate(); return true;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getHistorySize(); i++) { lc.drawLine(lastX, lastY, e.getHistoricalX(i), e.getHistoricalY(i), p); lastX = e.getHistoricalX(i); lastY = e.getHistoricalY(i); cur.add(new float[]{lastX, lastY}); }
                lc.drawLine(lastX, lastY, x, y, p); lastX = x; lastY = y; cur.add(new float[]{x, y});
                invalidate(); return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                commit(); return true;
            default: return super.onTouchEvent(e);
        }
    }

    private void commit() {
        if (cur.isEmpty()) return;
        try {
            JSONArray pts = new JSONArray();
            float[] prev = null;
            for (float[] q : cur) {      // thin out points that are less than ~1px apart
                if (prev != null && Math.abs(q[0] - prev[0]) < 1 && Math.abs(q[1] - prev[1]) < 1) continue;
                pts.put(new JSONObject().put("x", Math.round((q[0] - ox) / scale * 10) / 10.0).put("y", Math.round((q[1] - oy) / scale * 10) / 10.0));
                prev = q;
            }
            actions.add(new JSONObject().put("type", eraser ? "eraser" : "brush").put("color", hex(color)).put("size", size).put("points", pts));
            redo.clear();
        } catch (Exception ignored) { }
        cur.clear();
    }

    boolean undo() { if (actions.isEmpty()) return false; redo.add(actions.remove(actions.size() - 1)); renderAll(); return true; }
    boolean redo() { if (redo.isEmpty()) return false; actions.add(redo.remove(redo.size() - 1)); renderAll(); return true; }
    void clear() { actions.clear(); redo.clear(); renderAll(); }

    /** Small JPEG data URL for the gallery, like the website's canvas thumbnail. */
    String thumbnail() {
        if (layer == null) return null;
        int tw = 320, th = Math.max(1, Math.round(tw * layer.getHeight() / (float) layer.getWidth()));
        Bitmap t = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(t);
        c.drawColor(0xFFFFFFFF);
        c.drawBitmap(Bitmap.createScaledBitmap(layer, tw, th, true), 0, 0, null);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        t.compress(Bitmap.CompressFormat.JPEG, 72, bo);
        return "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }
}

final class DrawListScreen extends Screen {
    private FrameLayout holder;
    @Override String title() { return "Draw"; }
    @Override View build() { holder = new FrameLayout(c); render(); return holder; }
    @Override void onAuth() { if (holder != null) render(); }
    @Override void onShow() { if (Auth.signedIn() && holder != null && holder.getChildCount() > 0) render(); }

    static Bitmap thumbOf(String url) {
        try {
            if (url.startsWith("data:image")) { byte[] b = Base64.decode(url.substring(url.indexOf(',') + 1), Base64.DEFAULT); return BitmapFactory.decodeByteArray(b, 0, b.length); }
        } catch (Exception ignored) { }
        return null;
    }

    private void render() {
        holder.removeAllViews();
        LinearLayout l = col();
        if (!Auth.signedIn()) { l.addView(AuthView.build(this, "Sign in to save and open your drawings.")); holder.addView(scroll(l)); return; }
        final LinearLayout grid = Ui.vbox(c);
        grid.addView(Ui.loading(c));
        l.addView(grid);
        holder.addView(scroll(l));
        FrameLayout fab = new FrameLayout(c);
        fab.setBackground(Ui.ripple(Ui.gradient(28)));
        fab.setClickable(true); fab.setContentDescription("New drawing");
        fab.addView(Ui.icon(c, "plus", 24, 0xFFFFFFFF), new FrameLayout.LayoutParams(Ui.dp(24), Ui.dp(24), Gravity.CENTER));
        fab.setElevation(Ui.dp(6));
        fab.setOnClickListener(v -> a.push(new DrawEditorScreen(null)));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(Ui.dp(56), Ui.dp(56), Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, Ui.dp(18), Ui.dp(18));
        holder.addView(fab, fp);
        async(() -> Api.rest("GET", "drawings?select=id,title,thumbnail_url,updated_at&user_id=eq." + Http.enc(Auth.userId) + "&order=updated_at.desc&limit=60", null, true), (rows, e) -> {
            grid.removeAllViews();
            if (e != null) { grid.addView(Ui.state(c, "alert", "Couldn't load drawings", msg(e))); return; }
            if (rows.length() == 0) { grid.addView(Ui.state(c, "pen", "No drawings yet", "Tap + to start sketching.")); return; }
            LinearLayout row = null;
            for (int i = 0; i < rows.length(); i++) {
                if (i % 2 == 0) { row = Ui.hbox(c); row.setGravity(Gravity.TOP); grid.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10)); }
                row.addView(card(rows.optJSONObject(i)));
            }
            if (rows.length() % 2 == 1) { View pad = new View(c); row.addView(pad, new LinearLayout.LayoutParams(0, 1, 1f)); }
        });
    }

    private View card(final JSONObject o) {
        LinearLayout card = Ui.vbox(c);
        card.setBackground(Ui.ripple(Ui.shape(Ui.CARD, 14, Ui.LINE)));
        card.setClipToOutline(true);
        card.setClickable(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        p.setMargins(Ui.dp(4), 0, Ui.dp(4), 0);
        card.setLayoutParams(p);
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(0xFFFFFFFF);
        String th = J.s(o, "thumbnail_url");
        Bitmap b = th.isEmpty() ? null : thumbOf(th);
        if (b != null) iv.setImageBitmap(b); else if (th.startsWith("http")) Img.load(th, iv);
        card.addView(iv, new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(130)));
        LinearLayout m = Ui.vbox(c);
        m.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(10));
        m.addView(Rows.clamp(Ui.text(c, J.s(o, "title", "Untitled"), 14, Ui.TXT, true), 1));
        m.addView(Ui.text(c, Fmt.rel(J.s(o, "updated_at")), 11.5f, Ui.MUT, false));
        card.addView(m);
        card.setOnClickListener(v -> a.push(new DrawEditorScreen(J.s(o, "id"))));
        card.setOnLongClickListener(v -> {
            new android.app.AlertDialog.Builder(a).setTitle("Delete drawing?").setNegativeButton("Cancel", null).setPositiveButton("Delete", (d, w) ->
                async(() -> Api.rest("DELETE", "drawings?id=eq." + Http.enc(J.s(o, "id")) + "&user_id=eq." + Http.enc(Auth.userId), null, false), (r, e) -> { if (e != null) toast("Couldn't delete: " + msg(e)); else render(); })).show();
            return true;
        });
        return card;
    }
}

final class DrawEditorScreen extends Screen {
    private static final int[] COLORS = {0xFF111827, 0xFFEF4444, 0xFFF59E0B, 0xFF16A34A, 0xFF06B6D4, 0xFF3B82F6, 0xFFA21CAF, 0xFFFFFFFF};
    private String id, title = "";
    private DrawView dv;
    private boolean saving;
    DrawEditorScreen(String id) { this.id = id; }
    @Override String title() { return id == null ? "New drawing" : "Drawing"; }

    @Override View[] actions() {
        return new View[]{Ui.iconButton(a, "undo", v -> { if (!dv.undo()) toast("Nothing to undo"); }),
                          Ui.iconButton(a, "redo", v -> { if (!dv.redo()) toast("Nothing to redo"); }),
                          Ui.iconButton(a, "check", v -> save())};
    }

    @Override View build() {
        LinearLayout root = Ui.vbox(c);
        dv = new DrawView(c);
        root.addView(dv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        LinearLayout tools = Ui.vbox(c);
        tools.setBackgroundColor(Ui.CARD);
        tools.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(10));
        final LinearLayout sw = Ui.hbox(c);
        for (final int col : COLORS) {
            View v = new View(c);
            v.setBackground(Ui.shape(col, 16, col == 0xFFFFFFFF ? Ui.MUT : 0));
            v.setTag(col);
            v.setClickable(true);
            v.setOnClickListener(x -> { dv.color = col; dv.eraser = false; mark(sw, col, false); });
            sw.addView(v, Ui.lp(Ui.dp(32), Ui.dp(32), 0, 0, 10, 0));
        }
        final TextView er = Ui.chip(c, "Eraser", false);
        er.setOnClickListener(v -> { dv.eraser = !dv.eraser; er.setTextColor(dv.eraser ? 0xFFFFFFFF : Ui.TXT); er.setBackground(Ui.ripple(dv.eraser ? Ui.shape(Ui.ACC, 20, 0) : Ui.shape(Ui.CARD, 20, Ui.LINE))); });
        sw.addView(er);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false); hs.addView(sw);
        mark(sw, dv.color, false);
        tools.addView(hs);
        LinearLayout sz = Ui.hbox(c);
        sz.addView(Ui.text(c, "Size", 12.5f, Ui.MUT, true), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 0, 10, 0));
        SeekBar sb = new SeekBar(c);
        sb.setMax(38); sb.setProgress(4);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACC)); sb.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACC));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { dv.size = p + 2; }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        sz.addView(sb, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        TextView clear = Ui.text(c, "Clear", 13, Ui.BAD, true);
        clear.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(4), Ui.dp(8));
        clear.setClickable(true);
        clear.setOnClickListener(v -> new android.app.AlertDialog.Builder(a).setTitle("Clear the canvas?").setNegativeButton("Cancel", null).setPositiveButton("Clear", (d, w) -> dv.clear()).show());
        sz.addView(clear);
        tools.addView(sz, Ui.lp(Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        root.addView(tools);

        if (id != null) {
            async(() -> Api.rest("GET", "drawings?select=*&id=eq." + Http.enc(id) + "&user_id=eq." + Http.enc(Auth.userId) + "&limit=1", null, true), (rows, e) -> {
                if (e != null || rows.length() == 0) { toast("Couldn't open this drawing."); return; }
                JSONObject o = rows.optJSONObject(0);
                title = J.s(o, "title");
                List<JSONObject> list = new ArrayList<>();
                Object d = o.opt("data");
                try { if (d instanceof String) d = new JSONArray((String) d); } catch (Exception ex) { d = null; }
                if (d instanceof JSONArray) for (int i = 0; i < ((JSONArray) d).length(); i++) { JSONObject x = ((JSONArray) d).optJSONObject(i); if (x != null) list.add(x); }
                dv.setActions(list);
            });
        }
        return root;
    }
    /** Ring the selected swatch. */
    private void mark(LinearLayout sw, int col, boolean eraser) {
        for (int i = 0; i < sw.getChildCount(); i++) {
            View v = sw.getChildAt(i);
            if (!(v.getTag() instanceof Integer)) continue;
            int c0 = (Integer) v.getTag();
            v.setBackground(Ui.shape(c0, 16, c0 == col ? Ui.ACC : (c0 == 0xFFFFFFFF ? Ui.MUT : 0)));
            v.setScaleX(c0 == col ? 1.15f : 1f); v.setScaleY(c0 == col ? 1.15f : 1f);
        }
    }

    private void save() {
        if (saving) return;
        final EditText name = Ui.input(c, "Title");
        name.setText(title.isEmpty() ? "Untitled" : title);
        LinearLayout w = Ui.vbox(c); w.setPadding(Ui.dp(20), Ui.dp(8), Ui.dp(20), 0); w.addView(name);
        new android.app.AlertDialog.Builder(a).setTitle("Save drawing").setView(w).setNegativeButton("Cancel", null).setPositiveButton("Save", (d, x) -> {
            title = name.getText().toString().trim();
            if (title.isEmpty()) title = "Untitled";
            saving = true;
            final JSONArray data = new JSONArray();
            for (JSONObject o : dv.actions) data.put(o);
            final String thumb = dv.thumbnail();
            async(() -> {
                JSONObject row = new JSONObject().put("user_id", Auth.userId).put("title", title).put("data", data).put("updated_at", NoteTime.iso());
                if (thumb != null) row.put("thumbnail_url", thumb);
                if (id == null) { JSONArray r = Api.rest("POST", "drawings", row, true); return r.length() > 0 ? J.s(r.optJSONObject(0), "id") : ""; }
                Api.rest("PATCH", "drawings?id=eq." + Http.enc(id) + "&user_id=eq." + Http.enc(Auth.userId), row, false);
                return id;
            }, (nid, e) -> {
                saving = false;
                if (e != null) { toast("Couldn't save: " + msg(e)); return; }
                if (nid != null && !nid.isEmpty()) id = nid;
                toast("Saved");
            });
        }).show();
    }
}
