# Security Common Library - Tài liệu Thiết kế Kiến trúc

## Mục lục

1. [Tổng quan Security Common Library](#1-tổng-quan-security-common-library)
2. [Tại sao cần Shared Security Infrastructure](#2-tại-sao-cần-shared-security-infrastructure)
3. [Trách nhiệm của Common Library](#3-trách-nhiệm-của-common-library)
4. [JWT Validation Infrastructure](#4-jwt-validation-infrastructure)
5. [Security Filter Chain](#5-security-filter-chain)
6. [Authentication Converters](#6-authentication-converters)
7. [Principal Abstraction](#7-principal-abstraction)
8. [Annotations](#8-annotations)
9. [Exception Handling](#9-exception-handling)
10. [Permission SPI](#10-permission-spi)
11. [Auto Configuration](#11-auto-configuration)
12. [Package Structure](#12-package-structure)
13. [Versioning & Compatibility](#13-versioning--compatibility)
14. [Testing Strategy](#14-testing-strategy)
15. [Anti-patterns & Boundaries](#15-anti-patterns--boundaries)

---

## 1. Tổng quan Security Common Library

### 1.1 Mục đích

```
┌─────────────────────────────────────────────────────────────────────┐
│                    security-common-lib Purpose                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│         ┌─────────────────────────────────────────────────────┐    │
│         │           security-common-lib                        │    │
│         │     (Spring Boot Security Starter)                   │    │
│         └─────────────────────────────────────────────────────┘    │
│                                │                                    │
│         ┌──────────────────────┼──────────────────────┐           │
│         │                      │                      │            │
│         ▼                      ▼                      ▼            │
│  ┌─────────────┐        ┌─────────────┐        ┌─────────────┐    │
│  │  document- │        │  workflow- │        │   order-   │    │
│  │  service   │        │  service   │        │  service   │    │
│  └─────────────┘        └─────────────┘        └─────────────┘    │
│                                                                     │
│  PURPOSE:                                                           │
│  ├── Shared JWT validation logic                                   │
│  ├── Shared SecurityFilterChain                                    │
│  ├── Shared annotations and utilities                              │
│  ├── Consistent security behavior across services                  │
│  └── Single source of truth for security configuration              │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**security-common-lib** là một Spring Boot Starter chứa infrastructure security dùng chung cho tất cả resource services, bao gồm:

- JWT validation
- Authentication conversion
- Principal abstraction
- Permission evaluation
- Security annotations
- Exception handling
- Auto-configuration

### 1.2 Library là gì?

```
┌─────────────────────────────────────────────────────────────────────┐
│                    What security-common-lib IS                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  IS:                                                                │
│  ✅ Reusable Spring Security infrastructure                        │
│  ✅ Shared authentication/authorization abstractions                │
│  ✅ Common JWT validation logic                                      │
│  ✅ Security filter chain templates                                 │
│  ✅ Permission evaluator interfaces (SPI)                          │
│  ✅ Security annotations library                                     │
│  ✅ Auto-configuration for common scenarios                        │
│                                                                     │
│  IS NOT:                                                            │
│  ❌ Business authorization logic                                    │
│  ❌ User management                                                │
│  ❌ OAuth2 login orchestration                                     │
│  ❌ Workflow permission rules                                        │
│  ❌ Domain-specific permissions                                      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. Tại sao cần Shared Security Infrastructure

### 2.1 Vấn đề khi không có Shared Library

```
┌─────────────────────────────────────────────────────────────────────┐
│          Problems Without Shared Security Library                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   Service A (document-service)                                      │
│   ├── Own JWT decoder                                             │
│   ├── Own SecurityFilterChain                                     │
│   ├── Own JwtAuthenticationConverter                               │
│   └── Different exception handling                                  │
│                                                                     │
│   Service B (workflow-service)                                     │
│   ├── Own JWT decoder (DIFFERENT impl)                           │
│   ├── Own SecurityFilterChain (DIFFERENT impl)                   │
│   ├── Own JwtAuthenticationConverter (DIFFERENT impl)              │
│   └── Different exception handling (DIFFERENT impl)               │
│                                                                     │
│   Service C (order-service)                                        │
│   └── Same problems multiplied...                                  │
│                                                                     │
│   PROBLEMS:                                                         │
│   ❌ Duplicate code across services                                │
│   ❌ Inconsistent security behavior                                │
│   ❌ Different JWT validation logic (security risk)                 │
│   ❌ Hard to maintain and update                                   │
│   ❌ Security patches need to be applied in multiple places        │
│   ❌ Inconsistent error responses                                 │
│   ❌ Developers re-invent the wheel                               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 Giải pháp với Shared Library

```
┌─────────────────────────────────────────────────────────────────────┐
│            Solutions With Shared Security Library                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│                        ┌───────────────────┐                        │
│                        │ security-common-  │                        │
│                        │      lib          │                        │
│                        └─────────┬─────────┘                        │
│                                  │                                  │
│         ┌──────────────────────┼──────────────────────┐           │
│         │                      │                      │            │
│         ▼                      ▼                      ▼            │
│  ┌─────────────┐        ┌─────────────┐        ┌─────────────┐    │
│  │  document- │        │  workflow- │        │   order-   │    │
│  │  service   │        │  service   │        │  service   │    │
│  │             │        │             │        │             │    │
│  │  Extends:  │        │  Extends:   │        │  Extends:   │    │
│  │  - JWT     │        │  - JWT     │        │  - JWT     │    │
│  │  - Filter  │        │  - Filter  │        │  - Filter  │    │
│  │  - Annot. │        │  - Annot.  │        │  - Annot.  │    │
│  │  - Perm.   │        │  - Perm.   │        │  - Perm.   │    │
│  └─────────────┘        └─────────────┘        └─────────────┘    │
│                                                                     │
│   BENEFITS:                                                         │
│   ✅ Single source of truth for security                           │
│   ✅ Consistent behavior across services                           │
│   ✅ Centralized security updates/patches                           │
│   ✅ Developers focus on business logic                            │
│   ✅ Standardized error responses                                  │
│   ✅ Easy to audit and review                                      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.3 Shared vs Service-Specific

| Khía cạnh | Shared Library | Service-Specific |
|-----------|---------------|------------------|
| **JWT validation** | ✅ Shared | ❌ No |
| **Token decoding** | ✅ Shared | ❌ No |
| **GrantedAuthority conversion** | ✅ Shared | ❌ No |
| **Security annotations** | ✅ Shared | ❌ No |
| **Exception handling** | ✅ Shared | ❌ No |
| **Permission interfaces** | ✅ Shared (SPI) | Implementation per service |
| **Business rules** | ❌ No | ✅ Service-specific |
| **Domain permissions** | ❌ No | ✅ Service-specific |
| **Workflow rules** | ❌ No | ✅ Service-specific |

---

## 3. Trách nhiệm của Common Library

### 3.1 Tổng quan Trách nhiệm

```
┌─────────────────────────────────────────────────────────────────────┐
│              security-common-lib Responsibilities                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  AUTHENTICATION INFRASTRUCTURE:                                     │
│  ├── JWT token validation                                           │
│  ├── Token signature verification                                    │
│  ├── Token claims extraction                                        │
│  └── Security context population                                    │
│                                                                     │
│  AUTHORIZATION INFRASTRUCTURE:                                      │
│  ├── SecurityFilterChain configuration                              │
│  ├── JwtAuthenticationConverter                                     │
│  ├── GrantedAuthority extraction                                    │
│  └── Permission evaluator interfaces                                │
│                                                                     │
│  UTILITIES & ABSTRACTIONS:                                          │
│  ├── CurrentUser abstraction                                       │
│  ├── Security annotations (@CurrentUser, @HasRole)                 │
│  ├── Exception handling                                            │
│  └── Security utilities                                            │
│                                                                     │
│  AUTO-CONFIGURATION:                                                │
│  ├── Spring Boot auto-configuration                                │
│  ├── Externalized configuration support                             │
│  └── Starter dependencies                                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 Trách nhiệm chi tiết

| Trách nhiệm | Mô tả | Ví dụ |
|-------------|-------|-------|
| **JWT Validation** | Validate JWT signature, issuer, audience | Decoder, validator |
| **Authority Conversion** | Convert JWT claims to Spring authorities | JwtAuthenticationConverter |
| **Security Context** | Populate SecurityContextHolder | Filter chain |
| **Current User** | Extract user info from token | CurrentUser, UserPrincipal |
| **Annotations** | Declarative security | @CurrentUser, @HasRole |
| **Exception Handling** | Consistent security errors | 401, 403 responses |
| **Permission SPI** | Extensible permission interface | PermissionEvaluator |

---

## 4. JWT Validation Infrastructure

### 4.1 JWT Validation Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                    JWT Validation Flow                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   Incoming Request                                                  │
│        │                                                              │
│        │ Extract Bearer token                                        │
│        ▼                                                              │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │           security-common-lib JwtDecoder                    │   │
│   │                                                              │   │
│   │   1. Extract token from Authorization header                │   │
│   │                                                              │   │
│   │   2. Validate signature (using JWKS from Keycloak)          │   │
│   │      └── GET https://keycloak/realms/enterprise/protocol/  │   │
│   │          openid-connect/certs                               │   │
│   │                                                              │   │
│   │   3. Validate claims:                                        │   │
│   │      ├── iss (issuer)                                       │   │
│   │      ├── aud (audience)                                     │   │
│   │      ├── exp (expiration)                                   │   │
│   │      └── iat (issued at)                                    │   │
│   │                                                              │   │
│   │   4. Extract claims from payload                            │   │
│   │      └── sub, preferred_username, email, roles, etc.        │   │
│   │                                                              │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │           JwtAuthenticationConverter                        │   │
│   │                                                              │   │
│   │   Convert JWT → Collection<GrantedAuthority>                 │   │
│   │                                                              │   │
│   │   Extract from token:                                       │   │
│   │   ├── realm_access.roles → ROLE_XXX                       │   │
│   │   ├── resource_access.{client}.roles → ROLE_XXX           │   │
│   │   └── scope → SCOPE_XXX                                   │   │
│   │                                                              │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│   SecurityContextHolder.getContext().setAuthentication()            │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 JWT Decoder Configuration

```java
// JwtDecoder configuration in common-lib
@ConfigurationProperties(prefix = "security.jwt")
public class JwtDecoderProperties {
    private String issuerUri;
    private String audience;
    private Duration clockSkew = Duration.ofSeconds(60);
}

// Auto-configuration
@Bean
@ConditionalOnMissingBean
public JwtDecoder jwtDecoder(JwtDecoderProperties properties) {
    NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(
        properties.getIssuerUri()
    );
    
    // Add custom validation
    jwtDecoder.setJwtValidator(
        JwtValidators.createDefaultWithIssuer(properties.getIssuerUri())
    );
    
    return jwtDecoder;
}
```

### 4.3 JWT Claims Extraction

```
┌─────────────────────────────────────────────────────────────────────┐
│                    JWT Claims Structure                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  INCOMING JWT:                                                      │
│  ─────────────                                                      │
│  {                                                                 │
│    "sub": "user-uuid-12345",                                      │
│    "iss": "https://keycloak/realms/enterprise",                   │
│    "aud": ["document-service"],                                   │
│    "exp": 1716200000,                                            │
│    "realm_access": {                                              │
│      "roles": ["USER", "SUPPORT"]                                 │
│    },                                                             │
│    "resource_access": {                                           │
│      "document-service": {                                        │
│        "roles": ["DOC_VIEWER", "DOC_EDITOR"]                      │
│      }                                                           │
│    },                                                             │
│    "preferred_username": "john.doe",                             │
│    "email": "john.doe@example.com",                               │
│    "name": "John Doe"                                             │
│  }                                                                │
│                                                                     │
│  EXTRACTED BY JwtAuthenticationConverter:                          │
│  ──────────────────────────────────────────                       │
│  GrantedAuthority[] = [                                           │
│    "ROLE_USER",                                                  │
│    "ROLE_SUPPORT",                                               │
│    "ROLE_DOC_VIEWER",                                            │
│    "ROLE_DOC_EDITOR",                                            │
│    "SCOPE_openid",                                                │
│    "SCOPE_profile"                                               │
│  ]                                                               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 5. Security Filter Chain

### 5.1 Filter Chain Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Security Filter Chain                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   HTTP Request                                                       │
│        │                                                              │
│        ▼                                                              │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │ 1. SecurityContextPersistenceFilter                         │   │
│   │    └── Load/create SecurityContext                         │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │ 2. ServerHttpRequestMatcherFilter                           │   │
│   │    └── Match request to security filter chain              │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                      │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │ 3. OAuth2AuthorizationFilter (Custom from common-lib)      │   │
│   │    └── Extract JWT, validate, set authentication            │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │ 4. AuthorizationFilter                                      │   │
│   │    └── @PreAuthorize, @Secured annotations                 │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│   Controller                                                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.2 Shared SecurityFilterChain Configuration

```java
// BaseSecurityConfiguration in common-lib
public abstract class BaseSecurityConfiguration {
    
    protected void configureSecurity(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // Or configure CSRF
            .sessionManagement(session -> 
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )
            .authorizeHttpRequests(auth -> {
                // Public endpoints
                configurePublicEndpoints(auth);
                // Protected endpoints (default)
                auth.anyRequest().authenticated();
            })
            .oauth2ResourceServer(oauth2 -> 
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(
                    jwtAuthenticationConverter()
                ))
            );
    }
    
    // Abstract methods for service-specific configuration
    protected abstract void configurePublicEndpoints(
        AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth
    );
    
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Shared converter from common-lib
        return new EnterpriseJwtAuthenticationConverter();
    }
}

// Service-specific configuration extends base
@Configuration
@ConditionalOnProperty(name = "app.name", havingValue = "document-service")
public class DocumentServiceSecurityConfig extends BaseSecurityConfiguration {
    
    @Override
    protected void configurePublicEndpoints(
        AuthorizeHttpRequestsConfigurer<...>.AuthorizationManagerRequestMatcherRegistry auth
    ) {
        auth.requestMatchers("/actuator/health").permitAll();
        auth.requestMatchers("/api/v1/documents/public/**").permitAll();
    }
}
```

### 5.3 Stateless Session Management

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Stateless Architecture                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  WHY STATELESS?                                                     │
│  ├── Horizontal scaling: No session affinity required               │
│  ├── No session storage: Simpler infrastructure                    │
│  ├── Performance: No session lookup per request                    │
│  └── Consistency: Token = session, no sync issues                  │
│                                                                     │
│  TRADEOFFS:                                                         │
│  ├── Token must be validated on every request                      │
│  ├── Cannot revoke individual sessions easily                      │
│  └── Token size in request header                                  │
│                                                                     │
│  MITIGATIONS:                                                       │
│  ├── Short-lived access tokens (5-15 min)                         │
│  ├── Refresh token rotation                                        │
│  └── Token blacklist for critical revocations                      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6. Authentication Converters

### 6.1 JwtAuthenticationConverter Strategy

```
┌─────────────────────────────────────────────────────────────────────┐
│                JwtAuthenticationConverter Flow                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   JWT Token                                                          │
│      │                                                                │
│      │ JwtAuthenticationConverter                                   │
│      ▼                                                                │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                   JWT Payload                               │   │
│   │                                                             │   │
│   │   {                                                         │   │
│   │     "sub": "user-uuid",                                   │   │
│   │     "realm_access": {                                     │   │
│   │       "roles": ["USER", "ADMIN"]                          │   │
│   │     },                                                     │   │
│   │     "resource_access": {                                   │   │
│   │       "document-service": {                               │   │
│   │         "roles": ["DOC_VIEWER"]                           │   │
│   │       }                                                   │   │
│   │     }                                                     │   │
│   │   }                                                        │   │
│   │                                                             │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              ▼                                      │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │              GrantedAuthority[]                            │   │
│   │                                                             │   │
│   │   Collection<GrantedAuthority> authorities = [            │   │
│   │     new SimpleGrantedAuthority("ROLE_USER"),              │   │
│   │     new SimpleGrantedAuthority("ROLE_ADMIN"),             │   │
│   │     new SimpleGrantedAuthority("ROLE_DOC_VIEWER"),        │   │
│   │     new ScopeOAuth2AuthenticatedPrincipal(                 │   │
│   │       "SCOPE_openid", "SCOPE_profile"                     │   │
│   │     )                                                      │   │
│   │   ]                                                         │   │
│   │                                                             │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              ▼                                      │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │              UsernamePasswordAuthenticationToken            │   │
│   │                                                             │   │
│   │   new UsernamePasswordAuthenticationToken(                  │   │
│   │     principal,  // User ID or UserPrincipal                │   │
│   │     credentials, // null (token already validated)         │   │
│   │     authorities  // Collection<GrantedAuthority>            │   │
│   │   )                                                         │   │
│   │                                                             │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.2 EnterpriseJwtAuthenticationConverter Implementation

```java
// In security-common-lib
public class EnterpriseJwtAuthenticationConverter 
        implements Converter<Jwt, AbstractAuthenticationToken> {
    
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        
        // Create authentication with user principal
        EnterpriseUserPrincipal principal = EnterpriseUserPrincipal.fromJwt(jwt);
        
        return new JwtAuthenticationToken(jwt, authorities, principal.getName());
    }
    
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        
        // 1. Realm roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles != null) {
                roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .forEach(authorities::add);
            }
        }
        
        // 2. Client roles (for this service)
        String clientId = jwt.getAudience().stream().findFirst().orElse(null);
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null && clientId != null) {
            Map<String, Object> clientAccess = 
                (Map<String, Object>) resourceAccess.get(clientId);
            if (clientAccess != null) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) clientAccess.get("roles");
                if (roles != null) {
                    roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .forEach(authorities::add);
                }
            }
        }
        
        // 3. Scopes
        String scope = jwt.getClaim("scope");
        if (scope != null) {
            Arrays.stream(scope.split(" "))
                .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                .forEach(authorities::add);
        }
        
        return authorities;
    }
}
```

---

## 7. Principal Abstraction

### 7.1 CurrentUser Abstraction

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CurrentUser Architecture                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │              EnterpriseUserPrincipal                        │   │
│   │  (implements UserDetails, Principal)                       │   │
│   │                                                             │   │
│   │   Properties:                                              │   │
│   │   ├── id: String (sub from JWT)                           │   │
│   │   ├── username: String (preferred_username)                │   │
│   │   ├── email: String                                        │   │
│   │   ├── displayName: String (name)                          │   │
│   │   ├── realmRoles: List<String>                            │   │
│   │   ├── clientRoles: Map<String, List<String>>              │   │
│   │   └── attributes: Map<String, Object>                      │   │
│   │                                                             │   │
│   │   Methods:                                                  │   │
│   │   ├── hasRole(String role)                                 │   │
│   │   ├── hasClientRole(String client, String role)           │   │
│   │   ├── hasAuthority(String authority)                       │   │
│   │   └── getClientRole(String client)                         │   │
│   │                                                             │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │              CurrentUserService                             │   │
│   │  (Request-scoped bean)                                      │   │
│   │                                                             │   │
│   │   Methods:                                                  │   │
│   │   ├── getCurrentUser(): EnterpriseUserPrincipal            │   │
│   │   ├── getCurrentUserId(): String                           │   │
│   │   └── requireCurrentUser(): EnterpriseUserPrincipal        │   │
│   │                                                             │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 7.2 Principal Factory

```java
// In security-common-lib
public class EnterpriseUserPrincipal implements Principal, UserDetails {
    
    private final String id;
    private final String username;
    private final String email;
    private final String displayName;
    private final Set<GrantedAuthority> authorities;
    private final Map<String, Object> attributes;
    
    public static EnterpriseUserPrincipal fromJwt(Jwt jwt) {
        return EnterpriseUserPrincipal.builder()
            .id(jwt.getSubject())
            .username(jwt.getClaimAsString("preferred_username"))
            .email(jwt.getClaimAsString("email"))
            .displayName(jwt.getClaimAsString("name"))
            .authorities(extractAuthorities(jwt))
            .attributes(jwt.getClaims())
            .build();
    }
    
    public boolean hasRole(String role) {
        return authorities.stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
    
    public boolean hasClientRole(String clientId, String role) {
        // Check resource_access for client-specific roles
        Map<String, Object> resourceAccess = 
            (Map<String, Object>) attributes.get("resource_access");
        if (resourceAccess != null) {
            Map<String, Object> clientAccess = 
                (Map<String, Object>) resourceAccess.get(clientId);
            if (clientAccess != null) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) clientAccess.get("roles");
                return roles != null && roles.contains(role);
            }
        }
        return false;
    }
}

// Request-scoped service
@Component
@Scope("request")
public class CurrentUserService {
    
    @Autowired
    private SecurityContextHolderStrategy securityContextHolderStrategy;
    
    public EnterpriseUserPrincipal getCurrentUser() {
        Authentication auth = securityContextHolderStrategy
            .getContext().getAuthentication();
        
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return (EnterpriseUserPrincipal) jwtAuth.getPrincipal();
        }
        
        return null; // or throw exception
    }
    
    public String getCurrentUserId() {
        EnterpriseUserPrincipal user = getCurrentUser();
        return user != null ? user.getId() : null;
    }
    
    public EnterpriseUserPrincipal requireCurrentUser() {
        EnterpriseUserPrincipal user = getCurrentUser();
        if (user == null) {
            throw new AuthenticationException("User not authenticated");
        }
        return user;
    }
}
```

### 7.3 Usage in Resource Services

```java
// In document-service
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    
    @Autowired
    private CurrentUserService currentUserService;
    
    @PostMapping
    public ResponseEntity<DocumentDTO> createDocument(
            @RequestBody CreateDocumentRequest request) {
        
        // Get current user from security context
        EnterpriseUserPrincipal currentUser = currentUserService.getCurrentUser();
        
        // Use user info
        String creatorId = currentUser.getId();
        String creatorEmail = currentUser.getEmail();
        
        // Business logic...
        
        return ResponseEntity.created(uri).body(document);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<DocumentDTO> getDocument(@PathVariable String id) {
        EnterpriseUserPrincipal currentUser = currentUserService.requireCurrentUser();
        
        // Check authorization
        if (!currentUser.hasRole("DOC_VIEWER") && 
            !currentUser.hasRole("DOC_EDITOR")) {
            throw new AccessDeniedException("Not authorized to view documents");
        }
        
        return ResponseEntity.ok(document);
    }
}
```

---

## 8. Annotations

### 8.1 Available Annotations

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Shared Security Annotations                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  @CurrentUser                                               │   │
│  │  ─────────────────                                           │   │
│  │  Inject current authenticated user                         │   │
│  │                                                             │   │
│  │  Usage:                                                     │   │
│  │  @GetMapping("/me")                                         │   │
│  │  public UserDTO getCurrentUser(@CurrentUser User user) {}   │   │
│  │                                                             │   │
│  │  Alternative with full principal:                          │   │
│  │  @GetMapping("/profile")                                    │   │
│  │  public ProfileDTO getProfile(                             │   │
│  │      @CurrentUser EnterpriseUserPrincipal user) {}         │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  @HasRole                                                   │   │
│  │  ────────────                                               │   │
│  │  Check if user has specific realm role                     │   │
│  │                                                             │   │
│  │  Usage:                                                     │   │
│  │  @PostMapping                                               │   │
│  │  @HasRole("ADMIN")                                          │   │
│  │  public ResponseEntity<Void> adminAction() {}              │   │
│  │                                                             │   │
│  │  With OR logic:                                             │   │
│  │  @GetMapping("/admin")                                      │   │
│  │  @HasRole(value = {"ADMIN", "SUPER_ADMIN"})                │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  @HasAuthority                                              │   │
│  │  ────────────────                                           │   │
│  │  Check for specific authority (ROLE_ or SCOPE_)           │   │
│  │                                                             │   │
│  │  Usage:                                                     │   │
│  │  @PostMapping("/publish")                                  │   │
│  │  @HasAuthority("ROLE_DOC_EDITOR")                          │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  @HasPermission                                             │   │
│  │  ─────────────────                                          │   │
│  │  Custom permission check via PermissionEvaluator            │   │
│  │                                                             │   │
│  │  Usage:                                                     │   │
│  │  @DeleteMapping("/{id}")                                    │   │
│  │  @HasPermission(value = "document", arg = "#id)           │   │
│  │  public ResponseEntity<Void> deleteDocument() {}           │   │
│  │                                                             │   │
│  │  Multiple permissions (AND):                               │   │
│  │  @PostMapping("/approve")                                   │   │
│  │  @HasPermission({"document", "approve"})                   │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  @RequireAuthentication                                     │   │
│  │  ───────────────────────                                    │   │
│  │  Simple require authenticated (no role check)              │   │
│  │                                                             │   │
│  │  Usage:                                                     │   │
│  │  @GetMapping("/profile")                                    │   │
│  │  @RequireAuthentication                                      │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 Annotation Implementation

```java
// In security-common-lib

// CurrentUser annotation
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {}

// HasRole annotation
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole(%value)")
@Documented
public @interface HasRole {
    String[] value();
}

// HasAuthority annotation
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyAuthority(%value)")
@Documented
public @interface HasAuthority {
    String[] value();
}

// HasPermission annotation
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasPermission(%value, %arg)")
@Documented
public @interface HasPermission {
    String[] value();
    String arg() default "";
}

// Argument resolver for @CurrentUser
@Component
public class CurrentUserMethodArgumentResolver 
        implements HandlerMethodArgumentResolver {
    
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }
    
    @Override
    public Object resolveArgument(MethodParameter parameter, 
                                   ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest,
                                   WebDataBinderFactory binderFactory) {
        
        EnterpriseUserPrincipal user = currentUserService.getCurrentUser();
        
        if (user == null) {
            throw new AuthenticationCredentialsNotFoundException(
                "No authenticated user found"
            );
        }
        
        Class<?> parameterType = parameter.getParameterType();
        if (parameterType.isAssignableFrom(EnterpriseUserPrincipal.class)) {
            return user;
        }
        
        return user;
    }
}
```

---

## 9. Exception Handling

### 9.1 Security Exception Hierarchy

```
┌─────────────────────────────────────────────────────────────────────┐
│                Security Exception Hierarchy                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   Exception                                                          │
│   └── RuntimeException                                              │
│       └── SecurityException                                         │
│           ├── AuthenticationException                               │
│           │   ├── JwtAuthenticationException                       │
│           │   ├── TokenExpiredException                            │
│           │   ├── InvalidTokenException                             │
│           │   └── InsufficientAuthenticationException               │
│           │                                                          │
│           └── AuthorizationException                                │
│               ├── AccessDeniedException                              │
│               ├── InsufficientPermissionException                   │
│               └── ForbiddenResourceException                        │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 9.2 Global Exception Handler

```java
// In security-common-lib
@RestControllerAdvice
public class SecurityExceptionHandler {
    
    @ExceptionHandler(JwtAuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleJwtAuthenticationException(
            JwtAuthenticationException ex) {
        return ErrorResponse.of(
            "AUTHENTICATION_FAILED",
            "Invalid or expired JWT token",
            HttpStatus.UNAUTHORIZED
        );
    }
    
    @ExceptionHandler(TokenExpiredException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleTokenExpired(TokenExpiredException ex) {
        return ErrorResponse.of(
            "TOKEN_EXPIRED",
            "Access token has expired. Please refresh.",
            HttpStatus.UNAUTHORIZED
        );
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return ErrorResponse.of(
            "ACCESS_DENIED",
            "You don't have permission to access this resource",
            HttpStatus.FORBIDDEN
        );
    }
    
    @ExceptionHandler(InsufficientPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleInsufficientPermission(
            InsufficientPermissionException ex) {
        return ErrorResponse.of(
            "INSUFFICIENT_PERMISSION",
            ex.getMessage(),
            HttpStatus.FORBIDDEN
        );
    }
    
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleCredentialsNotFound(
            AuthenticationCredentialsNotFoundException ex) {
        return ErrorResponse.of(
            "AUTHENTICATION_REQUIRED",
            "Authentication is required to access this resource",
            HttpStatus.UNAUTHORIZED
        );
    }
}

// Standard error response
public class ErrorResponse {
    private String code;
    private String message;
    private int status;
    private String traceId;
    private Instant timestamp;
    
    public static ErrorResponse of(String code, String message, HttpStatus status) {
        return ErrorResponse.builder()
            .code(code)
            .message(message)
            .status(status.value())
            .traceId(getCurrentTraceId())
            .timestamp(Instant.now())
            .build();
    }
}
```

### 9.3 HTTP Response Codes

| Exception | HTTP Status | Code | Description |
|-----------|-------------|------|-------------|
| Invalid JWT | 401 | `AUTHENTICATION_FAILED` | Token malformed or invalid |
| Expired token | 401 | `TOKEN_EXPIRED` | Access token expired |
| Missing token | 401 | `AUTHENTICATION_REQUIRED` | No authentication provided |
| Insufficient role | 403 | `ACCESS_DENIED` | User lacks required role |
| Insufficient permission | 403 | `INSUFFICIENT_PERMISSION` | Business permission denied |

---

## 10. Permission SPI

### 10.1 Permission Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Permission SPI Architecture                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │              PERMISSION SPI (Service Provider Interface)     │   │
│   │                                                              │   │
│   │   // Interface in common-lib                               │   │
│   │   public interface PermissionEvaluator {                    │   │
│   │       boolean hasPermission(                                │   │
│   │           Authentication auth,                               │   │
│   │           Object targetDomainObject,                        │   │
│   │           Object permission                                 │   │
│   │       );                                                    │   │
│   │   }                                                         │   │
│   │                                                              │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              │ Implement by each service            │
│                              ▼                                      │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │  document-service PermissionEvaluatorImpl                  │   │
│   │                                                              │   │
│   │   public boolean hasPermission(...) {                       │   │
│   │       // Business-specific permission logic                │   │
│   │       // "Can user edit document X?"                       │   │
│   │       // "Can user delete document Y?"                     │   │
│   │   }                                                         │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 10.2 PermissionEvaluator Interface

```java
// In security-common-lib
public interface PermissionEvaluator {
    
    /**
     * Check permission for a domain object
     */
    boolean hasPermission(
        Authentication authentication,
        Object targetDomainObject,
        Object permission
    );
    
    /**
     * Check permission by target type and id
     */
    boolean hasPermission(
        Authentication authentication,
        Serializable targetId,
        String targetType,
        Object permission
    );
    
    /**
     * Check permission without specific target
     */
    boolean hasGlobalPermission(
        Authentication authentication,
        Object permission
    );
}

// Method signature expressions helper
public class PermissionExpressionRoot {
    private Authentication authentication;
    private Object targetObject;
    
    public boolean hasPermission(Object permission) {
        // Delegate to registered PermissionEvaluator
        return permissionEvaluator.hasPermission(
            authentication, targetObject, permission
        );
    }
    
    public boolean hasPermission(Serializable targetId, 
                                  String targetType, 
                                  Object permission) {
        return permissionEvaluator.hasPermission(
            authentication, targetId, targetType, permission
        );
    }
}
```

### 10.3 Service-Specific Implementation

```java
// In document-service
@Component
public class DocumentPermissionEvaluator implements PermissionEvaluator {
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Override
    public boolean hasPermission(Authentication auth, 
                                  Object targetDomainObject, 
                                  Object permission) {
        if (targetDomainObject instanceof Document document) {
            return hasDocumentPermission(auth, document, permission);
        }
        return false;
    }
    
    @Override
    public boolean hasPermission(Authentication auth, 
                                  Serializable targetId, 
                                  String targetType, 
                                  Object permission) {
        if ("document".equals(targetType)) {
            Document document = documentRepository.findById((String) targetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Document not found: " + targetId
                ));
            return hasDocumentPermission(auth, document, permission);
        }
        return false;
    }
    
    @Override
    public boolean hasGlobalPermission(Authentication auth, Object permission) {
        // Global permission check (e.g., can create documents at all)
        if ("document:create".equals(permission)) {
            EnterpriseUserPrincipal user = (EnterpriseUserPrincipal) auth.getPrincipal();
            return user.hasRole("DOC_EDITOR") || user.hasRole("DOC_ADMIN");
        }
        return false;
    }
    
    private boolean hasDocumentPermission(Authentication auth, 
                                          Document document, 
                                          Object permission) {
        EnterpriseUserPrincipal user = (EnterpriseUserPrincipal) auth.getPrincipal();
        
        String perm = String.valueOf(permission);
        
        switch (perm) {
            case "read":
                // Anyone with DOC_VIEWER can read
                return user.hasRole("DOC_VIEWER") || 
                       user.hasRole("DOC_EDITOR") || 
                       user.hasRole("DOC_ADMIN");
                       
            case "write":
                // Only editor or admin, and owner or same department
                if (!user.hasRole("DOC_EDITOR") && !user.hasRole("DOC_ADMIN")) {
                    return false;
                }
                return document.getOwnerId().equals(user.getId()) ||
                       document.getDepartmentId().equals(user.getDepartmentId());
                       
            case "delete":
                // Only admin or owner
                if (user.hasRole("DOC_ADMIN")) {
                    return true;
                }
                return document.getOwnerId().equals(user.getId());
                
            case "approve":
                // Only DOC_REVIEWER in same department
                return user.hasRole("DOC_REVIEWER") &&
                       document.getDepartmentId().equals(user.getDepartmentId());
                       
            default:
                return false;
        }
    }
}
```

---

## 11. Auto Configuration

### 11.1 Spring Boot Starter Structure

```
┌─────────────────────────────────────────────────────────────────────┐
│                Spring Boot Starter Structure                         │
├─────────────────────────────────────────────────────────────────────┤
                                                                     │
│  security-common-lib/                                                │
│  │                                                                   │
│  ├── pom.xml                                                        │
│  │   ├── Spring Boot Auto-configure                                  │
│  │   ├── Spring Security OAuth2 Resource Server                     │
│  │   └── Nimbus JOSE JWT                                            │
│  │                                                                   │
│  └── src/main/java/com/enterprise/security/                        │
│      ├── SecurityCommonLibAutoConfiguration.java                    │
│      ├── JwtDecoderAutoConfiguration.java                          │
│      ├── SecurityFilterChainAutoConfiguration.java                 │
│      └── WebMvcSecurityAutoConfiguration.java                      │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  META-INF/spring/org.springframework.boot.autoconfigure.     │   │
│  │    -common-autoconfigure-metadata.properties                │   │
│  │  └── org.springframework.boot.autoconfigure.EnableAutoConfiguration=\│
│  │      com.enterprise.security.SecurityCommonLibAutoConfiguration │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 11.2 Auto Configuration Classes

```java
// In security-common-lib
@Configuration
@AutoConfigureAfter(SpringBootAutoConfiguration.class)
public class SecurityCommonLibAutoConfiguration {
    
    // JwtDecoder auto-configuration
    @Configuration
    @EnableConfigurationProperties(JwtDecoderProperties.class)
    @ConditionalOnProperty(prefix = "security.jwt", name = "enabled", 
                          havingValue = "true", matchIfMissing = true)
    public static class JwtDecoderAutoConfiguration {
        
        @Bean
        @ConditionalOnMissingBean(JwtDecoder.class)
        public JwtDecoder jwtDecoder(JwtDecoderProperties properties) {
            return JwtDecoders.fromIssuerLocation(properties.getIssuerUri());
        }
    }
    
    // Security Filter Chain auto-configuration
    @Configuration
    @EnableMethodSecurity(prePostEnabled = true)
    @ConditionalOnProperty(prefix = "security.enabled", havingValue = "true")
    public static class SecurityFilterChainAutoConfiguration {
        
        @Autowired
        private List<SecurityFilterChain> filterChains;
        
        @Bean
        @ConditionalOnMissingBean
        public SecurityFilterChain defaultSecurityFilterChain(
                HttpSecurity http,
                JwtAuthenticationConverter jwtAuthenticationConverter) {
            
            http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> 
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> 
                    oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)));
            
            return http.build();
        }
        
        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new EnterpriseJwtAuthenticationConverter();
        }
    }
    
    // Argument Resolver auto-configuration
    @Configuration
    @EnableWebMvc
    @ConditionalOnProperty(prefix = "security.annotations", 
                          name = "enabled", havingValue = "true", 
                          matchIfMissing = true)
    public static class WebMvcSecurityAutoConfiguration 
            implements WebMvcConfigurer {
        
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new CurrentUserMethodArgumentResolver());
        }
    }
}
```

### 11.3 Usage in Resource Services

```xml
<!-- In document-service pom.xml -->
<dependency>
    <groupId>com.enterprise</groupId>
    <artifactId>security-common-lib</artifactId>
    <version>${security-common-lib.version}</version>
