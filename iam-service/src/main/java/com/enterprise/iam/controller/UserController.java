package com.enterprise.iam.controller;

import com.enterprise.iam.application.dto.*;
import com.enterprise.iam.domain.entity.User;
import com.enterprise.iam.service.UserService;
import com.enterprise.iam.service.OrganizationService;
import com.enterprise.iam.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    
    private final UserService userService;
    private final OrganizationService organizationService;
    private final SecurityUtils securityUtils;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        
        String actorId = securityUtils.getCurrentUserId();
        String actorUsername = securityUtils.getCurrentUsername();
        
        log.info("Creating user: {} by {}", request.getUsername(), actorUsername);
        
        UserDto user = userService.createUser(request, actorId, actorUsername);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(user, "User created successfully"));
    }
    
    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserDto>> getUserById(@PathVariable String userId) {
        UserDto user = userService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    @GetMapping("/username/{username}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserDto>> getUserByUsername(@PathVariable String username) {
        UserDto user = userService.getUserByUsername(username);
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<UserDto>>> getAllUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<UserDto> users = userService.getAllUsers(pageable);
        
        PagedResponse<UserDto> response = PagedResponse.of(
            users.getContent(),
            users.getNumber(),
            users.getSize(),
            users.getTotalElements()
        );
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<UserDto>>> searchUsers(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<UserDto> users = userService.searchUsers(query, pageable);
        
        PagedResponse<UserDto> response = PagedResponse.of(
            users.getContent(),
            users.getNumber(),
            users.getSize(),
            users.getTotalElements()
        );
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/department/{departmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<UserDto>>> getUsersByDepartment(
            @PathVariable String departmentId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<UserDto> users = userService.getUsersByDepartment(departmentId, pageable);
        
        PagedResponse<UserDto> response = PagedResponse.of(
            users.getContent(),
            users.getNumber(),
            users.getSize(),
            users.getTotalElements()
        );
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @PutMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRequest request) {
        
        String actorId = securityUtils.getCurrentUserId();
        String actorUsername = securityUtils.getCurrentUsername();
        
        UserDto user = userService.updateUser(userId, request, actorId, actorUsername);
        
        return ResponseEntity.ok(ApiResponse.success(user, "User updated successfully"));
    }
    
    @PostMapping("/{userId}/disable")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> disableUser(@PathVariable String userId) {
        
        String actorId = securityUtils.getCurrentUserId();
        String actorUsername = securityUtils.getCurrentUsername();
        
        userService.disableUser(userId, actorId, actorUsername);
        
        return ResponseEntity.ok(ApiResponse.success(null, "User disabled successfully"));
    }
    
    @PostMapping("/{userId}/enable")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> enableUser(@PathVariable String userId) {
        
        String actorId = securityUtils.getCurrentUserId();
        String actorUsername = securityUtils.getCurrentUsername();
        
        userService.enableUser(userId, actorId, actorUsername);
        
        return ResponseEntity.ok(ApiResponse.success(null, "User enabled successfully"));
    }
    
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String userId) {
        
        String actorId = securityUtils.getCurrentUserId();
        String actorUsername = securityUtils.getCurrentUsername();
        
        userService.deleteUser(userId, actorId, actorUsername);
        
        return ResponseEntity.ok(ApiResponse.success(null, "User deleted successfully"));
    }
    
    @GetMapping("/{userId}/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<RoleDto>>> getUserRoles(@PathVariable String userId) {
        List<RoleDto> roles = userService.getUserRoles(userId);
        return ResponseEntity.ok(ApiResponse.success(roles));
    }
    
    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> assignRoles(
            @PathVariable String userId,
            @Valid @RequestBody AssignRolesRequest request) {
        
        String actorId = securityUtils.getCurrentUserId();
        String actorUsername = securityUtils.getCurrentUsername();
        
        User user = userService.getUserEntityById(userId);
        
        List<String> roleNames = request.getRoles().stream()
            .map(AssignRolesRequest.RoleAssignment::getRoleName)
            .toList();
        
        userService.assignRolesToUser(user, roleNames, actorId, actorUsername);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Roles assigned successfully"));
    }
    
    @DeleteMapping("/{userId}/roles/{roleName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> revokeRole(
            @PathVariable String userId,
            @PathVariable String roleName) {
        
        String actorId = securityUtils.getCurrentUserId();
        String actorUsername = securityUtils.getCurrentUsername();
        
        userService.revokeRoleFromUser(userId, roleName, actorId, actorUsername);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Role revoked successfully"));
    }
    
    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(@PathVariable String userId) {
        userService.requestPasswordReset(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset email sent"));
    }
}
