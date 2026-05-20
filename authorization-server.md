# Authorization Server Layer - Tài liệu Thiết kế Kiến trúc

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [ Vai trò của Keycloak](#2--vai-trò-của-keycloak)
3. [Trách nhiệm Authentication](#3-trách-nhiệm-authentication)
4. [Trách nhiệm OAuth2/OIDC](#4-trách-nhiệm-oauth2oidc)
5. [SSO & Token Management](#5-sso--token-management)
6. [Chiến lược Realm, Client & Scope](#6-chiến-lược-realm-client--scope)
7. [Role Strategy](#7-role-strategy)
8. [Token Design Philosophy](#8-token-design-philosophy)
9. [Security Best Practices](#9-security-best-practices)
10. [Gateway & Resource Server Integration](#10-gateway--resource-server-integration)
11. [Deployment & Scaling](#11-deployment--scaling)
12. [Security, Audit & Logging](#12-security-audit--logging)
13. [Anti-patterns & Boundaries](#13-anti-patterns--boundaries)
14. [Quyết định Thiết kế quan trọng](#14-quyết-định-thiết-kế-quan-trọng)

---

## 1. Tổng quan

### 1.1 Mục đích của Authorization Server

```
┌─────────────────────────────────────────────────────────────────┐
│                    AUTHORIZATION SERVER                         │
│                                                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │     SSO     │  │    AuthN    │  │    OAuth2/OIDC Token   │  │
│  │  Identity  │  │   Password  │  │  Access/Refresh/ID      │  │
│  │  Federation │  │     MFA     │  │    Issuance            │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
│                                                                 │
│  >> KHÔNG XỬ LÝ business authorization logic <<                 │
└─────────────────────────────────────────────────────────────────┘
```

**Authorization Server** là thành phần trung tâm chịu trách nhiệm:

- **Authentication (Xác thực)**: Xác minh danh tính người dùng
- **Token Issuance (Phát hành Token)**: Tạo và quản lý access token, refresh token, ID token
- **SSO (Single Sign-On)**: Cho phép đăng nhập một lần, truy cập nhiều ứng dụng
- **Identity Federation**: Tích hợp với các identity provider bên ngoài (LDAP, Social Login, SAML)
- **Session Management**: Quản lý phiên đăng nhập người dùng

### 1.2 Tại sao sử dụng Keycloak

| Tiêu chí | Giải thích |
|----------|------------|
| **Open Source** | Miễn phí, không phụ thuộc vendor, có community lớn |
| **Standards Compliance** | Tuân thủ đầy đủ OAuth2, OIDC, SAML2, LDAP |
| **Mature Product** | Được sử dụng rộng rãi trong enterprise, stable |
| **Admin Console** | Giao diện quản trị trực quan cho realm, client, role |
| **High Availability** | Hỗ trợ cluster, database replication |
| **Admin API** | RESTful API cho automation và integration |
| **Customizable** | Hỗ trợ theme, authenticator SPI, protocol mapper |

**Quyết định quan trọng**: Việc sử dụng Keycloak thay vì tự xây dựng OAuth2 Server là **bắt buộc** trong kiến trúc enterprise. Tự xây dựng OAuth2 Server là anti-pattern vì:

1. OAuth2/OIDC là specification phức tạp, dễ có lỗ hổng bảo mật
2. Mất thời gian phát triển và bảo trì
3. Không có SSO, federation, MFA có sẵn
4. Không đáp ứng được compliance requirements

---

## 2. Vai trò của Keycloak

### 2.1 Keycloak Đảm nhiệm

```
┌────────────────────────────────────────────────────────────────────┐
│                         KEYCLOAK                                   │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  1. Xác thực người dùng (Authentication)                          │
│     ├── Username/Password                                          │
│     ├── OTP/MFA (TOTP, SMS, Email)                                │
│     ├── X.509 Certificate                                          │
│     ├── Kerberos/SPNEGO                                            │
│     └── WebAuthn (FIDO2)                                          │
│                                                                    │
│  2. Identity Federation                                            │
│     ├── LDAP/Active Directory                                      │
│     ├── SAML 2.0 IdP                                              │
│     ├── Social Login (Google, Facebook, GitHub...)               │
│     └── OpenID Connect IdP                                        │
│                                                                    │
│  3. Token Issuance                                                 │
│     ├── Access Token (JWT)                                         │
│     ├── Refresh Token                                              │
│     ├── ID Token                                                   │
│     └── Offline Token                                              │
│                                                                    │
│  4. SSO Management                                                 │
│     ├── Cross-domain SSO                                           │
│     ├── Session management                                         │
│     └── Logout propagation                                         │
│                                                                    │
│  5. Password Policy                                                │
│     ├── Complexity requirements                                    │
│     ├── History                                                   │
│     ├── Expiration                                                │
│     └── Lockout rules                                              │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

### 2.2 Keycloak KHÔNG Đảm nhiệm

```
┌────────────────────────────────────────────────────────────────────┐
│                     KEYCLOAK边界 - KHÔNG XỬ LÝ                     │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  ✗ Business Authorization Logic                                   │
│    Ví dụ: "User A có quyền phê duyệt tài liệu phòng B"           │
│                                                                    │
│  ✗ Workflow Permission Rules                                      │
│    Ví dụ: "Document status = PENDING mới được approve"           │
│                                                                    │
│  ✗ Domain Ownership Logic                                         │
│    Ví dụ: "Admin phòng A chỉ quản lý user thuộc phòng A"         │
│                                                                    │
│  ✗ Organization Hierarchy                                         │
│    Ví dụ: "Department → Division → Company tree"                 │
│                                                                    │
│  ✗ Business Data Permissions                                       │
│    Ví dụ: "User chỉ đọc document của department mình"            │
│                                                                    │
│  ✗ Application-Specific Roles                                     │
│    Ví dụ: "ROLE_DOCUMENT_APPROVER", "ROLE_WORKFLOW_ADMIN"         │
│    (Trừ khi là cross-app shared role)                            │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

**Nguyên tắc vàng**: Keycloak chỉ quản lý **authentication identity** và **technical access rights**. Tất cả **business authorization** phải nằm trong resource services.

---

## 3. Trách nhiệm Authentication

### 3.1 Authentication Flow

```
┌─────────┐         ┌──────────────┐         ┌───────────────┐
│Frontend │────────▶│ Auth-Service │────────▶│   Keycloak    │
│         │         │    (BFF)     │         │    (AuthN)    │
└─────────┘         └──────────────┘         └───────────────┘
     │                    │                        │
     │   1. Login Form    │                        │
     │───────────────────▶│                        │
     │                    │   2. Auth Request      │
     │                    │───────────────────────▶│
     │                    │                        │
     │                    │   3. Credential Check   │
     │                    │    (Password/MFA)     │
     │                    │◀──────────────────────│
     │                    │                        │
     │   4. Token Exchange│                        │
     │◀───────────────────│                        │
```

### 3.2 Authentication Methods

| Method | Use Case | Security Level |
|--------|----------|----------------|
| **Password** | Default login | Medium |
| **OTP (TOTP)** | 2FA enforcement | High |
| **SMS OTP** | Mobile user 2FA | Medium-High |
| **Email OTP** | Password reset | Medium |
| **WebAuthn (FIDO2)** | Passwordless, Phishing-resistant | Very High |
| **X.509 Certificate** | Machine-to-machine, VPN | Very High |
| **Kerberos/SPNEGO** | Intranet integration | High |

### 3.3 MFA Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                    MFA Implementation Strategy                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Level 1: No MFA (Development/Testing only)                    │
│  └── username/password only                                     │
│                                                                 │
│  Level 2: Optional MFA                                         │
│  └── User opt-in from account settings                         │
│                                                                 │
│  Level 3: Required MFA (Production recommended)               │
│  └── Mandatory for all users                                   │
│                                                                 │
│  Level 4: Conditional MFA                                      │
│  └── MFA required based on:                                    │
│      ├── IP range (outside corporate network)                  │
│      ├── User role (admin must MFA)                            │
│      ├── Time-based (off-hours access)                         │
│      └── Application sensitivity                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Khuyến nghị**: Production environment nên sử dụng **Level 3 hoặc Level 4** với WebAuthn hoặc TOTP.

### 3.4 Identity Federation

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Identity Federation Topology                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│                    ┌─────────────────────┐                         │
│                    │    Keycloak         │                         │
│                    │  (Broker)           │                         │
│                    └──────────┬──────────┘                         │
│                               │                                    │
│         ┌─────────────────────┼─────────────────────┐              │
│         │                     │                     │              │
│    ┌────▼────┐          ┌─────▼─────┐         ┌────▼────┐        │
│    │  LDAP   │          │  SAML IdP │         │ OIDC IdP│        │
│    │  (AD)   │          │ (Corp)    │         │(Social) │        │
│    └─────────┘          └───────────┘         └─────────┘        │
│                                                                     │
│    ✓ Auto-provisioning     ✓ SSO with Corp    ✓ Google/GitHub     │
│    ✓ Sync users            ✓ Federation        ✓ Enterprise SSO    │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Federation Use Cases**:

| Nguồn Identity | Khi nào dùng | Sync Strategy |
|----------------|--------------|---------------|
| LDAP/AD | Enterprise internal users | User federation with periodic sync |
| SAML IdP | SSO với hệ thống enterprise khác | Identity brokering |
| OIDC IdP | Cloud identity (Azure AD, Okta) | Identity brokering |
| Social Login | Customer-facing apps | Just-in-time provisioning |

---

## 4. Trách nhiệm OAuth2/OIDC

### 4.1 OAuth2 Flows

```
┌─────────────────────────────────────────────────────────────────────┐
│                    OAuth2 Flow Selection Guide                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Flow 1: Authorization Code + PKCE                                 │
│  ┌──────────┐    ┌────────────┐    ┌──────────────┐                │
│  │  Browser  │───▶│ Auth-Service│───▶│  Keycloak   │                │
│  │  (SPA)    │◀───│   (BFF)    │◀───│              │                │
│  └──────────┘    └────────────┘    └──────────────┘                │
│  └── Recommended for: Frontend applications                        │
│                                                                     │
│  Flow 2: Client Credentials                                         │
│  ┌──────────────┐         ┌──────────────┐                        │
│  │  Service A   │────────▶│   Keycloak   │                        │
│  │  (Service)   │         │              │                        │
│  └──────────────┘         └──────────────┘                        │
│  └── Recommended for: Service-to-service communication             │
│                                                                     │
│  Flow 3: Device Authorization Grant                                │
│  └── For CLI tools, smart TV, IoT devices                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 OIDC Endpoints

| Endpoint | Mục đích | Ai gọi |
|----------|----------|--------|
| `/.well-known/openid-configuration` | OIDC Discovery | All clients |
| `/authorize` | Authorization endpoint | Auth-Service (BFF) |
| `/token` | Token issuance | Auth-Service, Services |
| `/userinfo` | Get user profile | Auth-Service |
| `/logout` | Logout endpoint | Auth-Service |
| `/jwk-set` | Public keys for JWT validation | All resource servers |

### 4.3 Token Types

```
┌─────────────────────────────────────────────────────────────────────┐
│                         TOKEN TYPES                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. Access Token (JWT)                                             │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │ {                                                              │ │
│  │   "iss": "https://keycloak.example.com/realms/enterprise",    │ │
│  │   "sub": "user-uuid-12345",                                   │ │
│  │   "aud": "document-service",                                  │ │
│  │   "exp": 1716200000,                                         │ │
│  │   "iat": 1716196400,                                         │ │
│  │   "scope": "openid profile email",                           │ │
│  │   "realm_access": { "roles": ["USER"] },                      │ │
│  │   "resource_access": {                                        │ │
│  │     "document-service": { "roles": ["DOC_VIEWER"] }          │ │
│  │   }                                                           │ │
│  │ }                                                             │ │
│  │                                                               │ │
│  │ Lifetime: 5-15 phút (SHORT)                                  │ │
│  │ Storage: Memory only (KHÔNG localStorage)                   │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  2. Refresh Token                                                  │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │ {                                                              │ │
│  │   "iss": "https://keycloak.example.com/realms/enterprise",    │ │
│  │   "sub": "user-uuid-12345",                                   │ │
│  │   "type": "Refresh Token",                                   │ │
│  │   "exp": 1716800000  // 7 days                               │ │
│  │ }                                                              │ │
│  │                                                               │ │
│  │ Lifetime: 7-30 ngày (LONG)                                   │ │
│  │ Storage: HttpOnly, Secure Cookie (server-side)               │ │
│  │ Feature: Rotation (refresh token family)                      │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  3. ID Token (OIDC)                                                │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │ Standard OIDC claims for identity                             │ │
│  │ "name", "email", "preferred_username", "picture"             │ │
│  │                                                               │ │
│  │ Lifetime: 5-15 phút (same as access token)                   │ │
│  │ Use: Auth-Service only for user info                         │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.4 Token Expiration Strategy

| Token Type | Default Lifetime | Khi nào refresh |
|------------|------------------|-----------------|
| Access Token | 5 phút | Client tự refresh khi nhận 401 |
| Refresh Token | 15 phút | Access token expired |
| Offline Token | 30 ngày | Không có user session |

**Chiến lược**:

1. **Short-lived Access Token**: Giảm thiểu risk nếu token bị leak
2. **Refresh Token Rotation**: Mỗi lần refresh sinh token mới, revoke token cũ
3. **Absolute Session Timeout**: Refresh token có max lifetime (vd: 8 giờ)

---

## 5. SSO & Token Management

### 5.1 SSO Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         SSO FLOW                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   User Login lần đầu (Keycloak)                                    │
│   ┌────────┐      ┌────────────┐      ┌───────────────┐           │
│   │ User   │─────▶│ Auth-Service│─────▶│   Keycloak    │           │
│   │        │◀─────│            │◀─────│  (Login)      │           │
│   └────────┘      └────────────┘      └───────────────┘           │
│        │                 │                                          │
│        │         Session + Refresh Token                           │
│        │         (HttpOnly Cookie)                                 │
│        │                 │                                          │
│        ▼                 ▼                                          │
│   ┌─────────────────────────────────────────────────────────┐       │
│   │              TRUY CẬP APP KHÁC (SSO)                   │       │
│   │                                                         │       │
│   │   App A (document-service)    App B (workflow-service)   │       │
│   │         │                          │                   │       │
│   │         ▼                          ▼                   │       │
│   │   ┌────────────┐            ┌────────────┐            │       │
│   │   │ Validate   │            │ Validate    │            │       │
│   │   │ JWT        │            │ JWT         │            │       │
│   │   │ ✓ Valid    │            │ ✓ Valid     │            │       │
│   │   └────────────┘            └────────────┘            │       │
│   │         │                          │                   │       │
│   └─────────┼──────────────────────────┼───────────────────┘       │
│             │                          │                          │
│             ▼                          ▼                          │
│        ┌────────────────────────────────────┐                      │
│        │     Không cần login lại!          │                      │
│        │     (Single Sign-On achieved)      │                      │
│        └────────────────────────────────────┘                      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.2 Session Management

| Component | Storage | Lifetime | Notes |
|-----------|---------|----------|-------|
| Keycloak Session | Database (Infinispan) | 10 giờ default | SSO session |
| Auth-Service Session | Redis/DB | 8 giờ | BFF session |
| Refresh Token | HttpOnly Cookie | 7 ngày | Client-side |
| Access Token | Memory | 5 phút | Không persist |

**Session Cookie Strategy**:

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Cookie Configuration                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Auth Cookie (Refresh Token)                                        │
│  ├── Name: __Host-refresh-token / refresh-token                    │
│  ├── HttpOnly: TRUE                                                │
│  ├── Secure: TRUE (HTTPS only)                                     │
│  ├── SameSite: Strict / Lax                                       │
│  ├── Path: /auth                                                   │
│  └── Domain: .example.com (shared domain)                         │
│                                                                     │
│  Session Cookie (if used)                                          │
│  ├── HttpOnly: TRUE                                                │
│  ├── Secure: TRUE                                                 │
│  ├── SameSite: Lax                                                 │
│  ├── Path: /                                                       │
│  └── Max-Age: 28800 (8 hours)                                     │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.3 Logout Strategy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Logout Flow                                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   1. Frontend call: POST /auth/logout                             │
│                           │                                        │
│                           ▼                                        │
│   2. Auth-Service:                                                   │
│      ├── Revoke refresh token                                      │
│      ├── Invalidate session                                        │
│      └── Clear cookies                                             │
│                           │                                        │
│                           ▼                                        │
│   3. Auth-Service → Keycloak:                                     │
│      └── POST /realms/{realm}/logout                              │
│                           │                                        │
│                           ▼                                        │
│   4. Keycloak:                                                      │
│      ├── Revoke access tokens                                      │
│      ├── Invalidate SSO session                                    │
│      └── Notify other applications (backchannel logout)           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6. Chiến lược Realm, Client & Scope

### 6.1 Realm Strategy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Realm Architecture                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Realm: production                                                 │
│  ├── Users: All production users                                  │
│  ├── Clients: All production applications                         │
│  ├── Roles: Global roles (ADMIN, USER, SUPPORT)                   │
│  └── Identity Providers: Production federation                    │
│                                                                     │
│  Realm: development (optional)                                    │
│  ├── Users: Developers only                                        │
│  ├── Clients: Dev instances                                       │
│  └── For: Testing without affecting production                    │
│                                                                     │
│  Realm: [tenant-name] (Multi-tenant if needed)                    │
│  ├── For: Isolated tenant deployments                             │
│  └── Consider: Only if strict isolation required                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Quyết định quan trọng**:

| Scenario | Recommended Realm Setup |
|----------|-------------------------|
| Single organization | Single realm `production` |
| Multiple business units (isolated) | Multiple realms |
| Multi-tenant SaaS | Realm per tenant |
| Development/Testing | Separate realm or disabled security |

**Khuyến nghị cho hệ thống này**: Sử dụng **single realm `enterprise`** với client roles để phân chia ứng dụng.

### 6.2 Client Strategy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Client Architecture                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  REALM: enterprise                                                 │
│  │                                                                   │
│  ├──┬─ frontend-webapp                                              │
│  │  │   ├── Type: public                                           │
│  │  │   ├── Protocol: openid-connect                                │
│  │  │   ├── Auth flow: authorization_code + PKCE                  │
│  │  │   └── Redirect URIs: https://app.example.com/*              │
│  │                                                                     │
│  ├──┬─ auth-service                                                │
│  │  │   ├── Type: confidential                                      │
│  │  │   ├── Auth flow: client_credentials                          │
│  │  │   └── Service account enabled: true                          │
│  │  │   └── Use: Admin API calls from Auth-Service                │
│  │                                                                     │
│  ├──┬─ iam-service                                                  │
│  │  │   ├── Type: confidential                                      │
│  │  │   ├── Auth flow: client_credentials                          │
│  │  │   └── Service account enabled: true                          │
│  │  │   └── Use: User/Role management via Admin API               │
│  │                                                                     │
│  ├──┬─ document-service                                             │
│  │  │   ├── Type: confidential                                      │
│  │  │   ├── Auth flow: authorization_code + bearer                 │
│  │  │   └── Service account enabled: false                         │
│  │                                                                     │
│  └──┬─ internal-service                                             │
│     │   ├── Type: confidential                                      │
│     │   ├── Auth flow: client_credentials                          │
│     │   └── Use: Service-to-service (m2m)                         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.3 Client Types

| Client Type | Access | Use Case | Security |
|-------------|--------|----------|----------|
| **public** | Browser/mobile | SPA, Mobile app | PKCE required |
| **confidential** | Server-side | Backend services | Client secret required |
| **bearer-only** | API only | Microservices (validate only) | Token validation |

### 6.4 Scope Strategy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Scope Design                                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  DEFAULT SCOPES (always present):                                   │
│  ├── openid        : Required for OIDC                            │
│  ├── profile       : name, given_name, family_name, picture        │
│  └── email         : email, email_verified                        │
│                                                                     │
│  CUSTOM SCOPES:                                                     │
│  ├── document:read     : Read documents                            │
│  ├── document:write    : Create/edit documents                    │
│  ├── document:delete   : Delete documents                         │
│  ├── workflow:approve  : Approve workflow items                    │
│  └── admin:manage      : Admin operations                          │
│                                                                     │
│  SCOPE ASSIGNED AT:                                                │
│  └── Client scope assignment (mappable to roles)                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Quyết định thiết kế**: Thay vì dùng nhiều scopes phức tạp, ưu tiên dùng **client roles** vì:
- Role-based access control quen thuộc hơn với developers
- Dễ manage qua Admin UI
- Compatible với Spring Security's `@Secured`, `@RolesAllowed`

---

## 7. Role Strategy

### 7.1 Realm Roles vs Client Roles

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Role Types Comparison                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  REALM ROLES                          CLIENT ROLES                   │
│  ═══════════════                     ═══════════════               │
│                                                                     │
│  ┌─────────────────┐                  ┌─────────────────┐          │
│  │ REALM: enterprise│                │ CLIENT:         │          │
│  │                 │                  │ document-service│          │
│  │ ┌─────────────┐ │                  │                 │          │
│  │ │ SUPER_ADMIN │ │                  │ ┌─────────────┐│          │
│  │ │ ADMIN       │ │                  │ │DOC_VIEWER   ││          │
│  │ │ USER        │ │                  │ │DOC_EDITOR   ││          │
│  │ │ SUPPORT     │ │                  │ │DOC_ADMIN    ││          │
│  │ │ AUDITOR     │ │                  │ └─────────────┘│          │
│  │ └─────────────┘ │                  └─────────────────┘          │
│  └─────────────────┘                                                   │
│                                                                     │
│  • Global/company-wide         • Application-specific              │
│  • Cross-service access         • Only for this service            │
│  • Assigned to users           • Assigned to users/clients         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 7.2 Recommended Role Hierarchy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Role Hierarchy                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  REALM ROLES (Global):                                              │
│  ────────────────────                                               │
│                                                                     │
│  SUPER_ADMIN                                                        │
│      │                                                              │
│      ├── ADMIN                                                      │
│      │       │                                                      │
│      │       ├── USER_MANAGER                                       │
│      │       ├── AUDITOR                                            │
│      │       └── SUPPORT                                            │
│      │                                                              │
│      └── USER (base role for all authenticated users)               │
│                                                                     │
│  CLIENT ROLES (per application):                                   │
│  ────────────────────────────────                                   │
│                                                                     │
│  document-service:                                                  │
│      ├── DOC_ADMIN                                                  │
│      │       │                                                      │
│      │       ├── DOC_EDITOR                                         │
│      │       │       │                                              │
│      │       │       └── DOC_VIEWER                                 │
│      │       │                                                      │
│      │       └── DOC_VIEWER                                         │
│      │                                                              │
│      └── DOC_REviewer (workflow-specific)                           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 7.3 Role Assignment Matrix

| Role | Assignable By | Assignable To | Scope |
|------|---------------|---------------|-------|
| SUPER_ADMIN | System only | - | All |
| ADMIN | SUPER_ADMIN | ADMIN, USER_MANAGER | All |
| USER_MANAGER | ADMIN | USER, SUPPORT | IAM |
| AUDITOR | ADMIN | - | Read-only all |
| SUPPORT | ADMIN, USER_MANAGER | USER | Limited |
| USER | USER_MANAGER, ADMIN | - | Basic access |
| DOC_ADMIN | ADMIN | DOC_EDITOR, DOC_VIEWER | document-service |
| DOC_EDITOR | DOC_ADMIN | DOC_VIEWER | document-service |
| DOC_VIEWER | DOC_ADMIN | - | document-service |

### 7.4 Token Role Payload

**Access Token chỉ nên chứa**:

```json
{
  "sub": "user-uuid-12345",
  "iss": "https://keycloak/realms/enterprise",
  "aud": "document-service",
  "realm_access": {
    "roles": ["USER"]
  },
  "resource_access": {
    "document-service": {
      "roles": ["DOC_VIEWER"]
    }
  },
  "preferred_username": "john.doe",
  "email": "john.doe@example.com"
}
```

**Điều quan trọng**: Không đưa permissions chi tiết vào token vì:
1. Token bị cache ở nhiều nơi (CDN, proxies)
2. Refresh token cần để revoke permission ngay lập tức
3. Business rules thay đổi thường xuyên
4. Token size tăng, ảnh hưởng performance

---

## 8. Token Design Philosophy

### 8.1 JWT Token Structure

```
┌─────────────────────────────────────────────────────────────────────┐
│                    JWT Token Structure                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ HEADER                                                       │   │
│  │ {                                                           │   │
│  │   "alg": "RS256",                                          │   │
│  │   "typ": "JWT",                                            │   │
│  │   "kid": "key-id-12345"                                    │   │
│  │ }                                                           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                             .                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ PAYLOAD                                                      │   │
│  │ {                                                           │   │
│  │   "iss": "https://keycloak/realms/enterprise",             │   │
│  │   "sub": "user-uuid-12345",                                │   │
│  │   "aud": ["document-service", "workflow-service"],         │   │
│  │   "exp": 1716200000,                                      │   │
│  │   "iat": 1716196400,                                      │   │
│  │   "jti": "token-unique-id",                               │   │
│  │   "realm_access": { "roles": ["USER"] },                  │   │
│  │   "resource_access": {...},                               │   │
│  │   "scope": "openid profile email",                        │   │
│  │   "preferred_username": "john.doe",                        │   │
│  │   "email": "john.doe@example.com",                        │   │
│  │   "name": "John Doe"                                       │   │
│  │ }                                                           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                             .                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ SIGNATURE (RS256 with private key)                          │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 Token Claims Philosophy

| Claim Type | Included | Reason |
|------------|----------|--------|
| `sub` (subject) | ✅ Yes | User identifier |
| `iss` (issuer) | ✅ Yes | Token validation |
| `aud` (audience) | ✅ Yes | Restrict to specific service |
| `exp` (expiration) | ✅ Yes | Token expiry |
| `iat` (issued at) | ✅ Yes | Token age |
| `jti` (JWT ID) | ✅ Yes | Token revocation support |
| `realm_access.roles` | ✅ Yes (minimal) | Cross-service roles only |
| `resource_access.roles` | ✅ Yes (minimal) | Service-specific roles |
| `preferred_username` | ✅ Yes | Display purpose |
| `email` | ✅ Optional | If needed by services |
| **Detailed permissions** | ❌ No | Too volatile |
| **Organization hierarchy** | ❌ No | Separate lookup |
| **Business attributes** | ❌ No | Separate API call |

### 8.3 Token Signature Algorithm

| Algorithm | Use Case | Security |
|-----------|----------|----------|
| **RS256** (RSA + SHA-256) | Production recommended | Very high (asymmetric) |
| RS384 | If required by compliance | High |
| ES256 (ECDSA) | Modern alternative | High (smaller key) |
| HS256 (HMAC) | ❌ NOT recommended | Low (symmetric, secret sharing) |

**Khuyến nghị**: Sử dụng **RS256** với Keycloak's default key rotation.

---

## 9. Security Best Practices

### 9.1 PKCE (Proof Key for Code Exchange)

```
┌─────────────────────────────────────────────────────────────────────┐
│                    PKCE Flow                                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   1. Client generates:                                              │
│      ├── code_verifier: Random 43-128 char string                 │
│      └── code_challenge: BASE64URL(SHA256(code_verifier))         │
│                                                                     │
│   2. Authorization Request:                                         │
│      GET /authorize?                                               │
│        response_type=code                                          │
│        &client_id=frontend-webapp                                  │
│        &redirect_uri=https://app.example.com/callback             │
│        &scope=openid                                               │
│        &code_challenge=E9Melhoa2OwvFrEMTJguCHaEAK77j1Q               │
│        &code_challenge_method=S256                                 │
│                                                                     │
│   3. Token Request:                                                │
│      POST /token                                                   │
│      {                                                             │
│        "grant_type": "authorization_code",                         │
│        "code": "xyz...",                                           │
│        "redirect_uri": "...",                                      │
│        "client_id": "frontend-webapp",                             │
│        "code_verifier": "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gLFOi"  │
│      }                                                             │
│                                                                     │
│   4. Keycloak validates:                                           │
│      └── SHA256(code_verifier) == code_challenge                  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Tại sao PKCE bắt buộc cho public clients**:

| Threat | Without PKCE | With PKCE |
|--------|--------------|-----------|
| Authorization code interception | Vulnerable | Protected |
| Authorization code substitution | Vulnerable | Protected |
| Token generation by attacker | Possible | Prevented |

### 9.2 Security Checklist

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Security Configuration Checklist                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Authentication:                                                    │
│  ☑ Enforce password policy (min length, complexity, history)        │
│  ☑ Enable MFA for production                                       │
│  ☑ Configure account lockout (5 failed attempts, 15 min lockout)    │
│  ☑ Set session idle timeout (30 minutes)                          │
│  ☑ Enable CORS with strict origin validation                      │
│                                                                     │
│  Token Security:                                                   │
│  ☑ Access token lifetime ≤ 15 minutes                             │
│  ☑ Refresh token rotation enabled                                 │
│  ☑ Offline token disabled (unless required)                       │
│  ☑ Absolute session timeout (max 8-24 hours)                      │
│  ☑ Audience restriction enabled                                    │
│  ☑ Signed tokens with RS256                                       │
│                                                                     │
│  Client Security:                                                   │
│  ☑ PKCE required for public clients                               │
│  ☑ Client secret for confidential clients                         │
│  ☑ Valid redirect URIs whitelisted                               │
│  ☑ Proof Key for Code Exchange required                           │
│  ☑ Access token cipher enabled if needed                          │
│                                                                     │
│  Network Security:                                                  │
│  ☑ Admin Console accessible only from internal network           │
│  ☑ HTTPS enforced for all endpoints                               │
│  ☑ Mutual TLS for Keycloak cluster                                 │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 9.3 Token Revocation

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Token Revocation Strategy                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. Refresh Token Revocation                                        │
│     ├── Used when: User logout, password change, role change        │
│     └── Endpoint: POST /realms/{realm}/logout                      │
│                                                                     │
│  2. Single Token Revocation                                         │
│     ├── Endpoint: POST /realms/{realm}/token/revoke                │
│     └── Header: X-Token-Typehint: ACCESS or REFRESH                │
│                                                                     │
│  3. Refresh Token Family (Rotation)                                 │
│     ├── New refresh token on each use                              │
│     ├── Old token stored in "family"                              │
│     └── Reuse of old token = ALL revoked                          │
│                                                                     │
│  4. Keycloak Revocation Policies                                   │
│     └── Configure in Realm Settings → Tokens                      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 10. Gateway & Resource Server Integration

### 10.1 API Gateway Integration

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Gateway Integration                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌────────────┐     ┌──────────────┐     ┌────────────────────┐   │
│   │  Browser   │────▶│ API Gateway  │────▶│  Resource Service  │   │
│   └────────────┘     └──────────────┘     └────────────────────┘   │
│                            │                        │              │
│                            │                        │              │
│                            ▼                        │              │
│                     ┌──────────────┐                 │              │
│                     │  Keycloak   │◀────────────────┘              │
│                     │ (JWK Set)   │    Validate JWT                 │
│                     └──────────────┘                               │
│                                                                     │
│   Gateway responsibilities:                                         │
│   ├── Route to appropriate service                                 │
│   ├── Extract and validate JWT (optional)                          │
│   ├── Rate limiting                                                 │
│   ├── CORS handling                                                 │
│   └── Forward Bearer token to downstream                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 10.2 Resource Server Integration

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Resource Server Integration                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   Option 1: Token Validation at Gateway                             │
│   ┌────────┐    ┌───────────┐    ┌────────────────────────┐        │
│   │Request │───▶│  Gateway  │───▶│   Resource Service     │        │
│   │+Token  │    │ Validates │    │   (No validation)     │        │
│   └────────┘    └───────────┘    └────────────────────────┘        │
│   └── Simple, but gateway must be trusted                         │
│                                                                     │
│   Option 2: Token Validation at Each Service (RECOMMENDED)        │
│   ┌────────┐    ┌───────────┐    ┌────────────────────────┐        │
│   │Request │───▶│  Gateway  │───▶│   Resource Service     │        │
│   │+Token  │    │ (Forward) │    │   Validates JWT       │        │
│   └────────┘    └───────────┘    └────────────────────────┘        │
│                                     │                              │
│                                     ▼                              │
│                              ┌──────────────┐                      │
│                              │  Keycloak   │                      │
│                              │ (JWK Set)   │                      │
│                              └──────────────┘                      │
│   └── More secure, each service validates its own access          │
│                                                                     │
│   Option 3: Token Validation via Auth-Service                      │
│   ┌────────┐    ┌───────────┐    ┌────────────────────────┐        │
│   │Request │───▶│  Gateway  │───▶│   Resource Service     │        │
│   │+Token  │    │           │    │   (Validates via       │        │
│   └────────┘    └───────────┘    │    Auth-Service)       │        │
│                                  └────────────────────────┘        │
│   └── BFF pattern, Auth-Service handles validation                │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 10.3 Service-to-Service Authentication

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Service Account Flow (M2M)                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   Service A needs to call Service B                                │
│                                                                     │
│   ┌─────────────┐         ┌─────────────┐         ┌────────────┐  │
│   │ Service A   │────────▶│  Keycloak   │◀────────│ Service B  │  │
│   │             │         │             │         │            │  │
│   │ 1. Get      │ 2. JWT  │ 3. Validate │ 4. JWT  │ 5. Validate│  │
│   │    token    │────────▶│    token    │────────▶│    token   │  │
│   │             │         │             │         │            │  │
│   │ client_id:  │         │ iss, aud,   │         │ Check      │  │
│   │ service-a    │         │ exp, sig    │         │ audience   │  │
│   │             │         │             │         │            │  │
│   └─────────────┘         └─────────────┘         └────────────┘  │
│                                                                     │
│   Token Request:                                                   │
│   POST /token                                                      │
│   grant_type=client_credentials                                   │
│   &client_id=service-a                                             │
│   &client_secret=xxx                                               │
│   &scope=document:read                                             │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 11. Deployment & Scaling

### 11.1 Recommended Deployment Topology

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Production Deployment Topology                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│                         INTERNET                                    │
│                            │                                        │
│                            ▼                                        │
│                    ┌───────────────┐                               │
│                    │ Load Balancer │                               │
│                    │   (HTTPS)     │                               │
│                    └───────┬───────┘                               │
│                            │                                        │
│          ┌─────────────────┼─────────────────┐                     │
│          │                 │                 │                     │
│          ▼                 ▼                 ▼                     │
│   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐             │
│   │  Auth-Svc   │   │  IAM-Svc   │   │  Gateway    │             │
│   │   (BFF)     │   │             │   │             │             │
│   └──────┬──────┘   └──────┬──────┘   └──────┬──────┘             │
│          │                 │                 │                     │
│          └─────────────────┼─────────────────┘                     │
│                            │                                        │
│                            ▼                                        │
│                    ┌───────────────┐                               │
│                    │ Keycloak      │                               │
│                    │ (Clustered)   │                               │
│                    │               │                               │
│                    │ ┌─────┐ ┌─────┐│                             │
│                    │ │ KC1 │ │ KC2 ││                             │
│                    │ └─────┘ └─────┘│                             │
│                    │               │                               │
│                    │ ┌─────────────┐│                             │
│                    │ │  Database   ││                             │
│                    │ │ (PostgreSQL)││                             │
│                    │ └─────────────┘│                             │
│                    │               │                               │
│                    │ ┌─────────────┐│                             │
│                    │ │   Infinispan ││                            │
│                    │ │  (Sessions)  ││                            │
│                    │ └─────────────┘│                             │
│                    └───────────────┘                               │
│                                                                     │
│   Resource Services:                                                │
│   ├── document-service (scalable)                                 │
│   ├── workflow-service (scalable)                                 │
│   └── order-service (scalable)                                    │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 11.2 Keycloak Clustering

| Component | Configuration | Notes |
|-----------|---------------|-------|
| **Database** | PostgreSQL (recommended) | Master-master or primary-replica |
| **Session Cache** | Infinispan | Distributed cache for sessions |
| **Load Balancer** | Sticky session or stateless | For Keycloak admin console |
| **Discovery** | JDBC_PING or DNS_DNS_PING | For Infinispan cluster |

### 11.3 Scaling Considerations

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Scaling Strategy                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Keycloak Scaling:                                                 │
│  ├── Vertical: Start with larger instance                          │
│  ├── Horizontal: Add more Keycloak nodes                          │
│  ├── Database: Ensure proper indexing, connection pooling          │
│  └── Cache: Redis for external session cache                       │
│                                                                     │
│  Scaling Triggers:                                                 │
│  ├── > 10,000 concurrent users: Consider clustering               │
│  ├── > 100 requests/second: Load test and optimize                 │
│  ├── > 1 million users: Consider sharding realms                 │
│  └── > 10 million users: Multiple realms + sharding               │
│                                                                     │
│  Resource Service Scaling:                                         │
│  └── Stateless services scale horizontally                        │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 11.4 High Availability Configuration

```
┌─────────────────────────────────────────────────────────────────────┐
│                    HA Configuration Checklist                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Keycloak HA:                                                      │
│  ☑ Minimum 2 Keycloak instances (active-passive or active-active) │
│  ☑ PostgreSQL with streaming replication                          │
│  ☑ Infinispan with 2+ owners for distributed cache                │
│  ☑ Health check endpoint monitoring                               │
│  ☑ Graceful shutdown with proper timeout                          │
│                                                                     │
│  Database HA:                                                      │
│  ☑ PostgreSQL primary-replica or Patroni cluster                 │
│  ☑ Automated failover                                             │
│  ☑ Connection pooling (HikariCP)                                  │
│                                                                     │
│  Network HA:                                                        │
│  ☑ Load balancer health checks                                    │
│  ☑ DNS failover                                                    │
│  ☑ SSL certificate rotation automation                            │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 12. Security, Audit & Logging

### 12.1 Security Concerns

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Security Threats & Mitigations                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Threat: Token Theft                                                │
│  ├── Mitigation: Short-lived tokens (5-15 min)                     │
│  ├── Mitigation: HttpOnly cookies                                  │
│  └── Mitigation: Token audience restriction                       │
│                                                                     │
│  Threat: Authorization Code Interception                            │
│  ├── Mitigation: PKCE mandatory for public clients                 │
│  └── Mitigation: Strict redirect URI validation                    │
│                                                                     │
│  Threat: Refresh Token Replay                                      │
│  ├── Mitigation: Refresh token rotation                            │
│  └── Mitigation: Token family tracking                             │
│                                                                     │
│  Threat: Client Credential Leak                                    │
│  ├── Mitigation: Secure secret storage (Vault)                     │
│  ├── Mitigation: Regular secret rotation                           │
│  └── Mitigation: Audit log for client operations                  │
│                                                                     │
│  Threat: Privilege Escalation                                       │
│  ├── Mitigation: Principle of least privilege                      │
│  ├── Mitigation: Role hierarchy validation                         │
│  └── Mitigation: Business rule validation in services              │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 12.2 Audit Logging Requirements

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Audit Events to Log                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Authentication Events:                                            │
│  ├── LOGIN_SUCCESS: User logged in successfully                    │
│  ├── LOGIN_FAILED: Failed login attempt                           │
│  ├── LOGOUT: User logged out                                       │
│  ├── SESSION_EXPIRED: Session timeout                              │
│  └── MFA_SUCCESS/MFA_FAILED: MFA attempts                        │
│                                                                     │
│  Token Events:                                                      │
│  ├── TOKEN_ISSUED: Access/refresh token issued                    │
│  ├── TOKEN_REFRESH: Token refreshed                               │
│  ├── TOKEN_REVOKED: Token revoked                                │
│  └── TOKEN_EXPIRED: Token expired                                 │
│                                                                     │
│  Account Events:                                                   │
│  ├── USER_CREATED: New user created                              │
│  ├── USER_UPDATED: User profile changed                           │
│  ├── PASSWORD_CHANGED: Password updated                           │
│  ├── ACCOUNT_LOCKED: Account locked                              │
│  ├── ACCOUNT_ENABLED: Account enabled                             │
│  └── IDENTITY_LINK_ADDED: Federated identity linked              │
│                                                                     │
│  Admin Events:                                                     │
│  ├── CLIENT_CREATED/UPDATED/DELETED                              │
│  ├── ROLE_MAPPED: Role assigned to user                          │
│  └── REALM_UPDATED: Realm configuration changed                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 12.3 Logging Configuration

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Keycloak Logging Configuration                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Log Levels:                                                        │
│  ├── INFO: Successful logins, token issuance                       │
│  ├── WARN: Failed logins, suspicious activity                      │
│  └── ERROR: System errors, security violations                     │
│                                                                     │
│  Log Output:                                                        │
│  ├── File: keycloak-audit.log (JSON format)                       │
│  ├── Syslog: Forward to SIEM                                      │
│  └── Database: jboss-log-manager with JDBC handler                │
│                                                                     │
│  Sensitive Data:                                                   │
│  ├── NEVER log: passwords, tokens, secrets                        │
│  ├── NEVER log: full token content                               │
│  └── ALWAYS mask: email parts, user IDs in debug                  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 13. Anti-patterns & Boundaries

### 13.1 Anti-patterns

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ANTI-PATTERNS - KHÔNG LÀM                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ❌ 1. Custom OAuth2 Server                                         │
│     Không tự xây dựng OAuth2 authorization server                  │
│     Rủi ro: Bảo mật, tuân thủ standard, maintenance                │
│                                                                     │
│  ❌ 2. Business Permissions in JWT                                  │
│     Không đưa business permissions vào access token                │
│     Rủi ro: Cached, khó revoke, size lớn                            │
│                                                                     │
│  ❌ 3. Long-lived Access Tokens                                     │
│     Không dùng access token > 1 giờ                                │
│     Rủi ro: Token leak = persistent access                         │
│                                                                     │
│  ❌ 4. Keycloak Admin API từ mọi Service                            │
│     Không để resource services gọi Keycloak Admin API              │
│     Rủi ro: Security boundary violation, audit loss                 │
│                                                                     │
│  ❌ 5. Multiple Realms không có lý do                                │
│     Không tạo realm mới trừ khi cần tenant isolation thực sự       │
│     Rủi ro: User sync phức tạp, quản lý khó                        │
│                                                                     │
│  ❌ 6. Token Storage trong Browser (localStorage)                    │
│     Không lưu tokens trong localStorage/sessionStorage              │
│     Rủi ro: XSS attack                                             │
│                                                                     │
│  ❌ 7. Weak PKCE hoặc không dùng PKCE                               │
│     Phải dùng S256 method cho public clients                        │
│     Rủi ro: Authorization code theft                               │
│                                                                     │
│  ❌ 8. Realm Roles cho Application-specific Permissions             │
│     Dùng client roles cho app-specific permissions                 │
│     Rủi ro: Role pollution, security confusion                      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 13.2 Clear Boundaries

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Boundary Responsibilities                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  KEYCLOAK (Authorization Server):                                   │
│  ───────────────────────────────────────                            │
│  ✅ Xác thực người dùng (Authentication)                           │
│  ✅ Phát hành và quản lý tokens                                    │
│  ✅ SSO (Single Sign-On)                                           │
│  ✅ Identity Federation                                            │
│  ✅ Password policies                                              │
│  ✅ MFA/OTP                                                        │
│  ✅ Session management                                             │
│  ✅ OAuth2/OIDC implementation                                     │
│  ❌ Business authorization rules                                  │
│  ❌ Organization hierarchy logic                                  │
│  ❌ Workflow permissions                                           │
│  ❌ Domain-specific permissions                                    │
│                                                                     │
│  AUTH-SERVICE (BFF):                                                │
│  ──────────────────────                                            │
│  ✅ Login orchestration                                            │
│  ✅ Session management                                             │
│  ✅ Cookie handling                                                │
│  ✅ Token exchange (OAuth2 flows)                                  │
│  ✅ Unified auth API for frontend                                 │
│  ❌ User/role management                                           │
│  ❌ Business authorization                                         │
│  ❌ Direct database access for users                               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 14. Quyết định Thiết kế quan trọng

### 14.1 Tổng hợp Quyết định

| Quyết định | Lựa chọn | Lý do |
|------------|----------|-------|
| Authorization Server | Keycloak | Open source, enterprise-ready, OIDC compliant |
| Realm setup | Single realm `enterprise` | Đơn giản, dễ quản lý, user sync dễ |
| Token lifetime | Access: 5 phút, Refresh: 15 phút | Security vs UX balance |
| PKCE | Bắt buộc cho public clients | Bảo mật authorization code |
| Role type | Realm roles cho global, Client roles cho app | Separation of concerns |
| Token content | Minimal claims, no business data | Security, revocation, performance |
| Service auth | Client credentials | Service-to-service standard |
| Admin API access | Chỉ IAM-Service | Centralized, auditable |
| Session storage | Server-side (Redis/DB) | Security, scalability |
| MFA | Required in production | Bảo mật bắt buộc |

### 14.2 Trade-offs

| Trade-off | Current Decision | Alternative |
|-----------|------------------|-------------|
| Single vs Multiple Realms | Single realm | Multiple realms (complex tenant isolation) |
| Token in Cookie vs Header | Cookie (HttpOnly) cho refresh | Header cho pure stateless |
| Centralized vs Distributed AuthZ | Distributed business authZ | Centralized (bottleneck, single point of failure) |

---

## 15. Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│                    KEYCLOAK - SUMMARY                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ĐÂY LÀ:                                                            │
│  ✅ Authorization Server (OAuth2/OIDC)                             │
│  ✅ Authentication Server                                          │
│  ✅ SSO Provider                                                    │
│  ✅ Identity Federation Hub                                         │
│  ✅ Token Issuer                                                    │
│                                                                     │
│  KHÔNG PHẢI:                                                        │
│  ❌ Business Authorization Service                                 │
│  ❌ User Management Service                                        │
│  ❌ Organization Management                                        │
│  ❌ Workflow Permission Engine                                     │
│  ❌ Audit Logging Service                                          │
│                                                                     │
│  NGUYÊN TẮC VÀNG:                                                   │
│  🔐 Authentication tập trung (Centralized)                          │
│  🔐 Authorization phân tán (Distributed)                           │
│  🔐 Keycloak = Authentication, NOT Authorization                   │
│  🔐 Business logic = Resource Services                             │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```
