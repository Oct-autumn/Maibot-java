package org.maibot.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public class TimerProxy {
    public static void start(Runnable task, String format, Logger log) {
        long startTime = System.currentTimeMillis();
        task.run();
        long endTime = System.currentTimeMillis();
        log.debug(format, (endTime - startTime));
    }

    public static void start(Runnable task, String format, String loggerName) {
        start(task, format, LoggerFactory.getLogger(loggerName));
    }

    public static void start(Runnable task, String format) {
        start(task, format, LoggerFactory.getLogger("Timer"));
    }

    public static void start(Runnable task) {
        start(task, "Task executed in {} ms", LoggerFactory.getLogger("Timer"));
    }

    public static <T> T start(Supplier<T> task, String format, Logger log) {
        long startTime = System.currentTimeMillis();
        var ret = task.get();
        long endTime = System.currentTimeMillis();
        log.debug(format, (endTime - startTime));
        return ret;
    }

    public static <T> T start(Supplier<T> task, String format, String loggerName) {
        return start(task, format, LoggerFactory.getLogger(loggerName));
    }

    public static <T> T start(Supplier<T> task, String format) {
        return start(task, format, LoggerFactory.getLogger("Timer"));
    }

    public static <T> T start(Supplier<T> task) {
        return start(task, "Task executed in {} ms", LoggerFactory.getLogger("Timer"));
    }
}
