# Customer, Connection, and Tariff Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Memisahkan halaman pelanggan, sambungan, dan tarif menjadi workspace domain tunggal serta membersihkan inkonsistensi route dan tabel frontend tanpa mengubah kontrak backend.

**Architecture:** Pertahankan hooks API dan komponen presentasional yang sudah ada di modul operasi, tetapi pecah `MasterDataWorkspace` menjadi `CustomerWorkspace` dan `ConnectionWorkspace` dengan state/query masing-masing. Pindahkan mutation golongan tarif ke `TariffWorkspace`; route App Router mengimpor workspace domain yang tepat secara eksplisit.

**Tech Stack:** Next.js 16.2.10 App Router, React 19.2.7, TypeScript 6.0.3 strict, Tailwind CSS 4.3.2, TanStack Query 5.101.2, Node test runner.

## Global Constraints

- Tidak mengubah endpoint Spring Boot, schema database, permission, atau aturan workflow.
- Backend authorization tetap menjadi sumber kebenaran untuk semua mutation.
- Bahasa tampilan diprioritaskan Bahasa Indonesia.
- Semua workspace mempertahankan loading, error, empty, unauthenticated, pending, dan mutation error state.
- Mode terang/gelap dan layout responsif tidak boleh mengalami regresi.
- Jangan menyentuh `.superpowers/` atau membatalkan perubahan mode gelap yang belum di-commit.

---

## File Structure

- Modify `frontend/src/app/customers/page.tsx`: render `CustomerWorkspace` saja.
- Modify `frontend/src/app/connections/page.tsx`: render `ConnectionWorkspace` saja.
- Modify `frontend/src/features/operations/operations-workspaces.tsx`: pisahkan state/query/JSX pelanggan dan sambungan; pindahkan pengelolaan golongan tarif ke tarif; koreksi tabel.
- Create `frontend/src/features/operations/operations-route-boundaries.test.ts`: regression contract untuk pemetaan route dan larangan workspace gabungan.
- Modify `frontend/package.json`: masukkan test boundary ke `test:permissions`.
- Modify `docs/00-CONTEXT-PACK.md`: catat keputusan, requirement, dan hasil verifikasi.
- Modify `docs/05-UI-UX-PLAN.md`: dokumentasikan ownership workspace operasi.

### Task 1: Route Boundary Regression Contract

**Files:**
- Create: `frontend/src/features/operations/operations-route-boundaries.test.ts`
- Modify: `frontend/package.json`

**Interfaces:**
- Consumes: route files `src/app/customers/page.tsx` dan `src/app/connections/page.tsx`.
- Produces: regression gate yang mengharuskan `CustomerWorkspace`, `ConnectionWorkspace`, dan melarang `MasterDataWorkspace`.

- [ ] **Step 1: Write the failing route contract test**

```ts
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

function readRoute(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

test("customer and connection routes use separate domain workspaces", () => {
  const customerRoute = readRoute("../../app/customers/page.tsx");
  const connectionRoute = readRoute("../../app/connections/page.tsx");

  assert.match(customerRoute, /CustomerWorkspace/);
  assert.doesNotMatch(customerRoute, /ConnectionWorkspace|MasterDataWorkspace/);
  assert.match(connectionRoute, /ConnectionWorkspace/);
  assert.doesNotMatch(connectionRoute, /CustomerWorkspace|MasterDataWorkspace/);
});

test("tariff route remains owned by TariffWorkspace", () => {
  const tariffRoute = readRoute("../../app/tariffs/page.tsx");
  assert.match(tariffRoute, /TariffWorkspace/);
  assert.doesNotMatch(tariffRoute, /CustomerWorkspace|ConnectionWorkspace|MasterDataWorkspace/);
});
```

- [ ] **Step 2: Register the test in the existing frontend gate**

Add this path to `test:permissions` in `frontend/package.json`:

```txt
src/features/operations/operations-route-boundaries.test.ts
```

- [ ] **Step 3: Run the test and verify RED**

Run:

```powershell
npm.cmd run test:permissions
```

Expected: the new customer/connection route test fails because both still use `MasterDataWorkspace`; existing tests remain green.

- [ ] **Step 4: Commit the failing regression contract**

```powershell
git add frontend/package.json frontend/src/features/operations/operations-route-boundaries.test.ts
git commit -m "test(frontend): guard operations route boundaries"
```

### Task 2: Split Customer and Connection Workspaces

**Files:**
- Modify: `frontend/src/features/operations/operations-workspaces.tsx:246-794`
- Modify: `frontend/src/app/customers/page.tsx`
- Modify: `frontend/src/app/connections/page.tsx`

