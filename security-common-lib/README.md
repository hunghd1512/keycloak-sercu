# Enterprise Security Common Library

A shared security infrastructure library for enterprise microservices with Spring Boot 3 and Spring Security 6.

## Overview

This library provides a comprehensive security foundation that can be imported by any microservice to get standardized JWT authentication, role-based access control, and custom permission evaluation out of the box.

```
┌─────────────────────────────────────────────────────────────────┐
│              security-common-lib Purpose                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│         ┌─────────────────────────────────────────────────────┐   │
│         │           security-common-lib                        │   │
│         │     (Spring Boot Security Starter)                   │   │
│         └─────────────────────────────────────────────────────┘   │
│                                │                                  │
│         ┌──────────────────────┼──────────────────────┐        │
│         │                      │                      │         │
│         ▼                      ▼                      ▼         │
│  ┌─────────────┐        ┌─────────────┐        ┌─────────────┐ │
│  │  document- │        │  workflow- │        │   order-   │ │
│  │  service   │        │  service   │        │  service   │ │
│  └─────────────┘        └─────────────┘        └─────────────┘ │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

## Features

- **JWT Validation**: Automatic JWT token validation with JWKS support from Keycloak
- **Role-Based Access Control**: Realm and client role extraction from Keycloak tokens
- **Custom Annotations**: `@CurrentUser`, `@HasRole`, `@HasAuthority`, `@HasPermission`
- **Permission SPI**: Extensible permission evaluation for domain-specific authorization
- **Auto-Configuration**: Spring Boot starter for zero-configuration setup
- **Test Utilities**: Helper classes for security testing

## Installation

### Maven

```xml
<dependency>
    <groupId>com.enterprise</groupId>
    <artifactId>security-common-lib</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.enterprise:security-common-lib:1.0.0'
```

## Quick Start

### 1. Add Dependency

Add the library to your `pom.xml` or `build.gradle`.

### 2. Configure Application

```yaml
# application.yml
security:
  enabled: true
  jwt:
    issuer-uri: https://keycloak.example.com/realms/enterprise
    # OR use explicit JWK Set URI
    jwk-set-uri: https://keycloak.example.com/realms/enterprise/protocol/openid-connect/certs
```

### 3. Use Annotations

```java
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    @GetMapping("/{id}")
    public Document getDocument(@PathVariable String id) {
        // Your implementation
    }

    @PostMapping
    @HasRole("DOC_EDITOR")
    public Document createDocument(@RequestBody CreateDocumentRequest request) {
        // Only DOC_EDITORS can access this
    }

    @DeleteMapping("/{id}")
    @HasPermission(value = "document", arg = "#id")
    public void deleteDocument(@PathVariable String id) {
        // Permission check via PermissionEvaluator
    }
}
```

## Configuration

### Security Properties

```yaml
security:
  enabled: true                    # Enable/disable security (default: true)
  
  jwt:
    enabled: true                  # Enable JWT validation (default: true)
    issuer-uri: https://...       # Keycloak issuer URI
    jwk-set-uri: https://...      # Explicit JWKS endpoint
    audience: my-service           # Expected audience (optional)
    clock-skew-seconds: 60        # Token clock skew tolerance
  
  filter-chain:
    csrf-enabled: false            # Enable CSRF protection
    cors-enabled: true             # Enable CORS
    session-creation-policy: STATELESS
  
  annotations:
    enabled: true                 # Enable annotation support
    pre-post-enabled: true         # Enable @PreAuthorize
```

## Usage Examples

### Current User Injection

```java
@GetMapping("/profile")
public UserProfile getProfile(@CurrentUser EnterpriseUserPrincipal user) {
    return userService.getById(user.getId());
}
```

### Role-Based Access

```java
@PostMapping("/admin")
@HasRole("ADMIN")
public void adminOnlyAction() {
    // Only users with ADMIN role can access
}

@GetMapping("/moderator-or-admin")
@HasRole({"ADMIN", "MODERATOR"})
public void modOrAdminAction() {
    // ADMINs or MODERATORs can access
}
```

### Authority-Based Access

```java
@PostMapping("/publish")
@HasAuthority("ROLE_DOC_EDITOR")
public void publish() {
    // Only with specific authority
}
```

### Custom Permissions (Permission SPI)

```java
// 1. Implement PermissionEvaluator
@Component
public class DocumentPermissionEvaluator implements PermissionEvaluator {
    
