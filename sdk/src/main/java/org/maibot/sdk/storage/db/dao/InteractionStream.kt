package org.maibot.sdk.storage.db.dao

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.maibot.sdk.storage.domain.StreamType
import java.time.Instant

@Entity
@Table(
    name = "interaction_stream",
    indexes = [Index(
        name = "idx_interaction_stream_type_entity_group",
        columnList = "type, entity_id, group_id",
        unique = true
    )]
)
class InteractionStream {
    @JvmField
    @Id
    var id: String? = null

    @JvmField
    @Column(name = "type", nullable = false)
    var type: StreamType? = null

    @JvmField
    @ManyToOne
    @JoinColumn(name = "entity_id", referencedColumnName = "id")
    var entity: InteractionEntity? = null

    @JvmField
    @ManyToOne
    @JoinColumn(name = "group_id", referencedColumnName = "id")
    var group: InteractionGroup? = null

    @JvmField
    @OneToMany(mappedBy = "stream", cascade = [CascadeType.ALL])
    val messages: MutableList<Message>? = null

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    var createdAt: Instant? = null

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    var updatedAt: Instant? = null

    companion object {
        @JvmStatic
        fun idGen(streamType: StreamType, id: Long): String {
            return streamType.prefix + id
        }
    }
}
