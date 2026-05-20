package com.enterprise.iam.exception;

public class InvalidOrganizationHierarchyException extends RuntimeException {
    
    public InvalidOrganizationHierarchyException(String message) {
        super(message);
    }
    
    public InvalidOrganizationHierarchyException(String message, Throwable cause) {
        super(message, cause);
    }
}
