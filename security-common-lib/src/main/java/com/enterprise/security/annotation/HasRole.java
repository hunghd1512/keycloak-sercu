package com.enterprise.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Annotation to check if the current user has any of the specified roles.
 * The roles are matched against the realm roles in the JWT token.
 * 
 * Usage:
 * <pre>
 * @PostMapping("/admin-only")
 * @HasRole("ADMIN")
 * public ResponseEntity<Void> adminAction() {
 *     // Only ADMINs can access this
 * }
 * 
 * @GetMapping("/moderator-or-admin")
 * @HasRole({"ADMIN", "MODERATOR"})
 * public ResponseEntity<Void> modOrAdminAction() {
 *     // ADMINs or MODERATORs can access this
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole(toString())")
public @interface HasRole {
    
    /**
     * The roles required to access the method.
     * At least one of these roles must be present.
     */
    String[] value();
}
