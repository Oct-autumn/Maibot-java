package org.maibot.core.manager;

import jakarta.persistence.EntityManager;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.storage.db.dao.InteractionEntity;
import org.maibot.sdk.storage.db.dao.InteractionGroup;
import org.maibot.sdk.storage.db.dao.InteractionStream;
import org.maibot.sdk.storage.domain.StreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

@Component
public class InteractionStreamManagerImpl implements org.maibot.sdk.manager.InteractionStreamManager {
    private static final Logger log = LoggerFactory.getLogger(InteractionStreamManagerImpl.class);

    private final ConcurrentMap<String, CompletableFuture<InteractionStream>> lockMap = new ConcurrentHashMap<>();

    private String generateKey(StreamType type, Long entityId, Long groupId) {
        return type.name()
          + ":"
          + (entityId == null ? "" : entityId)
          + ":"
          + (groupId == null ? "" : groupId);
    }

    @Override
    public InteractionStream getOrCreateIfAbsent(
      EntityManager em,
      StreamType type,
      InteractionEntity interactionEntity,
      InteractionGroup interactionGroup
    ) {
        String key = switch (type) {
            case PRIVATE -> {
                if (interactionEntity == null) {
                    throw new IllegalArgumentException("InteractionEntity cannot be null for PRIVATE stream type.");
                }
                yield generateKey(
                  type,
                  interactionEntity.getId(),
                  null
                );
            }
            case GROUP -> {
                if (interactionGroup == null) {
                    throw new IllegalArgumentException("InteractionGroup cannot be null for GROUP stream types.");
                }
                yield generateKey(
                  type,
                  null,
                  interactionGroup.getId()
                );
            }
            default -> throw new IllegalArgumentException("Unsupported StreamType: " + type);
        };
        // 使用锁对象，防止重复查询和创建
        var lockObject = new CompletableFuture<InteractionStream>();
        var mappedLock = lockMap.putIfAbsent(key, lockObject);

        if (mappedLock == null) {
            // 当前线程获得锁，执行获取或创建逻辑
            try {
                InteractionStream stream;
                switch (type) {
                    case PRIVATE -> {
                        // 检查实体是否存在
                        var query = em.createQuery(
                          "SELECT s FROM InteractionStream s WHERE s.type = :type AND s.entity.id = :entityId",
                          InteractionStream.class
                        );
                        query.setParameter("type", StreamType.PRIVATE);
                        query.setParameter("entityId", interactionEntity.getId());
                        var results = query.getResultList();

                        if (results.isEmpty()) {
                            // 不存在则创建新实体
                            stream = new InteractionStream();
                            stream.setId(InteractionStream.idGen(StreamType.PRIVATE, interactionEntity.getId()));
                            stream.setType(StreamType.PRIVATE);
                            stream.setEntity(interactionEntity);
                            em.persist(stream);
                        } else {
                            // 已存在则直接返回
                            stream = results.getFirst();
                        }
                    }
                    case GROUP -> {
                        // 检查实体是否存在
                        var query = em.createQuery(
                          "SELECT s FROM InteractionStream s WHERE s.type = :type AND s.group.id = :groupId",
                          InteractionStream.class
                        );
                        query.setParameter("type", StreamType.GROUP);
                        query.setParameter("groupId", interactionGroup.getId());
                        var results = query.getResultList();

                        if (results.isEmpty()) {
                            // 不存在则创建新实体
                            stream = new InteractionStream();
                            stream.setId(InteractionStream.idGen(StreamType.GROUP, interactionGroup.getId()));
                            stream.setType(StreamType.GROUP);
                            stream.setGroup(interactionGroup);
                            em.persist(stream);
                        } else {
                            // 已存在则直接返回
                            stream = results.getFirst();
                        }
                    }
                    default -> // 理论上不会到达这里
                      throw new IllegalArgumentException("Unsupported StreamType: " + type);
                }
                lockObject.complete(stream);
                return stream;
            } catch (Exception e) {
                log.warn("An exception occurred while creating InteractionStream for key: {}", key, e);
                return null;
            } finally {
                lockMap.remove(key);
            }
        } else {
            // 其他线程等待锁完成
            try {
                var entity = mappedLock.get();
                em.merge(entity); // 将实体附加到当前 EntityManager 上
                return entity;
            } catch (CancellationException | ExecutionException ignored) {
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }
}
