package com.jpage4500.devicemanager.utils;

public class ArrayUtils {

    public static int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) return i;
        }
        return -1;
    }

    public static int indexOf(long[] array, long value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) return i;
        }
        return -1;
    }

    public static <T> int indexOf(T[] array, T value) {
        for (int i = 0; i < array.length; i++) {
            T obj = array[i];
            if (obj != null && (obj == value || obj.equals(value))) return i;
        }
        return -1;
    }

    public static int indexOf(String[] valueArr, String searchFor) {
        if (valueArr == null) return -1;
        for (int i = 0; i < valueArr.length; i++) {
            String value = valueArr[i];
            if (TextUtils.equals(value, searchFor)) return i;
        }
        return -1;
    }
}
