package com.enterprise.auth.exception;

public class AuthException extends RuntimeException {
    
    private final String code;
    
    public AuthException(String message) {
        super(message);
        this.code = "AUTH_ERROR";
    }
    
    public AuthException(String code, String message) {
        super(message);
        this.code = code;
    }
    
    public AuthException(String message, Throwable cause) {
        super(message, cause);
        this.code = "AUTH_ERROR";
    }
    
    public String getCode() {
        return code;
    }
}
