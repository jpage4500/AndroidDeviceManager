package com.jpage4500.devicemanager.data;

import com.jpage4500.devicemanager.table.LogsTableModel;
import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogFilterEntry {
    private static final Logger log = LoggerFactory.getLogger(LogFilterEntry.class);

    public LogsTableModel.Columns column;
    public LogFilterExpression expression = LogFilterExpression.EQUALS;
    public String value;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        // -- column --
        if (column == null) sb.append("*");
        else sb.append(column.name().toLowerCase());

        sb.append(":");

        // -- value --
        if (TextUtils.equalsIgnoreCaseAny(value, "*", "")) {
            sb.append("*");
        } else {
            if (expression.isNotExpression()) {
                sb.append("!");
            }
            switch (expression) {
                case STARTS_WITH:
                case NOT_STARTS_WITH:
                    sb.append(value);
                    sb.append("*");
                    break;
                case ENDS_WITH:
                case NOT_ENDS_WITH:
                    sb.append("*");
                    sb.append(value);
                    break;
                case CONTAINS:
                case NOT_CONTAINS:
                    sb.append("*");
                    sb.append(value);
                    sb.append("*");
                    break;
                default:
                    sb.append(value);
                    break;
            }
        }
        return sb.toString();
    }

    public boolean isMatch(LogEntry logEntry) {
        boolean isMatch;
        if (column != null) {
            String logValue = null;
            switch (column) {
                case DATE -> logValue = logEntry.date;
                case APP -> logValue = logEntry.app;
                case TID -> logValue = logEntry.tid;
                case LEVEL -> {
                    logValue = logEntry.level;
                    //log.trace("isMatch: {}, val:{}, expr:{}", logValue, value, expression);
                    if (value != null && expression == LogFilterExpression.STARTS_WITH) {
                        switch (value) {
                            case "D":
                                return TextUtils.equalsIgnoreCaseAny(logValue, "D", "I", "W", "E");
                            case "I":
                                return TextUtils.equalsIgnoreCaseAny(logValue, "I", "W", "E");
                            case "W":
                                return TextUtils.equalsIgnoreCaseAny(logValue, "W", "E");
                        }
                    }
                }
                case TAG -> logValue = logEntry.tag;
                case MSG -> logValue = logEntry.message;
            }
            isMatch = evaluateExpression(expression, logValue);
        } else {
            // match text from one of: message, app, tag
            isMatch = evaluateExpression(expression, logEntry.message) ||
                    evaluateExpression(expression, logEntry.app) ||
                    evaluateExpression(expression, logEntry.tag);
        }
        return isMatch;
    }

    private boolean evaluateExpression(LogFilterExpression expression, String searchField) {
        boolean isMatch = false;
        if (expression == null) return false;
        switch (expression) {
            case EQUALS -> isMatch = TextUtils.equalsIgnoreCase(searchField, value);
            case CONTAINS -> isMatch = TextUtils.containsAny(searchField, true, value);
            case STARTS_WITH -> isMatch = TextUtils.startsWithAny(searchField, true, value);
            case ENDS_WITH -> isMatch = TextUtils.endsWithAny(searchField, true, value);
            // -- NOT versions of above --
            case NOT_EQUALS -> isMatch = !TextUtils.equalsIgnoreCase(searchField, value);
            case NOT_CONTAINS -> isMatch = !TextUtils.containsAny(searchField, true, value);
            case NOT_STARTS_WITH -> isMatch = !TextUtils.startsWithAny(searchField, true, value);
            case NOT_ENDS_WITH -> isMatch = !TextUtils.endsWithAny(searchField, true, value);
        }
        return isMatch;
    }

    public static LogFilterEntry parse(String entry) {
        String[] entryArr = entry.split(":", 2);
        if (entryArr.length < 2) {
            log.warn("parse: invalid entry: {}", entry);
            return null;
        }
        String key = entryArr[0].trim();
        String value = entryArr[1].trim();
        LogFilterEntry expr = new LogFilterEntry();
        if (TextUtils.notEmpty(key) && !TextUtils.equals(key, "*")) {
            String colName = key.toUpperCase();
            try {
                expr.column = LogsTableModel.Columns.valueOf(colName);
            } catch (IllegalArgumentException e) {
                log.warn("parse: invalid column: {}", colName);
            }
        }
        if (TextUtils.equalsIgnoreCaseAny(value, "*", "")) {
            return expr;
        }
        char firstChar = value.charAt(0);
        boolean isNotExpression = false;
        if (firstChar == '!') {
            isNotExpression = true;
            firstChar = value.charAt(1);
        }
        if (firstChar == '*') {
            if (isNotExpression) expr.expression = LogFilterExpression.NOT_ENDS_WITH;
            else expr.expression = LogFilterExpression.ENDS_WITH;
        }
        char lastChar = value.charAt(value.length() - 1);
        // fix some invalid expressions such as:
        // - ENDS_WITH and last char = "*"
        // - LEVEL and last char = "+"
        if (lastChar == '*' || (expr.column == LogsTableModel.Columns.LEVEL && lastChar == '+')) {
            if (expr.expression == LogFilterExpression.ENDS_WITH) expr.expression = LogFilterExpression.CONTAINS;
            else if (expr.expression == LogFilterExpression.NOT_ENDS_WITH) expr.expression = LogFilterExpression.NOT_CONTAINS;
            else if (isNotExpression) expr.expression = LogFilterExpression.NOT_STARTS_WITH;
            else expr.expression = LogFilterExpression.STARTS_WITH;
        }
        int stPos = 0;
        if (isNotExpression) stPos++;
        switch (expr.expression) {
            case CONTAINS:
            case ENDS_WITH:
            case NOT_CONTAINS:
            case NOT_ENDS_WITH:
                stPos++;
                break;
        }
        // if (expr.expression == Expression.ENDS_WITH || expr.expression == Expression.CONTAINS) stPos++;

        int endPos = value.length();
        switch (expr.expression) {
            case CONTAINS:
            case STARTS_WITH:
            case NOT_CONTAINS:
            case NOT_STARTS_WITH:
                endPos--;
                break;
        }
        // if (expr.expression == Expression.STARTS_WITH || expr.expression == Expression.CONTAINS) endPos--;

        expr.value = value.substring(stPos, endPos);
        return expr;
    }

}
