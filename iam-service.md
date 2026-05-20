# IAM-Service - Tài liệu Thiết kế Kiến trúc

## Mục lục

1. [Tổng quan IAM-Service](#1-tổng-quan-iam-service)
2. [Tại sao IAM-Service tồn tại](#2-tại-sao-iam-service-tồn-tại)
3. [Trách nhiệm của IAM-Service](#3-trách-nhiệm-của-iam-service)
4. [Quản lý User](#4-quản-lý-user)
5. [Quản lý Role](#5-quản-lý-role)
6. [Quản lý Organization](#6-quản-lý-organization)
7. [RBAC Design](#7-rbac-design)
8. [Caching & Synchronization](#8-caching--synchronization)
9. [API Design](#9-api-design)
10. [Package Structure](#10-package-structure)
11. [Security & Audit](#11-security--audit)
12. [Scalability & HA](#12-scalability--ha)
13. [Anti-patterns](#13-anti-patterns)
14. [Boundaries với các thành phần khác](#14-boundaries-với-các-thành-phần-khác)

---

## 1. Tổng quan IAM-Service

### 1.1 Mục đích

```
┌─────────────────────────────────────────────────────────────────────┐
│                         IAM-SERVICE                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│     ┌─────────────────────────────────────────────────────────┐     │
│     │              IAM-Service Architecture                   │     │
│     └─────────────────────────────────────────────────────────┘     │
│                                                                     │
│   ┌─────────────┐         ┌─────────────┐         ┌─────────────┐ │
│   │ Admin Portal │────────▶│ IAM-Service │────────▶│   Keycloak  │ │
│   │             │         │             │         │ (Admin API) │ │
│   └─────────────┘         └──────┬──────┘         └─────────────┘ │
│                                  │                                   │
│                                  ▼                                   │
│                         ┌─────────────────┐                         │
│                         │   IAM Database  │                         │
│                         │   (PostgreSQL)  │                         │
│                         └─────────────────┘                         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**IAM-Service** là service trung gian đứng giữa Admin Portal và Keycloak, chịu trách nhiệm:

- **Quản lý User** một cách có kiểm soát và theo business rules
- **Quản lý Role/Group** với authorization metadata
- **Quản lý Organization/Department** hierarchy
- **Business IAM Policies** enforcement
- **User Profile Aggregation** từ nhiều nguồn
- **Audit Logging** cho tất cả IAM mutations

### 1.2 IAM-Service là gì?

```
┌─────────────────────────────────────────────────────────────────────┐
│                    IAM-Service Definition                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  IAM-Service = Identity & Access Management Facade                 │
│                                                                     │
│  ├── Web Application / Admin Portal phục vụ quản trị viên         │
│  ├── Business logic cho IAM operations                             │
│  ├── Keycloak Admin Client wrapper với validation                 │
│  ├── Local database cho business-specific IAM metadata           │
│  └── Audit trail cho tất cả IAM changes                            │
│                                                                     │
│  IAM-Service ≠ Authorization Server                                │
│  IAM-Service ≠ Authentication Server                               │
│  IAM-Service ≠ Business Authorization                              │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. Tại sao IAM-Service tồn tại

### 2.1 Vấn đề khi không có IAM-Service

```
┌─────────────────────────────────────────────────────────────────────┐
│           Problems Without IAM-Service                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   Scenario: Resource Services call Keycloak Admin API directly     │
│                                                                     │
│   ┌──────────────┐    ┌──────────────┐    ┌───────────────┐      │
│   │ document-   │    │  workflow-   │    │   order-      │      │
│   │  service    │    │   service    │    │   service     │      │
│   └──────┬───────┘    └──────┬───────┘    └───────┬───────┘      │
│          │                   │                     │               │
│          │   Direct Admin API calls               │               │
│          └───────────────────┼───────────────────┘               │
│                              │                                      │
│                              ▼                                      │
│                    ┌─────────────────┐                             │
│                    │    Keycloak     │                             │
│                    │  Admin API      │                             │
│                    └─────────────────┘                             │
│                                                                     │
│   PROBLEMS:                                                         │
│   ❌ Mỗi service có cách quản lý user khác nhau                    │
│   ❌ Không có centralized audit trail                            │
│   ❌ Business rules phân tán khắp nơi                             │
│   ❌ Security boundary violation (business service không nên        │
│      có quyền Admin)                                               │
│   ❌ Duplicate user management code                               │
│   ❌ Hard để enforce company-wide policies                         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 Giải pháp có IAM-Service

```
┌─────────────────────────────────────────────────────────────────────┐
│              Solutions With IAM-Service                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌──────────────┐    ┌──────────────┐    ┌───────────────┐      │
│   │ document-   │    │  workflow-   │    │   order-      │      │
│   │  service    │    │   service    │    │   service     │      │
│   └──────┬───────┘    └──────┬───────┘    └───────┬───────┘      │
│          │                   │                     │               │
│          │  Business AuthZ   │  Business AuthZ    │               │
│          │  Only            │  Only              │               │
│          └───────────────────┴───────────────────┘               │
│                              │                                      │
│                              │                                      │
│                              ▼                                      │
│                    ┌─────────────────┐                             │
│                    │  IAM-Service    │                             │
│                    │  (Centralized) │                             │
│                    └────────┬────────┘                             │
│                             │                                      │
│                             │ Admin API calls                      │
│                             ▼                                      │
│                    ┌─────────────────┐                             │
│                    │    Keycloak     │                             │
│                    │  Admin API      │                             │
│                    └─────────────────┘                             │
│                                                                     │
│   BENEFITS:                                                         │
│   ✅ Centralized user management                                   │
│   ✅ Single audit trail                                           │
│   ✅ Consistent business rules                                     │
│   ✅ Proper security boundaries                                    │
│   ✅ Reusable code                                                 │
│   ✅ Company-wide policy enforcement                               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.3 IAM-Service vs Keycloak

| Khía cạnh | Keycloak | IAM-Service |
|-----------|----------|-------------|
| **Authentication** | ✅ Xử lý | ❌ Không |
| **Token Issuance** | ✅ Xử lý | ❌ Không |
| **User Storage** | ✅ Primary storage | ❌ Cache/extended metadata |
| **Admin API** | ✅ Cung cấp | ✅ Consumer only |
| **Business Rules** | ❌ Không | ✅ Xử lý |
| **Organization Hierarchy** | ❌ Không | ✅ Xử lý |
| **Audit Logging** | ✅ Basic | ✅ Extended |
| **User Profile Extension** | ❌ Limited | ✅ Unlimited |
| **Role Assignment Rules** | ❌ Basic | ✅ Business rules |

---

## 3. Trách nhiệm của IAM-Service

### 3.1 Tổng quan Trách nhiệm

```
┌─────────────────────────────────────────────────────────────────────┐
│                    IAM-Service Responsibilities                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  CORE RESPONSIBILITIES (MUST DO):                                   │
│  ───────────────────────────────────                               │
│                                                                     │
│  1. User Management                                               │
│     ├── Create/Update/Delete users                                 │
│     ├── Disable/Enable accounts                                   │
│     ├── Password reset triggering                                  │
│     ├── User profile management                                    │
│     └── User import/export                                         │
│                                                                     │
│  2. Role & Group Management                                        │
│     ├── Role assignment with business rules                         │
│     ├── Group management                                          │
│     ├── Role hierarchy management                                  │
│     └── Permission matrix management                               │
│                                                                     │
│  3. Organization Management                                        │
│     ├── Department/Unit hierarchy                                  │
│     ├── Organization tree management                              │
│     ├── User-Organization mapping                                 │
│     └── Organization-specific policies                            │
│                                                                     │
│  4. Business IAM Policies                                          │
│     ├── Who can assign which roles                                │
│     ├── Cross-department access rules                             │
│     ├── Delegation rules                                          │
│     └── Approval workflows                                        │
│                                                                     │
│  5. Integration with Keycloak                                     │
│     ├── Call Keycloak Admin API only                              │
│     ├── Sync users and roles                                      │
│     └── Keep data consistent                                      │
│                                                                     │
│  6. Audit Logging                                                  │
│     └── Log all IAM changes with context                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 Trách nhiệm chi tiết

| Trách nhiệm | Mô tả | Ví dụ |
|-------------|-------|-------|
| **User Lifecycle** | Quản lý vòng đời user | Create, disable, delete với business rules |
| **Role Assignment** | Gán role với validation | "SUPER_ADMIN only assignable by SUPER_ADMIN" |
| **Organization Mapping** | Map user vào org | User belongs to Department A |
| **Profile Aggregation** | Tổng hợp profile từ nhiều nguồn | Avatar từ HR, phone từ LDAP |
| **Policy Enforcement** | Enforce business policies | "Department admin chỉ quản user trong dept" |
| **Keycloak Sync** | Đồng bộ với Keycloak | Tạo user trong Keycloak khi create |
| **Audit Trail** | Ghi log tất cả thay đổi | Ai gán role gì cho ai, khi nào |

---

## 4. Quản lý User

### 4.1 User Management Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                    User Management Flow                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   Admin Portal                                                       │
│        │                                                              │
│        │ POST /iam/users (create user)                              │
│        ▼                                                              │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                    IAM-Service                               │   │
│   │                                                              │   │
│   │   1. Validate business rules                                  │   │
│   │      └── Requester có quyền tạo user không?                 │   │
│   │                                                              │   │
│   │   2. Validate user data                                      │   │
│   │      └── Email unique? Password policy?                      │   │
│   │                                                              │   │
│   │   3. Create user in Keycloak (Admin API)                    │   │
│   │      └── POST /users                                         │   │
│   │                                                              │   │
│   │   4. Store extended profile in IAM DB                       │   │
│   │      └── Department, manager, title, custom attrs           │   │
│   │                                                              │   │
│   │   5. Assign default roles                                    │   │
│   │      └── POST /users/{id}/role-mappings/realm               │   │
│   │                                                              │   │
│   │   6. Send notification (optional)                            │   │
│   │      └── Email, Slack notification                           │   │
│   │                                                              │   │
│   │   7. Audit log                                              │   │
│   │      └── Log who created, when, with what roles              │   │
│   │                                                              │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              ▼                                      │
│   ┌─────────────────┐      ┌─────────────────┐                     │
│   │    Keycloak     │      │   IAM Database  │                     │
│   │   (Core User)   │      │  (Extended)     │                     │
│   │                 │      │                  │                     │
│   │ - username      │      │ - department_id │                     │
│   │ - password      │◀─────│ - manager_id    │                     │
│   │ - email         │ Sync │ - title         │                     │
│   │ - first/last    │      │ - custom_attrs  │                     │
│   │ - enabled       │      │ - metadata      │                     │
│   └─────────────────┘      └─────────────────┘                     │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 User Data Storage Strategy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    User Data Storage                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  KEYCLOAK (Source of Truth for Authentication):                    │
│  ───────────────────────────────────────────                        │
│  ├── username                                                       │
│  ├── email                                                          │
│  ├── firstName, lastName                                           │
│  ├── password (hashed)                                            │
│  ├── enabled/disabled                                              │
│  ├── credentials (password, OTP)                                   │
│  ├── attributes (basic, small set)                                │
│  └── groups                                                         │
│                                                                     │
│  IAM-DATABASE (Business Metadata):                                 │
│  ─────────────────────────────────                                 │
│  ├── department_id (FK)                                            │
│  ├── manager_id (FK, self-reference)                              │
│  ├── title / position                                             │
│  ├── employee_id (external HR system)                             │
│  ├── phone_number                                                  │
│  ├── avatar_url                                                    │
│  ├── hire_date                                                     │
│  ├── cost_center                                                   │
│  ├── location                                                      │
│  ├── custom_attributes (JSONB)                                    │
│  ├── keycloak_user_id (FK)                                        │
│  ├── created_at, updated_at                                        │
│  └── last_synced_at                                                │
│                                                                     │
│  DATA SYNCHRONIZATION:                                              │
│  ├── Keycloak → IAM: User events (created, updated, deleted)     │
│  └── IAM → Keycloak: Only when needed (extended attrs)            │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.3 User CRUD Operations

| Operation | Keycloak Action | IAM-DB Action | Business Rules |
|-----------|-----------------|----------------|----------------|
| **Create** | Create user, set password | Insert profile | Check permission, unique email |
| **Read** | Get from Keycloak | Get from IAM-DB | Check permission |
| **Update** | Update Keycloak | Update IAM-DB | Check permission, audit |
| **Delete** | Delete from Keycloak | Soft delete in IAM-DB | Check permission, check dependencies |
| **Disable** | Set enabled=false | Set status=DISABLED | Check permission |
| **Reset Password** | Update credential | Log action | Check permission, notify user |

### 4.4 Password Reset Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Password Reset Flow                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   User clicks "Forgot Password"                                     │
│        │                                                              │
│        ▼                                                              │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                   IAM-Service                               │   │
│   │                                                              │   │
│   │   1. Validate email exists in Keycloak                      │   │
│   │                                                              │   │
│   │   2. Execute actions-queued-email-sender action             │   │
│   │      └── Keycloak sends reset email                         │   │
│   │                                                              │   │
│   │   3. Audit log: "Password reset requested for {email}"     │   │
│   │                                                              │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│   User clicks email link                                           │
│        │                                                              │
│        ▼                                                              │
│   Keycloak reset password page                                     │
│        │                                                              │
│        ▼                                                              │
│   User sets new password                                           │
│        │                                                              │
│        ▼                                                              │
│   Keycloak updates password                                        │
│        │                                                              │
│        ▼                                                              │
│   (Optional) IAM-Service notifies admin of password change        │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 5. Quản lý Role

### 5.1 Role Management Model

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Role Management Model                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                      REALM ROLES                            │   │
│   │                    (Global/Company-wide)                   │   │
│   │                                                              │   │
│   │    SUPER_ADMIN                                              │   │
│   │        │                                                    │   │
│   │        ├── ADMIN ──────────────────── can manage admins    │   │
│   │        │       │                                           │   │
│   │        │       ├── USER_MANAGER ──────── can manage users  │   │
│   │        │       │                                           │   │
│   │        │       ├── AUDITOR ────────────── read-only access │   │
│   │        │       │                                           │   │
│   │        │       └── SUPPORT ────────────── limited support  │   │
│   │        │                                                    │   │
│   │        └── USER ──────────────────────── base role           │   │
│   │                                                              │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                    CLIENT ROLES                             │   │
│   │                  (Application-specific)                    │   │
│   │                                                              │   │
│   │  CLIENT: document-service                                   │   │
│   │  ─────────────────────────────                               │   │
│   │    DOC_ADMIN                                               │   │
│   │        │                                                   │   │
│   │        ├── DOC_EDITOR                                       │   │
│   │        │       │                                           │   │
│   │        │       └── DOC_VIEWER                               │   │
│   │        │                                                   │   │
│   │        └── DOC_REVIEWER ──────────── workflow approver     │   │
│   │                                                              │   │
│   │  CLIENT: workflow-service                                    │   │
│   │  ───────────────────────────                                 │   │
│   │    WF_ADMIN                                                │   │
│   │        │                                                   │   │
│   │        ├── WF_EDITOR                                        │   │
│   │        └── WF_VIEWER                                        │   │
│   │                                                              │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.2 Role Hierarchy

```json
{
  "realm_roles": {
    "SUPER_ADMIN": {
      "description": "Super Administrator - full system access",
      "composite": true,
      "includes": ["ADMIN"],
      "assignable_by": []
    },
    "ADMIN": {
      "description": "Administrator - system configuration",
      "composite": true,
      "includes": ["USER_MANAGER", "AUDITOR", "SUPPORT"],
      "assignable_by": ["SUPER_ADMIN"]
    },
    "USER_MANAGER": {
      "description": "User Manager - manage users and roles",
      "composite": false,
      "assignable_by": ["ADMIN", "SUPER_ADMIN"]
    },
    "USER": {
      "description": "Base user - standard access",
      "composite": false,
      "assignable_by": ["ADMIN", "USER_MANAGER", "SUPER_ADMIN"]
    }
  }
}
```

### 5.3 Role Assignment Rules

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Business Role Assignment Rules                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  RULE 1: Who can assign what roles                                  │
│  ────────────────────────────────────                              │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  SUPER_ADMIN can assign: [SUPER_ADMIN, ADMIN, USER_MANAGER, │   │
│  │                            USER, AUDITOR, SUPPORT]          │   │
│  │                                                              │   │
│  │  ADMIN can assign: [USER_MANAGER, USER, AUDITOR, SUPPORT]  │   │
│  │                                                              │   │
│  │  USER_MANAGER can assign: [USER]                            │   │
│  │                                                              │   │
│  │  SUPPORT can assign: NONE (read-only)                      │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  RULE 2: Department-scoped role assignment                          │
│  ──────────────────────────────────────────────                    │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Department Admin can assign roles only to users in their  │   │
│  │  department or sub-departments.                              │   │
│  │                                                              │   │
│  │  Example:                                                    │   │
│  │  - Admin of "Engineering" can manage users in "Engineering" │   │
│  │  - Cannot manage users in "Marketing"                       │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  RULE 3: Self-service role restrictions                             │
│  ────────────────────────────────────────                          │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Users CANNOT assign roles to themselves.                  │   │
│  │  Only admins can assign roles.                              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.4 Role Assignment API Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Role Assignment Flow                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   Admin Portal                                                       │
│        │                                                              │
│        │ POST /iam/users/{userId}/roles                             │
│        │ Body: { "roles": ["DOC_EDITOR"], "clientId": "document-svc" }│
│        ▼                                                              │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                    IAM-Service                               │   │
│   │                                                              │   │
│   │   1. Validate requester permissions                         │   │
│   │      └── Requester có quyền assign role này không?         │   │
│   │                                                              │   │
│   │   2. Validate target user                                   │   │
│   │      └── User có tồn tại không? Đã disabled?               │   │
│   │                                                              │   │
│   │   3. Validate role assignment rules                         │   │
│   │      └── Role này có được assign trong department scope?    │   │
│   │                                                              │   │
│   │   4. Call Keycloak Admin API                                │   │
│   │      └── POST /admin/realms/{realm}/users/{id}/role-mappings│   │
│   │                                                              │   │
│   │   5. Update IAM database (if needed)                        │   │
│   │                                                              │   │
│   │   6. Audit log                                               │   │
│   │      └── Log: Admin X assigned Role Y to User Z at time T   │   │
│   │                                                              │   │
│   │   7. Send notifications (optional)                           │   │
│   │      └── Email user about new role                           │   │
│   │                                                              │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6. Quản lý Organization

### 6.1 Organization Hierarchy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Organization Hierarchy                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│                        ┌─────────────┐                              │
│                        │   COMPANY   │                              │
│                        │   (Root)    │                              │
│                        └──────┬──────┘                              │
│                               │                                     │
│          ┌────────────────────┼────────────────────┐               │
│          │                    │                    │                │
│    ┌─────▼─────┐        ┌─────▼─────┐        ┌─────▼─────┐       │
│    │   CTO     │        │    CFO    │        │    COO    │       │
│    │  Office   │        │  Office   │        │   Office  │       │
│    └─────┬─────┘        └─────┬─────┘        └─────┬─────┘       │
│          │                    │                    │                │
│    ┌─────┴─────┐        ┌─────┴─────┐        ┌─────┴─────┐       │
│    │Engineering│        │   Finance │        │   Sales   │       │
│    │           │        │           │        │           │       │
│    └─────┬─────┘        └─────┬─────┘        └─────┬─────┘       │
│          │                    │                    │                │
│    ┌─────┴─────┐        ┌─────┴─────┐        ┌─────┴─────┐       │
│    │  Backend  │        │    AP     │        │  APAC     │       │
│    │   Team    │        │  Team     │        │  Team     │       │
│    └───────────┘        └───────────┘        └───────────┘       │
│                                                                     │
│  Organization Properties:                                           │
│  ├── id (UUID)                                                     │
│  ├── name                                                          │
│  ├── code (short code for API)                                    │
│  ├── parent_id (FK to parent org)                                 │
│  ├── type (COMPANY, DIVISION, DEPARTMENT, TEAM)                    │
│  ├── manager_id (FK to user)                                      │
│  ├── path (materialized path: /1/2/5/)                            │
│  ├── level (depth in tree)                                        │
│  └── metadata (JSONB for custom attrs)                            │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.2 User-Organization Mapping

| Mapping Type | Mô tả | Ví dụ |
|-------------|-------|-------|
| **Direct Assignment** | User trực tiếp thuộc org | User "John" belongs to "Engineering" |
| **Manager** | User là manager của org | User "Jane" is manager of "Backend Team" |
| **Delegate** | User được ủy quyền quản lý org | User "Bob" can manage "Sales" temporarily |
| **Cross-functional** | User thuộc nhiều org | User works in both "Engineering" and "Product" |

### 6.3 Organization-Based Access Control

```
┌─────────────────────────────────────────────────────────────────────┐
│                Organization-Based Access Control                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  SCENARIO 1: Department Admin accesses users                        │
│  ─────────────────────────────────────────                          │
│  Department Admin of "Engineering" requests:                        │
│  GET /iam/users?department=engineering                             │
│                                                                     │
│  Result: Only users in "Engineering" and sub-departments           │
│                                                                     │
│  SCENARIO 2: Cross-department access attempt                       │
│  ─────────────────────────────────────────────                     │
│  Department Admin of "Engineering" requests:                        │
│  GET /iam/users?department=marketing                               │
│                                                                     │
│  Result: Access Denied (403 Forbidden)                             │
│                                                                     │
│  SCENARIO 3: Higher-level admin accesses any department             │
│  ─────────────────────────────────────────────────                  │
│  ADMIN requests:                                                   │
│  GET /iam/users?department=marketing                               │
│                                                                     │
│  Result: Success (ADMIN has global access)                         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 7. RBAC Design

### 7.1 Permission Model

```
┌─────────────────────────────────────────────────────────────────────┐
│                    RBAC Permission Model                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  PERMISSION = WHAT + WHO + WHICH                                    │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  PERMISSION STRUCTURE                                       │   │
│  │                                                              │   │
│  │  {                                                           │   │
│  │    "resource": "users",                                     │   │
│  │    "action": "create",                                      │   │
│  │    "scope": "department:engineering"                        │   │
│  │  }                                                           │   │
│  │                                                              │   │
│  │  Permission: "Can CREATE users in Engineering department"   │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ACTION TYPES:                                                     │
│  ├── CREATE: Tạo mới                                              │
│  ├── READ: Xem/thông tin                                          │
│  ├── UPDATE: Cập nhật                                            │
│  ├── DELETE: Xóa (soft delete)                                   │
│  ├── ASSIGN_ROLE: Gán role                                       │
│  └── MANAGE: Full management                                       │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 7.2 Role Hierarchy Implementation

```json
{
  "role_hierarchies": [
    {
      "role": "SUPER_ADMIN",
      "includes": ["ADMIN"],
      "inherited_permissions": ["*"]
    },
    {
      "role": "ADMIN",
      "includes": ["USER_MANAGER", "AUDITOR", "SUPPORT"],
      "inherited_permissions": ["user:read:*", "role:read:*"]
    },
    {
      "role": "USER_MANAGER",
      "includes": [],
      "permissions": ["user:create:own_dept", "user:read:*", "role:assign:basic"]
    },
    {
      "role": "AUDITOR",
      "includes": [],
      "permissions": ["audit:read:*", "user:read:*", "role:read:*"]
    },
    {
      "role": "USER",
      "includes": [],
      "permissions": ["profile:read:self", "profile:update:self"]
    }
  ]
}
```

### 7.3 Group Strategy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Group Strategy                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  GROUP vs ROLE:                                                     │
│  ─────────────                                                     │
│                                                                     │
│  ROLE = Job function / Access level                                 │
│  ├── Used for: Authorization decisions                             │
│  ├── Assigned: By admins                                          │
│  └── Stored: In Keycloak                                           │
│                                                                     │
│  GROUP = Organizational membership                                 │
│  ├── Used for: User categorization, email lists                    │
│  ├── Assigned: Automatically (org-based) or manually               │
│  ├── Stored: In Keycloak                                           │
│  └── Example: "Engineering Team", "All Managers"                   │
│                                                                     │
│  RECOMMENDED USAGE:                                                 │
│  ├── Use ROLES for authorization                                   │
│  ├── Use GROUPS for:                                              │
│  │    ├── Default role assignment                                 │
│  │    ├── LDAP group sync                                        │
│  │    └── Organizational categorization                           │
│  └── Avoid using GROUP membership for authorization                 │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 8. Caching & Synchronization

### 8.1 Caching Strategy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Caching Strategy                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  CACHE LAYERS:                                                      │
│  ─────────────                                                      │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  L1: In-Memory Cache (Caffeine/ConcurrentHashMap)          │   │
│  │  ─────────────────────────────────────────────────────────  │   │
│  │  - Scope: Per JVM instance                                 │   │
│  │  - TTL: 5 minutes                                          │   │
│  │  - Use: Frequently accessed, rarely changing data          │   │
│  │  - Example: Role definitions, permission matrix             │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              ▼                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  L2: Distributed Cache (Redis)                              │   │
│  │  ─────────────────────────────────────────────────────────  │   │
│  │  - Scope: All service instances                            │   │
│  │  - TTL: 15 minutes                                         │   │
│  │  - Use: User profiles, organization tree, role mappings    │   │
│  │  - Example: User:{userId} → UserProfile                    │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  CACHE KEY PATTERNS:                                                │
│  ───────────────────                                                │
│  ├── user:{userId}              → UserProfileDTO                  │
│  ├── user:{userId}:roles         → List<RoleDTO>                   │
│  ├── user:{userId}:org           → OrganizationDTO                 │
│  ├── org:tree                   → Full organization tree          │
│  ├── role:{roleId}              → RoleDTO                         │
│  └── permission:matrix          → PermissionMatrixDTO              │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 Cache Invalidation Strategy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Cache Invalidation Events                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  When user role changes:                                             │
│  ├── Invalidate: user:{userId}                                     │
│  ├── Invalidate: user:{userId}:roles                               │
│  └── Event: UserRoleChangedEvent                                   │
│                                                                     │
│  When organization changes:                                         │
│  ├── Invalidate: org:tree                                          │
│  ├── Invalidate: user:{affectedUserId}:org                        │
│  └── Event: OrganizationChangedEvent                               │
│                                                                     │
│  When role definition changes:                                       │
│  ├── Invalidate: role:{roleId}                                     │
│  ├── Invalidate: permission:matrix                                 │
│  └── Event: RoleDefinitionChangedEvent                             │
│                                                                     │
│  IMPLEMENTATION:                                                    │
│  ├── Event-driven invalidation                                     │
│  ├── TTL-based expiration as fallback                             │
│  └── Write-through for critical data                              │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.3 Keycloak Synchronization

```
┌─────────────────────────────────────────────────────────────────────┐
│                Keycloak Synchronization Strategy                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  SYNCHRONIZATION MODELS:                                            │
│  ──────────────────────────                                        │
│                                                                     │
│  Model 1: Pull (Polling) - NOT RECOMMENDED                         │
│  ┌─────────────┐         ┌─────────────┐                          │
│  │  IAM-Service│◀────────│  Keycloak   │                          │
│  │             │ poll    │  Database   │                          │
│  └─────────────┘         └─────────────┘                          │
│  └── Cons: Delay, database direct access, brittle                  │
│                                                                     │
│  Model 2: Push via Events - RECOMMENDED                           │
│  ┌─────────────┐         ┌─────────────┐         ┌─────────────┐ │
│  │  Keycloak   │────────▶│  Event Bus  │────────▶│  IAM-Service│ │
│  │             │ webhook │             │         │             │ │
│  └─────────────┘         └─────────────┘         └──────┬──────┘ │
│                                                          │          │
│                                                          ▼          │
│                                                   ┌─────────────┐  │
│                                                   │  IAM Cache  │  │
│                                                   │  Updated    │  │
│                                                   └─────────────┘  │
│  └── Pros: Real-time, event-driven, decoupled                          │
│                                                                     │
│  Model 3: Hybrid (Pull + Push)                                       │
│  └── Initial sync via API, then event-driven updates                │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.4 Event-Driven Sync Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                Event-Driven Sync Architecture                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   Keycloak Event Listener (SPI)                                     │
│        │                                                              │
│        │ Publish events to Kafka/Event Bus                          │
│        ▼                                                              │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                      EVENT BUS                               │   │
│   │                                                              │   │
│   │   Topics:                                                    │   │
│   │   ├── keycloak.user.created                                 │   │
│   │   ├── keycloak.user.updated                                 │   │
│   │   ├── keycloak.user.deleted                                 │   │
│   │   ├── keycloak.role.assigned                                │   │
│   │   └── keycloak.group.membership.changed                     │   │
│   │                                                              │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                   IAM-Service                               │   │
│   │                                                              │   │
│   │   Event Consumers:                                          │   │
│   │   ├── UserEventConsumer                                     │   │
│   │   │   └── Updates local user cache/DB                     │   │
│   │   ├── RoleEventConsumer                                    │   │
│   │   │   └── Updates role cache                               │   │
│   │   └── GroupEventConsumer                                    │   │
│   │       └── Updates group memberships                        │   │
│   │                                                              │   │
│   │   Outbox Pattern:                                           │   │
│   │   └── Ensures eventual consistency                        │   │
│   │                                                              │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 9. API Design

### 9.1 API Structure

```
┌─────────────────────────────────────────────────────────────────────┐
│                    IAM-Service API Structure                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  BASE URL: /api/v1/iam                                             │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  USER APIs                                                  │   │
│  │  ─────────────────────────────────────────────────────────  │   │
│  │  POST   /users                  - Create user               │   │
│  │  GET    /users                  - List users (paginated)    │   │
│  │  GET    /users/{id}             - Get user details          │   │
│  │  PUT    /users/{id}             - Update user               │   │
│  │  DELETE /users/{id}             - Delete user (soft)        │   │
│  │  POST   /users/{id}/disable     - Disable user             │   │
│  │  POST   /users/{id}/enable      - Enable user              │   │
│  │  POST   /users/{id}/reset-password - Trigger password reset│   │
│  │  GET    /users/me               - Get current user profile │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  ROLE APIs                                                  │   │
│  │  ─────────────────────────────────────────────────────────  │   │
│  │  GET    /roles                     - List all roles        │   │
│  │  GET    /roles/{id}                - Get role details       │   │
│  │  POST   /users/{id}/roles          - Assign roles to user   │   │
│  │  DELETE /users/{id}/roles/{roleId} - Remove role from user │   │
│  │  GET    /users/{id}/roles          - Get user's roles      │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  ORGANIZATION APIs                                          │   │
│  │  ─────────────────────────────────────────────────────────  │   │
│  │  GET    /organizations              - List organizations    │   │
│  │  GET    /organizations/tree         - Get org hierarchy    │   │
│  │  POST   /organizations               - Create organization   │   │
│  │  PUT    /organizations/{id}           - Update organization  │   │
│  │  GET    /organizations/{id}/users    - Get org's users      │   │
│  │  POST   /users/{id}/organizations    - Assign user to org   │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  PERMISSION APIs                                           │   │
│  │  ─────────────────────────────────────────────────────────  │   │
│  │  GET    /permissions                 - List permissions     │   │
│  │  GET    /permissions/matrix         - Get permission matrix │   │
│  │  GET    /users/{id}/permissions     - Get user's permissions │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  AUDIT APIs                                                 │   │
│  │  ─────────────────────────────────────────────────────────  │   │
│  │  GET    /audit/users                - User audit trail     │   │
│  │  GET    /audit/roles                - Role change trail    │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 9.2 API Request/Response Examples

```json
// POST /api/v1/iam/users
// Create User Request
{
  "username": "john.doe",
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "enabled": true,
  "attributes": {
    "employeeId": "EMP001",
    "departmentId": "dept-engineering",
    "title": "Senior Developer"
  },
  "roles": ["USER"],
  "groups": ["Engineering Team"]
}

// Create User Response (201 Created)
{
  "id": "user-uuid-12345",
  "username": "john.doe",
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "enabled": true,
  "createdAt": "2026-05-20T10:00:00Z",
  "createdBy": "admin-uuid",
  "attributes": {
    "employeeId": "EMP001",
    "departmentId": "dept-engineering"
  }
}
```

```json
// POST /api/v1/iam/users/{id}/roles
// Assign Role Request
{
  "roles": [
    {
      "name": "DOC_EDITOR",
      "clientId": "document-service"
    }
  ]
}

// Assign Role Response (200 OK)
{
  "userId": "user-uuid-12345",
  "assignedRoles": [
    {
      "name": "DOC_EDITOR",
      "clientId": "document-service",
      "assignedAt": "2026-05-20T10:05:00Z",
      "assignedBy": "admin-uuid"
    }
  ]
}
```

### 9.3 Error Handling

```json
// Error Response Format
{
  "error": {
    "code": "ROLE_ASSIGNMENT_FORBIDDEN",
    "message": "You don't have permission to assign ADMIN role",
    "details": {
      "requiredRole": "SUPER_ADMIN",
      "currentUserRole": "ADMIN"
    },
    "timestamp": "2026-05-20T10:05:00Z",
    "traceId": "abc123"
  }
}

// Common Error Codes
{
  "USER_NOT_FOUND": "User with ID {id} not found",
  "USER_ALREADY_EXISTS": "User with email {email} already exists",
  "ROLE_ASSIGNMENT_FORBIDDEN": "Insufficient permissions to assign role",
  "INVALID_ROLE_ASSIGNMENT": "Role cannot be assigned due to business rules",
  "DEPARTMENT_SCOPE_VIOLATION": "Cannot manage users outside your department",
  "USER_DISABLED": "User account is disabled",
  "CIRCULAR_ROLE_DEPENDENCY": "Circular role hierarchy detected"
}
```

---

## 10. Package Structure

### 10.1 Recommended Package Organization

```
┌─────────────────────────────────────────────────────────────────────┐
│                    IAM-Service Package Structure                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  com.enterprise.iam                                                 │
│  │                                                                   │
│  ├── config/                     # Configuration                     │
│  │   ├── SecurityConfig.java                                    │
│  │   ├── CacheConfig.java                                        │
│  │   ├── KeycloakConfig.java                                     │
│  │   └── WebConfig.java                                          │
│  │                                                                   │
│  ├── controller/                # REST Controllers                 │
│  │   ├── UserController.java                                     │
│  │   ├── RoleController.java                                     │
│  │   ├── OrganizationController.java                              │
│  │   ├── PermissionController.java                                │
│  │   └── AuditController.java                                     │
│  │                                                                   │
│  ├── service/                   # Business Services                │
│  │   ├── UserService.java                                        │
│  │   ├── RoleService.java                                        │
│  │   ├── OrganizationService.java                                 │
│  │   ├── PermissionService.java                                   │
│  │   ├── PolicyService.java                                       │
│  │   └── AuditService.java                                        │
│  │                                                                   │
│  ├── domain/                    # Domain Entities                   │
│  │   ├── entity/                                                 │
│  │   │   ├── User.java                                           │
│  │   │   ├── Role.java                                           │
│  │   │   ├── Organization.java                                   │
│  │   │   ├── Group.java                                          │
│  │   │   └── AuditLog.java                                       │
│  │   ├── repository/                                             │
│  │   │   ├── UserRepository.java                                 │
│  │   │   ├── RoleRepository.java                                 │
│  │   │   └── OrganizationRepository.java                         │
│  │   └── vo/                                                     │
│  │       ├── UserId.java                                          │
│  │       ├── RoleId.java                                          │
│  │       └── Permission.java                                      │
│  │                                                                   │
│  ├── application/               # Application Services (Use Cases) │
│  │   ├── usecase/                                                │
│  │   │   ├── CreateUserUseCase.java                              │
│  │   │   ├── AssignRoleUseCase.java                              │
│  │   │   ├── UpdateUserProfileUseCase.java                       │
│  │   │   └── ManageOrganizationUseCase.java                      │
│  │   └── dto/                                                    │
│  │       ├── CreateUserCommand.java                               │
│  │       ├── UserDTO.java                                        │
│  │       └── RoleAssignmentCommand.java                          │
│  │                                                                   │
│  ├── integration/               # External Integrations             │
│  │   ├── keycloak/                                               │
│  │   │   ├── KeycloakClient.java                                 │
│  │   │   ├── KeycloakUserMapper.java                             │
│  │   │   ├── KeycloakRoleMapper.java                             │
│  │   │   └── KeycloakEventListener.java                          │
│  │   └── event/                                                  │
│  │       ├── EventPublisher.java                                 │
│  │       └── EventConsumer.java                                  │
│  │                                                                   │
│  ├── security/                   # Security & Authorization         │
│  │   ├── IamSecurityService.java                                 │
│  │   ├── PolicyEvaluator.java                                    │
│  │   ├── ScopeValidator.java                                     │
│  │   └── RoleAssignmentValidator.java                            │
│  │                                                                   │
│  ├── cache/                     # Caching Layer                     │
│  │   ├── UserCacheService.java                                   │
│  │   ├── RoleCacheService.java                                   │
│  │   └── OrgCacheService.java                                    │
│  │                                                                   │
│  └── exception/                  # Exception Handling               │
│      ├── UserNotFoundException.java                               │
│      ├── RoleAssignmentForbiddenException.java                    │
│      └── GlobalExceptionHandler.java                              │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 10.2 Module Dependencies

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Module Dependencies                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   controller                                                        │
│      │                                                              │
│      ▼                                                              │
│   service ◀─────────────────────────────────┐                       │
│      │                                       │                       │
│      ▼                                       │                       │
│   application (use cases)                    │                       │
│      │                                       │                       │
│      ▼                                       │                       │
│   domain ◀────────────────┐                 │                       │
│      │                     │                 │                       │
│      ▼                     │                 │                       │
│   integration ─────────────┼─────────────────┘                       │
│      │                     │                                         │
│      ▼                     ▼                                         │
│   ┌───────────┐     ┌─────────────┐                                  │
│   │ Keycloak  │     │  Database   │                                  │
│   │  Client   │     │  (JPA/Hibernate) │                            │
│   └───────────┘     └─────────────┘                                  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 11. Security & Audit

### 11.1 Security Considerations

```
┌─────────────────────────────────────────────────────────────────────┐
│                    IAM-Service Security                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  AUTHENTICATION:                                                    │
│  ├── All endpoints require authentication                           │
│  ├── Service-to-service: mTLS or JWT (client credentials)           │
│  └── Admin endpoints: Elevated role required                       │
│                                                                     │
│  AUTHORIZATION:                                                     │
│  ├── Role-based access to IAM APIs                                  │
│  ├── Department-scoped access control                              │
│  ├── Business rules enforcement                                    │
│  └── Principle of least privilege                                  │
│                                                                     │
│  INPUT VALIDATION:                                                   │
│  ├── Validate all input parameters                                  │
│  ├── Sanitize user-provided data                                   │
│  └── SQL injection prevention (JPA)                                │
│                                                                     │
│  KEYCLOAK ADMIN API SECURITY:                                       │
│  ├── Use dedicated service account                                 │
│  ├── Store credentials in Vault                                    │
│  ├── Regular credential rotation                                   │
│  └── Audit all admin API calls                                     │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 11.2 Audit Logging Requirements

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Audit Events                                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  USER EVENTS:                                                       │
│  ├── USER_CREATED: {admin} created {user}                          │
│  ├── USER_UPDATED: {admin} updated {user} fields: {fields}        │
│  ├── USER_DELETED: {admin} deleted {user}                          │
│  ├── USER_DISABLED: {admin} disabled {user}                       │
│  ├── USER_ENABLED: {admin} enabled {user}                         │
│  ├── PASSWORD_RESET_REQUESTED: {admin} requested reset for {user} │
│  └── USER_LOGIN: {user} logged in from {ip}                       │
│                                                                     │
│  ROLE EVENTS:                                                       │
│  ├── ROLE_ASSIGNED: {admin} assigned {role} to {user}            │
│  ├── ROLE_REMOVED: {admin} removed {role} from {user}             │
│  ├── ROLE_CREATED: {admin} created role {role}                   │
│  └── ROLE_UPDATED: {admin} updated role {role}                   │
│                                                                     │
│  ORGANIZATION EVENTS:                                               │
│  ├── ORG_CREATED: {admin} created organization {org}             │
│  ├── ORG_UPDATED: {admin} updated organization {org}             │
│  ├── USER_ASSIGNED_TO_ORG: {admin} assigned {user} to {org}      │
│  └── USER_REMOVED_FROM_ORG: {admin} removed {user} from {org}    │
│                                                                     │
│  POLICY EVENTS:                                                     │
│  ├── POLICY_VIOLATION: {admin} attempted {action} - denied        │
│  └── POLICY_CHANGED: {admin} modified policy {policy}             │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 11.3 Audit Log Schema

```sql
CREATE TABLE iam_audit_log (
    id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    actor_id UUID NOT NULL,
    actor_username VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(100),
    action VARCHAR(50) NOT NULL,
    details JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    success BOOLEAN DEFAULT TRUE,
    error_message TEXT
);

-- Indexes for common queries
CREATE INDEX idx_audit_actor ON iam_audit_log(actor_id, timestamp);
CREATE INDEX idx_audit_target ON iam_audit_log(target_type, target_id);
CREATE INDEX idx_audit_event ON iam_audit_log(event_type, timestamp);
```

---

## 12. Scalability & HA

### 12.1 Scaling Considerations

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Scaling Strategy                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  IAM-Service is primarily I/O-bound, not CPU-bound:                │
│  ├── Keycloak Admin API calls (network latency)                   │
│  ├── Database queries                                              │
│  └── Cache operations                                              │
│                                                                     │
│  HORIZONTAL SCALING:                                                │
│  ├── Stateless design for horizontal scaling                      │
│  ├── Multiple instances behind load balancer                      │
│  ├── Redis cache for distributed caching                          │
│  └── Database connection pooling (HikariCP)                      │
│                                                                     │
│  CACHING STRATEGY:                                                  │
│  ├── Cache Keycloak data locally                                   │
│  ├── Reduce Keycloak Admin API calls                               │
│  ├── Use event-driven cache invalidation                          │
│  └── TTL-based cache expiration                                    │
│                                                                     │
│  DATABASE CONSIDERATIONS:                                           │
│  ├── Read replicas for read-heavy workloads                       │
│  ├── Index optimization for common queries                        │
│  └── Pagination for large result sets                             │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 12.2 High Availability Configuration

| Component | HA Strategy | Notes |
|-----------|-------------|-------|
| **IAM-Service** | Multiple instances | Stateless, behind LB |
| **Database** | Primary-replica | Read replicas for read operations |
| **Cache (Redis)** | Redis Cluster/Sentinel | For distributed cache |
| **Keycloak** | Clustered Keycloak | See Keycloak HA section |
| **Event Bus** | Kafka/RabbitMQ Cluster | For event-driven sync |

---

## 13. Anti-patterns

### 13.1 Anti-patterns to Avoid

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ANTI-PATTERNS - KHÔNG LÀM                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ❌ 1. Business Authorization in IAM-Service                       │
│     IAM-Service chỉ quản lý IAM metadata, không business logic      │
│     Ví dụ sai: "Can user A approve document B?"                   │
│     Đúng: Để resource service xử lý                               │
│                                                                     │
│  ❌ 2. Direct Keycloak Access from Resource Services                │
│     Resource services KHÔNG được gọi Keycloak Admin API            │
│     Rủi ro: Security boundary, audit loss, duplicate code           │
│                                                                     │
│  ❌ 3. Caching Tokens in IAM-Service                               │
│     Không cache access tokens hay refresh tokens                   │
│     Rủi ro: Token revocation không hiệu quả                        │
│                                                                     │
│  ❌ 4. Sync Users via Database Direct Access                       │
│     Không đọc Keycloak database trực tiếp                         │
│     Rủi ro: Brittle, unsupported, schema changes break             │
│                                                                     │
│  ❌ 5. Complex Business Workflows in IAM                           │
│     IAM-Service không nên chứa workflow phức tạp                   │
│     Nên tách thành workflow-service riêng                         │
│                                                                     │
│  ❌ 6. Global Cache Without Invalidation                           │
│     Cache phải có invalidation strategy                             │
│     Rủi ro: Stale data, inconsistent state                          │
│                                                                     │
│  ❌ 7. Synchronous Calls to Keycloak in Request Path               │
│     Nên dùng async/event-driven cho sync operations               │
│     Rủi ro: Latency, timeout, cascading failures                   │
│                                                                     │
│  ❌ 8. Storing Business Data in Keycloak Attributes               │
│     Chỉ dùng Keycloak attributes cho auth-related data            │
│     Business data nên trong IAM database                           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 13.2 Common Mistakes

| Mistake | Impact | Solution |
|---------|--------|----------|
| Calling Keycloak sync in request path | High latency | Use event-driven, async |
| No cache invalidation | Stale data | Event-driven invalidation |
| Putting business rules in IAM | Coupling, hard to maintain | Keep in resource services |
| Direct DB access to Keycloak | Brittle, unsupported | Use Admin API |
| No audit trail | Compliance risk | Implement comprehensive audit |
| Ignoring Keycloak events | Data inconsistency | Subscribe to events |

---

## 14. Boundaries với các thành phần khác

### 14.1 IAM-Service vs Keycloak

| IAM-Service | Keycloak |
|-------------|----------|
| **Consumer** of Admin API | **Provider** of Admin API |
| Business rules for IAM | Authentication logic |
| Extended user metadata | Core user identity |
| Organization hierarchy | Groups (basic) |
| Audit trail | Basic event log |
| Policy enforcement | Policy storage |

### 14.2 IAM-Service vs Resource Services

| IAM-Service | Resource Services |
|-------------|-------------------|
| **Centralized** IAM management | **Distributed** business logic |
| User/Role/Org management | Domain-specific business |
| Keycloak integration | Business authorization |
| Company-wide policies | Application-specific rules |
| **MUTATE** IAM data | **READ** IAM data (via APIs) |
| Audit IAM changes | Audit business actions |

### 14.3 IAM-Service vs Auth-Service

| IAM-Service | Auth-Service |
|-------------|--------------|
| User/Role management | Login orchestration |
| Keycloak Admin API | Keycloak OAuth2 API |
| Business rules for IAM | Session management |
| Admin Portal backend | Frontend BFF |
| Create/read/update users | Authenticate users |

### 14.4 Boundary Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│                    IAM-Service Boundaries                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  IAM-Service ĐẠT ĐƯỢC:                                             │
│  ✅ Quản lý user lifecycle (CRUD)                                  │
│  ✅ Quản lý role assignment                                       │
│  ✅ Quản lý organization hierarchy                                 │
│  ✅ Enforce business IAM policies                                  │
│  ✅ Gọi Keycloak Admin API                                         │
│  ✅ Lưu extended user profile                                      │
│  ✅ Audit tất cả IAM changes                                       │
│  ✅ Aggregation user profile                                       │
│                                                                     │
│  IAM-Service KHÔNG ĐƯỢC LÀM:                                      │
│  ❌ Authentication (Keycloak's job)                                │
│  ❌ Token issuance (Keycloak's job)                                │
│  ❌ Session management (Auth-Service's job)                        │
│  ❌ Business authorization logic (Resource Services' job)         │
│  ❌ Workflow permission (Workflow Service's job)                   │
│  ❌ Direct Keycloak database access                                │
│  ❌ Caching tokens                                                 │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 15. Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│                    IAM-SERVICE - SUMMARY                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  VAI TRÒ:                                                           │
│  ├── Identity & Access Management Facade                          │
│  ├── Centralized user/role/organization management               │
│  └── Keycloak Admin API consumer                                   │
│                                                                     │
│  ĐẶC ĐIỂM:                                                         │
│  ├── Business rules for IAM operations                            │
│  ├── Extended user metadata beyond Keycloak                       │
│  ├── Organization hierarchy management                             │
│  ├── Comprehensive audit logging                                   │
│  └── Event-driven sync with Keycloak                              │
│                                                                     │
│  TÍCH HỢP:                                                         │
│  ├── → Keycloak: Via Admin API                                    │
│  ├── ← Admin Portal: REST APIs                                    │
│  ├── ← Resource Services: Read-only IAM APIs                     │
│  └── → Event Bus: Sync events                                     │
│                                                                     │
│  NGUYÊN TẮC VÀNG:                                                   │
│  🔐 IAM-Service = IAM Metadata, NOT Business Logic               │
│  🔐 Centralized IAM Management                                    │
│  🔐 Resource Services = Business Authorization                  │
│  🔐 Keycloak Admin API = Single Entry Point                      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```
