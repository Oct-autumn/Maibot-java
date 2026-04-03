package org.maibot.sdk.storage.db.dao

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(
    name = "interaction_entity",
    uniqueConstraints = [UniqueConstraint(columnNames = ["platform", "platform_user_id"])],
    indexes = [Index(name = "idx_platform_user", columnList = "platform, platform_user_id")]
)
class InteractionEntity {
    @JvmField
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 平台名称 */
    @JvmField
    @Column(name = "platform", nullable = false)
    var platform: String? = null

    /** 平台用户ID */
    @JvmField
    @Column(name = "platform_user_id", nullable = false)
    var platformUserId: String? = null

    /** 昵称 */
    @JvmField
    @Column(name = "nickname", nullable = false)
    var nickname: String? = null

    @JvmField
    @ManyToOne
    @JoinColumn(name = "person_id", referencedColumnName = "id", nullable = false)
    var person: Person? = null

    @OneToMany(mappedBy = "sender", cascade = [CascadeType.ALL])
    val messages: MutableList<Message?>? = null

    @OneToMany(mappedBy = "entity", cascade = [CascadeType.ALL])
    val groupMembers: MutableList<GroupMember?>? = null

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    var createdAt: Instant? = null

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    var updatedAt: Instant? = null
}
