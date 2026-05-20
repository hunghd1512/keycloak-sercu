package com.enterprise.auth.exception;

public class InvalidCredentialsException extends AuthException {
    
    public InvalidCredentialsException(String message) {
        super("INVALID_CREDENTIALS", message);
    }
}
