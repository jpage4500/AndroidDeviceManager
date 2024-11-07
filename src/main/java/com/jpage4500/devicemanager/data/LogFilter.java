package com.jpage4500.devicemanager.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * represents a named filter - which can contain multiple filter expressions
 */
public class LogFilter {
    private static final Logger log = LoggerFactory.getLogger(LogFilter.class);

    public String name;
    public boolean isSystemFilter; // true for built-in filters that can't be edited/deleted
    public List<LogFilterEntry> filterList;

    public LogFilter() {
    }

    public LogFilter(LogFilter copy) {
        this.name = "Copy of " + copy.name;
        this.isSystemFilter = copy.isSystemFilter;
        this.filterList = new ArrayList<>();
        copy.filterList.forEach(logEntry -> {
            LogFilterEntry copyEntry = LogFilterEntry.parse(logEntry.toString());
            this.filterList.add(copyEntry);
        });
    }

    public boolean isMatch(LogEntry logEntry) {
        if (filterList == null) return false;
        // iterate until 1 filter is 'false'
        for (LogFilterEntry filter : filterList) {
            if (!filter.isMatch(logEntry)) return false;
        }
        // all filters matched - return true
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (filterList != null) {
            for (LogFilterEntry expression : filterList) {
                if (!sb.isEmpty()) sb.append(" && ");
                sb.append(expression);
            }
        }
        return name + " -> " + sb;
    }

    public static LogFilter parse(String filterText) {
        LogFilter filter = new LogFilter();
        if (filterText == null) return filter;
        filter.filterList = new ArrayList<>();
        // TODO: support more than just "&&" (ie: "||")
        String[] filterArr = filterText.split(" && ");
        for (String entry : filterArr) {
            LogFilterEntry expr = LogFilterEntry.parse(entry);
            if (expr != null) {
                filter.filterList.add(expr);
            }
        }
        return filter;
    }

}
