package org.maibot.core.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import org.jline.reader.LineReader;
import org.maibot.core.config.MainConfig;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class LogConfig {
    /*-!- 不要在此类中使用Logger -!-*/

    private static final Set<String> AVAL_LEVELS = new HashSet<>(Arrays.asList(
      "TRACE",
      "DEBUG",
      "INFO",
      "WARN",
      "ERROR",
      "OFF"
    ));

    public static void configure(MainConfig.Log conf) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger("ROOT");
        rootLogger.setLevel(Level.TRACE); // 将日志级别设置为 TRACE，以便过滤器可以处理所有级别的日志
        rootLogger.detachAndStopAllAppenders(); // 清除现有的 appender

        rootLogger.addAppender(getTerminalAppender(context, conf.console()));

        if (!AVAL_LEVELS.contains(conf.console().level().toUpperCase())) {
            System.out.printf("<!> 无效的终端日志级别：%s，使用默认级别 - DEBUG\n", conf.console().level());
        }
        if (!AVAL_LEVELS.contains(conf.file().level().toUpperCase())) {
            System.out.printf("<!> 无效的文件日志级别：%s，使用默认级别 - DEBUG\n", conf.file().level());
        }

        if (!conf.file().level().equalsIgnoreCase("OFF")) {
            // 确保日志目录存在
            java.io.File logDir = new java.io.File(conf.file().logDir());
            if (!logDir.exists() && logDir.mkdirs()) {
                System.out.println("创建日志目录: " + logDir.getAbsolutePath());
            }
            rootLogger.addAppender(getFileAppender(context, conf.file()));
        }
    }

    public static CustomTerminalAppender getTerminalAppender(
      LoggerContext context,
      MainConfig.Log.ConsoleLogSettings conf
    ) {
        var terminalAppender = new CustomTerminalAppender();
        terminalAppender.setName("terminal");
        terminalAppender.setContext(context);

        var terminalFilter = createCustomFilter(Level.toLevel(conf.level()), conf.filterRule());
        terminalAppender.addFilter(terminalFilter);

        terminalAppender.start();

        return terminalAppender;
    }

    private static CustomFilter createCustomFilter(Level defaultLevel, List<String> rules) {
        CustomFilter filter = new CustomFilter();
        filter.setDefaultLevel(defaultLevel);
        for (String rule : rules) {
            String[] parts = rule.split(":");
            String packageName = parts[0];
            String level = parts.length > 1 ? parts[1].toUpperCase() : "OFF";

            if (!AVAL_LEVELS.contains(level)) {
                // 无效的日志级别，跳过
                continue;
            }

            filter.addRule(packageName, Level.valueOf(level));
        }
        filter.start();
        return filter;
    }

    private static FileAppender<ILoggingEvent> getFileAppender(
      LoggerContext context,
      MainConfig.Log.FileLogSettings conf
    ) {
        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setName("file");
        fileAppender.setContext(context);
        fileAppender.setFile(conf.logDir() + "/maibot.log");

        {
            PatternLayoutEncoder fileEncoder = new PatternLayoutEncoder();
            fileEncoder.setContext(context);
            fileEncoder.setPattern("%d{yyyy-MM-dd HH:mm:ss} [%thread] " +
                                     "%-5level " +
                                     "%logger - " +
                                     "%msg" +
                                     "%n");
            fileEncoder.start();
            fileAppender.setEncoder(fileEncoder);
        }

        var fileFilter = createCustomFilter(Level.toLevel(conf.level()), conf.filterRule());
        fileAppender.addFilter(fileFilter);

        fileAppender.start();

        return fileAppender;
    }

    public static void setTerminalLineReader(LineReader reader) {
        Logger logger = (Logger) LoggerFactory.getLogger("ROOT");
        var terminalAppender = (CustomTerminalAppender) logger.getAppender("terminal");
        terminalAppender.setLineReader(reader);
    }
}

