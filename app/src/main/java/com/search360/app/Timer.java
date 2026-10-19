package com.search360.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Pomodoro state machine. Pure Java so it can be unit-tested; everything time-based takes `now`. */
final class TimerModel {
    static final int FOCUS = 0, SHORT = 1, LONG = 2;
    static final String[] NAMES = {"Focus", "Short break", "Long break"};

    // settings
    int focusMin = 25, shortMin = 5, longMin = 15, longEvery = 4, goal = 8, vibStrength = 1;
    boolean autoBreak, autoFocus, vibrate = true, sound = true, alarmSound, notify = true, keepAwake = true, ongoing = true, alwaysShow;
    // state
    int mode = FOCUS, cycle = 0, doneToday = 0;
    long endAt = 0, remainMs = 25 * 60000L, alertedEnd = 0, focusMsToday = 0;
    boolean running = false;
    String day = "";

    long durMs(int m) { return (m == FOCUS ? focusMin : m == SHORT ? shortMin : longMin) * 60000L; }
    long remaining(long now) { return running ? Math.max(0, endAt - now) : remainMs; }
    boolean due(long now) { return running && endAt <= now + 500; }

    void start(long now) { if (remainMs <= 0) remainMs = durMs(mode); endAt = now + remainMs; running = true; }
    void pause(long now) { if (!running) return; remainMs = Math.max(0, endAt - now); running = false; endAt = 0; }
    void reset() { running = false; endAt = 0; remainMs = durMs(mode); }
    void setMode(int m) { mode = m; reset(); }
    void complete(long now) { advance(now, true); }
    void skip(long now) { advance(now, false); }

    private void advance(long now, boolean credit) {
        int next;
        if (mode == FOCUS) {
            if (credit) {
                doneToday++; focusMsToday += durMs(FOCUS);
                cycle++;
                if (cycle >= longEvery) { cycle = 0; next = LONG; } else next = SHORT;
            } else next = SHORT;
        } else next = FOCUS;
        mode = next; running = false; endAt = 0; remainMs = durMs(next);
        if ((next == FOCUS && autoFocus) || (next != FOCUS && autoBreak)) start(now);
    }

    void rollDay(String today) { if (!today.equals(day)) { day = today; doneToday = 0; focusMsToday = 0; } }

    /** Change a duration (0 focus, 1 short, 2 long). A timer that is idle and untouched follows the new length; a paused or running one keeps its time. */
    void setDuration(int which, int minutes) {
        boolean untouched = !running && remainMs == durMs(mode);
        if (which == FOCUS) focusMin = minutes; else if (which == SHORT) shortMin = minutes; else longMin = minutes;
        if (untouched) remainMs = durMs(mode);
    }

    JSONObject toJson() {
        try {
            return new JSONObject().put("fm", focusMin).put("sm", shortMin).put("lm", longMin).put("le", longEvery).put("goal", goal).put("vs", vibStrength)
                .put("ab", autoBreak).put("af", autoFocus).put("vib", vibrate).put("snd", sound).put("alm", alarmSound).put("ntf", notify).put("ka", keepAwake).put("og", ongoing).put("as", alwaysShow)
                .put("mode", mode).put("cyc", cycle).put("done", doneToday).put("end", endAt).put("rem", remainMs).put("ale", alertedEnd)
                .put("fms", focusMsToday).put("run", running).put("day", day);
        } catch (Exception e) { return new JSONObject(); }
    }
    static TimerModel fromJson(String s) {
        TimerModel m = new TimerModel();
        try {
            JSONObject j = new JSONObject(s);
            m.focusMin = clamp(j.optInt("fm", 25), 1, 180); m.shortMin = clamp(j.optInt("sm", 5), 1, 60); m.longMin = clamp(j.optInt("lm", 15), 1, 90);
            m.longEvery = clamp(j.optInt("le", 4), 2, 12); m.goal = clamp(j.optInt("goal", 8), 1, 30); m.vibStrength = clamp(j.optInt("vs", 1), 0, 2);
            m.autoBreak = j.optBoolean("ab"); m.autoFocus = j.optBoolean("af"); m.vibrate = j.optBoolean("vib", true); m.sound = j.optBoolean("snd", true);
            m.alarmSound = j.optBoolean("alm"); m.notify = j.optBoolean("ntf", true); m.keepAwake = j.optBoolean("ka", true); m.ongoing = j.optBoolean("og", true); m.alwaysShow = j.optBoolean("as");
            m.mode = clamp(j.optInt("mode"), 0, 2); m.cycle = Math.max(0, j.optInt("cyc")); m.doneToday = Math.max(0, j.optInt("done"));
            m.endAt = j.optLong("end"); m.remainMs = j.optLong("rem", m.durMs(m.mode)); m.alertedEnd = j.optLong("ale");
            m.focusMsToday = j.optLong("fms"); m.running = j.optBoolean("run"); m.day = j.optString("day", "");
            if (!m.running && m.remainMs <= 0) m.remainMs = m.durMs(m.mode);
        } catch (Exception ignored) { }
        return m;
    }
    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}

