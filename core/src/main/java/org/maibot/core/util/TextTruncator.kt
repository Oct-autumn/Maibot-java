package org.maibot.core.util

/**
 * 存档文本截断
 */
object TextTruncator {
    /**
     * 按最大字符数截断文本，超长时追加标记；`maxChars <= 0` 表示不截断
     */
    fun truncate(text: String?, maxChars: Int, truncatedSuffix: String?): String? {
        if (text == null) {
            return null
        }
        if (maxChars <= 0 || text.length <= maxChars) {
            return text
        }
        return text.substring(0, maxChars) + (truncatedSuffix ?: "")
    }
}
