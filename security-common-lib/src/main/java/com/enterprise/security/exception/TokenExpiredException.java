package com.enterprise.security.exception;

/**
 * Exception thrown when JWT token has expired.
 */
public class TokenExpiredException extends SecurityException {
    
    public TokenExpiredException(String message) {
        super(message);
    }
    
    public TokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
