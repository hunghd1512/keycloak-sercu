package com.enterprise.security.exception;

/**
 * Exception thrown when user lacks required permission.
 */
public class InsufficientPermissionException extends SecurityException {
    
    public InsufficientPermissionException(String message) {
        super(message);
    }
    
    public InsufficientPermissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
