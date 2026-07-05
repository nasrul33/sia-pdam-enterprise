# Architecture - SIA-PDAM Enterprise

## Style

Modular monolith, DDD-inspired boundaries, REST-first API, PostgreSQL as source of truth.

## Layers

1. Presentation: REST controllers, DTOs, validation, response envelope.
2. Application: use cases, transaction boundary, authorization, audit hook.
3. Domain: entities, value objects, domain services, business invariants.
4. Infrastructure: repositories, Flyway, Redis, MinIO, integration adapters.
5. Database: PostgreSQL constraints, indexes, triggers for critical invariants.

## Key Decisions

- Keep financial workflows inside one backend for transaction safety.
- Use Flyway for controlled migrations.
- Use BigDecimal/Money for all monetary values.
- Use database uniqueness for idempotency.
- Use audit trail for sensitive actions.
