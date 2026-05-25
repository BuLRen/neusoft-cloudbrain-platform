package com.xikang.common.utils;

/**
 * String utility class
 */
public class StringUtils {

    private static final String EMPTY = "";

    /**
     * Check if string is empty
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * Check if string is not empty
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * Check if string is blank
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Check if string is not blank
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * Trim string, return null if empty
     */
    public static String trimToNull(String str) {
        String trimmed = str != null ? str.trim() : null;
        return isEmpty(trimmed) ? null : trimmed;
    }

    /**
     * Trim string, return empty string if null
     */
    public static String trimToEmpty(String str) {
        return str != null ? str.trim() : EMPTY;
    }

    /**
     * Get string or default value
     */
    public static String defaultIfEmpty(String str, String defaultStr) {
        return isEmpty(str) ? defaultStr : str;
    }

    /**
     * Get string or empty string
     */
    public static String defaultIfEmpty(String str) {
        return defaultIfEmpty(str, EMPTY);
    }

    /**
     * Convert camelCase to snake_case
     */
    public static String camelToSnake(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return str.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    /**
     * Convert snake_case to camelCase
     */
    public static String snakeToCamel(String str) {
        if (isEmpty(str)) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (char c : str.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                result.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return result.toString();
    }

    private StringUtils() {
        // Utility class, prevent instantiation
    }
}
