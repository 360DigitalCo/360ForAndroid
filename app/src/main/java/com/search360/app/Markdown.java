package com.search360.app;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small Markdown parser for chat answers. Pure Java: produces blocks and styled runs; Android rendering is separate. */
final class Markdown {
    static final int P = 0, H = 1, UL = 2, OL = 3, QUOTE = 4, CODE = 5, HR = 6, TABLE = 7;

    static final class Run {
        final String text; final boolean bold, italic, code; final String url;
        Run(String t, boolean b, boolean i, boolean c, String u) { text = t; bold = b; italic = i; code = c; url = u; }
    }
    static final class Block {
        int type, level, depth, num;
        String lang = "", text = "";          // CODE / TABLE raw text
        List<Run> runs = new ArrayList<>();
    }

    private static final Pattern HEAD = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern UL_RE = Pattern.compile("^(\\s*)[-*+\\u2022]\\s+(.*)$");
    private static final Pattern OL_RE = Pattern.compile("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$");
    private static final Pattern HR_RE = Pattern.compile("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$");
    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)\\s*([\\w+#.-]*)\\s*$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\s*\\|.*\\|\\s*$");
    private static final Pattern TABLE_SEP = Pattern.compile("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$");

    static List<Block> parse(String src) {
        List<Block> out = new ArrayList<>();
        if (src == null) return out;
        String[] lines = src.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder para = null;
        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i];
            Matcher m = FENCE.matcher(ln);
            if (m.matches()) {
                flush(out, para); para = null;
                Block b = new Block(); b.type = CODE; b.lang = m.group(2);
                StringBuilder code = new StringBuilder();
                int j = i + 1;
                while (j < lines.length && !FENCE.matcher(lines[j]).matches()) { if (code.length() > 0) code.append('\n'); code.append(lines[j]); j++; }
                b.text = code.toString();     // an unterminated fence (still streaming) simply runs to the end
                out.add(b); i = j; continue;
            }
            if (ln.trim().isEmpty()) { flush(out, para); para = null; continue; }
            if (TABLE_ROW.matcher(ln).matches()) {
                flush(out, para); para = null;
                List<String> rows = new ArrayList<>();
                int j = i;
                while (j < lines.length && TABLE_ROW.matcher(lines[j]).matches()) { if (!TABLE_SEP.matcher(lines[j]).matches()) rows.add(lines[j]); j++; }
                Block b = new Block(); b.type = TABLE; b.text = formatTable(rows);
                out.add(b); i = j - 1; continue;
            }
            if ((m = HEAD.matcher(ln)).matches()) {
                flush(out, para); para = null;
                Block b = new Block(); b.type = H; b.level = m.group(1).length(); b.runs = inline(m.group(2)); out.add(b); continue;
            }
            if (HR_RE.matcher(ln).matches()) { flush(out, para); para = null; Block b = new Block(); b.type = HR; out.add(b); continue; }
            if ((m = UL_RE.matcher(ln)).matches()) {
                flush(out, para); para = null;
                Block b = new Block(); b.type = UL; b.depth = Math.min(3, m.group(1).replace("\t", "  ").length() / 2); b.runs = inline(m.group(2)); out.add(b); continue;
            }
            if ((m = OL_RE.matcher(ln)).matches()) {
                flush(out, para); para = null;
                Block b = new Block(); b.type = OL; b.depth = Math.min(3, m.group(1).replace("\t", "  ").length() / 2);
                b.num = Integer.parseInt(m.group(2)); b.runs = inline(m.group(3)); out.add(b); continue;
            }
            if (ln.startsWith(">")) {
                flush(out, para); para = null;
                Block b = new Block(); b.type = QUOTE; b.runs = inline(ln.replaceFirst("^>\\s?", "")); out.add(b); continue;
            }
            if (para == null) para = new StringBuilder(); else para.append('\n');
            para.append(ln.trim());
        }
        flush(out, para);
        return out;
    }

    private static void flush(List<Block> out, StringBuilder para) {
        if (para == null || para.length() == 0) return;
        Block b = new Block(); b.type = P; b.runs = inline(para.toString()); out.add(b);
    }

    /** Align table cells in monospace columns. */
    static String formatTable(List<String> rows) {
        List<String[]> cells = new ArrayList<>();
        int cols = 0;
        for (String r : rows) {
            String t = r.trim();
            if (t.startsWith("|")) t = t.substring(1);
            if (t.endsWith("|")) t = t.substring(0, t.length() - 1);
            String[] c = t.split("\\|", -1);
            for (int i = 0; i < c.length; i++) c[i] = stripInline(c[i].trim());
            cells.add(c); cols = Math.max(cols, c.length);
        }
        int[] w = new int[cols];
        for (String[] r : cells) for (int i = 0; i < r.length; i++) w[i] = Math.max(w[i], r[i].length());
        StringBuilder sb = new StringBuilder();
        for (int ri = 0; ri < cells.size(); ri++) {
            String[] r = cells.get(ri);
            for (int i = 0; i < cols; i++) {
                String v = i < r.length ? r[i] : "";
                sb.append(v);
                for (int k = v.length(); k < w[i]; k++) sb.append(' ');
                if (i < cols - 1) sb.append("  |  ");
            }
            sb.append('\n');
            if (ri == 0 && cells.size() > 1) { for (int i = 0; i < cols; i++) { for (int k = 0; k < w[i]; k++) sb.append('-'); if (i < cols - 1) sb.append("--+--"); } sb.append('\n'); }
        }
        return sb.toString().replaceAll("\\s+$", "");
    }
    static String stripInline(String s) { return s.replaceAll("\\*\\*(.+?)\\*\\*", "$1").replaceAll("`([^`]*)`", "$1"); }

    private static final Pattern LINK = Pattern.compile("^\\[([^\\]]+)\\]\\((https?://[^)\\s]+)\\)");
    private static final Pattern BARE = Pattern.compile("^(https?://[^\\s<>)\\]]+[^\\s<>)\\].,;:!?])");

    /** Inline spans: `code`, **bold**, *italic*, [text](url), bare URLs. Unmatched markers stay literal (streaming-safe). */
    static List<Run> inline(String s) {
        List<Run> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            String rest = s.substring(i);
            if (c == '`') {
                int e = s.indexOf('`', i + 1);
                if (e > i + 1) { push(out, buf); out.add(new Run(s.substring(i + 1, e), false, false, true, null)); i = e + 1; continue; }
            }
            if ((rest.startsWith("**") || rest.startsWith("__")) && rest.length() > 4) {
                String d = rest.substring(0, 2);
                int e = s.indexOf(d, i + 2);
                if (e > i + 2) { push(out, buf); for (Run r : inline(s.substring(i + 2, e))) out.add(new Run(r.text, true, r.italic, r.code, r.url)); i = e + 2; continue; }
            }
            if ((c == '*' || c == '_') && i + 1 < s.length() && !Character.isWhitespace(s.charAt(i + 1)) && s.charAt(i + 1) != c
                    && (c == '*' || i == 0 || !Character.isLetterOrDigit(s.charAt(i - 1)))) {
                int e = s.indexOf(c, i + 1);
                while (e > 0 && e + 1 < s.length() && s.charAt(e + 1) == c) e = s.indexOf(c, e + 2);
                if (e > i + 1 && !Character.isWhitespace(s.charAt(e - 1)) && (c == '*' || e + 1 >= s.length() || !Character.isLetterOrDigit(s.charAt(e + 1)))) {
                    push(out, buf); for (Run r : inline(s.substring(i + 1, e))) out.add(new Run(r.text, r.bold, true, r.code, r.url)); i = e + 1; continue;
                }
            }
            if (c == '[') {
                Matcher m = LINK.matcher(rest);
                if (m.find()) { push(out, buf); out.add(new Run(m.group(1), false, false, false, m.group(2))); i += m.end(); continue; }
            }
            if (c == 'h') {
                Matcher m = BARE.matcher(rest);
                if (m.find() && (i == 0 || !Character.isLetterOrDigit(s.charAt(i - 1)))) { push(out, buf); out.add(new Run(m.group(1), false, false, false, m.group(1))); i += m.end(); continue; }
            }
            buf.append(c); i++;
        }
        push(out, buf);
        return out;
    }
    private static void push(List<Run> out, StringBuilder buf) { if (buf.length() > 0) { out.add(new Run(buf.toString(), false, false, false, null)); buf.setLength(0); } }
}
