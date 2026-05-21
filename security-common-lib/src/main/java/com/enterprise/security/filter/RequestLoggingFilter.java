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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Filter that logs incoming requests and outgoing responses for debugging and auditing.
 * Can be disabled via configuration.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {
    
    private static final String START_TIME_ATTRIBUTE = "requestStartTime";
    private final ConcurrentMap<String, Long> requestTimes = new ConcurrentHashMap<>();
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        
        if (!log.isDebugEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String requestId = request.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        if (requestId == null) {
            requestId = "no-request-id";
        }
        
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        String remoteAddr = getRemoteAddr(request);
        
        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_ATTRIBUTE, startTime);
        requestTimes.put(requestId, startTime);
        
        log.debug("[{}] {} {} started from {} - User-Agent: {}", 
            requestId, method, requestUri, remoteAddr, request.getHeader("User-Agent"));
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            
            log.debug("[{}] {} {} completed with status {} in {}ms", 
                requestId, method, requestUri, status, duration);
            
            requestTimes.remove(requestId);
        }
    }
    
    private String getRemoteAddr(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }
}
