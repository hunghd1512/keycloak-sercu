package com.enterprise.iam.config;

import com.enterprise.iam.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@RequiredArgsConstructor
public class JpaAuditConfig {

    private final SecurityUtils securityUtils;

    @Bean
    public AuditorAware<String> auditorProvider() {
        return new SpringSecurityAuditorAware(securityUtils);
    }
}
