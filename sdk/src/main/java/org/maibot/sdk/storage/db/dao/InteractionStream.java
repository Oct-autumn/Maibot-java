package org.maibot.sdk.storage.db.dao;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.maibot.sdk.storage.domain.StreamType;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "interaction_stream", indexes = {
  @Index(name = "idx_entity_id", columnList = "entity_id", unique = true),
  @Index(name = "idx_group_id", columnList = "group_id", unique = true)
})
public class InteractionStream {
    @Id
    private String id;

    @Column(name = "type", nullable = false)
    private StreamType type;

    @ManyToOne
    @JoinColumn(name = "entity_id", foreignKey = @ForeignKey(name = "FK_InteractionStream_InteractionEntity"))
    private InteractionEntity entity;

    @ManyToOne
    @JoinColumn(name = "group_id", foreignKey = @ForeignKey(name = "FK_InteractionStream_Group"))
    private InteractionGroup group;

    @OneToMany(mappedBy = "stream", cascade = CascadeType.ALL)
    private List<Message> messages;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;

    public static String idGen(StreamType streamType, Long id) {
        return streamType.getPrefix() + id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public StreamType getType() {
        return type;
    }

    public void setType(StreamType type) {
        this.type = type;
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

    public List<Message> getMessages() {
        return messages;
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
}
