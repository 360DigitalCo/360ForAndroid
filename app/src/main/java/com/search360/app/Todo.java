package com.search360.app;

import android.app.DatePickerDialog;
import android.graphics.Paint;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** To-Do: the website's Stack app (todo_lists + todo_items). */
final class TodoScreen extends Screen {
    private FrameLayout holder;
    private final List<JSONObject> lists = new ArrayList<>(), items = new ArrayList<>();
    private String listId;
    private LinearLayout chips, itemsBox;
    private TextView progress;
    private EditText add;

    @Override String title() { return "To-Do"; }
    @Override View build() { holder = new FrameLayout(c); render(); return holder; }
    @Override void onAuth() { if (holder != null) render(); }

    private void render() {
        holder.removeAllViews();
        if (!Auth.signedIn()) { LinearLayout l = col(); l.addView(AuthView.build(this, "Sign in to keep your tasks in sync.")); holder.addView(scroll(l)); return; }
        LinearLayout l = col();
        chips = Ui.hbox(c);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false); hs.addView(chips);
        l.addView(hs, Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));

        LinearLayout addRow = Ui.hbox(c);
        add = Ui.input(c, "Add a task");
        add.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        add.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        add.setLayoutParams(Ui.lp(0, Ui.WRAP, 0, 0, 8, 0));
        ((LinearLayout.LayoutParams) add.getLayoutParams()).weight = 1f;
        add.setOnEditorActionListener((v, id, e) -> { addItem(); return true; });
        addRow.addView(add);
        addRow.addView(Ui.iconButton(c, "plus", v -> addItem()));
        l.addView(addRow);

        progress = Ui.text(c, "", 12.5f, Ui.MUT, false);
        l.addView(progress, Ui.lp(Ui.WRAP, Ui.WRAP, 2, 0, 0, 8));
        itemsBox = Ui.vbox(c);
        itemsBox.addView(Ui.loading(c));
        l.addView(itemsBox);
        holder.addView(scroll(l));
        loadLists();
    }

    private void loadLists() {
        async(() -> {
            JSONArray r = Api.rest("GET", "todo_lists?select=*&user_id=eq." + Http.enc(Auth.userId) + "&order=created_at.asc", null, true);
            if (r.length() == 0) r = Api.rest("POST", "todo_lists", new JSONObject().put("user_id", Auth.userId).put("name", "My tasks"), true);
            return r;
        }, (rows, e) -> {
            if (e != null) { itemsBox.removeAllViews(); itemsBox.addView(Ui.state(c, "alert", "Couldn't load your lists", msg(e))); return; }
            lists.clear();
            for (int i = 0; i < rows.length(); i++) if (rows.optJSONObject(i) != null) lists.add(rows.optJSONObject(i));
            String saved = Store.get("todo_list", "");
            listId = null;
            for (JSONObject o : lists) if (J.s(o, "id").equals(saved)) listId = saved;
            if (listId == null && !lists.isEmpty()) listId = J.s(lists.get(0), "id");
            paintChips();
            loadItems();
        });
    }

    private void paintChips() {
        chips.removeAllViews();
        for (final JSONObject o : lists) {
            final String id = J.s(o, "id");
            TextView ch = Ui.chip(c, J.s(o, "name", "List"), id.equals(listId));
            ch.setOnClickListener(v -> { listId = id; Store.put("todo_list", id); paintChips(); loadItems(); });
            ch.setOnLongClickListener(v -> { listMenu(o); return true; });
            chips.addView(ch);
        }
        TextView plus = Ui.chip(c, "+ New list", false);
        plus.setOnClickListener(v -> newList());
        chips.addView(plus);
    }

    private void newList() {
        final EditText name = Ui.input(c, "List name");
        new android.app.AlertDialog.Builder(a).setTitle("New list").setView(pad(name)).setNegativeButton("Cancel", null)
            .setPositiveButton("Create", (d, w) -> {
                final String n = name.getText().toString().trim();
                if (n.isEmpty()) return;
                async(() -> Api.rest("POST", "todo_lists", new JSONObject().put("user_id", Auth.userId).put("name", n), true), (r, e) -> {
                    if (e != null) { toast("Couldn't create list: " + msg(e)); return; }
                    if (r.length() > 0) { lists.add(r.optJSONObject(0)); listId = J.s(r.optJSONObject(0), "id"); Store.put("todo_list", listId); paintChips(); loadItems(); }
                });
            }).show();
    }

    private void listMenu(final JSONObject o) {
        new android.app.AlertDialog.Builder(a).setTitle(J.s(o, "name")).setItems(new String[]{"Rename", "Delete list"}, (d, i) -> {
            final String id = J.s(o, "id");
            if (i == 0) {
                final EditText name = Ui.input(c, "List name"); name.setText(J.s(o, "name"));
                new android.app.AlertDialog.Builder(a).setTitle("Rename list").setView(pad(name)).setNegativeButton("Cancel", null).setPositiveButton("Save", (dd, w) -> {
                    final String n = name.getText().toString().trim();
                    if (n.isEmpty()) return;
                    async(() -> Api.rest("PATCH", "todo_lists?id=eq." + Http.enc(id) + "&user_id=eq." + Http.enc(Auth.userId), new JSONObject().put("name", n), false), (r, e) -> {
                        if (e != null) toast("Couldn't rename: " + msg(e)); else { try { o.put("name", n); } catch (Exception ignored) { } paintChips(); }
                    });
                }).show();
            } else {
                new android.app.AlertDialog.Builder(a).setTitle("Delete this list?").setMessage("Its tasks are deleted too.").setNegativeButton("Cancel", null).setPositiveButton("Delete", (dd, w) ->
                    async(() -> { Api.rest("DELETE", "todo_items?list_id=eq." + Http.enc(id) + "&user_id=eq." + Http.enc(Auth.userId), null, false); Api.rest("DELETE", "todo_lists?id=eq." + Http.enc(id) + "&user_id=eq." + Http.enc(Auth.userId), null, false); return Boolean.TRUE; }, (r, e) -> {
                        if (e != null) { toast("Couldn't delete: " + msg(e)); return; }
                        lists.remove(o); if (id.equals(listId)) { listId = null; Store.remove("todo_list"); }
                        if (lists.isEmpty()) loadLists(); else { if (listId == null) listId = J.s(lists.get(0), "id"); paintChips(); loadItems(); }
                    })).show();
            }
        }).show();
    }

    private View pad(View v) { LinearLayout w = Ui.vbox(c); w.setPadding(Ui.dp(20), Ui.dp(8), Ui.dp(20), 0); w.addView(v); return w; }

    private void loadItems() {
        if (listId == null) return;
        itemsBox.removeAllViews(); itemsBox.addView(Ui.loading(c));
        final String id = listId;
        async(() -> Api.rest("GET", "todo_items?select=*&list_id=eq." + Http.enc(id) + "&order=created_at.asc", null, true), (rows, e) -> {
            if (!id.equals(listId)) return;
            if (e != null) { itemsBox.removeAllViews(); itemsBox.addView(Ui.state(c, "alert", "Couldn't load tasks", msg(e))); return; }
            items.clear();
            for (int i = 0; i < rows.length(); i++) if (rows.optJSONObject(i) != null) items.add(rows.optJSONObject(i));
            paintItems();
        });
    }

    private void paintItems() {
        Collections.sort(items, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject x, JSONObject y) {
                int d = Boolean.compare(x.optBoolean("done"), y.optBoolean("done"));
                if (d != 0) return d;
                String dx = J.s(x, "due_date", "9999"), dy = J.s(y, "due_date", "9999");
                return dx.compareTo(dy);
            }
        });
        itemsBox.removeAllViews();
        int done = 0;
        for (JSONObject o : items) if (o.optBoolean("done")) done++;
        progress.setText(items.isEmpty() ? "" : done + " of " + items.size() + " done");
        if (items.isEmpty()) { itemsBox.addView(Ui.state(c, "list", "Nothing to do", "Add your first task above.")); return; }
        for (JSONObject o : items) itemsBox.addView(row(o));
    }

    private View row(final JSONObject o) {
        final boolean done = o.optBoolean("done");
        LinearLayout r = Ui.hbox(c);
        r.setBackground(Ui.shape(Ui.CARD, 14, Ui.LINE));
        r.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(8), Ui.dp(10));
        r.setLayoutParams(Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
        FrameLayout box = new FrameLayout(c);
        box.setBackground(done ? Ui.shape(Ui.OK, 13, 0) : Ui.shape(0, 13, Ui.MUT));
        box.setClickable(true);
        box.setContentDescription(done ? "Mark not done" : "Mark done");
        if (done) box.addView(Ui.icon(c, "check", 16, 0xFFFFFFFF), new FrameLayout.LayoutParams(Ui.dp(16), Ui.dp(16), Gravity.CENTER));
        box.setOnClickListener(v -> toggle(o));
        r.addView(box, Ui.lp(Ui.dp(26), Ui.dp(26), 0, 0, 12, 0));
        LinearLayout t = Ui.vbox(c);
        TextView tx = Ui.text(c, J.s(o, "text"), 15, done ? Ui.MUT : Ui.TXT, false);
        if (done) tx.setPaintFlags(tx.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        t.addView(tx);
        String due = J.s(o, "due_date");
        if (!due.isEmpty()) {
            boolean overdue = !done && due.substring(0, Math.min(10, due.length())).compareTo(today()) < 0;
            t.addView(Ui.text(c, "Due " + due.substring(0, Math.min(10, due.length())), 11.5f, overdue ? Ui.BAD : Ui.MUT, overdue), Ui.lp(Ui.WRAP, Ui.WRAP, 0, 2, 0, 0));
        }
        t.setClickable(true);
        t.setOnClickListener(v -> pickDate(o));
        r.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        r.addView(Ui.iconButton(c, "trash", v -> delete(o)));
        return r;
    }

    private static String today() { return String.format(Locale.US, "%tF", Calendar.getInstance()); }

    private void addItem() {
        final String text = add.getText().toString().trim();
        if (text.isEmpty() || listId == null) return;
        add.setText("");
        final String id = listId;
        async(() -> Api.rest("POST", "todo_items", new JSONObject().put("list_id", id).put("user_id", Auth.userId).put("text", text).put("done", false), true), (r, e) -> {
            if (e != null) { add.setText(text); toast("Couldn't add: " + msg(e)); return; }
            if (r.length() > 0 && id.equals(listId)) { items.add(r.optJSONObject(0)); paintItems(); }
        });
    }

    private void toggle(final JSONObject o) {
        final boolean now = !o.optBoolean("done");
        try { o.put("done", now); } catch (Exception ignored) { }
        paintItems();
        async(() -> Api.rest("PATCH", "todo_items?id=eq." + Http.enc(J.s(o, "id")) + "&user_id=eq." + Http.enc(Auth.userId), new JSONObject().put("done", now), false), (r, e) -> {
            if (e != null) { try { o.put("done", !now); } catch (Exception ignored) { } paintItems(); toast("Couldn't update: " + msg(e)); }
        });
    }

    private void delete(final JSONObject o) {
        items.remove(o); paintItems();
        async(() -> Api.rest("DELETE", "todo_items?id=eq." + Http.enc(J.s(o, "id")) + "&user_id=eq." + Http.enc(Auth.userId), null, false), (r, e) -> {
            if (e != null) { items.add(o); paintItems(); toast("Couldn't delete: " + msg(e)); }
        });
    }

    private void pickDate(final JSONObject o) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(a, (dp, y, m, d) -> {
            final String val = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            try { o.put("due_date", val); } catch (Exception ignored) { }
            paintItems();
            async(() -> Api.rest("PATCH", "todo_items?id=eq." + Http.enc(J.s(o, "id")) + "&user_id=eq." + Http.enc(Auth.userId), new JSONObject().put("due_date", val), false), (r, e) -> { if (e != null) toast("Couldn't set date: " + msg(e)); });
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }
}
