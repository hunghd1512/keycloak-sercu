package com.enterprise.iam.exception;

public class PolicyViolationException extends RuntimeException {
    
    public PolicyViolationException(String message) {
        super(message);
    }
    
    public PolicyViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
