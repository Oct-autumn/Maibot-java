package org.maibot.core.log;

import org.jline.jansi.Ansi;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnsiFormatter {
    private static final Pattern FORMAT_BLOCK_PATTERN =
            Pattern.compile("@\\{(?<codes>[^ ]+) (?<text>.*?)}@");
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{(?<idx>\\d*)}");

    public static final String HEX_PREFIX = "#";

    /**
     * 解析并渲染ANSI字符串
     * <p>
     * render将自动解析其中的<code>@{code(,code)* text}@</code>模式，并应用args进行格式化。
     * <br>
     * 例如：
     * <blockquote><pre>
     *     AnsiParser.render("@{red,underline Hello {}!}@", "World");
     *     // 渲染结果为：Hello World!，其中Hello World!为红色并带下划线
     *     AnsiParser.render("@{red {}}@, {}!}@", "Hello", "World");
     *     // 渲染结果为：Hello, World!，其中Hello为红色
     *     AnsiParser.render("@{green Bold {2}!}@ {1}", "Text1", "Text2");
     *     // 渲染结果为：Bold Text2! Text1，其中Bold Text2!为绿色
     *     AnsiParser.render("@{{} Hello {}!}@", "#ff0000", "World");
     *     // 渲染结果为：Hello World!，其中Hello World!为红色（24位色）
     * </pre></blockquote>
     * <p>
     *
     * @param format 模板串
     * @param args   参数
     * @return 渲染后的字符串
     */
    public static String render(String format, Object... args) {
        // Step 1: 占位符索引模式检查与转换
        format = indexPlaceHolder(format, args.length);

        // Step 1: 处理 @{}@ block
        Matcher m = FORMAT_BLOCK_PATTERN.matcher(format);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String codesRaw = m.group("codes");
            String text = m.group("text");

            // 递归处理内部占位符稍后统一做，这里只做样式
            List<String> codes = Arrays.stream(codesRaw.split(","))
                    .map(String::trim)
                    .toList();
            Ansi ansi = Ansi.ansi();
            for (String code : codes) {
                var matcher = PLACEHOLDER_PATTERN.matcher(code);
                if (matcher.matches()) {
                    // 由参数指定样式
                    int idx = Integer.parseInt(matcher.group("idx")) - 1;
                    if (!(args[idx] instanceof String)) {
                        throw new IllegalArgumentException("ANSI style code argument must be a String.");
                    }
                    applyStyle(ansi, String.valueOf(args[idx]));
                } else {
                    // 直接样式代码
                    applyStyle(ansi, code);
                }

            }
            ansi.a(text).reset();

            m.appendReplacement(sb, Matcher.quoteReplacement(ansi.toString()));
        }
        m.appendTail(sb);

        // Step 2: 替换 {} 或 {n}
        return fillArgs(sb.toString(), args);
    }

    /**
     * 检查占位符使用模式是否合法，并将其切换为索引模式
     * <p>
     * 占位符有两种使用模式：顺序模式（{}）和索引模式（{n}）。
     * <br>
     * 不能混用两种模式，索引也不能超出参数数量，否则抛出异常。
     * <br>
     * 例如：
     * <blockquote><pre>
     *     AnsiParser.placeHolderModeCheck("Hello {}, {}!", 2); // "Hello {1}, {2}!"
     *     AnsiParser.placeHolderModeCheck("Hello {1}, {1}!", 2); // "Hello {1}, {1}!"
     *     AnsiParser.placeHolderModeCheck("Hello {1}, {3}!", 2); // 抛出异常，索引超出范围
     *     AnsiParser.placeHolderModeCheck("Hello {1}, {}!", 2); // 抛出异常，混用模式
     * </pre></blockquote>
     *
     * @param format   模板串
     * @param argCount 参数数量
     * @return true=索引模式，false=顺序模式，null=无占位符
     * @throws IllegalArgumentException 如果占位符使用不合法
     */
    private static String indexPlaceHolder(String format, int argCount) {
        Boolean useIndex = null; // null=未确定，true=索引模式，false=顺序模式

        Matcher placeHolderMatcher = PLACEHOLDER_PATTERN.matcher(format);

        StringBuilder sb = new StringBuilder();
        int seqIndex = 1;
        while (placeHolderMatcher.find()) {
            String idxStr = placeHolderMatcher.group("idx");
            boolean thisIsIndex = !idxStr.isEmpty();

            if (useIndex == null) {
                useIndex = thisIsIndex;
            } else if (useIndex != thisIsIndex) {
                throw new IllegalArgumentException(
                        "Mixed use of indexed and sequential placeholders is not allowed.");
            }

            int idx;
            if (useIndex) {
                idx = Integer.parseInt(idxStr);
                if (!(idx >= 1 && idx <= argCount)) {
                    throw new IllegalArgumentException("Placeholder index " + idx + " out of range.");
                }
                // 保持不变
                placeHolderMatcher.appendReplacement(sb, Matcher.quoteReplacement(placeHolderMatcher.group()));
            } else {
                idx = seqIndex;
                seqIndex++;
                if (!(idx <= argCount)) {
                    throw new IllegalArgumentException("Placeholder index " + idx + " out of range.");
                }
                // 替换为索引模式
                placeHolderMatcher.appendReplacement(sb, "{" + (idx) + "}");
            }
        }
        placeHolderMatcher.appendTail(sb);

        return sb.toString();
    }

    private static void applyStyle(Ansi ansi, String code) {
        switch (code.toUpperCase()) {
            /* Foreground colors */
            case "BLACK", "FG_BLACK" -> ansi.fgBlack();
            case "RED", "FG_RED" -> ansi.fgRed();
            case "GREEN", "FG_GREEN" -> ansi.fgGreen();
            case "YELLOW", "FG_YELLOW" -> ansi.fgYellow();
            case "BLUE", "FG_BLUE" -> ansi.fgBlue();
            case "MAGENTA", "FG_MAGENTA" -> ansi.fgMagenta();
            case "CYAN", "FG_CYAN" -> ansi.fgCyan();
            case "WHITE", "FG_WHITE" -> ansi.fg(Ansi.Color.WHITE.fg());
            case "DEFAULT", "FG_DEFAULT" -> ansi.fgDefault();
            case "B_BLACK", "BRIGHT_BLACK", "FG_BRIGHT_BLACK" -> ansi.fgBrightBlack();
            case "B_RED", "BRIGHT_RED", "FG_BRIGHT_RED" -> ansi.fgBrightRed();
            case "B_GREEN", "BRIGHT_GREEN", "FG_BRIGHT_GREEN" -> ansi.fgBrightGreen();
            case "B_YELLOW", "BRIGHT_YELLOW", "FG_BRIGHT_YELLOW" -> ansi.fgBrightYellow();
            case "B_BLUE", "BRIGHT_BLUE", "FG_BRIGHT_BLUE" -> ansi.fgBrightBlue();
            case "B_MAGENTA", "BRIGHT_MAGENTA", "FG_BRIGHT_MAGENTA" -> ansi.fgBrightMagenta();
            case "B_CYAN", "BRIGHT_CYAN", "FG_BRIGHT_CYAN" -> ansi.fgBrightCyan();
            case "B_WHITE", "BRIGHT_WHITE", "FG_BRIGHT_WHITE" -> ansi.fg(Ansi.Color.WHITE.fgBright());
            /* Background colors */
            case "BG_BLACK" -> ansi.bg(Ansi.Color.BLACK.bg());
            case "BG_RED" -> ansi.bg(Ansi.Color.RED.bg());
            case "BG_GREEN" -> ansi.bg(Ansi.Color.GREEN.bg());
            case "BG_YELLOW" -> ansi.bg(Ansi.Color.YELLOW.bg());
            case "BG_BLUE" -> ansi.bg(Ansi.Color.BLUE.bg());
            case "BG_MAGENTA" -> ansi.bg(Ansi.Color.MAGENTA.bg());
            case "BG_CYAN" -> ansi.bg(Ansi.Color.CYAN.bg());
            case "BG_WHITE" -> ansi.bg(Ansi.Color.WHITE.bg());
            case "BG_DEFAULT" -> ansi.bgDefault();
            case "BG_B_BLACK" -> ansi.bg(Ansi.Color.BLACK.bgBright());
            case "BG_B_RED" -> ansi.bg(Ansi.Color.RED.bgBright());
            case "BG_B_GREEN" -> ansi.bg(Ansi.Color.GREEN.bgBright());
            case "BG_B_YELLOW" -> ansi.bg(Ansi.Color.YELLOW.bgBright());
            case "BG_B_BLUE" -> ansi.bg(Ansi.Color.BLUE.bgBright());
            case "BG_B_MAGENTA" -> ansi.bg(Ansi.Color.MAGENTA.bgBright());
            case "BG_B_CYAN" -> ansi.bg(Ansi.Color.CYAN.bgBright());
            case "BG_B_WHITE" -> ansi.bg(Ansi.Color.WHITE.bgBright());
            /* Styles */
            case "RESET" -> ansi.reset();
            case "BOLD", "INTENSITY_BOLD" -> ansi.a(Ansi.Attribute.INTENSITY_BOLD);
            case "FAINT", "INTENSITY_FAINT" -> ansi.a(Ansi.Attribute.INTENSITY_FAINT);
            case "INTENSITY_NORMAL" -> ansi.a(Ansi.Attribute.INTENSITY_BOLD_OFF);
            case "ITALIC", "ITALIC_ON" -> ansi.a(Ansi.Attribute.ITALIC);
            case "ITALIC_OFF" -> ansi.a(Ansi.Attribute.ITALIC_OFF);
            case "UNDERLINE", "UNDERLINE_ON" -> ansi.a(Ansi.Attribute.UNDERLINE);
            case "UNDERLINE_DOUBLE", "UNDERLINE_DOUBLE_ON" -> ansi.a(Ansi.Attribute.UNDERLINE_DOUBLE);
            case "UNDERLINE_OFF" -> ansi.a(Ansi.Attribute.UNDERLINE_OFF);
            case "SLOW_BLINK", "BLINK_SLOW" -> ansi.a(Ansi.Attribute.BLINK_SLOW);
            case "RAPID_BLINK", "BLINK_RAPID" -> ansi.a(Ansi.Attribute.BLINK_FAST);
            case "BLINK_OFF" -> ansi.a(Ansi.Attribute.BLINK_OFF);
            case "NEGATIVE_ON" -> ansi.a(Ansi.Attribute.NEGATIVE_ON);
            case "NEGATIVE_OFF" -> ansi.a(Ansi.Attribute.NEGATIVE_OFF);
            case "CONCEAL_ON" -> ansi.a(Ansi.Attribute.CONCEAL_ON);
            case "CONCEAL_OFF" -> ansi.a(Ansi.Attribute.CONCEAL_OFF);
            case "STRIKETHROUGH_ON" -> ansi.a(Ansi.Attribute.STRIKETHROUGH_ON);
            case "STRIKETHROUGH_OFF" -> ansi.a(Ansi.Attribute.STRIKETHROUGH_OFF);
            default -> {
                if (code.matches(HEX_PREFIX + "[0-9a-fA-F]{6}")
                        || code.matches("FG" + HEX_PREFIX + "[0-9a-fA-F]{6}")) {
                    // 24-bit color
                    int r = Integer.parseInt(code.substring(1, 3), 16);
                    int g = Integer.parseInt(code.substring(3, 5), 16);
                    int b = Integer.parseInt(code.substring(5, 7), 16);
                    ansi.fgRgb(r, g, b);
                } else if (code.matches("BG" + HEX_PREFIX + "[0-9a-fA-F]{6}")) {
                    // 24-bit background color
                    int r = Integer.parseInt(code.substring(3, 5), 16);
                    int g = Integer.parseInt(code.substring(5, 7), 16);
                    int b = Integer.parseInt(code.substring(7, 9), 16);
                    ansi.bgRgb(r, g, b);
                } else {
                    throw new IllegalArgumentException("Unknown ANSI code: " + code);
                }
            }
        }
    }

    private static String fillArgs(String format, Object[] args) {
        Matcher placeHolderMatcher = PLACEHOLDER_PATTERN.matcher(format);
        StringBuilder sb = new StringBuilder();

        while (placeHolderMatcher.find()) {
            var idx = Integer.parseInt(placeHolderMatcher.group("idx")) - 1;
            placeHolderMatcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(args[idx])));
        }
        placeHolderMatcher.appendTail(sb);

        return sb.toString();
    }
}
