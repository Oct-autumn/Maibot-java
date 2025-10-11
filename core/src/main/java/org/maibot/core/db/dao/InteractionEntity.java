package org.maibot.core.db.dao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(
        name = "interaction_entity",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"platformId", "platformUserId"})},
        indexes = {@Index(name = "idx_platform_user", columnList = "platformId, platformUserId")}
)
public class InteractionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String platformId;
    private String platformUserId;

    @ManyToOne
    @JoinColumn(name = "personId", nullable = false, foreignKey = @ForeignKey(name = "FK_InteractionEntity_Person"))
    private Person person;

    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL)
    private List<Message> messages;
}
