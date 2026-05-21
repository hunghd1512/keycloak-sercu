package com.enterprise.security.permission;

import org.springframework.security.core.Authentication;

import java.io.Serializable;

/**
 * Service Provider Interface for custom permission evaluation.
 * Implement this interface in your service to provide domain-specific permission checks.
 * 
 * Usage:
 * 1. Implement this interface in your service
 * 2. Annotate your implementation with @Component
 * 3. Use @HasPermission annotation in your controllers
 * 
 * Example:
 * <pre>
 * @Component
 * public class DocumentPermissionEvaluator implements PermissionEvaluator {
 *     
 *     public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
 *         if (targetDomainObject instanceof Document document) {
 *             return checkDocumentPermission(auth, document, permission);
 *         }
 *         return false;
 *     }
 *     
 *     public boolean hasPermission(Authentication auth, Serializable targetId, 
 *                                String targetType, Object permission) {
 *         if ("document".equals(targetType)) {
 *             Document doc = documentRepository.findById((String) targetId);
 *             return checkDocumentPermission(auth, doc, permission);
 *         }
 *         return false;
 *     }
 * }
 * </pre>
 */
public interface PermissionEvaluator {
    
    /**
     * Check permission for a domain object.
     * 
     * @param authentication The current authentication
     * @param targetDomainObject The domain object to check permission for
     * @param permission The permission to check (e.g., "read", "write", "delete")
     * @return true if permission is granted, false otherwise
     */
    boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission);
    
    /**
     * Check permission by target type and ID.
     * This method is used when the domain object is not loaded yet.
     * 
     * @param authentication The current authentication
     * @param targetId The ID of the target object
     * @param targetType The type of the target (e.g., "document", "order")
     * @param permission The permission to check
     * @return true if permission is granted, false otherwise
     */
    boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission);
    
    /**
     * Check global permission without specific target.
     * This method is used for checking permissions that don't require a specific object instance.
     * 
     * @param authentication The current authentication
     * @param permission The permission to check
     * @return true if permission is granted, false otherwise
     */
    default boolean hasGlobalPermission(Authentication authentication, Object permission) {
        return false;
    }
}
