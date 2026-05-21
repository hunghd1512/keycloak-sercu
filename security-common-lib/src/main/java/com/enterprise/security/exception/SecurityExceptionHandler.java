package com.enterprise.security.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for security-related exceptions.
 * Converts exceptions to standardized error responses.
 */
@RestControllerAdvice
@Slf4j
public class SecurityExceptionHandler {
    
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
            UnauthorizedException ex, HttpServletRequest request) {
        
        log.warn("Unauthorized access attempt: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        
        ErrorResponse error = ErrorResponse.of(
            "UNAUTHORIZED",
            ex.getMessage(),
            HttpStatus.UNAUTHORIZED,
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(
            ForbiddenException ex, HttpServletRequest request) {
        
        log.warn("Forbidden access attempt: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        
        ErrorResponse error = ErrorResponse.of(
            "FORBIDDEN",
            ex.getMessage(),
            HttpStatus.FORBIDDEN,
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTokenException(
            InvalidTokenException ex, HttpServletRequest request) {
        
        log.warn("Invalid token: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        
        ErrorResponse error = ErrorResponse.of(
            "INVALID_TOKEN",
            ex.getMessage(),
            HttpStatus.UNAUTHORIZED,
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpiredException(
            TokenExpiredException ex, HttpServletRequest request) {
        
        log.warn("Token expired: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        
        ErrorResponse error = ErrorResponse.of(
            "TOKEN_EXPIRED",
            "Your session has expired. Please login again.",
            HttpStatus.UNAUTHORIZED,
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    @ExceptionHandler(InsufficientPermissionException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientPermissionException(
            InsufficientPermissionException ex, HttpServletRequest request) {
        
        log.warn("Insufficient permission: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        
        ErrorResponse error = ErrorResponse.of(
            "INSUFFICIENT_PERMISSION",
            ex.getMessage(),
            HttpStatus.FORBIDDEN,
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        
        log.warn("Access denied: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        
        ErrorResponse error = ErrorResponse.of(
            "ACCESS_DENIED",
            "You don't have permission to access this resource",
            HttpStatus.FORBIDDEN,
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        
        log.warn("Authentication failed: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        
        ErrorResponse error = ErrorResponse.of(
            "AUTHENTICATION_FAILED",
            "Authentication failed. Please provide valid credentials.",
            HttpStatus.UNAUTHORIZED,
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    @ExceptionHandler(InvalidBearerTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBearerTokenException(
            InvalidBearerTokenException ex, HttpServletRequest request) {
        
        log.warn("Invalid bearer token: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        
        ErrorResponse error = ErrorResponse.of(
            "INVALID_TOKEN",
            "The provided token is invalid or malformed",
            HttpStatus.UNAUTHORIZED,
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(
            SecurityException ex, HttpServletRequest request) {
        
        log.warn("Security exception: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        
        ErrorResponse error = ErrorResponse.of(
            "SECURITY_ERROR",
            ex.getMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        log.error("Unexpected error: {} - Path: {}", ex.getMessage(), request.getRequestURI(), ex);
        
        String traceId = UUID.randomUUID().toString();
        
        ErrorResponse error = ErrorResponse.builder()
            .code("INTERNAL_ERROR")
            .message("An unexpected error occurred")
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .path(request.getRequestURI())
            .traceId(traceId)
            .timestamp(java.time.Instant.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
