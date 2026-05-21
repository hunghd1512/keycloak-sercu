package com.enterprise.security.exception;

/**
 * Exception thrown when JWT token is invalid or expired.
 */
public class InvalidTokenException extends SecurityException {
    
    public InvalidTokenException(String message) {
        super(message);
    }
    
    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
