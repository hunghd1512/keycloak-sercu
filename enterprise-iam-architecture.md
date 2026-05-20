# Enterprise IAM Architecture
# Keycloak + Auth-Service + IAM-Service + Common Security Lib

---

# 1. Architecture Overview

```text
                                +----------------------+
                                |      Keycloak        |
                                | Authorization Server |
                                +----------------------+
                                   ^              ^
                                   |              |
                            OIDC APIs        Admin APIs
                                   |              |
                    +-------------------+   +-------------------+
                    |   Auth-Service    |   |    IAM-Service    |
                    +-------------------+   +-------------------+
                              ^                       ^
                              |                       |
                           Frontend              Admin Portal

        ----------------------------------------------------------------

                    +----------------------------------------------+
                    |              Resource Services                |
                    | document-service / workflow / order-service  |
                    +----------------------------------------------+
                                      ^
                                      |
                             security-common-lib
```

---

# 2. Responsibility Separation

| Component | Responsibility |
|---|---|
| Keycloak | Authentication / Token Issuing |
| Auth-Service | Login orchestration / Session / BFF |
| IAM-Service | User management / Role / Organization |
| Resource Services | Business APIs |
| security-common-lib | Shared security infrastructure |

---

# 3. Keycloak Responsibilities

## Keycloak handles

- Authentication
- Password management
- MFA
- Access token
- Refresh token
- SSO
- Identity federation
- OAuth2/OIDC

---

## Keycloak should NOT handle

- Business authorization
- Workflow permission
- Domain ownership logic
- Organization workflow

---

# 4. Auth-Service

## Purpose

```text
OAuth2 Client + BFF + Authentication Gateway
```

Auth-Service is NOT an Authorization Server.

---

# 4.1 Responsibilities

## Login orchestration

```text
Frontend -> Auth-Service -> Keycloak
```

---

## Session management

- HttpOnly cookie
- CSRF protection
- Secure session storage
- Logout synchronization

---

## Token exchange

- authorization_code
- refresh_token
- PKCE flow

---

## Hide tokens from frontend

Frontend should not store:
- refresh token
- long-lived access token

---

## Unified authentication API

### APIs

```text
POST /auth/login
POST /auth/logout
POST /auth/refresh
GET  /auth/me
```

---

# 4.2 Internal Modules

```text
auth-service
 ├── authentication
 ├── oauth2
 ├── keycloak
 ├── session
 ├── csrf
 ├── cookie
 ├── audit
 └── security
```

---

# 4.3 Auth-Service SHOULD NOT

## DO NOT

- create user
- manage role
- manage organization
- manage business permission
- issue custom JWT

---

# 4.4 Database

Optional lightweight DB:

```text
auth_session
login_audit
device_login
refresh_token_tracking
```

---

# 5. IAM-Service

## Purpose

```text
Identity & Access Management Facade
```

---

# 5.1 Responsibilities

## User management

- create user
- disable user
- unlock user
- reset password
- assign groups

---

## Role management

- realm role
- client role
- group role
- role mapping

---

## Organization management

Examples:

```text
department
tenant
agency
division
```

---

## Business IAM rules

Examples:

```text
Only SUPER_ADMIN can assign ADMIN
```

```text
Department admin only manages own users
```

---

## User profile aggregation

Examples:

```text
avatar
phone
position
department
workflow role
```

---

## Audit logging

- role assignment
- password reset
- account lock/unlock

---

# 5.2 IAM-Service APIs

## User APIs

```text
POST   /users
GET    /users/{id}
PUT    /users/{id}
DELETE /users/{id}
```

---

## Role APIs

```text
POST /roles
POST /users/{id}/roles
```

---

## Organization APIs

```text
POST /organizations
GET  /organizations/tree
```

---

## Permission APIs

```text
GET  /permissions
POST /roles/{id}/permissions
```

---

# 5.3 Internal Modules

```text
iam-service
 ├── users
 ├── roles
 ├── permissions
 ├── organizations
 ├── workflow
 ├── keycloak
 ├── policy
 └── audit
```

---

# 5.4 IAM-Service SHOULD NOT

## DO NOT

- login user
- manage session
- issue access token
- replace Keycloak authentication

---

# 6. security-common-lib

## Purpose

Reusable Spring Security starter for all resource services.

