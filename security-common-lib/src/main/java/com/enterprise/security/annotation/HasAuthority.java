package com.enterprise.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Annotation to check if the current user has any of the specified authorities.
 * Unlike @HasRole which checks realm roles, this checks for any authority including
 * scopes and client roles.
 * 
 * Usage:
 * <pre>
 * @PostMapping("/publish")
 * @HasAuthority("ROLE_DOC_EDITOR")
 * public ResponseEntity<Void> publish() {
 *     // Only DOC_EDITORs can access this
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyAuthority(toString())")
public @interface HasAuthority {
    
    /**
     * The authorities required to access the method.
     * At least one of these authorities must be present.
     */
    String[] value();
}
