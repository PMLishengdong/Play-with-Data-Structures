package com.example.aichat.util;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.BoldSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.TypefaceSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownRenderer {
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.*?)\\*\\*|__(.*?)__");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("\\*(.*?)\\*|_(.*?)_");
    private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("~~(.*?)~~");
    private static final Pattern CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w+)?\\n([\\s\\S]*?)```");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern LIST_PATTERN = Pattern.compile("^(\\s*[-*+]|\\s*\\d+\\.)\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern QUOTE_PATTERN = Pattern.compile("^>\\s*(.+)$", Pattern.MULTILINE);

    private static final int COLOR_CODE_BACKGROUND = Color.parseColor("#2D2D2D");
    private static final int COLOR_CODE_TEXT = Color.parseColor("#E0E0E0");
    private static final int COLOR_LINK = Color.parseColor("#6200EE");
    private static final int COLOR_QUOTE = Color.parseColor("#E0E0E0");

    public static SpannableStringBuilder render(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return new SpannableStringBuilder();
        }

        String processed = markdown;
        SpannableStringBuilder result = new SpannableStringBuilder();

        List<SpanInfo> spans = new ArrayList<SpanInfo>();

        processed = processCodeBlocks(processed, spans);
        processed = processHeadings(processed, spans);
        processed = processQuotes(processed, spans);
        processed = processLists(processed, spans);
        processed = processBold(processed, spans);
        processed = processItalic(processed, spans);
        processed = processStrikethrough(processed, spans);
        processed = processCode(processed, spans);
        processed = processLinks(processed, spans);

        result.append(processed);

        for (SpanInfo span : spans) {
            result.setSpan(span.span, span.start, span.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return result;
    }

    private static String processCodeBlocks(String text, List<SpanInfo> spans) {
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String language = matcher.group(1);
            String code = matcher.group(2);
            String replacement = "\n" + code + "\n";
            int start = sb.length();
            sb.append(replacement);
            int end = sb.length();
            spans.add(new SpanInfo(start, end, new BackgroundColorSpan(COLOR_CODE_BACKGROUND)));
            spans.add(new SpanInfo(start, end, new ForegroundColorSpan(COLOR_CODE_TEXT)));
            spans.add(new SpanInfo(start, end, new TypefaceSpan("monospace")));
        }
        return matcher.replaceAll(sb.toString());
    }

    private static String processHeadings(String text, List<SpanInfo> spans) {
        Matcher matcher = HEADING_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hashes = matcher.group(1);
            String content = matcher.group(2);
            int level = hashes.length();
            float size = 1.0f - (level - 1) * 0.15f;
            if (size < 0.6f) size = 0.6f;
            int start = sb.length();
            sb.append(content).append("\n\n");
            int end = sb.length() - 2;
            spans.add(new SpanInfo(start, end, new BoldSpan()));
            spans.add(new SpanInfo(start, end, new RelativeSizeSpan(size)));
        }
        return matcher.replaceAll(sb.toString());
    }

    private static String processQuotes(String text, List<SpanInfo> spans) {
        Matcher matcher = QUOTE_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(1);
            int start = sb.length();
            sb.append("> ").append(content).append("\n");
            int end = sb.length();
            spans.add(new SpanInfo(start, end, new QuoteSpan(Color.parseColor("#E0E0E0"))));
            spans.add(new SpanInfo(start, end, new ForegroundColorSpan(Color.parseColor("#757575"))));
        }
        return matcher.replaceAll(sb.toString());
    }

    private static String processLists(String text, List<SpanInfo> spans) {
        Matcher matcher = LIST_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String marker = matcher.group(1);
            String content = matcher.group(2);
            int start = sb.length();
            sb.append("  • ").append(content).append("\n");
            int end = sb.length();
        }
        return matcher.replaceAll(sb.toString());
    }

    private static String processBold(String text, List<SpanInfo> spans) {
        Matcher matcher = BOLD_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            int start = sb.length();
            sb.append(content);
            int end = sb.length();
            spans.add(new SpanInfo(start, end, new BoldSpan()));
        }
        return matcher.replaceAll(sb.toString());
    }

    private static String processItalic(String text, List<SpanInfo> spans) {
        Matcher matcher = ITALIC_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            int start = sb.length();
            sb.append(content);
            int end = sb.length();
            spans.add(new SpanInfo(start, end, new android.text.style.ItalicSpan()));
        }
        return matcher.replaceAll(sb.toString());
    }

    private static String processStrikethrough(String text, List<SpanInfo> spans) {
        Matcher matcher = STRIKETHROUGH_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(1);
            int start = sb.length();
            sb.append(content);
            int end = sb.length();
            spans.add(new SpanInfo(start, end, new StrikethroughSpan()));
        }
        return matcher.replaceAll(sb.toString());
    }

    private static String processCode(String text, List<SpanInfo> spans) {
        Matcher matcher = CODE_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(1);
            int start = sb.length();
            sb.append(content);
            int end = sb.length();
            spans.add(new SpanInfo(start, end, new BackgroundColorSpan(COLOR_CODE_BACKGROUND)));
            spans.add(new SpanInfo(start, end, new ForegroundColorSpan(COLOR_CODE_TEXT)));
            spans.add(new SpanInfo(start, end, new TypefaceSpan("monospace")));
        }
        return matcher.replaceAll(sb.toString());
    }

    private static String processLinks(String text, List<SpanInfo> spans) {
        Matcher matcher = LINK_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String label = matcher.group(1);
            String url = matcher.group(2);
            int start = sb.length();
            sb.append(label);
            int end = sb.length();
            spans.add(new SpanInfo(start, end, new ForegroundColorSpan(COLOR_LINK)));
        }
        return matcher.replaceAll(sb.toString());
    }

    private static class SpanInfo {
        int start;
        int end;
        Object span;

        SpanInfo(int start, int end, Object span) {
            this.start = start;
            this.end = end;
            this.span = span;
        }
    }
}
