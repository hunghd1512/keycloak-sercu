package com.enterprise.iam.exception;

public class RoleAssignmentForbiddenException extends RuntimeException {
    
    public RoleAssignmentForbiddenException(String message) {
        super(message);
    }
    
    public RoleAssignmentForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
