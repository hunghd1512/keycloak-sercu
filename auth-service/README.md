# Auth Service

Authentication BFF (Backend for Frontend) Service - Spring Boot 3.2

## Overview

Auth-Service là service trung gian giữa Frontend và Keycloak, đóng vai trò:
- OAuth2 Client wrapper cho Frontend
- Session Management với Redis
- Token refresh tự động
- Cookie-based authentication

## Architecture

```
Frontend ──▶ Auth-Service ──▶ Keycloak
              │
              ├── Session stored in Redis
              └── Cookie (HttpOnly) for session ID
```

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.9+
- Redis 7+
- Keycloak 24+

### Configuration

1. Copy environment template:
```bash
cp .env.example .env
```

2. Update `.env` with your Keycloak configuration

3. Create Keycloak client:
   - Go to Keycloak Admin Console
   - Create new client: `auth-service`
   - Client Protocol: `openid-connect`
   - Access Type: `confidential`
   - Valid Redirect URIs: `http://localhost:8081/*`

### Run with Maven

```bash
# Development
mvn spring-boot:run

# With custom config
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=file:./custom.yml"
```

### Run with Docker

```bash
# Build image
docker build -t auth-service:latest .

# Run with docker-compose
docker-compose up -d
```

### Run with Docker Compose (full stack)

```bash
docker-compose -f docker-compose.yml up -d
```

## API Endpoints

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/login` | Login with username/password |
| POST | `/auth/logout` | Logout current session |
| POST | `/auth/logout-all` | Logout all user sessions |
| POST | `/auth/refresh` | Refresh access token |
| GET | `/auth/me` | Get current user info |

### Session Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/auth/sessions` | List all active sessions |
| DELETE | `/auth/sessions/{id}` | Revoke specific session |
| POST | `/auth/introspect` | Check session validity |

## API Examples

### Login

```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "john", "password": "secret"}' \
  -c cookies.txt
```

### Get User Info

```bash
curl http://localhost:8081/auth/me \
  -b cookies.txt
```

### Refresh Token

```bash
curl -X POST http://localhost:8081/auth/refresh \
  -b cookies.txt
```

### Logout

```bash
curl -X POST http://localhost:8081/auth/logout \
  -b cookies.txt
```

## Configuration Reference

### application.yml

```yaml
server:
  port: 8081

keycloak:
  server-url: http://localhost:8080
  realm: enterprise
  client-id: auth-service
  client-secret: your-secret

spring:
  data:
    redis:
      host: localhost
      port: 6379

auth:
  session:
    timeout: 28800  # 8 hours
    max-concurrent-sessions: 3
  cookie:
    http-only: true
    secure: false  # Set true in production
    same-site: Strict
```

## Security Features

- HttpOnly cookies for session ID
- SameSite cookie attribute
- CSRF token support
- Session invalidation on logout
- Concurrent session limit
- Token revocation on logout

## Monitoring

Health check endpoint:
```
GET /actuator/health
```

Metrics:
```
GET /actuator/metrics
```

## License

Proprietary - Enterprise Internal Use Only
