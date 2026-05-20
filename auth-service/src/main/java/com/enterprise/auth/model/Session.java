package com.enterprise.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "auth:sessions", timeToLive = 28800) // 8 hours
public class Session implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    private String id;
    
    @Indexed
    private String userId;
    
    private String username;
    
    private String email;
    
    private String accessToken;
    
    private String refreshToken;
    
    private String idToken;
    
    private String sessionState;
    
    private Instant createdAt;
    
    private Instant expiresAt;
    
    private Instant lastAccessedAt;
    
    private String deviceId;
    
    private String deviceName;
    
    private String userAgent;
    
    private String ipAddress;
    
    private List<String> roles;
    
    private boolean active;
    
    public void updateAccessToken(String accessToken, long expiresIn) {
        this.accessToken = accessToken;
        this.expiresAt = Instant.now().plusSeconds(expiresIn);
        this.lastAccessedAt = Instant.now();
    }
    
    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }
}
