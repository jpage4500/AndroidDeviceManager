package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.table.ExploreTableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Objects;

public class ExplorerRowComparator implements Comparator<DeviceFile> {
    private static final Logger log = LoggerFactory.getLogger(ExplorerRowComparator.class);

    private final ExploreTableModel.Columns column;

    public ExplorerRowComparator(ExploreTableModel.Columns column) {
        this.column = column;
    }

    @Override
    public int compare(DeviceFile o1, DeviceFile o2) {
        switch (column) {
            case NAME:
                return sortByName(o1, o2);
            case SIZE:
                return sortBySize(o1, o2);
            case DATE:
                return sortByDate(o1, o2);
            default:
                throw new IllegalArgumentException();
        }
    }

    private int sortByDate(DeviceFile o1, DeviceFile o2) {
        // always sort folders on top
        int rc = sortDir(o1, o2);
        if (rc == 0) {
            // secondary sort: date
            long date1 = o1.dateMs;
            long date2 = o2.dateMs;
            rc = Long.compare(date1, date2);
        }
        return rc;
    }

    private int sortBySize(DeviceFile o1, DeviceFile o2) {
        // always sort folders on top
        int rc = sortDir(o1, o2);
        if (rc == 0) {
            // secondary sort: size
            rc = Long.compare(o1.size, o2.size);
        }
        return rc;
    }

    private int sortByName(DeviceFile o1, DeviceFile o2) {
        // always sort folders on top
        int rc = sortDir(o1, o2);
        if (rc == 0) {
            // secondary sort: name
            String name1 = o1.name;
            String name2 = o2.name;
            rc = Objects.compare(name1, name2, String::compareToIgnoreCase);
        }
        return rc;
    }

    private int sortDir(DeviceFile o1, DeviceFile o2) {
        // always sort folders on top
        boolean o1DirOrLink = o1.isDirectory || o1.isSymbolicLink;
        boolean o2DirOrLink = o2.isDirectory || o2.isSymbolicLink;
        if (o1DirOrLink != o2DirOrLink) {
            // dir/link on top always
            if (o1DirOrLink) return -1;
            else return 1;
        }
        return 0;
    }

}