/** Persistence, alarm scheduling and completion handling shared by the screen and the background receiver. */
final class TimerCore {
    static String dayKey() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    static TimerModel load() {
        String s = Store.get("tm", null);
        TimerModel m = s == null ? new TimerModel() : TimerModel.fromJson(s);
        m.rollDay(dayKey());
        return m;
    }
    static void save(TimerModel m) { Store.put("tm", m.toJson().toString()); }

    /** Persist, (re)schedule the end-of-session alarm and refresh the ongoing notification. */
    static void apply(Context c, TimerModel m) { save(m); schedule(c, m); TimerNotif.update(c, m); }

    /** Actions triggered from the notification buttons (the app may not be open). */
    static synchronized void toggle(Context c) { TimerModel m = load(); long now = System.currentTimeMillis(); if (m.running) m.pause(now); else m.start(now); apply(c, m); }
    static synchronized void skip(Context c) { TimerModel m = load(); m.skip(System.currentTimeMillis()); apply(c, m); }
    static synchronized void reset(Context c) { TimerModel m = load(); m.reset(); apply(c, m); }

    private static PendingIntent pi(Context c) {
        return PendingIntent.getBroadcast(c, 7001, new Intent(c, TimerReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi(c));
    }
    /** Wake the app at endAt so the alert fires even when the phone is idle or the app is closed. */
    static void schedule(Context c, TimerModel m) {
        cancel(c);
        if (!m.running) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, m.endAt, pi(c)); }
        catch (SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, m.endAt, pi(c)); }
    }

    /** Idempotent: whichever of the receiver or the open screen notices first handles it; the other sees nothing due. */
    static synchronized boolean finishIfDue(Context c, boolean alert) {
        TimerModel m = load();
        long now = System.currentTimeMillis();
        if (!m.due(now)) return false;
        int finished = m.mode;
        boolean doAlert = alert && m.endAt != m.alertedEnd;
        m.alertedEnd = m.endAt;
        m.complete(now);
        apply(c, m);
        if (doAlert) TimerAlert.fire(c, finished, m);
        return true;
    }
}

/** The persistent "timer" notification: live countdown + Pause/Resume, Skip, Reset. */
final class TimerNotif {
    static final int ID = 7003;
    static final String ACT_TOGGLE = "com.search360.app.TIMER_TOGGLE", ACT_SKIP = "com.search360.app.TIMER_SKIP", ACT_RESET = "com.search360.app.TIMER_RESET";

    /** Running, or paused part-way through a session. */
    static boolean active(TimerModel m) { return m.running || m.remainMs < m.durMs(m.mode); }

    private static PendingIntent act(Context c, String action, int code) {
        Intent i = new Intent(c, TimerReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void update(Context c, TimerModel m) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (!m.ongoing || !(active(m) || m.alwaysShow)) { nm.cancel(ID); return; }
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                Class<?> cc = Class.forName("android.app.NotificationChannel");
                Object ch = cc.getConstructor(String.class, CharSequence.class, int.class).newInstance("timer_ongoing", "Running timer", 2);   // low importance: silent
                cc.getMethod("setShowBadge", boolean.class).invoke(ch, false);
                NotificationManager.class.getMethod("createNotificationChannel", cc).invoke(nm, ch);
                b = Notification.Builder.class.getConstructor(Context.class, String.class).newInstance(c, "timer_ongoing");
            } else b = new Notification.Builder(c);
            long now = System.currentTimeMillis();
            long rem = m.remaining(now);
            boolean paused = !m.running && active(m);
            String title = TimerModel.NAMES[m.mode] + (m.running ? "" : paused ? " (paused)" : " (ready)");
            String text;
            if (m.running) text = "Ends at " + new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(m.endAt));
            else text = String.format(Locale.US, "%02d:%02d left", (rem + 999) / 60000, ((rem + 999) / 1000) % 60);
            Intent open = new Intent(c, MainActivity.class).putExtra("open", "timer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            b.setSmallIcon(c.getApplicationInfo().icon).setContentTitle(title).setContentText(text).setOngoing(true).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(c, 7004, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            if (m.running) {
                b.setWhen(m.endAt).setShowWhen(true).setUsesChronometer(true);
                try { Notification.Builder.class.getMethod("setChronometerCountDown", boolean.class).invoke(b, true); } catch (Exception ignored) { }   // API 24+
                b.addAction(0, "Pause", act(c, ACT_TOGGLE, 7010));
                b.addAction(0, "Skip", act(c, ACT_SKIP, 7011));
            } else {
                b.setShowWhen(false);
                b.addAction(0, paused ? "Resume" : "Start", act(c, ACT_TOGGLE, 7010));
                if (paused) b.addAction(0, "Reset", act(c, ACT_RESET, 7012)); else b.addAction(0, "Skip", act(c, ACT_SKIP, 7011));
            }
            nm.notify(ID, b.build());
        } catch (Exception ignored) { }
    }
}

