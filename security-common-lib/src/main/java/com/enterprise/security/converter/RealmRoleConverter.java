package com.enterprise.security.converter;

import com.enterprise.security.jwt.JwtClaimsExtractor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Converter that extracts only realm roles from JWT.
 */
public class RealmRoleConverter implements Converter<Jwt, List<GrantedAuthority>> {
    
    @Override
    public List<GrantedAuthority> convert(Jwt jwt) {
        return JwtClaimsExtractor.extractRealmRoles(jwt).stream()
            .map(role -> (GrantedAuthority) () -> "ROLE_" + role.toUpperCase())
            .collect(Collectors.toList());
    }
}
