package com.enterprise.auth.service;

import com.enterprise.auth.config.AuthProperties;
import com.enterprise.auth.dto.LoginRequest;
import com.enterprise.auth.dto.LoginResponse;
import com.enterprise.auth.dto.UserInfoResponse;
import com.enterprise.auth.exception.AuthException;
import com.enterprise.auth.exception.InvalidCredentialsException;
import com.enterprise.auth.exception.SessionLimitExceededException;
import com.enterprise.auth.exception.TokenExpiredException;
import com.enterprise.auth.model.KeycloakTokenResponse;
import com.enterprise.auth.model.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final KeycloakTokenService keycloakTokenService;
    private final SessionService sessionService;
    private final AuthProperties authProperties;
    
    public LoginResponse login(LoginRequest request, String deviceId, String deviceName, 
                                String userAgent, String ipAddress) {
        log.info("Login attempt for user: {}", request.getUsername());
        
        // 1. Authenticate with Keycloak
        KeycloakTokenResponse tokenResponse = keycloakTokenService.authenticate(
            request.getUsername(), 
            request.getPassword()
        );
        
        if (!tokenResponse.isSuccess()) {
            log.warn("Keycloak authentication failed: {}", tokenResponse.getErrorDescription());
            throw new InvalidCredentialsException(
                tokenResponse.getErrorDescription() != null 
                    ? tokenResponse.getErrorDescription() 
                    : "Invalid credentials"
            );
        }
        
        String userId = extractUserId(tokenResponse.getAccessToken());
        
        // 2. Check concurrent session limit
        if (sessionService.hasMaxSessionsReached(userId)) {
            int currentSessions = sessionService.countUserSessions(userId);
            int maxSessions = authProperties.getSession().getMaxConcurrentSessions();
            log.warn("Session limit exceeded for user: {}, current: {}, max: {}", 
                    userId, currentSessions, maxSessions);
            throw new SessionLimitExceededException(userId, currentSessions, maxSessions);
        }
        
        // 3. Create session
        String sessionId = sessionService.createSession(
            tokenResponse,
            deviceId,
            deviceName,
            userAgent,
            ipAddress
        );
        
        log.info("Login successful for user: {}, sessionId: {}", request.getUsername(), sessionId);
        
        // 4. Build response
        return buildLoginResponse(tokenResponse, sessionId);
    }
    
    public LoginResponse refresh(String sessionId) {
        log.debug("Refreshing session: {}", sessionId);
        
        // 1. Get current session
        Session session = sessionService.getSession(sessionId);
        
        if (session.getRefreshToken() == null) {
            throw new AuthException("No refresh token available");
        }
        
        // 2. Call Keycloak to refresh tokens
        KeycloakTokenResponse newTokens = keycloakTokenService.refreshToken(
            session.getRefreshToken()
        );
        
        if (!newTokens.isSuccess()) {
            if ("invalid_grant".equals(newTokens.getError())) {
                log.warn("Refresh token expired for session: {}", sessionId);
                sessionService.invalidateSession(sessionId);
                throw new TokenExpiredException("Refresh token has expired");
            }
            throw new AuthException("REFRESH_FAILED", newTokens.getErrorDescription());
        }
        
        // 3. Update session with new tokens
        sessionService.updateSession(sessionId, newTokens);
        
        log.debug("Session refreshed successfully: {}", sessionId);
        
        return LoginResponse.builder()
            .accessToken(newTokens.getAccessToken())
            .tokenType(newTokens.getTokenType())
            .expiresIn(newTokens.getExpiresIn())
            .expiresAt(Instant.now().plusSeconds(newTokens.getExpiresIn()).toEpochMilli())
            .sessionId(sessionId)
            .build();
    }
    
    public void logout(String sessionId) {
        log.info("Logout request for session: {}", sessionId);
        
        Session session = null;
        try {
            session = sessionService.getSession(sessionId);
        } catch (Exception e) {
            log.debug("Session not found for logout: {}", sessionId);
            return;
        }
        
        // 1. Revoke token in Keycloak
        if (session.getRefreshToken() != null) {
            try {
                keycloakTokenService.revokeToken(session.getRefreshToken());
                log.debug("Token revoked in Keycloak for session: {}", sessionId);
            } catch (Exception e) {
                log.warn("Failed to revoke token in Keycloak: {}", e.getMessage());
            }
        }
        
        // 2. Invalidate session in Redis
        sessionService.invalidateSession(sessionId);
        
        log.info("Logout successful for session: {}", sessionId);
    }
    
    public void logoutAllSessions(String userId) {
        log.info("Logout all sessions for user: {}", userId);
        
        List<Session> sessions = sessionService.getUserSessions(userId);
        
        for (Session session : sessions) {
            try {
                if (session.getRefreshToken() != null) {
                    keycloakTokenService.revokeToken(session.getRefreshToken());
                }
            } catch (Exception e) {
                log.warn("Failed to revoke token for session {}: {}", session.getId(), e.getMessage());
            }
        }
        
        sessionService.invalidateAllUserSessions(userId);
        
        log.info("All sessions invalidated for user: {}", userId);
    }
    
    public UserInfoResponse getUserInfo(String sessionId) {
        log.debug("Getting user info for session: {}", sessionId);
        
        Session session = sessionService.getSession(sessionId);
        
        return UserInfoResponse.builder()
            .userId(session.getUserId())
            .username(session.getUsername())
            .email(session.getEmail())
            .sessionId(session.getId())
            .expiresAt(session.getExpiresAt().toEpochMilli())
            .roles(session.getRoles())
            .build();
    }
    
    public List<UserInfoResponse> getActiveSessions(String userId) {
        log.debug("Getting active sessions for user: {}", userId);
        
        return sessionService.getUserSessions(userId)
            .stream()
            .map(session -> UserInfoResponse.builder()
                .sessionId(session.getId())
                .userId(session.getUserId())
                .username(session.getUsername())
                .deviceId(session.getDeviceId())
                .deviceName(session.getDeviceName())
                .userAgent(session.getUserAgent())
                .ipAddress(session.getIpAddress())
                .createdAt(session.getCreatedAt())
                .lastAccessedAt(session.getLastAccessedAt())
                .expiresAt(session.getExpiresAt().toEpochMilli())
                .build())
            .toList();
    }
    
    public void revokeSession(String userId, String sessionId) {
        log.info("Revoking session {} for user {}", sessionId, userId);
        
        Session session = sessionService.getSession(sessionId);
        
        if (!session.getUserId().equals(userId)) {
            throw new AuthException("Cannot revoke session of another user");
        }
        
        // Revoke token in Keycloak
        if (session.getRefreshToken() != null) {
            keycloakTokenService.revokeToken(session.getRefreshToken());
        }
        
        // Invalidate session
        sessionService.invalidateSession(sessionId);
        
        log.info("Session revoked: {}", sessionId);
    }
    
    private String extractUserId(String accessToken) {
        if (accessToken == null) {
            return null;
        }
        
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                // Simple JSON parsing for "sub" claim
                int subIndex = payload.indexOf("\"sub\"");
                if (subIndex >= 0) {
                    int colonIndex = payload.indexOf(':', subIndex);
                    int quoteIndex = payload.indexOf('"', colonIndex + 1);
                    int endQuoteIndex = payload.indexOf('"', quoteIndex + 1);
                    if (colonIndex > 0 && quoteIndex > 0 && endQuoteIndex > quoteIndex) {
                        return payload.substring(quoteIndex + 1, endQuoteIndex);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract user ID from token: {}", e.getMessage());
        }
        return null;
    }
    
    private LoginResponse buildLoginResponse(KeycloakTokenResponse tokenResponse, String sessionId) {
        Session tempSession = sessionService.getSession(sessionId);
        
        return LoginResponse.builder()
            .accessToken(tokenResponse.getAccessToken())
            .tokenType(tokenResponse.getTokenType())
            .expiresIn(tokenResponse.getExpiresIn())
            .expiresAt(Instant.now().plusSeconds(tokenResponse.getExpiresIn()).toEpochMilli())
            .sessionId(sessionId)
            .user(LoginResponse.UserInfo.builder()
                .userId(tempSession.getUserId())
                .username(tempSession.getUsername())
                .email(tempSession.getEmail())
                .build())
            .build();
    }
}
