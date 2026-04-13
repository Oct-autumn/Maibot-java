package org.maibot.sdk.storage.db.dao

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "group_member")
@IdClass(GroupMember.GroupMemberId::class)
class GroupMember {
    @JvmField
    @Id
    @Column(name = "entity_id")
    var entityId: Long? = null

    @JvmField
    @Id
    @Column(name = "group_id")
    var groupId: Long? = null

    @JvmField
    @Column(name = "card_name")
    var cardName: String? = null

    @JvmField
    @ManyToOne
    @JoinColumn(name = "entity_id", referencedColumnName = "id")
    var entity: InteractionEntity? = null

    @JvmField
    @ManyToOne
    @JoinColumn(name = "group_id", referencedColumnName = "id")
    var group: InteractionGroup? = null

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    var createdAt: Instant? = null

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    var updatedAt: Instant? = null

    class GroupMemberId() {
        private var groupId: Long? = null
        private var entityId: Long? = null

        constructor(groupId: Long, entityId: Long) : this() {
            this.groupId = groupId
            this.entityId = entityId
        }

        override fun hashCode(): Int {
            return (groupId?.hashCode() ?: 0) * 31 + (entityId?.hashCode() ?: 0)
        }

        // equals and hashCode methods should be implemented for composite key
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is GroupMemberId) return false
            return entityId == other.entityId && groupId == other.groupId
        }
    }
}
