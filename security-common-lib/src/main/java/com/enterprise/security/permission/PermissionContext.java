package com.enterprise.security.permission;

import lombok.Data;

/**
 * Context object for permission evaluation.
 * Contains information about the current request and target object.
 */
@Data
public class PermissionContext {
    
    private String userId;
    
    private String username;
    
    private Object targetObject;
    
    private String targetType;
    
    private String targetId;
    
    private String permission;
    
    private String action;
    
    private String resource;
    
    private PermissionContext() {
    }
    
    public static PermissionContextBuilder builder() {
        return new PermissionContextBuilder();
    }
    
    public static class PermissionContextBuilder {
        private String userId;
        private String username;
        private Object targetObject;
        private String targetType;
        private String targetId;
        private String permission;
        private String action;
        private String resource;
        
        public PermissionContextBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }
        
        public PermissionContextBuilder username(String username) {
            this.username = username;
            return this;
        }
        
        public PermissionContextBuilder targetObject(Object targetObject) {
            this.targetObject = targetObject;
            return this;
        }
        
        public PermissionContextBuilder targetType(String targetType) {
            this.targetType = targetType;
            return this;
        }
        
        public PermissionContextBuilder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }
        
        public PermissionContextBuilder permission(String permission) {
            this.permission = permission;
            return this;
        }
        
        public PermissionContextBuilder action(String action) {
            this.action = action;
            return this;
        }
        
        public PermissionContextBuilder resource(String resource) {
            this.resource = resource;
            return this;
        }
        
        public PermissionContext build() {
            PermissionContext context = new PermissionContext();
            context.userId = this.userId;
            context.username = this.username;
            context.targetObject = this.targetObject;
            context.targetType = this.targetType;
            context.targetId = this.targetId;
            context.permission = this.permission;
            context.action = this.action;
            context.resource = this.resource;
            return context;
        }
    }
}
