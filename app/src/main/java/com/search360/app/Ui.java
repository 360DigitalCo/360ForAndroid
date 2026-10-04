package com.search360.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Tiny key/value store (private prefs). */
final class Store {
    static SharedPreferences p;
    static void init(Context c) { p = c.getSharedPreferences("360", Context.MODE_PRIVATE); }
    static String get(String k, String d) { return p.getString(k, d); }
    static void put(String k, String v) { p.edit().putString(k, v).apply(); }
    static void remove(String k) { p.edit().remove(k).apply(); }
}

/** Vector-drawable icons (res/drawable/ic_*.xml), tinted at runtime. */
final class Icons {
    static Drawable drawable(Context c, String name, int color) {
        int id = c.getResources().getIdentifier("ic_" + name.replace('-', '_'), "drawable", c.getPackageName());
        if (id == 0) id = c.getResources().getIdentifier("ic_alert", "drawable", c.getPackageName());
        Drawable d = c.getResources().getDrawable(id, c.getTheme()).mutate();
        if (color != 0) d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        return d;
    }
    static ImageView view(Context c, String name, int sizeDp, int color) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(drawable(c, name, color));
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(sizeDp), Ui.dp(sizeDp)));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return v;
    }
}

/** Theme palette + programmatic view builders (no XML layouts, no WebView). */
final class Ui {
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int ACC = 0xFF3B82F6, ACC2 = 0xFF06B6D4, OK = 0xFF16A34A, BAD = 0xFFEF4444, WARN = 0xFFD97706;
    static float density = 1f;
    static boolean dark;
    static int BG, CARD, TXT, MUT, LINE;

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        String t = Store.get("theme", "system");
        boolean sys = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        dark = t.equals("dark") || (t.equals("system") && sys);
        if (dark) { BG = 0xFF0B0F1A; CARD = 0xFF141A2A; TXT = 0xFFEEF0F5; MUT = 0xFF9AA3B5; LINE = 0x1AFFFFFF; }
        else { BG = 0xFFF4F5F8; CARD = 0xFFFFFFFF; TXT = 0xFF111827; MUT = 0xFF6B7280; LINE = 0x1A0F172A; }
    }

    static int dp(float v) { return Math.round(v * density); }

    static GradientDrawable shape(int fill, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }
    static Drawable ripple(Drawable bg) {
        return new RippleDrawable(ColorStateList.valueOf(dark ? 0x28FFFFFF : 0x16000000), bg, null);
    }
    static GradientDrawable gradient(float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ACC, ACC2});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    /** LayoutParams with dp margins. */
    static LinearLayout.LayoutParams lp(int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }
    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    static LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, WRAP, w); }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
    static LinearLayout vbox(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static LinearLayout hbox(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    static View spacer(Context c, float hDp) { View v = new View(c); v.setLayoutParams(lp(MATCH, dp(hDp))); return v; }

    static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(shape(CARD, 14, LINE));
        l.setPadding(dp(14), dp(14), dp(14), dp(14));
        l.setLayoutParams(lp(MATCH, WRAP, 0, 0, 0, 10));
        return l;
    }

    /** style: 0 primary (gradient), 1 outline, 2 danger, 3 ghost */
    static TextView button(Context c, String label, int style) {
        TextView t = text(c, label, 15, style == 0 || style == 2 ? 0xFFFFFFFF : (style == 3 ? ACC : TXT), true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(48));
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        Drawable bg = style == 0 ? gradient(13) : style == 2 ? shape(BAD, 13, 0) : shape(style == 3 ? 0 : CARD, 13, LINE);
        t.setBackground(ripple(bg));
        t.setClickable(true);
        t.setLayoutParams(lp(MATCH, WRAP, 0, 0, 0, 10));
        return t;
    }
    static TextView chip(Context c, String label, boolean on) {
        TextView t = text(c, label, 13, on ? 0xFFFFFFFF : TXT, true);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setBackground(ripple(on ? shape(ACC, 20, 0) : shape(CARD, 20, LINE)));
        t.setClickable(true);
        t.setLayoutParams(lp(WRAP, WRAP, 0, 0, 8, 0));
        return t;
    }
    static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(MUT);
        e.setTextColor(TXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setBackground(shape(CARD, 12, LINE));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setSingleLine(true);
        e.setLayoutParams(lp(MATCH, WRAP, 0, 0, 0, 10));
        return e;
    }
    static ImageView icon(Context c, String name, int sizeDp, int color) { return Icons.view(c, name, sizeDp, color); }

    /** Round icon button (header actions). */
    static FrameLayout iconButton(Context c, String name, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(ripple(shape(CARD, 19, LINE)));
        f.setClickable(true);
        f.setOnClickListener(l);
        f.setContentDescription(name);
        ImageView i = icon(c, name, 18, TXT);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER);
        f.addView(i, p);
        f.setLayoutParams(lp(dp(38), dp(38), 4, 0, 0, 0));
        return f;
    }

    static View loading(Context c) {
        FrameLayout f = new FrameLayout(c);
        ProgressBar p = new ProgressBar(c);
        p.getIndeterminateDrawable().setColorFilter(ACC, PorterDuff.Mode.SRC_IN);
        f.addView(p, new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));
        f.setPadding(0, dp(40), 0, dp(40));
        f.setLayoutParams(lp(MATCH, WRAP));
        return f;
    }
    static View state(Context c, String icon, String title, String msg) {
        LinearLayout l = vbox(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(dp(24), dp(44), dp(24), dp(44));
        l.addView(icon(c, icon, 36, MUT));
        TextView t = text(c, title, 16, TXT, true);
        t.setGravity(Gravity.CENTER);
        l.addView(t, lp(WRAP, WRAP, 0, 10, 0, 4));
        if (msg != null && !msg.isEmpty()) {
            TextView m = text(c, msg, 13, MUT, false);
            m.setGravity(Gravity.CENTER);
            l.addView(m);
        }
        l.setLayoutParams(lp(MATCH, WRAP));
        return l;
    }
    static TextView label(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 11, MUT, true);
        t.setLetterSpacing(0.07f);
        t.setLayoutParams(lp(WRAP, WRAP, 2, 16, 0, 8));
        return t;
    }
    static void click(View v, Runnable r) { v.setClickable(true); v.setOnClickListener(x -> r.run()); }
}
