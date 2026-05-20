package com.enterprise.iam.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDto {
    
    private String id;
    
    private String name;
    
    private String clientId;
    
    private String description;
    
    private Boolean enabled;
    
    private Boolean isComposite;
    
    private RoleType roleType;
    
    private Integer userCount;
    
    private Instant createdAt;
    
    private Instant updatedAt;
    
    public enum RoleType {
        REALM,
        CLIENT
    }
}
