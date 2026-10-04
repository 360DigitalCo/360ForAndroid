package com.search360.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Native shell: top bar, five tabs, stacked pages. No WebView anywhere in the app. */
public class MainActivity extends Activity {
    static final String[] TAB_IDS = {"home", "search", "mail", "ai", "apps"};
    static final String[] TAB_LABELS = {"Home", "Search", "Mail", "AI", "Apps"};
    static final String[] TAB_ICONS = {"home", "search", "mail", "chat", "grid"};
    static int lastTab = 0;

    private FrameLayout tabHost, pageLayer;
    private final Screen[] tabs = new Screen[5];
    private final View[] tabViews = new View[5];
    private final LinearLayout[] navItems = new LinearLayout[5];
    private TextView mailBadge;
    private int tab = 0;
    private final List<Screen> pages = new ArrayList<>();
    private final List<View> pageViews = new ArrayList<>();
    private Runnable pendingLocation;
    private final Runnable authListener = this::authChanged;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Store.init(this);
        Auth.init();
        Ui.init(this);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BG));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.CARD);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        flags = Ui.dark ? (flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) : (flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().getDecorView().setSystemUiVisibility(flags);

        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        root.addView(topBar(), Ui.lp(Ui.MATCH, Ui.WRAP));
        FrameLayout stage = new FrameLayout(this);
        tabHost = new FrameLayout(this);
        pageLayer = new FrameLayout(this);
        stage.addView(tabHost, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        stage.addView(pageLayer, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        root.addView(stage, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        root.addView(bottomNav(), Ui.lp(Ui.MATCH, Ui.WRAP));
        setContentView(root);

        Auth.listeners.add(authListener);
        selectTab(lastTab);
        handleIntent(getIntent());
    }

    @Override protected void onDestroy() {
        Auth.listeners.remove(authListener);
        for (Screen s : tabs) if (s != null) s.onDestroy();
        for (Screen s : pages) s.onDestroy();
        super.onDestroy();
    }

    /* ── chrome ─────────────────────────────────────────────── */
    private View topBar() {
        LinearLayout bar = Ui.hbox(this);
        bar.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(12), Ui.dp(8));
        TextView brand = Ui.text(this, "360", 22, Ui.ACC, true);
        brand.setLetterSpacing(-0.03f);
        bar.addView(brand, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        bar.addView(Ui.iconButton(this, "user", v -> push(new AccountScreen())));
        bar.addView(Ui.iconButton(this, Ui.dark ? "sun" : "moon", v -> {
            Store.put("theme", Ui.dark ? "light" : "dark");
            recreate();
        }));
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        LinearLayout wrap = Ui.vbox(this);
        wrap.addView(bar);
        wrap.addView(line, Ui.lp(Ui.MATCH, 1));
        return wrap;
    }

    private View bottomNav() {
        LinearLayout wrap = Ui.vbox(this);
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        wrap.addView(line, Ui.lp(Ui.MATCH, 1));
        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(Ui.CARD);
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            LinearLayout it = Ui.vbox(this);
            it.setGravity(Gravity.CENTER);
            it.setPadding(0, Ui.dp(8), 0, Ui.dp(8));
            it.setBackground(Ui.ripple(null));
            it.setClickable(true);
            it.setContentDescription(TAB_LABELS[i]);
            FrameLayout iconBox = new FrameLayout(this);
            ImageView ic = Ui.icon(this, TAB_ICONS[i], 22, Ui.MUT);
            iconBox.addView(ic, new FrameLayout.LayoutParams(Ui.dp(22), Ui.dp(22), Gravity.CENTER));
            if (i == 2) {
                mailBadge = Ui.text(this, "", 9, 0xFFFFFFFF, true);
                mailBadge.setGravity(Gravity.CENTER);
                mailBadge.setBackground(Ui.shape(Ui.BAD, 8, 0));
                mailBadge.setPadding(Ui.dp(4), 0, Ui.dp(4), 0);
                mailBadge.setVisibility(View.GONE);
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.dp(16), Gravity.TOP | Gravity.END);
                iconBox.addView(mailBadge, bp);
            }
            it.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(34), Ui.dp(24)));
            TextView t = Ui.text(this, TAB_LABELS[i], 10.5f, Ui.MUT, true);
            it.addView(t, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 2, 0, 0));
            it.setOnClickListener(v -> selectTab(idx));
            nav.addView(it, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            navItems[i] = it;
        }
        wrap.addView(nav, Ui.lp(Ui.MATCH, Ui.WRAP));
        return wrap;
    }

    void setMailBadge(int n) {
        if (mailBadge == null) return;
        mailBadge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        mailBadge.setText(n > 99 ? "99+" : String.valueOf(n));
    }

    /* ── navigation ─────────────────────────────────────────── */
    private Screen makeTab(int i) {
        switch (i) {
            case 0: return new HomeScreen();
            case 1: return new SearchScreen();
            case 2: return new MailScreen();
            case 3: return new AiScreen();
            default: return new AppsScreen();
        }
    }

    void selectTab(int i) {
        while (!pages.isEmpty()) popPage();
        tab = i; lastTab = i;
        if (tabs[i] == null) {
            tabs[i] = makeTab(i);
            tabs[i].attach(this);
            tabViews[i] = tabs[i].build();
            tabHost.addView(tabViews[i], new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        }
        for (int k = 0; k < 5; k++) {
            if (tabViews[k] != null) tabViews[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
            boolean on = k == i;
            LinearLayout it = navItems[k];
            ((ImageView) ((FrameLayout) it.getChildAt(0)).getChildAt(0)).setColorFilter(on ? Ui.ACC : Ui.MUT, android.graphics.PorterDuff.Mode.SRC_IN);
            ((TextView) it.getChildAt(1)).setTextColor(on ? Ui.ACC : Ui.MUT);
        }
        hideKeyboard();
        tabs[i].onShow();
    }

    void push(Screen s) {
        s.attach(this);
        LinearLayout page = Ui.vbox(this);
        page.setBackgroundColor(Ui.BG);
        page.setClickable(true);     // swallow touches so nothing leaks to the tab below
        LinearLayout head = Ui.hbox(this);
        head.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(12), Ui.dp(8));
        head.addView(Ui.iconButton(this, "back", v -> onBackPressed()));
        TextView t = Ui.text(this, s.title(), 17, Ui.TXT, true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(t, Ui.lp(0, Ui.WRAP, 8, 0, 8, 0));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1f;
        View[] acts = s.actions();
        if (acts != null) for (View v : acts) head.addView(v);
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        page.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));
        page.addView(line, Ui.lp(Ui.MATCH, 1));
        View body = s.build();
        page.addView(body, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        pageLayer.addView(page, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        pages.add(s); pageViews.add(page);
        s.onShow();
    }

    private void popPage() {
        if (pages.isEmpty()) return;
        Screen s = pages.remove(pages.size() - 1);
        View v = pageViews.remove(pageViews.size() - 1);
        pageLayer.removeView(v);
        s.onDestroy();
    }

    @Override public void onBackPressed() {
        hideKeyboard();
        if (!pages.isEmpty()) { popPage(); return; }
        if (tab != 0) { selectTab(0); return; }
        super.onBackPressed();
    }

    private void authChanged() {
        for (Screen s : tabs) if (s != null) s.onAuth();
        for (Screen s : new ArrayList<>(pages)) s.onAuth();
    }

    /* ── helpers used by screens ────────────────────────────── */
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    void openExternal(String url) {
        try {
            Uri u = Uri.parse(url);
            if (!"https".equals(u.getScheme()) && !"http".equals(u.getScheme())) return;
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception ignored) { }
    }

    void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            InputMethodManager m = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (m != null) m.hideSoftInputFromWindow(f.getWindowToken(), 0);
        }
    }

    void requestLocation(Runnable granted) {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            granted.run();
            return;
        }
        pendingLocation = granted;
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code != 7) return;
        boolean ok = false;
        for (int r : res) if (r == PackageManager.PERMISSION_GRANTED) ok = true;
        Runnable r = pendingLocation; pendingLocation = null;
        if (ok && r != null) r.run();
        else toast("Location permission is needed for this.");
    }

    /* ── OAuth return trip: three60app://auth-callback#access_token=… ── */
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); handleIntent(i); }

    private void handleIntent(Intent i) {
        if (i == null || i.getData() == null || !"three60app".equals(i.getData().getScheme())) return;
        final Uri u = i.getData();
        setIntent(new Intent());   // consume so a rotation does not replay it
        Http.async(() -> { Auth.handleCallback(u); return Boolean.TRUE; }, (v, e) -> {
            if (e != null) toast(e.getMessage() == null ? "Sign-in failed." : e.getMessage());
            else if (Auth.signedIn()) toast("Signed in");
        });
    }
}
