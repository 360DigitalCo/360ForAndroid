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
        if (e == null) return "Something went wrong.";
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.SocketTimeoutException || e instanceof java.net.ConnectException)
            return "No connection. Check your network and try again.";
        String m = e.getMessage() == null ? "" : e.getMessage().trim();
        int code = e instanceof Api.ApiError ? ((Api.ApiError) e).code : 0;
        if (m.startsWith("<")) m = "";                                   // an HTML error page from a gateway
        if (m.toLowerCase(java.util.Locale.US).matches(".*all ai providers.*(unavailable|down).*")) return "The AI service is busy right now. Please try again in a moment.";
        if (code == 401 || code == 403 || m.toLowerCase(java.util.Locale.US).contains("jwt"))
            return Auth.signedIn() ? "Your session expired. Sign out and back in, then try again." : "Sign in to use this.";
        if (code == 404) return "This feature isn't available on the server yet.";
        if (code == 408 || code == 504) return "The server took too long to respond. Try again.";
        if (code == 429) return "Too many requests. Wait a moment and try again.";
        if (code >= 500) return m.isEmpty() || m.length() > 140 ? "The server had a problem. Try again in a moment." : m;
        if (m.length() > 200) m = m.substring(0, 200) + "\u2026";
        return m.isEmpty() ? "Something went wrong." : m;
    }
}
