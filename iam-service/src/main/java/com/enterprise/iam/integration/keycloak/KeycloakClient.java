package com.enterprise.iam.integration.keycloak;

import com.enterprise.iam.config.KeycloakProperties;
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
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakClient {
    
    private final WebClient webClient;
    private final KeycloakProperties keycloakProperties;
    private final ObjectMapper objectMapper;
    
    private volatile String accessToken;
    private volatile long tokenExpiresAt;
    
    public String getAccessToken() {
        if (accessToken == null || isTokenExpired()) {
            synchronized (this) {
                if (accessToken == null || isTokenExpired()) {
                    accessToken = obtainAccessToken();
                }
            }
        }
        return accessToken;
    }
    
    private String obtainAccessToken() {
        log.debug("Obtaining admin access token from Keycloak");
        
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", keycloakProperties.getAdmin().getClientId());
        formData.add("client_secret", keycloakProperties.getAdmin().getClientSecret());
        formData.add("username", keycloakProperties.getAdmin().getUsername());
        formData.add("password", keycloakProperties.getAdmin().getPassword());
        
        try {
            String response = webClient.post()
                .uri(keycloakProperties.getTokenEndpoint())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));
            
            JsonNode json = objectMapper.readTree(response);
            String token = json.get("access_token").asText();
            int expiresIn = json.get("expires_in").asInt();
            
            this.tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000) - 60000; // 1 min buffer
            
            log.debug("Admin access token obtained successfully");
            return token;
        } catch (Exception e) {
            log.error("Failed to obtain admin access token", e);
            throw new RuntimeException("Failed to authenticate with Keycloak admin", e);
        }
    }
    
    private boolean isTokenExpired() {
        return System.currentTimeMillis() >= tokenExpiresAt;
    }
    
    public void resetToken() {
        this.accessToken = null;
        this.tokenExpiresAt = 0;
    }
    
    // User Management APIs
    
    public JsonNode createUser(Map<String, Object> userRepresentation) {
        log.debug("Creating user in Keycloak: {}", userRepresentation.get("username"));
        
        String response = webClient.post()
            .uri(keycloakProperties.getUsersEndpoint())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(userRepresentation)
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(30));
        
        log.info("User created successfully in Keycloak");
        return objectMapper.readTree(response != null ? response : "{}");
    }
    
    public Optional<String> getUserIdByUsername(String username) {
        log.debug("Looking up user ID for username: {}", username);
        
        try {
            String response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(keycloakProperties.getUsersEndpoint())
                    .queryParam("username", username)
                    .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));
            
            JsonNode users = objectMapper.readTree(response);
            if (users.isArray() && users.size() > 0) {
                return Optional.of(users.get(0).get("id").asText());
            }
        } catch (Exception e) {
            log.warn("User not found in Keycloak: {}", username);
        }
        
        return Optional.empty();
    }
    
    public JsonNode getUser(String userId) {
        log.debug("Getting user from Keycloak: {}", userId);
        
        try {
            String response = webClient.get()
                .uri(keycloakProperties.getUsersEndpoint() + "/" + userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));
            
            return objectMapper.readTree(response);
        } catch (WebClientResponseException.NotFound e) {
            return null;
        }
    }
    
    public JsonNode updateUser(String userId, Map<String, Object> userRepresentation) {
        log.debug("Updating user in Keycloak: {}", userId);
        
        webClient.put()
            .uri(keycloakProperties.getUsersEndpoint() + "/" + userId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(userRepresentation)
            .retrieve()
            .bodyToMono(Void.class)
            .block(Duration.ofSeconds(30));
        
        log.info("User updated successfully in Keycloak: {}", userId);
        return getUser(userId);
    }
    
    public void deleteUser(String userId) {
        log.debug("Deleting user from Keycloak: {}", userId);
        
        webClient.delete()
            .uri(keycloakProperties.getUsersEndpoint() + "/" + userId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .retrieve()
            .bodyToMono(Void.class)
            .block(Duration.ofSeconds(30));
        
        log.info("User deleted from Keycloak: {}", userId);
    }
    
    public void enableUser(String userId, boolean enabled) {
        log.debug("Setting user enabled status in Keycloak: {} -> {}", userId, enabled);
        
        Map<String, Object> update = Map.of("enabled", enabled);
        
        webClient.put()
            .uri(keycloakProperties.getUsersEndpoint() + "/" + userId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(update)
            .retrieve()
            .bodyToMono(Void.class)
            .block(Duration.ofSeconds(30));
    }
    
    public void sendVerificationEmail(String userId) {
        log.debug("Sending verification email for user: {}", userId);
        
        webClient.put()
            .uri(keycloakProperties.getUsersEndpoint() + "/" + userId + "/send-verify-email")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .retrieve()
            .bodyToMono(Void.class)
            .block(Duration.ofSeconds(30));
    }
    
    public void executeActionsEmail(String userId, String[] actions) {
        log.debug("Executing actions email for user: {}", userId);
        
        webClient.put()
            .uri(uriBuilder -> uriBuilder
                .path(keycloakProperties.getUsersEndpoint() + "/" + userId + "/execute-actions-email")
                .queryParam("redirect_uri", "")
                .build(actions))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(actions)
            .retrieve()
            .bodyToMono(Void.class)
            .block(Duration.ofSeconds(30));
    }
    
    // Role Management APIs
    
    public JsonNode getRealmRoles() {
        log.debug("Getting realm roles from Keycloak");
        
        String response = webClient.get()
            .uri(keycloakProperties.getRolesEndpoint())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(30));
        
        return objectMapper.readTree(response);
    }
    
    public JsonNode getClientRoles(String clientId) {
        log.debug("Getting client roles for client: {}", clientId);
        
        String response = webClient.get()
            .uri(keycloakProperties.getAdminRealmEndpoint() + "/clients/" + clientId + "/roles")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(30));
        
        return objectMapper.readTree(response);
    }
    
    public void assignRealmRoles(String userId, JsonNode roles) {
        log.debug("Assigning realm roles to user: {}", userId);
        
        webClient.post()
            .uri(keycloakProperties.getUsersEndpoint() + "/" + userId + "/role-mappings/realm")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(roles)
            .retrieve()
            .bodyToMono(Void.class)
            .block(Duration.ofSeconds(30));
        
        log.info("Realm roles assigned successfully to user: {}", userId);
    }
    
    public void assignClientRoles(String userId, String clientUuid, JsonNode roles) {
        log.debug("Assigning client roles to user: {}", userId);
        
        webClient.post()
            .uri(keycloakProperties.getUsersEndpoint() + "/" + userId + 
                 "/role-mappings/clients/" + clientUuid)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(roles)
            .retrieve()
            .bodyToMono(Void.class)
            .block(Duration.ofSeconds(30));
        
        log.info("Client roles assigned successfully to user: {}", userId);
    }
    
    public void removeRealmRoles(String userId, JsonNode roles) {
        log.debug("Removing realm roles from user: {}", userId);
        
        webClient.delete()
            .uri(keycloakProperties.getUsersEndpoint() + "/" + userId + "/role-mappings/realm")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(roles)
            .retrieve()
            .bodyToMono(Void.class)
            .block(Duration.ofSeconds(30));
        
        log.info("Realm roles removed successfully from user: {}", userId);
    }
    
    public JsonNode getUserRealmRoles(String userId) {
        log.debug("Getting realm roles for user: {}", userId);
        
        String response = webClient.get()
            .uri(keycloakProperties.getUsersEndpoint() + "/" + userId + "/role-mappings/realm")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(30));
        
        return objectMapper.readTree(response);
    }
    
    // Group Management APIs
    
    public JsonNode getGroups() {
        log.debug("Getting groups from Keycloak");
        
        String response = webClient.get()
            .uri(keycloakProperties.getGroupsEndpoint())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(30));
        
        return objectMapper.readTree(response);
    }
    
    public void addUserToGroup(String userId, String groupId) {
        log.debug("Adding user to group: {} -> {}", userId, groupId);
        
        webClient.put()
            .uri(keycloakProperties.getUsersEndpoint() + "/" + userId + "/groups/" + groupId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .retrieve()
            .bodyToMono(Void.class)
            .block(Duration.ofSeconds(30));
        
        log.info("User added to group successfully");
    }
    
    public void removeUserFromGroup(String userId, String groupId) {
        log.debug("Removing user from group: {} -> {}", userId, groupId);
        
        webClient.delete()
            .uri(keycloakProperties.getUsersEndpoint() + "/" + userId + "/groups/" + groupId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .retrieve()
            .bodyToMono(Void.class)
            .block(Duration.ofSeconds(30));
        
        log.info("User removed from group successfully");
    }
    
    public JsonNode getUserGroups(String userId) {
        log.debug("Getting groups for user: {}", userId);
        
        String response = webClient.get()
            .uri(keycloakProperties.getUsersEndpoint() + "/" + userId + "/groups")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(30));
        
        return objectMapper.readTree(response);
    }
    
    // Client Management APIs
    
    public JsonNode getClients() {
        log.debug("Getting clients from Keycloak");
        
        String response = webClient.get()
            .uri(keycloakProperties.getAdminRealmEndpoint() + "/clients")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(30));
        
        return objectMapper.readTree(response);
    }
    
    public Optional<String> getClientUuid(String clientId) {
        JsonNode clients = getClients();
        for (JsonNode client : clients) {
            if (clientId.equals(client.get("clientId").asText())) {
                return Optional.of(client.get("id").asText());
            }
        }
        return Optional.empty();
    }
}
