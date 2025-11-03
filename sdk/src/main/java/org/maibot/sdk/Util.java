package org.maibot.sdk;

public class Util {

    public static String strAbbreviate(String str, int maxLength, int threshold) {
        if (str.length() <= maxLength) {
            return str;
        } else {
            return String.format(
              "%s...%s(len=%d)",
              str.substring(0, threshold),
              str.substring(str.length() - threshold),
              str.length()
            );
        }
    }
}
