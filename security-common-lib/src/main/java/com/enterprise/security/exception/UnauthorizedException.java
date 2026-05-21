package com.enterprise.security.exception;

/**
 * Exception thrown when authentication fails.
 */
public class UnauthorizedException extends SecurityException {
    
    public UnauthorizedException(String message) {
        super(message);
    }
    
    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
