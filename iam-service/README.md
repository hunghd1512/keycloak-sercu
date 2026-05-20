# IAM Service

Enterprise Identity and Access Management Service - Centralized user, role, and organization management with Keycloak integration.

## Overview

IAM-Service acts as the centralized facade for Identity & Access Management operations, providing:

- **User Management**: Create, update, delete, enable/disable users
- **Role Management**: Role assignment with business rules validation
- **Organization Management**: Hierarchical organization structure
- **Audit Logging**: Comprehensive audit trail for all IAM operations
- **Keycloak Integration**: Seamless synchronization with Keycloak Admin API

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         IAM-SERVICE                               │
├─────────────────────────────────────────────────────────────────┤
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
│                         ┌─────────────────┐                         │
│                         │   Redis Cache   │                         │
│                         └─────────────────┘                         │
│                                                                     │
└─────────────────────────────────────────────────────────────────┘
```

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose (for local development)

### Run with Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f iam-service

# Stop all services
docker-compose down
```

### Run Locally

```bash
# Build the project
./mvnw clean package

# Run the application
./mvnw spring-boot:run

# Or run the JAR directly
java -jar target/iam-service-1.0.0-SNAPSHOT.jar
```

## Configuration

Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
```

Key configuration properties:

| Property | Description | Default |
|----------|-------------|---------|
| `KEYCLOAK_SERVER_URL` | Keycloak server URL | `http://localhost:8080` |
| `KEYCLOAK_REALM` | Keycloak realm name | `enterprise` |
| `DB_HOST` | PostgreSQL host | `localhost` |
| `REDIS_HOST` | Redis host | `localhost` |

## API Endpoints

### User Management

| Method | Endpoint | Description | Required Role |
|--------|----------|-------------|---------------|
| POST | `/users` | Create user | ADMIN, USER_MANAGER |
| GET | `/users` | List all users | ADMIN, USER_MANAGER |
| GET | `/users/{id}` | Get user by ID | authenticated |
| PUT | `/users/{id}` | Update user | ADMIN, USER_MANAGER |
| DELETE | `/users/{id}` | Delete user | SUPER_ADMIN |
| POST | `/users/{id}/disable` | Disable user | ADMIN |
| POST | `/users/{id}/enable` | Enable user | ADMIN |
| POST | `/users/{id}/reset-password` | Reset password | ADMIN |

### Role Management

| Method | Endpoint | Description | Required Role |
|--------|----------|-------------|---------------|
| GET | `/roles` | List all roles | authenticated |
| GET | `/roles/realm` | List realm roles | authenticated |
| GET | `/roles/client/{id}` | List client roles | authenticated |
| POST | `/roles` | Create role | SUPER_ADMIN |
| GET | `/users/{id}/roles` | Get user's roles | authenticated |
| POST | `/users/{id}/roles` | Assign roles | ADMIN, USER_MANAGER |
| DELETE | `/users/{id}/roles/{name}` | Revoke role | ADMIN, USER_MANAGER |

### Organization Management

| Method | Endpoint | Description | Required Role |
|--------|----------|-------------|---------------|
| POST | `/organizations` | Create organization | ADMIN |
| GET | `/organizations/tree` | Get org tree | authenticated |
| GET | `/organizations/{id}` | Get org by ID | authenticated |
| PUT | `/organizations/{id}` | Update organization | ADMIN |
| DELETE | `/organizations/{id}` | Delete organization | SUPER_ADMIN |

### Audit

| Method | Endpoint | Description | Required Role |
|--------|----------|-------------|---------------|
| GET | `/audit` | Get all audit logs | ADMIN, AUDITOR |
| GET | `/audit/actor/{id}` | Get by actor | ADMIN, AUDITOR |
| GET | `/audit/event/{type}` | Get by event type | ADMIN, AUDITOR |
| GET | `/audit/failed` | Get failed events | ADMIN, AUDITOR |

## Security

### Authentication

IAM-Service uses JWT tokens issued by Keycloak for authentication. All endpoints (except health checks) require a valid JWT token.

### Authorization

Role-based access control:

| Role | Permissions |
|------|-------------|
| SUPER_ADMIN | Full access to all operations |
| ADMIN | User management, role assignment, organization management |
| USER_MANAGER | Basic user management, role assignment |
| AUDITOR | Read-only access to audit logs |
| USER | Read own profile |

## Caching

IAM-Service uses Redis for distributed caching:

| Cache Name | TTL | Description |
|------------|-----|-------------|
| users | 5 min | User profiles |
| userRoles | 5 min | User role mappings |
| roles | 10 min | Role definitions |
| organizations | 30 min | Organization tree |
| permissions | 1 hour | Permission matrix |

## Monitoring

### Health Check

```bash
curl http://localhost:8082/actuator/health
```

### Metrics

```bash
curl http://localhost:8082/actuator/metrics
```

### Prometheus

```bash
curl http://localhost:8082/actuator/prometheus
```

## Project Structure

```
iam-service/
├── src/main/java/com/enterprise/iam/
│   ├── config/           # Configuration classes
│   ├── controller/       # REST controllers
│   ├── service/          # Business services
│   ├── domain/           # Domain entities & repositories
│   ├── application/      # DTOs & use cases
│   ├── integration/      # Keycloak integration
│   ├── security/         # Security components
│   ├── cache/            # Caching components
│   └── exception/        # Exception handling
├── src/main/resources/
│   └── application.yml   # Application configuration
├── src/test/             # Test sources
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

## License

Proprietary - Enterprise IAM Solution
