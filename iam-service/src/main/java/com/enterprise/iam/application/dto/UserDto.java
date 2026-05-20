package com.enterprise.iam.application.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    
    private String id;
    
    private String keycloakUserId;
    
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters")
    private String username;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    private String firstName;
    
    private String lastName;
    
    private String displayName;
    
    private String avatarUrl;
    
    private String phoneNumber;
    
    private String employeeId;
    
    private String title;
    
    private String departmentId;
    
    private String departmentName;
    
    private String managerId;
    
    private String managerName;
    
    private String costCenter;
    
    private String location;
    
    private Boolean enabled;
    
    private Boolean emailVerified;
    
    private Instant hireDate;
    
    private Instant lastLoginAt;
    
    private String description;
    
    private Map<String, String> customAttributes;
    
    private List<RoleDto> roles;
    
    private List<OrganizationDto> organizations;
    
    private Instant createdAt;
    
    private String createdBy;
    
    private Instant updatedAt;
    
    private String updatedBy;
    
    public String getFullName() {
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return username;
    }
}
