# Blueprint Gap Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menutup seluruh gap blueprint melalui permission parity, kontrol pre-close, billing lengkap, administrasi user/role, Keycloak OIDC produksi, UX entity selector, dan release hardening.

**Architecture:** Setiap task adalah vertical slice modular monolith yang mengubah migration, backend, frontend, test, dan dokumentasi secara atomik. Basic Auth dipertahankan hanya pada profil local/test; produksi menggunakan Keycloak OIDC melalui Spring Resource Server dan Next.js BFF agar access token tidak disimpan di browser storage.

**Tech Stack:** Java 26, Spring Boot 4.1.0, Spring Security, Spring Data JPA/JdbcTemplate, Flyway, PostgreSQL 16+, Next.js 16.2.10, React 19.2.7, TypeScript 6 strict, TanStack Query 5, Zod 4, Docker Compose, Keycloak, Testcontainers.

## Global Constraints

- Tidak ada migration destruktif, rename, drop, atau rewrite data lama.
- Semua nilai uang memakai `BigDecimal`/`Money`, scale 2, dan `RoundingMode.HALF_UP`.
- Jurnal terposting immutable; koreksi memakai reversal.
- Debit harus sama dengan kredit sebelum posting.
- Permission wajib ditegakkan di backend; permission UI hanya pelengkap.
- Basic Auth hanya aktif pada profil `local` dan `test`.
- Access token OIDC tidak disimpan di `localStorage` atau `sessionStorage`.
- Semua mutasi sensitif mencatat actor, reason, timestamp, dan audit event.
- API lama tetap kompatibel: response hanya ditambah field dan request baru bersifat additive.

---

### Task 1: Permission Parity untuk Domain Operasional

**Files:**
- Create: `backend/src/main/resources/db/migration/V25__operational_permission_parity.sql`
- Modify: `backend/src/main/java/id/pdam/sia/shared/security/Permissions.java`
- Modify: `backend/src/main/java/id/pdam/sia/customer/web/CustomerController.java`
- Modify: `backend/src/main/java/id/pdam/sia/connection/web/ConnectionController.java`
- Modify: `backend/src/main/java/id/pdam/sia/metering/web/MeteringController.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/web/TariffController.java`
- Modify: `backend/src/main/java/id/pdam/sia/receivable/web/ReceivableAgingController.java`
- Create: `backend/src/test/java/id/pdam/sia/shared/security/OperationalPermissionSeedMigrationTest.java`
- Create: `backend/src/test/java/id/pdam/sia/shared/security/OperationalControllerPermissionTest.java`
- Modify: `docs/04-API-CONTRACT.md`

**Interfaces:**
- Produces: constants `CUSTOMER_READ`, `CUSTOMER_MANAGE`, `CONNECTION_READ`, `CONNECTION_MANAGE`, `METER_ROUTE_READ`, `METER_ROUTE_MANAGE`, `METER_READING_READ`, `METER_READING_CREATE`, `METER_READING_VERIFY`, `METER_READING_LOCK`, `TARIFF_READ`, `TARIFF_MANAGE`, `TARIFF_CALCULATE`, `RECEIVABLE_AGING_READ`, `RECEIVABLE_AGING_GENERATE`.
- Consumes: existing Spring method security and RBAC tables `roles`, `permissions`, `role_permissions`.

- [ ] **Step 1: Write failing permission and migration tests**

