package com.search360.app;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** A native screen: either a bottom-nav tab or a pushed page. */
abstract class Screen {
    MainActivity a;
    Context c;
    boolean alive = true;

    void attach(MainActivity act) { a = act; c = act; }
    abstract View build();
    String title() { return ""; }
    /** Optional header buttons for pushed pages. */
    View[] actions() { return null; }
    void onShow() { }
    void onAuth() { }
    void onDestroy() { alive = false; }

    /** Run in background; callback is skipped if the screen was closed meanwhile. */
    <T> void async(Http.Task<T> task, final Http.Cb<T> cb) {
        Http.async(task, (v, e) -> { if (alive) cb.done(v, e); });
    }
    void toast(String s) { a.toast(s); }

    LinearLayout col() {
        LinearLayout l = Ui.vbox(c);
        l.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(24));
        return l;
    }
    ScrollView scroll(View content) {
        ScrollView s = new ScrollView(c);
        s.setFillViewport(true);
        s.setVerticalScrollBarEnabled(false);
        s.addView(content);
        return s;
    }
    static String msg(Exception e) {
        String m = e == null ? "" : e.getMessage();
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.SocketTimeoutException) return "No connection. Check your network and try again.";
        return m == null || m.isEmpty() ? "Something went wrong." : m;
    }
}
