package com.enterprise.security.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@AutoConfiguration(
    after = {OAuth2ResourceServerAutoConfiguration.class}
)
@ConditionalOnClass({Jwt.class, JwtDecoder.class})
@ConditionalOnProperty(prefix = "security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({SecurityProperties.class, JwtDecoderProperties.class})
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
@Slf4j
public class SecurityCommonLibAutoConfiguration {
    
    private final SecurityProperties securityProperties;
    private final JwtDecoderProperties jwtDecoderProperties;
    
    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring default security filter chain");
        
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        
        return http.build();
    }
    
    @Bean
    @ConditionalOnMissingBean(name = "jwtAuthenticationConverterBean")
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        return new EnterpriseJwtAuthenticationConverter();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder() {
        String jwkSetUri = jwtDecoderProperties.getJwkSetUri();
        
        if (jwkSetUri == null || jwkSetUri.isEmpty()) {
            String issuerUri = jwtDecoderProperties.getIssuerUri();
            if (issuerUri != null && !issuerUri.isEmpty()) {
                jwkSetUri = issuerUri.replace("/realms/", "/realms/") + "/protocol/openid-connect/certs";
            } else {
                log.warn("No JWK Set URI configured. JWT decoding will not work without proper configuration.");
                return token -> {
                    throw new IllegalStateException("JWT decoder not configured. Please set security.jwt.jwk-set-uri or security.jwt.issuer-uri");
                };
            }
        }
        
        log.info("Configuring JWT decoder with JWK Set URI: {}", jwkSetUri);
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        configuration.setExposedHeaders(List.of("Authorization", "X-Request-Id"));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
