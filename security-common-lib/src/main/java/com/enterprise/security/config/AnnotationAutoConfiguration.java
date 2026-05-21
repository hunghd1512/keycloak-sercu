package com.enterprise.security.config;

import com.enterprise.security.resolver.CurrentUserMethodArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Auto-configuration for security annotations support.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnProperty(prefix = "security.annotations", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AnnotationAutoConfiguration implements WebMvcConfigurer {
    
    private final CurrentUserService currentUserService;
    
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserMethodArgumentResolver(currentUserService));
    }
}
