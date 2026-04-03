package org.maibot.sdk.manager;

import jakarta.persistence.EntityManager;
import org.maibot.sdk.storage.db.dao.BinFile;

public interface BinFileManager {
    /**
     * 获取或创建 BinFile 记录（如果不存在）
     *
     * @param em       实体管理器
     * @param hash     文件的 SHA-256 哈希值
     * @param fileType 文件类型
     * @param binData  二进制数据
     * @return BinFile 记录及其数据
     */
    BinFileWithData getOrCreatIfAbsent(EntityManager em, String hash, String fileType, byte[] binData);

    /**
     * 更新 BinFile 记录及其数据
     *
     * @param em      实体管理器
     * @param binFile 需要更新的 BinFile 记录
     * @param binData 新的二进制数据
     * @return 更新后的 BinFile 记录及其数据
     */
    BinFileWithData update(EntityManager em, BinFile binFile, byte[] binData);

    /**
     * 根据 wgetUrl 获取 BinFile 记录及其数据
     *
     * @param em   实体管理器
     * @param hash 文件的 SHA-256 哈希值
     * @return BinFile 记录及其数据
     */
    BinFileWithData get(EntityManager em, String hash);

    /**
     * 根据 ID 获取 BinFile 记录及其数据
     *
     * @param em 实体管理器
     * @param id 记录 ID
     * @return BinFile 记录及其数据
     */
    BinFileWithData get(EntityManager em, long id);

    record BinFileWithData(
      BinFile binFile,
      byte[] data
    ) {
    }
}
