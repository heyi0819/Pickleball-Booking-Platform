# Pickleball Booking Platform

The approved product and engineering baseline is recorded in [PROJECT_CONTEXT.md](./PROJECT_CONTEXT.md). The repository has completed and sealed **Slices 0–7 and S8.1–S8.4**, including identity and organization scope, coach and lesson demand, matching and enrollment, course operations, finance and payout, notification delivery, and Admin Ops / failure recovery. The latest merged product closure is S8.4 on `main`.

## Prerequisites

- Git
- JDK 21
- Node.js 24 LTS with npm 11+
- Docker Desktop with Docker Engine and Compose

## Local setup

1. Copy `.env.example` to `.env` and adjust only your local values. Never commit it.
2. Start PostgreSQL 18:

   ```bash
   docker compose up -d postgres
   ```

3. Run the backend from `backend/`:

   ```powershell
   .\mvnw.cmd test
   .\mvnw.cmd spring-boot:run
   ```

   On macOS/Linux use `./mvnw test` and `./mvnw spring-boot:run`. The first command downloads Maven through the committed wrapper.

4. Run frontend checks from `frontend/`:

   ```bash
   npm ci
   npm run typecheck
   npm run lint
   npm test
   npm run build
   ```

The backend health endpoint is available at [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health). The frontend development servers can be started with `npm run dev -w @pickleball/liff` or `npm run dev -w @pickleball/admin`.

## Repository layout

```text
backend/                 Java 21 / Spring Boot modular monolith
  src/main/java/...      feature modules with api/application/domain/infrastructure boundaries
  src/main/resources/db/migration/  Flyway migrations
frontend/                npm workspace, Node 24 LTS
  apps/liff/             member-facing LIFF foundation
  apps/admin/            committee/admin foundation
  packages/              ui, api-client, shared, config
compose.yaml             PostgreSQL 18 local service
.github/workflows/ci.yml GitHub Actions validation
```

## Scope

The approved roadmap currently ends at Slice 8. After S8.4 Closure, the next legal step is to review and close focused maintenance/documentation PRs, then have the maintainer explicitly define and approve the scope, risks, and acceptance gate for any next product Slice. Do not automatically start Slice 9 or a Future Extension.
