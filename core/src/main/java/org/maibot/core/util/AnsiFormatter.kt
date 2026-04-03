package org.maibot.core.util

import org.jline.jansi.Ansi
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

object AnsiFormatter {
    private const val HEX_PREFIX: String = "#"
    private val FORMAT_BLOCK_PATTERN: Pattern = Pattern.compile("@\\{(?<codes>[^ ]+) (?<text>.*?)}@")
    private val PLACEHOLDER_PATTERN: Pattern = Pattern.compile("\\{(?<idx>\\d*)}")

    /**
     * 解析并渲染ANSI字符串
     *
     *
     * render将自动解析其中的`@{code(,code)* text}@`模式，并应用args进行格式化。
     * <br></br>
     * 例如：
     * <blockquote><pre>
     * AnsiParser.render("@{red,underline Hello {}!}@", "World");
     * // 渲染结果为：Hello World!，其中Hello World!为红色并带下划线
     * AnsiParser.render("@{red {}}@, {}!}@", "Hello", "World");
     * // 渲染结果为：Hello, World!，其中Hello为红色
     * AnsiParser.render("@{green Bold {2}!}@ {1}", "Text1", "Text2");
     * // 渲染结果为：Bold Text2! Text1，其中Bold Text2!为绿色
     * AnsiParser.render("@{{} Hello {}!}@", "#ff0000", "World");
     * // 渲染结果为：Hello World!，其中Hello World!为红色（24位色）
     * </pre></blockquote>
     *
     * @param format 模板串
     * @param args   参数
     * @return 渲染后的字符串
     */
    fun render(format: String, vararg args: Any?): String {
        // Step 1: 占位符索引模式检查与转换
        var format = format
        format = indexPlaceHolder(format, args.size)

        // Step 1: 处理 @{}@ block
        val sb = StringBuilder()

        with(FORMAT_BLOCK_PATTERN.matcher(format)) {
            while (find()) {
                val codesRaw = group("codes")
                val text = group("text")

                // 递归处理内部占位符稍后统一做，这里只做样式
                val ansi = Ansi.ansi()
                val codes = codesRaw.split(",".toRegex()).filter { it.isNotEmpty() }.map { it.trim() }
                for (code in codes) {
                    val matcher = PLACEHOLDER_PATTERN.matcher(code)
                    if (matcher.matches()) {
                        // 由参数指定样式
                        val idx = matcher.group("idx").toInt() - 1
                        require(args[idx] is String) { "ANSI style code argument must be a String." }
                        applyStyle(ansi, args[idx].toString())
                    } else {
                        // 直接样式代码
                        applyStyle(ansi, code)
                    }
                }

                ansi.a(text).reset()

                appendReplacement(sb, Matcher.quoteReplacement(ansi.toString()))
            }
            appendTail(sb)
        }

        // Step 2: 替换 {} 或 {n}
        return fillArgs(sb.toString(), args)
    }

    /**
     * 检查占位符使用模式是否合法，并将其切换为索引模式
     * 
     * 
     * 占位符有两种使用模式：顺序模式（{}）和索引模式（{n}）。
     * <br></br>
     * 不能混用两种模式，索引也不能超出参数数量，否则抛出异常。
     * <br></br>
     * 例如：
     * <blockquote><pre>
     * AnsiParser.placeHolderModeCheck("Hello {}, {}!", 2); // "Hello {1}, {2}!"
     * AnsiParser.placeHolderModeCheck("Hello {1}, {1}!", 2); // "Hello {1}, {1}!"
     * AnsiParser.placeHolderModeCheck("Hello {1}, {3}!", 2); // 抛出异常，索引超出范围
     * AnsiParser.placeHolderModeCheck("Hello {1}, {}!", 2); // 抛出异常，混用模式
    </pre></blockquote> * 
     * 
     * @param format   模板串
     * @param argCount 参数数量
     * @return 转换后的模板串（占位符已切换为索引模式）
     * @throws IllegalArgumentException 如果占位符使用不合法
     */
    private fun indexPlaceHolder(format: String, argCount: Int): String {
        var useIndex: Boolean? = null // null=未确定，true=索引模式，false=顺序模式

        val sb = StringBuilder()
        var seqIndex = 1

        with(PLACEHOLDER_PATTERN.matcher(format)) {
            while (find()) {
                val idxStr = group("idx")
                val thisIsIndex = !idxStr.isEmpty()

                if (useIndex == null) {
                    useIndex = thisIsIndex
                } else require(useIndex == thisIsIndex) { "Mixed use of indexed and sequential placeholders is not allowed." }


                if (useIndex) {
                    val idx = idxStr.toInt()
                    require(idx in 1..argCount) { "Placeholder index $idx out of range." }
                    // 保持不变
                    appendReplacement(sb, Matcher.quoteReplacement(group()))
                } else {
                    val idx = seqIndex
                    require(idx <= argCount) { "Placeholder index $idx out of range." }
                    // 替换为索引模式
                    appendReplacement(sb, "{$idx}")

                    seqIndex++
                }
            }
            appendTail(sb)
        }

        return sb.toString()
    }

