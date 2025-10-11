package org.maibot.core.db.dao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(
        name = "interaction_stream",
        indexes = {@Index(name = "idx_entity_group", columnList = "entity_id, group_id")}
)
public class InteractionStream {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "entity_id", foreignKey = @ForeignKey(name = "FK_InteractionStream_InteractionEntity"))
    private InteractionEntity entity;

    @ManyToOne
    @JoinColumn(name = "group_id", foreignKey = @ForeignKey(name = "FK_InteractionStream_Group"))
    private InteractionGroup group;

    @OneToMany(mappedBy = "stream", cascade = CascadeType.ALL)
    private List<Message> messages;
}
