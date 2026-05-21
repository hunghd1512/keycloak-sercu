package com.enterprise.security.util;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for token operations.
 */
public final class TokenUtils {
    
    private TokenUtils() {
    }
    
    public static String extractToken(String authHeader) {
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
    
    public static boolean isBearerToken(String authHeader) {
        return StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ");
    }
    
    public static List<String> extractRoles(Collection<GrantedAuthority> authorities) {
        if (authorities == null) {
            return List.of();
        }
        
        return authorities.stream()
            .filter(a -> a.getAuthority().startsWith("ROLE_"))
            .map(a -> a.getAuthority().substring(5))
            .collect(Collectors.toList());
    }
    
    public static List<String> extractScopes(Collection<GrantedAuthority> authorities) {
        if (authorities == null) {
            return List.of();
        }
        
        return authorities.stream()
            .filter(a -> a.getAuthority().startsWith("SCOPE_"))
            .map(a -> a.getAuthority().substring(6))
            .collect(Collectors.toList());
    }
}
