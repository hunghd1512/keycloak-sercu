# Keycloak Overview — Góc nhìn Spring Security

> Tài liệu này giải thích Keycloak Admin REST API theo tương đương Spring Security để dễ hiểu và áp dụng vào codebase.

---

## Mục lục

1. [Cấu trúc cơ bản API](#1-cấu-trúc-cơ-bản-api)
2. [Các nhóm API chính và ánh xạ Spring Security](#2-các-nhóm-api-chính-và-ánh-xạ-spring-security)
3. [Authentication — "Bạn là ai?"](#3-authentication--bạn-là-ai)
4. [Authorization — "Bạn được làm gì?"](#4-authorization--bạn-được-làm-gì)
5. [Client Scopes + Protocol Mappers — "Token chứa gì?"](#5-client-scopes--protocol-mappers--token-chứa-gì)
6. [Cấu trúc tổ chức Identity trong Keycloak](#6-cấu-trúc-tổ-chức-identity-trong-keycloak)
7. [So sánh Spring Security vs Keycloak](#7-so-sánh-spring-security-vs-keycloak)
8. [Hai luồng chính khi dùng Keycloak](#8-hai-luồng-chính-khi-dùng-keycloak)
9. [Cách đọc REST API Documentation hiệu quả](#9-cách-đọc-rest-api-documentation-hiệu-quả)
10. [URI Pattern của Admin API](#10-uri-pattern-của-admin-api)

---

## 1. Cấu trúc cơ bản API

```
{base_url}/admin/realms/{realm}/...
```

Mọi request Admin API đều phải đi qua **realm**. Tương đương trong Spring Security, tất cả request đều phải qua `SecurityFilterChain` trước khi được xử lý.

### Base URL ví dụ

```
http://localhost:8080/admin/realms/my-realm/users
```

---

## 2. Các nhóm API chính và ánh xạ Spring Security

| Nhóm API Keycloak | Tương đương Spring Security | Vai trò |
|---|---|---|
| **Realms / Clients / Components** | `@Configuration` SecurityConfig | Cấu hình hệ thống bảo mật tổng thể |
| **Users / Groups** | `UserDetailsService`, `GrantedAuthority` | Quản lý Identity (ai là ai) |
| **Role Mappings** | `hasRole()`, `hasAuthority()` | Phân quyền cấp user/group |
| **Clients** | `ClientRegistration` (OAuth2) | Ứng dụng / Service muốn đăng nhập |
| **Authentication (Flows)** | `AuthenticationProvider`, `Filter` | Xác thực — bạn là ai |
| **Authorization (Permissions)** | `MethodSecurity`, `@PreAuthorize` | Ủy quyền — bạn được làm gì |
| **Client Scopes** | `GrantedAuthorities` + scope trong token | Phạm vi quyền khi cấp token |
| **Protocol Mappers** | `AuthenticationConverter`, claim transformer | Chèn claim vào JWT token |
| **Sessions** | `SessionRegistry` | Quản lý phiên đăng nhập |
| **Attack Detection** | `AuthenticationFailureHandler` | Khóa tài khoản khi brute-force |

---

## 3. Authentication — "Bạn là ai?"

### 3.1 Authentication Flows (tương đương FilterChain)

Authentication Flow là **pipeline xác thực** — giống như `SecurityFilterChain` trong Spring Security.

```
Authentication Management APIs
├── Authentication Flows
│   ├── browser flow       → đăng nhập thường (username/password + cookie)
│   ├── direct grant flow   → đăng nhập bằng API (Resource Owner Password Credentials)
│   ├── service account     → machine-to-machine (client_credentials)
│   ├── docker auth flow   → đăng nhập cho Docker registry
│   └── first broker login → khi user login qua IdP bên ngoài
├── Authenticators (tương đương AuthenticationProvider cụ thể)
│   ├── password           → DaoAuthenticationProvider
│   ├── OTP (TOTP/HOTP)    → TOTPAuthenticator
│   ├── kerberos           → LDAP/AD integration
│   └── broker (SAML/OIDC)→ ExternalAuthenticationProvider
├── Required Actions (bước bắt buộc sau đăng nhập)
│   ├── update-password
│   ├── verify-email
│   ├── configure-totp
│   └── update-profile
└── Form Actions (form-based authentication)
    ├── registration
    ├── profile-update
    └── conditions
```

### 3.2 Các API Authentication chính

```bash
# Flows
GET    /admin/realms/{realm}/authentication/flows
POST   /admin/realms/{realm}/authentication/flows
GET    /admin/realms/{realm}/authentication/flows/{id}
PUT    /admin/realms/{realm}/authentication/flows/{id}
DELETE /admin/realms/{realm}/authentication/flows/{id}

# Executions (bước trong flow)
GET    /admin/realms/{realm}/authentication/flows/{flowAlias}/executions
PUT    /admin/realms/{realm}/authentication/flows/{flowAlias}/executions
POST   /admin/realms/{realm}/authentication/executions/{executionId}/lower-priority
POST   /admin/realms/{realm}/authentication/executions/{executionId}/raise-priority

# Required Actions
GET    /admin/realms/{realm}/authentication/required-actions
POST   /admin/realms/{realm}/authentication/register-required-action
PUT    /admin/realms/{realm}/authentication/required-actions/{alias}
```

### 3.3 Minh họa luồng Authentication

```
User request login
       │
       ▼
┌─────────────────┐
│ Browser Flow    │ ←── Authentication Flow
└────────┬────────┘
         │
    ┌────▼────┐
    │ Cookie  │ ←── Authenticator: Identity Cookie Authenticator
    └────┬────┘
         │
    ┌────▼──────────┐
    │ Password      │ ←── Authenticator: Password (DaoAuthenticationProvider)
    └────┬──────────┘
         │
    ┌────▼──────────┐
    │ OTP?          │ ←── Authenticator: TOTP (nếu bật 2FA)
    └────┬──────────┘
         │
    ┌────▼──────────────────┐
    │ Required Actions?     │ ←── verify-email, update-password...
    └────┬──────────────────┘
         │
         ▼
  Authentication Success
  → Tạo token (access_token + refresh_token)
```

---

## 4. Authorization — "Bạn được làm gì?"

Keycloak có **2 cơ chế Authorization**:

### 4.1 Cách 1: Role-Based Access Control (RBAC) — đơn giản

```
Realm Roles       → vai trò mức hệ thống (ADMIN, VIEWER, SYSTEM_ADMIN)
Client Roles      → vai trò mức ứng dụng (app-admin, app-user, app-editor)
```

**Khi user đăng nhập, token chứa:**

```json
{
  "realm_access": {
    "roles": ["ADMIN", "VIEWER"]
  },
  "resource_access": {
    "my-client": {
      "roles": ["app-admin"]
    }
  }
}
```

**Tương đương Spring Security:**

```java
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAuthority('SCOPE_read:users')")
```

**Role Mapping APIs:**

```bash
# Realm-level role mappings
GET    /admin/realms/{realm}/users/{user-id}/role-mappings
POST   /admin/realms/{realm}/users/{user-id}/role-mappings
DELETE /admin/realms/{realm}/users/{user-id}/role-mappings

# Client-level role mappings
GET    /admin/realms/{realm}/users/{user-id}/role-mappings/clients/{client-id}
POST   /admin/realms/{realm}/users/{user-id}/role-mappings/clients/{client-id}
DELETE /admin/realms/{realm}/users/{user-id}/role-mappings/clients/{client-id}

# Available roles
GET    /admin/realms/{realm}/users/{user-id}/role-mappings/clients/{client-id}/available
GET    /admin/realms/{realm}/users/{user-id}/role-mappings/clients/{client-id}/composite
```

### 4.2 Cách 2: Permission-Based (Keycloak Authorization Services) — mạnh hơn

Dùng khi cần **Resource-level authorization** — giống Spring Security ACL hoặc method-level `@PreAuthorize("hasPermission(...)")`.

```
Authorization API
├── Resources          → đối tượng cần bảo vệ (file, API endpoint, document)
│   └── /api/orders, /api/users, /api/reports
├── Scopes             → hành động trên resource (read, write, delete, approve)
├── Policies           → quy tắc định nghĩa ai được phép
│   ├── role-policy    → user có role X thì được
│   ├── group-policy   → user thuộc group X thì được
│   ├── js-policy      → script tùy chỉnh (JavaScript)
│   ├── time-policy    → chỉ trong giờ hành chính
│   ├── user-policy    → user cụ thể
│   └── aggregate-policy → kết hợp nhiều policy
└── Permissions        → ánh xạ Resource + Scope → Policy
```

**Luồng hoạt động:**

```
1. Client yêu cầu resource (/api/reports/123)
2. App gọi Keycloak xin permission (RPT - Requesting Party Token)
3. Keycloak (PDP) kiểm tra Policies
4. Trả về token chứa các permission được granted
5. App kiểm tra permission trong token trước khi cho phép truy cập
```

**Authorization APIs:**

```bash
# Resource Server
GET    /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server
PUT    /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server

# Resources
GET    /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server/resource
POST   /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server/resource
GET    /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server/resource/{resource-id}
DELETE /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server/resource/{resource-id}

# Scopes
GET    /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server/scope
POST   /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server/scope

# Policies
GET    /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server/policy
POST   /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server/policy
GET    /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server/policy/providers

# Permissions
GET    /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server/permission
POST   /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server/permission

# Evaluate permission (test)
POST   /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server/permission/evaluate
POST   /admin/realms/{realm}/clients/{client-uuid}/authz/resource-server/policy/evaluate
```

---

## 5. Client Scopes + Protocol Mappers — "Token chứa gì?"

### 5.1 Client Scopes

Client Scope định nghĩa **tập hợp claims/permissions** có thể cấp cho user. Giống như `scope` trong OAuth2 specification.

```
Client Scopes
├── Default scopes (luôn có trong token)
│   ├── email       → claim "email"
│   ├── profile     → claims: name, family_name, given_name, preferred_username...
│   └── roles       → claims: realm_access.roles, resource_access.{client}.roles
└── Optional scopes (request thì mới có)
    ├── offline_access → refresh token dài hạn
    ├── address        → physical address
    └── phone          → phone number
```

### 5.2 Protocol Mappers

Protocol Mapper chèn claims vào token. Tương đương `JwtAuthenticationConverter` trong Spring Security.

```
Protocol Mappers (built-in)
├── Hardcoded claim         → chèn giá trị cố định
├── User attribute          → chèn user attribute vào token
├── User property          → chèn property của user (username, email...)
├── Role mapper            → chèn roles vào token
│   ├── realm roles        → realm_access.roles
│   └── client roles       → resource_access.{client}.roles
├── OIDC Claim             → map OIDC claim
├── SAML Assertion         → map SAML assertion
└── Script-based mapper    → custom JS để transform claims
```

**Client Scope APIs:**

```bash
GET    /admin/realms/{realm}/client-scopes
POST   /admin/realms/{realm}/client-scopes
GET    /admin/realms/{realm}/client-scopes/{client-scope-id}
PUT    /admin/realms/{realm}/client-scopes/{client-scope-id}
DELETE /admin/realms/{realm}/client-scopes/{client-scope-id}

# Gán scope cho client
GET    /admin/realms/{realm}/clients/{client-uuid}/default-client-scopes
PUT    /admin/realms/{realm}/clients/{client-uuid}/default-client-scopes/{clientScopeId}
DELETE /admin/realms/{realm}/clients/{client-uuid}/default-client-scopes/{clientScopeId}

GET    /admin/realms/{realm}/clients/{client-uuid}/optional-client-scopes
PUT    /admin/realms/{realm}/clients/{client-uuid}/optional-client-scopes/{clientScopeId}
DELETE /admin/realms/{realm}/clients/{client-uuid}/optional-client-scopes/{clientScopeId}
```

### 5.3 Token structure example

```json
{
  "iss": "http://localhost:8080/realms/my-realm",
  "sub": "user-uuid-123",
  "aud": "my-client",
  "exp": 1699999999,
  "iat": 1699999999,
  "preferred_username": "john.doe",
  "email": "john@enterprise.com",
  "name": "John Doe",
  "given_name": "John",
  "family_name": "Doe",
  "realm_access": {
    "roles": ["ADMIN", "VIEWER"]
  },
  "resource_access": {
    "my-client": {
      "roles": ["app-admin"]
    }
  },
  "scope": "openid profile email roles"
}
```

---

## 6. Cấu trúc tổ chức Identity trong Keycloak

```
Realm (tương đương ApplicationContext trong Spring)
│
├── Clients (OAuth2 Client / Resource Server)
│   ├── my-web-app       → enable authorization = có resource-server riêng
│   ├── my-api           → confidential client (server-side)
│   ├── mobile-app       → public client (client-side, PKCE bắt buộc)
│   └── service-account  → client_credentials grant
│
├── Client Scopes (định nghĩa các scope có thể cấp)
│   ├── default scopes    → luôn cấp
│   └── optional scopes   → request thì cấp
│
├── Realm Roles (vai trò mức hệ thống)
│   ├── ADMIN
│   ├── VIEWER
│   └── SYSTEM_ADMIN
│
├── Groups (nhóm user theo org/dept)
│   ├── /IT
│   │   ├── /IT/Development
│   │   └── /IT/Operations
│   └── /HR
│       └── /HR/Recruitment
│
├── Identity Providers (SSO bên ngoài)
│   ├── google
│   ├── github
│   ├── azure-ad
│   └── saml-idp-enterprise
│
├── Organizations (multi-tenant)
│   └── /enterprise-corp
│
└── Components (cấu hình low-level)
    ├── user-storage (LDAP, AD, custom)
    ├── key-providers (RSA, ECDSA signing keys)
    └── authenticators (custom authenticator)
```

---

## 7. So sánh Spring Security vs Keycloak

| Spring Security | Keycloak Admin API |
|---|---|
| `AuthenticationManager` | `Authentication Management` (flows) |
| `UserDetailsService` | `Users API` (CRUD user) |
| `GrantedAuthority` | `Roles API` + `Role Mappings API` |
| `@PreAuthorize` | `Authorization / Permissions API` |
| `SecurityFilterChain` | `Authentication Flows` (browser/ciba/direct) |
| `JwtAuthenticationConverter` | `Protocol Mappers` |
| `ClientRegistration` (OAuth2) | `Clients API` |
| `OAuth2AuthorizationService` | `Client Scopes API` |
| `SessionRegistry` | `Sessions API` |
| `AccountStatusUserDetailsChecker` | `Attack Detection API` |
| `PasswordEncoder` | `Realm Settings` (password policy) |
| `CORS configuration` | `Realm Settings` (cors settings) |
| `@EnableMethodSecurity` | Authorization Services (enable trên client) |
| `logout()` | `Logout API` (hoặc OIDC logout endpoint) |

---

## 8. Hai luồng chính khi dùng Keycloak

### Luồng 1: Keycloak làm Identity Provider (đơn giản — đang dùng)

```
┌──────────┐       ┌─────────────────────────────────┐       ┌──────────┐
│  User    │       │         IAM Service              │       │ Keycloak │
│ Browser  │───────│  (KeycloakClient + KeycloakMapper)│───────│ (IdP)    │
└──────────┘       └─────────────────────────────────┘       └──────────┘
                          │                                        │
                          │  1. Sync user/role qua Admin API        │
                          │────────────────────────────────────────►
                          │                                        │
                          │  2. User đăng nhập, Keycloak cấp JWT   │
                          │◄────────────────────────────────────────
                          │                                        │
                          │  3. App validate JWT, đọc roles từ claims│
                          ▼                                        ▼
                   Validate JWT
                   + @PreAuthorize(hasRole(...))
```

- Keycloak cấp token, app chỉ validate token và đọc roles từ claims
- Tương đương Spring Security OAuth2 Resource Server
- Các API chính: `Users API`, `Role Mappings API`, `Clients API`

### Luồng 2: Keycloak làm Authorization Server (mạnh hơn)

```
┌──────────┐       ┌─────────────────────────────────┐       ┌──────────┐
│  User    │       │         App (Resource Server)   │       │ Keycloak │
│          │───────│                                 │───────│ (AuthZ)  │
└──────────┘       │  1. Kiểm tra permission trong  │       │          │
                   │     token (RPT)                 │       │          │
                   └─────────────────────────────────┘       └──────────┘
                                                              │
                                                              │ PDP checks:
                                                              │ 1. Resource: /api/reports/123
                                                              │ 2. Scope: read
                                                              │ 3. Policy: role=MANAGER
                                                              ▼
                   Xem Authorization APIs → Policies → Permissions
```

- Dùng khi cần resource-level authorization
- Token là **RPT** (Requesting Party Token) chứa permission thực sự
- Tương đương `@PreAuthorize("hasPermission(#resource, 'READ')")`

---

## 9. Cách đọc REST API Documentation hiệu quả

Với mỗi nhóm API, hỏi 3 câu:

```
1. "Nó quản lý cái gì?"      → Entity (Users, Clients, Roles...)
2. "Tương đương component nào trong Spring Security?" → Để móc vào kiến thức đã có
3. "Khi nào dùng?"           → Thực hành trong codebase
```

### Ví dụ: Role Mappings

```bash
GET /admin/realms/{realm}/users/{user-id}/role-mappings/clients/{client-id}
```

```
1. Quản lý cái gì?   → role gán cho user cho 1 client cụ thể
2. Tương đương:      → @PreAuthorize("hasRole('ADMIN')") trên method
3. Khi nào dùng?     → Gán quyền app-specific cho user (KeycloakClient.java)
```

---

## 10. URI Pattern của Admin API

### 10.1 Users API

```bash
GET    /admin/realms/{realm}/users                    # Tìm users (phân trang)
POST   /admin/realms/{realm}/users                    # Tạo user
GET    /admin/realms/{realm}/users/count              # Đếm users
GET    /admin/realms/{realm}/users/{user-id}          # Lấy user theo id
PUT    /admin/realms/{realm}/users/{user-id}          # Cập nhật user
DELETE /admin/realms/{realm}/users/{user-id}          # Xóa user

# Role mappings
GET    /admin/realms/{realm}/users/{user-id}/role-mappings
POST   /admin/realms/{realm}/users/{user-id}/role-mappings
DELETE /admin/realms/{realm}/users/{user-id}/role-mappings
GET    /admin/realms/{realm}/users/{user-id}/role-mappings/clients/{client-id}
POST   /admin/realms/{realm}/users/{user-id}/role-mappings/clients/{client-id}

# Groups
GET    /admin/realms/{realm}/users/{user-id}/groups
PUT    /admin/realms/{realm}/users/{user-id}/groups/{group-id}
DELETE /admin/realms/{realm}/users/{user-id}/groups/{group-id}

# Sessions & credentials
GET    /admin/realms/{realm}/users/{user-id}/sessions
GET    /admin/realms/{realm}/users/{user-id}/credentials
DELETE /admin/realms/{realm}/users/{user-id}/credentials/{credential-id}
PUT    /admin/realms/{realm}/users/{user-id}/credentials/{credential-id}
```

### 10.2 Groups API

```bash
GET    /admin/realms/{realm}/groups
POST   /admin/realms/{realm}/groups
GET    /admin/realms/{realm}/groups/{group-id}
PUT    /admin/realms/{realm}/groups/{group-id}
DELETE /admin/realms/{realm}/groups/{group-id}
GET    /admin/realms/{realm}/groups/{group-id}/members
POST   /admin/realms/{realm}/groups/{group-id}/children

# Role mappings for group
GET    /admin/realms/{realm}/groups/{group-id}/role-mappings
POST   /admin/realms/{realm}/groups/{group-id}/role-mappings
DELETE /admin/realms/{realm}/groups/{group-id}/role-mappings
```

### 10.3 Clients API

```bash
GET    /admin/realms/{realm}/clients                   # List clients
POST   /admin/realms/{realm}/clients                  # Tạo client
GET    /admin/realms/{realm}/clients/{client-uuid}    # Get client (UUID, not client-id!)
PUT    /admin/realms/{realm}/clients/{client-uuid}    # Update client
DELETE /admin/realms/{realm}/clients/{client-uuid}    # Delete client

# Client secret
GET    /admin/realms/{realm}/clients/{client-uuid}/client-secret
POST   /admin/realms/{realm}/clients/{client-uuid}/client-secret

# Sessions
GET    /admin/realms/{realm}/clients/{client-uuid}/user-sessions
GET    /admin/realms/{realm}/clients/{client-uuid}/session-count
GET    /admin/realms/{realm}/clients/{client-uuid}/offline-sessions
```

### 10.4 Roles API

```bash
GET    /admin/realms/{realm}/roles                    # List realm roles
POST   /admin/realms/{realm}/roles                    # Tạo realm role
GET    /admin/realms/{realm}/roles/{role-name}       # Get role
PUT    /admin/realms/{realm}/roles/{role-name}
DELETE /admin/realms/{realm}/roles/{role-name}

# Client roles
GET    /admin/realms/{realm}/clients/{client-uuid}/roles
POST   /admin/realms/{realm}/clients/{client-uuid}/roles
GET    /admin/realms/{realm}/clients/{client-uuid}/roles/{role-name}
```

### 10.5 Sessions API

```bash
# Global
GET    /admin/realms/{realm}/sessions
DELETE /admin/realms/{realm}/sessions/{session-id}

# Per user
GET    /admin/realms/{realm}/users/{user-id}/sessions
DELETE /admin/realms/{realm}/users/{user-id}/sessions/{session-id}

# Per client
GET    /admin/realms/{realm}/clients/{client-uuid}/user-sessions
```

### 10.6 Attack Detection API

```bash
GET    /admin/realms/{realm}/attack-detection/brute-force/users/{user-id}
DELETE /admin/realms/{realm}/attack-detection/brute-force/users/{user-id}
DELETE /admin/realms/{realm}/attack-detection/brute-force/users  # Clear all
```

---

## Phụ lục: Lưu ý quan trọng khi làm việc với Keycloak Admin API

### UUID vs Client-ID

```
⚠️ Rất nhiều bug xảy ra vì nhầm lẫn giữa:
   - client-uuid    → internal ID (UUID), dùng trong URL admin API
   - client-id      → human-readable ID (string), dùng trong OAuth2 flow
   - user-id        → internal ID (UUID), dùng trong URL admin API
```

### Xác thực Admin API

```bash
# Admin Console: user có role realm-management/realm-admin
curl -X GET "http://localhost:8080/admin/realms/{realm}/users" \
  -H "Authorization: Bearer {access_token}"

# Service Account (machine-to-machine)
curl -X POST "http://localhost:8080/realms/{realm}/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id={client-id}" \
  -d "client_secret={client-secret}"
```

### Các Repository tham khảo trong codebase

| File | Mô tả |
|---|---|
| `KeycloakClient.java` | Giao tiếp Admin API với Keycloak |
| `KeycloakUserMapper.java` | Map user giữa Keycloak và DTO |
| `KeycloakUserConstants.java` | Constants cho field names |
| `OAuth2Constants.java` | OAuth2 endpoint constants |
| `KeycloakTokenService.java` | Xử lý token (trong auth-service) |

---

## Tài liệu tham khảo

- [Keycloak Admin REST API](https://www.keycloak.org/docs-api/latest/rest-api/index.html)
- [Keycloak Server Developer Guide](https://www.keycloak.org/guides)
- [OAuth 2.0 RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
