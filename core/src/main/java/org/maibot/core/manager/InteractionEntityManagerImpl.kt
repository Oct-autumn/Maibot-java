package org.maibot.core.manager;

import jakarta.persistence.EntityManager;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.manager.InteractionEntityManager;
import org.maibot.sdk.storage.db.dao.InteractionEntity;
import org.maibot.sdk.storage.db.dao.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

import static java.util.Objects.requireNonNullElseGet;

@Component
public class InteractionEntityManagerImpl implements InteractionEntityManager {
    private static final Logger log = LoggerFactory.getLogger(InteractionEntityManagerImpl.class);

    // 锁对象映射，用于防止 交互实体 的重复创建
    private final ConcurrentMap<String, CompletableFuture<InteractionEntity>> lockMap = new ConcurrentHashMap<>();

    private String generateKey(String platform, String platformUserId) {
        return platform + ":" + platformUserId;
    }

    @Override
    public InteractionEntity getOrCreatIfAbsent(
      EntityManager em,
      String platform,
      String platformUserId,
      String nickname
    ) {
        var key = generateKey(platform, platformUserId);
        // 使用锁对象，防止重复查询和创建
        var lockObject = new CompletableFuture<InteractionEntity>();
        var mappedLock = lockMap.putIfAbsent(key, lockObject);

        if (mappedLock == null) {
            // 当前线程获得锁，执行获取或创建逻辑
            try {
                // 检查实体是否存在，防止重复创建
                InteractionEntity entity = requireNonNullElseGet(
                  this.get(em, platform, platformUserId),
                  () -> {
                      // 不存在则创建新实体
                      // 先创建Person实体
                      Person person = new Person();
                      person.setName(nickname);
                      em.persist(person);
                      // 再创建InteractionEntity实体
                      var newEntity = new InteractionEntity();
                      newEntity.setPlatform(platform);
                      newEntity.setPlatformUserId(platformUserId);
                      newEntity.setPerson(person);
                      newEntity.setNickname(nickname);
                      em.persist(newEntity);

                      return newEntity;
                  }
                );

                // 完成锁对象，通知等待的线程
                lockObject.complete(entity);
                return entity;
            } catch (Exception e) {
                log.warn(
                  "An exception occurred while creating InteractionEntity for platform: {}, platformUserId: {}",
                  platform,
                  platformUserId,
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
                var entity = mappedLock.get();
                em.merge(entity);   // 将entity注册到本地EntityManager上下文中
                return entity;
            } catch (CancellationException | ExecutionException ignored) {
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    @Override
    public InteractionEntity get(EntityManager em, String platform, String platformUserId) {
        // 直接查询数据库
        try {
            var query = em.createQuery(
              "SELECT e FROM InteractionEntity e WHERE e.platform = :platform AND e.platformUserId = :platformUserId",
              InteractionEntity.class
            );
            query.setParameter("platform", platform);
            query.setParameter("platformUserId", platformUserId);
            var resultList = query.getResultList();

            InteractionEntity entity = null;
            if (!resultList.isEmpty()) {
                entity = resultList.getFirst();
            }

            return entity;
        } catch (Exception e) {
            log.warn(
              "An exception occurred while retrieving InteractionEntity for platform: {}, platformUserId: {}",
              platform,
              platformUserId,
              e
            );
            return null;
        }
    }
}
