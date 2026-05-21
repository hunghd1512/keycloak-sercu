package com.enterprise.security.util;

import com.enterprise.security.principal.EnterpriseUserPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Test utilities for security testing.
 * Provides mock JWT creation and authentication helpers.
 */
public final class SecurityTestUtils {
    
    private SecurityTestUtils() {
    }
    
    /**
     * Create a mock JWT with basic claims.
     */
    public static Jwt createMockJwt(String userId, String username) {
        return createMockJwt(userId, username, List.of());
    }
    
    /**
     * Create a mock JWT with roles.
     */
    public static Jwt createMockJwt(String userId, String username, List<String> roles) {
        return createMockJwt(userId, username, username + "@example.com", "Test User", roles, Map.of());
    }
    
    /**
     * Create a mock JWT with full customization.
     */
    public static Jwt createMockJwt(String userId, String username, String email, 
                                   String displayName, List<String> roles,
                                   Map<String, Object> additionalClaims) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "RS256");
        headers.put("typ", "JWT");
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("preferred_username", username);
        claims.put("email", email);
        claims.put("name", displayName);
        claims.put("realm_access", Map.of("roles", new ArrayList<>(roles)));
        claims.put("iss", "https://keycloak.example.com/realms/enterprise");
        claims.put("aud", List.of("test-service"));
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());
        claims.put("auth_time", Instant.now().getEpochSecond());
        
        claims.putAll(additionalClaims);
        
        return new Jwt(
            "mock-token-" + UUID.randomUUID(),
            Instant.now(),
            Instant.now().plusSeconds(3600),
            headers,
            claims
        );
    }
    
    /**
     * Create mock authorities from roles.
     */
    public static Collection<GrantedAuthority> createAuthorities(String... roles) {
        return Arrays.stream(roles)
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            .collect(Collectors.toList());
    }
    
    /**
     * Create mock authorities from scopes.
     */
    public static Collection<GrantedAuthority> createScopeAuthorities(String... scopes) {
        return Arrays.stream(scopes)
            .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope.toUpperCase()))
            .collect(Collectors.toList());
    }
    
    /**
     * Create a mock EnterpriseUserPrincipal.
     */
    public static EnterpriseUserPrincipal createMockPrincipal(String userId, String username) {
        return createMockPrincipal(userId, username, username + "@example.com", "Test User", List.of());
    }
    
    /**
     * Create a mock EnterpriseUserPrincipal with roles.
     */
    public static EnterpriseUserPrincipal createMockPrincipal(String userId, String username, 
                                                           String email, String displayName,
                                                           List<String> roles) {
        return EnterpriseUserPrincipal.builder()
            .id(userId)
            .username(username)
            .email(email)
            .displayName(displayName)
            .realmRoles(new HashSet<>(roles))
            .authorities(createAuthorities(roles.toArray(new String[0])))
            .attributes(Map.of(
                "sub", userId,
                "preferred_username", username,
                "email", email
            ))
            .build();
    }
    
    /**
     * Create a client-scoped JWT.
     */
    public static Jwt createClientScopedJwt(String userId, String username, 
                                           Map<String, List<String>> clientRoles) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "RS256");
        headers.put("typ", "JWT");
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("preferred_username", username);
        claims.put("resource_access", clientRoles);
        claims.put("iss", "https://keycloak.example.com/realms/enterprise");
        claims.put("aud", List.of("test-service"));
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());
        
        return new Jwt(
            "mock-token-" + UUID.randomUUID(),
            Instant.now(),
            Instant.now().plusSeconds(3600),
            headers,
            claims
        );
    }
}
