package com.enterprise.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Annotation to check if the current user has the specified permission.
 * Requires a PermissionEvaluator to be registered in the application context.
 * 
 * Usage:
 * <pre>
 * @DeleteMapping("/{id}")
 * @HasPermission(value = "document", arg = "#id")
 * public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
 *     // Permission check via PermissionEvaluator
 * }
 * 
 * @PostMapping("/approve")
 * @HasPermission({"document", "approve"})
 * public ResponseEntity<Void> approveDocument() {
 *     // Multiple permissions (AND logic)
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasPermission(#arg, #value)")
public @interface HasPermission {
    
    /**
     * The permission value(s) required.
     * Multiple values use AND logic.
     */
    String[] value();
    
    /**
     * The argument to pass to the permission evaluator.
     * Can be a SpEL expression (e.g., "#id", "#document.id").
     */
    String arg() default "";
}
