package org.maibot.core.manager;

import jakarta.persistence.EntityManager;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.manager.GroupMemberManager;
import org.maibot.sdk.storage.db.dao.GroupMember;
import org.maibot.sdk.storage.db.dao.InteractionEntity;
import org.maibot.sdk.storage.db.dao.InteractionGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

import static java.util.Objects.requireNonNullElseGet;

@Component
public class GroupMemberManagerImpl implements GroupMemberManager {
    private static final Logger log = LoggerFactory.getLogger(GroupMemberManagerImpl.class);

    // 锁对象映射，用于防止 交互实体 的重复创建
    private final ConcurrentMap<String, CompletableFuture<GroupMember>> lockMap = new ConcurrentHashMap<>();

    private String generateKey(Long groupId, Long userId) {
        return groupId + ":" + userId;
    }

    @Override
    public GroupMember getOrCreatIfAbsent(
      EntityManager em,
      InteractionGroup group,
      InteractionEntity entity,
      String cardName
    ) {
        var key = generateKey(group.getId(), entity.getId());
        // 使用锁对象，防止重复查询和创建
        var lockObject = new CompletableFuture<GroupMember>();
        var mappedLock = lockMap.putIfAbsent(key, lockObject);

        if (mappedLock == null) {
            // 当前线程获得锁，执行获取或创建逻辑
            try {
                // 检查实体是否存在，防止重复创建
                GroupMember groupMember;
                groupMember = requireNonNullElseGet(
                  this.get(em, group, entity),
                  () -> {
                      var newGroupMember = new GroupMember();
                      newGroupMember.setGroup(group);
                      newGroupMember.setEntity(entity);
                      newGroupMember.setCardName(cardName);
                      em.persist(newGroupMember);
                      return newGroupMember;
                  }
                );

                // 完成锁对象，通知等待的线程
                lockObject.complete(groupMember);
                return groupMember;
            } catch (Exception e) {
                log.warn(
                  "获取或创建 GroupMember (groupId:{}, entityId:{}) 时发生异常",
                  group.getId(),
                  entity.getId(),
                  e
                );
                // 出现异常，取消锁对象，通知等待的线程
                lockObject.cancel(false);
                return null;
            } finally {
                // 移除锁对象
                lockMap.remove(key);
            }
        } else {
            // 其他线程等待结果
            try {
                var groupMember = mappedLock.get();
                em.merge(groupMember);   // 将entity注册到本地EntityManager上下文中
                return groupMember;
            } catch (CancellationException | ExecutionException ignored) {
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    @Override
    public GroupMember get(EntityManager em, InteractionGroup group, InteractionEntity entity) {
        // 直接查询数据库
        try {
            var query = em.createQuery(
              "SELECT e FROM GroupMember e WHERE e.id = :groupMemberId",
              GroupMember.class
            );
            query.setParameter("groupMemberId", new GroupMember.GroupMemberId(group.getId(), entity.getId()));

            var resultList = query.getResultList();
            if (resultList.isEmpty()) {
                return null;
            } else {
                return resultList.getFirst();
            }
        } catch (Exception e) {
            log.warn(
              "获取 GroupMember (groupId:{}, entityId:{}) 时发生异常",
              group.getId(),
              entity.getId(),
              e
            );
            return null;
        }
    }
}
