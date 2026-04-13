package org.maibot.sdk.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.storage.db.dao.BinFile
import org.maibot.sdk.storage.db.dao.ImageDesc
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
        imgFile: BinFileManager.BinFileWithData,
        isEmoji: Boolean
    ): ImageDesc?

    /**
     * 根据 BinFile 获取图像描述记录
     *
     * @param em      实体管理器
     * @param binFile 图像的二进制文件信息
     * @return 图像描述记录及其描述文本的异步结果(涉及LLM调用)，若不存在则返回 null
     */
    fun get(em: EntityManager, imgFile: BinFile): ImageDesc?
}
