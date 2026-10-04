package com.search360.app;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/* ═════════════════════════ Weather ═════════════════════════ */
final class Weather {
    static final class Day { String label; int code; double hi, lo; }
    static final class Data {
        double temp, feels, wind; int code, humidity;
        String unit, windUnit, desc, place;
        List<Day> days = new ArrayList<>();
    }

    static String unit() { return Store.get("unit", Locale.getDefault().getCountry().equals("US") ? "F" : "C"); }

    static String iconFor(int c) {
        if (c == 0) return "sun";
        if (c <= 3) return "cloud";
        if (c == 45 || c == 48) return "fog";
        if ((c >= 51 && c <= 67) || (c >= 80 && c <= 82)) return "rain";
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "snow";
        if (c >= 95) return "storm";
        return "cloud";
    }
    static String descFor(int c) {
        if (c == 0) return "Clear sky";
        if (c == 1) return "Mostly clear";
        if (c == 2) return "Partly cloudy";
        if (c == 3) return "Overcast";
        if (c == 45 || c == 48) return "Foggy";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Rain showers";
        if (c == 85 || c == 86) return "Snow showers";
        if (c >= 95) return "Thunderstorms";
        return "Mixed conditions";
    }

    /** Stored place, else last known device location. Null if neither is available. */
    static double[] where(Context c) {
        String la = Store.get("wx_lat", null), lo = Store.get("wx_lon", null);
        if (la != null && lo != null) {
            try { return new double[]{Double.parseDouble(la), Double.parseDouble(lo)}; } catch (Exception ignored) { }
        }
        try {
            if (c.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    && c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null;
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            return best == null ? null : new double[]{best.getLatitude(), best.getLongitude()};
        } catch (Exception e) { return null; }
    }

    static void load(final Screen s, Http.Cb<Data> cb) {
        final double[] loc = where(s.c);
        if (loc == null) { cb.done(null, null); return; }
        s.async(() -> fetch(loc[0], loc[1]), cb);
    }

    static Data fetch(double lat, double lon) throws Exception {
        String u = unit();
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=7"
                + "&temperature_unit=" + (u.equals("F") ? "fahrenheit" : "celsius")
                + "&wind_speed_unit=" + (u.equals("F") ? "mph" : "kmh");
        Http.Resp r = Http.request("GET", url, null, null);
        if (r.code != 200) throw new Exception("Weather service unavailable");
        JSONObject j = new JSONObject(r.body);
        JSONObject cur = j.getJSONObject("current");
        Data d = new Data();
        d.unit = u; d.windUnit = u.equals("F") ? "mph" : "km/h";
        d.temp = cur.optDouble("temperature_2m"); d.feels = cur.optDouble("apparent_temperature");
        d.humidity = cur.optInt("relative_humidity_2m"); d.wind = cur.optDouble("wind_speed_10m");
        d.code = cur.optInt("weather_code"); d.desc = descFor(d.code);
        JSONObject dy = j.optJSONObject("daily");
        if (dy != null) {
            JSONArray t = dy.getJSONArray("time"), cd = dy.getJSONArray("weather_code"), hi = dy.getJSONArray("temperature_2m_max"), lo = dy.getJSONArray("temperature_2m_min");
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US), out = new SimpleDateFormat("EEE", Locale.getDefault());
            for (int i = 0; i < t.length(); i++) {
                Day x = new Day();
                try { x.label = i == 0 ? "Today" : out.format(in.parse(t.getString(i))); } catch (Exception e) { x.label = t.getString(i); }
                x.code = cd.optInt(i); x.hi = hi.optDouble(i); x.lo = lo.optDouble(i);
                d.days.add(x);
            }
        }
        d.place = Store.get("wx_name", null);
        if (d.place == null) d.place = Store.get("wx_devname", null);
        if (d.place == null) {
            try {
                Http.Resp g = Http.request("GET", "https://nominatim.openstreetmap.org/reverse?format=json&zoom=10&lat=" + lat + "&lon=" + lon, null, null);
                JSONObject a = new JSONObject(g.body).optJSONObject("address");
                if (a != null) {
                    d.place = Auth.firstNonEmpty(a.optString("city"), a.optString("town"), a.optString("village"), a.optString("county"), a.optString("state"));
                    if (!d.place.isEmpty()) Store.put("wx_devname", d.place);
                }
            } catch (Exception ignored) { }
        }
        if (d.place == null) d.place = "";
        return d;
    }
}

final class WeatherScreen extends Screen {
    private LinearLayout body;
    @Override String title() { return "Weather"; }

    @Override View build() {
        LinearLayout l = col();
        final EditText q = Ui.input(c, "Search a city");
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setOnEditorActionListener((v, id, e) -> { search(q.getText().toString().trim()); return true; });
        l.addView(q);
        TextView loc = Ui.button(c, "Use my location", 1);
        loc.setOnClickListener(v -> a.requestLocation(() -> {
            Store.remove("wx_lat"); Store.remove("wx_lon"); Store.remove("wx_name"); Store.remove("wx_devname");
            refresh();
        }));
        l.addView(loc);
        body = Ui.vbox(c);
        l.addView(body);
        refresh();
        return scroll(l);
    }

