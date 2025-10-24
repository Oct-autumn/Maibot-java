package org.maibot.core.db.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@Setter
@Table(name = "db_version")
public class DatabaseVersion {
    @Id
    private Long id;

    @Column(nullable = false)
    private String version;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private String createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private String updatedAt;
}
