package org.maibot.sdk.util

object StrUtils {
    @JvmStatic
    fun strAbbreviate(str: String, maxLength: Int, threshold: Int): String {
        return if (str.length <= maxLength) {
            str
        } else {
            String.format(
                "%s...%s(len=%d)",
                str.substring(0, threshold),
                str.substring(str.length - threshold),
                str.length
            )
        }
    }
}
