package com.enterprise.security.jwt;

import com.enterprise.security.principal.EnterpriseUserPrincipal;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@UtilityClass
public class JwtClaimsExtractor {
    
    public static final String REALM_ACCESS_CLAIM = "realm_access";
    public static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    public static final String ROLES_KEY = "roles";
    public static final String SCOPE_CLAIM = "scope";
    public static final String PREFERRED_USERNAME_CLAIM = "preferred_username";
    public static final String EMAIL_CLAIM = "email";
    public static final String NAME_CLAIM = "name";
    public static final String SUBJECT_CLAIM = "sub";
    public static final String GIVEN_NAME_CLAIM = "given_name";
    public static final String FAMILY_NAME_CLAIM = "family_name";
    public static final String LOCALE_CLAIM = "locale";
    public static final String AZP_CLAIM = "azp";
    public static final String SESSION_STATE_CLAIM = "session_state";
    public static final String ACR_CLAIM = "acr";
    public static final String AUTH_TIME_CLAIM = "auth_time";
    public static final String AT_HASH_CLAIM = "at_hash";
    public static final String ACTIVE_CLAIM = "active";
    public static final String TYPE_CLAIM = "typ";
    
    public static String extractSubject(Jwt jwt) {
        return jwt.getSubject();
    }
    
    public static String extractUsername(Jwt jwt) {
        return jwt.getClaimAsString(PREFERRED_USERNAME_CLAIM);
    }
    
    public static String extractEmail(Jwt jwt) {
        return jwt.getClaimAsString(EMAIL_CLAIM);
    }
    
    public static String extractDisplayName(Jwt jwt) {
        String name = jwt.getClaimAsString(NAME_CLAIM);
        if (name != null) {
            return name;
        }
        
        String givenName = jwt.getClaimAsString(GIVEN_NAME_CLAIM);
        String familyName = jwt.getClaimAsString(FAMILY_NAME_CLAIM);
        
        if (givenName != null && familyName != null) {
            return givenName + " " + familyName;
        }
        
        return extractUsername(jwt);
    }
    
    public static String extractFirstName(Jwt jwt) {
        return jwt.getClaimAsString(GIVEN_NAME_CLAIM);
    }
    
    public static String extractLastName(Jwt jwt) {
        return jwt.getClaimAsString(FAMILY_NAME_CLAIM);
    }
    
    public static String extractLocale(Jwt jwt) {
        return jwt.getClaimAsString(LOCALE_CLAIM);
    }
    
    public static String extractAuthorizedParty(Jwt jwt) {
        return jwt.getClaimAsString(AZP_CLAIM);
    }
    
    public static Instant extractAuthTime(Jwt jwt) {
        Long authTime = jwt.getClaim(AUTH_TIME_CLAIM);
        return authTime != null ? Instant.ofEpochSecond(authTime) : null;
    }
    
    public static List<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(REALM_ACCESS_CLAIM);
        if (realmAccess == null) {
            return Collections.emptyList();
        }
        
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get(ROLES_KEY);
        return roles != null ? roles : Collections.emptyList();
    }
    
    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> extractClientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim(RESOURCE_ACCESS_CLAIM);
        if (resourceAccess == null) {
            return Collections.emptyMap();
        }
        
        Map<String, List<String>> clientRoles = new HashMap<>();
        
        resourceAccess.forEach((clientId, access) -> {
            if (access instanceof Map) {
                Map<String, Object> accessMap = (Map<String, Object>) access;
                List<String> roles = (List<String>) accessMap.get(ROLES_KEY);
                if (roles != null) {
                    clientRoles.put(clientId, roles);
                }
            }
        });
        
        return clientRoles;
    }
    
    public static List<String> extractScopes(Jwt jwt) {
        String scope = jwt.getClaimAsString(SCOPE_CLAIM);
        if (scope == null || scope.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(scope.split(" "));
    }
    
    public static List<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        
        // Extract realm roles
        List<String> realmRoles = extractRealmRoles(jwt);
        authorities.addAll(realmRoles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            .collect(Collectors.toSet()));
        
        // Extract client roles
        Map<String, List<String>> clientRoles = extractClientRoles(jwt);
        clientRoles.forEach((clientId, roles) -> 
            authorities.addAll(roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toSet())));
        
        // Extract scopes
        List<String> scopes = extractScopes(jwt);
        authorities.addAll(scopes.stream()
            .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope.toUpperCase()))
            .collect(Collectors.toSet()));
        
        return new ArrayList<>(authorities);
    }
    
    public static Map<String, Object> extractAllClaims(Jwt jwt) {
        return new HashMap<>(jwt.getClaims());
    }
    
    public static Map<String, String> extractCustomAttributes(Jwt jwt) {
        Map<String, String> attributes = new HashMap<>();
        Map<String, Object> claims = jwt.getClaims();
        
        Set<String> standardClaims = Set.of(
            SUBJECT_CLAIM, PREFERRED_USERNAME_CLAIM, EMAIL_CLAIM, NAME_CLAIM,
            GIVEN_NAME_CLAIM, FAMILY_NAME_CLAIM, LOCALE_CLAIM, AZP_CLAIM,
            SCOPE_CLAIM, REALM_ACCESS_CLAIM, RESOURCE_ACCESS_CLAIM,
            AUTH_TIME_CLAIM, ACR_CLAIM, TYPE_CLAIM, SESSION_STATE_CLAIM,
            "iat", "exp", "nbf", "iss", "aud", "jti"
        );
        
        claims.forEach((key, value) -> {
            if (!standardClaims.contains(key) && value != null) {
                attributes.put(key, value.toString());
            }
        });
        
        return attributes;
    }
    
    public static EnterpriseUserPrincipal toUserPrincipal(Jwt jwt) {
        return EnterpriseUserPrincipal.builder()
            .id(extractSubject(jwt))
            .username(extractUsername(jwt))
            .email(extractEmail(jwt))
            .displayName(extractDisplayName(jwt))
            .firstName(extractFirstName(jwt))
            .lastName(extractLastName(jwt))
            .locale(extractLocale(jwt))
            .realmRoles(extractRealmRoles(jwt))
            .clientRoles(extractClientRoles(jwt))
            .authorities(extractAuthorities(jwt))
            .attributes(extractAllClaims(jwt))
            .build();
    }
}
