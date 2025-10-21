package org.maibot.core.thinking;

import lombok.Getter;
import lombok.Setter;
import org.maibot.core.cdi.annotation.Component;
import org.maibot.core.db.dao.InteractionStream;
import org.maibot.core.db.dao.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 思维流组件
 * <p>
 * 思维流是对交互流的思考与决策单元。<br>
 * 它负责观察交互流中的消息内容，并根据预设的规则和模型做出相应的反应和决策。
 * <p>
 */
@Component(singleton = false)
public class ThinkingFlow {
    public enum FlowState {
        SLEEPING(0),
        ACTIVE(1),
        FOCUSED(2);

        @Getter
        private final int code;

        FlowState(int code) {
            this.code = code;
        }

        boolean isAtLeast(FlowState other) {
            return this.code >= other.code;
        }
    }

    private final int OBSERVATION_WINDOW_SIZE;

    /// 交互流ID
    private final String id;
    /// 观察者
    private final FlowObserver flowObserver = new FlowObserver(this);

    /// 交互流状态
    @Getter
    private FlowState state = FlowState.SLEEPING;
    /// 上次活跃时间戳
    @Getter
    private long lastActiveTimestamp = System.currentTimeMillis();
    /// 交互流观察窗口
    private final Deque<Message> observationWindow = new ArrayDeque<>();

    protected ThinkingFlow(int max_observation_window_size, String id) {
        this.OBSERVATION_WINDOW_SIZE = max_observation_window_size;

        this.id = id;
    }

    public static String idGen(Long id, boolean isPrivate) {
        return InteractionStream.idGen(id, isPrivate);
    }

    public void observe() {
        this.flowObserver.run();
    }

    public void stopObserving() {
        this.flowObserver.stop();
    }

    public void setState(FlowState newState) {
        this.state = newState;
        this.flowObserver.onStateChange(newState);
    }

    public void addToObservationWindow(Message message) {
        this.observationWindow.addLast(message);
        // 限制观察窗口大小，例如最多保留最近100条消息
        while (this.observationWindow.size() > OBSERVATION_WINDOW_SIZE) {
            this.observationWindow.removeFirst();
        }
        this.lastActiveTimestamp = System.currentTimeMillis();
        if (!this.state.isAtLeast(FlowState.ACTIVE)) {
            this.setState(FlowState.ACTIVE);
        }
    }


    /**
     * 流观察器
     */
    public static class FlowObserver implements Runnable {
        private final Logger log = LoggerFactory.getLogger(FlowObserver.class);

        private final ThinkingFlow thinkingFlow;

        private final ReentrantLock lock = new ReentrantLock();
        private final Condition activityCondition = lock.newCondition();
        private volatile boolean running = true;

        /// 交互流处于激活状态下的观察间隔
        @Setter
        private int activeObservationIntervalSec = 20;
        /// 交互流处于专注状态下的观察间隔
        @Setter
        private int focusedObservationIntervalSec = 5;

        protected FlowObserver(ThinkingFlow flow) {
            this.thinkingFlow = flow;
        }

        public void onStateChange(FlowState newState) {
            if (newState.isAtLeast(FlowState.ACTIVE)) {
                this.signal();
            }
        }

        public void stop() {
            running = false;
            this.signal();
        }

        private void signal() {
            lock.lock();
            try {
                activityCondition.signal();
                notify();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void run() {
            while (running) {
                // 如果流处于休眠状态，等待激活信号
                try {
                    lock.lock();
                    while (thinkingFlow.getState() == FlowState.SLEEPING && running) {
                        activityCondition.await();
                    }
                    if (!running) break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    lock.unlock();
                }

                // 观察交互流内容并做出决策
                // TODO: 实现观察逻辑

                try {
                    lock.lock();
                    var flowState = thinkingFlow.getState();
                    switch (flowState) {
                        case ACTIVE -> wait(activeObservationIntervalSec * 1000L);
                        case FOCUSED -> wait(focusedObservationIntervalSec * 1000L);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.unlock();
                }
            }
        }
    }
}
