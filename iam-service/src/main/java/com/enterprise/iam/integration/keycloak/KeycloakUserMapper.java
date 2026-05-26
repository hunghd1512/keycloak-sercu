package com.enterprise.iam.integration.keycloak;

import com.enterprise.iam.application.dto.UserDto;
import com.enterprise.iam.constants.KeycloakUserConstants;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.*;

@UtilityClass
public class KeycloakUserMapper {
    
    public static Map<String, Object> toKeycloakRepresentation(UserDto user) {
        Map<String, Object> representation = new HashMap<>();
        
        representation.put(KeycloakUserConstants.FIELD_USERNAME, user.getUsername());
        representation.put(KeycloakUserConstants.FIELD_EMAIL, user.getEmail());
        representation.put(KeycloakUserConstants.FIELD_FIRST_NAME, user.getFirstName());
        representation.put(KeycloakUserConstants.FIELD_LAST_NAME, user.getLastName());
        representation.put(KeycloakUserConstants.FIELD_ENABLED, 
            user.getEnabled() != null ? user.getEnabled() : KeycloakUserConstants.DEFAULT_ENABLED);
        representation.put(KeycloakUserConstants.FIELD_EMAIL_VERIFIED, 
            user.getEmailVerified() != null ? user.getEmailVerified() : KeycloakUserConstants.DEFAULT_EMAIL_VERIFIED);
        
        if (user.getDisplayName() != null) {
            representation.put(KeycloakUserConstants.FIELD_DISPLAY_NAME, user.getDisplayName());
        }
        
        Map<String, List<String>> attributes = new HashMap<>();
        
        if (user.getPhoneNumber() != null) {
            attributes.put(KeycloakUserConstants.ATTR_PHONE_NUMBER, Collections.singletonList(user.getPhoneNumber()));
        }
        
        if (user.getEmployeeId() != null) {
            attributes.put(KeycloakUserConstants.ATTR_EMPLOYEE_ID, Collections.singletonList(user.getEmployeeId()));
        }
        
        if (user.getDepartmentId() != null) {
            attributes.put(KeycloakUserConstants.ATTR_DEPARTMENT_ID, Collections.singletonList(user.getDepartmentId()));
        }
        
        if (user.getTitle() != null) {
            attributes.put(KeycloakUserConstants.ATTR_TITLE, Collections.singletonList(user.getTitle()));
        }
        
        if (user.getLocation() != null) {
            attributes.put(KeycloakUserConstants.ATTR_LOCATION, Collections.singletonList(user.getLocation()));
        }
        
        if (user.getCustomAttributes() != null && !user.getCustomAttributes().isEmpty()) {
            user.getCustomAttributes().forEach((key, value) -> 
                attributes.put(key, Collections.singletonList(value)));
        }
        
        if (!attributes.isEmpty()) {
            representation.put(KeycloakUserConstants.FIELD_ATTRIBUTES, attributes);
        }
        
        return representation;
    }
    
    public static UserDto fromKeycloakRepresentation(JsonNode user) {
        return UserDto.builder()
            .keycloakUserId(getTextValue(user, KeycloakUserConstants.FIELD_ID))
            .username(getTextValue(user, KeycloakUserConstants.FIELD_USERNAME))
            .email(getTextValue(user, KeycloakUserConstants.FIELD_EMAIL))
            .firstName(getTextValue(user, KeycloakUserConstants.FIELD_FIRST_NAME))
            .lastName(getTextValue(user, KeycloakUserConstants.FIELD_LAST_NAME))
            .displayName(getTextValue(user, KeycloakUserConstants.FIELD_DISPLAY_NAME))
            .enabled(getBooleanValue(user, KeycloakUserConstants.FIELD_ENABLED))
            .emailVerified(getBooleanValue(user, KeycloakUserConstants.FIELD_EMAIL_VERIFIED))
            .createdAt(parseTimestamp(user, KeycloakUserConstants.FIELD_CREATED_TIMESTAMP))
            .build();
    }
    
    public static UserDto fromKeycloakRepresentation(JsonNode user, Map<String, String> customAttributes) {
        UserDto dto = fromKeycloakRepresentation(user);
        
        dto.setPhoneNumber(customAttributes.get(KeycloakUserConstants.ATTR_PHONE_NUMBER));
        dto.setEmployeeId(customAttributes.get(KeycloakUserConstants.ATTR_EMPLOYEE_ID));
        dto.setDepartmentId(customAttributes.get(KeycloakUserConstants.ATTR_DEPARTMENT_ID));
        dto.setTitle(customAttributes.get(KeycloakUserConstants.ATTR_TITLE));
        dto.setLocation(customAttributes.get(KeycloakUserConstants.ATTR_LOCATION));
        dto.setCustomAttributes(customAttributes);
        
        return dto;
    }
    
    public static Map<String, String> extractCustomAttributes(JsonNode user) {
        Map<String, String> attributes = new HashMap<>();
        
        if (user.has(KeycloakUserConstants.FIELD_ATTRIBUTES) && user.get(KeycloakUserConstants.FIELD_ATTRIBUTES).isObject()) {
            user.get(KeycloakUserConstants.FIELD_ATTRIBUTES).fields().forEachRemaining(entry -> {
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
