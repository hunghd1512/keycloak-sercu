package com.enterprise.security.config;

import com.enterprise.security.permission.CompositePermissionEvaluator;
import com.enterprise.security.permission.PermissionEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.DenyAllPermissionEvaluator;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

/**
 * Configuration for method-level security expression handling.
 */
@Configuration
@RequiredArgsConstructor
public class MethodSecurityConfig {
    
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            List<PermissionEvaluator> evaluators) {
        
        DefaultMethodSecurityExpressionHandler expressionHandler = 
            new DefaultMethodSecurityExpressionHandler();
        
        CompositePermissionEvaluator compositeEvaluator = 
            new CompositePermissionEvaluator(evaluators);
        
        expressionHandler.setPermissionEvaluator(compositeEvaluator);
        
        return expressionHandler;
    }
}
