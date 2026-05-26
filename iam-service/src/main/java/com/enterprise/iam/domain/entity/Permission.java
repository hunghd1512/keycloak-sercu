package com.enterprise.iam.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "permissions", indexes = {
    @Index(name = "idx_permission_name", columnList = "name", unique = true),
    @Index(name = "idx_permission_resource", columnList = "resource")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"id", "name"})
@ToString(exclude = {"roles"})
public class Permission extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(unique = true, nullable = false, length = 100)
    private String name;
    
    @Column(length = 100)
    private String resource;
    
    @Column(length = 50)
    private String action;
    
    @Column(length = 500)
    private String description;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;
    
    @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
    
    public String getFullPermission() {
        return resource + ":" + action;
    }
}
