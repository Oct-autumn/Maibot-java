package org.maibot.core.log

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import org.maibot.core.util.PrefixTreeMap
import java.util.*

class CustomFilter : Filter<ILoggingEvent>() {
    private val rules = PrefixTreeMap<String, Level>()

    var defaultLevel: Level = Level.INFO

    fun addRule(packageName: String, level: Level) {
        val packageSplit: Array<String> =
            packageName.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        rules.insert(Arrays.stream(packageSplit).toList(), level)
    }

    override fun decide(event: ILoggingEvent): FilterReply {
        val loggerNameSplit = event.loggerName.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }
        val ruleLevel = rules.search(loggerNameSplit)
        return if (event.level.isGreaterOrEqual(ruleLevel ?: defaultLevel)) {
            FilterReply.NEUTRAL // 允许通过
        } else {
            FilterReply.DENY // 拒绝
        }
    }
}
