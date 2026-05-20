package com.enterprise.auth.exception;

public class TokenExpiredException extends AuthException {
    
    public TokenExpiredException(String message) {
        super("TOKEN_EXPIRED", message);
    }
    
    public TokenExpiredException(String message, Throwable cause) {
        super("TOKEN_EXPIRED", message, cause);
    }
}
