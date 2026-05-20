package com.enterprise.auth.exception;

import com.enterprise.auth.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthException ex, HttpServletRequest request) {
        log.warn("Auth exception: {} - {}", ex.getCode(), ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ex.getCode())
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();
        
        HttpStatus status = determineHttpStatus(ex);
        return ResponseEntity.status(status).body(response);
    }
    
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex, 
                                                                  HttpServletRequest request) {
        log.warn("Invalid credentials: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ex.getCode())
            .message("Invalid username or password")
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(SessionNotFoundException ex, 
                                                               HttpServletRequest request) {
        log.warn("Session not found: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ex.getCode())
            .message("Session not found or expired")
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpired(TokenExpiredException ex, 
                                                           HttpServletRequest request) {
        log.warn("Token expired: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ex.getCode())
            .message("Token has expired")
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    @ExceptionHandler(SessionLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleSessionLimitExceeded(SessionLimitExceededException ex, 
                                                                     HttpServletRequest request) {
        log.warn("Session limit exceeded: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ex.getCode())
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        log.warn("Validation error: {}", ex.getMessage());
        
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::mapFieldError)
            .collect(Collectors.toList());
        
        ErrorResponse response = ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message("Validation failed")
            .fieldErrors(fieldErrors)
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex,
                                                                          HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
            .code("AUTHENTICATION_FAILED")
            .message("Authentication failed")
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                             HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
            .code("ACCESS_DENIED")
            .message("Access denied")
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", ex);
        
        ErrorResponse response = ErrorResponse.builder()
            .code("INTERNAL_ERROR")
            .message("An unexpected error occurred")
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    private HttpStatus determineHttpStatus(AuthException ex) {
        return switch (ex.getCode()) {
            case "INVALID_CREDENTIALS", "SESSION_NOT_FOUND", "TOKEN_EXPIRED" -> HttpStatus.UNAUTHORIZED;
            case "SESSION_LIMIT_EXCEEDED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
    
    private ErrorResponse.FieldError mapFieldError(FieldError fieldError) {
        return ErrorResponse.FieldError.builder()
            .field(fieldError.getField())
            .message(fieldError.getDefaultMessage())
            .rejectedValue(fieldError.getRejectedValue())
            .build();
    }
}
