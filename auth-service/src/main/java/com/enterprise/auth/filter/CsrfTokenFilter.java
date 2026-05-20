package com.enterprise.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
public class CsrfTokenFilter extends OncePerRequestFilter {
    
    public static final String CSRF_TOKEN_HEADER = "X-CSRF-Token";
    public static final String CSRF_TOKEN_REQUEST_ATTRIBUTE = "csrf_token";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        // Skip CSRF for public endpoints
        String path = request.getRequestURI();
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Generate and set CSRF token if not present
        String csrfToken = request.getHeader(CSRF_TOKEN_HEADER);
        if (csrfToken == null || csrfToken.isEmpty()) {
            csrfToken = UUID.randomUUID().toString();
        }
        
        // Store in request attribute for later use
        request.setAttribute(CSRF_TOKEN_REQUEST_ATTRIBUTE, csrfToken);
        
        // Add to response header
        response.setHeader(CSRF_TOKEN_HEADER, csrfToken);
        
        // Add security headers
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        
        filterChain.doFilter(request, response);
    }
    
    private boolean isPublicEndpoint(String path) {
        return path.equals("/auth/login") ||
               path.equals("/auth/refresh") ||
               path.equals("/auth/introspect") ||
               path.startsWith("/actuator") ||
               path.startsWith("/health");
    }
}