**Interfaces:**
- Consumes: existing `useCustomers`, `useCustomer`, `useCreateCustomer`, `useConnections`, `useConnection`, `useCreateConnection`, `useConnectionWorkflow`, lookup loaders, and shared UI helpers.
- Produces: `export function CustomerWorkspace(): JSX.Element` and `export function ConnectionWorkspace(): JSX.Element`.

- [ ] **Step 1: Replace customer route wiring**

Set `frontend/src/app/customers/page.tsx` to:

```tsx
import { CustomerWorkspace } from "@/features/operations/operations-workspaces";

export default function CustomersPage() {
  return <CustomerWorkspace />;
}
```

- [ ] **Step 2: Replace connection route wiring**

Set `frontend/src/app/connections/page.tsx` to:

```tsx
import { ConnectionWorkspace } from "@/features/operations/operations-workspaces";

export default function ConnectionsPage() {
  return <ConnectionWorkspace />;
}
```

- [ ] **Step 3: Build `CustomerWorkspace` from the existing customer blocks**

The component must own only these values:

```tsx
export function CustomerWorkspace() {
  const currentUserQuery = useCurrentUser();
  const authenticated = currentUserQuery.data?.authenticated ?? false;
  const [filters, setFilters] = useState({ page: 0, size: 10, search: "", status: "" });
  const [selectedCustomerId, setSelectedCustomerId] = useState<string | null>(null);
  const [form, setForm] = useState({
    customerNumber: "",
    fullName: "",
    identityNumber: "",
    phoneNumber: "",
    addressLine: "",
    areaCode: "",
    latitude: "",
    longitude: "",
    reason: ""
  });

  const customersQuery = useCustomers({
    page: filters.page,
    size: filters.size,
    search: filters.search || undefined,
    status: filters.status ? (filters.status as "ACTIVE" | "INACTIVE" | "BLACKLISTED") : undefined
  });
  const customerQuery = useCustomer(selectedCustomerId);
  const createCustomerMutation = useCreateCustomer();
```

Move only these existing sections into its JSX:

```txt
PageHeader: "Master Pelanggan"
AuthNotice
SummaryCard: "Pelanggan"
Section: "Daftar Pelanggan"
Section: "Detail Pelanggan"
Section: "Tambah Pelanggan"
```

The header copy must be:

```tsx
<PageHeader
  title="Master Pelanggan"
  description="Kelola identitas, alamat, status, dan data kontak pelanggan dalam satu workspace."
/>
```

Do not call `useConnections`, `useConnection`, `useTariffGroups`, `useCreateConnection`, `useCreateTariffGroup`, or `useConnectionWorkflow` inside this component.

- [ ] **Step 4: Build `ConnectionWorkspace` from the existing connection blocks**

The component must own only these values:

```tsx
export function ConnectionWorkspace() {
  const currentUserQuery = useCurrentUser();
  const authenticated = currentUserQuery.data?.authenticated ?? false;
  const [filters, setFilters] = useState({ page: 0, size: 10, customerId: "", status: "" });
  const [selectedConnectionId, setSelectedConnectionId] = useState<string | null>(null);
  const [form, setForm] = useState({
    customerId: "",
    tariffGroupId: "",
    connectionNumber: "",
    meterNumber: "",
    installedAt: "",
    reason: ""
  });
  const [workflow, setWorkflow] = useState<{
    connectionId: string;
    workflow: ConnectionWorkflow;
    reason: string;
  }>({ connectionId: "", workflow: "activate", reason: "" });

  const connectionsQuery = useConnections({
    page: filters.page,
    size: filters.size,
    customerId: filters.customerId || undefined,
    status: filters.status ? (filters.status as "DRAFT" | "ACTIVE" | "SUSPENDED" | "TERMINATED") : undefined
  });
  const connectionQuery = useConnection(selectedConnectionId);
  const createConnectionMutation = useCreateConnection();
  const connectionWorkflowMutation = useConnectionWorkflow();
```

Move only these existing sections into its JSX:

```txt
PageHeader: "Master Sambungan"
AuthNotice
SummaryCard: "Sambungan"
Section: "Daftar Sambungan"
Section: "Detail dan Workflow Sambungan"
Section: "Tambah Sambungan"
```

The header copy must be:

```tsx
<PageHeader
  title="Master Sambungan"
  description="Kelola pemasangan meter, golongan tarif, status layanan, dan workflow sambungan pelanggan."
/>
```

Customer and tariff group data are accessed only through `EntitySelector` lookup loaders. Do not render customer or tariff group management forms.

- [ ] **Step 5: Remove `MasterDataWorkspace`**

Delete the old exported function after all customer and connection JSX has moved. Confirm:

```powershell
rg -n "MasterDataWorkspace" frontend/src
```

Expected: no matches.

- [ ] **Step 6: Run the boundary gate and verify GREEN**

```powershell
npm.cmd run test:permissions
```

Expected: all tests pass, including the new route boundary tests.

