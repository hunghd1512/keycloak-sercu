# System Overview - Tài liệu Thiết kế Kiến trúc Tổng thể

## Mục lục

1. [Tổng quan Kiến trúc](#1-tổng-quan-kiến-trúc)
2. [Trách nhiệm từng thành phần](#2-trách-nhiệm-từng-thành-phần)
3. [Authentication Flow](#3-authentication-flow)
4. [Authorization Flow](#4-authorization-flow)
5. [User & Role Management Flows](#5-user--role-management-flows)
6. [API Gateway Integration](#6-api-gateway-integration)
7. [Service-to-Service Communication](#7-service-to-service-communication)
8. [Deployment Topology](#8-deployment-topology)
9. [Security Best Practices](#9-security-best-practices)
10. [Observability & Logging](#10-observability--logging)
11. [Anti-patterns & Common Mistakes](#11-anti-patterns--common-mistakes)
12. [Decision Rationale](#12-decision-rationale)

---

## 1. Tổng quan Kiến trúc

### 1.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              INTERNET                                        │
│                                   │                                         │
│                                   ▼                                         │
│                    ┌─────────────────────────┐                             │
│                    │       API Gateway        │                             │
│                    │   (Kong/AWS ALB/NGINX)  │                             │
│                    │                         │                             │
│                    │  - Route management     │                             │
│                    │  - SSL termination      │                             │
│                    │  - Rate limiting        │                             │
│                    │  - CORS handling        │                             │
│                    └───────────┬─────────────┘                             │
│                                │                                           │
│         ┌──────────────────────┼──────────────────────┐                   │
│         │                      │                      │                    │
│         ▼                      ▼                      ▼                    │
│  ┌─────────────┐       ┌─────────────┐       ┌─────────────┐           │
│  │    Auth-   │       │    IAM-     │       │   Resource  │           │
│  │  Service   │       │   Service   │       │   Services  │           │
│  │    (BFF)   │       │             │       │             │           │
│  └──────┬──────┘       └──────┬──────┘       └──────┬──────┘           │
│         │                     │                      │                    │
└─────────┼─────────────────────┼──────────────────────┼────────────────────┘
          │                     │                      │
          │   ┌─────────────────┼──────────────────────┘
          │   │                 │
          ▼   ▼                 ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PRIVATE NETWORK                                     │
│                                                                            │
│   ┌─────────────────────────────────────────────────────────────────┐     │
│   │                        KEYCLOAK                                 │     │
│   │                    Authorization Server                          │     │
│   │                                                               │     │
│   │   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌───────────────┐  │     │
│   │   │OAuth2/  │  │  User   │  │ Token   │  │  Identity     │  │     │
│   │   │OIDC     │  │Provider │  │Issuer  │  │ Federation    │  │     │
│   │   └─────────┘  └─────────┘  └─────────┘  └───────────────┘  │     │
│   │                                                               │     │
│   │   ┌───────────────────────────────────────────────────────┐   │     │
│   │   │  Realm: enterprise                                    │   │     │
│   │   │  - Clients: auth-service, iam-service, resource-svcs │   │     │
│   │   │  - Realm Roles: SUPER_ADMIN, ADMIN, USER            │   │     │
│   │   │  - MFA, Password Policy, SSO                          │   │     │
│   │   └───────────────────────────────────────────────────────┘   │     │
│   └─────────────────────────────────────────────────────────────────┘     │
│                                    │                                     │
│                                    │ Admin API                            │
│                                    ▼                                     │
│   ┌─────────────────────────────────────────────────────────────────┐     │
│   │                       DATABASE LAYER                            │     │
│   │                                                               │     │
│   │   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │     │
│   │   │   Keycloak DB   │  │   IAM-Service   │  │  Resource  │ │     │
│   │   │  (PostgreSQL)   │  │  Database       │  │  Databases  │ │     │
│   │   │                 │  │  (PostgreSQL)   │  │             │ │     │
│   │   │ - Users         │  │ - User Profiles │  │ - Domain    │ │     │
│   │   │ - Passwords     │  │ - Organizations │  │ - Business  │ │     │
│   │   │ - Groups        │  │ - Audit Logs   │  │   Data     │ │     │
│   │   │ - Realm Config  │  │ - Permissions  │  │             │ │     │
│   │   └─────────────────┘  └─────────────────┘  └─────────────┘ │     │
│   │                                                               │     │
│   └─────────────────────────────────────────────────────────────────┘     │
│                                                                            │
│   ┌─────────────────────────────────────────────────────────────────┐     │
│   │                       CACHING LAYER                              │     │
│   │                                                               │     │
│   │   ┌───────────────────────────────────────────────────────┐    │     │
│   │   │                     REDIS                                │    │     │
│   │   │  - Session (Auth-Service)                             │    │     │
│   │   │  - User cache (IAM-Service)                           │    │     │
│   │   │  - Permission cache (Resource Services)              │    │     │
│   │   └───────────────────────────────────────────────────────┘    │     │
│   │                                                               │     │
│   └─────────────────────────────────────────────────────────────────┘     │
│                                                                            │
│   ┌─────────────────────────────────────────────────────────────────┐     │
│   │                       MESSAGE BUS                               │     │
│   │                                                               │     │
│   │   ┌───────────────────────────────────────────────────────┐    │     │
│   │   │                    KAFKA / RABBITMQ                    │    │     │
│   │   │  - User events sync (Keycloak → IAM)                 │    │     │
│   │   │  - Cache invalidation events                          │    │     │
│   │   │  - Audit events                                      │    │     │
│   │   │  - Cross-service notifications                        │    │     │
│   │   └───────────────────────────────────────────────────────┘    │     │
│   │                                                               │     │
│   └─────────────────────────────────────────────────────────────────┘     │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Component Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    COMPONENT RESPONSIBILITIES                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐ │
│  │    Frontend     │  │   API Gateway   │  │       Keycloak              │ │
│  ├─────────────────┤  ├─────────────────┤  ├─────────────────────────────┤ │
│  │                 │  │                 │  │                             │ │
│  │ - SPA/Mobile    │  │ - Routing       │  │ - Authentication           │ │
│  │ - Auth flow UI  │  │ - SSL           │  │ - Token Issuance           │ │
│  │ - Session mgmt  │  │ - Rate limit    │  │ - SSO                      │ │
│  │ - Token refresh │  │ - CORS          │  │ - Identity Federation      │ │
│  │                 │  │ - Load balance  │  │ - MFA                      │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘ │
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐ │
│  │   Auth-Service   │  │   IAM-Service   │  │     Resource Services       │ │
│  ├─────────────────┤  ├─────────────────┤  ├─────────────────────────────┤ │
│  │                 │  │                 │  │                             │ │
│  │ - Login BFF     │  │ - User mgmt     │  │ - Business APIs            │ │
│  │ - Session mgmt  │  │ - Role mgmt     │  │ - Domain logic             │ │
│  │ - Token exhange │  │ - Org mgmt      │  │ - Business authorization   │ │
│  │ - Cookie mgmt   │  │ - Keycloak sync │  │ - JWT validation          │ │
│  │ - SSO flow      │  │ - Audit logging │  │ - security-common-lib     │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘ │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                     security-common-lib                              │ │
│  ├───────────────────────────────────────────────────────────────────────┤ │
│  │                                                                       │ │
│  │ - JWT validation          - Permission interfaces (SPI)              │ │
│  │ - Auth converters         - Security annotations                     │ │
│  │ - CurrentUser abstraction - Exception handling                      │ │
│  │                                                                       │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Trách nhiệm từng thành phần

### 2.1 Component Responsibility Matrix

| Thành phần | Authentication | Authorization | User Mgmt | Token Mgmt | Session Mgmt |
|------------|---------------|---------------|-----------|------------|-------------|
| **Keycloak** | ✅ Primary | ❌ | ❌ | ✅ | ✅ |
| **Auth-Service** | ✅ Orchestration | ❌ | ❌ | ✅ Exchange | ✅ (Redis) |
| **IAM-Service** | ❌ | ❌ | ✅ Primary | ❌ | ❌ |
| **Resource Services** | ❌ | ✅ Business | ❌ | ❌ | ❌ |
| **common-lib** | ✅ JWT validation | ✅ Infra | ❌ | ❌ | ❌ |

### 2.2 Auth-Service Trách nhiệm Chi tiết

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      AUTH-SERVICE (Backend-for-Frontend)                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  TRÁCH NHIỆM CHÍNH:                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ✅ Login Orchestration - Gọi Keycloak OAuth2 thay cho Frontend     │   │
│  │  ✅ Session Management - Lưu session trong Redis                     │   │
│  │  ✅ Cookie Management - Set HttpOnly, Secure, SameSite cookies      │   │
│  │  ✅ Token Exchange - Authorization code, refresh, revoke             │   │
│  │  ✅ User Info API - Trả về thông tin user từ session               │   │
│  │  ✅ Concurrent Session Limit - Max 3 sessions/user (configurable)   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  KHÔNG ĐẢM NHIỆM:                                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ❌ User/Role Management (IAM-Service đảm nhiệm)                    │   │
│  │  ❌ Business Authorization (Resource Services đảm nhiệm)             │   │
│  │  ❌ JWT Validation cho API calls (common-lib đảm nhiệm)             │   │
│  │  ❌ Keycloak Admin API (chỉ gọi /token, /userinfo, /logout)      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  REDIS SESSION STRUCTURE:                                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Key: auth:sessions:{sessionId}    → Session (TTL: 8h)             │   │
│  │  Key: auth:user-sessions:{userId} → Set of session IDs            │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 Source of Truth Table

| Dữ liệu | Nguồn tin cậy (SoT) | Ghi chú |
|----------|---------------------|----------|
| Credentials (password) | Keycloak | Không bao giờ lưu ở nơi khác |
| Authentication | Keycloak | Token được sign bởi Keycloak |
| JWT Token | Keycloak | Signature verification |
| User Profile (basic) | Keycloak | username, email, name |
| User Profile (extended) | IAM-DB | department, title, custom attrs |
| Organization | IAM-DB | Hierarchy, relationships |
| Role Definitions | Keycloak | Realm & Client roles |
| Role Assignments | Keycloak | Nguồn chính |
| Business Permissions | Resource Services | Domain-specific |
| Business Data | Resource Services DB | Domain data |
| Session | Auth-Service/Redis | HttpOnly cookie |
| Audit Logs | IAM-DB + Services | Distributed |

---

## 3. Authentication Flow

### 3.1 Auth-Service Login Flow (Password Grant)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AUTH-SERVICE LOGIN FLOW (Password Grant)                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Browser          Auth-Service         Keycloak         Redis                │
│   ───────          ───────────         ────────         ──────                │
│       │                │                   │                │                 │
│       │  1. POST /auth/login           │                │                 │
│       │  {username, password}           │                │                 │
│       │───────────────▶│                   │                │                 │
│       │                │                   │                │                 │
│       │                │  2. POST /token              │                │
│       │                │  grant_type=password         │                │
│       │                │  client_id + client_secret   │                │
│       │                │  username + password        │                │
│       │                │──────────────────▶│                │                 │
│       │                │                   │                │                 │
│       │                │  3. Tokens (access, refresh, id) │                │
│       │                │◀──────────────────│                │                 │
│       │                │                   │                │                 │
│       │                │  4. Create session in Redis    │                 │
│       │                │  {                             │                 │
│       │                │    sessionId,                  │                 │
│       │                │    userId,                     │                 │
│       │                │    tokens...                  │                 │
│       │                │  }                           │                 │
│       │                │─────────────────────▶│                │                 │
│       │                │                   │                │                 │
│       │                │  5. Set HttpOnly Cookie       │                 │
│       │  Set-Cookie: AUTH_SESSION_ID=xxx   │                │                 │
│       │◀──────────────│                   │                │                 │
│       │                │                   │                │                 │
│       │  6. Response + access token       │                │                 │
│       │◀──────────────│                   │                │                 │
│       │                │                   │                │                 │
│       │  END - User logged in successfully │                │                 │
│                                                                             │
│   LỢI ÍCH SO VỚI GỌI THẲNG KEYCLOAK:                                      │
│   ✅ Client secret không bao giờ expose trong frontend                       │
│   ✅ Token được giữ ở server (Redis), không lưu localStorage                 │
│   ✅ HttpOnly cookie bảo vệ khỏi XSS                                       │
│   ✅ Session management tập trung                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Token Refresh Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          TOKEN REFRESH FLOW                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Browser          Auth-Service         Keycloak         Redis                │
│   ───────          ───────────         ────────         ──────                │
│       │                │                   │                │                 │
│       │  1. API Request + Bearer Token │                │                 │
│       │────────────────▶│                   │                │                 │
│       │                │                   │                │                 │
│       │  2. 401 Unauthorized            │                │                 │
│       │◀────────────────│                   │                │                 │
│       │                │                   │                │                 │
│       │  3. POST /auth/refresh         │                │                 │
│       │  Cookie: AUTH_SESSION_ID=xxx    │                │                 │
│       │────────────────▶│                   │                │                 │
│       │                │                   │                │                 │
│       │                │  4. Get session from Redis   │                 │
│       │                │◀─────────────────────│                │                 │
│       │                │                   │                │                 │
│       │                │  5. POST /token  │                │                 │
│       │                │  grant_type=refresh_token       │                 │
│       │                │  refresh_token=xxx             │                 │
│       │                │──────────────────▶│                │                 │
│       │                │                   │                │                 │
│       │                │  6. New tokens   │                │                 │
│       │                │◀──────────────────│                │                 │
│       │                │                   │                │                 │
│       │                │  7. Update session in Redis     │                 │
│       │                │─────────────────────▶│                │                 │
│       │                │                   │                │                 │
│       │  8. New access token              │                │                 │
│       │◀────────────────│                   │                │                 │
│       │                │                   │                │                 │
│       │  9. Retry original request       │                │                 │
│       │────────────────▶│                   │                │                 │
│       │                │                   │                │                 │
│       │  10. Response  │                   │                │                 │
│       │◀────────────────│                   │                │                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 Logout Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            LOGOUT FLOW                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Browser          Auth-Service         Keycloak         Redis                │
│   ───────          ───────────         ────────         ──────                │
│       │                │                   │                │                 │
│       │  1. POST /auth/logout          │                │                 │
│       │  Cookie: AUTH_SESSION_ID=xxx     │                │                 │
│       │───────────────▶│                   │                │                 │
│       │                │                   │                │                 │
│       │                │  2. Get session from Redis     │                 │
│       │                │◀─────────────────────│                │                 │
│       │                │                   │                │                 │
│       │                │  3. Revoke refresh token       │                 │
│       │                │  POST /token/revoke           │                 │
│       │                │──────────────────▶│                │                 │
│       │                │                   │                │                 │
│       │                │  4. Success     │                │                 │
│       │                │◀──────────────────│                │                 │
│       │                │                   │                │                 │
│       │                │  5. Delete session           │                 │
│       │                │─────────────────────▶│                │                 │
│       │                │                   │                │                 │
│       │                │  6. Clear session cookie     │                 │
│       │  Set-Cookie: AUTH_SESSION_ID=; Max-Age=0       │                 │
│       │◀──────────────│                   │                │                 │
│       │                │                   │                │                 │
│       │  7. Response   │                   │                │                 │
│       │◀──────────────│                   │                │                 │
│       │                │                   │                │                 │
│       │  END - Logged out successfully   │                │                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Token Exchange Flow (PKCE)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        PKCE TOKEN EXCHANGE FLOW                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Frontend                                                         Keycloak  │
│   ────────                                                         ────────  │
│       │                                                               │      │
│       │  1. Generate code_verifier (random string)                    │      │
│       │      code_challenge = BASE64URL(SHA256(code_verifier))       │      │
│       │                                                               │      │
│       │  2. Redirect to /authorize                                  │      │
│       │      ?response_type=code                                     │      │
│       │      &client_id=frontend-webapp                             │      │
│       │      &redirect_uri=https://app.example.com/callback          │      │
│       │      &scope=openid profile email                            │      │
│       │      &code_challenge=E9Melhoa2OwvFrEMTJguCHaEA...            │      │
│       │      &code_challenge_method=S256                            │      │
│       │────────────────────────────────────────────────────────────▶│      │
│       │                                                               │      │
│       │  3. User authenticates (password, MFA)                       │      │
│       │◀────────────────────────────────────────────────────────────│      │
│       │                                                               │      │
│       │  4. Redirect to /callback with authorization_code            │      │
│       │◀────────────────────────────────────────────────────────────│      │
│       │                                                               │      │
│       │  5. POST /token                                              │      │
│       │      grant_type=authorization_code                          │      │
│       │      &code=xyz...                                          │      │
│       │      &redirect_uri=https://app.example.com/callback         │      │
│       │      &client_id=frontend-webapp                            │      │
│       │      &code_verifier=dBjftJeZ4CVP-mB92K27uhb...             │      │
│       │────────────────────────────────────────────────────────────▶│      │
│       │                                                               │      │
│       │  6. Validate: SHA256(code_verifier) == code_challenge       │      │
│       │  7. Issue tokens                                           │      │
│       │◀────────────────────────────────────────────────────────────│      │
│       │                                                               │      │
│       │  Response:                                                  │      │
│       │  {                                                          │      │
│       │    "access_token": "eyJhbGc...",                          │      │
│       │    "refresh_token": "long-lived...",                       │      │
│       │    "expires_in": 300                                        │      │
│       │  }                                                          │      │
│       │                                                               │      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 Refresh Token Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         REFRESH TOKEN FLOW                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Service           Auth-Service                  Keycloak                    │
│   ──────           ───────────                  ────────                    │
│       │                 │                          │                        │
│       │  1. Request with expired token            │                        │
│       │────────────────▶│                          │                        │
│       │                 │                          │                        │
│       │                 │  2. Token expired? 401   │                        │
│       │◀────────────────│                          │                        │
│       │                 │                          │                        │
│       │  3. Call /auth/refresh                   │                        │
│       │  (with refresh token cookie)             │                        │
│       │────────────────▶│                          │                        │
│       │                 │                          │                        │
│       │                 │  4. Validate refresh token │                        │
│       │                 │──────────────────────────▶│                        │
│       │                 │                          │                        │
│       │                 │  5. Check token not revoked │                        │
│       │                 │──────────────────────────▶│                        │
│       │                 │                          │                        │
│       │                 │  6. New Access Token     │                        │
│       │                 │◀──────────────────────────│                        │
│       │                 │                          │                        │
│       │                 │  7. Rotation: Issue new refresh token  │            │
│       │                 │  8. Set new HttpOnly cookie          │            │
│       │                 │                          │                        │
│       │  9. New tokens + continue                 │                        │
│       │◀────────────────│                          │                        │
│       │                 │                          │                        │
│       │  END - Request proceeds with new token    │                        │
│                                                                             │
│   SECURITY:                                                             │
│   - Refresh token rotation: Old token revoked, new token issued          │
│   - Reuse detection: If old token reused, revoke entire token family       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.4 Logout Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            LOGOUT FLOW                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Browser          Auth-Service         Keycloak        Resource Svcs       │
│   ───────          ───────────         ────────        ───────────        │
│       │                │                   │                │                │
│       │  1. POST /auth/logout            │                │                │
│       │───────────────▶│                   │                │                │
│       │                │                   │                │                │
│       │                │  2. Invalidate session            │                │
│       │                │  3. Clear cookies                 │                │
│       │                │  4. Revoke refresh token         │                │
│       │                │──────────────────▶│                │                │
│       │                │                   │                │                │
│       │                │  5. Revoke access tokens         │                │
│       │                │  6. Invalidate SSO session       │                │
│       │                │                   │                │                │
│       │  7. Redirect to login page      │                │                │
│       │◀───────────────│                   │                │                │
│       │                │                   │                │                │
│       │                │                   │  8. (Optional) Back-channel logout │
│       │                │                   │◀───────────────│                │
│       │                │                   │  9. Revoke local tokens          │
│       │                │                   │                │                │
│       │  END - User logged out            │                │                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Authorization Flow

### 4.1 API Authorization Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      API AUTHORIZATION FLOW                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Client          Gateway        Resource Svc     common-lib     IAM-Service │
│   ──────          ───────       ───────────     ──────────     ─────────── │
│       │               │              │               │               │        │
│       │  1. API Request + Bearer Token             │               │        │
│       │──────────────▶│              │               │               │        │
│       │               │              │               │               │        │
│       │               │  2. Forward request + token              │        │
│       │               │─────────────▶│               │               │        │
│       │               │              │               │               │        │
│       │               │              │  3. JWT Validation          │        │
│       │               │              │───────────────▶│               │        │
│       │               │              │               │               │        │
│       │               │              │  4. Verify signature        │        │
│       │               │              │  5. Verify issuer/audience │        │
│       │               │              │  6. Check expiration       │        │
│       │               │              │◀───────────────│               │        │
│       │               │              │               │               │        │
│       │               │              │  7. Extract authorities    │        │
│       │               │              │  (realm roles, client roles)│        │
│       │               │              │               │               │        │
│       │               │              │  8. Set SecurityContext   │        │
│       │               │              │               │               │        │
│       │               │              │  9. @PreAuthorize check   │        │
│       │               │              │  (e.g., @HasRole("ADMIN"))│        │
│       │               │              │               │               │        │
│       │               │              │  10. (If needed) Fetch extended profile│
│       │               │              │────────────────────────────▶│        │
│       │               │              │               │               │        │
│       │               │              │◀────────────────────────────│        │
│       │               │              │               │               │        │
│       │               │              │  11. Business Authorization│        │
│       │               │              │  (Can user access this resource?)│   │
│       │               │              │               │               │        │
│       │               │              │  12. Process request       │        │
│       │               │              │               │               │        │
│       │  13. Response│               │               │               │        │
│       │◀──────────────│               │               │               │        │
│       │               │              │               │               │        │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Multi-Layer Authorization

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     MULTI-LAYER AUTHORIZATION                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   LAYER 1: NETWORK / GATEWAY                                               │
│   ┌─────────────────────────────────────────────────────────────────────┐ │
│   │  - Valid SSL/TLS                                                     │ │
│   │  - Valid API key (optional)                                         │ │
│   │  - Rate limiting                                                    │ │
│   │  - IP allowlisting (if needed)                                     │ │
│   └─────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│                                    ▼                                        │
│   LAYER 2: AUTHENTICATION                                                 │
│   ┌─────────────────────────────────────────────────────────────────────┐ │
│   │  - Valid JWT token                                                  │ │
│   │  - Not expired                                                      │ │
│   │  - Valid signature                                                  │ │
│   │  - Correct audience                                                 │ │
│   └─────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│                                    ▼                                        │
│   LAYER 3: ROLE-BASED ACCESS                                              │
│   ┌─────────────────────────────────────────────────────────────────────┐ │
│   │  - @HasRole("ADMIN")                                                │ │
│   │  - @HasAuthority("ROLE_DOC_EDITOR")                                │ │
│   │  - @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")            │ │
│   │                                                                      │ │
│   │  Uses: common-lib annotations                                        │ │
│   │  Data: JWT claims (realm_access, resource_access)                   │ │
│   └─────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│                                    ▼                                        │
│   LAYER 4: BUSINESS AUTHORIZATION                                         │
│   ┌─────────────────────────────────────────────────────────────────────┐ │
│   │  - Can this user access this specific document?                    │ │
│   │  - Can this department admin manage this user?                      │ │
│   │  - Is this workflow state valid for this action?                   │ │
│   │                                                                      │ │
│   │  Uses: Service-specific PermissionEvaluator                         │ │
│   │  Data: Database, organization hierarchy, workflow state             │ │
│   └─────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│                                    ▼                                        │
│   LAYER 5: RESOURCE-LEVEL SECURITY                                       │
│   ┌─────────────────────────────────────────────────────────────────────┐ │
│   │  - Row-level security (department data isolation)                  │ │
│   │  - Field-level security (hide sensitive fields)                   │ │
│   │  - Query filtering based on user's scope                           │ │
│   └─────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. User & Role Management Flows

### 5.1 User Creation Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          USER CREATION FLOW                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Admin Portal        IAM-Service          Keycloak          IAM-DB          │
│   ───────────        ───────────          ────────          ──────          │
│        │                 │                   │                │               │
│        │  1. POST /iam/users              │                │               │
│        │  {                                │                │               │
│        │    username: "john.doe",        │                │               │
│        │    email: "john@example.com",    │                │               │
│        │    firstName: "John",           │                │               │
│        │    lastName: "Doe",            │                │               │
│        │    departmentId: "dept-eng",   │                │               │
│        │    roles: ["USER"]             │                │               │
│        │  }                              │                │               │
│        │────────────────▶│                   │                │               │
│        │                 │                   │                │               │
│        │                 │  2. Validate business rules         │               │
│        │                 │  - Check permission to create    │               │
│        │                 │  - Validate email uniqueness       │               │
│        │                 │  - Check department exists         │               │
│        │                 │  - Check role assignment allowed  │               │
│        │                 │                   │                │               │
│        │                 │  3. POST /admin/realms/enterprise/users│            │
│        │                 │  (Create user in Keycloak)       │               │
│        │                 │──────────────────▶│                │               │
│        │                 │                   │                │               │
│        │                 │  4. User ID returned              │               │
│        │                 │◀──────────────────│                │               │
│        │                 │                   │                │               │
│        │                 │  5. Set password (optional)       │               │
│        │                 │  (Temporary password for first login)│             │
│        │                 │──────────────────▶│                │               │
│        │                 │                   │                │               │
│        │                 │  6. Assign roles (realm/client)  │               │
│        │                 │──────────────────▶│                │               │
│        │                 │                   │                │               │
│        │                 │  7. INSERT user_profile          │               │
│        │                 │  {                                │               │
│        │                 │    keycloak_user_id: "kc-uuid",  │               │
│        │                 │    department_id: "dept-eng",   │               │
│        │                 │    employee_id: "EMP001",       │               │
│        │                 │    ...                          │               │
│        │                 │  }                              │               │
│        │                 │────────────────────────────────▶│               │
│        │                 │                   │                │               │
│        │                 │  8. Audit log                    │               │
│        │                 │  "Admin X created user Y"      │               │
│        │                 │────────────────────────────────▶│               │
│        │                 │                   │                │               │
│        │                 │  9. Response (201 Created)     │               │
│        │◀────────────────│                   │                │               │
│        │                 │                   │                │               │
│        │  END           │                   │                │               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Role Assignment Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ROLE ASSIGNMENT FLOW                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Admin Portal        IAM-Service          Keycloak          Cache          │
│   ───────────        ───────────          ────────          ─────          │
│        │                 │                   │                │            │
│        │  1. POST /iam/users/{id}/roles     │                │            │
│        │  {                                │                │            │
│        │    roles: [                       │                │            │
│        │      {name: "DOC_EDITOR", clientId: "document-service"}│          │
│        │    ]                              │                │            │
│        │  }                                │                │            │
│        │────────────────▶│                   │                │            │
│        │                 │                   │                │            │
│        │                 │  2. Validate permission           │            │
│        │                 │  - Can admin assign this role?   │            │
│        │                 │  - Is role assignment allowed?    │            │
│        │                 │  - Check department scope?        │            │
│        │                 │                   │                │            │
│        │                 │  3. POST /users/{id}/role-mappings/realm│       │
│        │                 │  or client/{clientId}            │            │
│        │                 │──────────────────▶│                │            │
│        │                 │                   │                │            │
│        │                 │  4. Role assigned in Keycloak    │            │
│        │                 │◀──────────────────│                │            │
│        │                 │                   │                │            │
│        │                 │  5. Invalidate user cache        │            │
│        │                 │──────────────────────────────▶│            │
│        │                 │                   │                │            │
│        │                 │  6. Audit log: Role assigned     │            │
│        │                 │  "Admin assigned DOC_EDITOR to User Y"│         │
│        │                 │                   │                │            │
│        │                 │  7. (Optional) Publish event     │            │
│        │                 │  for other services              │            │
│        │                 │──────────────────▶│                │            │
│        │                 │                   │                │            │
│        │                 │  8. Response (200 OK)           │            │
│        │◀────────────────│                   │                │            │
│        │                 │                   │                │            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.3 Organization Management Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ORGANIZATION MANAGEMENT FLOW                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Admin Portal        IAM-Service          IAM-DB            Cache           │
│   ───────────        ───────────          ──────            ─────          │
│        │                 │                   │                │            │
│        │  1. POST /iam/organizations        │                │            │
│        │  {                                │                │            │
│        │    name: "Backend Team",         │                │            │
│        │    code: "backend",             │                │            │
│        │    parentId: "dept-eng",        │                │            │
│        │    type: "TEAM",               │                │            │
│        │    managerId: "user-uuid"     │                │            │
│        │  }                              │                │            │
│        │────────────────▶│                   │                │            │
│        │                 │                   │                │            │
│        │                 │  2. Validate:                           │        │
│        │                 │  - Parent exists?                       │        │
│        │                 │  - No circular reference?                │        │
│        │                 │  - Code unique?                         │        │
│        │                 │  - Manager exists?                      │        │
│        │                 │                   │                │            │
│        │                 │  3. Calculate materialized path        │        │
│        │                 │  path = "/company/cto/eng/backend"     │        │
│        │                 │                   │                │            │
│        │                 │  4. INSERT organization                │        │
│        │                 │────────────────────────────────▶│                │
│        │                 │                   │                │            │
│        │                 │  5. Update manager's org relationship │        │
│        │                 │────────────────────────────────▶│                │
│        │                 │                   │                │            │
│        │                 │  6. Invalidate org tree cache         │        │
│        │                 │──────────────────────────────────▶│            │
│        │                 │                   │                │            │
│        │                 │  7. Audit log                    │                │
│        │                 │────────────────────────────────▶│                │
│        │                 │                   │                │            │
│        │                 │  8. Response (201 Created)        │                │
│        │◀────────────────│                   │                │            │
│        │                 │                   │                │            │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. API Gateway Integration

### 6.1 Gateway Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         API GATEWAY LAYER                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                          INTERNET                                           │
│                              │                                              │
│                              ▼                                              │
│                    ┌─────────────────┐                                      │
│                    │  API Gateway   │                                      │
│                    │                 │                                      │
│                    │ ┌─────────────┐│                                      │
│                    │ │ Rate Limiter ││                                      │
│                    │ │             ││                                      │
│                    │ │ - Per IP    ││                                      │
│                    │ │ - Per User  ││                                      │
│                    │ │ - Per API   ││                                      │
│                    │ └─────────────┘│                                      │
│                    │                 │                                      │
│                    │ ┌─────────────┐│                                      │
│                    │ │   CORS      ││                                      │
│                    │ │   Handler   ││                                      │
│                    │ └─────────────┘│                                      │
│                    │                 │                                      │
│                    │ ┌─────────────┐│                                      │
│                    │ │   Router    ││                                      │
│                    │ │             ││                                      │
│                    │ │ /auth/* → auth-service  │                          │
│                    │ │ /iam/* → iam-service    │                          │
│                    │ │ /api/documents/* → doc-svc │                      │
│                    │ │ /api/workflows/* → wf-svc   │                      │
│                    │ └─────────────┘│                                      │
│                    │                 │                                      │
│                    └────────┬────────┘                                      │
│                             │                                               │
│         ┌──────────────────┼──────────────────┐                            │
│         │                  │                  │                            │
│         ▼                  ▼                  ▼                            │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐                     │
│  │Auth-Service │   │IAM-Service  │   │   Resource  │                     │
│  │             │   │             │   │   Services  │                     │
│  └─────────────┘   └─────────────┘   └─────────────┘                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Gateway Routing Rules

| Path Pattern | Destination | Auth Required | Notes |
|--------------|--------------|---------------|-------|
| `/auth/login` | Auth-Service | No | Login initiation (username/password) |
| `/auth/refresh` | Auth-Service | Cookie | Token refresh |
| `/auth/logout` | Auth-Service | Yes | Logout (session cookie) |
| `/auth/me` | Auth-Service | Yes | Get current user info |
| `/auth/sessions` | Auth-Service | Yes | List active sessions |
| `/iam/**` | IAM-Service | Yes (ADMIN) | Admin operations |
| `/api/documents/**` | Document-Service | Yes | Business APIs |
| `/api/workflows/**` | Workflow-Service | Yes | Business APIs |
| `/actuator/health` | All | No | Health checks |

### 6.3 Gateway Security Configuration

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       GATEWAY SECURITY CONFIG                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   RATE LIMITING:                                                            │
│   ┌─────────────────────────────────────────────────────────────────────┐ │
│   │  Global: 10,000 requests/minute                                     │ │
│   │  Per IP: 1,000 requests/minute                                      │ │
│   │  Per User: 500 requests/minute                                      │ │
│   │  Auth endpoints: 10 requests/minute (brute-force protection)        │ │
│   └─────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│   CORS:                                                                     │
│   ┌─────────────────────────────────────────────────────────────────────┐ │
│   │  Allowed Origins: https://app.example.com                           │ │
│   │  Allowed Methods: GET, POST, PUT, DELETE, PATCH                     │ │
│   │  Allowed Headers: Authorization, Content-Type, X-Request-ID        │ │
│   │  Expose Headers: X-Total-Count, X-Page-Number                       │ │
│   │  Credentials: true                                                  │ │
│   │  Max Age: 3600 seconds                                              │ │
│   └─────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│   HEADERS:                                                                 │
│   ┌─────────────────────────────────────────────────────────────────────┐ │
│   │  X-Request-ID: Correlation ID for tracing                         │ │
│   │  X-Forwarded-For: Original client IP                               │ │
│   │  X-Forwarded-Proto: Original protocol (http/https)                │ │
│   │  Strict-Transport-Security: max-age=31536000; includeSubDomains   │ │
│   │  X-Content-Type-Options: nosniff                                   │ │
│   │  X-Frame-Options: DENY                                             │ │
│   └─────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Service-to-Service Communication

### 7.1 Service Account Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SERVICE-TO-SERVICE AUTHENTICATION                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Document-Service      Keycloak         Workflow-Service                    │
│   ───────────────       ────────         ───────────────                    │
│         │                  │                   │                            │
│         │  1. POST /token  │                   │                            │
│         │  grant_type=client_credentials     │                            │
│         │  &client_id=document-service       │                            │
│         │  &client_secret=xxx                 │                            │
│         │  &scope=workflow:read              │                            │
│         │──────────────────▶│                   │                            │
│         │                  │                   │                            │
│         │  2. Validate client credentials     │                            │
│         │  3. Issue access token             │                            │
│         │◀──────────────────│                   │                            │
│         │                  │                   │                            │
│         │  4. Call Workflow API               │                            │
│         │  Authorization: Bearer eyJhbGc... │                            │
│         │───────────────────────────────────▶│                            │
│         │                  │                   │                            │
│         │                  │  5. Validate JWT  │                            │
│         │                  │  (via JWKS)       │                            │
│         │                  │◀──────────────────│                            │
│         │                  │                   │                            │
│         │  6. Response     │                   │                            │
│         │◀───────────────────────────────────│                            │
│         │                  │                   │                            │
│         │  END             │                   │                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.2 Internal API Contracts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         INTERNAL API CONTRACTS                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   AUTH-SERVICE → KEYCLOAK:                                                 │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │  Endpoint: /realms/{realm}/protocol/openid-connect/token           │  │
│   │  Auth: client_id + client_secret                                  │  │
│   │  Purpose: Token exchange, refresh token                          │  │
│   │  Auth: client_credentials grant                                    │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│   IAM-SERVICE → KEYCLOAK (Admin API):                                       │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │  Endpoint: /admin/realms/{realm}/users                            │  │
│   │  Auth: Service account (client_credentials)                       │  │
│   │  Purpose: User/Role/Group management                             │  │
│   │  Auth: Bearer token from service account                         │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│   RESOURCE SERVICE → IAM-SERVICE (optional):                                │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │  Endpoint: /api/v1/iam/users/{id}/profile                        │  │
│   │  Auth: Service account JWT                                        │  │
│   │  Purpose: Fetch extended user profile                             │  │
│   │  Usage: When service needs department, manager, custom attrs      │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│   RESOURCE SERVICE → RESOURCE SERVICE:                                     │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │  Auth: Service account JWT (client_credentials)                  │  │
│   │  Pattern: Service account per consumer                           │  │
│   │  Example: Document-Service → Workflow-Service                     │  │
│   │  Audience: Target service ID                                     │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.3 Message Bus Integration

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         EVENT-DRIVEN ARCHITECTURE                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Keycloak         Kafka/RabbitMQ           IAM-Service                     │
│   ────────         ──────────────           ────────────                    │
│       │                  │                        │                         │
│       │  User Created     │                        │                         │
│       │  Event           │                        │                         │
│       │─────────────────▶│                        │                         │
│       │                  │                        │                         │
│       │                  │  Publish to topic:     │                         │
│       │                  │  keycloak.user.created │                         │
│       │                  │──────────────────────▶│                         │
│       │                  │                        │                         │
│       │                  │                        │  1. Update local cache  │
│       │                  │                        │  2. Store extended prof│
│       │                  │                        │  3. Audit log          │
│       │                  │                        │                         │
│       │                  │                        │  Publish to:           │
│       │                  │                        │  iam.user.created      │
│       │                  │                        │────────────────────────▶│ │
│       │                  │                        │                         │
│       │                  │                        │     ↓                   │
│       │                  │                        │  ┌──────────────┐       │
│       │                  │                        │  │ Notification │       │
│       │                  │                        │  │ Services     │       │
│       │                  │                        │  └──────────────┘       │
│       │                  │                        │                         │
│                                                                             │
│   EVENT TYPES:                                                             │
│   ├── keycloak.user.created                                               │
│   ├── keycloak.user.updated                                               │
│   ├── keycloak.user.deleted                                               │
│   ├── keycloak.role.assigned                                              │
│   ├── keycloak.role.revoked                                               │
│   ├── iam.cache.invalidate                                                │
│   └── audit.events.*                                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Deployment Topology

### 8.1 Production Deployment Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PRODUCTION DEPLOYMENT                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                              INTERNET                                        │
│                                 │                                           │
│                                 ▼                                           │
│                    ┌───────────────────────┐                               │
│                    │    Cloudflare/AWS WAF  │                               │
│                    │    (DDoS, CDN, SSL)    │                               │
│                    └───────────┬───────────┘                               │
│                                │                                            │
│                                ▼                                            │
│                    ┌───────────────────────┐                               │
│                    │     Load Balancer     │                               │
│                    │   (Application LB)     │                               │
│                    └───────────┬───────────┘                               │
│                                │                                            │
│          ┌─────────────────────┼─────────────────────┐                     │
│          │                     │                     │                      │
│          ▼                     ▼                     ▼                      │
│   ┌─────────────┐      ┌─────────────┐      ┌─────────────┐             │
│   │    Auth-    │      │    IAM-     │      │   Gateway   │             │
│   │  Service    │      │  Service   │      │             │             │
│   │             │      │             │      │             │             │
│   │ [Pod 1]     │      │ [Pod 1]     │      │ [Pod 1]     │             │
│   │ [Pod 2]     │      │ [Pod 2]     │      │ [Pod 2]     │             │
│   │ [Pod N]     │      │ [Pod N]     │      │ [Pod N]     │             │
│   └─────────────┘      └─────────────┘      └─────────────┘             │
│          │                     │                     │                      │
│          └─────────────────────┼─────────────────────┘                     │
│                                │                                           │
│          ┌─────────────────────┼─────────────────────┐                     │
│          │                     │                     │                      │
│          ▼                     ▼                     ▼                      │
│   ┌─────────────┐      ┌─────────────┐      ┌─────────────┐             │
│   │  Document-  │      │  Workflow- │      │   Order-   │             │
│   │  Service   │      │  Service   │      │  Service   │             │
│   │             │      │             │      │             │             │
│   │ [Pod 1-3]   │      │ [Pod 1-3]   │      │ [Pod 1-3]   │             │
│   │             │      │             │      │             │             │
│   └─────────────┘      └─────────────┘      └─────────────┘             │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐ │
│   │                        KEYCLOAK CLUSTER                             │ │
│   │                                                                      │ │
│   │   ┌─────────────┐      ┌─────────────┐                            │ │
│   │   │  Keycloak   │      │  Keycloak   │                            │ │
│   │   │  Node 1     │◀────▶│  Node 2     │    Infinispan Cluster     │ │
│   │   │             │      │             │                            │ │
│   │   └──────┬──────┘      └──────┬──────┘                            │ │
│   │          │                     │                                    │ │
│   │          └──────────┬──────────┘                                    │ │
│   │                     │                                               │ │
│   │                     ▼                                               │ │
│   │            ┌─────────────────┐                                     │ │
│   │            │   PostgreSQL    │  Primary + Read Replica             │ │
│   │            │   (HA Cluster)   │                                     │ │
│   │            └─────────────────┘                                     │ │
│   └─────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐ │
│   │                        SUPPORTING SERVICES                          │ │
│   │                                                                      │ │
│   │   ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐   │ │
│   │   │   Redis     │  │   Kafka     │  │   Prometheus/Grafana   │   │ │
│   │   │  Cluster    │  │  Cluster    │  │   (Monitoring)         │   │ │
│   │   │             │  │             │  │                         │   │ │
│   │   │  - Sessions │  │  - Events   │  │   - Metrics            │   │ │
│   │   │  - Cache    │  │  - Async    │  │   - Alerts             │   │ │
│   │   └─────────────┘  └─────────────┘  └─────────────────────────┘   │ │
│   │                                                                      │ │
│   │   ┌─────────────────────────────────────────────────────────────┐   │ │
│   │   │                    PostgreSQL Cluster                       │   │ │
│   │   │  ┌─────────┐   Primary   ┌─────────┐   Read Replica      │   │ │
│   │   │  │Node 1   │◀───────────▶│Node 2   │                     │   │ │
│   │   │  └─────────┘             └─────────┘                     │   │ │
│   │   │         │                                        │         │   │ │
│   │   │         └────────────────┬────────────────────────┘         │   │ │
│   │   │                          ▼                                   │   │ │
│   │   │                   ┌─────────────┐                            │   │ │
│   │   │                   │  PgBouncer  │  Connection Pooling       │   │ │
│   │   │                   └─────────────┘                            │   │ │
│   │   └─────────────────────────────────────────────────────────────┘   │ │
│   └─────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 8.2 Network Zones

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           NETWORK ZONES                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │                        PUBLIC ZONE                                   │  │
│   │                                                                      │  │
│   │   - Internet-facing endpoints                                       │  │
│   │   - API Gateway                                                     │  │
│   │   - TLS termination                                                │  │
│   │   - Rate limiting                                                   │  │
│   │                                                                      │  │
│   │   CIDR: 0.0.0.0/0 (incoming)                                       │  │
│   │                                                                      │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                        │
│                                    │ Load Balancer                          │
│                                    ▼                                        │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │                       DMZ ZONE                                      │  │
│   │                                                                      │  │
│   │   - Auth-Service (BFF)                                              │  │
│   │   - API Gateway                                                     │  │
│   │   - WAF                                                             │  │
│   │                                                                      │  │
│   │   Access: Only from Public Zone                                     │  │
│   │                                                                      │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                        │
│                                    │ Internal Load Balancer                  │
│                                    ▼                                        │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │                     APPLICATION ZONE                                 │  │
│   │                                                                      │  │
│   │   - IAM-Service                                                     │  │
│   │   - Resource Services                                               │  │
│   │   - Keycloak (Admin Console - restricted access)                   │  │
│   │                                                                      │  │
│   │   Access: Only from DMZ Zone + Internal Network                    │  │
│   │                                                                      │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                        │
│                                    │                                        │
│                                    ▼                                        │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │                       DATA ZONE                                      │  │
│   │                                                                      │  │
│   │   - PostgreSQL (Databases)                                          │  │
│   │   - Redis (Cache)                                                   │  │
│   │   - Kafka (Events)                                                  │  │
│   │   - Object Storage                                                  │  │
│   │                                                                      │  │
│   │   Access: Only from Application Zone                                │  │
│   │   No direct external access                                        │  │
│   │                                                                      │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 8.3 Scaling Configuration

| Component | Scaling Strategy | Min Replicas | Max Replicas |
|-----------|-----------------|--------------|--------------|
| **API Gateway** | Horizontal (HPA) | 2 | 10 |
| **Auth-Service** | Horizontal (HPA) | 2 | 10 |
| **IAM-Service** | Horizontal (HPA) | 2 | 10 |
| **Resource Services** | Horizontal (HPA) | 3 | 50 |
| **Keycloak** | StatefulSet | 2 | 5 |
| **PostgreSQL** | Primary-Replica | 1 | 2 (read replicas) |
| **Redis** | Cluster mode | 3 | 6 |
| **Kafka** | Broker replication | 3 | 9 |

---

## 9. Security Best Practices

### 9.1 Security Checklist

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SECURITY CHECKLIST                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   AUTHENTICATION:                                                          │
│   ☑ PKCE required for all public clients                                   │
│   ☑ MFA enforced in production                                             │
│   ☑ Password policy enforced (complexity, history, expiration)             │
│   ☑ Account lockout after 5 failed attempts                               │
│   ☑ Session timeout: 30 minutes idle                                      │
│   ☑ Refresh token rotation enabled                                         │
│   ☑ Short-lived access tokens (5-15 minutes)                             │
│                                                                             │
│   AUTHORIZATION:                                                           │
│   ☑ Role-based access at API layer                                        │
│   ☑ Business authorization in resource services                          │
│   ☑ Principle of least privilege                                          │
│   ☑ No wildcard permissions                                               │
│   ☑ Department-scoped access where applicable                             │
│                                                                             │
│   TOKEN SECURITY:                                                          │
│   ☑ JWT signed with RS256                                                 │
│   ☑ Audience restriction                                                  │
│   ☑ Token issuer validation                                               │
│   ☑ Token expiration validation                                           │
│   ☑ No sensitive data in JWT claims                                       │
│   ☑ Token stored in HttpOnly cookies (refresh token)                      │
│   ☑ Access token in memory only (no localStorage)                        │
│                                                                             │
│   NETWORK SECURITY:                                                        │
│   ☑ TLS 1.3 for all external connections                                  │
│   ☑ Internal mTLS for service-to-service                                  │
│   ☑ Network segmentation (DMZ, Application, Data zones)                  │
│   ☑ WAF in front of public endpoints                                      │
│   ☑ DDoS protection                                                       │
│   ☑ IP allowlisting for admin endpoints                                  │
│                                                                             │
│   DATA SECURITY:                                                           │
│   ☑ Passwords hashed with bcrypt/argon2                                   │
│   ☑ Sensitive data encrypted at rest                                     │
│   ☑ No credentials in code or config files                               │
│   ☑ Secrets managed in Vault                                              │
│   ☑ Audit logging for all sensitive operations                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 9.2 Security Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        SECURITY ARCHITECTURE                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   THREAT: Token Theft                                                      │
│   MITIGATION:                                                             │
│   - Short-lived access tokens (5-15 min)                                  │
│   - HttpOnly, Secure cookies                                             │
│   - Refresh token rotation                                                │
│                                                                             │
│   THREAT: Authorization Code Interception                                  │
│   MITIGATION:                                                             │
│   - PKCE with S256 method                                                │
│   - Strict redirect URI validation                                        │
│   - One-time use authorization codes                                      │
│                                                                             │
│   THREAT: Credential Brute Force                                          │
│   MITIGATION:                                                             │
│   - Rate limiting on auth endpoints                                       │
│   - Account lockout after failed attempts                                 │
│   - CAPTCHA after multiple failures                                      │
│   - MFA requirement                                                      │
│                                                                             │
│   THREAT: Privilege Escalation                                            │
│   MITIGATION:                                                             │
│   - Defense in depth (multiple authorization layers)                       │
│   - Business rules validation                                             │
│   - Audit logging                                                         │
│   - Regular access reviews                                                │
│                                                                             │
│   THREAT: Insider Threat                                                  │
│   MITIGATION:                                                             │
│   - Segregation of duties                                                │
│   - Audit trail for all admin actions                                     │
│   - Multi-person approval for sensitive operations                        │
│   - Regular access certification                                          │
│                                                                             │
│   THREAT: API Abuse                                                        │
│   MITIGATION:                                                             │
│   - Rate limiting                                                         │
│   - API keys for service accounts                                        │
│   - Request validation                                                    │
│   - Input sanitization                                                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Observability & Logging

### 10.1 Logging Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          LOGGING ARCHITECTURE                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │                    LOG LEVELS                                       │  │
│   │                                                                      │  │
│   │   ERROR: Security violations, authentication failures, system errors│  │
│   │   WARN:  Suspicious activities, rate limiting, failed authorizations │  │
│   │   INFO:  Successful logins, token issuance, role changes            │  │
│   │   DEBUG: JWT validation details, request/response (sanitized)       │  │
│   │                                                                      │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │                    LOG FORMAT (JSON)                                 │  │
│   │                                                                      │  │
│   │   {                                                                 │  │
│   │     "timestamp": "2026-05-20T10:30:00.000Z",                       │  │
│   │     "level": "INFO",                                               │  │
│   │     "service": "document-service",                                  │  │
│   │     "traceId": "abc123",                                           │  │
│   │     "spanId": "def456",                                            │  │
│   │     "userId": "user-uuid",                                         │  │
│   │     "action": "DOCUMENT_VIEW",                                     │  │
│   │     "resourceId": "doc-123",                                      │  │
│   │     "result": "SUCCESS",                                          │  │
│   │     "duration": 45                                                 │  │
│   │   }                                                                 │  │
│   │                                                                      │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│   AUDIT LOGS (IAM-Service):                                                │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │   WHO  | WHAT  | WHEN  | TARGET  | RESULT  | IP  | DETAILS         │  │
│   │   ─────────────────────────────────────────────────────────────     │  │
│   │   Admin| CREATE| Time  | User X  | SUCCESS | IP  | {changes}     │  │
│   │   Admin| ASSIGN| Time  | Role Y  | SUCCESS | IP  | To User Z     │  │
│   │   User | LOGIN | Time  | -       | SUCCESS | IP  | MFA used      │  │
│   │   User | LOGIN | Time  | -       | FAILED  | IP  | Wrong password │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 10.2 Distributed Tracing

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       DISTRIBUTED TRACING                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Browser Request: X-Request-ID: abc-123                                    │
│          │                                                                    │
│          ▼                                                                    │
│   ┌─────────────┐                                                          │
│   │   Gateway   │  traceId: abc-123                                        │
│   │             │  spanId: 001                                             │
│   └──────┬──────┘                                                          │
│          │                                                                   │
│          ├──────────────────────────────────────────────────────────────┐   │
│          │                                                      │        │
│          ▼                                                      ▼        │
│   ┌─────────────┐                                          ┌─────────────┐│
│   │ Auth-Service│                                          │IAM-Service ││
│   │ traceId:abc │                                          │traceId:abc ││
│   │ spanId: 002 │                                          │spanId: 003 ││
│   └─────────────┘                                          └─────────────┘│
│          │                                                                   │
│          ▼                                                                   │
│   ┌─────────────┐      ┌─────────────┐      ┌─────────────┐               │
│   │   Keycloak  │      │   Keycloak  │      │    Redis    │               │
│   │ traceId:abc │      │ traceId:abc │      │ traceId:abc │               │
│   │ spanId: 003 │      │ spanId: 004 │      │ spanId: 005 │               │
│   └─────────────┘      └─────────────┘      └─────────────┘               │
│                                                                             │
│   Trace in Jaeger/Zipkin:                                                  │
│   - Single trace across all services                                       │
│   - Correlation ID: X-Request-ID header                                    │
│   - Helps identify bottlenecks and failures                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 10.3 Metrics & Monitoring

| Metric Category | Metrics | Alert Threshold |
|----------------|---------|-----------------|
| **Authentication** | Login success/failure rate, Token issuance rate | Failure rate > 5% |
| **Authorization** | 401/403 rate by endpoint, Permission denied rate | 403 rate > 10% |
| **Performance** | Auth latency, Token validation latency | P99 > 200ms |
| **Health** | Service uptime, Keycloak cluster health | Any down |
| **Security** | Failed login attempts, Suspicious activity | > 10 failed/min |

---

## 11. Anti-patterns & Common Mistakes

### 11.1 Critical Anti-patterns

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     CRITICAL ANTI-PATTERNS                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ❌ BUILDING CUSTOM AUTH SERVER                                           │
│   ─────────────────────────────                                            │
│   Why: OAuth2/OIDC is complex, prone to security vulnerabilities           │
│   Instead: Use Keycloak, Azure AD, Auth0                                   │
│   Risk: Severe security breaches, compliance issues                        │
│                                                                             │
│   ❌ PUTTING ALL PERMISSIONS IN JWT                                        │
│   ─────────────────────────────────                                        │
│   Why: JWT is cached everywhere, hard to revoke                            │
│   Instead: Keep minimal claims, validate business rules per request         │
│   Risk: Stale permissions, privilege escalation                            │
│                                                                             │
│   ❌ LONG-LIVED ACCESS TOKENS                                              │
│   ─────────────────────────────                                           │
│   Why: Token leak = persistent unauthorized access                        │
│   Instead: 5-15 minute access tokens, refresh rotation                     │
│   Risk: Extended attack window                                             │
│                                                                             │
│   ❌ DIRECT KEYCLOAK ACCESS FROM RESOURCE SERVICES                        │
│   ─────────────────────────────────────────────                           │
│   Why: Security boundary violation, audit loss                            │
│   Instead: Use IAM-Service for user/role management                        │
│   Risk: Unauthorized access, inconsistent state                            │
│                                                                             │
│   ❌ CENTRALIZED BUSINESS AUTHORIZATION                                    │
│   ──────────────────────────────────                                      │
│   Why: Single point of failure, scalability bottleneck                     │
│   Instead: Distributed authorization in each resource service              │
│   Risk: System bottleneck, cascading failures                              │
│                                                                             │
│   ❌ STORING TOKENS IN FRONTEND STORAGE                                    │
│   ─────────────────────────────────                                       │
│   Why: XSS can steal tokens                                               │
│   Instead: HttpOnly cookies for refresh, memory for access                 │
│   Risk: Token theft via XSS                                               │
│                                                                             │
│   ❌ IGNORING TOKEN EXPIRATION IN CLIENT CODE                              │
│   ─────────────────────────────────────                                   │
│   Why: Requests fail with 401 unexpectedly                                │
│   Instead: Implement proper token refresh flow                             │
│   Risk: Poor user experience, failed requests                             │
│                                                                             │
│   ❌ NO AUDIT LOGGING FOR IAM CHANGES                                      │
│   ─────────────────────────────────                                       │
│   Why: No accountability, compliance violations                           │
│   Instead: Comprehensive audit trail in IAM-Service                        │
│   Risk: Audit findings, regulatory penalties                              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 11.2 Common Mistakes

| Mistake | Impact | Solution |
|---------|--------|----------|
| No PKCE for SPA | Authorization code theft | Implement PKCE with S256 |
| Forgetting audience validation | Token used for wrong service | Always validate audience |
| No refresh token rotation | Token replay attack | Enable rotation in Keycloak |
| Keycloak realm config in code | Inconsistent environments | Use export/import |
| Not caching JWKS | Performance issues | Cache with TTL |
| No token expiration handling | Broken UX | Implement refresh flow |
| Storing passwords in logs | Data leak | Never log credentials |
| No MFA | Credential compromise | Enable MFA in Keycloak |

---

## 12. Decision Rationale

### 12.1 Key Architecture Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Auth Server** | Keycloak | Enterprise-ready, OIDC compliant, no vendor lock-in |
| **Auth-Service (BFF)** | Spring Boot | OAuth2 client wrapper cho Frontend |
| **Session Storage** | Redis | Server-side session, HttpOnly cookie |
| **Access Token Lifetime** | 5-15 minutes | Security vs performance balance |
| **Business AuthZ** | Distributed | Scalability, loose coupling |
| **User Management** | Centralized (IAM-Service) | Consistency, audit, business rules |
| **Role Model** | Realm + Client roles | Clear separation of concerns |
| **Token Storage** | HttpOnly cookies | XSS protection |
| **MFA** | Required in production | Security baseline |
| **Service Auth** | Client credentials | Service-to-service standard |
| **Concurrent Sessions** | Max 3/user (configurable) | Resource protection |

### 12.2 Auth-Service Design Decisions

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AUTH-SERVICE DESIGN DECISIONS                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   DECISION: Auth-Service là OAuth2 Client, không phải Auth Server         │
│   ──────────────────────────────────────────────────────────────             │
│   Why: Frontend cần simple API, không cần biết OAuth2 complexity          │
│   Benefit: Security tốt hơn, code frontend đơn giản hơn                 │
│                                                                             │
│   DECISION: Password Grant thay vì Authorization Code + PKCE               │
│   ──────────────────────────────────────────────────────────────             │
│   Why: Đơn giản cho internal apps, client secret được bảo vệ ở server  │
│   Trade-off: Chỉ dùng cho trusted first-party apps                       │
│   Alternative: Authorization Code + PKCE cho untrusted clients              │
│                                                                             │
│   DECISION: Session trong Redis thay vì JWT storage                       │
│   ──────────────────────────────────────────────────────────────             │
│   Why: Immediate revocation, centralize session control                    │
│   Benefit: Logout tức thì có hiệu lực, dễ quản lý sessions             │
│                                                                             │
│   DECISION: HttpOnly cookie thay vì localStorage                          │
│   ──────────────────────────────────────────────────────────────             │
│   Why: Ngăn XSS đọc session ID                                            │
│   Security: SameSite=Strict + Secure=True                                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 12.3 Trade-off Analysis

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          TRADE-OFF ANALYSIS                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   TRADE-OFF: Single Realm vs Multiple Realms                                │
│   ──────────────────────────────────────                                    │
│   Decision: Single realm "enterprise"                                       │
│   Pros: Simple user management, easy SSO, single source of truth           │
│   Cons: Less isolation (mitigated by client roles)                        │
│   Verdict: Correct for single organization                                 │
│                                                                             │
│   TRADE-OFF: Token in Cookie vs Header                                     │
│   ─────────────────────────────────                                        │
│   Decision: HttpOnly cookie for refresh, memory for access (frontend)       │
│   Pros: XSS protection for refresh token                                   │
│   Cons: Requires proper CSRF protection (mitigated by SameSite)           │
│   Verdict: Cookie approach is more secure for SPAs                        │
│                                                                             │
│   TRADE-OFF: Centralized vs Distributed Authorization                      │
│   ──────────────────────────────────────────                               │
│   Decision: Distributed business authorization                              │
│   Pros: Scalable, loosely coupled, domain-specific logic                  │
│   Cons: Some code duplication (mitigated by common-lib)                    │
│   Verdict: Correct for microservices architecture                         │
│                                                                             │
│   TRADE-OFF: Pull vs Push User Sync                                        │
│   ──────────────────────────────                                          │
│   Decision: Push via events (Keycloak → IAM-Service)                      │
│   Pros: Real-time, decoupled, reliable                                     │
│   Cons: Event infrastructure needed (Kafka/RabbitMQ)                      │
│   Verdict: Event-driven is correct for enterprise scale                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 12.3 Recommended Evolution Path

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       EVOLUTION PATH                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   PHASE 1: Foundation (Week 1-4)                                           │
│   ├── Deploy Keycloak with production config                               │
│   ├── Implement Auth-Service (BFF)                                        │
│   ├── Implement security-common-lib                                       │
│   └── Migrate one resource service                                        │
│                                                                             │
│   PHASE 2: IAM Capability (Week 5-8)                                       │
│   ├── Implement IAM-Service                                               │
│   ├── Connect to Keycloak Admin API                                       │
│   ├── Implement Admin Portal                                              │
│   └── Migrate all resource services                                       │
│                                                                             │
│   PHASE 3: Advanced Security (Week 9-12)                                   │
│   ├── Enable MFA enforcement                                              │
│   ├── Implement advanced audit logging                                   │
│   ├── Add risk-based authentication                                      │
│   └── Penetration testing                                                │
│                                                                             │
│   PHASE 4: Optimization (Week 13-16)                                       │
│   ├── Performance testing and optimization                                │
│   ├── HA/DR setup                                                        │
│   ├── Monitoring and alerting fine-tuning                                │
│   └── Documentation and runbooks                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 13. Summary

### 13.1 Architecture Summary

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ARCHITECTURE SUMMARY                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   COMPONENTS:                                                              │
│   ├── Keycloak: Authorization Server (AuthN, Token, SSO)                    │
│   ├── Auth-Service: Login BFF (Orchestration, Session)                    │
│   ├── IAM-Service: User/Role/Org Management (Centralized IAM)              │
│   ├── security-common-lib: Shared security infrastructure                  │
│   └── Resource Services: Business APIs (Distributed AuthZ)                │
│                                                                             │
│   KEY PRINCIPLES:                                                          │
│   ├── Centralized Authentication (Keycloak)                                │
│   ├── Distributed Authorization (Resource Services)                        │
│   ├── Clear boundary: Auth ≠ Authorization ≠ IAM Management              │
│   ├── Event-driven synchronization                                        │
│   └── Defense in depth (multiple security layers)                         │
│                                                                             │
│   DATA FLOW:                                                               │
│   ├── Authentication: User → Auth-Service → Keycloak → Token             │
│   ├── Authorization: Request → Gateway → Service → common-lib → AuthZ   │
│   ├── User Mgmt: Admin → IAM-Service → Keycloak → SoT                    │
│   └── Events: Keycloak → Kafka → IAM-Service → Cache Invalidation        │
│                                                                             │
│   SECURITY:                                                                │
│   ├── PKCE for public clients                                             │
│   ├── Short-lived tokens + refresh rotation                              │
│   ├── MFA in production                                                   │
│   ├── Defense in depth                                                    │
│   └── Comprehensive audit logging                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 13.2 Quick Reference

| Question | Answer |
|----------|--------|
| Where is authentication handled? | Keycloak |
| Where is authorization handled? | Resource Services (business), common-lib (infrastructure) |
| Where is user management? | IAM-Service |
| Where are tokens issued? | Keycloak |
| Where are sessions managed? | Auth-Service (Redis), Keycloak (SSO) |
| Who calls Keycloak Admin API? | IAM-Service only |
| What does common-lib contain? | JWT validation, annotations, permission interfaces |
| What do resource services contain? | Business logic, business authorization |
| Where is audit log? | IAM-Service + each resource service |
| What is source of truth? | Keycloak for auth, IAM-DB for extended profile |
| **Auth-Service là gì?** | OAuth2 BFF wrapper cho Frontend |
| **Auth-Service đảm nhiệm gì?** | Login orchestration, session management (Redis), cookie handling |
| **Auth-Service không làm gì?** | User/Role management (IAM-Service), JWT validation (common-lib) |