/** Vibration, sound and notification, each controlled by the user's settings. */
final class TimerAlert {
    static final long[][] PATTERNS = {{0, 200, 120, 200}, {0, 400, 150, 400, 150, 400}, {0, 700, 200, 700, 200, 700, 200, 700}};

    static void vibrate(Context c, int strength) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) v.vibrate(PATTERNS[TimerModel.clamp(strength, 0, 2)], -1);
        } catch (Exception ignored) { }
    }

    static void playSound(Context c, boolean alarm) {
        try {
            final Ringtone r = RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(alarm ? RingtoneManager.TYPE_ALARM : RingtoneManager.TYPE_NOTIFICATION));
            if (r == null) return;
            r.play();
            if (alarm) new Handler(Looper.getMainLooper()).postDelayed(r::stop, 6000);
        } catch (Exception ignored) { }
    }

    static void fire(Context c, int finishedMode, TimerModel m) {
        if (m.vibrate) vibrate(c, m.vibStrength);
        if (m.sound) playSound(c, m.alarmSound);
        if (m.notify) {
            String title = finishedMode == TimerModel.FOCUS ? "Focus session complete" : "Break over";
            String text = finishedMode == TimerModel.FOCUS ? "Time for a " + (m.mode == TimerModel.LONG ? "long" : "short") + " break." + (m.running ? " Started automatically." : "")
                : "Ready to focus?" + (m.running ? " Started automatically." : "");
            notifyDone(c, title, text);
        }
    }

    /** Channels exist from API 26; they are created by reflection so this builds against any SDK level. */
    private static void notifyDone(Context c, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                Class<?> cc = Class.forName("android.app.NotificationChannel");
                Object ch = cc.getConstructor(String.class, CharSequence.class, int.class).newInstance("timer_done", "Timer alerts", 4);
                cc.getMethod("enableVibration", boolean.class).invoke(ch, false);          // we vibrate ourselves, per the user's setting
                cc.getMethod("setSound", android.net.Uri.class, android.media.AudioAttributes.class).invoke(ch, new Object[]{null, null});
                NotificationManager.class.getMethod("createNotificationChannel", cc).invoke(nm, ch);
                b = Notification.Builder.class.getConstructor(Context.class, String.class).newInstance(c, "timer_done");
            } else b = new Notification.Builder(c);
            Intent open = new Intent(c, MainActivity.class).putExtra("open", "timer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            b.setSmallIcon(c.getApplicationInfo().icon).setContentTitle(title).setContentText(text).setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(c, 7002, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            nm.notify(7001, b.build());
        } catch (Exception ignored) { }
    }
}

final class TimerScreen extends Screen {
    interface IntCb { void on(int v); }
    interface BoolCb { void on(boolean v); }

