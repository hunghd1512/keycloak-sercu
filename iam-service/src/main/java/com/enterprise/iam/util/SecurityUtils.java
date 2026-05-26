package com.enterprise.iam.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    public String getCurrentUserId() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getSubject() : null;
    }

    public String getCurrentUsername() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getClaimAsString("preferred_username") : null;
    }

    public String getCurrentUserEmail() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getClaimAsString("email") : null;
    }

    public Jwt getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}
