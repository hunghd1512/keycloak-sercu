package com.enterprise.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    
    private Session session = new Session();
    
    private Cookie cookie = new Cookie();
    
    private Token token = new Token();
    
    @Data
    public static class Session {
        private Duration timeout = Duration.ofHours(8);
        
        private int maxConcurrentSessions = 3;
        
        private String prefix = "auth:sessions:";
        
        private String userIndexPrefix = "auth:user-sessions:";
    }
    
    @Data
    public static class Cookie {
        private String name = "AUTH_SESSION_ID";
        
        private boolean httpOnly = true;
        
        private boolean secure = true;
        
        private String sameSite = "Strict";
        
        private Duration maxAge = Duration.ofDays(7);
        
        private String path = "/auth";
    }
    
    @Data
    public static class Token {
        private String accessTokenHeader = "X-Access-Token";
        
        private boolean includeInResponse = true;
    }
}
