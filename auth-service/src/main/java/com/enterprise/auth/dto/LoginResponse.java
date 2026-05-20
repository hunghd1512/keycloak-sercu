package com.enterprise.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    
    private String accessToken;
    
    private String refreshToken;
    
    private String tokenType;
    
    private Long expiresIn;
    
    private Long expiresAt;
    
    private String sessionId;
    
    private UserInfo user;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String userId;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String displayName;
    }
}