    private void search(final String q) {
        if (q.isEmpty()) return;
        a.hideKeyboard();
        body.removeAllViews(); body.addView(Ui.loading(c));
        async(() -> {
            Http.Resp r = Http.request("GET", "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + Http.enc(q), null, null);
            JSONArray arr = new JSONArray(r.body);
            if (arr.length() == 0) throw new Exception("No place found for \"" + q + "\"");
            return arr.getJSONObject(0);
        }, (o, e) -> {
            if (e != null) { body.removeAllViews(); body.addView(Ui.state(c, "alert", "Not found", msg(e))); return; }
            Store.put("wx_lat", o.optString("lat")); Store.put("wx_lon", o.optString("lon"));
            String name = o.optString("display_name");
            Store.put("wx_name", name.contains(",") ? name.substring(0, name.indexOf(',')) : name);
            refresh();
        });
    }

    private void refresh() {
        body.removeAllViews(); body.addView(Ui.loading(c));
        if (Weather.where(c) == null) {
            body.removeAllViews();
            body.addView(Ui.state(c, "pin", "Choose a location", "Search a city above or allow location access."));
            return;
        }
        Weather.load(this, (d, e) -> {
            body.removeAllViews();
            if (d == null) { body.addView(Ui.state(c, "alert", "Weather unavailable", msg(e))); return; }
            LinearLayout hero = Ui.vbox(c);
            hero.setBackground(Ui.gradient(18));
            hero.setPadding(Ui.dp(20), Ui.dp(20), Ui.dp(20), Ui.dp(20));
            LinearLayout top = Ui.hbox(c);
            LinearLayout left = Ui.vbox(c);
            left.addView(Ui.text(c, d.place, 14, 0xE6FFFFFF, true));
            left.addView(Ui.text(c, Math.round(d.temp) + "\u00B0" + d.unit, 52, 0xFFFFFFFF, true));
            left.addView(Ui.text(c, d.desc, 15, 0xFFFFFFFF, false));
            top.addView(left, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            top.addView(Ui.icon(c, Weather.iconFor(d.code), 64, 0xFFFFFFFF), new LinearLayout.LayoutParams(Ui.dp(64), Ui.dp(64)));
            hero.addView(top);
            hero.addView(Ui.text(c, "Feels like " + Math.round(d.feels) + "\u00B0   Humidity " + d.humidity + "%   Wind " + Math.round(d.wind) + " " + d.windUnit, 13, 0xE6FFFFFF, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 12, 0, 0));
            hero.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 4, 0, 10));
            body.addView(hero);
            body.addView(Ui.label(c, "7-day forecast"));
            for (Weather.Day x : d.days) {
                LinearLayout row = Ui.hbox(c);
                row.setBackground(Ui.shape(Ui.CARD, 12, Ui.LINE));
                row.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
                row.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
                TextView day = Ui.text(c, x.label, 14, Ui.TXT, true);
                row.addView(day, new LinearLayout.LayoutParams(Ui.dp(64), Ui.WRAP));
                row.addView(Ui.icon(c, Weather.iconFor(x.code), 22, Ui.ACC), Ui.lp(Ui.dp(22), Ui.dp(22), 0, 0, 10, 0));
                TextView ds = Ui.text(c, Weather.descFor(x.code), 13, Ui.MUT, false);
                row.addView(ds, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
                row.addView(Ui.text(c, Math.round(x.hi) + "\u00B0 / " + Math.round(x.lo) + "\u00B0", 14, Ui.TXT, true));
                body.addView(row);
            }
        });
    }
}

/* ═════════════════════════ News ═════════════════════════ */
final class NewsScreen extends Screen {
    private static final String[][] TOPICS = {
        {"Top", "top news today"}, {"World", "world news today"}, {"Tech", "technology news today"},
        {"Business", "business news today"}, {"Science", "science news today"}, {"Sports", "sports news today"}, {"Health", "health news today"},
    };
    private LinearLayout list;
    private int topic = 0;
    @Override String title() { return "News"; }

