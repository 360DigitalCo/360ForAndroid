package com.search360.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Null-safe JSON reads. optString() returns the literal text "null" for JSON null, which then shows up in the UI. */
final class J {
    static String s(JSONObject o, String k) { return s(o, k, ""); }
    static String s(JSONObject o, String k, String def) {
        if (o == null || !o.has(k) || o.isNull(k)) return def;
        String v = o.optString(k, def);
        return v == null || v.equals("null") ? def : v;
    }
}

/** Minimal HTTP + background-thread helpers (HttpURLConnection, HTTPS only). */
final class Http {
    static final ExecutorService POOL = Executors.newFixedThreadPool(6);
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Task<T> { T run() throws Exception; }
    interface Cb<T> { void done(T value, Exception error); }
    interface LineCb { boolean line(String line); }   // return false to stop

    static final class Resp { int code; String body = ""; }

    /** Run off the main thread, deliver on the main thread. */
    static <T> void async(final Task<T> task, final Cb<T> cb) {
        POOL.execute(() -> {
            T v = null; Exception err = null;
            try { v = task.run(); } catch (Exception e) { err = e; }
            final T fv = v; final Exception fe = err;
            MAIN.post(() -> cb.done(fv, fe));
        });
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    static HttpURLConnection open(String method, String url, Map<String, String> h, String body) throws IOException {
        return open(method, url, h, body, 30000);
    }

    static HttpURLConnection open(String method, String url, Map<String, String> h, String body, int readMs) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("HTTPS required");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try { c.setRequestMethod(method); }
        catch (java.net.ProtocolException pe) {
            if (!"PATCH".equals(method)) throw pe;
            forceMethod(c, "PATCH");
        }
        c.setConnectTimeout(15000);
        c.setReadTimeout(readMs);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "360App/3.1 (Android)");
        if (h != null) for (Map.Entry<String, String> e : h.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
        if (body != null) {
            byte[] b = body.getBytes("UTF-8");
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(b.length);
            OutputStream o = c.getOutputStream();
            o.write(b);
            o.close();
        }
        return c;
    }

    /** Some HttpURLConnection stacks reject PATCH; set the verb on the (delegate) connection directly. */
    private static void forceMethod(HttpURLConnection c, String m) throws IOException {
        try {
            Class<?> k = c.getClass();
            while (k != null) {
                try {
                    java.lang.reflect.Field f = k.getDeclaredField("method");
                    f.setAccessible(true); f.set(c, m);
                } catch (NoSuchFieldException ignored) { }
                try {
                    java.lang.reflect.Field d = k.getDeclaredField("delegate");
                    d.setAccessible(true);
                    Object del = d.get(c);
                    if (del != null) {
                        Class<?> dk = del.getClass();
                        while (dk != null) {
                            try { java.lang.reflect.Field f2 = dk.getDeclaredField("method"); f2.setAccessible(true); f2.set(del, m); } catch (NoSuchFieldException ignored) { }
                            dk = dk.getSuperclass();
                        }
                    }
                } catch (NoSuchFieldException ignored) { }
                k = k.getSuperclass();
            }
        } catch (Exception e) { throw new java.net.ProtocolException("PATCH not supported on this device"); }
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    static Resp request(String method, String url, Map<String, String> h, String body) throws IOException {
        HttpURLConnection c = open(method, url, h, body);
        try {
            Resp r = new Resp();
            r.code = c.getResponseCode();
            InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
            r.body = in == null ? "" : readAll(in);
            return r;
        } finally { c.disconnect(); }
    }

    /** Stream a response line-by-line (server-sent events). Blocking: call from a worker thread. */
    static void stream(String method, String url, Map<String, String> h, String body, LineCb cb) throws IOException {
        HttpURLConnection c = open(method, url, h, body, 120000);   // first token can be slow
        try {
            int code = c.getResponseCode();
            if (code >= 400) {
                InputStream e = c.getErrorStream();
                String raw = e != null ? readAll(e) : "";
                JSONObject j = Api.parse(raw);
                String m = Api.errorOf(j);
                if (m == null) m = J.s(j, "reply", J.s(j, "message", J.s(j, "msg", "Request failed (" + code + ")")));
                throw new Api.ApiError(code, m);
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) if (!cb.line(line)) break;
            br.close();
        } finally { c.disconnect(); }
    }
}

/** Supabase REST + Edge Functions, using the same endpoints/headers as the website. */
final class Api {
    static final String SB = "https://wiswfpfsjiowtrdyqpxy.supabase.co";
    static final String ANON = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Indpc3dmcGZzamlvd3RyZHlxcHh5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjgzMzg4OTcsImV4cCI6MjA4MzkxNDg5N30.z_4FtM2c8UwgrRlafPYjolQuod4IoHQats95XHio1zM";

    static final class ApiError extends IOException {
        final int code;
        ApiError(int code, String msg) { super(msg); this.code = code; }
    }

