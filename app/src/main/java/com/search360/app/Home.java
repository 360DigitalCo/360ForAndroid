package com.search360.app;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Sign-in form (Google / GitHub through the system browser, or email + password). */
final class AuthView {
    static View build(final Screen s, String message) {
        final Context c = s.c;
        LinearLayout l = Ui.vbox(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(Ui.dp(8), Ui.dp(24), Ui.dp(8), Ui.dp(24));

        TextView mark = Ui.text(c, "360", 20, 0xFFFFFFFF, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.gradient(17));
        l.addView(mark, Ui.lp(Ui.dp(58), Ui.dp(58), 0, 0, 0, 14));
        TextView t = Ui.text(c, "Sign in to 360", 21, Ui.TXT, true);
        l.addView(t);
        TextView m = Ui.text(c, message, 13.5f, Ui.MUT, false);
        m.setGravity(Gravity.CENTER);
        l.addView(m, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 18));

        l.addView(providerButton(c, "google", "Continue with Google", false, v -> s.a.openExternal(Auth.oauthUrl("google"))));
        l.addView(providerButton(c, "github", "Continue with GitHub", true, v -> s.a.openExternal(Auth.oauthUrl("github"))));

        TextView or = Ui.text(c, "or", 12, Ui.MUT, false);
        l.addView(or, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 12));

        final EditText email = Ui.input(c, "Email");
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        final EditText pass = Ui.input(c, "Password");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        l.addView(email);
        l.addView(pass);
        final TextView err = Ui.text(c, "", 13, Ui.BAD, false);
        final TextView go = Ui.button(c, "Sign in", 0);
        final TextView create = Ui.text(c, "No account? Create one free", 13.5f, Ui.ACC, true);
        create.setPadding(0, Ui.dp(10), 0, Ui.dp(10));

        go.setOnClickListener(v -> {
            final String e = email.getText().toString().trim(), p = pass.getText().toString();
            if (e.isEmpty() || p.isEmpty()) { err.setTextColor(Ui.BAD); err.setText("Enter your email and password."); return; }
            go.setEnabled(false); err.setText("");
            s.async(() -> { Auth.signIn(e, p); return Boolean.TRUE; }, (r, ex) -> {
                go.setEnabled(true);
                if (ex != null) { err.setTextColor(Ui.BAD); err.setText(Screen.msg(ex)); }
            });
        });
        create.setOnClickListener(v -> {
            final String e = email.getText().toString().trim(), p = pass.getText().toString();
            if (e.isEmpty() || p.length() < 6) { err.setTextColor(Ui.BAD); err.setText("Enter an email and a password of 6+ characters, then tap Create."); return; }
            err.setText("");
            s.async(() -> Auth.signUp(e, p), (r, ex) -> {
                if (ex != null) { err.setTextColor(Ui.BAD); err.setText(Screen.msg(ex)); }
                else { err.setTextColor(Ui.OK); err.setText(r); }
            });
        });
        l.addView(go);
        l.addView(err);
        l.addView(create);
        return l;
    }

    private static View providerButton(Context c, String icon, String label, boolean tint, View.OnClickListener click) {
        LinearLayout b = Ui.hbox(c);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.ripple(Ui.shape(Ui.CARD, 13, Ui.LINE)));
        b.setClickable(true);
        b.setMinimumHeight(Ui.dp(48));
        b.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(16), Ui.dp(10));
        ImageView i = new ImageView(c);
        i.setImageDrawable(Icons.drawable(c, icon, tint ? Ui.TXT : 0));
        b.addView(i, Ui.lp(Ui.dp(20), Ui.dp(20), 0, 0, 10, 0));
        b.addView(Ui.text(c, label, 15, Ui.TXT, true));
        b.setOnClickListener(click);
        b.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        return b;
    }
}

