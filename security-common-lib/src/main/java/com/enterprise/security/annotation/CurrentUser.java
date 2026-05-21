package com.enterprise.security.annotation;

import java.lang.annotation.*;

/**
 * Annotation to inject the current authenticated user into controller method parameters.
 * 
 * Usage:
 * <pre>
 * @GetMapping("/profile")
 * public UserDto getProfile(@CurrentUser EnterpriseUserPrincipal user) {
 *     return userService.getById(user.getId());
 * }
 * </pre>
 */
@Target({ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
    
    /**
     * Whether to throw an exception if no user is authenticated.
     * Default is true.
     */
    boolean required() default true;
}
