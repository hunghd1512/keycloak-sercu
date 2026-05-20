package com.enterprise.iam.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_actor", columnList = "actor_id"),
    @Index(name = "idx_audit_target", columnList = "target_type, target_id"),
    @Index(name = "idx_audit_event", columnList = "event_type"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"id"})
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuditEventType eventType;
    
    @Column(name = "actor_id", nullable = false)
    private String actorId;
    
    @Column(name = "actor_username", nullable = false, length = 100)
    private String actorUsername;
    
    @Column(name = "actor_ip", length = 45)
    private String actorIp;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    private TargetType targetType;
    
    @Column(name = "target_id")
    private String targetId;
    
    @Column(name = "target_name", length = 255)
    private String targetName;
    
    @Column(nullable = false, length = 50)
    private String action;
    
    @Column(columnDefinition = "TEXT")
    private String details;
    
    @Column(columnDefinition = "JSONB")
    private String changeData;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean success = true;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "request_id", length = 100)
    private String requestId;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @CreatedDate
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;
    
    public enum AuditEventType {
        // User events
        USER_CREATED,
        USER_UPDATED,
        USER_DELETED,
        USER_DISABLED,
        USER_ENABLED,
        USER_LOGIN,
        USER_LOGOUT,
        PASSWORD_RESET_REQUESTED,
        PASSWORD_CHANGED,
        
        // Role events
        ROLE_CREATED,
        ROLE_UPDATED,
        ROLE_DELETED,
        ROLE_ASSIGNED,
        ROLE_REVOKED,
        
        // Organization events
        ORG_CREATED,
        ORG_UPDATED,
        ORG_DELETED,
        USER_ASSIGNED_TO_ORG,
        USER_REMOVED_FROM_ORG,
        
        // Permission events
        PERMISSION_CREATED,
        PERMISSION_UPDATED,
        PERMISSION_DELETED,
        ROLE_PERMISSION_ADDED,
        ROLE_PERMISSION_REMOVED,
        
        // Policy events
        POLICY_VIOLATION,
        POLICY_CHANGED,
        
        // System events
        SYSTEM_CONFIG_CHANGED,
        CACHE_INVALIDATED,
        SYNC_COMPLETED
    }
    
    public enum TargetType {
        USER,
        ROLE,
        ORGANIZATION,
        PERMISSION,
        POLICY,
        SYSTEM,
        CACHE
    }
}