/** Shared tool grid used by Home and Apps. */
final class Tiles {
    static final String[][] TOOLS = {
        {"Weather", "sun"}, {"News", "news"}, {"Stocks", "trend"}, {"Maps", "map"},
        {"Translate", "translate"}, {"Shorten", "link"}, {"Notes", "note"}, {"Docs", "doc"}, {"Timer", "clock"},
    };

    static Screen make(int i) {
        switch (i) {
            case 0: return new WeatherScreen();
            case 1: return new NewsScreen();
            case 2: return new StocksScreen();
            case 3: return new MapScreen();
            case 4: return new TranslatorScreen();
            case 5: return new ShortenerScreen();
            case 6: return new NotesScreen();
            case 7: return new DocsScreen();
            default: return new PomodoroScreen();
        }
    }

    static View grid(final Screen s) {
        Context c = s.c;
        GridLayout g = new GridLayout(c);
        g.setColumnCount(4);
        for (int i = 0; i < TOOLS.length; i++) {
            final int idx = i;
            LinearLayout cell = Ui.vbox(c);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(Ui.dp(2), Ui.dp(6), Ui.dp(2), Ui.dp(10));
            FrameLayout box = new FrameLayout(c);
            box.setBackground(Ui.ripple(Ui.shape(Ui.CARD, 17, Ui.LINE)));
            box.setClickable(true);
            box.setOnClickListener(v -> s.a.push(make(idx)));
            box.addView(Ui.icon(c, TOOLS[i][1], 26, Ui.ACC), new FrameLayout.LayoutParams(Ui.dp(26), Ui.dp(26), Gravity.CENTER));
            box.setContentDescription(TOOLS[i][0]);
            cell.addView(box, new LinearLayout.LayoutParams(Ui.dp(56), Ui.dp(56)));
            TextView t = Ui.text(c, TOOLS[i][0], 11, Ui.TXT, true);
            t.setGravity(Gravity.CENTER);
            cell.addView(t, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 7, 0, 0));
            GridLayout.LayoutParams p = new GridLayout.LayoutParams(GridLayout.spec(i / 4), GridLayout.spec(i % 4, 1f));
            p.width = 0;
            g.addView(cell, p);
        }
        return g;
    }
}

final class HomeScreen extends Screen {
    private LinearLayout hero;

    @Override View build() {
        LinearLayout l = col();
        final EditText q = Ui.input(c, "Search the web");
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setCompoundDrawablesWithIntrinsicBounds(Icons.drawable(c, "search", Ui.MUT), null, null, null);
        q.setCompoundDrawablePadding(Ui.dp(10));
        q.setOnEditorActionListener((v, id, ev) -> {
            String s = q.getText().toString().trim();
            if (s.isEmpty()) return true;
            SearchScreen.pendingQuery = s;
            q.setText("");
            a.selectTab(1);
            return true;
        });
        l.addView(q);

        hero = Ui.hbox(c);
        hero.setBackground(Ui.gradient(18));
        hero.setPadding(Ui.dp(18), Ui.dp(16), Ui.dp(18), Ui.dp(16));
        hero.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
        hero.setClickable(true);
        hero.setOnClickListener(v -> a.push(new WeatherScreen()));
        hero.addView(Ui.text(c, "Loading weather", 14, 0xFFFFFFFF, true));
        l.addView(hero);
        loadWeather();

        l.addView(Ui.label(c, "Tools"));
        l.addView(Tiles.grid(this));
        return scroll(l);
    }

