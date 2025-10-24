package org.maibot.core.db.dao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.List;

@Entity
@Getter
@Setter
@Table(
        name = "interaction_group",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"platformId", "platformGroupId"})},
        indexes = {@Index(name = "idx_platform_group", columnList = "platformId, platformGroupId")}
)
public class InteractionGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String platformId;
    private String platformGroupId;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL)
    private List<GroupMember> members;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private String createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private String updatedAt;
}
