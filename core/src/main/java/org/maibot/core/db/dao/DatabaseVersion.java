package org.maibot.core.db.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "db_version")
public class DatabaseVersion {
    @Id
    private Long id;

    @Column(nullable = false)
    private String version;
}
