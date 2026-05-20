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
public class CreateUserRequest {
    
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, numbers, dots, underscores and hyphens")
    private String username;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;
    
    private String displayName;
    
    private String phoneNumber;
    
    private String employeeId;
    
    @Size(max = 100, message = "Title must not exceed 100 characters")
    private String title;
    
    private String departmentId;
    
    private String managerId;
    
    private String costCenter;
    
    @Size(max = 100, message = "Location must not exceed 100 characters")
    private String location;
    
    private Instant hireDate;
    
    private String description;
    
    private Map<String, String> customAttributes;
    
    private List<String> roles;
    
    private List<String> groups;
    
    private Boolean sendEmail = true;
    
    private Boolean mustChangePassword = false;
}