    @Override View build() {
        LinearLayout l = col();
        final LinearLayout chips = Ui.hbox(c);
        for (int i = 0; i < TOPICS.length; i++) {
            final int idx = i;
            TextView ch = Ui.chip(c, TOPICS[i][0], i == topic);
            ch.setOnClickListener(v -> {
                topic = idx;
                for (int k = 0; k < chips.getChildCount(); k++) {
                    TextView t = (TextView) chips.getChildAt(k);
                    boolean on = k == idx;
                    t.setTextColor(on ? 0xFFFFFFFF : Ui.TXT);
                    t.setBackground(Ui.ripple(on ? Ui.shape(Ui.ACC, 20, 0) : Ui.shape(Ui.CARD, 20, Ui.LINE)));
                }
                load();
            });
            chips.addView(ch);
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        l.addView(hs, Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        list = Ui.vbox(c);
        l.addView(list);
        load();
        return scroll(l);
    }

    private void load() {
        list.removeAllViews(); list.addView(Ui.loading(c));
        final String q = TOPICS[topic][1];
        async(() -> Api.fn("search", "POST", null, new JSONObject().put("q", q).put("tab", "news")), (j, e) -> {
            list.removeAllViews();
            if (e != null) { list.addView(Ui.state(c, "alert", "Couldn't load news", msg(e))); return; }
            JSONArray arr = j.optJSONArray("news");
            if (arr == null || arr.length() == 0) { list.addView(Ui.state(c, "news", "No stories right now", "Try another topic.")); return; }
            for (int i = 0; i < arr.length(); i++) list.addView(row(arr.optJSONObject(i)));
        });
    }

    private View row(final JSONObject o) {
        LinearLayout r = Ui.hbox(c);
        r.setGravity(Gravity.TOP);
        r.setBackground(Ui.ripple(Ui.shape(Ui.CARD, 14, Ui.LINE)));
        r.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        r.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 9));
        LinearLayout b = Ui.vbox(c);
        b.addView(Ui.text(c, o.optString("title"), 15, Ui.TXT, true));
        String meta = o.optString("source") + (o.optString("age").isEmpty() ? "" : "  \u2022  " + o.optString("age"));
        b.addView(Ui.text(c, meta, 12, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        String d = o.optString("desc");
        if (!d.isEmpty()) { TextView t = Ui.text(c, d, 13, Ui.MUT, false); t.setMaxLines(3); t.setEllipsize(android.text.TextUtils.TruncateAt.END); b.addView(t, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 0)); }
        r.addView(b, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        String th = o.optString("thumb");
        if (!th.isEmpty()) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackground(Ui.shape(Ui.LINE, 10, 0));
            iv.setClipToOutline(true);
            r.addView(iv, Ui.lp(Ui.dp(84), Ui.dp(84), 12, 0, 0, 0));
            Img.load(th, iv);
        }
        r.setOnClickListener(v -> a.push(new ReaderScreen(o.optString("url"), o.optString("title"))));
        return r;
    }
}

/* ═════════════════════════ Stocks ═════════════════════════ */
final class LineChartView extends View {
    double[][] series = new double[0][];
    int[] colors = new int[0];
    String[] xLabels = new String[0];
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), grid = new Paint(), txt = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint();
    private int touch = -1;

    LineChartView(Context c) {
        super(c);
        line.setStyle(Paint.Style.STROKE); line.setStrokeJoin(Paint.Join.ROUND); line.setStrokeCap(Paint.Cap.ROUND);
        grid.setColor(Ui.LINE); grid.setStrokeWidth(Ui.dp(1));
        txt.setColor(Ui.MUT); txt.setTextSize(Ui.dp(10.5f));
    }
    void set(double[][] s, int[] col, String[] x) { series = s; colors = col; xLabels = x; touch = -1; invalidate(); }

