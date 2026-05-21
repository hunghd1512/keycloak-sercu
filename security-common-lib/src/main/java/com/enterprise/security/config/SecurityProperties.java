package com.enterprise.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {
    
    private boolean enabled = true;
    
    private Jwt jwt = new Jwt();
    
    private FilterChain filterChain = new FilterChain();
    
    private Annotations annotations = new Annotations();
    
    @Data
    public static class Jwt {
        private boolean enabled = true;
        private String issuerUri;
        private String audience;
        private long clockSkewSeconds = 60;
        private String jwkSetUri;
    }
    
    @Data
    public static class FilterChain {
        private boolean enabled = true;
        private boolean csrfEnabled = false;
        private boolean corsEnabled = true;
        private String sessionCreationPolicy = "STATELESS";
    }
    
    @Data
    public static class Annotations {
        private boolean enabled = true;
        private boolean prePostEnabled = true;
    }
}
