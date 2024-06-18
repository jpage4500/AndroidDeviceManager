package com.jpage4500.devicemanager.data;

import com.jpage4500.devicemanager.table.LogsTableModel;
import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class LogFilter {
    private static final Logger log = LoggerFactory.getLogger(LogFilter.class);
    List<FilterExpression> filterList;

    public enum Expression {
        EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH
    }

    public static class FilterExpression {
        public LogsTableModel.Columns column;
        public boolean isNotExpression;
        public Expression expression = Expression.EQUALS;
        public String value;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (column == null) sb.append("*");
            else sb.append(column.name().toLowerCase());
            sb.append(":");
            if (TextUtils.equalsIgnoreCaseAny(value, "*", "")) {
                sb.append("*");
            } else {
                if (isNotExpression) sb.append("!");
                switch (expression) {
                    case STARTS_WITH:
                        sb.append(value);
                        sb.append("*");
                        break;
                    case ENDS_WITH:
                        sb.append("*");
                        sb.append(value);
                        break;
                    case CONTAINS:
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
                    case PID -> logValue = logEntry.pid;
                    case LEVEL -> {
                        logValue = logEntry.level;
                        //log.trace("isMatch: {}, val:{}, expr:{}", logValue, value, expression);
                        if (value != null && expression == Expression.STARTS_WITH) {
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
            return isNotExpression != isMatch;
        }

        private boolean evaluateExpression(Expression expression, String searchField) {
            boolean isMatch = false;
            if (expression == null) return false;
            switch (expression) {
                case EQUALS -> isMatch = TextUtils.equalsIgnoreCase(searchField, value);
                case CONTAINS -> isMatch = TextUtils.containsAny(searchField, true, value);
                case STARTS_WITH -> isMatch = TextUtils.startsWithAny(searchField, true, value);
                case ENDS_WITH -> isMatch = TextUtils.endsWithAny(searchField, true, value);
            }
            return isMatch;
        }
    }

    public boolean isMatch(LogEntry logEntry) {
        if (filterList == null) return false;
        // iterate until 1 filter is 'false'
        for (FilterExpression filter : filterList) {
            if (!filter.isMatch(logEntry)) return false;
        }
        // all filters matched - return true
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (FilterExpression expression : filterList) {
            if (!sb.isEmpty()) sb.append(" && ");
            sb.append(expression);
        }
        return sb.toString();
    }

    public static LogFilter parse(String filterText) {
        if (filterText == null) return null;
        LogFilter filter = new LogFilter();
        filter.filterList = new ArrayList<>();
        // TODO: support more than just "&&" (ie: "||")
        String[] filterArr = filterText.split(" && ");
        for (String entry : filterArr) {
            String[] entryArr = entry.split(":", 2);
            String key = entryArr[0].trim();
            String value = entryArr[1].trim();
            LogFilter.FilterExpression expr = new LogFilter.FilterExpression();
            if (TextUtils.notEmpty(key)) {
                String colName = key.toUpperCase();
                try {
                    expr.column = LogsTableModel.Columns.valueOf(colName);
                } catch (IllegalArgumentException e) {
                }
            }
            if (TextUtils.equalsIgnoreCaseAny(value, "*", "")) {
                filter.filterList.add(expr);
                continue;
            }
            char firstChar = value.charAt(0);
            if (firstChar == '!') {
                expr.isNotExpression = true;
                firstChar = value.charAt(1);
            }
            if (firstChar == '*') {
                expr.expression = Expression.ENDS_WITH;
            }
            char lastChar = value.charAt(value.length() - 1);
            if (lastChar == '*' || (expr.column == LogsTableModel.Columns.LEVEL && lastChar == '+')) {
                if (expr.expression == Expression.ENDS_WITH) expr.expression = Expression.CONTAINS;
                else expr.expression = Expression.STARTS_WITH;
            }
            int stPos = 0;
            if (expr.isNotExpression) stPos++;
            if (expr.expression == Expression.ENDS_WITH || expr.expression == Expression.CONTAINS) stPos++;

            int endPos = value.length();
            if (expr.expression == Expression.STARTS_WITH || expr.expression == Expression.CONTAINS) endPos--;

            expr.value = value.substring(stPos, endPos);

            //log.trace("parse: expr:{}", GsonHelper.toJson(expr));
            filter.filterList.add(expr);
        }
        return filter;
    }

}
