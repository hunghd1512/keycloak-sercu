package com.enterprise.iam.config;

import com.enterprise.iam.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * Provides the current auditor (user) for JPA auditing.
 * Uses SecurityContext to get the current authenticated user.
 */
@RequiredArgsConstructor
public class SpringSecurityAuditorAware implements AuditorAware<String> {

    private final SecurityUtils securityUtils;

    @Override
    public Optional<String> getCurrentAuditor() {
        String userId = securityUtils.getCurrentUserId();
        return Optional.ofNullable(userId);
    }
}
