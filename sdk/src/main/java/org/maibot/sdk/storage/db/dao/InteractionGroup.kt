package org.maibot.sdk.storage.db.dao;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

@Entity
@Table(
  name = "interaction_group",
  uniqueConstraints = {@UniqueConstraint(columnNames = {"platform", "platform_group_id"})},
  indexes = {@Index(name = "idx_platform_group", columnList = "platform, platform_group_id")}
)
public class InteractionGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /// 平台名称
    @Column(name = "platform", nullable = false)
    private String platform;
    /// 平台群组ID
    @Column(name = "platform_group_id", nullable = false)
    private String platformGroupId;

    /// 群组名称
    @Column(name = "group_name")
    private String groupName;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL)
    private List<GroupMember> members;

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

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getPlatformGroupId() {
        return platformGroupId;
    }

    public void setPlatformGroupId(String platformGroupId) {
        this.platformGroupId = platformGroupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public List<GroupMember> getMembers() {
        return members;
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
