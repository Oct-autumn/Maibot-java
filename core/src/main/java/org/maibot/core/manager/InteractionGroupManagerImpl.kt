package org.maibot.core.manager;

import jakarta.persistence.EntityManager;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.manager.InteractionGroupManager;
import org.maibot.sdk.storage.db.dao.InteractionGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

import static java.util.Objects.requireNonNullElseGet;

@Component
public class InteractionGroupManagerImpl implements InteractionGroupManager {
    private static final Logger log = LoggerFactory.getLogger(InteractionGroupManagerImpl.class);

    // 锁对象映射，用于防止 交互实体 的重复创建
    private final ConcurrentMap<String, CompletableFuture<InteractionGroup>> lockMap = new ConcurrentHashMap<>();

    private String generateKey(String platform, String platformGroupId) {
        return platform + ":" + platformGroupId;
    }

    @Override
    public InteractionGroup getOrCreatIfAbsent(
      EntityManager em,
      String platform,
      String platformGroupId,
      String groupName
    ) {
        var key = generateKey(platform, platformGroupId);
        // 使用锁对象，防止重复查询和创建
        var lockObject = new CompletableFuture<InteractionGroup>();
        var mappedLock = lockMap.putIfAbsent(key, lockObject);

        if (mappedLock == null) {
            // 当前线程获得锁，执行获取或创建逻辑
            try {
                // 检查实体是否存在
                InteractionGroup group = requireNonNullElseGet(
                  this.get(em, platform, platformGroupId),
                  () -> {
                      // 不存在则创建新实体
                      var newGroup = new InteractionGroup();
                      newGroup.setPlatform(platform);
                      newGroup.setPlatformGroupId(platformGroupId);
                      newGroup.setGroupName(groupName);
                      em.persist(newGroup);
                      return newGroup;
                  }
                );

                // 完成锁对象，通知等待的线程
                lockObject.complete(group);
                return group;
            } catch (Exception e) {
                log.warn(
                  "An exception occurred while creating InteractionGroup for platform: {}, platformGroupId: {}",
                  platform,
                  platformGroupId,
                  e
                );
                lockObject.cancel(false);
                throw e;
            } finally {
                lockMap.remove(key);
            }
        } else {
            // 其他线程等待锁完成
            try {
                var group = mappedLock.get();
                em.merge(group); // 将实体附加到当前 EntityManager 上
                return group;
            } catch (CancellationException | ExecutionException ignored) {
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    @Override
    public InteractionGroup get(EntityManager em, String platform, String platformGroupId) {
        // 直接查询数据库
        try {
            var query = em.createQuery(
              "SELECT g FROM InteractionGroup g WHERE g.platform = :platform AND g.platformGroupId = :platformGroupId",
              InteractionGroup.class
            );
            query.setParameter("platform", platform);
            query.setParameter("platformGroupId", platformGroupId);
            var resultList = query.getResultList();

            if (resultList.isEmpty()) {
                return null;
            } else {
                return resultList.getFirst();
            }
        } catch (Exception e) {
            log.warn(
              "An exception occurred while retrieving InteractionGroup for platform: {}, platformGroupId: {}",
              platform,
              platformGroupId,
              e
            );
            throw e;
        }
    }
}
