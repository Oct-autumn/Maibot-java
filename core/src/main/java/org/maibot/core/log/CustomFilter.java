package org.maibot.core.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import lombok.Setter;
import org.maibot.core.util.PrefixTreeMap;

import java.util.Arrays;

public class CustomFilter extends ch.qos.logback.core.filter.Filter<ILoggingEvent> {
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