- [ ] **Step 7: Commit the workspace split**

```powershell
git add frontend/src/app/customers/page.tsx frontend/src/app/connections/page.tsx frontend/src/features/operations/operations-workspaces.tsx
git commit -m "fix(frontend): separate customer and connection workspaces"
```

### Task 3: Move Tariff Group Management to Tariff Workspace

**Files:**
- Modify: `frontend/src/features/operations/operations-workspaces.tsx:1331-1740`

**Interfaces:**
- Consumes: existing `useTariffGroups`, `useCreateTariffGroup`, `CreateTariffGroupPayload` contract, and tariff workspace UI helpers.
- Produces: tariff-owned golongan list summary and create form.

- [ ] **Step 1: Add tariff group form and mutation state**

Inside `TariffWorkspace`, add:

```tsx
const [tariffGroupForm, setTariffGroupForm] = useState({ code: "", name: "", reason: "" });
const createTariffGroupMutation = useCreateTariffGroup();

function submitTariffGroup(event: FormEvent<HTMLFormElement>) {
  event.preventDefault();
  createTariffGroupMutation.mutate(tariffGroupForm, {
    onSuccess: () => setTariffGroupForm({ code: "", name: "", reason: "" })
  });
}
```

- [ ] **Step 2: Add the tariff group summary card**

Change the summary grid to four cards and add:

```tsx
<SummaryCard
  label="Golongan Tarif"
  value={String(tariffGroupsQuery.data?.totalItems ?? 0)}
  helper="Master golongan untuk sambungan dan versi tarif."
  tone="info"
/>
```

- [ ] **Step 3: Add tariff-owned management section**

Add a `Section` before version creation:

```tsx
<Section
  title="Tambah Golongan Tarif"
  description="Golongan menjadi referensi sambungan dan induk versi tarif."
>
  <form className="grid gap-3 md:grid-cols-2" onSubmit={submitTariffGroup}>
    <Field label="Kode">
      <input
        className={inputClass}
        value={tariffGroupForm.code}
        onChange={(event) => setTariffGroupForm((current) => ({ ...current, code: event.target.value }))}
        required
      />
    </Field>
    <Field label="Nama">
      <input
        className={inputClass}
        value={tariffGroupForm.name}
        onChange={(event) => setTariffGroupForm((current) => ({ ...current, name: event.target.value }))}
        required
      />
    </Field>
    <Field label="Alasan audit" className="md:col-span-2">
      <textarea
        className={inputClass}
        value={tariffGroupForm.reason}
        onChange={(event) => setTariffGroupForm((current) => ({ ...current, reason: event.target.value }))}
        required
      />
    </Field>
    <div className="md:col-span-2">
      <MutationError error={createTariffGroupMutation.error} fallback="Gagal membuat golongan tarif." />
      <button
        type="submit"
        className={primaryButtonClass}
        disabled={!authenticated || createTariffGroupMutation.isPending}
      >
        {createTariffGroupMutation.isPending ? "Menyimpan..." : "Simpan Golongan"}
      </button>
    </div>
  </form>
</Section>
```

- [ ] **Step 4: Verify tariff query invalidation contract**

Inspect `useCreateTariffGroup` and retain this invalidation behavior:

```ts
onSuccess: async () => {
  await queryClient.invalidateQueries({ queryKey: ["tariff-groups"] });
}
```

If the existing hook already provides it, do not duplicate invalidation in the component.

- [ ] **Step 5: Run targeted frontend gates**

```powershell
npm.cmd run test:permissions
npm.cmd run typecheck
```

Expected: tests and strict TypeScript pass.

- [ ] **Step 6: Commit tariff ownership correction**

```powershell
git add frontend/src/features/operations/operations-workspaces.tsx
git commit -m "fix(frontend): move tariff groups to tariff workspace"
```

### Task 4: Clean Tables and Audit Other Route Boundaries

**Files:**
- Modify: `frontend/src/features/operations/operations-workspaces.tsx`
- Test: `frontend/src/features/operations/operations-route-boundaries.test.ts`

**Interfaces:**
- Consumes: existing operational tables and App Router route files.
- Produces: aligned tables and a recorded route-ownership audit.

- [ ] **Step 1: Fix the customer table structure**

Remove this orphan header:

```tsx
<th className="px-4 py-3 text-left font-bold text-slate-700">Lock</th>
```

Keep exactly five headers matching the five row cells: nomor pelanggan, nama, telepon, status, aksi.

- [ ] **Step 2: Add a table structure assertion to the boundary test**

Append:

```ts
test("customer table has no orphan lock column", () => {
  const workspaceSource = readRoute("./operations-workspaces.tsx");
  assert.doesNotMatch(workspaceSource, />Lock<\/th>/);
});
```

- [ ] **Step 3: Audit route component ownership**

