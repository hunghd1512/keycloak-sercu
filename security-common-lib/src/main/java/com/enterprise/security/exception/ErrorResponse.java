package com.enterprise.security.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized error response for API errors.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    private String code;
    
    private String message;
    
    private int status;
    
    private String path;
    
    private String traceId;
    
    private Instant timestamp;
    
    private Map<String, String> fieldErrors;
    
    public static ErrorResponse of(String code, String message, HttpStatus status) {
        return ErrorResponse.builder()
            .code(code)
            .message(message)
            .status(status.value())
            .timestamp(Instant.now())
            .build();
    }
    
    public static ErrorResponse of(String code, String message, HttpStatus status, String path) {
        return ErrorResponse.builder()
            .code(code)
            .message(message)
            .status(status.value())
            .path(path)
            .timestamp(Instant.now())
            .build();
    }
    
    public static ErrorResponse of(String code, String message, HttpStatus status, 
                                   String path, Map<String, String> fieldErrors) {
        return ErrorResponse.builder()
            .code(code)
            .message(message)
            .status(status.value())
            .path(path)
            .fieldErrors(fieldErrors)
            .timestamp(Instant.now())
            .build();
    }
}
