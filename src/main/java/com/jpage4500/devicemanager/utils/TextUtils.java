package com.jpage4500.devicemanager.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * contains some frequently used string methods
 * NOTE: uses other classes like android.text.com.jpage4500.devicemanager.utils.TextUtils to avoid duplicating logic
 */
public class TextUtils {
    private static final Logger log = LoggerFactory.getLogger(TextUtils.class);

    public static int length(CharSequence str) {
        return str != null ? str.length() : 0;
    }

    /**
     * @return true if string is empty (null or length is 0)
     */
    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    /**
     * @return true if string is NOT empty (not null and length > 0)
     */
    public static boolean notEmpty(CharSequence str) {
        return !TextUtils.isEmpty(str);
    }

    /**
     * @return true if ANY of the values are empty
     */
    public static boolean isEmptyAny(String... valueArr) {
        for (String value : valueArr) {
            if (TextUtils.isEmpty(value)) return true;
        }
        // all values are non-empty
        return false;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == null || b == null) return a == b;
        else return a.equals(b);
    }

    /**
     * @param ignoreCase true to ignore case
     * @return true if a is equal to any of the passed in strings (bArr)
     */
    public static boolean equalsAny(CharSequence src, boolean ignoreCase, CharSequence... searchArr) {
        if (src == null || searchArr == null || searchArr.length == 0) return false;
        for (CharSequence searchText : searchArr) {
            if (ignoreCase) {
                if (equalsIgnoreCase(src, searchText)) return true;
            } else {
                if (equals(src, searchText)) return true;
            }
        }
        return false;
    }

    /**
     * Compare 2 strings; case insensitive
     * see com.jpage4500.devicemanager.utils.TextUtils.equals
     */
    public static boolean equalsIgnoreCase(CharSequence a, CharSequence b) {
        if (a == b) return true;
        int length;
        if (a != null && b != null && (length = a.length()) == b.length()) {
            if (a instanceof String && b instanceof String) {
                return ((String) a).equalsIgnoreCase((String) b);
            } else {
                for (int i = 0; i < length; i++) {
                    if (Character.toLowerCase(a.charAt(i)) != Character.toLowerCase(b.charAt(i))) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if a is equal to any of the passed in strings (bArr)
     */
    public static boolean equalsIgnoreCaseAny(CharSequence a, CharSequence... bArr) {
        for (CharSequence b : bArr) {
            if (equalsIgnoreCase(a, b)) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(String src, String searchText) {
        if (src == null || searchText == null) return false;
        return src.contains(searchText);
    }

    public static boolean containsAny(String src, boolean ignoreCase, CharSequence... searchArr) {
        if (src == null || searchArr == null || searchArr.length == 0) return false;
        for (CharSequence searchText : searchArr) {
            if (ignoreCase) {
                if (containsIgnoreCase(src, searchText.toString())) return true;
            } else {
                if (src.contains(searchText)) return true;
            }
        }
        return false;
    }

    /**
     * Compare 2 strings looking for a case insensitive match of searchText inside of src
     *
     * @param src        - full string to search
     * @param searchText - what we're looking for
     */
    public static boolean containsIgnoreCase(String src, String searchText) {
        if (isEmpty(src)) return false;
        else if (isEmpty(searchText)) return true; // empty string is contained

        final char firstLo = Character.toLowerCase(searchText.charAt(0));
        final char firstUp = Character.toUpperCase(searchText.charAt(0));

        for (int i = src.length() - searchText.length(); i >= 0; i--) {
            // Quick check before calling the more expensive regionMatches() method:
            final char ch = src.charAt(i);
            if (ch != firstLo && ch != firstUp) continue;

            if (src.regionMatches(true, i, searchText, 0, searchText.length())) return true;
        }

        return false;
    }

    public static boolean endsWithIgnoreCase(String src, String endsWith) {
        int suffixLength = endsWith.length();
        return src.regionMatches(true, src.length() - suffixLength, endsWith, 0, suffixLength);
    }

    public static boolean startsWith(String message, String prefixAttachment) {
        if (message == null) return false;
        else return message.startsWith(prefixAttachment);
    }

    public static boolean startsWithAny(String message, boolean ignoreCase, String... startsWithArr) {
        if (message == null) return false;
        for (String str : startsWithArr) {
            if (ignoreCase) {
                int msgLen = length(message);
                int searchLen = length(str);
                if (msgLen >= searchLen) {
                    boolean isFound = message.regionMatches(true, 0, str, 0, searchLen);
                    if (isFound) return true;
                }
            } else {
                if (str.startsWith(str)) return true;
            }
        }
        return false;
    }

    /**
     * return the first non-null, non-empty string passed in
     */
    public static String firstValid(String... nameArr) {
        if (nameArr == null) return null;
        for (String str : nameArr) {
            if (notEmpty(str)) return str;
        }
        return null;
    }

    public static boolean endsWith(String src, String... endsWithArr) {
        if (TextUtils.isEmpty(src) || endsWithArr == null) return false;
        for (String str : endsWithArr) {
            if (src.endsWith(str)) return true;
        }
        return false;
    }

    /**
     * @return a non-null value (empty string or value)
     */
    public static String notNull(String value) {
        return (value == null) ? "" : value;
    }

    public static int indexOf(String value, int searchFor) {
        if (value == null) return -1;
        else return value.indexOf(searchFor);
    }

    public static int indexOf(String value, String searchFor) {
        if (value == null) return -1;
        else return value.indexOf(searchFor);
    }

    public static int indexOf(String[] valueArr, String searchFor) {
        if (valueArr == null) return -1;
        for (int i = 0; i < valueArr.length; i++) {
            String value = valueArr[i];
            if (TextUtils.equals(value, searchFor)) return i;
        }
        return -1;
    }

    public static int indexOf(int[] valueArr, int searchFor) {
        if (valueArr == null) return -1;
        for (int i = 0; i < valueArr.length; i++) {
            if (valueArr[i] == searchFor) return i;
        }
        return -1;
    }

    /**
     * mask string with "*"'s -- all but the last 4 characters
     */
    public static String obfuscate(String token) {
        int len = length(token);
        int startPos = 4;
        int endPos = len - 4;
        if (len < 4) {
            startPos = 0;
            endPos = len;
        } else if (len < 8) {
            startPos = 2;
            endPos = len - 2;
        }
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            if (i >= startPos || i <= endPos) sb.append('*');
            else sb.append(token.charAt(i));
        }
        return sb.toString();
    }

    /**
     * split a string using space as a delimiter and returning the value at index
     */
    public static String split(String str, int index) {
        return split(str, "\\s+", index);
    }

    /**
     * split a string into pieces and return the value at index
     *
     * @return column or null if not found
     */
    public static String split(String str, String regex, int index) {
        if (TextUtils.isEmpty(str)) return null;
        try {
            String[] strArr = str.split(regex);
            if (strArr.length > index) {
                String value = strArr[index];
                return value.trim();
            }
        } catch (Exception e) {
            log.error("split: Exception: str:{}, regex:{}, error:{}", str, regex, e.getMessage());
        }
        return null;
    }

    /**
     * split a string into a List<String>, allowing for quoted strings
     *
     * @param splitChar - character to split text on
     * @return List of Strings; if splitChar isn't found, entire str will be returned
     */
    public static List<String> splitSafe(String str, char splitChar) {
        List<String> resultList = new ArrayList<>();
        if (length(str) == 0) return resultList;
        int start = 0;
        boolean isQuote = false;
        boolean isApostrophe = false;
        for (int pos = 0; pos < str.length(); pos++) {
            char ch = str.charAt(pos);
            if (ch == '\"') isQuote = !isQuote;
            if (ch == '\'') isApostrophe = !isApostrophe;
            else if (ch == splitChar && !isQuote && !isApostrophe) {
                resultList.add(str.substring(start, pos));
                start = pos + 1;
            }
        }
        resultList.add(str.substring(start).trim());
        return resultList;
    }

    /**
     * split a string with the given token
     * - will NOT include empty results ""
     * - will TRIM results (leading and trailing space)
     */
    public static String[] split(String string, String token) {
        if (isEmpty(string)) return new String[0];
        List<String> list = new ArrayList<>();
        String[] arr = string.split(token);
        for (String value : arr) {
            if (length(value) > 0) {
                list.add(value.trim());
            }
        }
        return list.toArray(new String[0]);
    }

    public static long getNumberLong(String text, long defValue) {
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
        }
        return defValue;
    }

}
