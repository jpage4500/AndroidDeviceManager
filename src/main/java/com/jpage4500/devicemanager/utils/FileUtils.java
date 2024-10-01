package com.jpage4500.devicemanager.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

    // when max filesize is exceeded - remove all but this percent of records (should be between 50-90%)
    private static final double MAX_EXCEEDED_KEEP_PERCENT = .70;

    /**
     * remove the first 30% of the given file's lines (assuming file is made up of multiple lines)
     * - see MAX_EXCEEDED_KEEP_PERCENT
     */
    public static void truncateFile(File file) {
        log.debug("truncateFile: filesize too large: {} bytes, {}", file.length(), file.getAbsolutePath());
        // file is getting too large!!
        int numLines;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            for (numLines = 0; (reader.readLine()) != null; numLines++) {
                // just counting lines first..
            }
            reader.close();

            int discardLines = numLines - ((int) (numLines * MAX_EXCEEDED_KEEP_PERCENT));

            // create backup file
            File backupFile = new File(file.getParentFile(), file.getName() + ".bak");
            FileOutputStream fos = new FileOutputStream(backupFile, false);
            OutputStreamWriter writer = new OutputStreamWriter(fos);

            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String line;
            for (int count = 0; (line = reader.readLine()) != null; count++) {
                // skip the first < discardLines lines
                if (count < discardLines) {
                    continue;
                }
                writer.append(line);
                writer.append('\n');
            }
            writer.close();
            fos.close();
            reader.close();

            // finally - move backup file
            boolean isDelete = file.delete();
            boolean isRename = backupFile.renameTo(file);
            log.debug("truncateFile: max filesize exceeded! {}, lines:{}, removing:{}, delete:{}, rename:{}", file.length(), numLines, discardLines, isDelete, isRename);
        } catch (Exception e) {
            log.error("truncateFile: Exception: file:{}, {}", file, e.getMessage());
        }
    }

    private static final String[] SIZE_UNITS = new String[]{"b", "k", "M", "G", "TB"};
    private static final DecimalFormat sizeDisplayFormat = new DecimalFormat("#,##0.#");

    /**
     * return string description of number of bytes (45k, 320b, 1.1M)
     */
    public static String bytesToDisplayString(Long sizeInBytes) {
        if (sizeInBytes == null) return "";
        else if (sizeInBytes <= 0) return String.valueOf(sizeInBytes);
        int digitGroups = (int) (Math.log10(sizeInBytes) / Math.log10(1024));
        return sizeDisplayFormat.format(sizeInBytes / Math.pow(1024, digitGroups)) + SIZE_UNITS[digitGroups];
    }

    private static final DecimalFormat sizeGigDisplayFormat = new DecimalFormat("0.0");

    /**
     * return string description of number of bytes (in X.X gig)
     */
    public static String bytesToGigDisplayString(Long sizeInBytes) {
        if (sizeInBytes == null) return "";
        else if (sizeInBytes <= 0) return "0.0G";

        int digitGroups = 3; //(int) (Math.log10(sizeInBytes) / Math.log10(1024));
        return sizeGigDisplayFormat.format(sizeInBytes / Math.pow(1024, digitGroups)) + SIZE_UNITS[digitGroups];
    }

    public static String getNameNoExt(File file) {
        if (file == null) return null;
        String name = file.getName();
        int i = name.lastIndexOf('.');
        if (i == -1) return name;
        return name.substring(0, i);
    }

    public static class FileStats {
        public int numTotal;
        public int numFiles;
        public int numFolders;  // # folders
        public int numApk;      // # apk files
        public List<String> nameList = new ArrayList<>();  // list of filename's
    }

    /**
     * come up with total number of folders/files/etc from a File
     */
    public static FileStats getFileStats(List<File> fileList) {
        FileStats stats = new FileStats();
        for (File file : fileList) {
            getFileListStatsInternal(file, stats);
        }
        return stats;
    }

    /**
     * come up with total number of folders/files/etc from a File
     */
    public static FileStats getFileStats(File file) {
        FileStats stats = new FileStats();
        getFileListStatsInternal(file, stats);
        return stats;
    }

    private static void getFileListStatsInternal(File file, FileStats stats) {
        stats.numTotal++;
        String name = file.getName();
        stats.nameList.add(name);
        if (file.isDirectory()) {
            stats.numFolders++;
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    getFileListStatsInternal(child, stats);
                }
            }
        } else {
            stats.numFiles++;
            if (name.endsWith(".apk")) {
                stats.numApk++;
            }
        }
    }
}
