package com.enterprise.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeycloakTokenResponse {
    
    private String accessToken;
    
    private String refreshToken;
    
    private String idToken;
    
    private String sessionState;
    
    private String tokenType;
    
    private Long expiresIn;
    
    private Long refreshExpiresIn;
    
    private Long issuedAt;
    
    private Long notBeforePolicy;
    
    private String scope;
    
    private String error;
    
    private String errorDescription;
    
    private Map<String, Object> claims;
    
    public boolean isSuccess() {
        return accessToken != null && error == null;
    }
    
    public boolean hasError() {
        return error != null;
    }
}
