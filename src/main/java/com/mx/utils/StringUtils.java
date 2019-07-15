package com.mx.utils;

/**
 * @author milo
 */
public final class StringUtils {

    public static String upperFirstChar(String name) {
        if (name.length() < 1) {
            return name;
        }
        String firstChar = name.substring(0, 1).toUpperCase();
        if (name.length() > 1) {
            return firstChar + name.substring(1);
        }
        return firstChar;
    }
}