    private void loadWeather() {
        Weather.load(this, (d, e) -> {
            hero.removeAllViews();
            if (d == null) {
                hero.addView(Ui.icon(c, "cloud", 28, 0xFFFFFFFF), Ui.lp(Ui.dp(28), Ui.dp(28), 0, 0, 12, 0));
                hero.addView(Ui.text(c, "Tap to set your weather location", 14, 0xFFFFFFFF, true));
                return;
            }
            LinearLayout left = Ui.vbox(c);
            left.addView(Ui.text(c, Math.round(d.temp) + "\u00B0" + d.unit, 36, 0xFFFFFFFF, true));
            left.addView(Ui.text(c, d.desc, 13, 0xE6FFFFFF, false));
            hero.addView(left, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            LinearLayout right = Ui.vbox(c);
            right.setGravity(Gravity.END);
            right.addView(Ui.icon(c, Weather.iconFor(d.code), 34, 0xFFFFFFFF), Ui.lp(Ui.dp(34), Ui.dp(34), 0, 0, 0, 4));
            right.addView(Ui.text(c, d.place, 12, 0xE6FFFFFF, false));
            hero.addView(right);
        });
    }
}

final class AppsScreen extends Screen {
    @Override View build() {
        LinearLayout l = col();
        l.addView(Ui.label(c, "All tools"));
        l.addView(Tiles.grid(this));
        l.addView(Ui.label(c, "Account"));
        LinearLayout card = Ui.card(c);
        TextView row = Ui.text(c, Auth.signedIn() ? Auth.email : "Sign in or create an account", 15, Ui.TXT, true);
        card.addView(row);
        card.setClickable(true);
        card.setOnClickListener(v -> a.push(new AccountScreen()));
        l.addView(card);
        return scroll(l);
    }
}

final class AccountScreen extends Screen {
    private FrameLayout holder;

    @Override String title() { return "Account"; }
    @Override View build() {
        holder = new FrameLayout(c);
        render();
        return holder;
    }
    @Override void onAuth() { if (holder != null) render(); }

    private void render() {
        holder.removeAllViews();
        LinearLayout l = col();
        if (!Auth.signedIn()) {
            l.addView(AuthView.build(this, "Sign in to sync your mail, notes and docs."));
        } else {
            LinearLayout card = Ui.card(c);
            card.addView(Ui.text(c, Auth.email == null ? "" : Auth.email, 16, Ui.TXT, true));
            final TextView addr = Ui.text(c, "360Mail address: loading", 13, Ui.MUT, false);
            card.addView(addr, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
            l.addView(card);
            Mail.address(this, (v, e) -> addr.setText(v == null ? "360Mail address: not set up" : "360Mail address: " + v));
        }

        l.addView(Ui.label(c, "Appearance"));
        final String cur = Store.get("theme", "system");
        LinearLayout seg = Ui.hbox(c);
        String[][] opts = {{"system", "System"}, {"light", "Light"}, {"dark", "Dark"}};
        for (final String[] o : opts) {
            TextView chip = Ui.chip(c, o[1], cur.equals(o[0]));
            chip.setOnClickListener(v -> { Store.put("theme", o[0]); a.recreate(); });
            seg.addView(chip);
        }
        l.addView(seg);

        l.addView(Ui.label(c, "Units"));
        LinearLayout u = Ui.hbox(c);
        final String unit = Weather.unit();
        for (final String o : new String[]{"F", "C"}) {
            TextView chip = Ui.chip(c, "\u00B0" + o, unit.equals(o));
            chip.setOnClickListener(v -> { Store.put("unit", o); a.toast("Units set to \u00B0" + o); a.recreate(); });
            u.addView(chip);
        }
        l.addView(u);

        l.addView(Ui.label(c, "Privacy"));
        LinearLayout p = Ui.card(c);
        p.addView(Ui.text(c, "No ads. No analytics. No data selling. Pages you open from search are fetched through the anonymous viewer, so sites never see your address.", 13.5f, Ui.MUT, false));
        l.addView(p);

        if (Auth.signedIn()) {
            TextView out = Ui.button(c, "Sign out", 1);
            out.setOnClickListener(v -> { Auth.signOut(); a.toast("Signed out"); });
            l.addView(Ui.spacer(c, 6));
            l.addView(out);
        }
        l.addView(Ui.text(c, "360 for Android 3.1.0 (native)", 12, Ui.MUT, false));
        holder.addView(scroll(l));
    }
}
