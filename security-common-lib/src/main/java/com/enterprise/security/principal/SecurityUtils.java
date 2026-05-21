package com.enterprise.security.principal;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Utility class for security-related operations.
 */
@Component
public class SecurityUtils {
    
    public static Authentication getCurrentAuthentication() {
        return org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication();
    }
    
    public static boolean isAuthenticated() {
        Authentication authentication = getCurrentAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
    
    public static String getCurrentUserId() {
        Authentication authentication = getCurrentAuthentication();
        
        if (authentication == null) {
            return null;
        }
        
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        
        return authentication.getName();
    }
    
    public static Collection<GrantedAuthority> getCurrentAuthorities() {
        Authentication authentication = getCurrentAuthentication();
        
        if (authentication == null) {
            return null;
        }
        
        return authentication.getAuthorities();
    }
    
    public static boolean hasRole(String role) {
        Collection<GrantedAuthority> authorities = getCurrentAuthorities();
        
        if (authorities == null || role == null) {
            return false;
        }
        
        String roleAuthority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        
        return authorities.stream()
            .anyMatch(a -> a.getAuthority().equalsIgnoreCase(roleAuthority));
    }
    
    public static boolean hasAnyRole(String... roles) {
        return Arrays.stream(roles).anyMatch(SecurityUtils::hasRole);
    }
    
    public static boolean hasAuthority(String authority) {
        Collection<GrantedAuthority> authorities = getCurrentAuthorities();
        
        if (authorities == null || authority == null) {
            return false;
        }
        
        return authorities.stream()
            .anyMatch(a -> a.getAuthority().equals(authority));
    }
    
    public static boolean hasAnyAuthority(String... authorities) {
        return Arrays.stream(authorities).anyMatch(SecurityUtils::hasAuthority);
    }
    
    public static boolean hasAllAuthorities(String... authorities) {
        return Arrays.stream(authorities).allMatch(SecurityUtils::hasAuthority);
    }
    
    public static String extractTokenFromRequest(String authHeader) {
        if (authHeader == null || authHeader.isEmpty()) {
            return null;
        }
        
        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        
        return authHeader;
    }
    
    public static String getTokenValue(String authHeader) {
        return extractTokenFromRequest(authHeader);
    }
    
    public static Collection<String> getRolesFromAuthorities(Collection<GrantedAuthority> authorities) {
        if (authorities == null) {
            return java.util.Collections.emptyList();
        }
        
        return authorities.stream()
            .filter(a -> a.getAuthority().startsWith("ROLE_"))
            .map(a -> a.getAuthority().substring(5))
            .collect(Collectors.toList());
    }
}