    private fun applyStyle(ansi: Ansi, code: String) {
        when (code.uppercase(Locale.getDefault())) {
            "BLACK", "FG_BLACK" -> ansi.fgBlack()
            "RED", "FG_RED" -> ansi.fgRed()
            "GREEN", "FG_GREEN" -> ansi.fgGreen()
            "YELLOW", "FG_YELLOW" -> ansi.fgYellow()
            "BLUE", "FG_BLUE" -> ansi.fgBlue()
            "MAGENTA", "FG_MAGENTA" -> ansi.fgMagenta()
            "CYAN", "FG_CYAN" -> ansi.fgCyan()
            "WHITE", "FG_WHITE" -> ansi.fg(Ansi.Color.WHITE.fg())
            "DEFAULT", "FG_DEFAULT" -> ansi.fgDefault()
            "B_BLACK", "BRIGHT_BLACK", "FG_BRIGHT_BLACK" -> ansi.fgBrightBlack()
            "B_RED", "BRIGHT_RED", "FG_BRIGHT_RED" -> ansi.fgBrightRed()
            "B_GREEN", "BRIGHT_GREEN", "FG_BRIGHT_GREEN" -> ansi.fgBrightGreen()
            "B_YELLOW", "BRIGHT_YELLOW", "FG_BRIGHT_YELLOW" -> ansi.fgBrightYellow()
            "B_BLUE", "BRIGHT_BLUE", "FG_BRIGHT_BLUE" -> ansi.fgBrightBlue()
            "B_MAGENTA", "BRIGHT_MAGENTA", "FG_BRIGHT_MAGENTA" -> ansi.fgBrightMagenta()
            "B_CYAN", "BRIGHT_CYAN", "FG_BRIGHT_CYAN" -> ansi.fgBrightCyan()
            "B_WHITE", "BRIGHT_WHITE", "FG_BRIGHT_WHITE" -> ansi.fg(Ansi.Color.WHITE.fgBright())
            "BG_BLACK" -> ansi.bg(Ansi.Color.BLACK.bg())
            "BG_RED" -> ansi.bg(Ansi.Color.RED.bg())
            "BG_GREEN" -> ansi.bg(Ansi.Color.GREEN.bg())
            "BG_YELLOW" -> ansi.bg(Ansi.Color.YELLOW.bg())
            "BG_BLUE" -> ansi.bg(Ansi.Color.BLUE.bg())
            "BG_MAGENTA" -> ansi.bg(Ansi.Color.MAGENTA.bg())
            "BG_CYAN" -> ansi.bg(Ansi.Color.CYAN.bg())
            "BG_WHITE" -> ansi.bg(Ansi.Color.WHITE.bg())
            "BG_DEFAULT" -> ansi.bgDefault()
            "BG_B_BLACK" -> ansi.bg(Ansi.Color.BLACK.bgBright())
            "BG_B_RED" -> ansi.bg(Ansi.Color.RED.bgBright())
            "BG_B_GREEN" -> ansi.bg(Ansi.Color.GREEN.bgBright())
            "BG_B_YELLOW" -> ansi.bg(Ansi.Color.YELLOW.bgBright())
            "BG_B_BLUE" -> ansi.bg(Ansi.Color.BLUE.bgBright())
            "BG_B_MAGENTA" -> ansi.bg(Ansi.Color.MAGENTA.bgBright())
            "BG_B_CYAN" -> ansi.bg(Ansi.Color.CYAN.bgBright())
            "BG_B_WHITE" -> ansi.bg(Ansi.Color.WHITE.bgBright())
            "RESET" -> ansi.reset()
            "BOLD", "INTENSITY_BOLD" -> ansi.a(Ansi.Attribute.INTENSITY_BOLD)
            "FAINT", "INTENSITY_FAINT" -> ansi.a(Ansi.Attribute.INTENSITY_FAINT)
            "INTENSITY_NORMAL" -> ansi.a(Ansi.Attribute.INTENSITY_BOLD_OFF)
            "ITALIC", "ITALIC_ON" -> ansi.a(Ansi.Attribute.ITALIC)
            "ITALIC_OFF" -> ansi.a(Ansi.Attribute.ITALIC_OFF)
            "UNDERLINE", "UNDERLINE_ON" -> ansi.a(Ansi.Attribute.UNDERLINE)
            "UNDERLINE_DOUBLE", "UNDERLINE_DOUBLE_ON" -> ansi.a(Ansi.Attribute.UNDERLINE_DOUBLE)
            "UNDERLINE_OFF" -> ansi.a(Ansi.Attribute.UNDERLINE_OFF)
            "SLOW_BLINK", "BLINK_SLOW" -> ansi.a(Ansi.Attribute.BLINK_SLOW)
            "RAPID_BLINK", "BLINK_RAPID" -> ansi.a(Ansi.Attribute.BLINK_FAST)
            "BLINK_OFF" -> ansi.a(Ansi.Attribute.BLINK_OFF)
            "NEGATIVE_ON" -> ansi.a(Ansi.Attribute.NEGATIVE_ON)
            "NEGATIVE_OFF" -> ansi.a(Ansi.Attribute.NEGATIVE_OFF)
            "CONCEAL_ON" -> ansi.a(Ansi.Attribute.CONCEAL_ON)
            "CONCEAL_OFF" -> ansi.a(Ansi.Attribute.CONCEAL_OFF)
            "STRIKETHROUGH_ON" -> ansi.a(Ansi.Attribute.STRIKETHROUGH_ON)
            "STRIKETHROUGH_OFF" -> ansi.a(Ansi.Attribute.STRIKETHROUGH_OFF)
            else -> {
                if (code.matches(("$HEX_PREFIX[0-9a-fA-F]{6}").toRegex())) {
                    // 24-bit color
                    val r = code.substring(1, 3).toInt(16)
                    val g = code.substring(3, 5).toInt(16)
                    val b = code.substring(5, 7).toInt(16)
                    ansi.fgRgb(r, g, b)
                } else if (code.matches(("FG$HEX_PREFIX[0-9a-fA-F]{6}").toRegex())) {
                    // 24-bit color
                    val r = code.substring(3, 5).toInt(16)
                    val g = code.substring(5, 7).toInt(16)
                    val b = code.substring(7, 9).toInt(16)
                    ansi.fgRgb(r, g, b)
                } else if (code.matches(("BG$HEX_PREFIX[0-9a-fA-F]{6}").toRegex())) {
                    // 24-bit background color
                    val r = code.substring(3, 5).toInt(16)
                    val g = code.substring(5, 7).toInt(16)
                    val b = code.substring(7, 9).toInt(16)
                    ansi.bgRgb(r, g, b)
                } else {
                    throw IllegalArgumentException("Unknown ANSI code: $code")
                }
            }
        }
    }

    private fun fillArgs(format: String, args: Array<out Any?>): String {
        return StringBuilder().apply {
            with(PLACEHOLDER_PATTERN.matcher(format)) {
                while (find()) {
                    val idx = group("idx").toInt() - 1
                    appendReplacement(this@apply, Matcher.quoteReplacement(args[idx].toString()))
                }
                appendTail(this@apply)
            }
        }.toString()
    }
}