    /** The website sends BOTH headers; functions that verify JWTs reject apikey-only calls. */
    static Map<String, String> headers(boolean json) {
        Map<String, String> h = new HashMap<>();
        h.put("apikey", ANON);
        h.put("Authorization", "Bearer " + (Auth.access != null ? Auth.access : ANON));
        if (json) h.put("Content-Type", "application/json");
        return h;
    }

    static JSONObject parse(String body) {
        try {
            String t = body == null ? "" : body.trim();
            if (t.isEmpty()) return new JSONObject();
            if (t.startsWith("[")) return new JSONObject().put("items", new JSONArray(t));
            return new JSONObject(t);
        } catch (Exception e) { return new JSONObject(); }
    }
    static String errorOf(JSONObject j) {
        Object e = j.opt("error");
        if (e instanceof JSONObject) return ((JSONObject) e).optString("message", "Request failed");
        if (e != null && e != JSONObject.NULL) return String.valueOf(e);
        return null;
    }

    /** Edge Function call; `query` is an already-encoded query string or null. */
    static JSONObject fn(String name, String method, String query, JSONObject body) throws Exception {
        Auth.refreshIfNeeded();
        String url = SB + "/functions/v1/" + name + (query == null ? "" : "?" + query);
        Http.Resp r = Http.request(method, url, headers(body != null), body == null ? null : body.toString());
        if (r.code == 401 && Auth.signedIn() && Auth.forceRefresh()) r = Http.request(method, url, headers(body != null), body == null ? null : body.toString());
        JSONObject j = parse(r.body);
        String err = errorOf(j);
        if (r.code >= 400 && err == null) err = Auth.firstNonEmpty(J.s(j, "message"), J.s(j, "msg"), "Request failed (" + r.code + ")");
        if (r.code >= 400 || err != null) throw new ApiError(r.code, err);
        return j;
    }

    /** PostgREST call. `path` like "notes?select=*&order=updated_at.desc". Returns rows (empty for minimal responses). */
    static JSONArray rest(String method, String path, JSONObject body, boolean returnRows) throws Exception {
        Auth.refreshIfNeeded();
        Map<String, String> h = headers(body != null);
        h.put("Prefer", returnRows ? "return=representation" : "return=minimal");
        Http.Resp r = Http.request(method, SB + "/rest/v1/" + path, h, body == null ? null : body.toString());
        if (r.code == 401 && Auth.signedIn() && Auth.forceRefresh()) { h = headers(body != null); h.put("Prefer", returnRows ? "return=representation" : "return=minimal"); r = Http.request(method, SB + "/rest/v1/" + path, h, body == null ? null : body.toString()); }
        if (r.code >= 400) {
            JSONObject j = parse(r.body);
            throw new ApiError(r.code, J.s(j, "message", "Request failed (" + r.code + ")"));
        }
        String t = r.body.trim();
        return t.startsWith("[") ? new JSONArray(t) : new JSONArray();
    }
}

/** Email/password + Google/GitHub (system browser -> three60app://auth-callback deep link). */
final class Auth {
    static String access, refresh, userId, email;
    static long expiresAt;
    static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    static final String REDIRECT = "three60app://auth-callback";

    static void init() {
        try {
            String s = Store.get("auth", null);
            if (s == null) return;
            JSONObject j = new JSONObject(s);
            access = J.s(j, "a", null); refresh = J.s(j, "r", null);
            userId = J.s(j, "id", null); email = J.s(j, "e", null);
            expiresAt = j.optLong("x", 0);
        } catch (Exception ignored) { }
    }
    static boolean signedIn() { return access != null && userId != null && !userId.isEmpty(); }

    static void save() {
        try {
            JSONObject j = new JSONObject();
            j.put("a", access); j.put("r", refresh); j.put("id", userId); j.put("e", email); j.put("x", expiresAt);
            Store.put("auth", j.toString());
        } catch (Exception ignored) { }
    }
    static void notifyAuth() { for (final Runnable r : listeners) Http.MAIN.post(r); }

    static void clear() {
        access = refresh = userId = email = null; expiresAt = 0;
        Store.remove("auth"); Store.remove("mailAddr");
        notifyAuth();
    }

    static Map<String, String> plain(boolean bearer) {
        Map<String, String> h = new HashMap<>();
        h.put("apikey", Api.ANON);
        h.put("Content-Type", "application/json");
        if (bearer && access != null) h.put("Authorization", "Bearer " + access);
        return h;
    }

    /** Apply a token response ({access_token, refresh_token, expires_in|expires_at, user}). */
    static void apply(JSONObject j) throws Exception {
        access = j.getString("access_token");
        refresh = J.s(j, "refresh_token", refresh);
        long exp = j.optLong("expires_at", 0);
        expiresAt = exp > 0 ? exp : System.currentTimeMillis() / 1000 + j.optLong("expires_in", 3600);
        JSONObject u = j.optJSONObject("user");
        if (u != null) { userId = J.s(u, "id", userId); email = J.s(u, "email", email); }
        save();
        notifyAuth();
    }

