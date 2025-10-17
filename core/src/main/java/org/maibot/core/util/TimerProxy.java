package org.maibot.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimerProxy {
    public static void start(Runnable task, String format, Logger log) {
        long startTime = System.currentTimeMillis();
        task.run();
        long endTime = System.currentTimeMillis();
        log.debug(format, (endTime - startTime));
    }

    public static void start(Runnable task, String format) {
        start(task, format, LoggerFactory.getLogger("Timer"));
    }

    public static void start(Runnable task) {
        start(task, "Task executed in {} ms");
    }
}
