package com.enterprise.auth.service;

import com.enterprise.auth.config.AuthProperties;
import com.enterprise.auth.exception.SessionNotFoundException;
import com.enterprise.auth.model.KeycloakTokenResponse;
import com.enterprise.auth.model.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final AuthProperties authProperties;
    
    private static final String SESSION_PREFIX = "auth:sessions:";
    private static final String USER_SESSIONS_PREFIX = "auth:user-sessions:";
    
    public String createSession(KeycloakTokenResponse tokens, String deviceId, String deviceName, 
                                 String userAgent, String ipAddress) {
        String sessionId = generateSessionId();
        
        Session session = Session.builder()
            .id(sessionId)
            .userId(extractUserId(tokens.getAccessToken()))
            .username(extractUsername(tokens.getAccessToken()))
            .email(extractEmail(tokens.getAccessToken()))
            .accessToken(tokens.getAccessToken())
            .refreshToken(tokens.getRefreshToken())
            .idToken(tokens.getIdToken())
            .sessionState(tokens.getSessionState())
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(tokens.getExpiresIn()))
            .lastAccessedAt(Instant.now())
            .deviceId(deviceId)
            .deviceName(deviceName)
            .userAgent(userAgent)
            .ipAddress(ipAddress)
            .roles(extractRoles(tokens.getAccessToken()))
            .active(true)
            .build();
        
        // Save session to Redis
        saveSession(session);
        
        // Index session by user
        indexSessionByUser(session.getUserId(), sessionId);
        
        log.info("Session created for user: {}, sessionId: {}", session.getUsername(), sessionId);
        
        return sessionId;
    }
    
    public Session getSession(String sessionId) {
        String key = SESSION_PREFIX + sessionId;
        Session session = (Session) redisTemplate.opsForValue().get(key);
        
        if (session == null) {
            throw new SessionNotFoundException("Session not found or expired: " + sessionId);
        }
        
        if (!session.isActive()) {
            throw new SessionNotFoundException("Session has been invalidated: " + sessionId);
        }
        
        return session;
    }
    
    public Optional<Session> findSession(String sessionId) {
        try {
            return Optional.of(getSession(sessionId));
        } catch (SessionNotFoundException e) {
            return Optional.empty();
        }
    }
    
    public void updateSession(String sessionId, KeycloakTokenResponse tokens) {
        Session session = getSession(sessionId);
        
        session.setAccessToken(tokens.getAccessToken());
        session.setRefreshToken(tokens.getRefreshToken());
        if (tokens.getIdToken() != null) {
            session.setIdToken(tokens.getIdToken());
        }
        session.setExpiresAt(Instant.now().plusSeconds(tokens.getExpiresIn()));
        session.setLastAccessedAt(Instant.now());
        session.setRoles(extractRoles(tokens.getAccessToken()));
        
        saveSession(session);
        
        log.debug("Session updated: {}", sessionId);
    }
    
    public void invalidateSession(String sessionId) {
        Session session = null;
        try {
            session = getSession(sessionId);
        } catch (SessionNotFoundException e) {
            log.warn("Session not found for invalidation: {}", sessionId);
            return;
        }
        
        // Mark session as inactive
        session.setActive(false);
        
        // Delete session
        String key = SESSION_PREFIX + sessionId;
        redisTemplate.delete(key);
        
        // Remove from user index
        if (session.getUserId() != null) {
            removeSessionFromUserIndex(session.getUserId(), sessionId);
        }
        
        log.info("Session invalidated: {}", sessionId);
    }
    
    public void invalidateAllUserSessions(String userId) {
        Set<String> sessionIds = getUserSessionIds(userId);
        
        for (String sessionId : sessionIds) {
            invalidateSession(sessionId);
        }
        
        // Delete user session index
        redisTemplate.delete(USER_SESSIONS_PREFIX + userId);
        
        log.info("All sessions invalidated for user: {}, count: {}", userId, sessionIds.size());
    }
    
    public List<Session> getUserSessions(String userId) {
        Set<String> sessionIds = getUserSessionIds(userId);
        
        return sessionIds.stream()
            .map(this::findSession)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }
    
    public int countUserSessions(String userId) {
        Long size = redisTemplate.opsForSet().size(USER_SESSIONS_PREFIX + userId);
        return size != null ? size.intValue() : 0;
    }
    
    public boolean hasMaxSessionsReached(String userId) {
        int maxSessions = authProperties.getSession().getMaxConcurrentSessions();
        int currentSessions = countUserSessions(userId);
        return currentSessions >= maxSessions;
    }
    
    public void extendSession(String sessionId) {
        Session session = getSession(sessionId);
        session.setLastAccessedAt(Instant.now());
        saveSession(session);
    }
    
    private void saveSession(Session session) {
        String key = SESSION_PREFIX + session.getId();
        Duration ttl = authProperties.getSession().getTimeout();
        
        redisTemplate.opsForValue().set(key, session, ttl.toSeconds(), TimeUnit.SECONDS);
    }
    
    private void indexSessionByUser(String userId, String sessionId) {
        String userKey = USER_SESSIONS_PREFIX + userId;
        redisTemplate.opsForSet().add(userKey, sessionId);
        redisTemplate.expire(userKey, authProperties.getSession().getTimeout().toSeconds(), TimeUnit.SECONDS);
    }
    
    private void removeSessionFromUserIndex(String userId, String sessionId) {
        String userKey = USER_SESSIONS_PREFIX + userId;
        redisTemplate.opsForSet().remove(userKey, sessionId);
    }
    
    private Set<String> getUserSessionIds(String userId) {
        String userKey = USER_SESSIONS_PREFIX + userId;
        Set<Object> members = redisTemplate.opsForSet().members(userKey);
        
        if (members == null) {
            return Collections.emptySet();
        }
        
        return members.stream()
            .map(Object::toString)
            .collect(Collectors.toSet());
    }
    
    private String generateSessionId() {
        byte[] bytes = new byte[32];
        new Random().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    private String extractUserId(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                // Parse JSON to extract sub claim
                // Simple parsing without full JSON library
                if (payload.contains("\"sub\"")) {
                    int start = payload.indexOf("\"sub\"") + 6;
                    String sub = payload.substring(start).replaceAll("[\":\\s,}]", "");
                    return sub.trim();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract user ID from token", e);
        }
        return null;
    }
    
    private String extractUsername(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                if (payload.contains("\"preferred_username\"")) {
                    int start = payload.indexOf("\"preferred_username\"") + 21;
                    String username = payload.substring(start).split("[\",}]")[0];
                    return username.trim();
                }
                if (payload.contains("\"username\"")) {
                    int start = payload.indexOf("\"username\"") + 11;
                    String username = payload.substring(start).split("[\",}]")[0];
                    return username.trim();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract username from token", e);
        }
        return null;
    }
    
    private String extractEmail(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                if (payload.contains("\"email\"")) {
                    int start = payload.indexOf("\"email\"") + 8;
                    String email = payload.substring(start).split("[\",}]")[0];
                    return email.trim();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract email from token", e);
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private List<String> extractRoles(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                if (payload.contains("\"realm_access\"")) {
                    int realmAccessStart = payload.indexOf("\"realm_access\"");
                    int rolesStart = payload.indexOf("\"roles\"", realmAccessStart);
                    if (rolesStart > 0) {
                        int arrayStart = payload.indexOf("[", rolesStart);
                        int arrayEnd = payload.indexOf("]", arrayStart);
                        String rolesArray = payload.substring(arrayStart + 1, arrayEnd);
                        // Parse role names
                        List<String> roles = new ArrayList<>();
                        for (String role : rolesArray.split("\"")) {
                            if (!role.trim().isEmpty() && !role.trim().equals(",")) {
                                roles.add(role.trim());
                            }
                        }
                        return roles;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract roles from token", e);
        }
        return Collections.emptyList();
    }
}
