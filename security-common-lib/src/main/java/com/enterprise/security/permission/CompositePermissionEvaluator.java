package com.enterprise.security.permission;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Composite permission evaluator that delegates to all registered PermissionEvaluator implementations.
 * This allows multiple services to provide their own permission checks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompositePermissionEvaluator implements PermissionEvaluator {
    
    private final List<PermissionEvaluator> evaluators;
    
    @Override
    public boolean hasPermission(Authentication authentication, 
                                 Object targetDomainObject, 
                                 Object permission) {
        for (PermissionEvaluator evaluator : evaluators) {
            try {
                if (evaluator.hasPermission(authentication, targetDomainObject, permission)) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("Permission evaluator {} threw exception for {}: {}", 
                    evaluator.getClass().getSimpleName(), targetDomainObject, e.getMessage());
            }
        }
        return false;
    }
    
    @Override
    public boolean hasPermission(Authentication authentication, 
                                 Serializable targetId, 
                                 String targetType, 
                                 Object permission) {
        for (PermissionEvaluator evaluator : evaluators) {
            try {
                if (evaluator.hasPermission(authentication, targetId, targetType, permission)) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("Permission evaluator {} threw exception for {}: {}", 
                    evaluator.getClass().getSimpleName(), targetType, e.getMessage());
            }
        }
        return false;
    }
    
    @Override
    public boolean hasGlobalPermission(Authentication authentication, Object permission) {
        for (PermissionEvaluator evaluator : evaluators) {
            try {
                if (evaluator.hasGlobalPermission(authentication, permission)) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("Permission evaluator {} threw exception for global permission: {}", 
                    evaluator.getClass().getSimpleName(), e.getMessage());
            }
        }
        return false;
    }
    
    /**
     * Check all evaluators for permission (requires all to return true).
     */
    public boolean hasAllPermissions(Authentication authentication,
                                    Object targetDomainObject,
                                    Object... permissions) {
        for (Object permission : permissions) {
            if (!hasPermission(authentication, targetDomainObject, permission)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Get list of evaluators that handle a specific target type.
     */
    public List<PermissionEvaluator> getEvaluatorsForType(String targetType) {
        List<PermissionEvaluator> result = new ArrayList<>();
        for (PermissionEvaluator evaluator : evaluators) {
            if (evaluator.getClass().getSimpleName().toLowerCase().contains(targetType.toLowerCase())) {
                result.add(evaluator);
            }
        }
        return result;
    }
}
