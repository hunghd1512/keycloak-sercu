package com.enterprise.iam.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {
    
    private String serverUrl;
    
    private String realm;
    
    private Admin admin = new Admin();
    
    private String adminClientId;
    
    private String tokenEndpoint;
    
    private String adminEndpoint;
    
    private String userEndpoint;
    
    private String roleEndpoint;
    
    private String groupEndpoint;
    
    public String getTokenEndpoint() {
        if (tokenEndpoint != null) {
            return tokenEndpoint;
        }
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }
    
    public String getAdminRealmEndpoint() {
        if (adminEndpoint != null) {
            return adminEndpoint;
        }
        return serverUrl + "/admin/realms/" + realm;
    }
    
    public String getUsersEndpoint() {
        return getAdminRealmEndpoint() + "/users";
    }
    
    public String getRolesEndpoint() {
        return getAdminRealmEndpoint() + "/roles";
    }
    
    public String getGroupsEndpoint() {
        return getAdminRealmEndpoint() + "/groups";
    }
    
    @Data
    public static class Admin {
        private String clientId = "admin-cli";
        private String clientSecret;
        private String username = "admin";
        private String password = "admin";
    }
}