    @Override protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        if (series.length == 0 || series[0].length < 2) {
            txt.setTextAlign(Paint.Align.CENTER);
            cv.drawText("No chart data", getWidth() / 2f, getHeight() / 2f, txt);
            return;
        }
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double[] s : series) for (double v : s) if (!Double.isNaN(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        if (lo == Double.MAX_VALUE) return;
        if (hi - lo < 1e-9) { hi += 1; lo -= 1; }
        double pad = (hi - lo) * 0.06; lo -= pad; hi += pad;
        float l = Ui.dp(6), r = Ui.dp(44), t = Ui.dp(8), b = Ui.dp(22);
        float w = getWidth() - l - r, h = getHeight() - t - b;
        txt.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i <= 3; i++) {
            float y = t + h * i / 3f;
            cv.drawLine(l, y, l + w, y, grid);
            cv.drawText(fmt(hi - (hi - lo) * i / 3.0), l + w + Ui.dp(4), y + Ui.dp(4), txt);
        }
        int n = series[0].length;
        for (int si = 0; si < series.length; si++) {
            double[] s = series[si];
            Path p = new Path(); boolean started = false; float lastX = 0, firstX = 0;
            for (int i = 0; i < s.length; i++) {
                if (Double.isNaN(s[i])) continue;
                float x = l + w * i / (float) (n - 1), y = t + (float) (h * (hi - s[i]) / (hi - lo));
                if (!started) { p.moveTo(x, y); firstX = x; started = true; } else p.lineTo(x, y);
                lastX = x;
            }
            if (!started) continue;
            if (si == 0) {
                Path f = new Path(p);
                f.lineTo(lastX, t + h); f.lineTo(firstX, t + h); f.close();
                fill.setShader(new LinearGradient(0, t, 0, t + h, (colors[0] & 0x00FFFFFF) | 0x44000000, (colors[0] & 0x00FFFFFF), Shader.TileMode.CLAMP));
                cv.drawPath(f, fill);
            }
            line.setColor(colors[si]); line.setStrokeWidth(Ui.dp(si == 0 ? 2f : 1.4f));
            cv.drawPath(p, line);
        }
        txt.setTextAlign(Paint.Align.LEFT);
        if (xLabels.length >= 3) {
            cv.drawText(xLabels[0], l, getHeight() - Ui.dp(5), txt);
            txt.setTextAlign(Paint.Align.CENTER);
            cv.drawText(xLabels[1], l + w / 2, getHeight() - Ui.dp(5), txt);
            txt.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(xLabels[2], l + w, getHeight() - Ui.dp(5), txt);
        }
        if (touch >= 0 && touch < n && !Double.isNaN(series[0][touch])) {
            float x = l + w * touch / (float) (n - 1), y = t + (float) (h * (hi - series[0][touch]) / (hi - lo));
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG); g.setColor(Ui.MUT); g.setStrokeWidth(Ui.dp(1));
            cv.drawLine(x, t, x, t + h, g);
            g.setColor(colors[0]); cv.drawCircle(x, y, Ui.dp(4), g);
            txt.setColor(Ui.TXT); txt.setTextAlign(x > getWidth() / 2f ? Paint.Align.RIGHT : Paint.Align.LEFT);
            cv.drawText(fmt(series[0][touch]), x + (x > getWidth() / 2f ? -Ui.dp(8) : Ui.dp(8)), t + Ui.dp(12), txt);
            txt.setColor(Ui.MUT);
        }
    }
    static String fmt(double v) { return Math.abs(v) >= 1000 ? String.format(Locale.US, "%.0f", v) : String.format(Locale.US, "%.2f", v); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (series.length == 0 || series[0].length < 2) return false;
        getParent().requestDisallowInterceptTouchEvent(true);
        float l = Ui.dp(6), w = getWidth() - l - Ui.dp(44);
        touch = Math.max(0, Math.min(series[0].length - 1, Math.round((e.getX() - l) / w * (series[0].length - 1))));
        if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) touch = -1;
        invalidate();
        return true;
    }
}

