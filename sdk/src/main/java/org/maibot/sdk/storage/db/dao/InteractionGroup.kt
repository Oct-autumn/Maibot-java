package org.maibot.sdk.storage.db.dao

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(
    name = "interaction_group",
    uniqueConstraints = [UniqueConstraint(columnNames = ["platform", "platform_group_id"])],
    indexes = [Index(name = "idx_platform_group", columnList = "platform, platform_group_id")]
)
class InteractionGroup {
    @JvmField
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 平台名称 */
    @JvmField
    @Column(name = "platform", nullable = false)
    var platform: String? = null

    /** 平台群组ID */
    @JvmField
    @Column(name = "platform_group_id", nullable = false)
    var platformGroupId: String? = null

    /** 群组名称 */
    @JvmField
    @Column(name = "group_name")
    var groupName: String? = null

    @OneToMany(mappedBy = "group", cascade = [CascadeType.ALL])
    val members: MutableList<GroupMember?>? = null

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    var createdAt: Instant? = null

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    var updatedAt: Instant? = null
}
