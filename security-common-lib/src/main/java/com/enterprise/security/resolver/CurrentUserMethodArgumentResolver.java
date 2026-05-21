package com.enterprise.security.resolver;

import com.enterprise.security.annotation.CurrentUser;
import com.enterprise.security.exception.UnauthorizedException;
import com.enterprise.security.principal.CurrentUserService;
import com.enterprise.security.principal.EnterpriseUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves @CurrentUser annotation to inject the current authenticated user
 * into controller method parameters.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserMethodArgumentResolver implements HandlerMethodArgumentResolver {
    
    private final CurrentUserService currentUserService;
    
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }
    
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        
        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        boolean required = annotation != null && annotation.required();
        
        EnterpriseUserPrincipal user = currentUserService.getCurrentUser();
        
        if (user == null) {
            if (required) {
                throw new UnauthorizedException("No authenticated user found. Please provide a valid JWT token.");
            }
            return null;
        }
        
        Class<?> parameterType = parameter.getParameterType();
        
        // If parameter type is EnterpriseUserPrincipal, return directly
        if (EnterpriseUserPrincipal.class.isAssignableFrom(parameterType)) {
            return user;
        }
        
        // If parameter type is String and name is "id", return user ID
        if (String.class.isAssignableFrom(parameterType) && "id".equals(parameter.getParameterName())) {
            return user.getId();
        }
        
        // If parameter type is Authentication, return the security authentication
        if (Authentication.class.isAssignableFrom(parameterType)) {
            return SecurityContextHolder.getContext().getAuthentication();
        }
        
        // Default: return the principal
        return user;
    }
}
