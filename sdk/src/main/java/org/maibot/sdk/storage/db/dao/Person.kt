package org.maibot.sdk.storage.db.dao

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

@Entity
@Table(name = "person")
class Person {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 名称 */
    @JvmField
    @Column(name = "name", nullable = false)
    var name: String? = null

    @OneToMany(mappedBy = "person", cascade = [CascadeType.ALL])
    val interactionEntities: MutableList<InteractionEntity?>? = null

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    var createdAt: String? = null

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    var updatedAt: String? = null
}
