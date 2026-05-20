package com.enterprise.iam.application.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {
    
    @Email(message = "Invalid email format")
    private String email;
    
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;
    
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;
    
    private String displayName;
    
    private String avatarUrl;
    
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phoneNumber;
    
    private String title;
    
    private String departmentId;
    
    private String managerId;
    
    private String costCenter;
    
    @Size(max = 100, message = "Location must not exceed 100 characters")
    private String location;
    
    private String description;
    
    private Map<String, String> customAttributes;
}
