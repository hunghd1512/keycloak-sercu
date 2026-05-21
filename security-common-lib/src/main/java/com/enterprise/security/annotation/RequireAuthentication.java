package com.enterprise.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Annotation to require authentication for a method or class.
 * This is useful for explicitly marking endpoints that require authentication
 * when the global security configuration might not cover them.
 * 
 * Usage:
 * <pre>
 * @GetMapping("/profile")
 * @RequireAuthentication
 * public UserDto getProfile() {
 *     // Requires authentication
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("isAuthenticated()")
public @interface RequireAuthentication {
}
