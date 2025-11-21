package org.maibot.sdk.storage.db.dao;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "bin_file", indexes = {@Index(name = "idx_bin_file_wget_url", columnList = "wget_url")})
public class BinFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /// 文件的 wget 下载地址
    @Column(name = "wget_url", length = 1024)
    private String wgetUrl;

    /// 文件的 SHA-256 哈希值，用于文件命名和数据校验
    @Column(name = "hash_sha256", length = 64)
    private String hash;

    /// 文件类型，例如 png、wav 等
    @Column(name = "file_type")
    private String fileType;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWgetUrl() {
        return wgetUrl;
    }

    public void setWgetUrl(String wgetUrl) {
        this.wgetUrl = wgetUrl;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
