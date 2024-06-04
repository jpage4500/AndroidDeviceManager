package com.jpage4500.devicemanager.data;

import com.jpage4500.devicemanager.table.LogsTableModel;
import com.jpage4500.devicemanager.utils.TextUtils;

import java.util.ArrayList;
import java.util.List;

public class LogFilter {
    List<FilterExpression> filterList;

    public enum Expression {
        STARTS_WITH, ENDS_WITH, CONTAINS
    }

    public static class FilterExpression {
        public LogsTableModel.Columns column;
        public boolean isNotExpression;
        public Expression expression;
        public String value;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (column == null) sb.append("*");
            else sb.append(column.name().toLowerCase());
            sb.append(":");
            if (isNotExpression) sb.append("!");
            switch (expression) {
                case STARTS_WITH:
                    sb.append("*");
                    sb.append(value);
                    break;
                case ENDS_WITH:
                    sb.append(value);
                    sb.append("*");
                    break;
                case CONTAINS:
                    sb.append("*");
                    sb.append(value);
                    sb.append("*");
                    break;
            }
            return sb.toString();
        }

        public boolean isMatch(LogEntry logEntry) {
            boolean isMatch = false;
            if (column != null) {
                String searchField = null;
                switch (column) {
                    case DATE -> searchField = logEntry.date;
                    case APP -> searchField = logEntry.app;
                    case TID -> searchField = logEntry.tid;
                    case PID -> searchField = logEntry.pid;
                    case LEVEL -> searchField = logEntry.level;
                    case TAG -> searchField = logEntry.tag;
                    case MSG -> searchField = logEntry.message;
                }
                isMatch = evaluateExpression(expression, searchField);
            } else {
                isMatch = evaluateExpression(expression, logEntry.message) ||
                        evaluateExpression(expression, logEntry.app) ||
                        evaluateExpression(expression, logEntry.tag);
            }
            return isNotExpression != isMatch;
        }

        private boolean evaluateExpression(Expression expression, String searchField) {
            boolean isMatch = false;
            switch (expression) {
                case STARTS_WITH -> isMatch = TextUtils.startsWithAny(searchField, true, value);
                case ENDS_WITH -> isMatch = TextUtils.endsWithAny(searchField, true, value);
                case CONTAINS -> isMatch = TextUtils.containsAny(searchField, true, value);
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
        LogFilter filter = null;
        // TODO: support more than just "&&" (ie: "||")
        String[] filterArr = filterText.split(" && ");
        for (String entry : filterArr) {
            String[] entryArr = entry.split(":", 2);
            LogFilter.FilterExpression expr = new LogFilter.FilterExpression();
            expr.column = LogsTableModel.Columns.valueOf(entryArr[0].toUpperCase());
            String value = entryArr[1].trim();
            if (TextUtils.isEmpty(value)) continue;
            char firstChar = value.charAt(0);
            if (firstChar == '!') {
                expr.isNotExpression = true;
                firstChar = value.charAt(1);
            }
            if (firstChar == '*') {
                expr.expression = Expression.STARTS_WITH;
            }
            char lastChar = value.charAt(value.length() - 1);
            if (lastChar == '*') {
                if (expr.expression == Expression.STARTS_WITH) expr.expression = Expression.CONTAINS;
                else expr.expression = Expression.ENDS_WITH;
            }
            int stPos = 0;
            if (expr.isNotExpression) stPos++;
            if (expr.expression == Expression.STARTS_WITH || expr.expression == Expression.CONTAINS) stPos++;

            int endPos = value.length() - 1;
            if (expr.expression == Expression.ENDS_WITH) endPos--;

            expr.value = value.substring(stPos, endPos);

            if (filter == null) {
                filter = new LogFilter();
                filter.filterList = new ArrayList<>();
            }
            filter.filterList.add(expr);
        }
        return filter;
    }

}
