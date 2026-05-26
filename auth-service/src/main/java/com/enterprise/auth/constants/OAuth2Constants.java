package com.enterprise.auth.constants;

/**
 * OAuth2 and Keycloak token endpoint constants.
 */
public final class OAuth2Constants {

    private OAuth2Constants() {
        // Prevent instantiation
    }

    // Grant Types
    public static final String GRANT_TYPE_PASSWORD = "password";
    public static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";
    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    public static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";

    // Request Parameters
    public static final String PARAM_GRANT_TYPE = "grant_type";
    public static final String PARAM_CLIENT_ID = "client_id";
    public static final String PARAM_CLIENT_SECRET = "client_secret";
    public static final String PARAM_USERNAME = "username";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_REFRESH_TOKEN = "refresh_token";
    public static final String PARAM_TOKEN = "token";
    public static final String PARAM_SCOPE = "scope";
    public static final String PARAM_CODE = "code";
    public static final String PARAM_REDIRECT_URI = "redirect_uri";

    // Standard Scopes
    public static final String SCOPE_OPENID = "openid";
    public static final String SCOPE_PROFILE = "profile";
    public static final String SCOPE_EMAIL = "email";
    public static final String SCOPE_OFFLINE_ACCESS = "offline_access";
    public static final String DEFAULT_SCOPE = "openid profile email";
}
