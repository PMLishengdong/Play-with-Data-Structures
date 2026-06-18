package com.aichat.app.tools;

import java.util.Map;

/** Safe arithmetic expression evaluator supporting + - * / and parentheses. */
public class CalculatorTool implements Tool {
    @Override public String getId() { return "calculator"; }
    @Override public String getDisplayName() { return "计算器"; }
    @Override public String getDescription() { return "对算术表达式求值，支持 + - * / 与括号"; }
    @Override
    public String getArgumentSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"expression\":{\"type\":\"string\",\"description\":\"算术表达式，如 (3+5)*2\"}"
                + "},\"required\":[\"expression\"]}";
    }
    @Override
    public String execute(Map<String, Object> arguments) {
        Args a = new Args(arguments);
        String expr = a.str("expression", "");
        if (expr == null || expr.length() == 0) return "error: empty expression";
        try {
            double v = evaluate(expr);
            if (v == Math.floor(v) && !Double.isInfinite(v)) {
                return String.valueOf((long) v);
            }
            return String.valueOf(v);
        } catch (Exception ex) {
            return "error: " + ex.getMessage();
        }
    }

    // Recursive-descent parser
    private double evaluate(String s) {
        Parser p = new Parser(s);
        double v = p.parseExpr();
        p.skipSpaces();
        if (p.pos < p.src.length()) throw new RuntimeException("unexpected: " + p.src.substring(p.pos));
        return v;
    }

    private static class Parser {
        final String src;
        int pos;
        Parser(String s) { this.src = s; this.pos = 0; }
        void skipSpaces() { while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++; }
        double parseExpr() { double v = parseTerm(); while (true) { skipSpaces(); if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) { char op = src.charAt(pos++); double r = parseTerm(); v = op == '+' ? v + r : v - r; } else break; } return v; }
        double parseTerm() { double v = parseFactor(); while (true) { skipSpaces(); if (pos < src.length() && (src.charAt(pos) == '*' || src.charAt(pos) == '/')) { char op = src.charAt(pos++); double r = parseFactor(); v = op == '*' ? v * r : (r == 0 ? Double.NaN : v / r); } else break; } return v; }
        double parseFactor() {
            skipSpaces();
            if (pos < src.length() && src.charAt(pos) == '(') { pos++; double v = parseExpr(); skipSpaces(); if (pos < src.length() && src.charAt(pos) == ')') pos++; return v; }
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) { char op = src.charAt(pos++); double v = parseFactor(); return op == '-' ? -v : v; }
            int start = pos;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) pos++;
            if (start == pos) throw new RuntimeException("expected number at " + pos);
            return Double.parseDouble(src.substring(start, pos));
        }
    }
}
