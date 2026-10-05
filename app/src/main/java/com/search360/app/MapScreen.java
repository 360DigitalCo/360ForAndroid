package com.search360.app;

import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

final class MapScreen extends Screen {
    private MapView map;
    private TextView info;
    @Override String title() { return "Maps"; }

    @Override View build() {
        FrameLayout root = new FrameLayout(c);
        map = new MapView(c);
        root.addView(map, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        double[] w = Weather.where(c);
        if (w != null) map.setView(w[0], w[1], 12);
        map.longPress = (la, lo) -> { map.setMarker(la, lo); info("Dropped pin  " + String.format(java.util.Locale.US, "%.5f, %.5f", la, lo)); };

        final EditText q = Ui.input(c, "Search places");
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setCompoundDrawablesWithIntrinsicBounds(Icons.drawable(c, "search", Ui.MUT), null, null, null);
        q.setCompoundDrawablePadding(Ui.dp(10));
        q.setElevation(Ui.dp(4));
        q.setOnEditorActionListener((v, id, e) -> { search(q.getText().toString().trim()); return true; });
        FrameLayout.LayoutParams qp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.TOP);
        qp.setMargins(Ui.dp(12), Ui.dp(12), Ui.dp(12), 0);
        root.addView(q, qp);

        LinearLayout ctl = Ui.vbox(c);
        ctl.addView(Ui.iconButton(c, "plus", v -> map.zoomBy(1)));
        ctl.addView(Ui.iconButton(c, "down", v -> map.zoomBy(-1)));
        ctl.addView(Ui.iconButton(c, "target", v -> a.requestLocation(() -> {
            Store.remove("wx_lat"); Store.remove("wx_lon");
            double[] p = Weather.where(c);
            if (p == null) { toast("Couldn't get your location yet."); return; }
            map.setView(p[0], p[1], 15); map.setMarker(p[0], p[1]); info("Your location");
        })));
        for (int i = 0; i < ctl.getChildCount(); i++) ((LinearLayout.LayoutParams) ctl.getChildAt(i).getLayoutParams()).setMargins(0, 0, 0, Ui.dp(8));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.END | Gravity.BOTTOM);
        cp.setMargins(0, 0, Ui.dp(12), Ui.dp(64));
        root.addView(ctl, cp);

        info = Ui.text(c, "", 14, Ui.TXT, true);
        info.setBackground(Ui.shape(Ui.CARD, 12, Ui.LINE));
        info.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        info.setVisibility(View.GONE);
        info.setElevation(Ui.dp(4));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.START);
        ip.setMargins(Ui.dp(12), 0, Ui.dp(70), Ui.dp(24));
        root.addView(info, ip);
        return root;
    }

    private void info(String s) { info.setText(s); info.setVisibility(View.VISIBLE); }

    private void search(final String q) {
        if (q.isEmpty()) return;
        a.hideKeyboard();
        async(() -> {
            Http.Resp r = Http.request("GET", "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + Http.enc(q), null, null);
            JSONArray arr = new JSONArray(r.body);
            if (arr.length() == 0) throw new Exception("No place found for \"" + q + "\"");
            return arr.getJSONObject(0);
        }, (o, e) -> {
            if (e != null) { toast(msg(e)); return; }
            double la = o.optDouble("lat"), lo = o.optDouble("lon");
            map.setView(la, lo, 14); map.setMarker(la, lo);
            info(J.s(o, "display_name"));
        });
    }
    @Override void onDestroy() { if (map != null) map.shutdown(); super.onDestroy(); }
}
