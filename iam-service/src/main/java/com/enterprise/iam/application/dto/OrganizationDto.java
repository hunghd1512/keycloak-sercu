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
public class OrganizationDto {
    
    private String id;
    
    @NotBlank(message = "Organization code is required")
    @Size(min = 2, max = 100, message = "Code must be between 2 and 100 characters")
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Code must contain only uppercase letters, numbers, underscores and hyphens")
    private String code;
    
    @NotBlank(message = "Organization name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;
    
    private String description;
    
    private String parentId;
    
    private String parentName;
    
    private String path;
    
    private Integer level;
    
    private OrganizationType type;
    
    private String managerId;
    
    private String managerName;
    
    private String location;
    
    private String costCenter;
    
    private Boolean enabled;
    
    private List<OrganizationDto> children;
    
    private Integer userCount;
    
    private Instant createdAt;
    
    private Instant updatedAt;
    
    public enum OrganizationType {
        COMPANY,
        DIVISION,
        DEPARTMENT,
        TEAM,
        UNIT
    }
}
