package com.enterprise.iam.constants;

/**
 * Keycloak user representation field constants.
 */
public final class KeycloakUserConstants {

    private KeycloakUserConstants() {
        // Prevent instantiation
    }

    // Basic Fields
    public static final String FIELD_ID = "id";
    public static final String FIELD_USERNAME = "username";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_FIRST_NAME = "firstName";
    public static final String FIELD_LAST_NAME = "lastName";
    public static final String FIELD_DISPLAY_NAME = "displayName";
    public static final String FIELD_ENABLED = "enabled";
    public static final String FIELD_EMAIL_VERIFIED = "emailVerified";
    public static final String FIELD_CREATED_TIMESTAMP = "createdTimestamp";

    // Attributes
    public static final String FIELD_ATTRIBUTES = "attributes";
    
    // Custom Attribute Keys
    public static final String ATTR_PHONE_NUMBER = "phone_number";
    public static final String ATTR_EMPLOYEE_ID = "employee_id";
    public static final String ATTR_DEPARTMENT_ID = "department_id";
    public static final String ATTR_TITLE = "title";
    public static final String ATTR_LOCATION = "location";
    public static final String ATTR_COST_CENTER = "cost_center";
    public static final String ATTR_MANAGER_ID = "manager_id";
    public static final String ATTR_HIRE_DATE = "hire_date";

    // Default Values
    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_EMAIL_VERIFIED = false;
}
