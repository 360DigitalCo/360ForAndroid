package com.search360.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Web-Mercator maths (pure Java so it can be unit-tested off-device). */
final class MapMath {
    static double size(double zoom) { return 256.0 * Math.pow(2, zoom); }
    static double lonToX(double lon, double z) { return (lon + 180.0) / 360.0 * size(z); }
    static double latToY(double lat, double z) {
        double s = Math.sin(Math.toRadians(Math.max(-85.0511, Math.min(85.0511, lat))));
        return (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * size(z);
    }
    static double xToLon(double x, double z) { return x / size(z) * 360.0 - 180.0; }
    static double yToLat(double y, double z) {
        double n = Math.PI - 2 * Math.PI * y / size(z);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }
}

/** Native slippy map: OpenStreetMap raster tiles, drag to pan, pinch / double-tap to zoom. */
final class MapView extends View {
    interface OnLongPress { void at(double lat, double lon); }

    double lat = 40.7128, lon = -74.0060, zoom = 11;
    Double markLat, markLon;
    OnLongPress longPress;

    private final LruCache<String, Bitmap> tiles = new LruCache<String, Bitmap>(24 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount() / 1024; }
    };
    private final Set<String> loading = new HashSet<>();
    private final ExecutorService net = Executors.newFixedThreadPool(4);
    private final Paint tp = new Paint(Paint.FILTER_BITMAP_FLAG), fillP = new Paint(), pin = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG), bg = new Paint();
    private final GestureDetector gd;
    private final ScaleGestureDetector sd;

    MapView(Context c) {
        super(c);
        fillP.setColor(Ui.dark ? 0xFF1A2030 : 0xFFE8EAEE);
        txt.setTextSize(Ui.dp(10)); txt.setColor(0xFF333333);
        bg.setColor(0xCCFFFFFF);
        gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) { panBy(dx, dy); return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { zoomAround((float) Math.min(19, zoom + 1) , e.getX(), e.getY()); return true; }
            @Override public void onLongPress(MotionEvent e) {
                if (longPress != null) { double[] g = screenToGeo(e.getX(), e.getY()); longPress.at(g[0], g[1]); }
            }
        });
        sd = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                double nz = zoom + Math.log(d.getScaleFactor()) / Math.log(2);
                zoomAround((float) nz, d.getFocusX(), d.getFocusY());
                return true;
            }
        });
    }

    void setView(double la, double lo, double z) { lat = la; lon = lo; zoom = Math.max(2, Math.min(19, z)); invalidate(); }
    void setMarker(Double la, Double lo) { markLat = la; markLon = lo; invalidate(); }
    void zoomBy(double d) { zoomAround((float) (zoom + d), getWidth() / 2f, getHeight() / 2f); }

    double[] screenToGeo(float sx, float sy) {
        double cx = MapMath.lonToX(lon, zoom), cy = MapMath.latToY(lat, zoom);
        double x = cx + (sx - getWidth() / 2.0), y = cy + (sy - getHeight() / 2.0);
        return new double[]{MapMath.yToLat(y, zoom), MapMath.xToLon(x, zoom)};
    }

    private void panBy(float dx, float dy) {
        double cx = MapMath.lonToX(lon, zoom) + dx, cy = MapMath.latToY(lat, zoom) + dy;
        lon = MapMath.xToLon(cx, zoom);
        lat = Math.max(-84, Math.min(84, MapMath.yToLat(cy, zoom)));
        if (lon > 180) lon -= 360; else if (lon < -180) lon += 360;
        invalidate();
    }

    /** Change zoom while keeping the geo point under (fx, fy) fixed on screen. */
    private void zoomAround(float newZoom, float fx, float fy) {
        newZoom = Math.max(2f, Math.min(19f, newZoom));
        double[] g = screenToGeo(fx, fy);
        zoom = newZoom;
        double cx = MapMath.lonToX(g[1], zoom) - (fx - getWidth() / 2.0), cy = MapMath.latToY(g[0], zoom) - (fy - getHeight() / 2.0);
        lon = MapMath.xToLon(cx, zoom);
        lat = Math.max(-84, Math.min(84, MapMath.yToLat(cy, zoom)));
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        getParent().requestDisallowInterceptTouchEvent(true);
        boolean a = sd.onTouchEvent(e);
        if (!sd.isInProgress()) a |= gd.onTouchEvent(e);
        return a || super.onTouchEvent(e);
    }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        cv.drawRect(0, 0, w, h, fillP);
        int z = (int) Math.floor(zoom);
        float scale = (float) Math.pow(2, zoom - z), ts = 256f * scale;
        double cxz = MapMath.lonToX(lon, zoom), cyz = MapMath.latToY(lat, zoom);
        int n = 1 << z;
        int x0 = (int) Math.floor((cxz - w / 2.0) / ts), x1 = (int) Math.floor((cxz + w / 2.0) / ts);
        int y0 = (int) Math.floor((cyz - h / 2.0) / ts), y1 = (int) Math.floor((cyz + h / 2.0) / ts);
        Rect src = null; RectF dst = new RectF();
        for (int ty = y0; ty <= y1; ty++) {
            if (ty < 0 || ty >= n) continue;
            for (int tx = x0; tx <= x1; tx++) {
                int wx = ((tx % n) + n) % n;
                String key = z + "/" + wx + "/" + ty;
                Bitmap b = tiles.get(key);
                float left = (float) (w / 2.0 + tx * ts - cxz), top = (float) (h / 2.0 + ty * ts - cyz);
                dst.set(left, top, left + ts + 0.5f, top + ts + 0.5f);
                if (b != null) cv.drawBitmap(b, src, dst, tp); else request(key, z, wx, ty);
            }
        }
        if (markLat != null && markLon != null) {
            float px = (float) (w / 2.0 + MapMath.lonToX(markLon, zoom) - cxz), py = (float) (h / 2.0 + MapMath.latToY(markLat, zoom) - cyz);
            float r = Ui.dp(11);
            Path p = new Path();
            p.moveTo(px, py); p.lineTo(px - r * 0.62f, py - r * 1.35f); p.lineTo(px + r * 0.62f, py - r * 1.35f); p.close();
            pin.setColor(0xFFEF4444);
            cv.drawPath(p, pin);
            cv.drawCircle(px, py - r * 1.9f, r, pin);
            pin.setColor(0xFFFFFFFF);
            cv.drawCircle(px, py - r * 1.9f, r * 0.42f, pin);
        }
        String attr = "\u00A9 OpenStreetMap contributors";
        float tw = txt.measureText(attr);
        cv.drawRect(Ui.dp(4), h - Ui.dp(20), Ui.dp(4) + tw + Ui.dp(10), h - Ui.dp(4), bg);
        cv.drawText(attr, Ui.dp(9), h - Ui.dp(8), txt);
    }

    private void request(final String key, final int z, final int x, final int y) {
        synchronized (loading) { if (!loading.add(key)) return; }
        net.execute(() -> {
            Bitmap b = Img.fetch("https://tile.openstreetmap.org/" + z + "/" + x + "/" + y + ".png", 1024);
            synchronized (loading) { loading.remove(key); }
            if (b != null) { tiles.put(key, b); postInvalidate(); }
        });
    }

    void shutdown() { net.shutdownNow(); }
}
