import org.junit.jupiter.api.Test;
import org.maibot.sdk.SNoGenerator;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SNoGeneratorTest {
    @Test
    public void generationTest1() {
        // 串行生成一系列序列号，确保它们是递增的
        SNoGenerator.SerialNo prev = SNoGenerator.nextSeq();
        for (int i = 0; i < 1000; i++) {
            SNoGenerator.SerialNo curr = SNoGenerator.nextSeq();
            assert curr.compareTo(prev) > 0 : "序列号未递增";
            prev = curr;
        }
    }

    @Test
    public void generationTest2() {
        // 并行生成一系列序列号，确保它们是唯一且递增的
        final int THREAD_COUNT = 10;
        final int IDS_PER_THREAD = 100000;
        Set<SNoGenerator.SerialNo> generatedIds = ConcurrentHashMap.newKeySet();

        Thread[] threads = new Thread[THREAD_COUNT];
        var startTime = System.currentTimeMillis();
        for (int t = 0; t < THREAD_COUNT; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < IDS_PER_THREAD; i++) {
                    SNoGenerator.SerialNo id = SNoGenerator.nextSeq();
                    if (!generatedIds.add(id)) {
                        throw new AssertionError("生成了重复的序列号: " + id);
                    }
                }
            });
            threads[t].start();
        }

        for (int t = 0; t < THREAD_COUNT; t++) {
            try {
                threads[t].join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        var endTime = System.currentTimeMillis();
        
        assert generatedIds.size() == THREAD_COUNT * IDS_PER_THREAD : "生成的序列号数量不正确";

        System.out.println("并行生成 " + (THREAD_COUNT * IDS_PER_THREAD) + " 个序列号耗时: " + (endTime - startTime) + " ms");
    }
}
