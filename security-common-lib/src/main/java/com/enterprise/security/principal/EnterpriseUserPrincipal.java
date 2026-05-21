package com.enterprise.security.principal;

import com.enterprise.security.jwt.JwtClaimsExtractor;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.SpringSecurityCoreVersion;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;

/**
 * Enterprise user principal that represents an authenticated user.
 * Contains user information extracted from JWT token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"id"})
@ToString(of = {"id", "username"})
public class EnterpriseUserPrincipal implements Serializable {
    
    @Serial
    private static final long serialVersionUID = SpringSecurityCoreVersion.SERIAL_VERSION_UID;
    
    private String id;
    
    private String username;
    
    private String email;
    
    private String displayName;
    
    private String firstName;
    
    private String lastName;
    
    private String locale;
    
    @Builder.Default
    private Set<String> realmRoles = new HashSet<>();
    
    @Builder.Default
    private Map<String, List<String>> clientRoles = new HashMap<>();
    
    @Builder.Default
    private Set<GrantedAuthority> authorities = new HashSet<>();
    
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();
    
    public static EnterpriseUserPrincipal fromJwt(Jwt jwt) {
        return JwtClaimsExtractor.toUserPrincipal(jwt);
    }
    
    public boolean hasRole(String role) {
        if (realmRoles == null) {
            return false;
        }
        return realmRoles.stream()
            .anyMatch(r -> r.equalsIgnoreCase(role));
    }
    
    public boolean hasAnyRole(String... roles) {
        if (realmRoles == null || roles == null) {
            return false;
        }
        return Arrays.stream(roles)
            .anyMatch(role -> realmRoles.stream()
                .anyMatch(r -> r.equalsIgnoreCase(role)));
    }
    
    public boolean hasAllRoles(String... roles) {
        if (realmRoles == null || roles == null) {
            return false;
        }
        Set<String> roleSet = new HashSet<>(realmRoles);
        return Arrays.stream(roles)
            .allMatch(role -> roleSet.stream()
                .anyMatch(r -> r.equalsIgnoreCase(role)));
    }
    
    public boolean hasClientRole(String clientId, String role) {
        if (clientRoles == null || clientId == null || role == null) {
            return false;
        }
        List<String> roles = clientRoles.get(clientId);
        return roles != null && roles.stream()
            .anyMatch(r -> r.equalsIgnoreCase(role));
    }
    
    public boolean hasAuthority(String authority) {
        if (authorities == null || authority == null) {
            return false;
        }
        return authorities.stream()
            .anyMatch(a -> a.getAuthority().equals(authority));
    }
    
    public boolean hasAnyAuthority(String... authorities) {
        if (this.authorities == null || authorities == null) {
            return false;
        }
        return Arrays.stream(authorities)
            .anyMatch(auth -> this.authorities.stream()
                .anyMatch(a -> a.getAuthority().equals(auth)));
    }
    
    public List<String> getRealmRolesList() {
        return realmRoles != null ? new ArrayList<>(realmRoles) : Collections.emptyList();
    }
    
    public List<String> getClientRolesList(String clientId) {
        if (clientRoles == null || clientId == null) {
            return Collections.emptyList();
        }
        List<String> roles = clientRoles.get(clientId);
        return roles != null ? roles : Collections.emptyList();
    }
    
    public Collection<GrantedAuthority> getAuthorities() {
        return authorities != null ? authorities : Collections.emptySet();
    }
    
    public Object getAttribute(String key) {
        return attributes != null ? attributes.get(key) : null;
    }
    
    public String getStringAttribute(String key) {
        Object value = getAttribute(key);
        return value != null ? value.toString() : null;
    }
}
