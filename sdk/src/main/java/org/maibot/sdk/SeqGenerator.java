package org.maibot.sdk;

import lombok.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 线程安全的全局序列号生成器（可用于时间排序）
 * <p>
 * 原理：将序列号分为两部分：时间戳部分和自增部分。时间戳部分使用当前时间的毫秒数，自增部分在同一毫秒内递增。
 *
 * @author OctAutumn
 */
public class SeqGenerator {
    /// 上一个序列号的时间戳部分
    private static final AtomicLong    lastTimestamp = new AtomicLong(Instant.now().getEpochSecond());
    /// 当前自增计数器（同一时间戳内递增）
    ///
    /// 由于系统重启一定会产生可观测的时间流逝，递增计数器一定会被重置。
    /// 因此不需要持久化存储递增计数器的值
    private static final AtomicInteger currentSeq    = new AtomicInteger(0);

    /**
     * 获取下一个序列号
     *
     * @return 下一个序列号
     */
    public static Sequence nextSeq() {
        // 获取当前时间戳（秒）
        // 比较，若与上次发放的是同一时间戳，则自增计数器加一；
        // - 若发生时间流逝，则重置自增计数器为0
        // - 若发生时钟回拨，则阻塞直到时间超过为止
        // - 若自增计数器溢出，则阻塞直到时间流逝为止
        AtomicReference<Sequence> seq = new AtomicReference<>();
        lastTimestamp.updateAndGet(prev -> {
            AtomicLong now = new AtomicLong(Instant.now().getEpochSecond());
            if (now.get() < prev) {
                // 时钟回拨，阻塞直到时间超过为止
                do {
                    try {
                        Thread.sleep(Duration.ofMillis(100));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    now.set(Instant.now().getEpochSecond());
                } while (now.get() <= prev);
            }

            if (now.get() == prev) {
                // 同一时间戳内，递增计数器
                int counter = currentSeq.getAndUpdate(curr -> {
                    if (curr == 0xFFFF) {
                        // 自增计数器溢出，阻塞直到时间流逝为止
                        long newNow;
                        do {
                            try {
                                Thread.sleep(Duration.ofMillis(100));
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                            newNow = Instant.now().getEpochSecond();
                        } while (newNow <= prev);
                        now.set(newNow);
                        return 0;
                    }
                    return curr + 1;
                });
                seq.set(new Sequence(now.get(), (short) counter));
                return prev;
            } else {
                // 时间流逝，重置自增计数器
                currentSeq.set(0);
                seq.set(new Sequence(now.get(), (short) 0));
                return now.get();
            }
        });

        return seq.get();
    }

    /**
     * 全局序列号，包含时间戳部分和自增部分
     * <p>
     * 前48位为时间戳部分（秒），后16位为自增部分（同一秒内递增）
     *
     * @param seqNumber 完整序列号
     */
    public record Sequence(
      long seqNumber
    ) implements Comparable<Sequence> {
        public Sequence(long timestamp, short counter) {
            this((timestamp << 16) | (counter & 0xFFFF));
        }

        @Override
        public int compareTo(Sequence o) {
            return Long.compare(this.seqNumber, o.seqNumber);
        }

        @Override
        @NonNull
        public String toString() {
            return String.format("Sequence[time=%d, counter=%d]", timestamp(), counter());
        }

        public long timestamp() {
            return seqNumber >>> 16;
        }

        public short counter() {
            return (short) (seqNumber & 0xFFFF);
        }
    }
}
