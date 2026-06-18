package com.aichat.app.markdown;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple markdown AST. We support the subset commonly produced by LLMs:
 *   - Headings  (# .. ######)
 *   - Paragraphs
 *   - Ordered / unordered lists
 *   - Task list items  - [ ] / - [x]
 *   - Fenced code blocks ``` ```
 *   - Indented code blocks
 *   - Block quotes  > ...
 *   - Horizontal rule  ---
 *   - Inline: emphasis *em*, strong **strong**, strikethrough ~~text~~,
 *     inline code `code`, links [text](url), images, autolinks, hard breaks.
 */
public final class MdNode {
    public static final int DOC = 0;
    public static final int HEADING = 1;
    public static final int PARAGRAPH = 2;
    public static final int CODE_BLOCK = 3;
    public static final int QUOTE = 4;
    public static final int LIST = 5;
    public static final int LIST_ITEM = 6;
    public static final int HR = 7;
    public static final int TEXT = 8;
    public static final int EMPH = 9;
    public static final int STRONG = 10;
    public static final int STRIKE = 11;
    public static final int CODE_INLINE = 12;
    public static final int LINK = 13;
    public static final int IMAGE = 14;
    public static final int HARD_BREAK = 15;
    public static final int TABLE = 16;
    public static final int TABLE_ROW = 17;
    public static final int TABLE_CELL = 18;
    public static final int TASK_ITEM = 19;

    public int type;
    public int level;            // heading level
    public String text;          // for TEXT / CODE_INLINE
    public String lang;          // for CODE_BLOCK
    public String href;          // for LINK / IMAGE
    public String title;         // for LINK / IMAGE
    public boolean ordered;      // for LIST
    public boolean checked;      // for TASK_ITEM
    public int startNumber;      // for LIST
    public List<MdNode> children = new ArrayList<MdNode>();
    public List<String> rowCells; // for TABLE_ROW
    public boolean headerRow;    // for TABLE_ROW

    public MdNode(int type) { this.type = type; }

    public MdNode child(int t) {
        MdNode n = new MdNode(t);
        children.add(n);
        return n;
    }
}
