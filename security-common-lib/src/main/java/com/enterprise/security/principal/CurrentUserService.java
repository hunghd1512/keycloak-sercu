package com.enterprise.security.principal;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Service for accessing the current authenticated user.
 * Provides convenient methods to extract user information from security context.
 */
@Component
public class CurrentUserService {
    
    public EnterpriseUserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
            return null;
        }
        
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return extractPrincipal(jwtAuth);
        }
        
        Object principal = authentication.getPrincipal();
        
        if (principal instanceof EnterpriseUserPrincipal) {
            return (EnterpriseUserPrincipal) principal;
        }
        
        return null;
    }
    
    public String getCurrentUserId() {
        EnterpriseUserPrincipal user = getCurrentUser();
        return user != null ? user.getId() : null;
    }
    
    public String getCurrentUsername() {
        EnterpriseUserPrincipal user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }
    
    public String getCurrentEmail() {
        EnterpriseUserPrincipal user = getCurrentUser();
        return user != null ? user.getEmail() : null;
    }
    
    public EnterpriseUserPrincipal requireCurrentUser() {
        EnterpriseUserPrincipal user = getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("No authenticated user found in security context");
        }
        return user;
    }
    
    public String requireCurrentUserId() {
        return requireCurrentUser().getId();
    }
    
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
    
    public boolean hasRole(String role) {
        EnterpriseUserPrincipal user = getCurrentUser();
        return user != null && user.hasRole(role);
    }
    
    public boolean hasAnyRole(String... roles) {
        EnterpriseUserPrincipal user = getCurrentUser();
        return user != null && user.hasAnyRole(roles);
    }
    
    public boolean hasAuthority(String authority) {
        EnterpriseUserPrincipal user = getCurrentUser();
        return user != null && user.hasAuthority(authority);
    }
    
    public Jwt getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        
        return null;
    }
    
    private EnterpriseUserPrincipal extractPrincipal(JwtAuthenticationToken jwtAuth) {
        Jwt jwt = jwtAuth.getToken();
        
        Object existingPrincipal = jwtAuth.getPrincipal();
        if (existingPrincipal instanceof EnterpriseUserPrincipal) {
            return (EnterpriseUserPrincipal) existingPrincipal;
        }
        
        return EnterpriseUserPrincipal.fromJwt(jwt);
    }
}
