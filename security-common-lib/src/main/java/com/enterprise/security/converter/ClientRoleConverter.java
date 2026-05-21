package com.enterprise.security.converter;

import com.enterprise.security.jwt.JwtClaimsExtractor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converter that extracts client-specific roles from JWT.
 */
public class ClientRoleConverter implements Converter<Jwt, Map<String, List<GrantedAuthority>>> {
    
    @Override
    public Map<String, List<GrantedAuthority>> convert(Jwt jwt) {
        Map<String, List<String>> clientRoles = JwtClaimsExtractor.extractClientRoles(jwt);
        
        Map<String, List<GrantedAuthority>> result = new HashMap<>();
        
        clientRoles.forEach((clientId, roles) -> 
            result.put(clientId, roles.stream()
                .map(role -> (GrantedAuthority) () -> "ROLE_" + role.toUpperCase())
                .collect(Collectors.toList())));
        
        return result;
    }
}
