package org.maibot.core.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.manager.BinFileManager
import org.maibot.sdk.manager.BinFileManager.BinFileWithData
import org.maibot.sdk.storage.db.dao.BinFile
import org.maibot.sdk.util.HashUtils.getSha256Hash
import org.maibot.sdk.util.LocalBinFileUtils.getFile
import org.maibot.sdk.util.LocalBinFileUtils.saveFile
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * 二进制文件管理器实现
 */
@Component
class BinFileManagerImpl : BinFileManager {
    // 锁对象映射，用于防止 交互实体 的重复创建
    private val lockMap = ConcurrentHashMap<String, CompletableFuture<BinFileWithData>>()

    override fun getOrCreatIfAbsent(
        em: EntityManager, hash: String, fileType: String, binData: ByteArray
    ): BinFileWithData? {
        // 使用锁对象，防止重复查询和创建
        val lockObject = CompletableFuture<BinFileWithData>()
        val mappedLock = lockMap.putIfAbsent(hash, lockObject)

        if (mappedLock == null) {
            // 当前线程获得锁，执行获取或创建逻辑
            try {
                // 检查实体是否存在，防止重复创建
                val binFileWithData = (this.get(em, hash) ?: run { // 不存在则创建新实体
                    saveFile(fileType, hash, binData)

                    BinFile().run {
                        this.hash = hash
                        this.fileType = fileType
                        em.persist(this)
                        BinFileWithData(this, binData)
                    }
                }).run {
                    if (data != null) {
                        return@run this
                    } else {
                        // 存在但本地文件缺失，补全本地文件
                        saveFile(fileType, hash, binData)
                        return@run BinFileWithData(binFile, binData)
                    }
                }

                // 完成锁对象，通知等待的线程
                lockObject.complete(binFileWithData)
                return binFileWithData
            } catch (e: Exception) {
                log.error("获取或创建 BinFile (hash: {}) 时发生异常", hash, e)
                lockObject.completeExceptionally(e)
                return null
            }
        } else {
            // 其他线程等待锁对象完成
            try {
                return mappedLock.get()
            } catch (e: Exception) {
                log.error("等待获取 BinFile (hash: {}) 时发生异常", hash, e)
                return null
            }
        }
    }

    override fun update(em: EntityManager, binFile: BinFile, binData: ByteArray): BinFileWithData? {
        try {
            ByteArrayInputStream(binData).use { binStream ->
                val hash = getSha256Hash(binStream)
                saveFile(binFile.fileType!!, hash, binData)
                binFile.hash = hash
                em.merge(binFile)
                return BinFileWithData(binFile, binData)
            }
        } catch (e: Exception) {
            log.error("更新 BinFile (id: {}) 时发生异常", binFile.id, e)
            return null
        }
    }

    override fun get(em: EntityManager, hash: String): BinFileWithData? {
        try {
            return em.createQuery("SELECT b FROM BinFile b WHERE b.hash = :hash", BinFile::class.java)
                .apply { setParameter("hash", hash) }.resultList.firstOrNull()?.let { internalGet(it) }
        } catch (e: Exception) {
            log.error("获取 BinFile (hash: {}) 时发生异常", hash, e)
            return null
        }
    }

    override fun get(em: EntityManager, id: Long): BinFileWithData? {
        try {
            return em.createQuery("SELECT b FROM BinFile b WHERE b.id = :id", BinFile::class.java)
                .apply { setParameter("id", id) }.resultList.firstOrNull()?.let { internalGet(it) }
        } catch (e: Exception) {
            log.error("获取 BinFile (id: {}) 时发生异常", id, e)
            return null
        }
    }

    private fun internalGet(binFile: BinFile): BinFileWithData {
        try {
            getFile(binFile.fileType!!, binFile.hash!!)?.inputStream()?.use { fileInputStream ->
                // 核对hash
                val hash = getSha256Hash(fileInputStream)
                if (hash == binFile.hash) {
                    val fileData = fileInputStream.readAllBytes()
                    return BinFileWithData(binFile, fileData)
                } else {
                    log.warn("本地文件 Hash 不匹配 (expected: {}, actual: {})，推测文件已损坏", binFile.hash, hash)
                }
            } ?: log.warn("本地文件不存在 (type: {}, hash: {})", binFile.fileType, binFile.hash)
        } catch (e: IOException) {
            log.error("读取本地文件时发生异常 (type: {}, hash: {})", binFile.fileType, binFile.hash, e)
        }
        return BinFileWithData(binFile, null)
    }

    companion object {
        private val log = LoggerFactory.getLogger(BinFileManagerImpl::class.java)
    }
}
