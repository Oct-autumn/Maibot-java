package org.maibot.sdk.manager;

import jakarta.persistence.EntityManager;
import org.maibot.sdk.storage.db.dao.BinFile;

public interface BinFileManager {
    /**
     * 获取或创建 BinFile 记录（如果不存在）
     *
     * @param em
     * @param wgetUrl
     * @param fileType
     * @param binData
     * @return
     */
    BinFileWithData getOrCreatIfAbsent(EntityManager em, String wgetUrl, String fileType, byte[] binData);

    /**
     * 更新 BinFile 记录及其数据
     *
     * @param em
     * @param binFile
     * @param binData
     * @return
     */
    BinFileWithData update(EntityManager em, BinFile binFile, byte[] binData);

    /**
     * 根据 wgetUrl 获取 BinFile 记录及其数据
     *
     * @param em
     * @param wgetUrl
     * @return
     */
    BinFileWithData get(EntityManager em, String wgetUrl);

    /**
     * 根据 ID 获取 BinFile 记录及其数据
     *
     * @param em
     * @param id
     * @return
     */
    BinFileWithData get(EntityManager em, long id);

    class BinFileWithData {
        private final BinFile binFile;
        private final byte[]  data;

        public BinFileWithData(BinFile binFile, byte[] data) {
            this.binFile = binFile;
            this.data = data;
        }

        public BinFile getBinFile() {
            return binFile;
        }

        public byte[] getData() {
            return data;
        }
    }
}
