package org.maibot.sdk.storage.db.dao;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "group_member")
@IdClass(GroupMember.GroupMemberId.class)
public class GroupMember {
    @Id
    @Column(name = "entity_id")
    private Long entityId;

    @Id
    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "card_name")
    private String cardName;

    @ManyToOne
    @JoinColumn(name = "entity_id", referencedColumnName = "id")
    private InteractionEntity entity;

    @ManyToOne
    @JoinColumn(name = "group_id", referencedColumnName = "id")
    private InteractionGroup group;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getCardName() {
        return cardName;
    }

    public void setCardName(String cardName) {
        this.cardName = cardName;
    }

    public InteractionEntity getEntity() {
        return entity;
    }

    public void setEntity(InteractionEntity entity) {
        this.entity = entity;
    }

    public InteractionGroup getGroup() {
        return group;
    }

    public void setGroup(InteractionGroup group) {
        this.group = group;
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

    public static class GroupMemberId {
        private Long groupId;
        private Long entityId;

        public GroupMemberId() {
        }

        public GroupMemberId(Long groupId, Long entityId) {
            this.groupId = groupId;
            this.entityId = entityId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, entityId);
        }

        // equals and hashCode methods should be implemented for composite key
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GroupMemberId that)) return false;
            return entityId.equals(that.entityId) && groupId.equals(that.groupId);
        }
    }
}
