package com.enterprise.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {
    
    private String serverUrl;
    
    private String realm;
    
    private String clientId;
    
    private String clientSecret;
    
    private Admin admin = new Admin();
    
    private String tokenEndpoint;
    
    private String authorizationEndpoint;
    
    private String userinfoEndpoint;
    
    private String jwksUri;
    
    private String issuer;
    
    public String getTokenEndpoint() {
        if (tokenEndpoint != null) {
            return tokenEndpoint;
        }
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }
    
    public String getAuthorizationEndpoint() {
        if (authorizationEndpoint != null) {
            return authorizationEndpoint;
        }
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/auth";
    }
    
    public String getUserinfoEndpoint() {
        if (userinfoEndpoint != null) {
            return userinfoEndpoint;
        }
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/userinfo";
    }
    
    public String getJwksUri() {
        if (jwksUri != null) {
            return jwksUri;
        }
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
    }
    
    public String getIssuer() {
        if (issuer != null) {
            return issuer;
        }
        return serverUrl + "/realms/" + realm;
    }
    
    public String getLogoutEndpoint() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/logout";
    }
    
    public String getRevokeEndpoint() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }
    
    @Data
    public static class Admin {
        private String username;
        private String password;
    }
}
