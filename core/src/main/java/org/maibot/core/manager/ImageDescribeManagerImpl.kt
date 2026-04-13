package org.maibot.core.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.manager.BinFileManager
import org.maibot.sdk.manager.ImageDescribeManager
import org.maibot.sdk.model.executors.ImageDescGenerator
import org.maibot.sdk.storage.db.DatabaseService
import org.maibot.sdk.storage.db.dao.BinFile
import org.maibot.sdk.storage.db.dao.ImageDesc
import org.maibot.sdk.task.TaskExecuteService
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/// get -> 获取描述，若不存在则返回null
/// getOrCreateIfAbsent -> 获取描述，若不存在则创建记录并异步生成描述，返回包含记录和描述生成结果的Future

@Component
class ImageDescribeManagerImpl
@AutoInject
private constructor(
    private val imageDescGenerator: ImageDescGenerator,
) : ImageDescribeManager {
    // 锁对象映射，用于防止实例的重复创建
    private val lockMap = ConcurrentHashMap<String, CompletableFuture<ImageDesc>>()

    /**
     * 获取或创建图像描述记录（如果不存在）
     *
     * @param em       实体管理器
     * @param binFile  图像的二进制文件信息
     * @param isEmoji  是否为表情图片
     * @return 图像描述记录及其描述文本的异步结果(涉及LLM调用)，若创建失败则返回 null
     */
    override fun getOrCreatIfAbsent(
        em: EntityManager, imgFile: BinFileManager.BinFileWithData, isEmoji: Boolean
    ): ImageDesc? {
        // 使用锁对象，防止重复查询和创建
        val hash = imgFile.binFile.hash!!
        val lockObject = CompletableFuture<ImageDesc>()
        val mappedLock = lockMap.putIfAbsent(hash, lockObject)

        if (mappedLock == null) {
            // 当前线程获得锁，执行获取或创建逻辑
            try {
                val imageDesc = (this.get(em, imgFile.binFile) ?: run {
                    // 不存在，先获取desc，然后创建新实体
                    val desc =
                        imageDescGenerator.generateImageDescription(imgFile.data!!, imgFile.binFile.fileType!!, isEmoji)

                    if (desc.isNullOrBlank()) {
                        log.warn("生成图像描述失败 (binFileHash: {})", hash)
                        return null
                    }

                    ImageDesc().apply {
                        this.binFile = imgFile.binFile
                        this.isEmoji = isEmoji
                        this.description = desc
                        em.persist(this)
                    }
                })

                // 完成锁对象，通知等待的线程
                lockObject.complete(imageDesc)
                return imageDesc
            } catch (e: Exception) {
                log.error("获取或创建 ImageDescribe (binFileHash: {}) 时发生异常", hash, e)
                lockObject.completeExceptionally(e)
                return null
            } finally {
                // 移除锁对象，允许其他线程继续尝试获取或创建
                lockMap.remove(hash)
            }
        } else {
            // 其他线程已经在处理该图像描述的获取或创建，等待结果
            try {
                return mappedLock.get()
            } catch (e: Exception) {
                log.error("等待获取 ImageDescribe (binFileHash: {}) 结果时发生异常", hash, e)
                return null
            }
        }
    }

    /**
     * 根据 BinFile 获取图像描述记录
     *
     * @param em      实体管理器
     * @param binFile 图像的二进制文件信息
     * @return 图像描述记录及其描述文本的异步结果(涉及LLM调用)，若不存在则返回 null
     */
    override fun get(em: EntityManager, imgFile: BinFile): ImageDesc? {
        try {
            return em.createQuery(
                "SELECT imgDesc FROM ImageDesc imgDesc WHERE imgDesc.binFile.id = :id",
                ImageDesc::class.java
            ).setParameter("id", imgFile.id!!).resultList.firstOrNull()
        } catch (e: Exception) {
            log.error("获取 ImageDescribe (binFileId: {}) 时发生异常", imgFile.id, e)
            return null
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ImageDescribeManagerImpl::class.java)
    }
}