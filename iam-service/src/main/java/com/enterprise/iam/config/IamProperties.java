package com.enterprise.iam.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "iam")
public class IamProperties {
    
    private Cache cache = new Cache();
    
    private Sync sync = new Sync();
    
    private Policy policy = new Policy();
    
    @Data
    public static class Cache {
        private boolean enabled = true;
        
        private Duration defaultTtl = Duration.ofMinutes(15);
        
        private Duration userTtl = Duration.ofMinutes(5);
        
        private Duration roleTtl = Duration.ofMinutes(10);
        
        private Duration orgTtl = Duration.ofMinutes(30);
    }
    
    @Data
    public static class Sync {
        private boolean enabled = true;
        
        private int batchSize = 100;
    }
    
    @Data
    public static class Policy {
        private int maxSessionPerUser = 3;
        
        private String defaultUserRole = "USER";
        
        private boolean requireEmailVerification = true;
    }
}
