package com.enterprise.security.converter;

import com.enterprise.security.jwt.JwtClaimsExtractor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

/**
 * Converts JWT tokens to Spring Security authentication.
 * Extracts realm roles, client roles, and scopes from Keycloak JWT format.
 */
public class EnterpriseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = JwtClaimsExtractor.extractAuthorities(jwt);
        
        return new JwtAuthenticationToken(
            jwt,
            authorities,
            JwtClaimsExtractor.extractUsername(jwt)
        );
    }
}
