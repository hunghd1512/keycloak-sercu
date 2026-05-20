package com.enterprise.auth.exception;

public class SessionNotFoundException extends AuthException {
    
    public SessionNotFoundException(String message) {
        super("SESSION_NOT_FOUND", message);
    }
    
    public SessionNotFoundException(String message, Throwable cause) {
        super("SESSION_NOT_FOUND", message, cause);
    }
}