    @Override
    public boolean hasPermission(Authentication auth, Object target, Object permission) {
        if (target instanceof Document doc) {
            return checkDocumentPermission(auth, doc, permission);
        }
        return false;
    }
    
    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId, 
                                  String targetType, Object permission) {
        if ("document".equals(targetType)) {
            Document doc = documentRepository.findById((String) targetId);
            return checkDocumentPermission(auth, doc, permission);
        }
        return false;
    }
}

// 2. Use in controller
@DeleteMapping("/{id}")
@HasPermission(value = "document", arg = "#id")
public void deleteDocument(@PathVariable String id) {
    // Permission evaluated by DocumentPermissionEvaluator
}
```

### CurrentUserService

```java
@Service
public class DocumentService {
    
    @Autowired
    private CurrentUserService currentUserService;
    
    public Document createDocument(CreateDocumentRequest request) {
        EnterpriseUserPrincipal user = currentUserService.requireCurrentUser();
        
        return Document.builder()
            .title(request.getTitle())
            .ownerId(user.getId())
            .departmentId(user.getStringAttribute("department_id"))
            .build();
    }
}
```

## API Reference

### Annotations

| Annotation | Purpose | Example |
|-----------|---------|---------|
| `@CurrentUser` | Inject authenticated user | `@CurrentUser EnterpriseUserPrincipal user` |
| `@HasRole` | Require realm role(s) | `@HasRole("ADMIN")` |
| `@HasAuthority` | Require any authority | `@HasAuthority("ROLE_DOC_EDITOR")` |
| `@HasPermission` | Custom permission check | `@HasPermission(value = "doc", arg = "#id")` |
| `@RequireAuthentication` | Require authentication | `@RequireAuthentication` |

### Principal Methods

```java
EnterpriseUserPrincipal user = currentUserService.getCurrentUser();

user.getId();           // User's subject ID
user.getUsername();     // Username
user.getEmail();        // Email
user.getDisplayName();  // Display name

user.hasRole("ADMIN");           // Check realm role
user.hasClientRole("app", "EDITOR");  // Check client role
user.hasAuthority("SCOPE_read"); // Check any authority
```

## Publishing to Nexus

### Maven Release

```bash
# Configure Nexus credentials in ~/.m2/settings.xml
mvn clean deploy -P release
```

### Gradle

```groovy
// build.gradle
publishing {
    publications {
        maven(MavenPublication) {
            from components.java
            pom {
                name = 'Enterprise Security Common Library'
                description = 'Shared security infrastructure for microservices'
            }
        }
    }
    repositories {
        maven {
            url = 'https://nexus.example.com/repository/maven-releases/'
            credentials {
                username = System.getenv('NEXUS_USERNAME')
                password = System.getenv('NEXUS_PASSWORD')
            }
        }
    }
}
```

## Project Structure

```
security-common-lib/
├── src/main/java/com/enterprise/security/
│   ├── annotation/           # Security annotations
│   ├── config/              # Auto-configuration
│   ├── converter/           # JWT authentication converters
│   ├── exception/           # Security exceptions
│   ├── filter/              # Security filters
│   ├── jwt/                 # JWT utilities
│   ├── permission/          # Permission SPI
│   ├── principal/           # User principal
│   ├── resolver/            # Method argument resolvers
│   └── util/                # Utilities
└── src/main/resources/
    └── META-INF/            # Spring auto-configuration
```

## Testing

```java
@SpringBootTest
class DocumentControllerTest {

    @Test
    @WithMockUser(roles = "DOC_EDITOR")
    void shouldAllowEditorToCreateDocument() {
        // Test implementation
    }
}
```

Using test utilities:

```java
@Test
void testWithMockJwt() {
    Jwt jwt = SecurityTestUtils.createMockJwt("user-1", "testuser", List.of("ADMIN"));
    
    // Use jwt in tests
}
```

## Dependencies

- Spring Boot 3.2+
- Spring Security 6
- Spring OAuth2 Resource Server
- Nimbus JOSE JWT

## License

Proprietary - Enterprise IAM Solution
