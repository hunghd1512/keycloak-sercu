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
@Table(name = "iam_users", indexes = {
    @Index(name = "idx_user_keycloak_id", columnList = "keycloak_user_id", unique = true),
    @Index(name = "idx_user_email", columnList = "email", unique = true),
    @Index(name = "idx_user_username", columnList = "username", unique = true),
    @Index(name = "idx_user_department", columnList = "department_id"),
    @Index(name = "idx_user_enabled", columnList = "enabled")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"id", "keycloakUserId"})
@ToString(exclude = {"roles", "organizations"})
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "keycloak_user_id", unique = true, nullable = false)
    private String keycloakUserId;
    
    @Column(unique = true, nullable = false, length = 100)
    private String username;
    
    @Column(unique = true, nullable = false, length = 255)
    private String email;
    
    @Column(name = "first_name", length = 100)
    private String firstName;
    
    @Column(name = "last_name", length = 100)
    private String lastName;
    
    @Column(name = "display_name", length = 255)
    private String displayName;
    
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;
    
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
    
    @Column(name = "employee_id", length = 50)
    private String employeeId;
    
    @Column(name = "title", length = 100)
    private String title;
    
    @Column(name = "department_id")
    private String departmentId;
    
    @Column(name = "manager_id")
    private String managerId;
    
    @Column(name = "cost_center", length = 50)
    private String costCenter;
    
    @Column(name = "location", length = 100)
    private String location;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;
    
    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;
    
    @Column(name = "hire_date")
    private Instant hireDate;
    
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_custom_attributes", 
                     joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "attribute_key")
    @Column(name = "attribute_value", columnDefinition = "TEXT")
    @Builder.Default
    private java.util.Map<String, String> customAttributes = new java.util.HashMap<>();
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
               joinColumns = @JoinColumn(name = "user_id"),
               inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
    
    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "users")
    @Builder.Default
    private Set<Organization> organizations = new HashSet<>();
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "updated_by")
    private String updatedBy;
    
    @Version
    private Long version;
    
    public String getFullName() {
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return username;
    }
    
    public void addRole(Role role) {
        this.roles.add(role);
        role.getUsers().add(this);
    }
    
    public void removeRole(Role role) {
        this.roles.remove(role);
        role.getUsers().remove(this);
    }
    
    public void clearRoles() {
        this.roles.forEach(role -> role.getUsers().remove(this));
        this.roles.clear();
    }
}
