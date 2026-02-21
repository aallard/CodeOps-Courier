# CodeOps-Courier

Full-featured API testing and development platform (Postman replacement) for the CodeOps ecosystem.

## Quick Start

```bash
# Start PostgreSQL
docker compose up -d

# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run

# Test
mvn test
```

## Configuration

| Property | Value |
|---|---|
| Application Port | 8099 |
| Database Port | 5438 |
| Database Name | codeops_courier |
| API Prefix | /api/v1/courier/ |
| Swagger UI | http://localhost:8099/swagger-ui.html |

## Docker Setup

PostgreSQL 16 Alpine runs on port 5438 via Docker Compose. Credentials: `codeops/codeops`.

```bash
docker compose up -d    # Start database
docker compose down     # Stop database
```

## Conventions

See [CONVENTIONS.md](CONVENTIONS.md) for coding standards and workflow rules.