```java
@Test
void tariffMutationRequiresTariffManage() throws Exception {
    mockMvc.perform(post("/api/tariff-versions")
            .with(user("operator").authorities(new SimpleGrantedAuthority("tariff.read")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"tariffGroupId":"11111111-1111-4111-8111-111111111111","effectiveDate":"2026-08-01","reason":"Uji"}"""))
        .andExpect(status().isForbidden());
}

@Test
void migrationSeedsAllOperationalPermissions() {
    assertThat(sql).contains("customer.read", "meter-reading.lock", "receivable-aging.generate");
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run: `gradle test --tests '*OperationalPermissionSeedMigrationTest' --tests '*OperationalControllerPermissionTest'`

Expected: FAIL because `V25` and permission constants do not exist.

- [ ] **Step 3: Add idempotent V25 seed and least-privilege grants**

```sql
INSERT INTO permissions (id, code, name)
VALUES
  ('00000000-0000-0000-0000-000000002501', 'customer.read', 'Read customers'),
  ('00000000-0000-0000-0000-000000002502', 'customer.manage', 'Manage customers'),
  ('00000000-0000-0000-0000-000000002503', 'connection.read', 'Read connections'),
  ('00000000-0000-0000-0000-000000002504', 'connection.manage', 'Manage connections'),
  ('00000000-0000-0000-0000-000000002505', 'meter-route.read', 'Read meter routes'),
  ('00000000-0000-0000-0000-000000002506', 'meter-route.manage', 'Manage meter routes'),
  ('00000000-0000-0000-0000-000000002507', 'meter-reading.read', 'Read meter readings'),
  ('00000000-0000-0000-0000-000000002508', 'meter-reading.create', 'Create and import meter readings'),
  ('00000000-0000-0000-0000-000000002509', 'meter-reading.verify', 'Submit, verify, and reject meter readings'),
  ('00000000-0000-0000-0000-000000002510', 'meter-reading.lock', 'Lock verified meter readings'),
  ('00000000-0000-0000-0000-000000002511', 'tariff.read', 'Read tariffs'),
  ('00000000-0000-0000-0000-000000002512', 'tariff.manage', 'Manage tariffs'),
  ('00000000-0000-0000-0000-000000002513', 'tariff.calculate', 'Calculate tariffs'),
  ('00000000-0000-0000-0000-000000002514', 'receivable-aging.read', 'Read receivable aging'),
  ('00000000-0000-0000-0000-000000002515', 'receivable-aging.generate', 'Generate receivable aging')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, updated_at = now();
```

Grant every permission to `super-admin`, read permissions to auditors/manajemen, and operational write permissions only to matching officer/supervisor roles.

- [ ] **Step 4: Replace every domain `isAuthenticated()` guard**

```java
@PostMapping("/meter-readings/{readingId}/lock")
@PreAuthorize(Permissions.METER_READING_LOCK)
public MeterReadingResponse lockReading(
        @PathVariable UUID readingId,
        @Valid @RequestBody MeteringWorkflowRequest request,
        Principal principal
) {
    return MeterReadingResponse.from(
            meteringApplicationService.lockReading(readingId, request.reason(), actor(principal))
    );
}
```

Apply read permissions to list/detail endpoints, create permission to create/import, verify permission to submit/verify/reject, manage permission to tariff/customer/connection mutations, and calculate permission to tariff calculation.

- [ ] **Step 5: Run permission tests and full backend tests**

Run: `gradle test --tests '*OperationalPermission*' --tests '*ControllerPermissionTest'`

Expected: PASS.

Run: `gradle test`

Expected: PASS.

- [ ] **Step 6: Commit permission parity**

```bash
git add backend/src/main docs/04-API-CONTRACT.md
git commit -m "feat(security): enforce operational permission parity"
```

### Task 2: Accounting Pre-Close Checklist

**Files:**
- Create: `backend/src/main/java/id/pdam/sia/accounting/application/PreCloseChecklistService.java`
- Create: `backend/src/main/java/id/pdam/sia/accounting/application/PreCloseChecklist.java`
- Create: `backend/src/main/java/id/pdam/sia/accounting/application/PreCloseBlocker.java`
- Create: `backend/src/main/java/id/pdam/sia/accounting/application/PreCloseSeverity.java`
- Create: `backend/src/main/java/id/pdam/sia/accounting/web/PreCloseChecklistResponse.java`
- Create: `backend/src/main/java/id/pdam/sia/accounting/web/PreCloseBlockerResponse.java`
- Modify: `backend/src/main/java/id/pdam/sia/accounting/application/AccountingApplicationService.java`
- Modify: `backend/src/main/java/id/pdam/sia/accounting/web/AccountingController.java`
- Modify: `backend/src/main/java/id/pdam/sia/accounting/repository/JournalEntryRepository.java`
- Modify: `backend/src/main/java/id/pdam/sia/accounting/repository/FixedAssetRepository.java`
- Modify: `backend/src/main/java/id/pdam/sia/accounting/repository/FixedAssetDepreciationRepository.java`
- Modify: `backend/src/main/java/id/pdam/sia/payment/repository/PaymentReconciliationSessionRepository.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/repository/BillingBatchRepository.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/repository/InvoiceRepository.java`
- Create: `backend/src/test/java/id/pdam/sia/accounting/application/PreCloseChecklistServiceTest.java`
- Modify: `backend/src/test/java/id/pdam/sia/accounting/application/AccountingApplicationServiceTest.java`
- Modify: `backend/src/test/java/id/pdam/sia/accounting/web/AccountingControllerPermissionTest.java`

**Interfaces:**
- Produces: `PreCloseChecklist evaluate(AccountingPeriod period)` and `void requireClear(AccountingPeriod period)`.
- Produces: `GET /api/accounting-periods/{periodId}/pre-close-checklist` guarded by `period.manage`.
- Consumes: accounting period UUID/value and repository count queries; no direct writes.

- [ ] **Step 1: Write failing checklist tests**

```java
@Test
void startClosingReviewRejectsDraftJournalBlocker() {
    when(journalEntryRepository.countByAccountingPeriodIdAndStatus(periodId, JournalStatus.DRAFT)).thenReturn(2L);
    assertThatThrownBy(() -> service.startClosingReview(periodId, "Tutup bulan", "admin"))
        .isInstanceOf(BusinessException.class)
        .extracting("code").isEqualTo("PERIOD_PRE_CLOSE_BLOCKED");
}

