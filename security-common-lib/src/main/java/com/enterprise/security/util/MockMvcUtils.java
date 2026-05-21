package com.enterprise.security.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

/**
 * Helper utilities for MockMvc testing.
 */
public final class MockMvcUtils {
    
    private MockMvcUtils() {
    }
    
    /**
     * Add a mock Bearer token to the request.
     */
    public static MockHttpServletRequestBuilder withMockToken(
            MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }
    
    /**
     * Add a mock Bearer token with random token.
     */
    public static MockHttpServletRequestBuilder withMockToken(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer mock-token-" + UUID.randomUUID());
    }
    
    /**
     * Add JSON content type header.
     */
    public static MockHttpServletRequestBuilder withJsonContent(MockHttpServletRequestBuilder builder) {
        return builder.contentType(MediaType.APPLICATION_JSON);
    }
    
    /**
     * Add a request ID header.
     */
    public static MockHttpServletRequestBuilder withRequestId(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Request-Id", UUID.randomUUID().toString());
    }
    
    /**
     * Create JSON POST request with mock token.
     */
    public static MockHttpServletRequestBuilder jsonPost(String url, Object body, String token) {
        return withMockToken(
            withJsonContent(
                withRequestId(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                        .content(toJson(body))
                )
            ), token
        );
    }
    
    /**
     * Create JSON GET request with mock token.
     */
    public static MockHttpServletRequestBuilder jsonGet(String url, String token) {
        return withMockToken(
            withJsonContent(
                withRequestId(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                )
            ), token
        );
    }
    
    private static String toJson(Object body) {
        try {
            return new ObjectMapper().writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }
}
