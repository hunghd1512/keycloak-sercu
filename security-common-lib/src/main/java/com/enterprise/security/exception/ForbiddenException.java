package com.enterprise.security.exception;

/**
 * Exception thrown when access is denied (user lacks required permissions/roles).
 */
public class ForbiddenException extends SecurityException {
    
    public ForbiddenException(String message) {
        super(message);
    }
    
    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
