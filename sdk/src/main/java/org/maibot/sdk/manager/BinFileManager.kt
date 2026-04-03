package org.maibot.sdk.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.storage.db.dao.BinFile

interface BinFileManager {
    /**
     * 获取或创建 BinFile 记录（如果不存在）
     * 
     * @param em       实体管理器
     * @param hash     文件的 SHA-256 哈希值
     * @param fileType 文件类型
     * @param binData  二进制数据
     * @return BinFile 记录及其数据
     */
    fun getOrCreatIfAbsent(em: EntityManager, hash: String, fileType: String, binData: ByteArray): BinFileWithData?

    /**
     * 更新 BinFile 记录及其数据
     * 
     * @param em      实体管理器
     * @param binFile 需要更新的 BinFile 记录
     * @param binData 新的二进制数据
     * @return 更新后的 BinFile 记录及其数据
     */
    fun update(em: EntityManager, binFile: BinFile, binData: ByteArray): BinFileWithData?

    /**
     * 根据 wgetUrl 获取 BinFile 记录及其数据
     * 
     * @param em   实体管理器
     * @param hash 文件的 SHA-256 哈希值
     * @return BinFile 记录及其数据
     */
    fun get(em: EntityManager, hash: String): BinFileWithData?

    /**
     * 根据 ID 获取 BinFile 记录及其数据
     * 
     * @param em 实体管理器
     * @param id 记录 ID
     * @return BinFile 记录及其数据
     */
    fun get(em: EntityManager, id: Long): BinFileWithData?

    @JvmRecord
    data class BinFileWithData(
        @JvmField val binFile: BinFile,
        @JvmField val data: ByteArray?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BinFileWithData

            if (binFile != other.binFile) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = binFile?.hashCode() ?: 0
            result = 31 * result + (data?.contentHashCode() ?: 0)
            return result
        }
    }
}
