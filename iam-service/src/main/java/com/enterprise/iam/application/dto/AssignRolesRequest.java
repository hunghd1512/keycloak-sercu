package com.enterprise.iam.application.dto;

import jakarta.validation.constraints.*;
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
public class AssignRolesRequest {
    
    @NotEmpty(message = "At least one role is required")
    private List<RoleAssignment> roles;
    
    private String reason;
    
    private Instant expiresAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleAssignment {
        
        @NotBlank(message = "Role name is required")
        private String roleName;
        
        private String clientId;
    }
}