final class StocksScreen extends Screen {
    private static final String[] WATCH = {"AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "TSLA", "META", "SPY"};
    private static final String[] RANGES = {"1d", "5d", "1mo", "6mo", "1y", "5y", "max"};
    private String symbol, range;
    private LinearLayout out, sugg;
    private int req = 0;
    @Override String title() { return "Stocks"; }

    @Override View build() {
        symbol = Store.get("st_sym", "AAPL"); range = Store.get("st_rng", "6mo");
        LinearLayout l = col();
        final EditText q = Ui.input(c, "Search a company or ticker");
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        sugg = Ui.vbox(c);
        q.setOnEditorActionListener((v, id, e) -> { String s = q.getText().toString().trim(); if (!s.isEmpty()) lookup(s); return true; });
        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable[] pending = new Runnable[1];
        q.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a1, int b, int c1) { }
            public void onTextChanged(CharSequence s, int a1, int b, int c1) { }
            public void afterTextChanged(android.text.Editable s) {
                if (pending[0] != null) h.removeCallbacks(pending[0]);
                final String t = s.toString().trim();
                if (t.length() < 2) { sugg.removeAllViews(); return; }
                pending[0] = () -> suggest(t);
                h.postDelayed(pending[0], 300);
            }
        });
        l.addView(q);
        l.addView(sugg);
        LinearLayout chips = Ui.hbox(c);
        for (final String w : WATCH) { TextView ch = Ui.chip(c, w, false); ch.setOnClickListener(v -> { symbol = w; load(); }); chips.addView(ch); }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false); hs.addView(chips);
        l.addView(hs, Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        out = Ui.vbox(c);
        l.addView(out);
        load();
        return scroll(l);
    }

    private void lookup(String s) {
        if (s.matches("^[A-Za-z.\\-^=]{1,8}$")) { symbol = s.toUpperCase(Locale.US); sugg.removeAllViews(); a.hideKeyboard(); load(); }
        else suggest(s);
    }

    private void suggest(final String q) {
        async(() -> Api.fn("stock-data", "GET", "action=search&q=" + Http.enc(q), null), (j, e) -> {
            sugg.removeAllViews();
            if (e != null || j == null) return;
            JSONArray arr = j.optJSONArray("quotes");
            if (arr == null) return;
            LinearLayout box = Ui.vbox(c);
            box.setBackground(Ui.shape(Ui.CARD, 12, Ui.LINE));
            box.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
            for (int i = 0; i < Math.min(6, arr.length()); i++) {
                final JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                TextView t = Ui.text(c, o.optString("symbol") + "  \u2022  " + o.optString("name") + (o.optString("exchange").isEmpty() ? "" : "  (" + o.optString("exchange") + ")"), 14, Ui.TXT, false);
                t.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
                t.setBackground(Ui.ripple(null)); t.setClickable(true);
                t.setOnClickListener(v -> { symbol = o.optString("symbol"); sugg.removeAllViews(); a.hideKeyboard(); load(); });
                box.addView(t);
            }
            if (box.getChildCount() > 0) sugg.addView(box);
        });
    }

    private void load() {
        Store.put("st_sym", symbol); Store.put("st_rng", range);
        out.removeAllViews(); out.addView(Ui.loading(c));
        final int my = ++req; final String sym = symbol, rg = range;
        async(() -> Api.fn("stock-data", "GET", "symbol=" + Http.enc(sym) + "&range=" + Http.enc(rg), null), (d, e) -> {
            if (my != req) return;
            out.removeAllViews();
            if (e != null) { out.addView(Ui.state(c, "alert", "Couldn't load " + sym, msg(e))); return; }
            render(d);
        });
    }

    private static String num(double v, int dec) { return Double.isNaN(v) ? "\u2014" : String.format(Locale.US, "%,." + dec + "f", v); }
    private static String compact(double v) {
        if (Double.isNaN(v)) return "\u2014";
        double a = Math.abs(v);
        if (a >= 1e12) return String.format(Locale.US, "%.2fT", v / 1e12);
        if (a >= 1e9) return String.format(Locale.US, "%.2fB", v / 1e9);
        if (a >= 1e6) return String.format(Locale.US, "%.2fM", v / 1e6);
        if (a >= 1e3) return String.format(Locale.US, "%.1fK", v / 1e3);
        return String.format(Locale.US, "%.0f", v);
    }
    private static String pct(double v, boolean already) { if (Double.isNaN(v)) return "\u2014"; double x = already ? v : v * 100; return (x >= 0 ? "+" : "") + String.format(Locale.US, "%.2f%%", x); }
    private static double dbl(JSONObject o, String k) { return o.isNull(k) ? Double.NaN : o.optDouble(k, Double.NaN); }

    private void render(JSONObject d) {
        LinearLayout hd = Ui.card(c);
        hd.addView(Ui.text(c, d.optString("companyName") + (d.optString("exchangeName").isEmpty() ? "" : "  \u2022  " + d.optString("exchangeName")), 13, Ui.MUT, false));
        hd.addView(Ui.text(c, d.optString("symbol"), 12, Ui.MUT, true));
        double last = dbl(d, "lastClose"), ch = dbl(d, "changePct");
        hd.addView(Ui.text(c, num(last, 2) + (d.optString("currency").isEmpty() ? "" : " " + d.optString("currency")), 32, Ui.TXT, true), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        hd.addView(Ui.text(c, Double.isNaN(ch) ? "\u2014" : (ch >= 0 ? "Up " : "Down ") + pct(ch, true) + " today", 14, ch >= 0 ? Ui.OK : Ui.BAD, true));
        out.addView(hd);

        LinearLayout rg = Ui.hbox(c);
        for (final String r : RANGES) {
            TextView ch2 = Ui.chip(c, r.toUpperCase(Locale.US), r.equals(range));
            ch2.setOnClickListener(v -> { range = r; load(); });
            rg.addView(ch2);
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false); hs.addView(rg);
        out.addView(hs, Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));

        JSONObject se = d.optJSONObject("series");
        if (se != null && se.optJSONArray("closes") != null) {
            double[] cl = arr(se.optJSONArray("closes")), s20 = arr(se.optJSONArray("sma20")), s50 = arr(se.optJSONArray("sma50"));
            JSONArray ts = se.optJSONArray("timestamps");
            String[] xl = new String[]{"", "", ""};
            if (ts != null && ts.length() > 2) {
                boolean intraday = range.equals("1d") || range.equals("5d");
                SimpleDateFormat f = new SimpleDateFormat(intraday ? "HH:mm" : (range.equals("5y") || range.equals("max") ? "MMM yy" : "MMM d"), Locale.getDefault());
                xl[0] = f.format(new Date(ts.optLong(0) * 1000)); xl[1] = f.format(new Date(ts.optLong(ts.length() / 2) * 1000)); xl[2] = f.format(new Date(ts.optLong(ts.length() - 1) * 1000));
            }
            LineChartView cv = new LineChartView(c);
            boolean hasMa = s20.length == cl.length && s50.length == cl.length;
            cv.set(hasMa ? new double[][]{cl, s20, s50} : new double[][]{cl}, hasMa ? new int[]{Ui.ACC, 0xFFF59E0B, 0xFFA21CAF} : new int[]{Ui.ACC}, xl);
            cv.setLayoutParams(Ui.lp(Ui.MATCH, Ui.dp(210), 0, 0, 0, 4));
            out.addView(cv);
            if (hasMa) {
                LinearLayout lg = Ui.hbox(c);
                lg.addView(Ui.text(c, "Close  ", 11, Ui.ACC, true)); lg.addView(Ui.text(c, "SMA 20  ", 11, 0xFFF59E0B, true)); lg.addView(Ui.text(c, "SMA 50", 11, 0xFFA21CAF, true));
                out.addView(lg);
            }
        }

        out.addView(Ui.label(c, "Key stats"));
        String[][] rows = {
            {"Day range", range(dbl(d, "dayLow"), dbl(d, "dayHigh"))}, {"52-week range", range(dbl(d, "fiftyTwoWeekLow"), dbl(d, "fiftyTwoWeekHigh"))},
            {"Market cap", compact(dbl(d, "marketCap"))}, {"Volume", compact(dbl(d, "volume"))}, {"Avg volume", compact(dbl(d, "avgVolume"))},
            {"P/E (TTM)", num(dbl(d, "peRatio"), 2)}, {"Forward P/E", num(dbl(d, "forwardPE"), 2)}, {"Beta", num(dbl(d, "beta"), 2)},
            {"Dividend yield", pct(dbl(d, "dividendYield"), false)}, {"Sector", d.optString("sector", "\u2014")}, {"Industry", d.optString("industry", "\u2014")},
        };
        out.addView(kv(rows));

        String rec = d.optString("recommendationKey", "");
        double tgt = dbl(d, "targetMeanPrice"), rgw = dbl(d, "revenueGrowth"), pm = dbl(d, "profitMargins");
        if (!rec.isEmpty() || !Double.isNaN(tgt) || !Double.isNaN(rgw) || !Double.isNaN(pm)) {
            out.addView(Ui.label(c, "Analysts"));
            out.addView(kv(new String[][]{{"Rating", rec.isEmpty() ? "\u2014" : rec.replace('_', ' ').toUpperCase(Locale.US)}, {"Target (avg)", num(tgt, 2)}, {"Revenue growth", pct(rgw, false)}, {"Profit margin", pct(pm, false)}}));
        }
        JSONObject tech = d.optJSONObject("technical");
        if (tech != null) {
            out.addView(Ui.label(c, "Technical outlook"));
            String lab = tech.optString("outlookLabel", "Neutral / Mixed");
            LinearLayout card = Ui.card(c);
            card.addView(Ui.text(c, lab, 16, lab.contains("Bullish") ? Ui.OK : lab.contains("Bearish") ? Ui.BAD : Ui.WARN, true));
            JSONArray sg = tech.optJSONArray("signals");
            if (sg != null) for (int i = 0; i < sg.length(); i++) card.addView(Ui.text(c, "\u2022 " + sg.optString(i), 13, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 6, 0, 0));
            out.addView(card);
        }
        out.addView(Ui.text(c, "Market data is delayed and for information only. Not investment advice.", 11.5f, Ui.MUT, false), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
    }

    private static String range(double lo, double hi) { return Double.isNaN(lo) || Double.isNaN(hi) ? "\u2014" : num(lo, 2) + " \u2013 " + num(hi, 2); }
    private static double[] arr(JSONArray a) {
        if (a == null) return new double[0];
        double[] r = new double[a.length()];
        for (int i = 0; i < r.length; i++) r[i] = a.isNull(i) ? Double.NaN : a.optDouble(i, Double.NaN);
        return r;
    }
    private View kv(String[][] rows) {
        LinearLayout box = Ui.card(c);
        for (int i = 0; i < rows.length; i++) {
            LinearLayout r = Ui.hbox(c);
            r.setPadding(0, Ui.dp(7), 0, Ui.dp(7));
            r.addView(Ui.text(c, rows[i][0], 13, Ui.MUT, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            TextView v = Ui.text(c, rows[i][1], 13.5f, Ui.TXT, true);
            v.setGravity(Gravity.END);
            r.addView(v, new LinearLayout.LayoutParams(0, Ui.WRAP, 1.2f));
            box.addView(r);
        }
        return box;
    }
}

/* ═════════════════════════ Translator ═════════════════════════ */
final class TranslatorScreen extends Screen {
    static final String[] LANGS = ("Auto-Detect,English,Spanish,Mandarin Chinese,French,Arabic,Hindi,Portuguese,Russian,German,Japanese,Italian,Dutch,Polish,Swedish,Norwegian,Danish,Finnish,Greek,Czech,Romanian,Hungarian,Slovak,Bulgarian,Croatian,Serbian,Ukrainian,Catalan,Lithuanian,Latvian,Estonian,Slovenian,Albanian,Macedonian,Maltese,Welsh,Irish,Korean,Vietnamese,Thai,Indonesian,Malay,Filipino,Bengali,Urdu,Punjabi,Tamil,Telugu,Marathi,Gujarati,Kannada,Malayalam,Sinhala,Nepali,Burmese,Khmer,Lao,Mongolian,Tibetan,Turkish,Persian,Hebrew,Kurdish,Swahili,Amharic,Hausa,Yoruba,Igbo,Zulu,Afrikaans,Somali,Azerbaijani,Kazakh,Uzbek,Georgian,Armenian,Latin,Esperanto").split(",");
    private String from = "Auto-Detect", to = "Spanish";
    private TextView fromBtn, toBtn, result;
    @Override String title() { return "Translator"; }

    @Override View build() {
        LinearLayout l = col();
        LinearLayout pick = Ui.hbox(c);
        fromBtn = Ui.chip(c, from, false); toBtn = Ui.chip(c, to, true);
        fromBtn.setOnClickListener(v -> choose(true)); toBtn.setOnClickListener(v -> choose(false));
        pick.addView(fromBtn);
        FrameLayout sw = Ui.iconButton(c, "swap", v -> {
            if (from.equals("Auto-Detect")) { a.toast("Pick a source language to swap."); return; }
            String t = from; from = to; to = t; sync();
        });
        pick.addView(sw); pick.addView(toBtn);
        l.addView(pick, Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        final EditText in = Ui.input(c, "Enter text to translate");
        in.setSingleLine(false); in.setMinLines(5); in.setGravity(Gravity.TOP);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        l.addView(in);
        final TextView go = Ui.button(c, "Translate", 0);
        l.addView(go);
        result = Ui.text(c, "", 16, Ui.TXT, false);
        result.setTextIsSelectable(true);
        LinearLayout card = Ui.card(c);
        card.addView(result);
        final TextView copy = Ui.button(c, "Copy translation", 1);
        copy.setOnClickListener(v -> { if (result.getText().length() > 0) copyText(c, result.getText().toString()); a.toast("Copied"); });
        card.addView(copy, Ui.lp(Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        card.setVisibility(View.GONE);
        l.addView(card);
        go.setOnClickListener(v -> {
            final String text = in.getText().toString().trim();
            if (text.isEmpty()) { a.toast("Enter some text first."); return; }
            if (!from.equals("Auto-Detect") && from.equals(to)) { a.toast("Source and target languages are the same."); return; }
            a.hideKeyboard(); go.setEnabled(false); card.setVisibility(View.VISIBLE); result.setTextColor(Ui.MUT); result.setText("Translating\u2026");
            async(() -> Api.fn("dynamic-endpoint", "POST", null, new JSONObject().put("text", text).put("from", from).put("to", to)), (j, e) -> {
                go.setEnabled(true);
                if (e != null) { result.setTextColor(Ui.BAD); result.setText("Translation failed: " + msg(e)); return; }
                String t = j.optString("translated", "");
                result.setTextColor(Ui.TXT);
                result.setText(t.isEmpty() ? "No translation returned. Please try again." : t);
            });
        });
        return scroll(l);
    }
    private void sync() { fromBtn.setText(from); toBtn.setText(to); }
    private void choose(final boolean source) {
        final List<String> opts = new ArrayList<>();
        for (String s : LANGS) if (source || !s.equals("Auto-Detect")) opts.add(s);
        new AlertDialog.Builder(a).setTitle(source ? "Translate from" : "Translate to")
            .setItems(opts.toArray(new String[0]), (d, i) -> { if (source) from = opts.get(i); else to = opts.get(i); sync(); }).show();
    }
    static void copyText(Context c, String s) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("360", s));
    }
}

/* ═════════════════════════ URL shortener ═════════════════════════ */
final class ShortenerScreen extends Screen {
    @Override String title() { return "URL Shortener"; }
    @Override View build() {
        LinearLayout l = col();
        final EditText in = Ui.input(c, "Paste a long link");
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        l.addView(in);
        final TextView go = Ui.button(c, "Shorten", 0);
        l.addView(go);
        final LinearLayout out = Ui.vbox(c);
        l.addView(out);
        go.setOnClickListener(v -> {
            String u = in.getText().toString().trim();
            if (u.isEmpty()) { a.toast("Paste a link first."); return; }
            if (!u.matches("(?i)^https?://.*")) u = "https://" + u;
            final String url = u;
            a.hideKeyboard(); go.setEnabled(false); out.removeAllViews(); out.addView(Ui.loading(c));
            async(() -> Api.fn("smooth-endpoint", "POST", null, new JSONObject().put("url", url)), (j, e) -> {
                go.setEnabled(true); out.removeAllViews();
                final String sh = j == null ? "" : j.optString("shortUrl", "");
                if (e != null || sh.isEmpty()) { out.addView(Ui.state(c, "alert", "Couldn't shorten that link", e == null ? "Check the link and try again." : msg(e))); return; }
                LinearLayout card = Ui.card(c);
                TextView t = Ui.text(c, sh, 17, Ui.ACC, true);
                t.setTextIsSelectable(true);
                card.addView(t);
                TextView copy = Ui.button(c, "Copy", 1);
                copy.setOnClickListener(x -> { TranslatorScreen.copyText(c, sh); a.toast("Copied"); });
                TextView share = Ui.button(c, "Share", 1);
                share.setOnClickListener(x -> { Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, sh); a.startActivity(Intent.createChooser(i, "Share link")); });
                card.addView(copy, Ui.lp(Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
                card.addView(share);
                out.addView(card);
            });
        });
        return scroll(l);
    }
}

/* ═════════════════════════ Pomodoro ═════════════════════════ */
final class RingView extends View {
    float progress = 1f; String label = "25:00", sub = "Focus";
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG), tp = new Paint(Paint.ANTI_ALIAS_FLAG), sp = new Paint(Paint.ANTI_ALIAS_FLAG);
    RingView(Context c) {
        super(c);
        track.setStyle(Paint.Style.STROKE); track.setStrokeWidth(Ui.dp(12)); track.setColor(Ui.LINE);
        arc.setStyle(Paint.Style.STROKE); arc.setStrokeWidth(Ui.dp(12)); arc.setStrokeCap(Paint.Cap.ROUND); arc.setColor(Ui.ACC);
        tp.setColor(Ui.TXT); tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(Ui.dp(54)); tp.setFakeBoldText(true);
        sp.setColor(Ui.MUT); sp.setTextAlign(Paint.Align.CENTER); sp.setTextSize(Ui.dp(14));
    }
    @Override protected void onDraw(Canvas cv) {
        float s = Math.min(getWidth(), getHeight()), pad = Ui.dp(14);
        RectF r = new RectF((getWidth() - s) / 2 + pad, (getHeight() - s) / 2 + pad, (getWidth() + s) / 2 - pad, (getHeight() + s) / 2 - pad);
        cv.drawArc(r, 0, 360, false, track);
        cv.drawArc(r, -90, 360 * Math.max(0f, Math.min(1f, progress)), false, arc);
        cv.drawText(label, getWidth() / 2f, getHeight() / 2f + Ui.dp(14), tp);
        cv.drawText(sub, getWidth() / 2f, getHeight() / 2f + Ui.dp(44), sp);
    }
}

final class PomodoroScreen extends Screen {
    private static final int[] MINS = {25, 5, 15};
    private static final String[] NAMES = {"Focus", "Short break", "Long break"};
    private final Handler h = new Handler(Looper.getMainLooper());
    private RingView ring; private TextView startBtn, count;
    private int mode = 0, done = 0;
    private long remainMs = MINS[0] * 60000L, endAt = 0;
    private boolean running = false;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            remainMs = Math.max(0, endAt - System.currentTimeMillis());
            paint();
            if (remainMs <= 0) { finish(); return; }
            h.postDelayed(this, 250);
        }
    };
    @Override String title() { return "Timer"; }

    @Override View build() {
        LinearLayout l = col();
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        final LinearLayout chips = Ui.hbox(c);
        for (int i = 0; i < 3; i++) {
            final int m = i;
            TextView ch = Ui.chip(c, NAMES[i], i == mode);
            ch.setOnClickListener(v -> { setMode(m); for (int k = 0; k < 3; k++) { TextView t = (TextView) chips.getChildAt(k); boolean on = k == m; t.setTextColor(on ? 0xFFFFFFFF : Ui.TXT); t.setBackground(Ui.ripple(on ? Ui.shape(Ui.ACC, 20, 0) : Ui.shape(Ui.CARD, 20, Ui.LINE))); } });
            chips.addView(ch);
        }
        l.addView(chips, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 0, 0, 14));
        ring = new RingView(c);
        l.addView(ring, Ui.lp(Ui.dp(270), Ui.dp(270), 0, 0, 0, 14));
        startBtn = Ui.button(c, "Start", 0);
        startBtn.setOnClickListener(v -> toggle());
        l.addView(startBtn);
        TextView reset = Ui.button(c, "Reset", 1);
        reset.setOnClickListener(v -> setMode(mode));
        l.addView(reset);
        count = Ui.text(c, "", 14, Ui.MUT, false);
        l.addView(count);
        paint();
        return scroll(l);
    }

    private void setMode(int m) {
        stop(); mode = m; remainMs = MINS[m] * 60000L; paint();
    }
    private void toggle() {
        if (running) { stop(); return; }
        running = true; endAt = System.currentTimeMillis() + remainMs;
        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        startBtn.setText("Pause"); h.post(tick);
    }
    private void stop() {
        running = false; h.removeCallbacks(tick);
        a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (startBtn != null) startBtn.setText("Start");
    }
    private void finish() {
        stop();
        if (mode == 0) done++;
        a.toast(mode == 0 ? "Focus session complete. Take a break." : "Break over. Ready to focus?");
        setMode(mode == 0 ? (done % 4 == 0 ? 2 : 1) : 0);
    }
    private void paint() {
        if (ring == null) return;
        long s = (remainMs + 999) / 1000;
        ring.label = String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
        ring.sub = NAMES[mode];
        ring.progress = remainMs / (float) (MINS[mode] * 60000L);
        ring.invalidate();
        count.setText("Completed today: " + done);
    }
    @Override void onDestroy() { stop(); super.onDestroy(); }
}