</dependency>
```

```yaml
# In document-service application.yml
security:
  enabled: true
  jwt:
    issuer-uri: https://keycloak.example.com/realms/enterprise
    audience: document-service
  annotations:
    enabled: true

# Optional: override defaults
security:
  jwt:
    clock-skew: 60s
  filter-chain:
    enabled: true
```

```java
// In document-service configuration
@SpringBootApplication
// Auto-configuration is enabled by default
// Just extend if needed
public class DocumentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentServiceApplication.class, args);
    }
}

// Extend if service needs custom config
@Configuration
public class DocumentSecurityConfig {
    
    @Bean
    public SecurityFilterChain documentSecurityFilterChain(
            HttpSecurity http,
            @Autowired(required = false) List<SecurityFilterChain> existingChains) {
        
        // Your custom configuration
        // This will be added to the existing filter chain list
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/documents/public/**").permitAll()
                .requestMatchers("/actuator/health").permitAll());
        
        return http.build();
    }
}
```

---

## 12. Package Structure

### 12.1 Package Organization

```
┌─────────────────────────────────────────────────────────────────────┐
│                security-common-lib Package Structure                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  com.enterprise.security                                            │
│  │                                                                   │
│  ├── jwt/                      # JWT handling                       │
│  │   ├── JwtDecoderProperties.java                                 │
│  │   ├── EnterpriseJwtDecoder.java                                │
│  │   └── JwtClaimsExtractor.java                                   │
│  │                                                                   │
│  ├── converter/                  # Authentication converters         │
│  │   ├── EnterpriseJwtAuthenticationConverter.java                │
│  │   ├── RealmRoleConverter.java                                  │
│  │   └── ClientRoleConverter.java                                 │
│  │                                                                   │
│  ├── principal/                  # User principal                    │
│  │   ├── EnterpriseUserPrincipal.java                             │
│  │   ├── CurrentUserService.java                                  │
│  │   └── UserDetailsMapper.java                                    │
│  │                                                                   │
│  ├── filter/                     # Security filters                  │
│  │   ├── JwtAuthenticationFilter.java                              │
│  │   └── TenantContextFilter.java                                 │
│  │                                                                   │
│  ├── annotation/                 # Security annotations             │
│  │   ├── CurrentUser.java                                        │
│  │   ├── HasRole.java                                             │
│  │   ├── HasAuthority.java                                        │
│  │   ├── HasPermission.java                                        │
│  │   └── RequireAuthentication.java                                │
│  │                                                                   │
│  ├── resolver/                   # Argument resolvers                │
│  │   └── CurrentUserMethodArgumentResolver.java                   │
│  │                                                                   │
│  ├── permission/                 # Permission SPI                    │
│  │   ├── PermissionEvaluator.java                                 │
│  │   ├── PermissionContext.java                                  │
│  │   └── MethodSecurityExpressionHandler.java                     │
│  │                                                                   │
│  ├── exception/                  # Security exceptions                │
│  │   ├── SecurityException.java                                   │
│  │   ├── AuthenticationException.java                             │
│  │   ├── AuthorizationException.java                             │
│  │   ├── JwtAuthenticationException.java                         │
│  │   ├── TokenExpiredException.java                               │
│  │   ├── AccessDeniedException.java                               │
│  │   └── SecurityExceptionHandler.java                            │
│  │                                                                   │
│  ├── config/                     # Auto-configuration               │
│  │   ├── SecurityCommonLibAutoConfiguration.java                  │
│  │   ├── JwtDecoderAutoConfiguration.java                         │
│  │   └── SecurityFilterChainAutoConfiguration.java               │
│  │                                                                   │
│  └── util/                       # Utilities                         │
│      ├── SecurityUtils.java                                       │
│      └── TokenUtils.java                                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 13. Versioning & Compatibility

### 13.1 Versioning Strategy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Versioning Strategy                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  SEMANTIC VERSIONING:                                                │
│  ─────────────────────                                              │
│  {major}.{minor}.{patch}                                           │
│                                                                     │
│  MAJOR: Breaking changes                                            │
│  ├── Changed JWT claims structure                                   │
│  ├── Removed public APIs                                           │
│  └── Changed annotation behavior                                    │
│                                                                     │
│  MINOR: New features (backward compatible)                         │
│  ├── New annotations                                               │
│  ├── New methods in interfaces                                      │
│  └── New configuration properties                                   │
│                                                                     │
│  PATCH: Bug fixes (backward compatible)                            │
│  ├── Security fixes                                                │
│  ├── Bug fixes                                                    │
│  └── Performance improvements                                      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 13.2 Backward Compatibility

| Change Type | Compatible | Notes |
|-------------|------------|-------|
| Add new method to interface | ✅ | Implementations unaffected |
| Add new annotation | ✅ | Existing code unaffected |
| Add new configuration property | ✅ | Default values |
| Add new exception type | ✅ | Existing exception handling |
| Remove deprecated method | ❌ | Major version |
| Change annotation behavior | ❌ | Major version |
| Remove configuration property | ❌ | Major version |
| Change exception hierarchy | ❌ | Major version |

### 13.3 Deprecation Strategy

```java
// Deprecation example
@Deprecated(since = "2.0.0", forRemoval = true)
@DeprecatedRemoval(version = "3.0.0")
public class OldPrincipal {
    // Will be removed in version 3.0.0
}

// Migration path in release notes
## Migration from 1.x to 2.0
### Breaking Changes
1. `OldPrincipal` replaced by `EnterpriseUserPrincipal`
2. Use `CurrentUserService.getCurrentUser()` instead
```

---

## 14. Testing Strategy

### 14.1 Testing Layers

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Testing Strategy                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  UNIT TESTS:                                                        │
│  ├── JwtAuthenticationConverter tests                              │
│  ├── EnterpriseUserPrincipal tests                                 │
│  ├── PermissionEvaluator mocks                                    │
│  └── Security annotation tests                                     │
│                                                                     │
│  INTEGRATION TESTS:                                                 │
│  ├── JWT validation with real Keycloak (test realm)               │
│  ├── SecurityFilterChain integration                               │
│  ├── Auto-configuration tests                                     │
│  └── Argument resolver tests                                       │
│                                                                     │
│  CONTRACT TESTS:                                                    │
│  ├── Ensure compatibility with Keycloak token format             │
│  └── JWT claims structure compatibility                           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 14.2 Test Utilities

```java
// Test utilities in common-lib for services to use
public class SecurityTestUtils {
    
    // Create mock JWT
    public static Jwt createMockJwt(String userId, String... roles) {
        Map<String, Object> headers = Map.of(
            "alg", "RS256",
            "typ", "JWT"
        );
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("preferred_username", "testuser");
        claims.put("email", "test@example.com");
        claims.put("realm_access", Map.of("roles", Arrays.asList(roles)));
        
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600),
                       headers, claims);
    }
    
    // Create mock Authentication
    public static Authentication createMockAuthentication(
            EnterpriseUserPrincipal principal, 
            GrantedAuthority... authorities) {
        return new UsernamePasswordAuthenticationToken(
            principal, null, Arrays.asList(authorities));
    }
    
    // Security context setup
    public static void setSecurityContext(Authentication auth) {
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}

// Example test
class EnterpriseJwtAuthenticationConverterTest {
    
    @Test
    void shouldExtractRealmRoles() {
        // Given
        Jwt jwt = SecurityTestUtils.createMockJwt("user-1", "USER", "ADMIN");
        JwtAuthenticationConverter converter = new EnterpriseJwtAuthenticationConverter();
        
        // When
        AbstractAuthenticationToken result = converter.convert(jwt);
        
        // Then
        assertThat(result.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }
}
```

---

## 15. Anti-patterns & Boundaries

### 15.1 Anti-patterns

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ANTI-PATTERNS - KHÔNG LÀM                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ❌ 1. Business Logic in Common-Lib                                │
│     Common-lib chỉ chứa infrastructure, không business logic        │
│     Ví dụ sai: canApproveDocument(), canEditWorkflow()            │
│     Đúng: Permission interfaces + service implementations          │
│                                                                     │
│  ❌ 2. User Management in Common-Lib                             │
│     Không CRUD user, không role management                         │
│     Rủi ro: Boundary confusion, circular dependencies               │
│                                                                     │
│  ❌ 3. Direct Database Access                                     │
│     Không access database trực tiếp                               │
│     Rủi ro: Coupling, not reusable                                 │
│                                                                     │
│  ❌ 4. Keycloak Admin API Calls                                   │
│     Không gọi Keycloak Admin API                                   │
│     Rủi ro: IAM-Service should handle this                         │
│                                                                     │
│  ❌ 5. Session Storage                                             │
│     Không store sessions, tokens                                  │
│     Rủi ro: Stateless design violated                             │
│                                                                     │
│  ❌ 6. Hard-coded Business Rules                                  │
│     Không hard-code domain-specific rules                         │
│     Rủi ro: Not reusable across services                           │
│                                                                     │
│  ❌ 7. Blocking I/O in Security Filters                           │
│     Security filters phải nhanh, không blocking                    │
│     Rủi ro: Latency, thread blocking                               │
│                                                                     │
│  ❌ 8. Too Many Dependencies                                      │
│     Giữ dependencies tối thiểu                                   │
│     Rủi ro: Heavy, conflicts with service dependencies              │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 15.2 Clear Boundaries

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Common-Lib Boundaries                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  security-common-lib ĐƯỢC CHỨA:                                    │
│  ✅ JWT validation infrastructure                                   │
│  ✅ Authentication/authorization abstractions                       │
│  ✅ Permission interfaces (SPI)                                   │
│  ✅ Security annotations                                           │
│  ✅ Exception handling (security-specific)                         │
│  ✅ Principal abstractions                                         │
│  ✅ Security utilities                                             │
│  ✅ Auto-configuration                                            │
│  ✅ Test utilities                                                 │
│                                                                     │
│  security-common-lib KHÔNG CHỨA:                                  │
│  ❌ Business authorization logic                                   │
│  ❌ Workflow permission rules                                       │
│  ❌ User management                                               │
│  ❌ Role management                                                │
│  ❌ Organization logic                                            │
│  ❌ Database access                                                │
│  ❌ Keycloak Admin API                                             │
│  ❌ Session storage                                                │
│  ❌ Business-specific permissions                                  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 15.3 What Belongs Where

| Security Concern | Where | Reason |
|-----------------|-------|--------|
| JWT validation | common-lib | Shared infrastructure |
| Role-based auth | common-lib + service | Converter in lib, rules in service |
| Business permission | Resource Service | Domain-specific |
| User lookup | IAM-Service API | Centralized management |
| Token refresh | Auth-Service | Login orchestration |
| Session management | Auth-Service | Cookie handling |

---

## 16. Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│              security-common-lib - SUMMARY                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  VAI TRÒ:                                                           │
│  ├── Shared security infrastructure for all services               │
│  ├── Standardized JWT validation                                    │
│  ├── Consistent security annotations                                │
│  └── Extensible permission system (SPI)                            │
│                                                                     │
│  ĐẶC ĐIỂM:                                                         │
│  ├── Spring Boot Starter for easy integration                       │
│  ├── Auto-configuration                                            │
│  ├── Production-ready                                               │
│  └── Well-tested                                                    │
│                                                                     │
│  INTEGRATION:                                                       │
│  ├── → Keycloak: For token validation                              │
│  ├── ← Resource Services: Import library, implement permissions   │
│  └── ← Auth-Service: For unified error responses                   │
│                                                                     │
│  NGUYÊN TẮC VÀNG:                                                   │
│  🔐 Common-lib = Security Infrastructure, NOT Business Logic       │
│  🔐 Business authorization = Resource Services                    │
│  🔐 Keep it simple, keep it shared                                 │
│  🔐 Extension via SPI, not modification                            │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```
