package org.maibot.sdk.storage.db.dao

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(
    name = "message",
    indexes = [Index(name = "idx_message_timestamp", columnList = "timestamp"), Index(
        name = "idx_message_stream",
        columnList = "stream_id"
    )]
)
class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "timestamp", nullable = false)
    var timestamp: Long? = null

    @Column(name = "sequence", nullable = false)
    var sequence: Long? = null

    @Column(name = "prompt_str", columnDefinition = "MEDIUMTEXT")
    var promptStr: String? = null

    @Column(name = "raw_content_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    var rawContentJson: String? = null

    @Column(name = "object_type", nullable = false, columnDefinition = "TEXT")
    var objectType: String? = null

    @ManyToOne
    @JoinColumn(name = "sender_id", referencedColumnName = "id", nullable = false)
    var sender: InteractionEntity? = null

    @ManyToOne
    @JoinColumn(
        name = "stream_id",
        referencedColumnName = "id",
        nullable = false,
        foreignKey = ForeignKey(name = "FK_Message_InteractionStream")
    )
    var stream: InteractionStream? = null

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    var createdAt: Instant? = null

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    var updatedAt: Instant? = null
}