@Test
void checklistReturnsStableBlockerCodes() {
    PreCloseChecklist result = checklistService.evaluate(period);
    assertThat(result.blockers()).extracting(PreCloseBlocker::code)
        .contains("DRAFT_JOURNALS");
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `gradle test --tests '*PreCloseChecklistServiceTest' --tests '*AccountingApplicationServiceTest'`

Expected: FAIL because checklist types and enforcement do not exist.

- [ ] **Step 3: Add repository count queries and checklist records**

```java
public record PreCloseBlocker(String code, String message, long count, PreCloseSeverity severity, String actionPath) {}

public record PreCloseChecklist(UUID periodId, String period, boolean closable, List<PreCloseBlocker> blockers) {
    public PreCloseChecklist {
        blockers = List.copyOf(blockers);
        closable = blockers.isEmpty();
    }
}
```

Queries must count draft journals, active assets without depreciation for the period, reconciliation sessions that are not completed/signed-off, unfinished billing batches, and draft invoices. Allowance is a blocker only when an aging snapshot exists and no allowance journal exists for that snapshot.

- [ ] **Step 4: Enforce checklist atomically and expose preview endpoint**

```java
@Transactional
public AccountingPeriod startClosingReview(UUID periodId, String reason, String actor) {
    AccountingPeriod period = findPeriod(periodId);
    preCloseChecklistService.requireClear(period);
    period.startClosingReview();
    auditTrailService.record(actor, "ACCOUNTING", "START_PERIOD_CLOSING_REVIEW", period.getId().toString(), reason);
    return period;
}
```

- [ ] **Step 5: Verify focused and full backend tests**

Run: `gradle test --tests '*PreClose*' --tests '*Accounting*'`

Expected: PASS.

Run: `gradle test`

Expected: PASS.

- [ ] **Step 6: Commit pre-close controls**

```bash
git add backend/src/main backend/src/test
git commit -m "feat(accounting): enforce pre-close checklist"
```

### Task 3: Tariff Non-Air Components, Penalty, and Balanced Posting

**Files:**
- Create: `backend/src/main/resources/db/migration/V26__tariff_non_air_components.sql`
- Modify: `backend/src/main/java/id/pdam/sia/billing/domain/TariffVersion.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/domain/Invoice.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/domain/InvoiceLineType.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/application/TariffCalculationResult.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/application/TariffEngineApplicationService.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/application/BillingBatchApplicationService.java`
- Modify: `backend/src/main/java/id/pdam/sia/accounting/application/BillingInvoicePostingCommand.java`
- Modify: `backend/src/main/java/id/pdam/sia/accounting/application/AccountingApplicationService.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/web/CreateTariffVersionRequest.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/web/TariffVersionResponse.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/web/CalculateTariffRequest.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/web/TariffCalculationResponse.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/web/IssueInvoiceRequest.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/web/InvoiceResponse.java`
- Modify: `backend/src/main/java/id/pdam/sia/billing/application/InvoiceDocumentApplicationService.java`
- Modify: `backend/src/test/java/id/pdam/sia/billing/application/TariffEngineApplicationServiceTest.java`
- Modify: `backend/src/test/java/id/pdam/sia/billing/application/BillingBatchApplicationServiceTest.java`
- Modify: `backend/src/test/java/id/pdam/sia/accounting/application/AccountingApplicationServiceTest.java`
- Create: `backend/src/test/java/id/pdam/sia/shared/security/TariffComponentMigrationTest.java`
- Modify: `frontend/src/features/billing/billing-schema.ts`
- Modify: `frontend/src/features/billing/billing-workspace.tsx`
- Modify: `frontend/src/features/billing/billing-workspace-model.ts`
- Modify: `frontend/src/features/billing/billing-workspace-model.test.ts`

**Interfaces:**
- Produces: tariff fields `fixedCharge`, `levyCharge`, `adminCharge`, `wasteCharge`, `penaltyRate`.
- Produces: `TariffCalculationResult` fields `usageCharge`, four fixed/non-air charges, `penaltyCharge`, `subtotal`, `total`.
- Consumes: `outstandingAmount` in `CalculateTariffRequest`; penalty formula is `outstandingAmount * penaltyRate`, where rate is decimal fraction `0..1`.
- Produces: invoice component snapshots and per-component `InvoiceLine` rows.

- [ ] **Step 1: Write failing tariff, invoice, migration, and posting tests**

```java
@Test
void calculationIncludesAllChargesAndPenaltyOnPriorOutstanding() {
    TariffCalculationResult result = service.calculate(requestWithUsageAndOutstanding("15", "30000"));
    assertThat(result.usageCharge().amount()).isEqualByComparingTo("20000.00");
    assertThat(result.fixedCharge().amount()).isEqualByComparingTo("5000.00");
    assertThat(result.levyCharge().amount()).isEqualByComparingTo("2000.00");
    assertThat(result.adminCharge().amount()).isEqualByComparingTo("2500.00");
    assertThat(result.wasteCharge().amount()).isEqualByComparingTo("3000.00");
    assertThat(result.penaltyCharge().amount()).isEqualByComparingTo("1500.00");
    assertThat(result.total().amount()).isEqualByComparingTo("34000.00");
}
```

Also assert that journal debit equals the sum of water, fixed, levy, admin, waste, and penalty credits, and that void creates exact opposite lines.

- [ ] **Step 2: Run tests and verify RED**

Run: `gradle test --tests '*TariffEngineApplicationServiceTest' --tests '*BillingBatchApplicationServiceTest' --tests '*TariffComponentMigrationTest'`

Expected: FAIL because component fields and columns do not exist.

- [ ] **Step 3: Add V26 additive columns and constraints**

```sql
ALTER TABLE tariff_versions
  ADD COLUMN fixed_charge numeric(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN levy_charge numeric(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN admin_charge numeric(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN waste_charge numeric(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN penalty_rate numeric(9,6) NOT NULL DEFAULT 0;

ALTER TABLE tariff_versions
  ADD CONSTRAINT ck_tariff_versions_non_negative_components
    CHECK (fixed_charge >= 0 AND levy_charge >= 0 AND admin_charge >= 0 AND waste_charge >= 0),
  ADD CONSTRAINT ck_tariff_versions_penalty_rate CHECK (penalty_rate >= 0 AND penalty_rate <= 1);
```

Add non-null default-zero snapshot columns to `invoices` and extend the existing line type constraint for `FIXED`, `LEVY`, `ADMIN`, `WASTE`, and `PENALTY`.

- [ ] **Step 4: Implement exact calculation and snapshot creation**

```java
Money penalty = outstanding.multiply(version.getPenaltyRate());
Money subtotal = usage.add(fixed).add(levy).add(admin).add(waste);
Money total = subtotal.add(penalty);
return new TariffCalculationResult(
        version.getId(),
        version.getTariffGroupId(),
        version.getEffectiveDate(),
        request.billingDate(),
        request.usageM3(),
        lines,
        usage,
        fixed,
        levy,
        admin,
        waste,
        penalty,
        subtotal,
        total
);
```

Create one invoice line per non-zero component and preserve water block lines. `outstandingAmount` starts from total, not subtotal.

- [ ] **Step 5: Split posting by component and preserve reversal symmetry**

Extend `IssueInvoiceRequest` additively with `nonAirRevenueAccountId` and `penaltyRevenueAccountId`. Require those IDs only when their component total is positive. Keep existing `revenueAccountId` as the water/fixed revenue account for backward compatibility.

- [ ] **Step 6: Update billing UI and run all gates**

Run: `gradle test`

Expected: PASS.

Run: `npm run test:permissions && npm run typecheck && npm run lint && npm run build`

Expected: PASS.

- [ ] **Step 7: Commit tariff parity**

```bash
git add backend frontend docs/04-API-CONTRACT.md
git commit -m "feat(billing): add non-air tariff and penalty controls"
```

### Task 4: User and Role Administration

**Files:**
- Create: `backend/src/main/resources/db/migration/V27__user_role_admin_permissions.sql`
- Create: `backend/src/main/java/id/pdam/sia/admin/application/UserAdministrationService.java`
- Create: `backend/src/main/java/id/pdam/sia/admin/application/IdentityProviderAdminPort.java`
- Create: `backend/src/main/java/id/pdam/sia/admin/application/DisabledIdentityProviderAdminAdapter.java`
- Create: `backend/src/main/java/id/pdam/sia/admin/web/UserAdminController.java`
- Create: `backend/src/main/java/id/pdam/sia/admin/web/UserAdminResponse.java`
- Create: `backend/src/main/java/id/pdam/sia/admin/web/RoleResponse.java`
- Create: `backend/src/main/java/id/pdam/sia/admin/web/UpdateUserStatusRequest.java`
- Create: `backend/src/main/java/id/pdam/sia/admin/web/UpdateUserRolesRequest.java`
- Modify: `backend/src/main/java/id/pdam/sia/shared/security/Permissions.java`
- Create: `backend/src/test/java/id/pdam/sia/admin/application/UserAdministrationServiceTest.java`
- Create: `backend/src/test/java/id/pdam/sia/admin/web/UserAdminControllerPermissionTest.java`
- Create: `frontend/src/app/admin/users/page.tsx`
- Create: `frontend/src/features/admin-users/admin-user-api.ts`
- Create: `frontend/src/features/admin-users/admin-user-schema.ts`
- Create: `frontend/src/features/admin-users/use-admin-users.ts`
- Create: `frontend/src/features/admin-users/admin-user-workspace.tsx`
- Create: `frontend/src/features/admin-users/admin-user-permissions.ts`
- Create: `frontend/src/features/admin-users/admin-user-permissions.test.ts`
- Modify: `frontend/src/components/layout/app-shell.tsx`
- Modify: `frontend/package.json`

**Interfaces:**
- Produces: `GET /api/admin/users`, `GET /api/admin/roles`, `PATCH /api/admin/users/{id}/status`, `PUT /api/admin/users/{id}/roles`.
- Produces: `UserAdminResponse(id, username, email, enabled, roles, authorities, identityProviderStatus, updatedAt)`.
- Consumes: current principal name and mandatory mutation `reason`.

- [ ] **Step 1: Write failing safety and controller tests**

```java
@Test
void userCannotDisableOwnAccount() {
    assertThatThrownBy(() -> service.updateStatus(adminId, false, "admin", "self disable"))
        .isInstanceOf(BusinessException.class)
        .extracting("code").isEqualTo("USER_SELF_DISABLE_FORBIDDEN");
}

@Test
void lastSuperAdminRoleCannotBeRemoved() {
    assertThatThrownBy(() -> service.replaceRoles(lastAdminId, Set.of("finance-staff"), "other", "change"))
        .isInstanceOf(BusinessException.class)
        .extracting("code").isEqualTo("LAST_SUPER_ADMIN_REQUIRED");
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `gradle test --tests '*UserAdministration*' --tests '*UserAdminControllerPermissionTest'`

Expected: FAIL because admin service/API do not exist.

- [ ] **Step 3: Seed permissions and implement transactional JdbcTemplate service**

Use `user.read`, `user.manage`, and `role.manage`; lock the target user and every current super-admin assignment before mutation:

```sql
SELECT ur.user_id
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
WHERE r.code = 'super-admin'
FOR UPDATE;
```

Count the locked rows in Java, replace role assignments in one transaction, and write one audit event containing before/after role codes.

- [ ] **Step 4: Build `/admin/users` with complete states**

```ts
export const adminUserSchema = z.object({
  id: z.string().uuid(),
  username: z.string().min(1),
  email: z.string().email(),
  enabled: z.boolean(),
  roles: z.array(z.string()),
  authorities: z.array(z.string()),
  identityProviderStatus: z.enum(["LOCAL_ONLY", "SYNCED", "SYNC_ERROR"]),
  updatedAt: z.string().datetime()
});
```

Render loading skeleton, API error retry, empty result, searchable table, status badge, role assignment modal, reason input, and permission-disabled actions.

- [ ] **Step 5: Run backend and frontend gates**

Run: `gradle test`

Expected: PASS.

Run: `npm run test:permissions && npm run typecheck && npm run lint && npm run build`

Expected: PASS and `/admin/users` appears in generated route output.

- [ ] **Step 6: Commit administration surface**

```bash
git add backend frontend docs/04-API-CONTRACT.md
git commit -m "feat(admin): add guarded user and role management"
```

### Task 5: Keycloak OIDC Production Profile and Secret Hardening

**Files:**
- Create: `backend/src/main/java/id/pdam/sia/shared/security/LocalSecurityConfiguration.java`
- Create: `backend/src/main/java/id/pdam/sia/shared/security/OidcSecurityConfiguration.java`
- Create: `backend/src/main/java/id/pdam/sia/shared/security/KeycloakAuthorityConverter.java`
- Create: `backend/src/main/java/id/pdam/sia/shared/security/ProductionSecurityProperties.java`
- Create: `backend/src/main/java/id/pdam/sia/shared/security/ProductionSecurityValidator.java`
- Modify: `backend/src/main/java/id/pdam/sia/shared/security/SecurityConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-local.yml`
- Create: `backend/src/main/resources/application-prod.yml`
- Modify: `backend/build.gradle.kts`
- Create: `backend/src/test/java/id/pdam/sia/shared/security/KeycloakAuthorityConverterTest.java`
- Create: `backend/src/test/java/id/pdam/sia/shared/security/ProductionSecurityValidatorTest.java`
- Modify: `backend/src/test/java/id/pdam/sia/shared/security/SecurityConfigTest.java`
- Create: `frontend/src/auth.ts`
- Create: `frontend/src/app/api/auth/[...nextauth]/route.ts`
- Create: `frontend/src/app/api/backend/[...path]/route.ts`
- Modify: `frontend/src/lib/api/client.ts`
- Modify: `frontend/src/app/providers.tsx`
- Modify: `frontend/package.json`
- Modify: `.env.example`
- Modify: `docker-compose.yml`

**Interfaces:**
- Produces: JWT authorities from `realm_access.roles`, `resource_access[sia-pdam].roles`, and optional `permissions` claim.
- Produces: local profile HTTP Basic; prod profile OAuth2 Resource Server only.
- Produces: Next.js same-origin BFF `/api/backend/*` that injects bearer token from server session.
- Consumes: `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`, `AUTH_SECRET`, `SIA_PAYMENT_WEBHOOK_SECRET`.

- [ ] **Step 1: Verify current official dependency versions before editing manifests**

Run: inspect Spring Boot dependency management and npm registry metadata for `next-auth`; pin exact compatible versions in the lockfile and document the resolved versions in `docs/00-CONTEXT-PACK.md`.

Expected: no dependency is added with an unbounded tag.

- [ ] **Step 2: Write failing profile, authority, and secret tests**

```java
@Test
void productionRejectsDefaultWebhookSecret() {
    ProductionSecurityProperties properties = new ProductionSecurityProperties(true, "dev-only-change-me", "issuer", "sia-pdam");
    assertThatThrownBy(() -> validator.validate(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("webhook secret");
}

@Test
void keycloakRolesBecomeApplicationAuthorities() {
    Collection<GrantedAuthority> authorities = converter.convert(jwtWithRealmAndClientRoles());
    assertThat(authorities).extracting(GrantedAuthority::getAuthority)
        .contains("journal.post", "ROLE_FINANCE_SUPERVISOR");
}
```

- [ ] **Step 3: Run tests and verify RED**

Run: `gradle test --tests '*KeycloakAuthorityConverterTest' --tests '*ProductionSecurityValidatorTest' --tests '*SecurityConfigTest'`

Expected: FAIL because OIDC configuration and validator do not exist.

- [ ] **Step 4: Split security by profile and implement fail-fast validation**

```java
@Configuration
@Profile("prod")
class OidcSecurityConfiguration {
    @Bean
    SecurityFilterChain oidcFilterChain(HttpSecurity http, KeycloakAuthorityConverter converter) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(SecurityRoutes::configure)
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
            .build();
    }
}
```

Local/test config owns `httpBasic`; prod config must not call `httpBasic`.

- [ ] **Step 5: Implement Next.js OIDC session and BFF**

The BFF reads the encrypted server session, rejects missing/expired access token with `401`, forwards method/body/query to the backend, strips hop-by-hop headers, and never logs tokens. Local development keeps current direct backend Basic Auth behavior only when `NEXT_PUBLIC_DEV_AUTH_MODE=basic`.

- [ ] **Step 6: Run auth, frontend, and local compose gates**

Run: `gradle test`

Expected: PASS.

Run: `npm ci && npm run test:permissions && npm run typecheck && npm run lint && npm run build`

Expected: PASS.

Run: `docker compose config`

Expected: PASS with local profile not requiring Keycloak.

- [ ] **Step 7: Commit production authentication**

```bash
git add backend frontend docker-compose.yml .env.example docs/00-CONTEXT-PACK.md
git commit -m "feat(auth): add Keycloak OIDC production profile"
```

### Task 6: Reusable Entity Selector and UUID UX Removal

**Files:**
- Create: `frontend/src/components/entity-selector/entity-selector.tsx`
- Create: `frontend/src/components/entity-selector/entity-selector-model.ts`
- Create: `frontend/src/components/entity-selector/entity-selector-model.test.ts`
- Create: `frontend/src/features/lookups/lookup-schema.ts`
- Create: `frontend/src/features/lookups/lookup-api.ts`
- Create: `frontend/src/features/lookups/use-entity-lookup.ts`
- Modify: `frontend/src/features/receivables/collection-actions/collection-action-workspace.tsx`
- Modify: `frontend/src/features/payments/payment-workspace.tsx`
- Modify: `frontend/src/features/operations/operations-workspaces.tsx`
- Modify: `frontend/src/features/accounting/accounting-workspace.tsx`
- Modify: `frontend/src/lib/query/query-keys.ts`
- Modify: `frontend/package.json`
- Modify: backend list controllers/services only where a bounded `search` parameter is absent.

**Interfaces:**
- Produces: `EntitySelector<T extends EntityOption>` with controlled `value`, `onChange`, `query`, `loadOptions`, `disabled`, and `ariaLabel`.
- Produces: `EntityOption { id: string; label: string; description?: string; status?: string }`.
- Consumes: existing paginated list APIs with `search`, `page=0`, `size=20`.

- [ ] **Step 1: Write failing selector model tests**

```ts
test("stale lookup response cannot replace latest query", () => {
  const state = reduceLookup(initialLookupState, { type: "request", requestId: 2, query: "INV-02" });
  const stale = reduceLookup(state, { type: "success", requestId: 1, options: oldOptions });
  assert.deepEqual(stale, state);
});

test("selected option remains visible outside current search page", () => {
  assert.deepEqual(mergeSelectedOption([], selected), [selected]);
});
```

- [ ] **Step 2: Run tests and verify RED**

Run: `npm run test:permissions`

Expected: FAIL until the new model test is included and implementation exists.

- [ ] **Step 3: Implement accessible async selector**

Use a 300 ms debounce, abort previous request, minimum query length 2, keyboard Up/Down/Enter/Escape, `role="combobox"`, `aria-expanded`, `aria-controls`, and a fixed-height result panel so loading/error/empty states do not shift surrounding forms.

- [ ] **Step 4: Replace raw UUID inputs**

Replace customer/invoice filters and creation fields, payment allocation invoice IDs, payment reversal payment ID, meter route/connection fields, and journal account fields. Preserve UUID validation in form models because the backend contract remains UUID-based.

- [ ] **Step 5: Verify no operator-facing UUID placeholders remain**

Run: `rg -n 'placeholder=.*(UUID|ID pelanggan|ID invoice|ID payment|Route ID)' frontend/src/features`

Expected: no matches in interactive workflow components.

Run: `npm run test:permissions && npm run typecheck && npm run lint && npm run build`

Expected: PASS.

- [ ] **Step 6: Commit UX selector migration**

```bash
git add frontend backend/src/main docs/05-UI-UX-PLAN.md
git commit -m "feat(frontend): replace raw identifiers with entity selectors"
```

### Task 7: Seeded API Integration Profile, Keycloak Smoke, and Production Runbook

**Files:**
- Create: `backend/src/test/java/id/pdam/sia/integration/AbstractPostgresIntegrationTest.java`
- Create: `backend/src/test/java/id/pdam/sia/integration/BillingPaymentAccountingFlowIT.java`
- Create: `backend/src/test/java/id/pdam/sia/integration/PeriodCloseFlowIT.java`
- Create: `backend/src/test/resources/application-integration.yml`
- Create: `backend/src/test/resources/db/integration-seed.sql`
- Create: `infra/keycloak/realm-sia-pdam-test.json`
- Create: `infra/DEPLOYMENT.md`
- Create: `infra/BACKUP-RESTORE.md`
- Create: `infra/ROLLBACK.md`
- Create: `infra/OBSERVABILITY.md`
- Modify: `infra/README.md`
- Create: `scripts/smoke-oidc.sh`
- Modify: `scripts/smoke-compose.sh`
- Modify: `.github/workflows/ci.yml`
- Modify: `docs/00-CONTEXT-PACK.md`
- Modify: `docs/11-QUALITY-GATES.md`
- Modify: `docs/12-BLUEPRINT-MAPPING.md`

**Interfaces:**
- Produces: Gradle integration test task matching `*IT` and using PostgreSQL Testcontainers.
- Produces: deterministic realm with test users/roles and no production credential.
- Produces: runbooks with exact deploy, backup, restore verification, rollback, health, metrics, logs, and alert commands.

- [ ] **Step 1: Add failing high-risk integration tests**

```java
@Test
void issuedInvoiceCanBeSettledReversedAndVoidedWithoutLedgerImbalance() {
    UUID invoiceId = issueSeededInvoice();
    UUID paymentId = settleInvoice(invoiceId);
    reversePayment(paymentId);
    voidInvoice(invoiceId);
    assertThat(postedJournalImbalance()).isEqualByComparingTo("0.00");
}

@Test
void periodClosingIsBlockedUntilDraftsDepreciationAndReconciliationAreCleared() {
    assertThat(startClosingReview()).hasStatus(422).bodyCode("PERIOD_PRE_CLOSE_BLOCKED");
    clearSeededBlockers();
    assertThat(startClosingReview()).hasStatus(200).bodyStatus("CLOSING_REVIEW");
}
```

- [ ] **Step 2: Run integration tests and verify RED**

Run: `gradle integrationTest`

Expected: FAIL until the source set/profile/seed is configured.

- [ ] **Step 3: Configure deterministic Testcontainers profile and seed**

Use one PostgreSQL 16+ container per test class, Flyway migrations, and SQL seed with fixed UUIDs. Tests call MockMvc/HTTP contracts instead of invoking services directly.

- [ ] **Step 4: Add Keycloak smoke and CI gates**

`scripts/smoke-oidc.sh` must obtain a test token, call `/api/auth/me`, verify a permitted endpoint returns `200`, verify a missing authority returns `403`, and verify logout/session invalidation through the frontend auth route. CI runs backend unit tests, integration tests, frontend gates, compose smoke, then OIDC smoke.

- [ ] **Step 5: Write production runbooks with executable verification**

Deployment must include preflight migration check, immutable image tag, health wait, smoke, and rollback trigger. Backup/restore must verify row counts and Flyway history in a disposable database. Observability must define structured fields and alert thresholds for auth failure, webhook signature failure, billing failure, unbalanced journal rejection, and pre-close blockers.

- [ ] **Step 6: Run full release gates**

Run: `gradle clean test integrationTest bootJar`

Expected: PASS.

Run: `npm ci && npm run test:permissions && npm run typecheck && npm run lint && npm run build`

Expected: PASS.

Run: `docker compose config && sh scripts/smoke-compose.sh && sh scripts/smoke-oidc.sh`

Expected: PASS; Flyway V25-V27 successful and all baseline routes return expected status.

- [ ] **Step 7: Commit release hardening**

```bash
git add backend infra scripts .github docs
git commit -m "test(release): add seeded integration and production runbooks"
```

### Task 8: Final Blueprint Audit, Documentation, and Push

**Files:**
- Modify: `docs/00-CONTEXT-PACK.md`
- Modify: `docs/04-API-CONTRACT.md`
- Modify: `docs/05-UI-UX-PLAN.md`
- Modify: `docs/10-ROADMAP-BACKLOG.md`
- Modify: `docs/11-QUALITY-GATES.md`
- Modify: `docs/12-BLUEPRINT-MAPPING.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: all implementation and gate results from Tasks 1-7.
- Produces: zero-open-gap audit for the approved specification and reproducible local/production startup instructions.

- [ ] **Step 1: Run static gap scans**

Run: `rg -n 'isAuthenticated\(\)' backend/src/main/java/id/pdam/sia`

Expected: no domain command/list endpoint that has a granular permission contract.

Run: `rg -n 'dev-only-change-me|NEXT_PUBLIC_DEV_BASIC_AUTH_(USERNAME|PASSWORD)' backend/src/main/resources/application-prod.yml frontend/src`

Expected: no production/default secret exposure.

Run: `rg -n 'placeholder=.*UUID' frontend/src`

Expected: no operator-facing raw UUID fields.

- [ ] **Step 2: Run the complete gate one final time from a clean state**

Run: `gradle clean test integrationTest bootJar`

Expected: PASS.

Run: `npm ci && npm run test:permissions && npm run typecheck && npm run lint && npm run build`

Expected: PASS.

Run: `docker compose down -v && sh scripts/smoke-compose.sh && sh scripts/smoke-oidc.sh`

Expected: PASS from a fresh database and fresh containers.

- [ ] **Step 3: Re-audit blueprint and update traceability**

Record each prior gap as `Closed`, cite migration/API/UI/test evidence, include exact gate date and commands, and leave no placeholder or ambiguous production-auth note.

- [ ] **Step 4: Verify Git scope and commit docs**

Run: `git status --short && git diff --check && git diff --stat origin/main...HEAD`

Expected: only approved gap-closure files, no whitespace errors, no secrets, no generated build output.

```bash
git add README.md docs
git commit -m "docs: close blueprint gap traceability"
```

- [ ] **Step 5: Push only after every gate passes**

Run: `git push origin main`

Expected: remote `main` advances to the final audited commit with no rejected updates.
