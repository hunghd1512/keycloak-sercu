package com.enterprise.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "security.jwt")
public class JwtDecoderProperties {
    
    private boolean enabled = true;
    
    private String issuerUri;
    
    private String audience;
    
    private Duration clockSkew = Duration.ofSeconds(60);
    
    private String jwkSetUri;
    
    private boolean validateIssuer = true;
    
    private boolean validateAudience = true;
    
    private boolean validateExpiration = true;
    
    private boolean validateNotBefore = false;
    
    private boolean validateIssuedAt = false;
}