---

# 6.1 Responsibilities

## JWT validation

- issuer-uri
- jwk-set-uri
- decoder
- signature validation

---

## SecurityFilterChain

Shared OAuth2 Resource Server configuration.

---

## JwtAuthenticationConverter

Convert:
- realm roles
- client roles
- scopes

to:

```text
GrantedAuthority
```

---

## Current User abstraction

Examples:

```java
CurrentUser
CurrentUserService
UserPrincipal
```

---

## Shared annotations

Examples:

```java
@CurrentUser
@HasRole
@HasPermission
```

---

## Shared exception handling

- 401
- 403

---

## Permission SPI

Shared abstraction only.

Example:

```java
public interface PermissionEvaluator {
    boolean hasPermission(...);
}
```

Business services implement actual logic.

---

# 6.2 Structure

```text
security-common-lib
 ├── jwt
 ├── converter
 ├── annotation
 ├── principal
 ├── filter
 ├── config
 ├── exception
 ├── permission
 │     ├── PermissionEvaluator
 │     └── PermissionContext
 └── autoconfigure
```

---

# 6.3 security-common-lib SHOULD NOT

## DO NOT place business authorization here

Bad examples:

```java
canApproveDocument()
canEditWorkflow()
```

These belong to business services.

---

# 7. Resource Services

Examples:

```text
document-service
workflow-service
order-service
```

---

# 7.1 Responsibilities

## Validate JWT

Using:

```text
spring-security-oauth2-resource-server
```

through:

```text
security-common-lib
```

---

## Business authorization

Examples:

```text
Only creator can edit document
```

```text
Department A cannot approve Department B documents
```

---

## Domain processing

- workflow
- document processing
- business rules

---

# 7.2 Resource Services SHOULD NOT

## DO NOT

- call Keycloak Admin API directly
- manage global users
- manage authentication

---

# 8. Login Flow

## Step 1

Frontend:

```text
GET /auth/login
```

---

## Step 2

Auth-Service redirects to Keycloak authorize endpoint.

---

## Step 3

User logs in on Keycloak.

---

## Step 4

Keycloak redirects callback:

```text
/auth/callback
```

---

## Step 5

Auth-Service:
- exchanges token
- creates session
- sets HttpOnly cookie

---

## Step 6

Frontend calls APIs using secure cookie/session.

---

# 9. User Creation Flow

## Step 1

Admin Portal:

```text
POST /iam/users
```

---

## Step 2

IAM-Service validates business rules.

---

## Step 3

IAM-Service calls Keycloak Admin API.

---

## Step 4

Create user + assign roles/groups.

---

## Step 5

Sync local business profile DB.

---

# 10. Source of Truth

| Data | Source |
|---|---|
| Password | Keycloak |
| JWT | Keycloak |
| Authentication | Keycloak |
| Session | Auth-Service |
| User profile | IAM-Service DB |
| Organization | IAM-Service DB |
| Workflow permission | Resource Service |
| Business data | Resource Service DB |

---

# 11. Security Best Practices

## SHOULD

- PKCE
- HttpOnly cookie
- Short-lived token
- Refresh token rotation
- CSRF protection
- Distributed business authorization
- Centralized IAM mutation

---

## SHOULD NOT

- Put all permissions inside JWT
- Let all services call Keycloak Admin API
- Rebuild OAuth2 server manually
- Expose admin token to frontend

---

# 12. Recommended Spring Stack

## Keycloak

- Authentication
- Token issuing
- SSO

---

## Auth-Service

- Spring Security OAuth2 Client
- Session management
- BFF pattern

---

## IAM-Service

- Keycloak Admin Client
- User management
- RBAC management

---

## Resource Services

- spring-security-oauth2-resource-server
- Business authorization

---

## Shared Library

```text
security-common-lib
```

Reusable security infrastructure starter.

---

# 13. Important Architecture Rule

## Centralized Authentication

```text
Keycloak
```

---

## Distributed Authorization

```text
Each business service validates its own business rules
```

DO NOT create centralized business authorization service.

---

# 14. Recommended Final Architecture

## Best balance for enterprise microservice

```text
Keycloak
    ->
Auth-Service
    ->
IAM-Service
    ->
Resource Services
```

With:

```text
security-common-lib
```

shared across all resource services.
