package com.search360.app;

import android.graphics.Typeface;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Renders Markdown blocks as native views: styled text, copyable code blocks, tables, rules. */
final class MdView {
    static void render(final Screen s, LinearLayout box, String md, int textColor, float sp) {
        box.removeAllViews();
        List<Markdown.Block> blocks = Markdown.parse(md);
        SpannableStringBuilder cur = null;
        int prev = -1;
        for (Markdown.Block b : blocks) {
            if (b.type == Markdown.CODE || b.type == Markdown.TABLE || b.type == Markdown.HR) {
                flush(s, box, cur, textColor, sp); cur = null; prev = -1;
                if (b.type == Markdown.HR) {
                    View line = new View(s.c); line.setBackgroundColor(Ui.LINE);
                    box.addView(line, Ui.lp(Ui.MATCH, Ui.dp(1), 0, 8, 0, 8));
                } else box.addView(codeBlock(s, b));
                continue;
            }
            if (cur == null) cur = new SpannableStringBuilder();
            else cur.append(prev == b.type && (b.type == Markdown.UL || b.type == Markdown.OL || b.type == Markdown.QUOTE) ? "\n" : "\n\n");
            int start = cur.length();
            if (b.type == Markdown.UL) cur.append("\u2022\u00A0\u00A0");
            else if (b.type == Markdown.OL) cur.append(b.num + ".\u00A0");
            appendRuns(s, cur, b.runs);
            int end = cur.length();
            if (b.type == Markdown.H) {
                float[] size = {1f, 1.38f, 1.24f, 1.13f, 1.06f, 1f, 1f};
                cur.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                cur.setSpan(new RelativeSizeSpan(size[Math.min(6, b.level)]), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (b.type == Markdown.UL || b.type == Markdown.OL) {
                int first = Ui.dp(4 + b.depth * 16), rest = first + Ui.dp(b.type == Markdown.OL ? 20 : 14);
                cur.setSpan(new LeadingMarginSpan.Standard(first, rest), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (b.type == Markdown.QUOTE) {
                cur.setSpan(new QuoteSpan(Ui.ACC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                cur.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            prev = b.type;
        }
        flush(s, box, cur, textColor, sp);
        if (box.getChildCount() == 0) box.addView(Ui.text(s.c, "\u2026", sp, Ui.MUT, false));
    }

    private static void flush(Screen s, LinearLayout box, SpannableStringBuilder sb, int color, float sp) {
        if (sb == null || sb.length() == 0) return;
        TextView t = Ui.text(s.c, "", sp, color, false);
        t.setLineSpacing(0, 1.28f);
        t.setLinkTextColor(Ui.ACC);
        t.setText(sb);
        t.setMovementMethod(LinkMovementMethod.getInstance());
        box.addView(t, Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 2));
    }

    private static void appendRuns(final Screen s, SpannableStringBuilder sb, List<Markdown.Run> runs) {
        for (final Markdown.Run r : runs) {
            int a = sb.length();
            sb.append(r.text);
            int b = sb.length();
            if (b == a) continue;
            if (r.bold) sb.setSpan(new StyleSpan(Typeface.BOLD), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (r.italic) sb.setSpan(new StyleSpan(Typeface.ITALIC), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (r.code) {
                sb.setSpan(new TypefaceSpan("monospace"), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new BackgroundColorSpan(Ui.dark ? 0x33FFFFFF : 0x14000000), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new RelativeSizeSpan(0.92f), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (r.url != null) {
                sb.setSpan(new ClickableSpan() {
                    @Override public void onClick(View v) { s.a.push(new ReaderScreen(r.url, "")); }
                    @Override public void updateDrawState(TextPaint ds) { ds.setColor(Ui.ACC); ds.setUnderlineText(true); }
                }, a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static View codeBlock(final Screen s, final Markdown.Block b) {
        LinearLayout wrap = Ui.vbox(s.c);
        wrap.setBackground(Ui.shape(0xFF0F172A, 12, 0));
        wrap.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 6, 0, 8));
        LinearLayout head = Ui.hbox(s.c);
        head.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(8), Ui.dp(2));
        TextView lang = Ui.text(s.c, b.type == Markdown.TABLE ? "table" : (b.lang.isEmpty() ? "code" : b.lang), 11, 0xFF94A3B8, true);
        head.addView(lang, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        TextView copy = Ui.text(s.c, "Copy", 12, 0xFF38BDF8, true);
        copy.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(10), Ui.dp(6));
        copy.setClickable(true);
        copy.setOnClickListener(v -> { TranslatorScreen.copyText(s.c, b.text); s.a.toast("Copied"); });
        head.addView(copy);
        wrap.addView(head);
        HorizontalScrollView hs = new HorizontalScrollView(s.c);
        hs.setHorizontalScrollBarEnabled(false);
        TextView code = Ui.text(s.c, b.text, 12.5f, 0xFFE2E8F0, false);
        code.setTypeface(Typeface.MONOSPACE);
        code.setHorizontallyScrolling(true);
        code.setTextIsSelectable(true);
        code.setPadding(Ui.dp(12), Ui.dp(2), Ui.dp(12), Ui.dp(12));
        hs.addView(code);
        wrap.addView(hs);
        return wrap;
    }
}

/** Streaming chat with the 360 AI edge function, matching the website's protocol. */
final class AiScreen extends Screen {
    private static final List<String[]> HISTORY = new ArrayList<>();   // {role, content}
    private static boolean loaded;
    private static final Pattern PROVIDERS_DOWN = Pattern.compile("(?i)all ai providers (are )?temporarily unavailable");
    private static final int MAX_ATTEMPTS = 10;
    private static final String[] IDEAS = {"Explain how vaccines work in simple terms", "Write a polite email declining a meeting", "Plan a 3-day trip to Tokyo", "Give me a Python function to merge two sorted lists"};

    private FrameLayout holder;
    private LinearLayout msgs;
    private ScrollView sv;
    private EditText input;
    private FrameLayout sendBtn;
    private volatile boolean busy, cancelled;
    private String lastUser;
    private final AtomicReference<String> latest = new AtomicReference<>("");
    private boolean flushQueued;
    private LinearLayout liveBody;
    private TextView liveStatus;

    @Override View build() {
        if (!loaded) { loaded = true; loadHistory(); }
        LinearLayout root = Ui.vbox(c);
        LinearLayout head = Ui.hbox(c);
        head.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(10), Ui.dp(4));
        head.addView(Ui.text(c, "360 AI", 17, Ui.TXT, true), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        head.addView(Ui.iconButton(c, "plus", v -> newChat()));
        root.addView(head);

        sv = new ScrollView(c);
        msgs = Ui.vbox(c);
        msgs.setPadding(Ui.dp(14), Ui.dp(6), Ui.dp(14), Ui.dp(8));
        sv.addView(msgs);
        root.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        paintHistory();

        LinearLayout bar = Ui.hbox(c);
        bar.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(10));
        input = Ui.input(c, "Message 360 AI");
        input.setSingleLine(false); input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setLayoutParams(Ui.lp(0, Ui.WRAP, 0, 0, 8, 0));
        ((LinearLayout.LayoutParams) input.getLayoutParams()).weight = 1f;
        bar.addView(input);
        sendBtn = new FrameLayout(c);
        sendBtn.setBackground(Ui.ripple(Ui.gradient(23)));
        sendBtn.setClickable(true);
        sendBtn.setContentDescription("Send");
        sendBtn.addView(Ui.icon(c, "send", 20, 0xFFFFFFFF), new FrameLayout.LayoutParams(Ui.dp(20), Ui.dp(20), Gravity.CENTER));
        sendBtn.setOnClickListener(v -> { if (busy) cancelled = true; else send(input.getText().toString().trim()); });
        bar.addView(sendBtn, Ui.lp(Ui.dp(46), Ui.dp(46)));
        root.addView(bar);
        return root;
    }

    /* ── history ─────────────────────────────────────────────── */
    private static void loadHistory() {
        try {
            JSONArray a = new JSONArray(Store.get("ai_hist", "[]"));
            for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); HISTORY.add(new String[]{o.getString("r"), o.getString("c")}); }
        } catch (Exception ignored) { }
    }
    private static void saveHistory() {
        try {
            JSONArray a = new JSONArray();
            for (int i = Math.max(0, HISTORY.size() - 40); i < HISTORY.size(); i++) a.put(new JSONObject().put("r", HISTORY.get(i)[0]).put("c", HISTORY.get(i)[1]));
            Store.put("ai_hist", a.toString());
        } catch (Exception ignored) { }
    }
    private void newChat() {
        if (busy) { cancelled = true; }
        HISTORY.clear(); Store.remove("ai_hist");
        paintHistory();
    }

    private void paintHistory() {
        msgs.removeAllViews();
        if (HISTORY.isEmpty()) {
            LinearLayout empty = Ui.vbox(c);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.addView(Ui.state(c, "chat", "Ask 360 AI anything", "Answers stream in live and are formatted for reading. Chats are saved only on this device."));
            for (final String idea : IDEAS) {
                TextView t = Ui.text(c, idea, 14, Ui.TXT, false);
                t.setBackground(Ui.ripple(Ui.shape(Ui.CARD, 14, Ui.LINE)));
                t.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
                t.setClickable(true);
                t.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
                t.setOnClickListener(v -> send(idea));
                empty.addView(t);
            }
            msgs.addView(empty);
            return;
        }
        for (String[] h : HISTORY) {
            if (h[0].equals("user")) userBubble(h[1]);
            else { LinearLayout b = assistantBubble(); MdView.render(this, b, h[1], Ui.TXT, 15); attachCopy(b, h[1]); }
        }
        scrollDown();
    }

    /* ── bubbles ─────────────────────────────────────────────── */
    private void userBubble(String text) {
        TextView t = Ui.text(c, text, 15, 0xFFFFFFFF, false);
        t.setLineSpacing(0, 1.2f);
        t.setBackground(Ui.shape(Ui.ACC, 18, 0));
        t.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP, 56, 6, 0, 8);
        p.gravity = Gravity.END;
        msgs.addView(t, p);
    }
    private LinearLayout assistantBubble() {
        LinearLayout card = Ui.vbox(c);
        card.setBackground(Ui.shape(Ui.CARD, 18, Ui.LINE));
        card.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(10));
        card.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 4, 20, 10));
        LinearLayout body = Ui.vbox(c);
        card.addView(body);
        card.setTag(body);
        msgs.addView(card);
        return body;
    }
    private void attachCopy(LinearLayout body, final String text) {
        View card = (View) body.getParent();
        card.setOnLongClickListener(v -> {
            new android.app.AlertDialog.Builder(a).setItems(new String[]{"Copy answer", "Share"}, (d, i) -> {
                if (i == 0) { TranslatorScreen.copyText(c, text); toast("Copied"); }
                else { android.content.Intent it = new android.content.Intent(android.content.Intent.ACTION_SEND); it.setType("text/plain"); it.putExtra(android.content.Intent.EXTRA_TEXT, text); a.startActivity(android.content.Intent.createChooser(it, "Share answer")); }
            }).show();
            return true;
        });
    }
    private void scrollDown() { sv.post(() -> sv.fullScroll(View.FOCUS_DOWN)); }
    private void setSendIcon(boolean stop) {
        ((ImageView) sendBtn.getChildAt(0)).setImageDrawable(Icons.drawable(c, stop ? "stop" : "send", 0xFFFFFFFF));
        sendBtn.setContentDescription(stop ? "Stop" : "Send");
    }

    /* ── sending / streaming ─────────────────────────────────── */
    private void send(final String text) {
        if (text.isEmpty() || busy) return;
        if (HISTORY.isEmpty()) msgs.removeAllViews();
        input.setText(""); a.hideKeyboard();
        lastUser = text;
        userBubble(text);
        liveBody = assistantBubble();
        liveStatus = Ui.text(c, "Thinking\u2026", 13.5f, Ui.MUT, false);
        liveStatus.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        ((LinearLayout) liveBody.getParent()).addView(liveStatus);
        scrollDown();
        busy = true; cancelled = false; setSendIcon(true);

        final JSONArray memory = new JSONArray();
        try { for (int i = Math.max(0, HISTORY.size() - 20); i < HISTORY.size(); i++) memory.put(new JSONObject().put("role", HISTORY.get(i)[0]).put("content", HISTORY.get(i)[1])); } catch (Exception ignored) { }
        HISTORY.add(new String[]{"user", text});
        saveHistory();
        final LinearLayout body = liveBody;
        final TextView status = liveStatus;
        Http.POOL.execute(() -> runStream(text, memory, body, status));
    }

    private void status(final TextView st, final String t) { Http.MAIN.post(() -> { if (alive) { st.setText(t); st.setVisibility(t.isEmpty() ? View.GONE : View.VISIBLE); } }); }

    private void runStream(String text, JSONArray memory, final LinearLayout body, final TextView st) {
        final StringBuilder acc = new StringBuilder();
        final boolean[] saw = {false};
        String err = null;
        int attempt = 0;
        while (true) {
            attempt++;
            final String[] evErr = {null};
            err = null;
            try {
                Auth.refreshIfNeeded();
                Map<String, String> h = Api.headers(true);
                h.put("Accept", "text/event-stream");
                h.put("Accept-Encoding", "identity");      // gzip would buffer the stream and stall the first tokens
                JSONObject req = new JSONObject().put("message", text).put("memory", memory).put("model", "default");
                req.put("userId", Auth.userId == null ? JSONObject.NULL : Auth.userId);
                Http.stream("POST", Api.SB + "/functions/v1/ai-chatbot", h, req.toString(), line -> {
                    if (cancelled || !alive) return false;
                    if (!line.startsWith("data:")) return true;
                    String p = line.substring(5).trim();
                    if (p.isEmpty() || p.equals("[DONE]")) return true;
                    JSONObject ev;
                    try { ev = new JSONObject(p); } catch (Exception e) { return true; }
                    String type = J.s(ev, "type");
                    if (type.equals("thinking")) { saw[0] = true; status(st, "Thinking\u2026"); }
                    else if (type.equals("tool")) { saw[0] = true; status(st, toolLabel(J.s(ev, "name", J.s(ev, "tool", "tool")))); }
                    else if (type.equals("text")) {
                        saw[0] = true;
                        acc.append(J.s(ev, "delta"));
                        status(st, "");
                        pushText(body, acc.toString());
                    } else if (type.equals("error")) evErr[0] = J.s(ev, "message", "The AI service had a problem.");
                    return true;
                });
                err = evErr[0];
            } catch (Exception e) { err = Screen.msg(e) + (e instanceof Api.ApiError ? "" : ""); if (e instanceof Api.ApiError) err = e.getMessage(); }

            if (cancelled || !alive) break;
            // Same policy as the website: if every provider is briefly down and nothing arrived, back off and retry.
            if (acc.length() == 0 && !saw[0] && err != null && PROVIDERS_DOWN.matcher(err).find() && attempt < MAX_ATTEMPTS) {
                status(st, "The AI service is busy. Retrying (" + attempt + ")\u2026");
                try { Thread.sleep(400L * attempt); } catch (InterruptedException ie) { break; }
                continue;
            }
            break;
        }
        final String fin = acc.toString(), ferr = err;
        final boolean wasCancelled = cancelled;
        Http.MAIN.post(() -> {
            busy = false; cancelled = false;
            if (!alive) return;
            setSendIcon(false);
            st.setVisibility(View.GONE);
            if (fin.isEmpty()) {
                body.removeAllViews();
                TextView e = Ui.text(c, wasCancelled ? "Stopped." : friendly(ferr), 14, wasCancelled ? Ui.MUT : Ui.BAD, false);
                body.addView(e);
                if (!wasCancelled) {
                    TextView retry = Ui.button(c, "Try again", 1);
                    retry.setLayoutParams(Ui.lp(Ui.WRAP, Ui.WRAP, 0, 10, 0, 0));
                    retry.setOnClickListener(v -> { if (!HISTORY.isEmpty() && HISTORY.get(HISTORY.size() - 1)[0].equals("user")) HISTORY.remove(HISTORY.size() - 1); saveHistory(); paintHistory(); send(lastUser); });
                    body.addView(retry);
                }
                if (!HISTORY.isEmpty() && HISTORY.get(HISTORY.size() - 1)[0].equals("user")) { HISTORY.remove(HISTORY.size() - 1); saveHistory(); }
            } else {
                MdView.render(AiScreen.this, body, fin, Ui.TXT, 15);
                HISTORY.add(new String[]{"assistant", fin});
                saveHistory();
                attachCopy(body, fin);
            }
            scrollDown();
        });
    }

    private static String friendly(String e) {
        if (e == null || e.isEmpty()) return "No response. Please try again.";
        if (PROVIDERS_DOWN.matcher(e).find()) return "The AI service is busy right now. Please try again in a moment.";
        return e;
    }
    private static String toolLabel(String n) {
        String s = n.toLowerCase(java.util.Locale.US);
        if (s.contains("search") || s.contains("web")) return "Searching the web\u2026";
        if (s.contains("code") || s.contains("python")) return "Running code\u2026";
        return "Using " + n + "\u2026";
    }

    /** Coalesce rapid token updates so rendering stays smooth. */
    private void pushText(final LinearLayout body, String full) {
        latest.set(full);
        Http.MAIN.post(() -> {
            if (flushQueued || !alive) return;
            flushQueued = true;
            Http.MAIN.postDelayed(() -> {
                flushQueued = false;
                if (!alive) return;
                MdView.render(AiScreen.this, body, latest.get(), Ui.TXT, 15);
                scrollDown();
            }, 80);
        });
    }
}
