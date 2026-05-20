package com.enterprise.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {
    
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_START_TIME = "request_start_time";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        // Generate or use existing request ID
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        
        // Set request ID in response header
        response.setHeader(REQUEST_ID_HEADER, requestId);
        
        // Store start time
        long startTime = System.currentTimeMillis();
        request.setAttribute(REQUEST_START_TIME, startTime);
        
        // Log request
        logRequest(request, requestId);
        
        // Process request
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Log response
            logResponse(request, response, requestId, startTime);
        }
    }
    
    private void logRequest(HttpServletRequest request, String requestId) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] {} {} from {} with headers: {}",
                     requestId,
                     request.getMethod(),
                     request.getRequestURI(),
                     getClientIp(request),
                     extractHeaders(request));
        } else {
            log.info("[{}] {} {} from {}",
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    getClientIp(request));
        }
    }
    
    private void logResponse(HttpServletRequest request, 
                            HttpServletResponse response, 
                            String requestId, 
                            long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("[{}] {} {} -> {} in {}ms",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                duration);
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private String extractHeaders(HttpServletRequest request) {
        StringBuilder headers = new StringBuilder();
        request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
            if (!headerName.equalsIgnoreCase("Authorization")) {
                headers.append(headerName).append("=").append(request.getHeader(headerName)).append(", ");
            } else {
                headers.append(headerName).append("=[REDACTED], ");
            }
        });
        return headers.toString();
    }
}