    private final Handler h = new Handler(Looper.getMainLooper());
    private TimerModel m;
    private RingView ring;
    private TextView startBtn, stats;
    private LinearLayout chips;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!alive) return;
            long now = System.currentTimeMillis();
            String raw = Store.get("tm", "");
            if (!raw.equals(lastRaw)) { lastRaw = raw; m = TimerCore.load(); refresh(true); }      // changed from the notification
            if (m.due(now)) { TimerCore.finishIfDue(c, true); m = TimerCore.load(); lastRaw = Store.get("tm", ""); refresh(true); }
            else refresh(false);
            h.postDelayed(this, 250);
        }
    };
    @Override String title() { return "Timer"; }

    @Override View build() {
        TimerCore.finishIfDue(c, false);       // anything that finished while the screen was closed
        m = TimerCore.load();
        lastRaw = Store.get("tm", "");
        LinearLayout l = col();
        l.setGravity(Gravity.CENTER_HORIZONTAL);

        chips = Ui.hbox(c);
        for (int i = 0; i < 3; i++) {
            final int mode = i;
            TextView ch = Ui.chip(c, TimerModel.NAMES[i], false);
            ch.setOnClickListener(v -> { m.setMode(mode); persist(); refresh(true); });
            chips.addView(ch);
        }
        l.addView(chips, Ui.lp(Ui.WRAP, Ui.WRAP, 0, 0, 0, 10));

        ring = new RingView(c);
        l.addView(ring, Ui.lp(Ui.dp(262), Ui.dp(262), 0, 0, 0, 10));

        LinearLayout ctl = Ui.hbox(c);
        TextView reset = Ui.button(c, "Reset", 1), skip = Ui.button(c, "Skip", 1);
        startBtn = Ui.button(c, "Start", 0);
        for (TextView t : new TextView[]{reset, startBtn, skip}) { LinearLayout.LayoutParams p = Ui.lp(0, Ui.WRAP, 4, 0, 4, 10); p.weight = 1f; t.setLayoutParams(p); ctl.addView(t); }
        ((LinearLayout.LayoutParams) startBtn.getLayoutParams()).weight = 1.4f;
        startBtn.setOnClickListener(v -> toggle());
        reset.setOnClickListener(v -> { m.reset(); persist(); refresh(true); });
        skip.setOnClickListener(v -> { m.skip(System.currentTimeMillis()); persist(); refresh(true); });
        l.addView(ctl, Ui.lp(Ui.MATCH, Ui.WRAP));

        stats = Ui.text(c, "", 14, Ui.MUT, false);
        stats.setGravity(Gravity.CENTER);
        l.addView(stats, Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));

        l.addView(Ui.label(c, "Durations"));
        LinearLayout dur = Ui.card(c);
        dur.addView(stepper("Focus", m.focusMin, 1, 180, " min", v -> { m.setDuration(TimerModel.FOCUS, v); persist(); refresh(true); }));
        dur.addView(stepper("Short break", m.shortMin, 1, 60, " min", v -> { m.setDuration(TimerModel.SHORT, v); persist(); refresh(true); }));
        dur.addView(stepper("Long break", m.longMin, 1, 90, " min", v -> { m.setDuration(TimerModel.LONG, v); persist(); refresh(true); }));
        dur.addView(stepper("Long break every", m.longEvery, 2, 12, " sessions", v -> { m.longEvery = v; persist(); }));
        dur.addView(stepper("Daily goal", m.goal, 1, 30, " sessions", v -> { m.goal = v; persist(); refresh(false); }));
        l.addView(dur);

        l.addView(Ui.label(c, "Alerts"));
        LinearLayout al = Ui.card(c);
        al.addView(toggle("Vibrate", "Buzz when a session ends", m.vibrate, v -> { m.vibrate = v; persist(); if (v) TimerAlert.vibrate(c, m.vibStrength); }));
        final LinearLayout strength = Ui.hbox(c);
        String[] sn = {"Gentle", "Standard", "Strong"};
        for (int i = 0; i < 3; i++) {
            final int s = i;
            TextView ch = Ui.chip(c, sn[i], m.vibStrength == i);
            ch.setOnClickListener(v -> {
                m.vibStrength = s; persist(); TimerAlert.vibrate(c, s);
                for (int k = 0; k < 3; k++) { TextView t = (TextView) strength.getChildAt(k); boolean on = k == s; t.setTextColor(on ? 0xFFFFFFFF : Ui.TXT); t.setBackground(Ui.ripple(on ? Ui.shape(Ui.ACC, 20, 0) : Ui.shape(Ui.CARD, 20, Ui.LINE))); }
            });
            strength.addView(ch);
        }
        strength.setPadding(0, Ui.dp(2), 0, Ui.dp(8));
        al.addView(strength);
        al.addView(toggle("Sound", "Play a tone when a session ends", m.sound, v -> { m.sound = v; persist(); if (v) TimerAlert.playSound(c, m.alarmSound); }));
        al.addView(toggle("Alarm tone", "Use the louder alarm sound instead of the notification tone", m.alarmSound, v -> { m.alarmSound = v; persist(); if (m.sound) TimerAlert.playSound(c, v); }));
        al.addView(toggle("Notification", "Alert you even when the app is closed", m.notify, v -> {
            m.notify = v; persist();
            if (v && Build.VERSION.SDK_INT >= 33) a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 8);
        }));
        al.addView(toggle("Persistent notification", "Live countdown with Pause, Skip and Reset while a timer is active", m.ongoing, v -> {
            m.ongoing = v; persist();
            if (v && Build.VERSION.SDK_INT >= 33) a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 8);
        }));
        al.addView(toggle("Show when idle", "Keep the notification even when no timer is running", m.alwaysShow, v -> { m.alwaysShow = v; persist(); }));
        l.addView(al);

        l.addView(Ui.label(c, "Behaviour"));
        LinearLayout bh = Ui.card(c);
        bh.addView(toggle("Auto-start breaks", "Begin the break as soon as focus ends", m.autoBreak, v -> { m.autoBreak = v; persist(); }));
        bh.addView(toggle("Auto-start focus", "Begin the next focus session after a break", m.autoFocus, v -> { m.autoFocus = v; persist(); }));
        bh.addView(toggle("Keep screen on", "Stop the screen sleeping while the timer runs", m.keepAwake, v -> { m.keepAwake = v; persist(); refresh(false); }));
        l.addView(bh);

        refresh(true);
        h.post(tick);
        return scroll(l);
    }

    private void toggle() {
        long now = System.currentTimeMillis();
        if (m.running) m.pause(now); else m.start(now);
        persist(); refresh(true);
    }
    private void persist() { TimerCore.apply(c, m); lastRaw = Store.get("tm", ""); }
    private String lastRaw = "";

    private void refresh(boolean full) {
        long now = System.currentTimeMillis();
        long rem = m.remaining(now);
        long s = (rem + 999) / 1000;
        ring.label = String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
        ring.sub = TimerModel.NAMES[m.mode];
        ring.progress = rem / (float) Math.max(1, m.durMs(m.mode));
        ring.invalidate();
        if (!full) { updateStats(); return; }
        startBtn.setText(m.running ? "Pause" : (rem < m.durMs(m.mode) ? "Resume" : "Start"));
        for (int i = 0; i < chips.getChildCount(); i++) {
            TextView t = (TextView) chips.getChildAt(i);
            boolean on = i == m.mode;
            t.setTextColor(on ? 0xFFFFFFFF : Ui.TXT);
            t.setBackground(Ui.ripple(on ? Ui.shape(i == 0 ? Ui.ACC : Ui.OK, 20, 0) : Ui.shape(Ui.CARD, 20, Ui.LINE)));
        }
        updateStats();
        if (m.running && m.keepAwake) a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void updateStats() {
        long mins = m.focusMsToday / 60000;
        stats.setText("Today: " + m.doneToday + " of " + m.goal + " sessions  \u2022  " + (mins / 60 > 0 ? (mins / 60) + " h " : "") + (mins % 60) + " min focused");
        if (m.doneToday >= m.goal) stats.setTextColor(Ui.OK); else stats.setTextColor(Ui.MUT);
    }

    /* ── setting rows ──────────────────────────────────────────── */
    private View stepper(String label, int value, final int min, final int max, final String unit, final IntCb cb) {
        LinearLayout r = Ui.hbox(c);
        r.setPadding(0, Ui.dp(6), 0, Ui.dp(6));
        r.addView(Ui.text(c, label, 14.5f, Ui.TXT, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        final int[] v = {value};
        final TextView val = Ui.text(c, value + unit, 14.5f, Ui.TXT, true);
        val.setGravity(Gravity.CENTER);
        val.setMinWidth(Ui.dp(92));
        r.addView(Ui.iconButton(c, "down", x -> { if (v[0] > min) { v[0]--; val.setText(v[0] + unit); cb.on(v[0]); } }));
        r.addView(val);
        r.addView(Ui.iconButton(c, "up", x -> { if (v[0] < max) { v[0]++; val.setText(v[0] + unit); cb.on(v[0]); } }));
        return r;
    }

    private View toggle(String label, String sub, boolean on, final BoolCb cb) {
        LinearLayout r = Ui.hbox(c);
        r.setPadding(0, Ui.dp(6), 0, Ui.dp(6));
        LinearLayout t = Ui.vbox(c);
        t.addView(Ui.text(c, label, 14.5f, Ui.TXT, false));
        t.addView(Ui.text(c, sub, 12, Ui.MUT, false));
        r.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        Switch sw = new Switch(c);
        sw.setChecked(on);
        ColorStateList thumb = new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.ACC, 0xFFB0B7C3});
        ColorStateList track = new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{(Ui.ACC & 0x00FFFFFF) | 0x66000000, 0x44808896});
        sw.setThumbTintList(thumb);
        sw.setTrackTintList(track);
        sw.setOnCheckedChangeListener((b, checked) -> cb.on(checked));
        r.addView(sw);
        return r;
    }

    @Override void onDestroy() {
        h.removeCallbacks(tick);
        a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   // the alarm keeps running without the screen
        super.onDestroy();
    }
}
