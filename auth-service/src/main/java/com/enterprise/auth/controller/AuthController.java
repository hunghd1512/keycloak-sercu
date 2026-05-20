package com.enterprise.auth.controller;

import com.enterprise.auth.config.AuthProperties;
import com.enterprise.auth.dto.*;
import com.enterprise.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    
    private final AuthService authService;
    private final AuthProperties authProperties;
    
    private static final String SESSION_COOKIE_NAME = "AUTH_SESSION_ID";
    
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        
        log.info("Login request received for user: {}", request.getUsername());
        
        // Extract device info
        String deviceId = extractDeviceId(httpRequest);
        String deviceName = extractDeviceName(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = extractIpAddress(httpRequest);
        
        // Perform login
        LoginResponse loginResponse = authService.login(
            request, deviceId, deviceName, userAgent, ipAddress
        );
        
        // Set session cookie
        setSessionCookie(httpResponse, loginResponse.getSessionId());
        
        log.info("Login successful for user: {}, sessionId: {}", 
                 request.getUsername(), loginResponse.getSessionId());
        
        return ResponseEntity.ok(ApiResponse.success(loginResponse, "Login successful"));
    }
    
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionId,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        
        log.info("Logout request received, sessionId: {}", sessionId);
        
        if (sessionId != null) {
            authService.logout(sessionId);
        }
        
        // Clear session cookie
        clearSessionCookie(httpResponse);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Logout successful"));
    }
    
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionId,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        
        log.info("Logout all sessions request received");
        
        if (sessionId != null) {
            // Get userId from session first
            try {
                UserInfoResponse userInfo = authService.getUserInfo(sessionId);
                authService.logoutAllSessions(userInfo.getUserId());
            } catch (Exception e) {
                log.warn("Failed to get user info for logout all: {}", e.getMessage());
            }
        }
        
        // Clear session cookie
        clearSessionCookie(httpResponse);
        
        return ResponseEntity.ok(ApiResponse.success(null, "All sessions logged out"));
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionId,
            HttpServletResponse httpResponse) {
        
        log.debug("Refresh token request received, sessionId: {}", sessionId);
        
        if (sessionId == null || sessionId.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("No session cookie found"));
        }
        
        LoginResponse loginResponse = authService.refresh(sessionId);
        
        // Update session cookie with new expiration
        setSessionCookie(httpResponse, loginResponse.getSessionId());
        
        log.debug("Token refreshed successfully for session: {}", sessionId);
        
        return ResponseEntity.ok(ApiResponse.success(loginResponse, "Token refreshed"));
    }
    
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoResponse>> getCurrentUser(
            @CookieValue(name = SESSION_COOKIE_NAME) String sessionId) {
        
        log.debug("Get current user request, sessionId: {}", sessionId);
        
        UserInfoResponse userInfo = authService.getUserInfo(sessionId);
        
        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }
    
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<UserInfoResponse>>> getActiveSessions(
            @CookieValue(name = SESSION_COOKIE_NAME) String sessionId) {
        
        log.debug("Get active sessions request");
        
        UserInfoResponse currentSession = authService.getUserInfo(sessionId);
        List<UserInfoResponse> sessions = authService.getActiveSessions(currentSession.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }
    
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @CookieValue(name = SESSION_COOKIE_NAME) String sessionId,
            @PathVariable String targetSessionId) {
        
        log.info("Revoke session request: {}", targetSessionId);
        
        UserInfoResponse currentSession = authService.getUserInfo(sessionId);
        authService.revokeSession(currentSession.getUserId(), targetSessionId);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Session revoked"));
    }
    
    @PostMapping("/introspect")
    public ResponseEntity<ApiResponse<SessionIntrospectionResponse>> introspect(
            @CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionId) {
        
        log.debug("Session introspection request");
        
        if (sessionId == null || sessionId.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(
                SessionIntrospectionResponse.builder()
                    .active(false)
                    .build()
            ));
        }
        
        try {
            UserInfoResponse userInfo = authService.getUserInfo(sessionId);
            return ResponseEntity.ok(ApiResponse.success(
                SessionIntrospectionResponse.builder()
                    .active(true)
                    .userId(userInfo.getUserId())
                    .username(userInfo.getUsername())
                    .sessionId(sessionId)
                    .expiresAt(userInfo.getExpiresAt())
                    .build()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(
                SessionIntrospectionResponse.builder()
                    .active(false)
                    .reason("Session not found or expired")
                    .build()
            ));
        }
    }
    
    // Private helper methods
    
    private void setSessionCookie(HttpServletResponse response, String sessionId) {
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE_NAME, sessionId)
            .httpOnly(authProperties.getCookie().isHttpOnly())
            .secure(authProperties.getCookie().isSecure())
            .sameSite(authProperties.getCookie().getSameSite())
            .path("/")
            .maxAge(authProperties.getCookie().getMaxAge().getSeconds())
            .build();
        
        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    private void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(0)
            .build();
        
        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    private String extractDeviceId(HttpServletRequest request) {
        String deviceId = request.getHeader("X-Device-Id");
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = request.getHeader("X-Forwarded-For");
        }
        return deviceId;
    }
    
    private String extractDeviceName(HttpServletRequest request) {
        String deviceName = request.getHeader("X-Device-Name");
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = request.getHeader("User-Agent");
        }
        return deviceName;
    }
    
    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SessionIntrospectionResponse {
        private boolean active;
        private String userId;
        private String username;
        private String sessionId;
        private Long expiresAt;
        private String reason;
    }
}
