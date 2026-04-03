package org.maibot.sdk;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程安全的全局序列号生成器（可用于时间排序）
 * <p>
 * 原理：将序列号分为两部分：时间戳部分和自增部分。时间戳部分使用当前时间的毫秒数，自增部分在同一毫秒内递增。
 *
 * @author OctAutumn
 */
public class SNoGenerator {
    /// 计数器状态，前48位为时间戳（毫秒），后16位为自增计数器
    ///
    /// 由于系统重启一定会产生可观测的时间流逝，递增计数器一定会被重置。
    /// 因此不需要持久化存储计数器的值
    private static final AtomicLong STATE = new AtomicLong(initialState());

    private static long initialState() {
        long ts = System.currentTimeMillis();
        return (ts << 16);
    }

    /**
     * 获取下一个序列号
     *
     * @return 下一个序列号
     */
    public static SerialNo nextSeq() {
        // 获取当前时间戳（秒）
        // 比较，若与上次发放的是同一时间戳，则自增计数器加一；
        // - 若发生时间流逝，则重置自增计数器为0，并更新时间戳；
        // - 若发生时钟回拨，则阻塞直到时间超过为止，然后继续发放序列号
        // - 若自增计数器溢出，则阻塞直到时间流逝为止，然后重置自增计数器为0
        // 返回由时间戳和自增计数组成的序列号
        while (true) {
            long s = STATE.get();
            long ts = s >>> 16;
            int cnt = (int) (s & 0xFFFFL);
            long now = System.currentTimeMillis();

            if (now < ts) {
                // 时钟回拨，短暂等待直到时间 > ts（在 CAS 之外等待）
                try {
                    Thread.sleep(Duration.ofMillis(10));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                continue;
            }

            if (now == ts) {
                if (cnt == 0xFFFF) {
                    // 溢出：等待到下一毫秒后尝试把计数器重置为0
                    do {
                        try {
                            Thread.sleep(Duration.ofMillis(10));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        now = System.currentTimeMillis();
                    } while (now <= ts);

                    long newState = (now << 16);
                    if (STATE.compareAndSet(s, newState)) {
                        return new SerialNo(now, (short) 0);
                    }
                } else {
                    long newCnt = (cnt + 1) & 0xFFFFL;
                    long newState = (ts << 16) | newCnt;
                    if (STATE.compareAndSet(s, newState)) {
                        return new SerialNo(ts, (short) newCnt);
                    }
                }
            } else { // now > ts
                long newState = (now << 16);
                if (STATE.compareAndSet(s, newState)) {
                    return new SerialNo(now, (short) 0);
                }
            }
        }
    }

    public static SerialNo from(long sNo) {
        return new SerialNo(sNo);
    }

    /**
     * 全局序列号，包含时间戳部分和自增部分
     * <p>
     * 前48位为时间戳部分（秒），后16位为自增部分（同一秒内递增）
     *
     * @param sNo 完整序列号
     */
    public record SerialNo(
      long sNo
    ) implements Comparable<SerialNo> {
        public SerialNo(long timestamp, short counter) {
            this((timestamp << 16) | (counter & 0xFFFF));
        }

        @Override
        public int compareTo(SerialNo o) {
            return Long.compare(this.sNo, o.sNo);
        }

        @Override
        @NotNull
        public String toString() {
            return String.format("Sequence[sNo=%s]", this.toHexString());
        }

        public String toHexString() {
            return String.format("%016X", sNo);
        }

        public long timestamp() {
            return sNo >>> 16;
        }

        public short counter() {
            return (short) (sNo & 0xFFFF);
        }
    }
}
