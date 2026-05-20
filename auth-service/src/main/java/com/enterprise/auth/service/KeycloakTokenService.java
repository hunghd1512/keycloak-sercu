package com.enterprise.auth.service;

import com.enterprise.auth.config.KeycloakProperties;
import com.enterprise.auth.model.KeycloakTokenResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakTokenService {
    
    private final WebClient webClient;
    private final KeycloakProperties keycloakProperties;
    private final ObjectMapper objectMapper;
    
    public KeycloakTokenResponse authenticate(String username, String password) {
        log.debug("Authenticating user: {}", username);
        
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", keycloakProperties.getClientId());
        formData.add("client_secret", keycloakProperties.getClientSecret());
        formData.add("username", username);
        formData.add("password", password);
        formData.add("scope", "openid profile email");
        
        return callTokenEndpoint(formData);
    }
    
    public KeycloakTokenResponse refreshToken(String refreshToken) {
        log.debug("Refreshing token");
        
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", keycloakProperties.getClientId());
        formData.add("client_secret", keycloakProperties.getClientSecret());
        formData.add("refresh_token", refreshToken);
        
        return callTokenEndpoint(formData);
    }
    
    public KeycloakTokenResponse clientCredentials(String scope) {
        log.debug("Getting client credentials token");
        
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", keycloakProperties.getClientId());
        formData.add("client_secret", keycloakProperties.getClientSecret());
        if (scope != null) {
            formData.add("scope", scope);
        }
        
        return callTokenEndpoint(formData);
    }
    
    public boolean revokeToken(String token) {
        log.debug("Revoking token");
        
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("client_id", keycloakProperties.getClientId());
            formData.add("client_secret", keycloakProperties.getClientSecret());
            formData.add("token", token);
            
            webClient.post()
                .uri(keycloakProperties.getRevokeEndpoint())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
            
            log.info("Token revoked successfully");
            return true;
        } catch (WebClientResponseException e) {
            log.error("Failed to revoke token: {}", e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Error revoking token", e);
            return false;
        }
    }
    
    public Map<String, Object> getUserInfo(String accessToken) {
        log.debug("Getting user info");
        
        try {
            String response = webClient.get()
                .uri(keycloakProperties.getUserinfoEndpoint())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
            
            return objectMapper.readValue(response, Map.class);
        } catch (WebClientResponseException e) {
            log.error("Failed to get user info: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Failed to get user info", e);
        }
    }
    
    public Map<String, Object> getUserProfile(String accessToken, String userId) {
        log.debug("Getting user profile for: {}", userId);
        
        try {
            String response = webClient.get()
                .uri(keycloakProperties.getServerUrl() + "/admin/realms/" + keycloakProperties.getRealm() 
                    + "/users/" + userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
            
            return objectMapper.readValue(response, Map.class);
        } catch (WebClientResponseException e) {
            log.error("Failed to get user profile: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Failed to get user profile", e);
        }
    }
    
    public boolean validateToken(String token) {
        try {
            // Keycloak introspection endpoint
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("client_id", keycloakProperties.getClientId());
            formData.add("client_secret", keycloakProperties.getClientSecret());
            formData.add("token", token);
            
            String response = webClient.post()
                .uri(keycloakProperties.getServerUrl() + "/realms/" + keycloakProperties.getRealm() 
                    + "/protocol/openid-connect/token/introspect")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
            
            JsonNode json = objectMapper.readTree(response);
            return json.has("active") && json.get("active").asBoolean();
        } catch (Exception e) {
            log.error("Error validating token", e);
            return false;
        }
    }
    
    public String getSessionState(String idToken) {
        if (idToken == null) {
            return null;
        }
        
        try {
            // Decode JWT payload (without verification - just parsing)
            String[] parts = idToken.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                JsonNode json = objectMapper.readTree(payload);
                if (json.has("session_state")) {
                    return json.get("session_state").asText();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse session_state from id_token", e);
        }
        return null;
    }
    
    private KeycloakTokenResponse callTokenEndpoint(MultiValueMap<String, String> formData) {
        try {
            String response = webClient.post()
                .uri(keycloakProperties.getTokenEndpoint())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
            
            return parseTokenResponse(response);
        } catch (WebClientResponseException e) {
            log.error("Keycloak token endpoint error: {}", e.getResponseBodyAsString());
            KeycloakTokenResponse errorResponse = new KeycloakTokenResponse();
            try {
                JsonNode json = objectMapper.readTree(e.getResponseBodyAsString());
                errorResponse.setError(json.has("error") ? json.get("error").asText() : "unknown_error");
                errorResponse.setErrorDescription(
                    json.has("error_description") ? json.get("error_description").asText() : e.getMessage()
                );
            } catch (Exception parseError) {
                errorResponse.setError("http_error");
                errorResponse.setErrorDescription(e.getMessage());
            }
            return errorResponse;
        } catch (Exception e) {
            log.error("Error calling Keycloak token endpoint", e);
            KeycloakTokenResponse errorResponse = new KeycloakTokenResponse();
            errorResponse.setError("connection_error");
            errorResponse.setErrorDescription(e.getMessage());
            return errorResponse;
        }
    }
    
    private KeycloakTokenResponse parseTokenResponse(String response) {
        try {
            JsonNode json = objectMapper.readTree(response);
            
            KeycloakTokenResponse tokenResponse = KeycloakTokenResponse.builder()
                .accessToken(json.has("access_token") ? json.get("access_token").asText() : null)
                .refreshToken(json.has("refresh_token") ? json.get("refresh_token").asText() : null)
                .idToken(json.has("id_token") ? json.get("id_token").asText() : null)
                .sessionState(json.has("session_state") ? json.get("session_state").asText() : null)
                .tokenType(json.has("token_type") ? json.get("token_type").asText() : "Bearer")
                .expiresIn(json.has("expires_in") ? json.get("expires_in").asLong() : 300L)
                .refreshExpiresIn(json.has("refresh_expires_in") ? json.get("refresh_expires_in").asLong() : 1800L)
                .issuedAt(json.has("issued_at") ? json.get("issued_at").asLong() : Instant.now().getEpochSecond())
                .scope(json.has("scope") ? json.get("scope").asText() : null)
                .build();
            
            // Parse claims from access_token
            if (json.has("access_token")) {
                tokenResponse.setClaims(parseTokenClaims(json.get("access_token").asText()));
            }
            
            return tokenResponse;
        } catch (Exception e) {
            log.error("Error parsing token response", e);
            KeycloakTokenResponse errorResponse = new KeycloakTokenResponse();
            errorResponse.setError("parse_error");
            errorResponse.setErrorDescription("Failed to parse token response");
            return errorResponse;
        }
    }
    
    private Map<String, Object> parseTokenClaims(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                return objectMapper.readValue(payload, Map.class);
            }
        } catch (Exception e) {
            log.warn("Failed to parse token claims", e);
        }
        return Collections.emptyMap();
    }
}
