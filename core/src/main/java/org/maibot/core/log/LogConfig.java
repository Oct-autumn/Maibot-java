package org.maibot.core.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.spi.FilterReply;
import lombok.Setter;
import org.maibot.core.config.MainConfig;
import org.maibot.core.util.PrefixTreeMap;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.util.*;

public class LogConfig {
    private static final Set<String> AVAL_LEVELS = new HashSet<>(Arrays.asList("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF"));

    public static void configure(MainConfig.Log conf) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger("ROOT");
        rootLogger.setLevel(Level.TRACE); // 将日志级别设置为 TRACE，以便过滤器可以处理所有级别的日志
        rootLogger.detachAndStopAllAppenders(); // 清除现有的 appender

        rootLogger.addAppender(getConsoleAppender(context, conf.console));

        if (AVAL_LEVELS.contains(conf.file.level.toUpperCase()) && !conf.file.level.equalsIgnoreCase("OFF")) {
            // 确保日志目录存在
            java.io.File logDir = new java.io.File(conf.file.path);
            if (!logDir.exists() && logDir.mkdirs()) {
                System.out.println("创建日志目录: " + logDir.getAbsolutePath());
            }
            rootLogger.addAppender(getFileAppender(context, conf.file));
        }
    }

    private static ConsoleAppender<ILoggingEvent> getConsoleAppender(LoggerContext context, MainConfig.Log.ConsoleLogSettings conf) {
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setName("console");
        consoleAppender.setContext(context);

        {
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss} [%thread] " +
                    "%highlight(%-5level){Faint, TRACE=gray, DEBUG=green, INFO=blue, WARN=yellow, ERROR=red} " +
                    "%logger{36} - " +
                    "%highlight(%msg){Faint, TRACE=lightgray, DEBUG=green, INFO=blue, WARN=yellow, ERROR=red}" +
                    "%n");
            encoder.start();
            consoleAppender.setEncoder(encoder);
        }

        {
            CustomFilter consoleFilter = new CustomFilter();
            consoleFilter.setDefaultLevel(Level.valueOf(conf.level.toUpperCase()));
            if (!conf.filterRule.isEmpty()) {
                // 添加包过滤器
                for (String pkg : conf.filterRule) {
                    applyRule(consoleFilter, pkg);
                }
            }
            consoleFilter.start();
            consoleAppender.addFilter(consoleFilter);
        }

        consoleAppender.start();

        return consoleAppender;
    }

    private static FileAppender<ILoggingEvent> getFileAppender(LoggerContext context, MainConfig.Log.FileLogSettings conf) {
        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setName("file");
        fileAppender.setContext(context);
        fileAppender.setFile(conf.path + "/maibot.log");

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

        {
            CustomFilter fileFilter = new CustomFilter();
            fileFilter.setDefaultLevel(Level.valueOf(conf.level.toUpperCase()));
            if (!conf.filterRule.isEmpty()) {
                // 添加包过滤器
                for (String pkg : conf.filterRule) {
                    applyRule(fileFilter, pkg);
                }
            }
            fileFilter.start();
            fileAppender.addFilter(fileFilter);
        }

        fileAppender.start();

        return fileAppender;
    }

    private static void applyRule(CustomFilter fileFilter, String rule) {
        String[] parts = rule.split(":");
        String packageName = parts[0];
        String level = parts.length > 1 ? parts[1].toUpperCase() : "OFF";

        if (!AVAL_LEVELS.contains(level)) {
            // 无效的日志级别，跳过
            return;
        }

        fileFilter.addRule(packageName, Level.valueOf(level));
    }

    public static void redirectConsoleOutputStream(OutputStream os) {
        var appender = ((LoggerContext) LoggerFactory.getILoggerFactory())
                .getLogger("ROOT")
                .getAppender("console");
        if (appender instanceof ConsoleAppender<ILoggingEvent> consoleAppender) {
            consoleAppender.setOutputStream(os);
        }
    }
}

class CustomFilter extends ch.qos.logback.core.filter.Filter<ILoggingEvent> {
    private final PrefixTreeMap<String, Level> rules;

    @Setter
    private Level defaultLevel = Level.INFO;

    public CustomFilter() {
        this.rules = new PrefixTreeMap<>();
    }

    public void addRule(String packageName, Level level) {
        var packageSplit = packageName.split("\\.");
        rules.insert(Arrays.stream(packageSplit).toList(), level);
    }

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String loggerName = event.getLoggerName();
        var nameSplit = loggerName.split("\\.");
        var ruleLevel = rules.search(Arrays.stream(nameSplit).toList());
        Level eventLevel = event.getLevel();
        if (ruleLevel != null) {
            if (eventLevel.isGreaterOrEqual(ruleLevel)) {
                return FilterReply.NEUTRAL; // 允许通过
            } else {
                return FilterReply.DENY; // 拒绝
            }
        } else {
            // 没有匹配的规则，使用默认级别
            if (eventLevel.isGreaterOrEqual(defaultLevel)) {
                return FilterReply.NEUTRAL; // 允许通过
            } else {
                return FilterReply.DENY; // 拒绝
            }
        }
    }
}
