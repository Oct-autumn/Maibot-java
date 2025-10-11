package org.maibot.core.db.dao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Entity
@Getter
@Setter
@Table(name = "group_member")
@IdClass(GroupMember.GroupMemberId.class)
public class GroupMember {
    public static class GroupMemberId {
        private Long entityId;
        private Long groupId;

        public GroupMemberId() {
        }

        public GroupMemberId(Long entityId, Long groupId) {
            this.entityId = entityId;
            this.groupId = groupId;
        }

        // equals and hashCode methods should be implemented for composite key
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GroupMemberId that)) return false;
            return entityId.equals(that.entityId) && groupId.equals(that.groupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entityId, groupId);
        }
    }

    @Id
    private Long entityId;

    @Id
    private Long groupId;

    @ManyToOne
    @JoinColumn(name = "entityId", insertable = false, updatable = false)
    private InteractionEntity entity;

    @ManyToOne
    @JoinColumn(name = "groupId", insertable = false, updatable = false)
    private InteractionGroup group;
}
