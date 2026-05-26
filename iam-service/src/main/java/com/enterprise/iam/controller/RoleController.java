package com.enterprise.iam.controller;

import com.enterprise.iam.application.dto.*;
import com.enterprise.iam.service.RoleService;
import com.enterprise.iam.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Slf4j
public class RoleController {
    
    private final RoleService roleService;
    private final SecurityUtils securityUtils;
    
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<RoleDto>>> getAllRoles() {
        List<RoleDto> roles = roleService.getAllRoles();
        return ResponseEntity.ok(ApiResponse.success(roles));
    }
    
    @GetMapping("/realm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<RoleDto>>> getRealmRoles() {
        List<RoleDto> roles = roleService.getRealmRoles();
        return ResponseEntity.ok(ApiResponse.success(roles));
    }
    
    @GetMapping("/client/{clientId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<RoleDto>>> getClientRoles(@PathVariable String clientId) {
        List<RoleDto> roles = roleService.getClientRoles(clientId);
        return ResponseEntity.ok(ApiResponse.success(roles));
    }
    
    @GetMapping("/{roleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RoleDto>> getRoleById(@PathVariable String roleId) {
        RoleDto role = roleService.getRoleById(roleId);
        return ResponseEntity.ok(ApiResponse.success(role));
    }
    
    @GetMapping("/name/{name}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RoleDto>> getRoleByName(@PathVariable String name) {
        RoleDto role = roleService.getRoleByName(name);
        return ResponseEntity.ok(ApiResponse.success(role));
    }
    
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RoleDto>> createRole(@Valid @RequestBody RoleDto roleDto) {
        
        String actorId = securityUtils.getCurrentUserId();
        String actorUsername = securityUtils.getCurrentUsername();
        
        log.info("Creating role: {} by {}", roleDto.getName(), actorUsername);
        
        RoleDto role = roleService.createRole(roleDto, actorId, actorUsername);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(role, "Role created successfully"));
    }
    
    @PutMapping("/{roleId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RoleDto>> updateRole(
            @PathVariable String roleId,
            @Valid @RequestBody RoleDto roleDto) {
        
        String actorId = securityUtils.getCurrentUserId();
        String actorUsername = securityUtils.getCurrentUsername();
        
        RoleDto role = roleService.updateRole(roleId, roleDto, actorId, actorUsername);
        
        return ResponseEntity.ok(ApiResponse.success(role, "Role updated successfully"));
    }
    
    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable String roleId) {
        
        String actorId = securityUtils.getCurrentUserId();
        String actorUsername = securityUtils.getCurrentUsername();
        
        roleService.deleteRole(roleId, actorId, actorUsername);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Role deleted successfully"));
    }
    
    @PostMapping("/{roleId}/children/{childRoleId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RoleDto>> addChildRole(
            @PathVariable String roleId,
            @PathVariable String childRoleId) {
        
        String actorId = securityUtils.getCurrentUserId();
        String actorUsername = securityUtils.getCurrentUsername();
        
        RoleDto role = roleService.addChildRole(roleId, childRoleId, actorId, actorUsername);
        
        return ResponseEntity.ok(ApiResponse.success(role, "Child role added successfully"));
    }
}
