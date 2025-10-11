package org.maibot.core.thinking;

import jakarta.persistence.criteria.CriteriaBuilder;
import org.maibot.core.db.DatabaseService;
import org.maibot.core.db.dao.InteractionStream;
import org.maibot.core.cdi.annotation.AutoInject;
import org.maibot.core.cdi.annotation.Component;

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
    private final Map<Long, ThinkingFlow> thinkingFlows = new ConcurrentHashMap<>();

    private final DatabaseService databaseService;

    private int activeFlowCount = 0;
    private int focusedFlowCount = 0;
    private int sleepingFlowCount = 0;

    @AutoInject
    private ThinkingFlowManager(DatabaseService databaseService) {
        this.databaseService = databaseService;
        restoreFromDb();
    }

    /**
     * 保存当前状态到数据库
     */
    private void saveToDb() {

    }

    /**
     * 从数据库恢复状态
     */
    private void restoreFromDb() {
        List<InteractionStream> interactionStreams = databaseService.exec(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            var cq = cb.createQuery(InteractionStream.class);
            var root = cq.from(InteractionStream.class);
            cq.select(root);
            var query = em.createQuery(cq);
            return query.getResultList();
        });
    }

    public void changeFlowState(Long streamId, ThinkingFlow.ThinkingState newState) {
        var flow = thinkingFlows.get(streamId);
        if (flow == null) {
            return;
        }
        flow.setState(newState);
    }

    /**
     * 获取或创建一个思维流
     *
     * @param streamId 流ID
     * @return 交互流实例
     */
    public ThinkingFlow getOrCreateInteractionFlow(Long streamId) {
        return thinkingFlows.computeIfAbsent(streamId, k -> {
            var newTf = new ThinkingFlow(k);
            // TODO: 数据库持久化
            return newTf;
        });
    }
}
