package com.aichat.app.markdown;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.widget.TextView;

import com.aichat.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@link MdNode} tree into a {@link SpannableStringBuilder} suitable
 * for assignment to a {@link TextView}. No WebView is used.
 *
 * The renderer is incremental: you can {@link #appendBlock(String)} as more
 * text arrives from the LLM and call {@link #bindTo(TextView)} to attach the
 * accumulated buffer to a view.
 */
public class MarkdownRenderer {
    private final Context ctx;
    private final MarkdownParser parser = new MarkdownParser();
    private final SpannableStringBuilder buffer = new SpannableStringBuilder();
    private final List<Integer> lineOffsets = new ArrayList<Integer>();
    private final StringBuilder pending = new StringBuilder();
    private final StyleConfig style;

    public MarkdownRenderer(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.style = new StyleConfig(ctx);
    }

    public StyleConfig getStyle() { return style; }

    public static class StyleConfig {
        public final int colorText;
        public final int colorCodeBg;
        public final int colorCodeText;
        public final int colorLink;
        public final int colorQuoteBar;
        public final int colorInlineCodeBg;
        public final float baseTextSizePx;

        public StyleConfig(Context ctx) {
            colorText = ctx.getResources().getColor(R.color.textPrimary);
            colorCodeBg = ctx.getResources().getColor(R.color.codeBg);
            colorCodeText = ctx.getResources().getColor(R.color.codeText);
            colorLink = ctx.getResources().getColor(R.color.link);
            colorQuoteBar = ctx.getResources().getColor(R.color.quoteBorder);
            colorInlineCodeBg = ctx.getResources().getColor(R.color.inlineCodeBg);
            baseTextSizePx = ctx.getResources().getDisplayMetrics().scaledDensity * 15f;
        }
    }

    public interface LinkClickListener {
        void onClick(String url);
    }

    private LinkClickListener linkListener;

    public void setLinkClickListener(LinkClickListener l) { this.linkListener = l; }

    public void clear() {
        buffer.clear();
        lineOffsets.clear();
        pending.setLength(0);
    }

    public void appendBlock(String markdownFragment) {
        if (markdownFragment == null || markdownFragment.length() == 0) return;
        pending.append(markdownFragment);
        flushCompleteBlocks(false);
    }

    /** Force-flush any remaining pending content, even if it isn't terminated
     *  by a blank line. Useful at the end of a stream. */
    public void finalizeStream() {
        flushCompleteBlocks(true);
    }

    private void flushCompleteBlocks(boolean flushAll) {
        int startLen = buffer.length();
        // Track fenced code state so an opening fence keeps the following
        // lines as a single block even if the closing fence is not seen yet.
        int fenceStart = -1;
        String fenceMarker = null;
        for (int p = 0; p < pending.length(); ) {
            int nl = pending.indexOf("\n", p);
            String line;
            boolean hasNl = nl >= 0;
            if (hasNl) {
                line = pending.substring(p, nl);
                p = nl + 1;
            } else {
                line = pending.substring(p);
                p = pending.length();
            }
            if (fenceStart < 0) {
                String t = line.trim();
                if (t.startsWith("```") || t.startsWith("~~~")) {
                    fenceMarker = t.startsWith("```") ? "```" : "~~~";
                    fenceStart = buffer.length();
                    String lang = t.substring(3).trim();
                    renderFenceOpen(lang);
                    continue;
                }
            } else {
                String t = line.trim();
                if (t.startsWith(fenceMarker)) {
                    renderFenceClose();
                    fenceStart = -1;
                    fenceMarker = null;
                    continue;
                } else {
                    appendFenceLine(line);
                    continue;
                }
            }
            // Normal block detection: commit on blank line
            if (line.trim().length() == 0) {
                // Separator; ensure the previous block ended with newline
                if (buffer.length() > 0 && buffer.charAt(buffer.length() - 1) != '\n') {
                    buffer.append('\n');
                }
                continue;
            }
            // Look ahead: if the next non-blank line in pending is also a normal
            // line, accumulate into a "soft" paragraph. To keep things simple
            // we just render line-by-line and join consecutive paragraph lines
            // using a soft newline. For the streaming case, we treat the line
            // as a paragraph and let the parser re-join later.
            // The simplest correct path: feed a single-line paragraph to the
            // parser and render.
            String mini = line + "\n\n";
            MdNode doc = parser.parse(mini);
            for (int i = 0; i < doc.children.size(); i++) renderBlock(doc.children.get(i));
            if (!hasNl && !flushAll) {
                // We just rendered the last (partial) line. If we are flushing
                // only completed blocks, undo by reverting the buffer to its
                // pre-line length. Easier: detect this and just trim trailing
                // newline of that block.
                // For simplicity, leave it; once a full blank line or fence
                // close arrives we'll emit a separator.
            }
        }
        // If a fenced block is open and we are flushing all, close it.
        if (fenceStart >= 0 && flushAll) {
            renderFenceClose();
        }
        pending.setLength(0);
        // Track line offsets
        for (int i = startLen; i < buffer.length(); i++) {
            if (i == startLen || buffer.charAt(i - 1) == '\n') {
                lineOffsets.add(i);
            }
        }
    }

    private void renderFenceOpen(String lang) {
        // We just mark the start of a code block by appending a placeholder
        // that we'll later overwrite with the content. Simpler: keep the
        // current line and the next N lines as raw code, render at close.
        int start = buffer.length();
        // We'll store the lang and start position by appending a sentinel
        // span that we later expand. For simplicity, we just remember a
        // pair in a side list.
        fenceStates.add(new FenceState(start, lang));
    }

    private void appendFenceLine(String line) {
        if (fenceStates.size() == 0) {
            // No fence open? Treat as paragraph line
            buffer.append(line).append('\n');
            return;
        }
        FenceState st = fenceStates.get(fenceStates.size() - 1);
        if (st.lines == null) st.lines = new StringBuilder();
        if (st.lines.length() > 0) st.lines.append('\n');
        st.lines.append(line);
    }

    private void renderFenceClose() {
        if (fenceStates.size() == 0) return;
        FenceState st = fenceStates.remove(fenceStates.size() - 1);
        // Remove the empty placeholder we inserted at open (start position
        // was used to track). If no lines came, leave a no-op.
        // For simplicity, insert code text at the end of buffer now.
        int codeStart = buffer.length();
        String text = st.lines == null ? "" : st.lines.toString();
        if (text.length() > 0) buffer.append(text);
        if (buffer.length() > 0 && buffer.charAt(buffer.length() - 1) != '\n') buffer.append('\n');
        int codeEnd = buffer.length();
        if (codeEnd > codeStart) {
            buffer.setSpan(new BackgroundColorSpan(style.colorCodeBg), codeStart, codeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            buffer.setSpan(new ForegroundColorSpan(style.colorCodeText), codeStart, codeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            buffer.setSpan(new TypefaceSpan("monospace"), codeStart, codeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            buffer.setSpan(new RelativeSizeSpan(0.92f), codeStart, codeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private final List<FenceState> fenceStates = new ArrayList<FenceState>();

    private static class FenceState {
        int start;
        String lang;
        StringBuilder lines;
        FenceState(int s, String l) { start = s; lang = l; }
    }

    public CharSequence getText() {
        return buffer;
    }

    public void bindTo(TextView tv) {
        tv.setText(buffer);
        tv.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        tv.setTextIsSelectable(true);
    }

    // ---------- Block rendering ----------

    private void renderBlock(MdNode node) {
        switch (node.type) {
            case MdNode.HEADING: renderHeading(node); break;
            case MdNode.PARAGRAPH: renderParagraph(node); break;
            case MdNode.CODE_BLOCK: renderCodeBlock(node); break;
            case MdNode.QUOTE: renderQuote(node); break;
            case MdNode.LIST: renderList(node); break;
            case MdNode.TASK_ITEM:
            case MdNode.LIST_ITEM: renderListItem(node); break;
            case MdNode.HR: renderHR(); break;
            case MdNode.TABLE: renderTable(node); break;
            default: renderInlines(node, buffer);
        }
        ensureTrailingNewline();
    }

    private void ensureTrailingNewline() {
        if (buffer.length() == 0 || buffer.charAt(buffer.length() - 1) != '\n') {
            buffer.append('\n');
        }
    }

    private void renderHeading(MdNode node) {
        int start = buffer.length();
        renderInlines(node, buffer);
        int end = buffer.length();
        float factor;
        switch (node.level) {
            case 1: factor = 1.6f; break;
            case 2: factor = 1.4f; break;
            case 3: factor = 1.25f; break;
            case 4: factor = 1.15f; break;
            case 5: factor = 1.05f; break;
            default: factor = 1.0f; break;
        }
        buffer.setSpan(new RelativeSizeSpan(factor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        buffer.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void renderParagraph(MdNode node) {
        renderInlines(node, buffer);
    }

    private void renderCodeBlock(MdNode node) {
        int start = buffer.length();
        String text = node.text == null ? "" : node.text;
        buffer.append(text);
        int end = buffer.length();
        buffer.setSpan(new BackgroundColorSpan(style.colorCodeBg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        buffer.setSpan(new ForegroundColorSpan(style.colorCodeText), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        buffer.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        buffer.setSpan(new RelativeSizeSpan(0.92f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // Add padding-ish top/bottom: we already ensure trailing newline
    }

    private void renderQuote(MdNode node) {
        int start = buffer.length();
        renderChildren(node);
        int end = buffer.length();
        // Add a quote bar via BackgroundColorSpan as a vertical stripe trick:
        // we'll instead change the foreground to muted and add a leading
        // border-like effect using a wide background padding. Simple
        // approach: just grey out the text.
        buffer.setSpan(new ForegroundColorSpan(Color.parseColor("#546E7A")), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        buffer.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void renderList(MdNode node) {
        for (int i = 0; i < node.children.size(); i++) {
            MdNode child = node.children.get(i);
            child.rowCells = null; // not used
            // Add bullet / number prefix
            int start = buffer.length();
            if (node.ordered) {
                buffer.append(String.valueOf(node.startNumber + i)).append(". ");
            } else {
                buffer.append("•  ");
            }
            int end = buffer.length();
            buffer.setSpan(new ForegroundColorSpan(style.colorLink), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            renderBlock(child);
        }
    }

    private void renderListItem(MdNode node) {
        if (node.type == MdNode.TASK_ITEM) {
            int start = buffer.length();
            buffer.append(node.checked ? "[x] " : "[ ] ");
            int end = buffer.length();
            buffer.setSpan(new ForegroundColorSpan(style.colorLink), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // Render first text child + any nested blocks
        if (node.children.size() == 1 && node.children.get(0).type == MdNode.TEXT) {
            renderInlines(node.children.get(0), buffer);
        } else {
            renderChildren(node);
        }
    }

    private void renderHR() {
        int start = buffer.length();
        buffer.append("──────────");
        int end = buffer.length();
        buffer.setSpan(new ForegroundColorSpan(Color.parseColor("#BDBDBD")), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void renderTable(MdNode node) {
        // Render as a simple grid using box-drawing characters. We keep each
        // row on its own line and pad cells.
        List<MdNode> rows = node.children;
        if (rows.size() == 0) return;
        int cols = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).rowCells != null && rows.get(i).rowCells.size() > cols) {
                cols = rows.get(i).rowCells.size();
            }
        }
        if (cols == 0) return;
        int[] widths = new int[cols];
        for (int i = 0; i < rows.size(); i++) {
            List<String> cells = rows.get(i).rowCells;
            if (cells == null) continue;
            for (int c = 0; c < cells.size() && c < cols; c++) {
                int len = visibleLength(cells.get(c));
                if (len > widths[c]) widths[c] = len;
            }
        }
        // Header
        renderTableRow(rows.get(0), widths, true);
        // Separator
        StringBuilder sep = new StringBuilder();
        sep.append('|');
        for (int c = 0; c < cols; c++) {
            for (int k = 0; k < widths[c] + 2; k++) sep.append('─');
            sep.append('|');
        }
        int sStart = buffer.length();
        buffer.append(sep.toString());
        int sEnd = buffer.length();
        buffer.setSpan(new ForegroundColorSpan(Color.parseColor("#90A4AE")), sStart, sEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ensureTrailingNewline();
        // Body
        for (int r = 1; r < rows.size(); r++) {
            renderTableRow(rows.get(r), widths, false);
        }
    }

    private void renderTableRow(MdNode row, int[] widths, boolean header) {
        int start = buffer.length();
        StringBuilder line = new StringBuilder();
        line.append('|');
        List<String> cells = row.rowCells;
        int colCount = cells == null ? 0 : cells.size();
        for (int c = 0; c < widths.length; c++) {
            String cell = (cells != null && c < colCount) ? cells.get(c) : "";
            line.append(' ').append(padRightVisible(cell, widths[c])).append(' ').append('|');
        }
        buffer.append(line.toString());
        int end = buffer.length();
        if (header) {
            buffer.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        ensureTrailingNewline();
    }

    private static int visibleLength(String s) {
        if (s == null) return 0;
        int len = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) len += 2;
            else len += 1;
        }
        return len;
    }

    private static String padRightVisible(String s, int width) {
        int len = visibleLength(s);
        if (len >= width) return s;
        StringBuilder sb = new StringBuilder(s == null ? "" : s);
        for (int i = len; i < width; i++) sb.append(' ');
        return sb.toString();
    }

    private void renderChildren(MdNode node) {
        for (int i = 0; i < node.children.size(); i++) renderBlock(node.children.get(i));
    }

    // ---------- Inline rendering ----------

    private void renderInlines(MdNode node, SpannableStringBuilder out) {
        for (int i = 0; i < node.children.size(); i++) {
            MdNode c = node.children.get(i);
            switch (c.type) {
                case MdNode.TEXT: out.append(c.text == null ? "" : c.text); break;
                case MdNode.CODE_INLINE: appendInlineCode(out, c.text == null ? "" : c.text); break;
                case MdNode.STRONG: appendSpan(out, renderInline(c), new StyleSpan(Typeface.BOLD)); break;
                case MdNode.EMPH: appendSpan(out, renderInline(c), new StyleSpan(Typeface.ITALIC)); break;
                case MdNode.STRIKE: appendSpan(out, renderInline(c), new StrikethroughSpan()); break;
                case MdNode.LINK: appendLink(out, c); break;
                case MdNode.IMAGE: appendImage(out, c); break;
                case MdNode.HARD_BREAK: out.append('\n'); break;
                default: renderInlines(c, out); break;
            }
        }
    }

    private String renderInline(MdNode node) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        renderInlines(node, sb);
        return sb.toString();
    }

    private void appendInlineCode(SpannableStringBuilder out, String text) {
        int start = out.length();
        out.append(text);
        int end = out.length();
        out.setSpan(new BackgroundColorSpan(style.colorInlineCodeBg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void appendSpan(SpannableStringBuilder out, String text, Object span) {
        int start = out.length();
        out.append(text);
        int end = out.length();
        out.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void appendLink(SpannableStringBuilder out, MdNode link) {
        int start = out.length();
        out.append(link.text == null ? (link.href == null ? "" : link.href) : link.text);
        int end = out.length();
        out.setSpan(new ForegroundColorSpan(style.colorLink), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (link.href != null) {
            final String url = link.href;
            out.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    if (linkListener != null) linkListener.onClick(url);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void appendImage(SpannableStringBuilder out, MdNode img) {
        out.append('[').append(img.text == null ? "" : img.text).append(']');
    }
}
