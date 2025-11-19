package org.maibot.sdk.storage.db.dao;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.List;

@Entity
@Table(name = "person")
public class Person {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL)
    private List<InteractionEntity> interactionEntities;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private String createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private String updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<InteractionEntity> getInteractionEntities() {
        return interactionEntities;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
