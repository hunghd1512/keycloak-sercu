package com.enterprise.iam.exception;

public class OrganizationCodeExistsException extends RuntimeException {
    
    public OrganizationCodeExistsException(String message) {
        super(message);
    }
    
    public OrganizationCodeExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
