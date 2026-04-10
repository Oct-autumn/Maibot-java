package org.maibot.sdk.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.storage.db.dao.ImageDescribe
import java.util.concurrent.CompletableFuture

interface ImageDescribeManager {
    /**
     * 获取或创建图像描述记录（如果不存在）
     *
     * @param em       实体管理器
     * @param binFile  图像的二进制文件信息
     * @param isEmoji  是否为表情图片
     * @return 图像描述记录及其描述文本的异步结果(涉及LLM调用)，若创建失败则返回 null
     */
    fun getOrCreatIfAbsent(
        em: EntityManager,
        hash: String,
        fileType: String,
        imgData: ByteArray,
        isEmoji: Boolean
    ): ImageDescWithFuture?

    /**
     * 根据 BinFile 获取图像描述记录
     *
     * @param em      实体管理器
     * @param binFile 图像的二进制文件信息
     * @return 图像描述记录及其描述文本的异步结果(涉及LLM调用)，若不存在则返回 null
     */
    fun get(em: EntityManager, hash: String): ImageDescWithFuture?

    @JvmRecord
    data class ImageDescWithFuture(
        @JvmField val imageDesc: ImageDescribe,
        @JvmField val descFuture: CompletableFuture<String>?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ImageDescWithFuture

            if (imageDesc != other.imageDesc) return false
            if (descFuture != other.descFuture) return false

            return true
        }

        override fun hashCode(): Int {
            var result = imageDesc.hashCode()
            result = 31 * result + (descFuture?.hashCode() ?: 0)
            return result
        }
    }
}
