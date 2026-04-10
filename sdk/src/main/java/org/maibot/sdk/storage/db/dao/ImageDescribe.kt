package org.maibot.sdk.storage.db.dao

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(
    name = "image_describe", indexes = [Index(name = "idx_message_timestamp", columnList = "timestamp"), Index(
        name = "idx_message_stream", columnList = "stream_id"
    )]
)
class ImageDescribe {
    @JvmField
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /**
     * 图像内容描述
     */
    @JvmField
    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null

    /**
     * 是否为表情图片
     */
    @JvmField
    @Column(name = "is_emoji", nullable = false)
    var isEmoji: Boolean? = null

    /**
     * 图像的二进制文件信息
     */
    @JvmField
    @OneToOne
    @JoinColumn(name = "file_id", referencedColumnName = "id", nullable = false)
    var binFile: BinFile? = null

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    var createdAt: Instant? = null

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    var updatedAt: Instant? = null
}
