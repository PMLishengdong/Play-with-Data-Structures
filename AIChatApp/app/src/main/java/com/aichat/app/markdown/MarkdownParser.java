package com.aichat.app.markdown;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight Markdown parser that supports the CommonMark-ish subset listed on
 * {@link MdNode}. The parser is single-pass over a List&lt;String&gt; of input
 * lines. Inline parsing is done in a second pass per text node.
 *
 * Intentionally not 100% spec-compliant — biased towards what an LLM emits.
 */
public final class MarkdownParser {

    public MdNode parse(String source) {
        if (source == null) source = "";
        String[] linesArr = source.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<String> lines = new ArrayList<String>();
        for (int i = 0; i < linesArr.length; i++) lines.add(linesArr[i]);
        MdNode doc = new MdNode(MdNode.DOC);
        parseBlocks(lines, 0, doc);
        // Second-pass: parse inlines inside TEXT-bearing children
        for (int i = 0; i < doc.children.size(); i++) parseInlines(doc.children.get(i));
        return doc;
    }

    // ---------- Block parsing ----------

    private int parseBlocks(List<String> lines, int start, MdNode parent) {
        int i = start;
        int n = lines.size();
        while (i < n) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.length() == 0) { i++; continue; }
            // Fenced code block
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                String fence = trimmed.startsWith("```") ? "```" : "~~~";
                String lang = trimmed.substring(3).trim();
                MdNode cb = parent.child(MdNode.CODE_BLOCK);
                cb.lang = lang;
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < n) {
                    String l = lines.get(i);
                    if (l.trim().startsWith(fence)) { i++; break; }
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(l);
                    i++;
                }
                cb.text = sb.toString();
                continue;
            }
            // Heading
            if (startsWithHeading(trimmed)) {
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#' && level < 6) level++;
                if (level <= 6 && trimmed.length() > level && trimmed.charAt(level) == ' ') {
                    MdNode h = parent.child(MdNode.HEADING);
                    h.level = level;
                    MdNode t = h.child(MdNode.TEXT);
                    t.text = trimmed.substring(level + 1).trim();
                    i++;
                    continue;
                }
            }
            // HR
            if (isHR(trimmed)) {
                parent.child(MdNode.HR);
                i++;
                continue;
            }
            // Block quote
            if (trimmed.startsWith(">")) {
                MdNode q = parent.child(MdNode.QUOTE);
                List<String> inner = new ArrayList<String>();
                while (i < n) {
                    String l = lines.get(i);
                    String lt = l.trim();
                    if (lt.startsWith(">")) {
                        inner.add(lt.substring(1).replaceFirst("^\\s+", ""));
                        i++;
                    } else if (lt.length() == 0) {
                        break;
                    } else {
                        break;
                    }
                }
                parseBlocks(inner, 0, q);
                continue;
            }
            // Lists
            if (isUL(trimmed) || isOL(trimmed)) {
                i = parseList(lines, i, parent);
                continue;
            }
            // Tables: header row + separator
            if (i + 1 < n && looksLikeTableSeparator(lines.get(i + 1))) {
                int tblEnd = parseTable(lines, i, parent);
                if (tblEnd > i) { i = tblEnd; continue; }
            }
            // Default: paragraph
            MdNode p = parent.child(MdNode.PARAGRAPH);
            StringBuilder sb = new StringBuilder();
            sb.append(trimmed);
            i++;
            while (i < n) {
                String l = lines.get(i);
                String lt = l.trim();
                if (lt.length() == 0) break;
                if (isBlockStart(lt)) break;
                sb.append('\n').append(lt);
                i++;
            }
            MdNode t = p.child(MdNode.TEXT);
            t.text = sb.toString();
        }
        return i;
    }

    private boolean isBlockStart(String trimmed) {
        if (trimmed.length() == 0) return true;
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) return true;
        if (startsWithHeading(trimmed)) return true;
        if (isHR(trimmed)) return true;
        if (trimmed.startsWith(">")) return true;
        if (isUL(trimmed) || isOL(trimmed)) return true;
        return false;
    }

    private boolean startsWithHeading(String s) {
        if (s.length() == 0 || s.charAt(0) != '#') return false;
        int i = 0;
        while (i < s.length() && s.charAt(i) == '#' && i < 6) i++;
        return i > 0 && i < s.length() && s.charAt(i) == ' ';
    }

    private boolean isHR(String s) {
        if (s.length() < 3) return false;
        char c = s.charAt(0);
        if (c != '-' && c != '*' && c != '_') return false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != c && ch != ' ' && ch != '\t') return false;
        }
        int nonSpace = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) nonSpace++;
        return nonSpace >= 3;
    }

    private boolean isUL(String s) {
        if (s.length() < 2) return false;
        char c = s.charAt(0);
        if (c != '-' && c != '*' && c != '+') return false;
        return s.charAt(1) == ' ' || s.charAt(1) == '\t';
    }

    private boolean isOL(String s) {
        if (s.length() < 3) return false;
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        if (i == 0 || i > 9) return false;
        if (i >= s.length() || s.charAt(i) != '.') return false;
        if (i + 1 >= s.length()) return false;
        char c = s.charAt(i + 1);
        return c == ' ' || c == '\t';
    }

    private int parseList(List<String> lines, int start, MdNode parent) {
        String first = lines.get(start).trim();
        boolean ordered = isOL(first);
        int startNum = 1;
        if (ordered) {
            int i = 0;
            while (Character.isDigit(first.charAt(i))) i++;
            startNum = Integer.parseInt(first.substring(0, i));
        }
        MdNode list = parent.child(MdNode.LIST);
        list.ordered = ordered;
        list.startNumber = startNum;
        int i = start;
        int n = lines.size();
        while (i < n) {
            String l = lines.get(i);
            String lt = l.trim();
            boolean cont = ordered ? isOL(lt) : isUL(lt);
            if (!cont) break;
            // detect task item
            String body = lt;
            int dot = body.indexOf('.');
            if (dot >= 0) body = body.substring(dot + 1).trim();
            else body = body.substring(1).trim();
            boolean isTask = false;
            boolean checked = false;
            if (body.startsWith("[ ] ")) { isTask = true; checked = false; body = body.substring(4); }
            else if (body.startsWith("[x] ") || body.startsWith("[X] ")) { isTask = true; checked = true; body = body.substring(4); }
            MdNode item = isTask ? list.child(MdNode.TASK_ITEM) : list.child(MdNode.LIST_ITEM);
            item.checked = checked;
            MdNode t = item.child(MdNode.TEXT);
            t.text = body;
            // also allow nested multi-line content
            i++;
            List<String> extra = new ArrayList<String>();
            while (i < n) {
                String nl = lines.get(i);
                if (nl.length() == 0) {
                    if (i + 1 < n) {
                        String n2 = lines.get(i + 1).trim();
                        if (isUL(n2) || isOL(n2)) break;
                        if (n2.length() == 0) { extra.add(""); i++; continue; }
                    }
                    break;
                }
                if (isUL(nl) || isOL(nl)) break;
                if (isBlockStart(nl.trim())) break;
                extra.add(nl);
                i++;
            }
            if (extra.size() > 0) parseBlocks(extra, 0, item);
            if (i < n && lines.get(i).trim().length() == 0) {
                String peek = i + 1 < n ? lines.get(i + 1).trim() : "";
                if (isUL(peek) || isOL(peek)) { i++; }
            }
        }
        return i;
    }

    // ---------- Table support ----------

    private boolean looksLikeTableSeparator(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() < 3) return false;
        boolean sawPipe = false;
        boolean sawDash = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '|') sawPipe = true;
            else if (c == '-') sawDash = true;
            else if (c == ':' || c == ' ') { /* ok */ }
            else return false;
        }
        return sawDash && (sawPipe || t.indexOf('-') >= 0);
    }

    private int parseTable(List<String> lines, int start, MdNode parent) {
        String header = lines.get(start);
        String sep = lines.get(start + 1);
        if (!looksLikeTableSeparator(sep)) return start;
        List<String> headers = splitRow(header);
        List<String> seps = splitRow(sep);
        if (headers.size() == 0 || seps.size() == 0) return start;
        MdNode table = parent.child(MdNode.TABLE);
        MdNode hr = table.child(MdNode.TABLE_ROW);
        hr.headerRow = true;
        hr.rowCells = new ArrayList<String>(headers);
        int i = start + 2;
        int n = lines.size();
        while (i < n) {
            String l = lines.get(i);
            if (l.trim().length() == 0) break;
            if (looksLikeTableSeparator(l)) break;
            List<String> cells = splitRow(l);
            MdNode row = table.child(MdNode.TABLE_ROW);
            row.rowCells = new ArrayList<String>(cells);
            i++;
        }
        return i;
    }

    private List<String> splitRow(String line) {
        String t = line.trim();
        if (t.startsWith("|")) t = t.substring(1);
        if (t.endsWith("|")) t = t.substring(0, t.length() - 1);
        String[] parts = t.split("\\|", -1);
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < parts.length; i++) {
            out.add(parts[i].trim());
        }
        return out;
    }

    // ---------- Inline parsing ----------

    private void parseInlines(MdNode parent) {
        for (int i = 0; i < parent.children.size(); i++) {
            MdNode c = parent.children.get(i);
            if (c.type == MdNode.TEXT) {
                List<MdNode> inlined = parseInline(c.text);
                // Replace c with the inline nodes
                parent.children.remove(i);
                for (int j = 0; j < inlined.size(); j++) parent.children.add(i + j, inlined.get(j));
                i += inlined.size() - 1;
            } else if (c.children.size() > 0) {
                parseInlines(c);
            }
        }
    }

    private List<MdNode> parseInline(String text) {
        List<MdNode> out = new ArrayList<MdNode>();
        if (text == null) return out;
        int i = 0;
        int n = text.length();
        StringBuilder buf = new StringBuilder();
        while (i < n) {
            char c = text.charAt(i);
            // Inline code
            if (c == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i) {
                    if (buf.length() > 0) { out.add(text(buf.toString())); buf.setLength(0); }
                    out.add(inlineCode(text.substring(i + 1, end)));
                    i = end + 1;
                    continue;
                }
            }
            // Image ![alt](url)
            if (c == '!' && i + 1 < n && text.charAt(i + 1) == '[') {
                int closeBracket = findUnescaped(text, ']', i + 2);
                int openParen = closeBracket > 0 && closeBracket + 1 < n ? text.charAt(closeBracket + 1) : 0;
                if (closeBracket > 0 && openParen == '(') {
                    int closeParen = findUnescaped(text, ')', closeBracket + 2);
                    if (closeParen > 0) {
                        if (buf.length() > 0) { out.add(text(buf.toString())); buf.setLength(0); }
                        MdNode img = new MdNode(MdNode.IMAGE);
                        img.text = text.substring(i + 2, closeBracket);
                        String target = text.substring(closeBracket + 2, closeParen);
                        int sp = target.indexOf(' ');
                        if (sp > 0) {
                            img.href = target.substring(0, sp);
                            img.title = target.substring(sp + 1).replaceAll("^\"|\"$", "");
                        } else {
                            img.href = target;
                        }
                        out.add(img);
                        i = closeParen + 1;
                        continue;
                    }
                }
            }
            // Link [text](url)
            if (c == '[') {
                int closeBracket = findUnescaped(text, ']', i + 1);
                int openParen = closeBracket > 0 && closeBracket + 1 < n ? text.charAt(closeBracket + 1) : 0;
                if (closeBracket > 0 && openParen == '(') {
                    int closeParen = findUnescaped(text, ')', closeBracket + 2);
                    if (closeParen > 0) {
                        if (buf.length() > 0) { out.add(text(buf.toString())); buf.setLength(0); }
                        MdNode link = new MdNode(MdNode.LINK);
                        link.text = text.substring(i + 1, closeBracket);
                        String target = text.substring(closeBracket + 2, closeParen);
                        int sp = target.indexOf(' ');
                        if (sp > 0) {
                            link.href = target.substring(0, sp);
                            link.title = target.substring(sp + 1).replaceAll("^\"|\"$", "");
                        } else {
                            link.href = target;
                        }
                        out.add(link);
                        i = closeParen + 1;
                        continue;
                    }
                }
            }
            // Autolink <http://...>
            if (c == '<' && i + 1 < n) {
                int end = text.indexOf('>', i + 1);
                if (end > 0) {
                    String content = text.substring(i + 1, end);
                    if (content.contains("://") || content.startsWith("mailto:")) {
                        if (buf.length() > 0) { out.add(text(buf.toString())); buf.setLength(0); }
                        MdNode link = new MdNode(MdNode.LINK);
                        link.text = content;
                        link.href = content;
                        out.add(link);
                        i = end + 1;
                        continue;
                    }
                }
            }
            // Strong ** **
            if (c == '*' && i + 1 < n && text.charAt(i + 1) == '*') {
                int end = findCloser(text, i + 2, "**");
                if (end > i + 1) {
                    if (buf.length() > 0) { out.add(text(buf.toString())); buf.setLength(0); }
                    MdNode s = new MdNode(MdNode.STRONG);
                    List<MdNode> inner = parseInline(text.substring(i + 2, end));
                    s.children.addAll(inner);
                    out.add(s);
                    i = end + 2;
                    continue;
                }
            }
            if (c == '_' && i + 1 < n && text.charAt(i + 1) == '_') {
                int end = findCloser(text, i + 2, "__");
                if (end > i + 1) {
                    if (buf.length() > 0) { out.add(text(buf.toString())); buf.setLength(0); }
                    MdNode s = new MdNode(MdNode.STRONG);
                    List<MdNode> inner = parseInline(text.substring(i + 2, end));
                    s.children.addAll(inner);
                    out.add(s);
                    i = end + 2;
                    continue;
                }
            }
            // Emph * *
            if (c == '*') {
                int end = findCloser(text, i + 1, "*");
                if (end > i) {
                    if (buf.length() > 0) { out.add(text(buf.toString())); buf.setLength(0); }
                    MdNode s = new MdNode(MdNode.EMPH);
                    List<MdNode> inner = parseInline(text.substring(i + 1, end));
                    s.children.addAll(inner);
                    out.add(s);
                    i = end + 1;
                    continue;
                }
            }
            if (c == '_') {
                int end = findCloser(text, i + 1, "_");
                if (end > i && (end + 1 >= text.length() || !Character.isLetterOrDigit(text.charAt(end + 1)))) {
                    if (buf.length() > 0) { out.add(text(buf.toString())); buf.setLength(0); }
                    MdNode s = new MdNode(MdNode.EMPH);
                    List<MdNode> inner = parseInline(text.substring(i + 1, end));
                    s.children.addAll(inner);
                    out.add(s);
                    i = end + 1;
                    continue;
                }
            }
            // Strikethrough ~~ ~~
            if (c == '~' && i + 1 < n && text.charAt(i + 1) == '~') {
                int end = findCloser(text, i + 2, "~~");
                if (end > i + 1) {
                    if (buf.length() > 0) { out.add(text(buf.toString())); buf.setLength(0); }
                    MdNode s = new MdNode(MdNode.STRIKE);
                    List<MdNode> inner = parseInline(text.substring(i + 2, end));
                    s.children.addAll(inner);
                    out.add(s);
                    i = end + 2;
                    continue;
                }
            }
            // Hard break: two spaces or backslash before newline
            if (c == '\\' && i + 1 < n && text.charAt(i + 1) == '\n') {
                out.add(new MdNode(MdNode.HARD_BREAK));
                i += 2;
                continue;
            }
            if (c == ' ' && i + 2 < n && text.charAt(i + 1) == ' ' && text.charAt(i + 2) == '\n') {
                out.add(new MdNode(MdNode.HARD_BREAK));
                i += 3;
                continue;
            }
            buf.append(c);
            i++;
        }
        if (buf.length() > 0) out.add(text(buf.toString()));
        return out;
    }

    private int findCloser(String text, int from, String marker) {
        int n = text.length();
        int i = from;
        while (i < n) {
            int idx = text.indexOf(marker, i);
            if (idx < 0) return -1;
            // Ensure not at line start/end
            if (idx > from) return idx;
            i = idx + 1;
        }
        return -1;
    }

    private int findUnescaped(String text, char ch, int from) {
        int n = text.length();
        int i = from;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < n) { i += 2; continue; }
            if (c == ch) return i;
            i++;
        }
        return -1;
    }

    private MdNode text(String t) {
        MdNode n = new MdNode(MdNode.TEXT);
        n.text = t;
        return n;
    }
    private MdNode inlineCode(String t) {
        MdNode n = new MdNode(MdNode.CODE_INLINE);
        n.text = t;
        return n;
    }
}
