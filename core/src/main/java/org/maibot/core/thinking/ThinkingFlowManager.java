package org.maibot.core.thinking;

import jakarta.persistence.criteria.CriteriaBuilder;
import org.maibot.core.cdi.Instance;
import org.maibot.core.db.DatabaseService;
import org.maibot.core.db.dao.InteractionEntity;
import org.maibot.core.db.dao.InteractionGroup;
import org.maibot.core.db.dao.InteractionStream;
import org.maibot.core.cdi.annotation.AutoInject;
import org.maibot.core.cdi.annotation.Component;
import org.maibot.core.util.TaskExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 交互流管理器
 * <p>
 * 我们定义对于某个Adapter的某个Group为一个交互流。
 * 对于一对一交流来说，也可以将其视为一个成员数量为2的交互流。
 * <p>
 * 每个交互流都有独立的Observation组件用于观察流内容并做出决策。
 * <p>
 * 对于交互流来说，其生命周期存在以下状态：<br>
 * 1. 创建：当有新的交互流被创建时，进入创建状态；<br>
 * 2. 活跃：当交互流中有成员在进行交流时，该流进入活跃状态；<br>
 * 3. 专注：当bot认为交互流中有值得关注的内容时，该流进入专注状态；<br>
 * 4. 休眠：当交互流中长时间没有活跃交流时，该流进入休眠状态；<br>
 */
@Component
public class ThinkingFlowManager {
    private static final Logger log = LoggerFactory.getLogger(ThinkingFlowManager.class);

    /* 单例资源区 */
    private final DatabaseService databaseService;
    private final TaskExecutorService taskExecutorService;

    /* 运行资源区 */
    /// 当前所有交互流
    private final Map<String, ThinkingFlow> thinkingFlows = new ConcurrentHashMap<>();

    @AutoInject
    private ThinkingFlowManager(DatabaseService databaseService, TaskExecutorService taskExecutorService) {
        this.databaseService = databaseService;
        this.taskExecutorService = taskExecutorService;
    }

    /**
     * 初始化思维流管理器
     */
    public void initialize() {
        this.restoreFromDb();
        this.runInteractionFlowObservers();
    }

    /**
     * 关闭思维流管理器
     */
    public void shutdown() {
        for (var flow : this.thinkingFlows.values()) {
            flow.stopObserving();
        }
        this.saveToDb();
    }

    /**
     * 保存当前状态到数据库
     */
    private void saveToDb() {
        databaseService.exec(em -> {
            for (var flow : this.thinkingFlows.values()) {
                // TODO: 将Flow同步到数据库
            }
        });
    }

    /**
     * 从数据库恢复状态
     */
    private void restoreFromDb() {
        try {
            databaseService.exec(em -> {
                CriteriaBuilder cb = em.getCriteriaBuilder();
                var cq = cb.createQuery(InteractionStream.class);
                var root = cq.from(InteractionStream.class);
                cq.select(root);
                var query = em.createQuery(cq);
                List<InteractionStream> streams = query.getResultList();

                for (var stream : streams) {
                    String streamId = stream.getId();
                    var flow = Instance.get(ThinkingFlowFactory.class)
                            .setFlowId(streamId)
                            .build();
                    thinkingFlows.put(streamId, flow);
                }

                // TODO: 从数据库取聊天消息填充观察窗口
            });
        } catch (Exception e) {
            log.error("Failed to restore interaction streams from database.", e);
        }
    }

    /**
     * 运行所有交互流的观察者
     */
    private void runInteractionFlowObservers() {
        for (var flow : this.thinkingFlows.values()) {
            if (flow.getState().isAtLeast(ThinkingFlow.FlowState.ACTIVE))
                this.taskExecutorService.submit(flow::observe, true);
        }
    }

    /**
     * 通过用户ID获取或创建一个思维流
     *
     * @param streamId 流ID
     * @return 交互流实例
     */
    public ThinkingFlow getOrCreateInteractionFlow(String streamId) {
        return thinkingFlows.computeIfAbsent(streamId, k -> {
            String[] parts = streamId.split("-");

            // 持久化
            databaseService.exec(em -> {
                InteractionStream stream = new InteractionStream();
                stream.setId(streamId);

                if (parts[0].equals("P")) {
                    // 私聊流
                    Long userId = Long.parseLong(parts[1]);
                    // 在数据库中查询对应的私聊对象是否存在
                    var res = em.find(InteractionEntity.class, userId);
                    if (res == null) {
                        throw new RuntimeException("InteractionEntity with ID " + userId + " does not exist. This shouldn't happen.");
                    }
                    stream.setEntity(res);
                } else if (parts[0].equals("G")) {
                    // 群聊流
                    Long groupId = Long.parseLong(parts[1]);
                    // 在数据库中查询对应的群聊对象是否存在
                    var res = em.find(InteractionGroup.class, groupId);
                    if (res == null) {
                        throw new RuntimeException("InteractionGroup with ID " + groupId + " does not exist. This shouldn't happen.");
                    }
                    stream.setGroup(res);
                }

                em.persist(stream);
            });

            return Instance.get(ThinkingFlowFactory.class)
                    .setFlowId(streamId)
                    .build();
        });
    }

    public int[] getFlowStatesCount() {
        int activeCount = 0;
        int focusedCount = 0;
        for (var flow : this.thinkingFlows.values()) {
            if (flow.getState() == ThinkingFlow.FlowState.ACTIVE) {
                activeCount++;
            } else if (flow.getState() == ThinkingFlow.FlowState.FOCUSED) {
                focusedCount++;
            }
        }
        int totalCount = this.thinkingFlows.size();
        int sleepingCount = totalCount - activeCount - focusedCount;
        return new int[]{totalCount, sleepingCount, activeCount, focusedCount};
    }
}
