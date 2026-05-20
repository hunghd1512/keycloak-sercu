package com.enterprise.iam.integration.keycloak;

import com.enterprise.iam.application.dto.UserDto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.*;

@UtilityClass
public class KeycloakUserMapper {
    
    public static Map<String, Object> toKeycloakRepresentation(UserDto user) {
        Map<String, Object> representation = new HashMap<>();
        
        representation.put("username", user.getUsername());
        representation.put("email", user.getEmail());
        representation.put("firstName", user.getFirstName());
        representation.put("lastName", user.getLastName());
        representation.put("enabled", user.getEnabled() != null ? user.getEnabled() : true);
        representation.put("emailVerified", user.getEmailVerified() != null ? user.getEmailVerified() : false);
        
        if (user.getDisplayName() != null) {
            representation.put("displayName", user.getDisplayName());
        }
        
        Map<String, List<String>> attributes = new HashMap<>();
        
        if (user.getPhoneNumber() != null) {
            attributes.put("phone_number", Collections.singletonList(user.getPhoneNumber()));
        }
        
        if (user.getEmployeeId() != null) {
            attributes.put("employee_id", Collections.singletonList(user.getEmployeeId()));
        }
        
        if (user.getDepartmentId() != null) {
            attributes.put("department_id", Collections.singletonList(user.getDepartmentId()));
        }
        
        if (user.getTitle() != null) {
            attributes.put("title", Collections.singletonList(user.getTitle()));
        }
        
        if (user.getLocation() != null) {
            attributes.put("location", Collections.singletonList(user.getLocation()));
        }
        
        if (user.getCustomAttributes() != null && !user.getCustomAttributes().isEmpty()) {
            user.getCustomAttributes().forEach((key, value) -> 
                attributes.put(key, Collections.singletonList(value)));
        }
        
        if (!attributes.isEmpty()) {
            representation.put("attributes", attributes);
        }
        
        return representation;
    }
    
    public static UserDto fromKeycloakRepresentation(JsonNode user) {
        return UserDto.builder()
            .keycloakUserId(getTextValue(user, "id"))
            .username(getTextValue(user, "username"))
            .email(getTextValue(user, "email"))
            .firstName(getTextValue(user, "firstName"))
            .lastName(getTextValue(user, "lastName"))
            .displayName(getTextValue(user, "displayName"))
            .enabled(getBooleanValue(user, "enabled"))
            .emailVerified(getBooleanValue(user, "emailVerified"))
            .createdAt(parseTimestamp(user, "createdTimestamp"))
            .build();
    }
    
    public static UserDto fromKeycloakRepresentation(JsonNode user, Map<String, String> customAttributes) {
        UserDto dto = fromKeycloakRepresentation(user);
        
        dto.setPhoneNumber(customAttributes.get("phone_number"));
        dto.setEmployeeId(customAttributes.get("employee_id"));
        dto.setDepartmentId(customAttributes.get("department_id"));
        dto.setTitle(customAttributes.get("title"));
        dto.setLocation(customAttributes.get("location"));
        dto.setCustomAttributes(customAttributes);
        
        return dto;
    }
    
    public static Map<String, String> extractCustomAttributes(JsonNode user) {
        Map<String, String> attributes = new HashMap<>();
        
        if (user.has("attributes") && user.get("attributes").isObject()) {
            user.get("attributes").fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isArray() && value.size() > 0) {
                    attributes.put(entry.getKey(), value.get(0).asText());
                }
            });
        }
        
        return attributes;
    }
    
    private static String getTextValue(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() 
            ? node.get(field).asText() 
            : null;
    }
    
    private static Boolean getBooleanValue(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() 
            ? node.get(field).asBoolean() 
            : null;
    }
    
    private static Instant parseTimestamp(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return Instant.ofEpochMilli(node.get(field).asLong());
        }
        return null;
    }
}
