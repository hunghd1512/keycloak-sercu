package com.enterprise.iam.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "iam_roles", indexes = {
    @Index(name = "idx_role_name", columnList = "name", unique = true),
    @Index(name = "idx_role_client", columnList = "client_id"),
    @Index(name = "idx_role_enabled", columnList = "enabled")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"id", "name", "clientId"})
@ToString(exclude = {"users", "permissions", "childRoles", "parentRoles"})
public class Role extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(unique = true, nullable = false, length = 100)
    private String name;
    
    @Column(name = "client_id")
    private String clientId;
    
    @Column(length = 500)
    private String description;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;
    
    @Column(name = "is_composite")
    @Builder.Default
    private Boolean isComposite = false;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false)
    @Builder.Default
    private RoleType roleType = RoleType.REALM;
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_permissions",
               joinColumns = @JoinColumn(name = "role_id"),
               inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_hierarchy",
               joinColumns = @JoinColumn(name = "parent_role_id"),
               inverseJoinColumns = @JoinColumn(name = "child_role_id"))
    @Builder.Default
    private Set<Role> childRoles = new HashSet<>();
    
    @ManyToMany(mappedBy = "childRoles", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Role> parentRoles = new HashSet<>();
    
    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<User> users = new HashSet<>();
    
    public enum RoleType {
        REALM,      // Realm-wide roles (SUPER_ADMIN, ADMIN, USER)
        CLIENT     // Client-specific roles (DOC_EDITOR, DOC_VIEWER)
    }
    
    public void addChildRole(Role childRole) {
        this.childRoles.add(childRole);
        childRole.getParentRoles().add(this);
    }
    
    public void removeChildRole(Role childRole) {
        this.childRoles.remove(childRole);
        childRole.getParentRoles().remove(this);
    }
    
    public void addPermission(Permission permission) {
        this.permissions.add(permission);
        permission.getRoles().add(this);
    }
    
    public void removePermission(Permission permission) {
        this.permissions.remove(permission);
        permission.getRoles().remove(this);
    }
    
    public Set<Role> getAllChildRoles() {
        Set<Role> allChildren = new HashSet<>();
        for (Role child : childRoles) {
            allChildren.add(child);
            allChildren.addAll(child.getAllChildRoles());
        }
        return allChildren;
    }
    
    public Set<Role> getAllParentRoles() {
        Set<Role> allParents = new HashSet<>();
        for (Role parent : parentRoles) {
            allParents.add(parent);
            allParents.addAll(parent.getAllParentRoles());
        }
        return allParents;
    }
}