    static synchronized void refreshIfNeeded() throws Exception {
        if (refresh == null || access == null) return;
        if (System.currentTimeMillis() / 1000 < expiresAt - 60) return;
        Http.Resp r = Http.request("POST", Api.SB + "/auth/v1/token?grant_type=refresh_token", plain(false),
                new JSONObject().put("refresh_token", refresh).toString());
        if (r.code == 200) apply(new JSONObject(r.body));
        else if (r.code == 400 || r.code == 401) clear();
    }

    /** Expired/invalid JWT: get a new access token now. False if that failed (user is signed out). */
    static synchronized boolean forceRefresh() {
        try { expiresAt = 0; refreshIfNeeded(); return signedIn(); } catch (Exception e) { return false; }
    }

    static void signIn(String em, String pw) throws Exception {
        Http.Resp r = Http.request("POST", Api.SB + "/auth/v1/token?grant_type=password", plain(false),
                new JSONObject().put("email", em).put("password", pw).toString());
        JSONObject j = Api.parse(r.body);
        if (r.code != 200) throw new Api.ApiError(r.code, firstNonEmpty(J.s(j, "error_description"), J.s(j, "msg"), J.s(j, "message"), "Sign-in failed"));
        apply(j);
    }

    /** Returns a user-facing message. */
    static String signUp(String em, String pw) throws Exception {
        Http.Resp r = Http.request("POST", Api.SB + "/auth/v1/signup", plain(false),
                new JSONObject().put("email", em).put("password", pw).toString());
        JSONObject j = Api.parse(r.body);
        if (r.code >= 400) throw new Api.ApiError(r.code, firstNonEmpty(J.s(j, "msg"), J.s(j, "error_description"), J.s(j, "message"), "Sign-up failed"));
        if (j.has("access_token")) { apply(j); return "Account created."; }
        return "Check your email to confirm your account.";
    }

    static String oauthUrl(String provider) {
        return Api.SB + "/auth/v1/authorize?provider=" + provider + "&redirect_to=" + Http.enc(REDIRECT);
    }

    /** Parse three60app://auth-callback#access_token=... and finish sign-in. Blocking. */
    static void handleCallback(Uri uri) throws Exception {
        String raw = uri.getFragment() != null ? uri.getFragment() : uri.getQuery();
        if (raw == null) return;
        Map<String, String> p = new HashMap<>();
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) p.put(Uri.decode(kv.substring(0, i)), Uri.decode(kv.substring(i + 1).replace('+', ' ')));
        }
        String err = firstNonEmpty(p.get("error_description"), p.get("error"), "");
        if (!err.isEmpty()) throw new Exception(err);
        String at = p.get("access_token"), rt = p.get("refresh_token");
        if (at == null || rt == null) return;
        access = at; refresh = rt;
        long exp = 3600;
        try { exp = Long.parseLong(p.get("expires_in")); } catch (Exception ignored) { }
        expiresAt = System.currentTimeMillis() / 1000 + exp;
        Map<String, String> h = plain(true);
        Http.Resp r = Http.request("GET", Api.SB + "/auth/v1/user", h, null);
        if (r.code != 200) { clear(); throw new Exception("Could not load your account"); }
        JSONObject u = new JSONObject(r.body);
        userId = J.s(u, "id"); email = J.s(u, "email");
        save();
        notifyAuth();
    }

    static void signOut() {
        final String tok = access;
        if (tok != null) Http.POOL.execute(() -> {
            try { Http.request("POST", Api.SB + "/auth/v1/logout", Api.headers(true), null); } catch (Exception ignored) { }
        });
        clear();
    }

    static String firstNonEmpty(String... s) {
        for (String x : s) if (x != null && !x.isEmpty() && !"null".equals(x)) return x;
        return "";
    }
}

/** Small LRU bitmap loader with downsampling. */
final class Img {
    static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8 / 1024)) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount() / 1024; }
    };

    static void load(final String url, final ImageView iv) {
        if (url == null || url.isEmpty()) { iv.setTag(null); iv.setImageDrawable(null); return; }
        final String u = url.startsWith("http://") ? "https://" + url.substring(7) : url;
        iv.setTag(u);
        Bitmap b = cache.get(u);
        if (b != null) { iv.setImageBitmap(b); return; }
        iv.setImageDrawable(null);
        Http.POOL.execute(() -> {
            final Bitmap bm = fetch(u, 720);
            if (bm == null) return;
            cache.put(u, bm);
            Http.MAIN.post(() -> { if (u.equals(iv.getTag())) iv.setImageBitmap(bm); });
        });
    }

    /** Blocking download + decode. Returns null on failure. */
    static Bitmap fetch(String url, int maxPx) {
        try {
            HttpURLConnection c = Http.open("GET", url, null, null);
            byte[] data;
            try {
                if (c.getResponseCode() != 200) return null;
                InputStream in = c.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) { bo.write(buf, 0, n); if (bo.size() > 8_000_000) return null; }
                data = bo.toByteArray();
            } finally { c.disconnect(); }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o2);
        } catch (Exception e) { return null; }
    }
}