Run:

```powershell
rg -n "return <.*Workspace" frontend/src/app --glob "page.tsx"
```

Expected: `/customers`, `/connections`, and `/tariffs` each render a distinct workspace. Generic `BlueprintListWorkspace` reuse remains allowed because each route passes a different explicit config and does not combine visible domains.

- [ ] **Step 4: Audit visible mixed-domain copy**

Run:

```powershell
rg -n "Master Pelanggan dan Sambungan|Frontend untuk customer, tariff group|Tambah Golongan Tarif|Tambah Sambungan" frontend/src/features/operations/operations-workspaces.tsx
```

Expected:

```txt
"Master Pelanggan dan Sambungan": no matches
"Frontend untuk customer, tariff group": no matches
"Tambah Golongan Tarif": only inside TariffWorkspace
"Tambah Sambungan": only inside ConnectionWorkspace
```

- [ ] **Step 5: Run complete static frontend gates**

```powershell
npm.cmd run test:permissions
npm.cmd run typecheck
npm.cmd run lint
npm.cmd run build
```

Expected: zero failures; build lists all 21 application routes.

- [ ] **Step 6: Commit cleanup**

```powershell
git add frontend/src/features/operations/operations-workspaces.tsx frontend/src/features/operations/operations-route-boundaries.test.ts
git commit -m "fix(frontend): clean operations workspace boundaries"
```

### Task 5: Runtime and Visual Verification

**Files:**
- Modify: `docs/00-CONTEXT-PACK.md`
- Modify: `docs/05-UI-UX-PLAN.md`

**Interfaces:**
- Consumes: final frontend image and route set.
- Produces: verified local application and documented domain ownership.

- [ ] **Step 1: Rebuild the frontend container**

```powershell
docker compose up -d --build frontend
```

Expected: frontend container is recreated and started; dependencies remain healthy.

- [ ] **Step 2: Smoke the affected routes and representative other pages**

Check HTTP 200 for:

```txt
/customers
/connections
/tariffs
/metering
/billing
/payments
/accounting
/admin/users
```

- [ ] **Step 3: Audit `/customers` in desktop and mobile browsers**

Verify:

```txt
H1: Pelanggan
Workspace header: Master Pelanggan
Allowed sections: Daftar Pelanggan, Detail Pelanggan, Tambah Pelanggan
Forbidden sections: Daftar Sambungan, Tambah Sambungan, Workflow Sambungan, Tambah Golongan Tarif
No page-level horizontal overflow
No browser console warning/error
```

- [ ] **Step 4: Audit `/connections` in desktop and mobile browsers**

Verify:

```txt
H1: Sambungan
Workspace header: Master Sambungan
Allowed sections: Daftar Sambungan, Detail dan Workflow Sambungan, Tambah Sambungan
Forbidden sections: Daftar Pelanggan, Detail Pelanggan, Tambah Pelanggan, Tambah Golongan Tarif
Entity selectors for pelanggan and golongan tarif remain usable
No page-level horizontal overflow
```

- [ ] **Step 5: Audit `/tariffs` and representative pages**

Verify `/tariffs` contains golongan tariff management, version, block, workflow, and simulation. Verify `/metering`, `/billing`, `/payments`, `/accounting`, and `/admin/users` retain matching route titles, loading/error/empty states, dark theme, and no console errors.

- [ ] **Step 6: Update UI plan and context pack**

Add this ownership rule to `docs/05-UI-UX-PLAN.md`:

```markdown
## Operations Workspace Ownership

- `/customers` owns customer list, detail, and creation only.
- `/connections` owns connection list, detail, creation, and lifecycle workflow; customer and tariff group are lookup dependencies only.
- `/tariffs` owns tariff group, version, block, lifecycle, and simulation management.
```

Add `REQ-UI-032` and final verification evidence to `docs/00-CONTEXT-PACK.md`.

- [ ] **Step 7: Run final diff and quality verification**

```powershell
git diff --check
npm.cmd run test:permissions
npm.cmd run typecheck
npm.cmd run lint
npm.cmd run build
```

Expected: all commands exit 0 and no whitespace errors.

- [ ] **Step 8: Commit verification documentation**

```powershell
git add docs/00-CONTEXT-PACK.md docs/05-UI-UX-PLAN.md
git commit -m "docs(frontend): record operations workspace cleanup"
```

## Final Review Checklist

- Customer, connection, and tariff ownership from the design spec is covered by Tasks 2 and 3.
- Cross-domain query prevention is explicit in Task 2.
- Table alignment and route audit are covered by Task 4.
- Loading, error, empty, authentication, pending, dark mode, and responsive states are covered by Tasks 2 and 5.
- Backend, database, permission, and financial workflow boundaries remain unchanged.
- No placeholder or deferred implementation item remains in this plan.
