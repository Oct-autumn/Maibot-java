package org.maibot.sdk.storage.db.dao

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "bin_file", indexes = [Index(name = "idx_bin_file_hash_sha256", columnList = "hash_sha256")])
class BinFile {
    @JvmField
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 文件的 SHA-256 哈希值，用于文件命名和数据校验 */
    @JvmField
    @Column(name = "hash_sha256", length = 64, unique = true)
    var hash: String? = null

    /** 文件类型，例如 png、wav 等 */
    @JvmField
    @Column(name = "file_type")
    var fileType: String? = null
    
    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    var createdAt: Instant? = null

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    var updatedAt: Instant? = null
}
