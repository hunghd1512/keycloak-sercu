package com.enterprise.iam.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "user_role_assignments", indexes = {
    @Index(name = "idx_ura_user", columnList = "user_id"),
    @Index(name = "idx_ura_role", columnList = "role_id"),
    @Index(name = "idx_ura_assigned_by", columnList = "assigned_by"),
    @Index(name = "idx_ura_unique", columnList = "user_id, role_id, assigned_by", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"id"})
public class UserRoleAssignment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
    
    @Column(name = "assigned_by", nullable = false)
    private String assignedBy;
    
    @Column(name = "assigned_by_username", length = 100)
    private String assignedByUsername;
    
    @CreatedDate
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;
    
    @Column(name = "expires_at")
    private Instant expiresAt;
    
    @Column(name = "reason", length = 500)
    private String reason;
    
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "revoked_by")
    private String revokedBy;
    
    @Column(name = "revoked_at")
    private Instant revokedAt;
    
    @Column(name = "revoke_reason", length = 500)
    private String revokeReason;
    
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
    
    public boolean isRevoked() {
        return revokedAt != null;
    }
    
    public boolean isValid() {
        return isActive && !isExpired() && !isRevoked();
    }
}
