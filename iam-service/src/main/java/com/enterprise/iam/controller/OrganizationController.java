package com.enterprise.iam.controller;

import com.enterprise.iam.application.dto.*;
import com.enterprise.iam.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
@Slf4j
public class OrganizationController {
    
    private final OrganizationService organizationService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrganizationDto>> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String actorId = jwt.getSubject();
        String actorUsername = jwt.getClaimAsString("preferred_username");
        
        log.info("Creating organization: {} by {}", request.getCode(), actorUsername);
        
        OrganizationDto org = organizationService.createOrganization(request, actorId, actorUsername);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(org, "Organization created successfully"));
    }
    
    @GetMapping("/{orgId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrganizationDto>> getOrganizationById(@PathVariable String orgId) {
        OrganizationDto org = organizationService.getOrganizationById(orgId);
        return ResponseEntity.ok(ApiResponse.success(org));
    }
    
    @GetMapping("/code/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrganizationDto>> getOrganizationByCode(@PathVariable String code) {
        OrganizationDto org = organizationService.getOrganizationByCode(code);
        return ResponseEntity.ok(ApiResponse.success(org));
    }
    
    @GetMapping("/tree")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<OrganizationDto>>> getRootOrganizations() {
        List<OrganizationDto> organizations = organizationService.getRootOrganizations();
        return ResponseEntity.ok(ApiResponse.success(organizations));
    }
    
    @GetMapping("/{orgId}/tree")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrganizationDto>> getOrganizationTree(@PathVariable String orgId) {
        OrganizationDto org = organizationService.getOrganizationTree(orgId);
        return ResponseEntity.ok(ApiResponse.success(org));
    }
    
    @GetMapping("/{orgId}/descendants")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<OrganizationDto>>> getOrganizationDescendants(@PathVariable String orgId) {
        List<OrganizationDto> descendants = organizationService.getOrganizationDescendants(orgId);
        return ResponseEntity.ok(ApiResponse.success(descendants));
    }
    
    @PutMapping("/{orgId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrganizationDto>> updateOrganization(
            @PathVariable String orgId,
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String actorId = jwt.getSubject();
        String actorUsername = jwt.getClaimAsString("preferred_username");
        
        OrganizationDto org = organizationService.updateOrganization(orgId, request, actorId, actorUsername);
        
        return ResponseEntity.ok(ApiResponse.success(org, "Organization updated successfully"));
    }
    
    @DeleteMapping("/{orgId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteOrganization(
            @PathVariable String orgId,
            @AuthenticationPrincipal Jwt jwt) {
        
        String actorId = jwt.getSubject();
        String actorUsername = jwt.getClaimAsString("preferred_username");
        
        organizationService.deleteOrganization(orgId, actorId, actorUsername);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Organization deleted successfully"));
    }
}
