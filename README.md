# DayClose

AI-powered inventory management assistant for small businesses. Handles customer calls, reserves stock, and queues commitments for owner approval.

## Architecture

**Microservices with database-per-service:**
- `api-gateway` - Routing, JWT validation (port 8080)
- `auth-service` - Authentication, tenants, users (port 8081)
- `inventory-service` - Items, stock, reservations (port 8082)
- `commitment-service` - Orders, approvals, workflow (port 8083)
- `knowledge-service` - Catalog, FAQs, RAG (port 8084)
- `notification-service` - SMS, reminders (port 8085)
- `voice-agent-service` - Call handling, STT/LLM/TTS (port 8086)

**Stack:** Java 21, Spring Boot 3, PostgreSQL 16, RabbitMQ, Gradle

## Quick Start

### 1. Start infrastructure
```bash
cd deploy/compose
docker-compose up -d
```

This starts:
- PostgreSQL (port 5432) with 6 databases
- RabbitMQ (ports 5672, 15672)
- Prometheus (port 9090)
- Grafana (port 3000)

### 2. Build services
```bash
./gradlew build
```

### 3. Run services
```bash
# Terminal 1
./gradlew :services:auth-service:bootRun

# Terminal 2
./gradlew :services:inventory-service:bootRun

# Terminal 3
./gradlew :services:commitment-service:bootRun

# Terminal 4
./gradlew :services:api-gateway:bootRun
```

### 4. Test workflow
```bash
bash test-workflow.sh
```

## Core Features (Implemented)

✅ **Auth Service**
- JWT authentication (RS256)
- Refresh token rotation
- Multi-tenant isolation
- JWKS endpoint

✅ **Inventory Service**
- Database-enforced no-oversell guarantee
- TTL stock reservations (30 min default)
- Atomic operations with row-level locking
- Multi-line reservations with deadlock prevention
- Append-only audit ledger
- Background expiry sweeper
- Abuse limits per customer/tenant

✅ **Commitment Service**
- State machine: created → pending_approval → approved → committed
- Owner approval queue
- Event sourcing for audit trail
- Idempotent event handling (inbox pattern)

✅ **API Gateway**
- Route-based forwarding
- JWT validation
- Correlation ID tracking

## API Examples

### Register & Login
```bash
# Register tenant
curl -X POST http://localhost:8081/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "tenantName": "My Shop",
    "email": "owner@shop.com",
    "password": "secure123",
    "role": "OWNER"
  }'

# Response includes access_token
```

### Create Item
```bash
curl -X POST http://localhost:8082/v1/items \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "WIDGET-001",
    "name": "Widget",
    "pricePaise": 10000,
    "unit": "pcs"
  }'
```

### Receive Stock
```bash
curl -X POST http://localhost:8082/v1/stock/receive \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "itemId": "...",
    "locationId": "...",
    "qty": 100
  }'
```

### Reserve Stock
```bash
curl -X POST http://localhost:8082/v1/reservations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "commitmentId": "...",
    "itemId": "...",
    "locationId": "...",
    "qty": 20,
    "customerRef": "customer-123",
    "ttl": "30 minutes"
  }'
```

### Create Commitment
```bash
curl -X POST http://localhost:8083/v1/commitments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "customerRef": "customer-123",
    "type": "order",
    "lines": [
      {
        "itemId": "...",
        "locationId": "...",
        "reservationId": "...",
        "qty": 20,
        "pricePaise": 10000
      }
    ]
  }'
```

### Approve Commitment
```bash
curl -X POST http://localhost:8083/v1/commitments/{id}/approve \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "actor": "owner",
    "reason": "Approved"
  }'
```

## Project Status

See [CURRENT_STATUS.md](../CURRENT_STATUS.md) for detailed progress.

**Completed:**
- ✅ Auth service (100%)
- ✅ Inventory service (100%)
- ✅ Commitment service (80% - saga orchestration pending)
- ✅ API Gateway (100%)

**In Progress:**
- 🚧 Event-driven sagas (approve → commit stock → notify)
- 🚧 Policy engine for auto-approval rules
- 🚧 Notification service
- 🚧 Knowledge service (RAG)
- 🚧 Voice agent service

## Architecture Decisions

See [DayClose Architecture Decision Records.md](../DayClose Architecture Decision Records.md)

Key decisions:
- ADR-0001: Microservices with Spring Boot and PostgreSQL
- ADR-0002: JWT authentication
- ADR-0005: TTL stock reservations
- ADR-0006: Transactional outbox + RabbitMQ

## Database Migrations

Each service has Flyway migrations in `src/main/resources/db/migration/`:

**inventory-service:**
- V1: Core schema (items, stock, reservations, functions)
- V2: Hardening (idempotency, expiry checks, tenant validation)
- V3: Outbox table
- V4: Row-level security (RLS)
- V5: Tenant settings and abuse limits

**auth-service:**
- V1: Users, tenants, refresh tokens, service credentials

**commitment-service:**
- V1: Commitments, lines, events, policy rules, inbox, outbox

## Testing

```bash
# Unit tests
./gradlew test

# Integration tests (with Testcontainers)
./gradlew test -DincludeTags=integration

# Build and test
./gradlew build

# Concurrency test (shell script)
cd services/inventory-service
bash ../../concurrency_test.sh
```

## Monitoring

- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **RabbitMQ Management**: http://localhost:15672 (dayclose/dayclose_password)

## Development

```bash
# Format code
./gradlew spotlessApply

# Check style
./gradlew spotlessCheck

# Clean build
./gradlew clean build
```

## License

Internal project - All rights reserved
