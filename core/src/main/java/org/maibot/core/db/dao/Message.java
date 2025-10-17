package org.maibot.core.db.dao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@Setter
@Table(name = "message")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private Long streamId;

    private Long senderEntityId;

    @ManyToOne
    @JoinColumn(name = "stream_id", nullable = false, foreignKey = @ForeignKey(name = "FK_Message_InteractionStream"))
    private InteractionStream stream;

    @ManyToOne
    @JoinColumn(name = "sender_entity_id", nullable = false, foreignKey = @ForeignKey(name = "FK_Message_InteractionEntity"))
    private InteractionEntity sender;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private String createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private String updatedAt;
}
