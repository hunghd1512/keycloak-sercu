package com.enterprise.iam.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "organizations", indexes = {
    @Index(name = "idx_org_code", columnList = "code", unique = true),
    @Index(name = "idx_org_parent", columnList = "parent_id"),
    @Index(name = "idx_org_path", columnList = "path")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"id", "code"})
@ToString(exclude = {"users", "children", "parent"})
public class Organization {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(unique = true, nullable = false, length = 100)
    private String code;
    
    @Column(nullable = false, length = 255)
    private String name;
    
    @Column(length = 1000)
    private String description;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Organization parent;
    
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private Set<Organization> children = new HashSet<>();
    
    @Column(name = "path", length = 1000)
    private String path;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer level = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrganizationType type = OrganizationType.DEPARTMENT;
    
    @Column(name = "manager_id")
    private String managerId;
    
    @Column(length = 100)
    private String location;
    
    @Column(name = "cost_center", length = 50)
    private String costCenter;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "organization_users",
               joinColumns = @JoinColumn(name = "organization_id"),
               inverseJoinColumns = @JoinColumn(name = "user_id"))
    @Builder.Default
    private Set<User> users = new HashSet<>();
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    public enum OrganizationType {
        COMPANY,
        DIVISION,
        DEPARTMENT,
        TEAM,
        UNIT
    }
    
    public void addChild(Organization child) {
        this.children.add(child);
        child.setParent(this);
        updatePath();
    }
    
    public void removeChild(Organization child) {
        this.children.remove(child);
        child.setParent(null);
    }
    
    public void updatePath() {
        if (this.parent != null) {
            this.path = this.parent.getPath() + "/" + this.id;
            this.level = this.parent.getLevel() + 1;
        } else {
            this.path = "/" + this.id;
            this.level = 0;
        }
    }
    
    public boolean isAncestorOf(Organization other) {
        if (other.getPath() == null || this.path == null) {
            return false;
        }
        return other.getPath().startsWith(this.path + "/");
    }
    
    public boolean isDescendantOf(Organization other) {
        return other.isAncestorOf(this);
    }
}
