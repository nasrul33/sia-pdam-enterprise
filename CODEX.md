# CODEX Execution Contract - SIA-PDAM Enterprise

## Mission

Bangun ulang SIA-PDAM Enterprise di repo baru dengan Java/Spring Boot dan Next.js. Repo lama hanya referensi domain. Jangan mengubah, merge, atau copy-paste membabi buta dari repo lama.


## Platform Baseline

- Java: 26.
- Spring Boot: 4.1.0.
- Gradle: 9.6.1.
- Database: PostgreSQL 16+.
- Runtime containers: Java 26 images.

Do not downgrade Java or Spring Boot unless explicitly approved for enterprise LTS reasons.

## Non-Negotiable Financial Rules

1. Never use `float` or `double` for money.
2. Use `BigDecimal` through `Money` for all monetary values.
3. Posted journals are immutable.
4. Corrections use reversal or adjustment journals.
5. Debit must equal credit before posting.
6. Period lock blocks posting and mutation in locked periods.
7. Payment idempotency must be enforced using database unique constraints.
8. Reports must be based on posted ledger data.
9. Sensitive actions must be audit logged.
10. No hardcoded secrets.

## Engineering Rules

1. Controllers stay thin.
2. Business logic lives in application/domain services.
3. DTOs define API boundaries.
4. Use database constraints for critical invariants.
5. Use transactions for multi-table financial writes.
6. Add tests before claiming completion.
7. Update context pack after meaningful decisions or deviations.
8. Do not commit if quality gates fail.

## Required Task Closure Format

```txt
Task ID:
Agent:
Scope:
Files changed:
Database migrations:
Tests added/updated:
Commands run:
Result:
Known risks:
Manual verification needed:
Context pack updates:
Next recommended task:
```

## Stop Conditions

Stop and report if:

1. a migration is destructive;
2. financial logic cannot be proven balanced;
3. posted data would need direct mutation;
4. period lock can be bypassed;
5. payment duplicate can create double settlement;
6. mutation endpoints lack server-side authorization;
7. a test/build failure requires human decision;
8. requirements conflict with this contract.
