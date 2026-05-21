package com.enterprise.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that adds a unique request ID to each request for tracing purposes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class RequestIdFilter extends OncePerRequestFilter {
    
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        
        filterChain.doFilter(request, response);
    }
}
