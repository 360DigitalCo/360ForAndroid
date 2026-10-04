package com.search360.app;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Streaming chat with the 360 AI edge function (server-sent events, parsed natively). */
final class AiScreen extends Screen {
    private static final List<String[]> HISTORY = new ArrayList<>();   // {role, content}
    private LinearLayout msgs;
    private ScrollView sv;
    private EditText input;
    private FrameLayout sendBtn;
    private boolean busy;

    @Override View build() {
        LinearLayout root = Ui.vbox(c);
        sv = new ScrollView(c);
        msgs = Ui.vbox(c);
        msgs.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(8));
        sv.addView(msgs);
        root.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        if (HISTORY.isEmpty()) {
            msgs.addView(Ui.state(c, "chat", "Ask 360 AI anything", "Answers stream in live. Conversations stay on this device."));
        } else for (String[] h : HISTORY) bubble(h[1], h[0].equals("user"));

        LinearLayout bar = Ui.hbox(c);
        bar.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(10));
        input = Ui.input(c, "Message");
        input.setSingleLine(false); input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setLayoutParams(Ui.lp(0, Ui.WRAP, 0, 0, 8, 0));
        ((LinearLayout.LayoutParams) input.getLayoutParams()).weight = 1f;
        bar.addView(input);
        sendBtn = new FrameLayout(c);
        sendBtn.setBackground(Ui.ripple(Ui.gradient(23)));
        sendBtn.setClickable(true);
        sendBtn.setContentDescription("Send");
        sendBtn.addView(Ui.icon(c, "send", 20, 0xFFFFFFFF), new FrameLayout.LayoutParams(Ui.dp(20), Ui.dp(20), Gravity.CENTER));
        sendBtn.setOnClickListener(v -> send());
        bar.addView(sendBtn, Ui.lp(Ui.dp(46), Ui.dp(46)));
        root.addView(bar);
        return root;
    }

    private TextView bubble(String text, boolean user) {
        TextView t = Ui.text(c, text, 15, user ? 0xFFFFFFFF : Ui.TXT, false);
        t.setLineSpacing(0, 1.25f);
        t.setBackground(user ? Ui.shape(Ui.ACC, 17, 0) : Ui.shape(Ui.CARD, 17, Ui.LINE));
        t.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        t.setTextIsSelectable(!user);
        LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP, user ? 48 : 0, 0, user ? 0 : 48, 10);
        p.gravity = user ? Gravity.END : Gravity.START;
        msgs.addView(t, p);
        return t;
    }
    private void scrollDown() { sv.post(() -> sv.fullScroll(View.FOCUS_DOWN)); }

    private void send() {
        final String text = input.getText().toString().trim();
        if (text.isEmpty() || busy) return;
        if (HISTORY.isEmpty()) msgs.removeAllViews();
        input.setText(""); busy = true; sendBtn.setAlpha(0.5f);
        bubble(text, true);
        final TextView reply = bubble("\u2026", false);
        scrollDown();
        final JSONArray memory = new JSONArray();
        try { for (int i = Math.max(0, HISTORY.size() - 20); i < HISTORY.size(); i++) memory.put(new JSONObject().put("role", HISTORY.get(i)[0]).put("content", HISTORY.get(i)[1])); } catch (Exception ignored) { }
        HISTORY.add(new String[]{"user", text});
        final StringBuilder acc = new StringBuilder();
        Http.POOL.execute(() -> {
            Exception err = null;
            try {
                Auth.refreshIfNeeded();
                java.util.Map<String, String> h = Api.headers(true);
                h.put("Accept", "text/event-stream");
                Http.stream("POST", Api.SB + "/functions/v1/ai-chatbot", h, new JSONObject().put("message", text).put("memory", memory).toString(), line -> {
                    if (!alive) return false;
                    if (!line.startsWith("data:")) return true;
                    String p = line.substring(5).trim();
                    if (p.isEmpty() || p.equals("[DONE]")) return true;
                    try {
                        JSONObject ev = new JSONObject(p);
                        if ("text".equals(ev.optString("type"))) {
                            acc.append(ev.optString("delta"));
                            final String now = acc.toString();
                            Http.MAIN.post(() -> { reply.setText(now); scrollDown(); });
                        }
                    } catch (Exception ignored) { }
                    return true;
                });
            } catch (Exception e) { err = e; }
            final Exception fe = err;
            Http.MAIN.post(() -> {
                busy = false; sendBtn.setAlpha(1f);
                if (!alive) return;
                if (acc.length() == 0) { reply.setTextColor(Ui.BAD); reply.setText(fe != null ? msg(fe) : "No response. Please try again."); }
                else HISTORY.add(new String[]{"assistant", acc.toString()});
            });
        });
    }
}
