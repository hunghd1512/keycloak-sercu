package com.enterprise.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Annotation to check if the current user has a specific client role.
 * 
 * Usage:
 * <pre>
 * @PostMapping("/publish")
 * @HasClientRole(clientId = "document-service", role = "DOC_EDITOR")
 * public ResponseEntity<Void> publishDocument() {
 *     // Only users with DOC_EDITOR role in document-service client
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HasClientRole {
    
    /**
     * The client ID to check the role against.
     */
    String clientId();
    
    /**
     * The role required.
     */
    String role();
}
