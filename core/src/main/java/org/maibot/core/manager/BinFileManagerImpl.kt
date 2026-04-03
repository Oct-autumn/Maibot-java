package org.maibot.core.manager;

import jakarta.persistence.EntityManager;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.manager.BinFileManager;
import org.maibot.sdk.storage.db.dao.BinFile;
import org.maibot.sdk.util.HashUtils;
import org.maibot.sdk.util.LocalBinFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 二进制文件管理器实现
 */
@Component
public class BinFileManagerImpl implements BinFileManager {
    private static final Logger log = LoggerFactory.getLogger(BinFileManagerImpl.class);

    // 锁对象映射，用于防止 交互实体 的重复创建
    private final ConcurrentMap<String, CompletableFuture<BinFileWithData>> lockMap = new ConcurrentHashMap<>();

    @Override
    public BinFileWithData getOrCreatIfAbsent(EntityManager em, String hash, String fileType, byte[] binData) {
        // 使用锁对象，防止重复查询和创建
        var lockObject = new CompletableFuture<BinFileWithData>();
        var mappedLock = lockMap.putIfAbsent(hash, lockObject);

        if (mappedLock == null) {
            // 当前线程获得锁，执行获取或创建逻辑
            try {
                // 检查实体是否存在，防止重复创建
                BinFileWithData binFileWithData;

                binFileWithData = this.get(em, hash);
                if (binFileWithData == null) {
                    // 不存在则创建新实体
                    LocalBinFileUtils.saveFile(fileType, hash, binData);

                    var newBinFile = new BinFile();
                    newBinFile.setHash(hash);
                    newBinFile.setFileType(fileType);
                    em.persist(newBinFile);

                    binFileWithData = new BinFileWithData(newBinFile, binData);
                } else if (binFileWithData.data() == null) {
                    // 存在但本地文件缺失，补全本地文件
                    LocalBinFileUtils.saveFile(fileType, hash, binData);
                    binFileWithData = new BinFileWithData(binFileWithData.binFile(), binData);
                }
                // 完成锁对象，通知等待的线程
                lockObject.complete(binFileWithData);
                return binFileWithData;
            } catch (Exception e) {
                log.error("获取或创建 BinFile (hash: {}) 时发生异常", hash, e);
                lockObject.completeExceptionally(e);
                return null;
            }
        } else {
            // 其他线程等待锁对象完成
            try {
                return mappedLock.get();
            } catch (Exception e) {
                log.error("等待获取 BinFile (hash: {}) 时发生异常", hash, e);
                return null;
            }
        }
    }

    @Override
    public BinFileWithData update(EntityManager em, BinFile binFile, byte[] binData) {
        try {
            try (var binStream = new ByteArrayInputStream(binData)) {
                var hash = HashUtils.getSha256Hash(binStream);
                LocalBinFileUtils.saveFile(binFile.getFileType(), hash, binData);

                binFile.setHash(hash);
                em.merge(binFile);

                return new BinFileWithData(binFile, binData);
            }
        } catch (Exception e) {
            log.error("更新 BinFile (id: {}) 时发生异常", binFile.getId(), e);
            return null;
        }
    }

    @Override
    public BinFileWithData get(EntityManager em, String hash) {
        try {
            var query = em.createQuery("SELECT b FROM BinFile b WHERE b.hash = :hash", BinFile.class);
            query.setParameter("hash", hash);

            var resultList = query.getResultList();
            if (resultList.isEmpty()) {
                return null;
            } else {
                var binFile = resultList.getFirst();
                return internalGet(binFile);
            }
        } catch (Exception e) {
            log.error("获取 BinFile (hash: {}) 时发生异常", hash, e);
            return null;
        }
    }

    @Override
    public BinFileWithData get(EntityManager em, long id) {
        try {
            var query = em.createQuery("SELECT b FROM BinFile b WHERE b.id = :id", BinFile.class);
            query.setParameter("id", id);

            var resultList = query.getResultList();
            if (resultList.isEmpty()) {
                return null;
            } else {
                var binFile = resultList.getFirst();
                return internalGet(binFile);
            }
        } catch (Exception e) {
            log.error("获取 BinFile (id: {}) 时发生异常", id, e);
            return null;
        }
    }

    private BinFileManager.BinFileWithData internalGet(BinFile binFile) {
        var file = LocalBinFileUtils.getFile(binFile.getFileType(), binFile.getHash());

        if (file != null) {
            try (var fileInputStream = new FileInputStream(file)) {
                // 核对hash
                var hash = HashUtils.getSha256Hash(fileInputStream);
                if (hash.equals(binFile.getHash())) {
                    var fileData = fileInputStream.readAllBytes();
                    return new BinFileWithData(binFile, fileData);
                } else {
                    log.warn("本地文件 Hash 不匹配 (expected: {}, actual: {})，推测文件已损坏", binFile.getHash(), hash);
                }
            } catch (IOException e) {
                log.error("读取本地文件时发生异常 (type: {}, hash: {})", binFile.getFileType(), binFile.getHash(), e);
            }
        } else {
            log.warn("本地文件不存在 (type: {}, hash: {})", binFile.getFileType(), binFile.getHash());
        }
        return new BinFileWithData(binFile, null);
    }
}
