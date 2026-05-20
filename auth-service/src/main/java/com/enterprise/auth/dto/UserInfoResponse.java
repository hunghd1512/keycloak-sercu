package com.enterprise.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {
    
    private String userId;
    
    private String username;
    
    private String email;
    
    private String firstName;
    
    private String lastName;
    
    private String displayName;
    
    private String sessionId;
    
    private Long expiresAt;
    
    private List<String> roles;
    
    private String deviceId;
    
    private String deviceName;
    
    private String userAgent;
    
    private String ipAddress;
    
    private Instant createdAt;
    
    private Instant lastAccessedAt;
}
