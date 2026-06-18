package com.example.aichat.tool;

import java.util.Map;

public class CalculatorTool implements ToolExecutor {
    @Override
    public String execute(Map<String, Object> params) {
        try {
            String expression = (String) params.get("expression");
            if (expression == null || expression.isEmpty()) {
                return "Error: No expression provided";
            }
            expression = expression.replaceAll("[^0-9+\\-*/().\\s]", "");
            double result = evaluateExpression(expression);
            return String.valueOf(result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private double evaluateExpression(String expression) {
        return new javax.script.ScriptEngineManager()
                .getEngineByName("JavaScript")
                .eval(expression);
    }
}
