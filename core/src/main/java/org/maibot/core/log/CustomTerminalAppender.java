package org.maibot.core.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Setter;
import org.jline.reader.LineReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class CustomTerminalAppender extends AppenderBase<ILoggingEvent> {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Setter
    private LineReader lineReader;

    private String encode(ILoggingEvent event) {
        var levelColor = switch (event.getLevel().toString()) {
            case "TRACE" -> "FG_BRIGHT_BLACK";
            case "DEBUG" -> "FG_BLUE";
            case "INFO" -> "FG_GREEN";
            case "WARN" -> "FG_YELLOW";
            case "ERROR" -> "FG_RED";
            default -> "FG_DEFAULT";
        };

        var template = "{1} @{FG_BRIGHT_CYAN [{2}]}@ @{{3} {4}}@ @{FG_CYAN {5}}@ - @{{3} {6}}@\n";

        return AnsiFormatter.render(
                template,
                DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp()).atZone(ZoneId.systemDefault())),
                event.getThreadName(),
                levelColor,
                String.format("%-5s", event.getLevel()),
                compressLoggerName(event.getLoggerName(), 30),
                event.getFormattedMessage()
        );
    }

    /**
     * 压缩LoggerName
     * <p>
     * 当超出指定长度时，依次进行以下压缩尝试： <br>
     * 1. 对于点分割的LoggerName，尝试将前缀包名缩写为首字母，直到不超出长度或无法再压缩为止。 <br>
     * 1.1. 若全部前缀包名压缩后仍然超出长度，则继续尝试移除最后一个包名的中间部分（中间用...连接），直到不超出长度或无法再压缩为止。 <br>
     * 2. 对于非点分割的LoggerName，尝试移除中间部分（中间用...连接），直到不超出长度或无法再压缩为止。 <br>
     *
     * @param loggerName 原始LoggerName
     * @param maxLen     最大长度
     * @return 压缩后的LoggerName
     */
    private String compressLoggerName(String loggerName, int maxLen) {
        if (loggerName.length() <= maxLen) {
            return loggerName;
        }

        String[] parts = loggerName.split("\\.");
        if (parts.length > 1) {
            // 点分割的LoggerName，尝试缩写包名
            for (int i = 0; i < parts.length - 1; i++) {
                if (parts[i].length() > 1) {
                    parts[i] = parts[i].substring(0, 1); // 缩写为首字母
                }
                String compressed = String.join(".", parts);
                if (compressed.length() <= maxLen) {
                    return compressed;
                }
            }
            // 如果全部前缀包名缩写后仍然超出长度，尝试移除最后一个包名的中间部分
            String lastPart = parts[parts.length - 1];
            int availableLen = maxLen - (loggerName.length() - lastPart.length()) - 3; // 3是"..."的长度
            if (availableLen > 0 && lastPart.length() > availableLen) {
                return String.join(".", Arrays.copyOf(parts, parts.length - 1)) + "." +
                        lastPart.substring(0, availableLen / 2) + "..." + lastPart.substring(lastPart.length() - availableLen / 2);
            }
        } else {
            // 非点分割的LoggerName，尝试移除中间部分
            int availableLen = maxLen - 3; // 3是"..."的长度
            if (availableLen > 0 && loggerName.length() > availableLen) {
                return loggerName.substring(0, availableLen / 2) + "..." + loggerName.substring(loggerName.length() - availableLen / 2);
            }
        }

        // 无法压缩到指定长度，返回原始名称
        return loggerName;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        try {
            String msg = encode(eventObject);
            if (lineReader != null) {
                lineReader.printAbove(msg);
            } else {
                System.out.print(msg);
            }
        } catch (Exception ignore) {
            // 处理异常
        }
    }
}
