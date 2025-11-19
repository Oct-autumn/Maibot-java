package org.maibot.sdk.storage.db.dao;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "message")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    @Column(name = "sequence", nullable = false)
    private Long sequence;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "raw_content_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String rawContentJson;

    @Column(name = "object_type", nullable = false, columnDefinition = "TEXT")
    private String objectType;

    @ManyToOne
    @JoinColumn(name = "stream_id", nullable = false, foreignKey = @ForeignKey(name = "FK_Message_InteractionStream"))
    private InteractionStream stream;

    @ManyToOne
    @JoinColumn(name = "sender_entity_id", nullable = false, foreignKey = @ForeignKey(name = "FK_Message_InteractionEntity"))
    private InteractionEntity sender;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Long getSequence() {
        return sequence;
    }

    public void setSequence(Long sequence) {
        this.sequence = sequence;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRawContentJson() {
        return rawContentJson;
    }

    public void setRawContentJson(String rawContentJson) {
        this.rawContentJson = rawContentJson;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public InteractionStream getStream() {
        return stream;
    }

    public void setStream(InteractionStream stream) {
        this.stream = stream;
    }

    public InteractionEntity getSender() {
        return sender;
    }

    public void setSender(InteractionEntity sender) {
        this.sender = sender;
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
