package org.maibot.sdk

import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * 线程安全的全局序列号生成器（可用于时间排序）
 * 
 * 
 * 原理：将序列号分为两部分：时间戳部分和自增部分。时间戳部分使用当前时间的毫秒数，自增部分在同一毫秒内递增。
 * 
 * @author OctAutumn
 */
object SNoGenerator {
    /** 计数器状态，前48位为时间戳（毫秒），后16位为自增计数器
     * 
     * 由于系统重启一定会产生可观测的时间流逝，递增计数器一定会被重置。
     * 因此不需要持久化存储计数器的值 */
    private val STATE = AtomicLong(initialState())

    private fun initialState(): Long {
        val ts = System.currentTimeMillis()
        return (ts shl 16)
    }

    /**
     * 获取下一个序列号
     *
     * @return 下一个序列号
     */
    @JvmStatic
    fun nextSeq(): SerialNo {
        // 获取当前时间戳（秒）
        // 比较，若与上次发放的是同一时间戳，则自增计数器加一；
        // - 若发生时间流逝，则重置自增计数器为0，并更新时间戳；
        // - 若发生时钟回拨，则阻塞直到时间超过为止，然后继续发放序列号
        // - 若自增计数器溢出，则阻塞直到时间流逝为止，然后重置自增计数器为0
        // 返回由时间戳和自增计数组成的序列号
        while (true) {
            val s = STATE.get()
            val ts = s ushr 16
            val cnt = (s and 0xFFFFL).toInt()
            var now = System.currentTimeMillis()

            if (now < ts) {
                // 时钟回拨，短暂等待直到时间 > ts（在 CAS 之外等待）
                try {
                    Thread.sleep(Duration.ofMillis(10))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                continue
            }

            if (now == ts) {
                if (cnt == 0xFFFF) {
                    // 溢出：等待到下一毫秒后尝试把计数器重置为0
                    do {
                        try {
                            Thread.sleep(Duration.ofMillis(10))
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                        now = System.currentTimeMillis()
                    } while (now <= ts)

                    val newState = (now shl 16)
                    if (STATE.compareAndSet(s, newState)) {
                        return SerialNo(now, 0.toShort())
                    }
                } else {
                    val newCnt = (cnt + 1).toLong() and 0xFFFFL
                    val newState = (ts shl 16) or newCnt
                    if (STATE.compareAndSet(s, newState)) {
                        return SerialNo(ts, newCnt.toShort())
                    }
                }
            } else { // now > ts
                val newState = (now shl 16)
                if (STATE.compareAndSet(s, newState)) {
                    return SerialNo(now, 0.toShort())
                }
            }
        }
    }

    @JvmStatic
    fun from(sNo: Long): SerialNo {
        return SerialNo(sNo)
    }

    /**
     * 全局序列号，包含时间戳部分和自增部分
     *
     *
     * 前48位为时间戳部分（秒），后16位为自增部分（同一秒内递增）
     *
     * @param sNo 完整序列号
     */
    data class SerialNo(
        val sNo: Long
    ) : Comparable<SerialNo> {
        val timestamp: Long
            get() = sNo ushr 16

        val counter: Short
            get() = (sNo and 0xFFFFL).toShort()

        constructor(timestamp: Long, counter: Short) : this((timestamp shl 16) or (counter.toInt() and 0xFFFF).toLong())

        override fun compareTo(other: SerialNo): Int {
            return this.sNo.compareTo(other.sNo)
        }

        override fun toString(): String {
            return String.format("Sequence[sNo=%s]", this.toHexString())
        }

        fun toHexString(): String {
            return String.format("%016X", sNo)
        }
    }
}
