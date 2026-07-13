"use client";

import { type FormEvent, useMemo, useState } from "react";
import { PageHeader } from "@/components/common/page-header";
import { EntitySelector } from "@/components/entity-selector/entity-selector";
import type { EntityOption } from "@/components/entity-selector/entity-selector-model";
import { EmptyState } from "@/components/state/empty-state";
import { ErrorState } from "@/components/state/error-state";
import { LoadingSkeleton } from "@/components/state/loading-skeleton";
import { StatusBadge } from "@/components/status/status-badge";
import { apiErrorMessage } from "@/lib/api/client";
import { useCurrentUser } from "@/features/auth/use-current-user";
import { entityLookupLoader } from "@/features/lookups/lookup-api";
import {
  connectionStatusValues,
  customerStatusValues,
  meterReadingStatusValues,
  tariffVersionStatusValues,
  type Connection,
  type ConnectionWorkflow,
  type CustomerSummary,
  type ImportMeterReadingRowPayload,
  type MeterReading,
  type MeterReadingStatus,
  type MeterReadingWorkflow,
  type TariffVersion,
  type TariffVersionWorkflow
} from "./operations-schema";
import {
  useAddTariffBlock,
  useConnection,
  useConnections,
  useConnectionWorkflow,
  useCreateConnection,
  useCreateCustomer,
  useCreateMeterReading,
  useCreateMeterRoute,
  useCreateTariffGroup,
  useCreateTariffVersion,
  useCustomer,
  useCustomers,
  useGenerateReceivableAgingSnapshot,
  useMeterReading,
  useImportMeterReadings,
  useMeterReadings,
  useMeterReadingWorkflow,
  useMeterRoutes,
  useReceivableAgingSnapshot,
  useReceivableAgingSnapshotByPeriod,
  useReceivableAgingSnapshots,
  useTariffBlocks,
  useTariffCalculation,
  useTariffGroups,
  useTariffVersion,
  useTariffVersionWorkflow,
  useTariffVersions,
  useTrialBalance
} from "./use-operations";

const inputClass =
  "w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-teal-600 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-100 disabled:text-slate-500";
const labelClass = "text-xs font-black uppercase text-slate-600";
const primaryButtonClass =
  "inline-flex items-center justify-center gap-2 rounded-lg bg-teal-700 px-4 py-2 text-sm font-black text-white shadow-[0_14px_28px_-20px_rgba(15,118,110,0.9)] transition hover:bg-teal-800 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-600";
const secondaryButtonClass =
  "inline-flex items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-black text-slate-800 shadow-[0_12px_24px_-22px_rgba(15,23,42,0.8)] transition hover:border-teal-300 hover:bg-teal-50 hover:text-teal-900 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500";
const dangerButtonClass =
  "inline-flex items-center justify-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-black text-red-800 transition hover:bg-red-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-red-300";
const connectionLookupLoader = entityLookupLoader("connection");
const customerLookupLoader = entityLookupLoader("customer");
const meterReadingLookupLoader = entityLookupLoader("meter-reading");
const meterRouteLookupLoader = entityLookupLoader("meter-route");
const tariffGroupLookupLoader = entityLookupLoader("tariff-group");
const tariffVersionLookupLoader = entityLookupLoader("tariff-version");

function selectedEntityOption<T extends { id: string }>(
  items: readonly T[],
  id: string,
  toOption: (item: T) => EntityOption
): EntityOption | null {
  const item = items.find((candidate) => candidate.id === id);
  return item ? toOption(item) : null;
}

function formatDate(value: string | null): string {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat("id-ID", { dateStyle: "medium" }).format(new Date(value));
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("id-ID", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat("id-ID", { maximumFractionDigits: 3 }).format(value);
}

function formatMoney(value: number): string {
  return new Intl.NumberFormat("id-ID", { currency: "IDR", maximumFractionDigits: 0, style: "currency" }).format(value);
}

function nullIfBlank(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function numberOrNull(value: string): number | null {
  if (value.trim().length === 0) {
    return null;
  }
  return Number(value);
}

function requiredNumber(value: string): number {
  return Number(value);
}

function toIsoInstant(value: string): string {
  return new Date(value).toISOString();
}

function parseOfflineImportRows(rowsText: string): ImportMeterReadingRowPayload[] {
  const rows = rowsText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .map((line, index) => {
      const columns = line.split(/[,\t;]/).map((column) => column.trim());
      if (columns.length < 4) {
        throw new Error(`Baris ${index + 1} minimal berisi connectionId, meter awal, meter akhir, dan waktu baca.`);
      }
      const previousReading = Number(columns[1]);
      const currentReading = Number(columns[2]);
      if (!Number.isFinite(previousReading) || !Number.isFinite(currentReading)) {
        throw new Error(`Baris ${index + 1} memiliki nilai meter tidak valid.`);
      }
      const anomalyReason = nullIfBlank(columns[5] ?? "");
      return {
        connectionId: columns[0],
        previousReading,
        currentReading,
        readAt: toIsoInstant(columns[3]),
        readerId: nullIfBlank(columns[4] ?? ""),
        anomalyFlag: anomalyReason !== null,
        anomalyReason
      };
    });
  if (rows.length === 0) {
    throw new Error("Baris import offline belum diisi.");
  }
  return rows;
}

function statusTone(status: string): "success" | "warning" | "danger" | "info" | "neutral" {
  if (["ACTIVE", "VERIFIED", "OPEN", "SUCCESS"].includes(status)) {
    return "success";
  }
  if (["DRAFT", "SUBMITTED", "CLOSING_REVIEW"].includes(status)) {
    return "warning";
  }
  if (["REJECTED", "SUSPENDED", "BLACKLISTED"].includes(status)) {
    return "danger";
  }
  if (["ARCHIVED", "TERMINATED", "LOCKED"].includes(status)) {
    return "neutral";
  }
  return "info";
}

function Field({
  label,
  children,
  className
}: Readonly<{ label: string; children: React.ReactNode; className?: string }>) {
  return (
    <label className={className ? `space-y-1 ${className}` : "space-y-1"}>
      <span className={labelClass}>{label}</span>
      {children}
    </label>
  );
}

function SummaryCard({
  label,
  value,
  helper,
  tone = "info"
}: Readonly<{ label: string; value: string; helper: string; tone?: "success" | "warning" | "danger" | "info" | "neutral" }>) {
  return (
    <div className="relative overflow-hidden rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="absolute inset-x-0 top-0 h-1 bg-teal-600" />
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-black text-slate-700">{label}</p>
        <StatusBadge label={tone.toUpperCase()} tone={tone} />
      </div>
      <p className="mt-3 text-2xl font-black text-slate-950">{value}</p>
      <p className="mt-1 text-sm font-medium leading-6 text-slate-600">{helper}</p>
    </div>
  );
}

function Section({
  title,
  description,
  children
}: Readonly<{ title: string; description?: string; children: React.ReactNode }>) {
  return (
    <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 bg-slate-50/70 px-5 py-4">
        <h2 className="text-base font-black text-slate-950">{title}</h2>
        {description ? <p className="mt-1 text-sm font-medium leading-6 text-slate-600">{description}</p> : null}
      </div>
      <div className="p-5">{children}</div>
    </section>
  );
}

function MutationError({ error, fallback }: Readonly<{ error: unknown; fallback: string }>) {
  if (!error) {
    return null;
  }
  return <ErrorState message={apiErrorMessage(error, fallback)} />;
}

function AuthNotice({ authenticated }: Readonly<{ authenticated: boolean }>) {
  if (authenticated) {
    return null;
  }
  return (
    <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm font-semibold text-amber-900">
      Aksi simpan dan workflow membutuhkan sesi admin. Data baca tetap ditampilkan jika backend mengizinkan.
    </div>
  );
}

function PageCount({ page, totalPages }: Readonly<{ page: number; totalPages: number }>) {
  return (
    <span className="text-sm font-semibold text-slate-600">
      Halaman {page + 1} dari {Math.max(totalPages, 1)}
    </span>
  );
}

function CustomerTable({
  customers,
  onSelect
}: Readonly<{ customers: CustomerSummary[]; onSelect: (customerId: string) => void }>) {
  if (customers.length === 0) {
    return <EmptyState title="Pelanggan belum tersedia" description="Daftar pelanggan akan muncul setelah data dibuat." />;
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50">
          <tr>
            <th className="px-4 py-3 text-left font-bold text-slate-700">No. Pelanggan</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Nama</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Telepon</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Status</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Aksi</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {customers.map((customer) => (
            <tr key={customer.id} className="hover:bg-teal-50">
              <td className="whitespace-nowrap px-4 py-3 font-bold text-slate-950">{customer.customerNumber}</td>
              <td className="px-4 py-3 text-slate-700">{customer.fullName}</td>
              <td className="whitespace-nowrap px-4 py-3 text-slate-700">{customer.phoneNumber ?? "-"}</td>
              <td className="whitespace-nowrap px-4 py-3">
                <StatusBadge label={customer.status} tone={statusTone(customer.status)} />
              </td>
              <td className="whitespace-nowrap px-4 py-3">
                <button type="button" className={secondaryButtonClass} onClick={() => onSelect(customer.id)}>
                  Detail
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ConnectionTable({
  connections,
  onSelect
}: Readonly<{ connections: Connection[]; onSelect: (connectionId: string) => void }>) {
  if (connections.length === 0) {
    return <EmptyState title="Sambungan belum tersedia" description="Sambungan pelanggan akan muncul setelah dibuat." />;
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50">
          <tr>
            <th className="px-4 py-3 text-left font-bold text-slate-700">No. Sambungan</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Meter</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Terpasang</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Status</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Aksi</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {connections.map((connection) => (
            <tr key={connection.id} className="hover:bg-teal-50">
              <td className="whitespace-nowrap px-4 py-3 font-bold text-slate-950">{connection.connectionNumber}</td>
              <td className="whitespace-nowrap px-4 py-3 text-slate-700">{connection.meterNumber}</td>
              <td className="whitespace-nowrap px-4 py-3 text-slate-700">{formatDate(connection.installedAt)}</td>
              <td className="whitespace-nowrap px-4 py-3">
                <StatusBadge label={connection.status} tone={statusTone(connection.status)} />
              </td>
              <td className="whitespace-nowrap px-4 py-3">
                <button type="button" className={secondaryButtonClass} onClick={() => onSelect(connection.id)}>
                  Pilih
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

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

  function submitCustomer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    createCustomerMutation.mutate({
      customerNumber: form.customerNumber,
      fullName: form.fullName,
      identityNumber: nullIfBlank(form.identityNumber),
      phoneNumber: nullIfBlank(form.phoneNumber),
      addressLine: form.addressLine,
      areaCode: form.areaCode,
      latitude: numberOrNull(form.latitude),
      longitude: numberOrNull(form.longitude),
      reason: form.reason
    });
  }

  if (customersQuery.isLoading) {
    return <LoadingSkeleton />;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Master Pelanggan"
        description="Kelola identitas, alamat, status, dan data kontak pelanggan dalam satu workspace."
      />
      <AuthNotice authenticated={authenticated} />

      <section className="max-w-md">
        <SummaryCard
          label="Pelanggan"
          value={String(customersQuery.data?.totalItems ?? 0)}
          helper="Total pelanggan sesuai filter aktif."
          tone="info"
        />
      </section>

      <section className="grid gap-4 xl:grid-cols-2">
        <Section title="Daftar Pelanggan">
          {customersQuery.isError ? (
            <ErrorState message={apiErrorMessage(customersQuery.error, "Pelanggan tidak tersedia.")} />
          ) : (
            <div className="space-y-4">
              <div className="grid gap-3 md:grid-cols-[1fr_180px_120px]">
                <input
                  className={inputClass}
                  placeholder="Cari nomor atau nama"
                  value={filters.search}
                  onChange={(event) => setFilters((prev) => ({ ...prev, page: 0, search: event.target.value }))}
                />
                <select
                  className={inputClass}
                  value={filters.status}
                  onChange={(event) => setFilters((prev) => ({ ...prev, page: 0, status: event.target.value }))}
                >
                  <option value="">Semua status</option>
                  {customerStatusValues.map((status) => (
                    <option key={status} value={status}>
                      {status}
                    </option>
                  ))}
                </select>
                <button type="button" className={secondaryButtonClass} onClick={() => void customersQuery.refetch()}>
                  Muat ulang
                </button>
              </div>
              <CustomerTable customers={customersQuery.data?.items ?? []} onSelect={setSelectedCustomerId} />
              <PageCount page={customersQuery.data?.page ?? 0} totalPages={customersQuery.data?.totalPages ?? 0} />
            </div>
          )}
        </Section>

        <Section title="Detail Pelanggan">
          {!selectedCustomerId ? (
            <EmptyState title="Pilih pelanggan" description="Klik Detail di tabel pelanggan untuk membaca alamat dan identitas." />
          ) : customerQuery.isLoading ? (
            <LoadingSkeleton />
          ) : customerQuery.isError ? (
            <ErrorState message={apiErrorMessage(customerQuery.error, "Detail pelanggan tidak tersedia.")} />
          ) : customerQuery.data ? (
            <div className="space-y-3 text-sm">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-lg font-bold text-slate-950">{customerQuery.data.fullName}</p>
                  <p className="font-semibold text-slate-600">{customerQuery.data.customerNumber}</p>
                </div>
                <StatusBadge label={customerQuery.data.status} tone={statusTone(customerQuery.data.status)} />
              </div>
              <p className="text-slate-700">NIK/Identitas: {customerQuery.data.identityNumber ?? "-"}</p>
              <p className="text-slate-700">Telepon: {customerQuery.data.phoneNumber ?? "-"}</p>
              <div className="rounded-lg border border-slate-200 p-3">
                <p className="font-bold text-slate-950">Alamat</p>
                {customerQuery.data.addresses.length === 0 ? (
                  <p className="mt-2 text-slate-600">Alamat belum tersedia.</p>
                ) : (
                  customerQuery.data.addresses.map((address) => (
                    <div key={address.id} className="mt-2 border-t border-slate-100 pt-2">
                      <p className="text-slate-700">{address.addressLine}</p>
                      <p className="text-slate-600">Area: {address.areaCode}</p>
                    </div>
                  ))
                )}
              </div>
            </div>
          ) : null}
        </Section>
      </section>

      <section className="max-w-3xl">
        <Section title="Tambah Pelanggan">
          <form className="space-y-3" onSubmit={submitCustomer}>
            <Field label="Nomor pelanggan">
              <input
                className={inputClass}
                value={form.customerNumber}
                onChange={(event) => setForm((prev) => ({ ...prev, customerNumber: event.target.value }))}
                required
              />
            </Field>
            <Field label="Nama lengkap">
              <input
                className={inputClass}
                value={form.fullName}
                onChange={(event) => setForm((prev) => ({ ...prev, fullName: event.target.value }))}
                required
              />
            </Field>
            <Field label="Nomor identitas">
              <input
                className={inputClass}
                value={form.identityNumber}
                onChange={(event) => setForm((prev) => ({ ...prev, identityNumber: event.target.value }))}
              />
            </Field>
            <Field label="Telepon">
              <input
                className={inputClass}
                value={form.phoneNumber}
                onChange={(event) => setForm((prev) => ({ ...prev, phoneNumber: event.target.value }))}
              />
            </Field>
            <Field label="Alamat">
              <textarea
                className={inputClass}
                value={form.addressLine}
                onChange={(event) => setForm((prev) => ({ ...prev, addressLine: event.target.value }))}
                required
              />
            </Field>
            <div className="grid gap-3 md:grid-cols-3">
              <Field label="Area">
                <input
                  className={inputClass}
                  value={form.areaCode}
                  onChange={(event) => setForm((prev) => ({ ...prev, areaCode: event.target.value }))}
                  required
                />
              </Field>
              <Field label="Latitude">
                <input
                  className={inputClass}
                  type="number"
                  step="0.0000001"
                  value={form.latitude}
                  onChange={(event) => setForm((prev) => ({ ...prev, latitude: event.target.value }))}
                />
              </Field>
              <Field label="Longitude">
                <input
                  className={inputClass}
                  type="number"
                  step="0.0000001"
                  value={form.longitude}
                  onChange={(event) => setForm((prev) => ({ ...prev, longitude: event.target.value }))}
                />
              </Field>
            </div>
            <Field label="Alasan audit">
              <textarea
                className={inputClass}
                value={form.reason}
                onChange={(event) => setForm((prev) => ({ ...prev, reason: event.target.value }))}
                required
              />
            </Field>
            <MutationError error={createCustomerMutation.error} fallback="Gagal membuat pelanggan." />
            <button type="submit" className={primaryButtonClass} disabled={!authenticated || createCustomerMutation.isPending}>
              Simpan Pelanggan
            </button>
          </form>
        </Section>
      </section>
    </div>
  );
}

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

  function submitConnection(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    createConnectionMutation.mutate({
      customerId: form.customerId,
      tariffGroupId: form.tariffGroupId,
      connectionNumber: form.connectionNumber,
      meterNumber: form.meterNumber,
      installedAt: nullIfBlank(form.installedAt),
      reason: form.reason
    });
  }

  function submitConnectionWorkflow(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    connectionWorkflowMutation.mutate({
      connectionId: workflow.connectionId,
      workflow: workflow.workflow,
      payload: { reason: workflow.reason }
    });
  }

  if (connectionsQuery.isLoading) {
    return <LoadingSkeleton />;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Master Sambungan"
        description="Kelola pemasangan meter, golongan tarif, status layanan, dan workflow sambungan pelanggan."
      />
      <AuthNotice authenticated={authenticated} />

      <section className="max-w-md">
        <SummaryCard
          label="Sambungan"
          value={String(connectionsQuery.data?.totalItems ?? 0)}
          helper="Total sambungan sesuai filter aktif."
          tone="success"
        />
      </section>

      <section className="grid gap-4 xl:grid-cols-[1.4fr_.8fr]">
        <Section title="Daftar Sambungan">
          {connectionsQuery.isError ? (
            <ErrorState message={apiErrorMessage(connectionsQuery.error, "Sambungan tidak tersedia.")} />
          ) : (
            <div className="space-y-4">
              <div className="grid gap-3 md:grid-cols-[1fr_180px_120px]">
                <EntitySelector
                  value={filters.customerId}
                  onChange={(value) => setFilters((prev) => ({ ...prev, page: 0, customerId: value }))}
                  loadOptions={customerLookupLoader}
                  label="Pelanggan"
                  ariaLabel="Filter sambungan berdasarkan pelanggan"
                  placeholder="Cari pelanggan"
                />
                <select
                  className={inputClass}
                  value={filters.status}
                  onChange={(event) => setFilters((prev) => ({ ...prev, page: 0, status: event.target.value }))}
                >
                  <option value="">Semua status</option>
                  {connectionStatusValues.map((status) => (
                    <option key={status} value={status}>
                      {status}
                    </option>
                  ))}
                </select>
                <button type="button" className={secondaryButtonClass} onClick={() => void connectionsQuery.refetch()}>
                  Muat ulang
                </button>
              </div>
              <ConnectionTable
                connections={connectionsQuery.data?.items ?? []}
                onSelect={(connectionId) => {
                  setSelectedConnectionId(connectionId);
                  setWorkflow((previousWorkflow) => ({ ...previousWorkflow, connectionId }));
                }}
              />
              <PageCount page={connectionsQuery.data?.page ?? 0} totalPages={connectionsQuery.data?.totalPages ?? 0} />
            </div>
          )}
        </Section>

        <Section title="Detail dan Workflow Sambungan">
          <form className="space-y-3" onSubmit={submitConnectionWorkflow}>
            <EntitySelector
              value={workflow.connectionId}
              selectedOption={selectedEntityOption(
                connectionsQuery.data?.items ?? [],
                workflow.connectionId,
                (connection) => ({
                  id: connection.id,
                  label: connection.connectionNumber,
                  description: `Meter ${connection.meterNumber}`,
                  status: connection.status
                })
              )}
              onChange={(value) => setWorkflow((previousWorkflow) => ({ ...previousWorkflow, connectionId: value }))}
              loadOptions={connectionLookupLoader}
              label="Sambungan"
              ariaLabel="Pilih sambungan untuk workflow"
              placeholder="Cari nomor sambungan atau meter"
              required
              invalid={!workflow.connectionId}
            />
            <Field label="Aksi">
              <select
                className={inputClass}
                value={workflow.workflow}
                onChange={(event) =>
                  setWorkflow((previousWorkflow) => ({
                    ...previousWorkflow,
                    workflow: event.target.value as ConnectionWorkflow
                  }))
                }
              >
                <option value="activate">Aktifkan</option>
                <option value="suspend">Suspend</option>
                <option value="terminate">Terminasi</option>
              </select>
            </Field>
            <Field label="Alasan audit">
              <textarea
                className={inputClass}
                value={workflow.reason}
                onChange={(event) => setWorkflow((previousWorkflow) => ({ ...previousWorkflow, reason: event.target.value }))}
                required
              />
            </Field>
            <MutationError error={connectionWorkflowMutation.error} fallback="Workflow sambungan gagal." />
            <button
              type="submit"
              className={workflow.workflow === "terminate" ? dangerButtonClass : primaryButtonClass}
              disabled={!authenticated || connectionWorkflowMutation.isPending}
            >
              Jalankan Workflow
            </button>
          </form>
          <div className="mt-5">
            {!selectedConnectionId ? (
              <EmptyState title="Belum memilih sambungan" description="Pilih baris sambungan untuk membaca status dan aksi." />
            ) : connectionQuery.isLoading ? (
              <LoadingSkeleton />
            ) : connectionQuery.isError ? (
              <ErrorState message={apiErrorMessage(connectionQuery.error, "Detail sambungan tidak tersedia.")} />
            ) : connectionQuery.data ? (
              <div className="space-y-2 text-sm text-slate-700">
                <p className="font-bold text-slate-950">{connectionQuery.data.connectionNumber}</p>
                <p>Meter: {connectionQuery.data.meterNumber}</p>
                <p>Tanggal pasang: {formatDate(connectionQuery.data.installedAt)}</p>
                <p>Aksi tersedia: {connectionQuery.data.availableActions.join(", ") || "-"}</p>
              </div>
            ) : null}
          </div>
        </Section>
      </section>

      <Section title="Tambah Sambungan">
        <form className="space-y-3" onSubmit={submitConnection}>
          <EntitySelector
            value={form.customerId}
            onChange={(value) => setForm((previousForm) => ({ ...previousForm, customerId: value }))}
            loadOptions={customerLookupLoader}
            label="Pelanggan"
            ariaLabel="Pilih pelanggan sambungan"
            placeholder="Cari nomor atau nama pelanggan"
            required
            invalid={!form.customerId}
          />
          <EntitySelector
            value={form.tariffGroupId}
            onChange={(value) => setForm((previousForm) => ({ ...previousForm, tariffGroupId: value }))}
            loadOptions={tariffGroupLookupLoader}
            label="Golongan tarif"
            ariaLabel="Pilih golongan tarif sambungan"
            placeholder="Cari kode atau nama golongan"
            required
            invalid={!form.tariffGroupId}
          />
          <Field label="Nomor sambungan">
            <input
              className={inputClass}
              value={form.connectionNumber}
              onChange={(event) => setForm((previousForm) => ({ ...previousForm, connectionNumber: event.target.value }))}
              required
            />
          </Field>
          <Field label="Nomor meter">
            <input
              className={inputClass}
              value={form.meterNumber}
              onChange={(event) => setForm((previousForm) => ({ ...previousForm, meterNumber: event.target.value }))}
              required
            />
          </Field>
          <Field label="Tanggal pasang">
            <input
              className={inputClass}
              type="date"
              value={form.installedAt}
              onChange={(event) => setForm((previousForm) => ({ ...previousForm, installedAt: event.target.value }))}
            />
          </Field>
          <Field label="Alasan audit">
            <textarea
              className={inputClass}
              value={form.reason}
              onChange={(event) => setForm((previousForm) => ({ ...previousForm, reason: event.target.value }))}
              required
            />
          </Field>
          <MutationError error={createConnectionMutation.error} fallback="Gagal membuat sambungan." />
          <button type="submit" className={primaryButtonClass} disabled={!authenticated || createConnectionMutation.isPending}>
            Simpan Sambungan
          </button>
        </form>
      </Section>
    </div>
  );
}

function MeterReadingTable({
  readings,
  onSelect
}: Readonly<{ readings: MeterReading[]; onSelect: (readingId: string) => void }>) {
  if (readings.length === 0) {
    return <EmptyState title="Baca meter belum tersedia" description="Data baca meter akan muncul setelah input dibuat." />;
  }
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50">
          <tr>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Periode</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">Awal</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">Akhir</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">Pakai m3</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Status</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Terkunci</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Aksi</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {readings.map((reading) => (
            <tr key={reading.id} className="hover:bg-teal-50">
              <td className="whitespace-nowrap px-4 py-3 font-bold text-slate-950">{reading.period}</td>
              <td className="whitespace-nowrap px-4 py-3 text-right text-slate-700">{formatNumber(reading.previousReading)}</td>
              <td className="whitespace-nowrap px-4 py-3 text-right text-slate-700">{formatNumber(reading.currentReading)}</td>
              <td className="whitespace-nowrap px-4 py-3 text-right font-bold text-slate-950">{formatNumber(reading.usageM3)}</td>
              <td className="whitespace-nowrap px-4 py-3">
                <StatusBadge label={reading.status} tone={statusTone(reading.status)} />
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-slate-700">
                {reading.lockedAt ? formatDateTime(reading.lockedAt) : "-"}
              </td>
              <td className="whitespace-nowrap px-4 py-3">
                <button type="button" className={secondaryButtonClass} onClick={() => onSelect(reading.id)}>
                  Pilih
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function MeteringWorkspace() {
  const currentUserQuery = useCurrentUser();
  const authenticated = currentUserQuery.data?.authenticated ?? false;
  const [routeFilters, setRouteFilters] = useState({ page: 0, size: 10, areaCode: "" });
  const [readingFilters, setReadingFilters] = useState({ page: 0, size: 10, routeId: "", period: "", status: "" });
  const [selectedReadingId, setSelectedReadingId] = useState<string | null>(null);
  const [routeForm, setRouteForm] = useState({ routeCode: "", name: "", areaCode: "", reason: "" });
  const [readingForm, setReadingForm] = useState({
    connectionId: "",
    routeId: "",
    period: "",
    previousReading: "",
    currentReading: "",
    readAt: "",
    readerId: "",
    anomalyFlag: false,
    anomalyReason: "",
    reason: ""
  });
  const [workflowForm, setWorkflowForm] = useState<{ readingId: string; workflow: MeterReadingWorkflow; reason: string }>({
    readingId: "",
    workflow: "submit",
    reason: ""
  });
  const [importForm, setImportForm] = useState({
    sourceDeviceId: "",
    sourceBatchReference: "",
    routeId: "",
    period: "",
    rowsText: "",
    reason: ""
  });
  const [importParseError, setImportParseError] = useState<string | null>(null);

  const routesQuery = useMeterRoutes({
    page: routeFilters.page,
    size: routeFilters.size,
    areaCode: routeFilters.areaCode || undefined
  });
  const readingsQuery = useMeterReadings({
    page: readingFilters.page,
    size: readingFilters.size,
    routeId: readingFilters.routeId || undefined,
    period: readingFilters.period || undefined,
    status: readingFilters.status ? (readingFilters.status as MeterReadingStatus) : undefined
  });
  const readingQuery = useMeterReading(selectedReadingId);
  const createRouteMutation = useCreateMeterRoute();
  const createReadingMutation = useCreateMeterReading();
  const importMutation = useImportMeterReadings();
  const workflowMutation = useMeterReadingWorkflow();

  function submitRoute(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    createRouteMutation.mutate(routeForm);
  }

  function submitReading(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    createReadingMutation.mutate({
      connectionId: readingForm.connectionId,
      routeId: readingForm.routeId,
      period: readingForm.period,
      previousReading: requiredNumber(readingForm.previousReading),
      currentReading: requiredNumber(readingForm.currentReading),
      readAt: toIsoInstant(readingForm.readAt),
      readerId: nullIfBlank(readingForm.readerId),
      anomalyFlag: readingForm.anomalyFlag,
      anomalyReason: nullIfBlank(readingForm.anomalyReason),
      reason: readingForm.reason
    });
  }

  function submitWorkflow(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    workflowMutation.mutate({
      readingId: workflowForm.readingId,
      workflow: workflowForm.workflow,
      payload: { reason: workflowForm.reason }
    });
  }

  function submitImport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setImportParseError(null);
    let rows: ImportMeterReadingRowPayload[];
    try {
      rows = parseOfflineImportRows(importForm.rowsText);
    } catch (error) {
      setImportParseError(error instanceof Error ? error.message : "Format import offline tidak valid.");
      return;
    }
    importMutation.mutate({
      sourceDeviceId: importForm.sourceDeviceId,
      sourceBatchReference: importForm.sourceBatchReference,
      routeId: importForm.routeId,
      period: importForm.period,
      rows,
      reason: importForm.reason
    });
  }

  if (routesQuery.isLoading || readingsQuery.isLoading) {
    return <LoadingSkeleton />;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Baca Meter"
        description="Frontend untuk meter route, input meter reading, detail reading, dan workflow submit/verify/reject."
      />
      <AuthNotice authenticated={authenticated} />

      <section className="grid gap-4 md:grid-cols-4">
        <SummaryCard label="Rute Meter" value={String(routesQuery.data?.totalItems ?? 0)} helper="Rute baca meter aktif di filter." />
        <SummaryCard
          label="Baca Meter"
          value={String(readingsQuery.data?.totalItems ?? 0)}
          helper="Total pembacaan sesuai filter."
          tone="success"
        />
        <SummaryCard
          label="Verified"
          value={String((readingsQuery.data?.items ?? []).filter((reading) => reading.status === "VERIFIED").length)}
          helper="Pembacaan terverifikasi di halaman ini."
          tone="warning"
        />
        <SummaryCard
          label="Locked"
          value={String((readingsQuery.data?.items ?? []).filter((reading) => reading.status === "LOCKED").length)}
          helper="Pembacaan terkunci siap billing di halaman ini."
          tone="success"
        />
      </section>

      <section className="grid gap-4 xl:grid-cols-2">
        <Section title="Rute Baca Meter">
          {routesQuery.isError ? (
            <ErrorState message={apiErrorMessage(routesQuery.error, "Rute meter tidak tersedia.")} />
          ) : (
            <div className="space-y-4">
              <div className="grid gap-3 md:grid-cols-[1fr_120px]">
                <input
                  className={inputClass}
                  placeholder="Filter area"
                  value={routeFilters.areaCode}
                  onChange={(event) => setRouteFilters((prev) => ({ ...prev, page: 0, areaCode: event.target.value }))}
                />
                <button type="button" className={secondaryButtonClass} onClick={() => void routesQuery.refetch()}>
                  Muat ulang
                </button>
              </div>
              {(routesQuery.data?.items ?? []).length === 0 ? (
                <EmptyState title="Rute belum tersedia" description="Tambahkan rute agar pembacaan meter bisa dipetakan." />
              ) : (
                <div className="grid gap-3">
                  {(routesQuery.data?.items ?? []).map((route) => (
                    <div key={route.id} className="rounded-lg border border-slate-200 p-3">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="font-bold text-slate-950">{route.routeCode}</p>
                          <p className="text-sm text-slate-700">{route.name}</p>
                        </div>
                        <StatusBadge label={route.areaCode} tone="info" />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </Section>

        <Section title="Tambah Rute">
          <form className="space-y-3" onSubmit={submitRoute}>
            <Field label="Kode rute">
              <input
                className={inputClass}
                value={routeForm.routeCode}
                onChange={(event) => setRouteForm((prev) => ({ ...prev, routeCode: event.target.value }))}
                required
              />
            </Field>
            <Field label="Nama rute">
              <input
                className={inputClass}
                value={routeForm.name}
                onChange={(event) => setRouteForm((prev) => ({ ...prev, name: event.target.value }))}
                required
              />
            </Field>
            <Field label="Area">
              <input
                className={inputClass}
                value={routeForm.areaCode}
                onChange={(event) => setRouteForm((prev) => ({ ...prev, areaCode: event.target.value }))}
                required
              />
            </Field>
            <Field label="Alasan audit">
              <textarea
                className={inputClass}
                value={routeForm.reason}
                onChange={(event) => setRouteForm((prev) => ({ ...prev, reason: event.target.value }))}
                required
              />
            </Field>
            <MutationError error={createRouteMutation.error} fallback="Gagal membuat rute meter." />
            <button type="submit" className={primaryButtonClass} disabled={!authenticated || createRouteMutation.isPending}>
              Simpan Rute
            </button>
          </form>
        </Section>
      </section>

      <section className="grid gap-4 xl:grid-cols-[1.2fr_.8fr]">
        <Section title="Daftar Baca Meter">
          {readingsQuery.isError ? (
            <ErrorState message={apiErrorMessage(readingsQuery.error, "Baca meter tidak tersedia.")} />
          ) : (
            <div className="space-y-4">
              <div className="grid gap-3 md:grid-cols-4">
                <EntitySelector value={readingFilters.routeId} onChange={(value) => setReadingFilters((prev) => ({ ...prev, page: 0, routeId: value }))} loadOptions={meterRouteLookupLoader} label="Rute" ariaLabel="Filter baca meter berdasarkan rute" placeholder="Cari kode atau nama rute" />
                <input
                  className={inputClass}
                  placeholder="YYYY-MM"
                  value={readingFilters.period}
                  onChange={(event) => setReadingFilters((prev) => ({ ...prev, page: 0, period: event.target.value }))}
                />
                <select
                  className={inputClass}
                  value={readingFilters.status}
                  onChange={(event) => setReadingFilters((prev) => ({ ...prev, page: 0, status: event.target.value }))}
                >
                  <option value="">Semua status</option>
                  {meterReadingStatusValues.map((status) => (
                    <option key={status} value={status}>
                      {status}
                    </option>
                  ))}
                </select>
                <button type="button" className={secondaryButtonClass} onClick={() => void readingsQuery.refetch()}>
                  Muat ulang
                </button>
              </div>
              <MeterReadingTable
                readings={readingsQuery.data?.items ?? []}
                onSelect={(readingId) => {
                  setSelectedReadingId(readingId);
                  setWorkflowForm((prev) => ({ ...prev, readingId }));
                }}
              />
            </div>
          )}
        </Section>

        <Section title="Workflow Baca Meter">
          <form className="space-y-3" onSubmit={submitWorkflow}>
            <EntitySelector value={workflowForm.readingId} selectedOption={selectedEntityOption(readingsQuery.data?.items ?? [], workflowForm.readingId, (reading) => ({ id: reading.id, label: `${reading.period} · ${formatNumber(reading.usageM3)} m3`, status: reading.status }))} onChange={(value) => setWorkflowForm((prev) => ({ ...prev, readingId: value }))} loadOptions={meterReadingLookupLoader} label="Pembacaan meter" ariaLabel="Pilih pembacaan untuk workflow" placeholder="Cari periode pembacaan" required invalid={!workflowForm.readingId} />
            <Field label="Aksi">
              <select
                className={inputClass}
                value={workflowForm.workflow}
                onChange={(event) => setWorkflowForm((prev) => ({ ...prev, workflow: event.target.value as MeterReadingWorkflow }))}
              >
                <option value="submit">Submit</option>
                <option value="verify">Verifikasi</option>
                <option value="reject">Tolak</option>
                <option value="lock">Kunci</option>
              </select>
            </Field>
            <Field label="Alasan audit">
              <textarea
                className={inputClass}
                value={workflowForm.reason}
                onChange={(event) => setWorkflowForm((prev) => ({ ...prev, reason: event.target.value }))}
                required
              />
            </Field>
            <MutationError error={workflowMutation.error} fallback="Workflow baca meter gagal." />
            <button
              type="submit"
              className={workflowForm.workflow === "reject" ? dangerButtonClass : primaryButtonClass}
              disabled={!authenticated || workflowMutation.isPending}
            >
              Jalankan Workflow
            </button>
          </form>
          <div className="mt-5">
            {!selectedReadingId ? (
              <EmptyState title="Belum memilih reading" description="Pilih pembacaan untuk melihat detail dan aksi tersedia." />
            ) : readingQuery.isLoading ? (
              <LoadingSkeleton />
            ) : readingQuery.isError ? (
              <ErrorState message={apiErrorMessage(readingQuery.error, "Detail baca meter tidak tersedia.")} />
            ) : readingQuery.data ? (
              <div className="space-y-2 text-sm text-slate-700">
                <p className="font-bold text-slate-950">{readingQuery.data.period}</p>
                <p>Connection ID: {readingQuery.data.connectionId}</p>
                <p>Route ID: {readingQuery.data.routeId}</p>
                <p>Dibaca: {formatDateTime(readingQuery.data.readAt)}</p>
                <p>Anomali: {readingQuery.data.anomalyFlag ? readingQuery.data.anomalyReason ?? "Ya" : "Tidak"}</p>
                <p>Import batch: {readingQuery.data.importBatchId ?? "-"}</p>
                <p>Dikunci: {readingQuery.data.lockedAt ? `${formatDateTime(readingQuery.data.lockedAt)} oleh ${readingQuery.data.lockedBy ?? "-"}` : "-"}</p>
                <p>Aksi tersedia: {readingQuery.data.availableActions.join(", ") || "-"}</p>
              </div>
            ) : null}
          </div>
        </Section>
      </section>

      <Section title="Input Baca Meter">
        <form className="grid gap-3 lg:grid-cols-4" onSubmit={submitReading}>
          <EntitySelector value={readingForm.connectionId} onChange={(value) => setReadingForm((prev) => ({ ...prev, connectionId: value }))} loadOptions={connectionLookupLoader} label="Sambungan" ariaLabel="Pilih sambungan baca meter" placeholder="Cari nomor sambungan atau meter" required invalid={!readingForm.connectionId} />
          <EntitySelector value={readingForm.routeId} onChange={(value) => setReadingForm((prev) => ({ ...prev, routeId: value }))} loadOptions={meterRouteLookupLoader} label="Rute" ariaLabel="Pilih rute baca meter" placeholder="Cari kode atau nama rute" required invalid={!readingForm.routeId} />
          <Field label="Periode">
            <input
              className={inputClass}
              placeholder="YYYY-MM"
              value={readingForm.period}
              onChange={(event) => setReadingForm((prev) => ({ ...prev, period: event.target.value }))}
              required
            />
          </Field>
          <Field label="Waktu baca">
            <input
              className={inputClass}
              type="datetime-local"
              value={readingForm.readAt}
              onChange={(event) => setReadingForm((prev) => ({ ...prev, readAt: event.target.value }))}
              required
            />
          </Field>
          <Field label="Meter awal">
            <input
              className={inputClass}
              type="number"
              step="0.001"
              value={readingForm.previousReading}
              onChange={(event) => setReadingForm((prev) => ({ ...prev, previousReading: event.target.value }))}
              required
            />
          </Field>
          <Field label="Meter akhir">
            <input
              className={inputClass}
              type="number"
              step="0.001"
              value={readingForm.currentReading}
              onChange={(event) => setReadingForm((prev) => ({ ...prev, currentReading: event.target.value }))}
              required
            />
          </Field>
          <Field label="Reader ID">
            <input
              className={inputClass}
              value={readingForm.readerId}
              onChange={(event) => setReadingForm((prev) => ({ ...prev, readerId: event.target.value }))}
            />
          </Field>
          <Field label="Alasan audit">
            <input
              className={inputClass}
              value={readingForm.reason}
              onChange={(event) => setReadingForm((prev) => ({ ...prev, reason: event.target.value }))}
              required
            />
          </Field>
          <label className="flex items-center gap-2 text-sm font-semibold text-slate-700">
            <input
              type="checkbox"
              checked={readingForm.anomalyFlag}
              onChange={(event) => setReadingForm((prev) => ({ ...prev, anomalyFlag: event.target.checked }))}
            />
            Tandai anomali
          </label>
          <Field label="Catatan anomali" className="lg:col-span-3">
            <input
              className={inputClass}
              value={readingForm.anomalyReason}
              onChange={(event) => setReadingForm((prev) => ({ ...prev, anomalyReason: event.target.value }))}
            />
          </Field>
          <div className="lg:col-span-4">
            <MutationError error={createReadingMutation.error} fallback="Gagal membuat baca meter." />
          </div>
          <button type="submit" className={primaryButtonClass} disabled={!authenticated || createReadingMutation.isPending}>
            Simpan Baca Meter
          </button>
        </form>
      </Section>

      <Section title="Import Offline">
        <form className="grid gap-3 lg:grid-cols-4" onSubmit={submitImport}>
          <Field label="Device ID">
            <input
              className={inputClass}
              value={importForm.sourceDeviceId}
              onChange={(event) => setImportForm((prev) => ({ ...prev, sourceDeviceId: event.target.value }))}
              required
            />
          </Field>
          <Field label="Batch reference">
            <input
              className={inputClass}
              value={importForm.sourceBatchReference}
              onChange={(event) => setImportForm((prev) => ({ ...prev, sourceBatchReference: event.target.value }))}
              required
            />
          </Field>
          <EntitySelector value={importForm.routeId} onChange={(value) => setImportForm((prev) => ({ ...prev, routeId: value }))} loadOptions={meterRouteLookupLoader} label="Rute" ariaLabel="Pilih rute import offline" placeholder="Cari kode atau nama rute" required invalid={!importForm.routeId} />
          <Field label="Periode">
            <input
              className={inputClass}
              placeholder="YYYY-MM"
              value={importForm.period}
              onChange={(event) => setImportForm((prev) => ({ ...prev, period: event.target.value }))}
              required
            />
          </Field>
          <Field label="Baris offline" className="lg:col-span-4">
            <textarea
              className={`${inputClass} min-h-32 font-mono`}
              placeholder="connectionId,awal,akhir,waktu,readerId,catatan"
              value={importForm.rowsText}
              onChange={(event) => setImportForm((prev) => ({ ...prev, rowsText: event.target.value }))}
              required
            />
          </Field>
          <Field label="Alasan audit" className="lg:col-span-3">
            <input
              className={inputClass}
              value={importForm.reason}
              onChange={(event) => setImportForm((prev) => ({ ...prev, reason: event.target.value }))}
              required
            />
          </Field>
          <div className="flex items-end">
            <button type="submit" className={primaryButtonClass} disabled={!authenticated || importMutation.isPending}>
              Import Offline
            </button>
          </div>
          <div className="lg:col-span-4">
            {importParseError ? <ErrorState message={importParseError} /> : null}
            <MutationError error={importMutation.error} fallback="Import offline gagal." />
          </div>
        </form>
        {importMutation.data ? (
          <div className="mt-5 grid gap-3 md:grid-cols-4">
            <SummaryCard label="Rows" value={String(importMutation.data.totalRows)} helper={importMutation.data.sourceBatchReference} />
            <SummaryCard label="Imported" value={String(importMutation.data.importedRows)} helper={importMutation.data.period} tone="success" />
            <SummaryCard label="Skipped" value={String(importMutation.data.skippedRows)} helper="Duplikat atau dilewati." tone="warning" />
            <SummaryCard label="Invalid" value={String(importMutation.data.invalidRows)} helper="Perlu koreksi perangkat." tone="danger" />
          </div>
        ) : null}
      </Section>
    </div>
  );
}

function TariffVersionTable({
  versions,
  onSelect
}: Readonly<{ versions: TariffVersion[]; onSelect: (versionId: string) => void }>) {
  if (versions.length === 0) {
    return <EmptyState title="Versi tarif belum tersedia" description="Buat versi tarif dan blok progresif terlebih dahulu." />;
  }
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50">
          <tr>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Tariff Group ID</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Efektif</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Status</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Aksi</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {versions.map((version) => (
            <tr key={version.id} className="hover:bg-teal-50">
              <td className="px-4 py-3 font-semibold text-slate-700">{version.tariffGroupId}</td>
              <td className="whitespace-nowrap px-4 py-3 text-slate-700">{formatDate(version.effectiveDate)}</td>
              <td className="whitespace-nowrap px-4 py-3">
                <StatusBadge label={version.status} tone={statusTone(version.status)} />
              </td>
              <td className="whitespace-nowrap px-4 py-3">
                <button type="button" className={secondaryButtonClass} onClick={() => onSelect(version.id)}>
                  Pilih
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function TariffWorkspace() {
  const currentUserQuery = useCurrentUser();
  const authenticated = currentUserQuery.data?.authenticated ?? false;
  const [filters, setFilters] = useState({ page: 0, size: 10, tariffGroupId: "", status: "" });
  const [selectedVersionId, setSelectedVersionId] = useState<string | null>(null);
  const [tariffGroupForm, setTariffGroupForm] = useState({ code: "", name: "", reason: "" });
  const [versionForm, setVersionForm] = useState({
    tariffGroupId: "",
    effectiveDate: "",
    fixedCharge: "0",
    levyCharge: "0",
    adminCharge: "0",
    wasteCharge: "0",
    penaltyRate: "0",
    reason: ""
  });
  const [blockForm, setBlockForm] = useState({ blockOrder: "1", minM3: "0", maxM3: "", pricePerM3: "", reason: "" });
  const [workflowForm, setWorkflowForm] = useState<{ tariffVersionId: string; workflow: TariffVersionWorkflow; reason: string }>({
    tariffVersionId: "",
    workflow: "activate",
    reason: ""
  });
  const [calculationForm, setCalculationForm] = useState({
    tariffGroupId: "",
    billingDate: "",
    usageM3: "",
    outstandingAmount: "0"
  });

  const tariffGroupsQuery = useTariffGroups({ page: 0, size: 100 });
  const versionsQuery = useTariffVersions({
    page: filters.page,
    size: filters.size,
    tariffGroupId: filters.tariffGroupId || undefined,
    status: filters.status ? (filters.status as "DRAFT" | "ACTIVE" | "ARCHIVED") : undefined
  });
  const versionQuery = useTariffVersion(selectedVersionId);
  const blocksQuery = useTariffBlocks(selectedVersionId);
  const createTariffGroupMutation = useCreateTariffGroup();
  const createVersionMutation = useCreateTariffVersion();
  const addBlockMutation = useAddTariffBlock();
  const workflowMutation = useTariffVersionWorkflow();
  const calculationMutation = useTariffCalculation();
  const tariffGroupsReady = tariffGroupsQuery.isSuccess;

  function submitTariffGroup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tariffGroupsReady) {
      return;
    }
    createTariffGroupMutation.mutate(tariffGroupForm, {
      onSuccess: () => setTariffGroupForm({ code: "", name: "", reason: "" })
    });
  }

  function submitVersion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tariffGroupsReady) {
      return;
    }
    createVersionMutation.mutate({
      tariffGroupId: versionForm.tariffGroupId,
      effectiveDate: versionForm.effectiveDate,
      fixedCharge: requiredNumber(versionForm.fixedCharge),
      levyCharge: requiredNumber(versionForm.levyCharge),
      adminCharge: requiredNumber(versionForm.adminCharge),
      wasteCharge: requiredNumber(versionForm.wasteCharge),
      penaltyRate: requiredNumber(versionForm.penaltyRate),
      reason: versionForm.reason
    });
  }

  function submitBlock(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedVersionId) {
      return;
    }
    addBlockMutation.mutate({
      tariffVersionId: selectedVersionId,
      payload: {
        blockOrder: Number(blockForm.blockOrder),
        minM3: requiredNumber(blockForm.minM3),
        maxM3: numberOrNull(blockForm.maxM3),
        pricePerM3: requiredNumber(blockForm.pricePerM3),
        reason: blockForm.reason
      }
    });
  }

  function submitWorkflow(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    workflowMutation.mutate({
      tariffVersionId: workflowForm.tariffVersionId,
      workflow: workflowForm.workflow,
      payload: { reason: workflowForm.reason }
    });
  }

  function submitCalculation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tariffGroupsReady) {
      return;
    }
    calculationMutation.mutate({
      tariffGroupId: calculationForm.tariffGroupId,
      billingDate: calculationForm.billingDate,
      usageM3: requiredNumber(calculationForm.usageM3),
      outstandingAmount: requiredNumber(calculationForm.outstandingAmount)
    });
  }

  if (versionsQuery.isLoading || tariffGroupsQuery.isLoading) {
    return <LoadingSkeleton />;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Tarif Air"
        description="Frontend untuk tariff group, versi tarif, blok progresif, aktivasi/arsip, dan simulasi kalkulasi."
      />
      <AuthNotice authenticated={authenticated} />

      {tariffGroupsQuery.isError ? (
        <Section
          title="Status Golongan Tarif"
          description="Data referensi wajib tersedia sebelum membuat golongan, versi, atau simulasi tarif."
        >
          <div className="space-y-3">
            <ErrorState message={apiErrorMessage(tariffGroupsQuery.error, "Golongan tarif tidak tersedia.")} />
            <button type="button" className={secondaryButtonClass} onClick={() => void tariffGroupsQuery.refetch()}>
              Muat ulang golongan tarif
            </button>
          </div>
        </Section>
      ) : null}

      <section className="grid gap-4 md:grid-cols-4">
        <SummaryCard
          label="Golongan Tarif"
          value={tariffGroupsQuery.isError ? "-" : String(tariffGroupsQuery.data?.totalItems ?? 0)}
          helper={tariffGroupsQuery.isError ? "Data golongan gagal dimuat." : "Master golongan untuk sambungan dan versi tarif."}
          tone="info"
        />
        <SummaryCard label="Versi Tarif" value={String(versionsQuery.data?.totalItems ?? 0)} helper="Versi sesuai filter." />
        <SummaryCard
          label="Blok Dipilih"
          value={String(blocksQuery.data?.length ?? 0)}
          helper="Jumlah blok pada versi terpilih."
          tone="success"
        />
        <SummaryCard
          label="Total Simulasi"
          value={calculationMutation.data ? formatMoney(calculationMutation.data.total) : "-"}
          helper="Pemakaian, biaya non-air, dan denda."
          tone="warning"
        />
      </section>

      <section className="grid gap-4 xl:grid-cols-[1.2fr_.8fr]">
        <Section title="Daftar Versi Tarif">
          {versionsQuery.isError ? (
            <ErrorState message={apiErrorMessage(versionsQuery.error, "Versi tarif tidak tersedia.")} />
          ) : (
            <div className="space-y-4">
              <div className="grid gap-3 md:grid-cols-4">
                <select
                  className={inputClass}
                  value={filters.tariffGroupId}
                  onChange={(event) => setFilters((prev) => ({ ...prev, page: 0, tariffGroupId: event.target.value }))}
                  disabled={!tariffGroupsReady}
                >
                  <option value="">Semua golongan</option>
                  {(tariffGroupsQuery.data?.items ?? []).map((group) => (
                    <option key={group.id} value={group.id}>
                      {group.code} - {group.name}
                    </option>
                  ))}
                </select>
                <select
                  className={inputClass}
                  value={filters.status}
                  onChange={(event) => setFilters((prev) => ({ ...prev, page: 0, status: event.target.value }))}
                >
                  <option value="">Semua status</option>
                  {tariffVersionStatusValues.map((status) => (
                    <option key={status} value={status}>
                      {status}
                    </option>
                  ))}
                </select>
                <button type="button" className={secondaryButtonClass} onClick={() => void versionsQuery.refetch()}>
                  Muat ulang
                </button>
              </div>
              <TariffVersionTable
                versions={versionsQuery.data?.items ?? []}
                onSelect={(versionId) => {
                  setSelectedVersionId(versionId);
                  setWorkflowForm((prev) => ({ ...prev, tariffVersionId: versionId }));
                }}
              />
            </div>
          )}
        </Section>

        <Section title="Detail Versi dan Blok">
          {!selectedVersionId ? (
            <EmptyState title="Pilih versi tarif" description="Pilih versi tarif untuk membaca blok progresif." />
          ) : versionQuery.isLoading || blocksQuery.isLoading ? (
            <LoadingSkeleton />
          ) : versionQuery.isError || blocksQuery.isError ? (
            <ErrorState message="Detail versi atau blok tarif tidak tersedia." />
          ) : (
            <div className="space-y-4">
              {versionQuery.data ? (
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-bold text-slate-950">{formatDate(versionQuery.data.effectiveDate)}</p>
                    <p className="text-sm text-slate-600">Aksi: {versionQuery.data.availableActions.join(", ") || "-"}</p>
                    <p className="mt-1 text-xs font-semibold text-slate-600">
                      Tetap {formatMoney(versionQuery.data.fixedCharge)}; retribusi {formatMoney(versionQuery.data.levyCharge)}; admin {formatMoney(versionQuery.data.adminCharge)}; sampah {formatMoney(versionQuery.data.wasteCharge)}; denda {(versionQuery.data.penaltyRate * 100).toLocaleString("id-ID")}%.
                    </p>
                  </div>
                  <StatusBadge label={versionQuery.data.status} tone={statusTone(versionQuery.data.status)} />
                </div>
              ) : null}
              {(blocksQuery.data ?? []).length === 0 ? (
                <EmptyState title="Blok belum tersedia" description="Tambahkan blok progresif sebelum aktivasi." />
              ) : (
                <div className="space-y-2">
                  {(blocksQuery.data ?? []).map((block) => (
                    <div key={block.id} className="rounded-lg border border-slate-200 p-3 text-sm">
                      <p className="font-bold text-slate-950">Blok {block.blockOrder}</p>
                      <p className="text-slate-700">
                        {formatNumber(block.minM3)} - {block.maxM3 === null ? "tak terbatas" : formatNumber(block.maxM3)} m3
                      </p>
                      <p className="text-slate-700">{formatMoney(block.pricePerM3)} / m3</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </Section>
      </section>

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
              disabled={!authenticated || !tariffGroupsReady || createTariffGroupMutation.isPending}
            >
              {createTariffGroupMutation.isPending ? "Menyimpan..." : "Simpan Golongan"}
            </button>
          </div>
        </form>
      </Section>

      <section className="grid gap-4 xl:grid-cols-3">
        <Section title="Tambah Versi Tarif">
          <form className="space-y-3" onSubmit={submitVersion}>
            <Field label="Golongan tarif">
              <select
                className={inputClass}
                value={versionForm.tariffGroupId}
                onChange={(event) => setVersionForm((prev) => ({ ...prev, tariffGroupId: event.target.value }))}
                disabled={!tariffGroupsReady}
                required
              >
                <option value="">Pilih golongan</option>
                {(tariffGroupsQuery.data?.items ?? []).map((group) => (
                  <option key={group.id} value={group.id}>
                    {group.code} - {group.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Tanggal efektif">
              <input
                className={inputClass}
                type="date"
                value={versionForm.effectiveDate}
                onChange={(event) => setVersionForm((prev) => ({ ...prev, effectiveDate: event.target.value }))}
                required
              />
            </Field>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Beban tetap">
                <input className={inputClass} type="number" min="0" step="0.01" value={versionForm.fixedCharge} onChange={(event) => setVersionForm((prev) => ({ ...prev, fixedCharge: event.target.value }))} required />
              </Field>
              <Field label="Retribusi">
                <input className={inputClass} type="number" min="0" step="0.01" value={versionForm.levyCharge} onChange={(event) => setVersionForm((prev) => ({ ...prev, levyCharge: event.target.value }))} required />
              </Field>
              <Field label="Administrasi">
                <input className={inputClass} type="number" min="0" step="0.01" value={versionForm.adminCharge} onChange={(event) => setVersionForm((prev) => ({ ...prev, adminCharge: event.target.value }))} required />
              </Field>
              <Field label="Sampah">
                <input className={inputClass} type="number" min="0" step="0.01" value={versionForm.wasteCharge} onChange={(event) => setVersionForm((prev) => ({ ...prev, wasteCharge: event.target.value }))} required />
              </Field>
            </div>
            <Field label="Tarif denda (desimal)">
              <input className={inputClass} type="number" min="0" max="1" step="0.000001" value={versionForm.penaltyRate} onChange={(event) => setVersionForm((prev) => ({ ...prev, penaltyRate: event.target.value }))} required />
            </Field>
            <Field label="Alasan audit">
              <textarea
                className={inputClass}
                value={versionForm.reason}
                onChange={(event) => setVersionForm((prev) => ({ ...prev, reason: event.target.value }))}
                required
              />
            </Field>
            <MutationError error={createVersionMutation.error} fallback="Gagal membuat versi tarif." />
            <button type="submit" className={primaryButtonClass} disabled={!authenticated || !tariffGroupsReady || createVersionMutation.isPending}>
              Simpan Versi
            </button>
          </form>
        </Section>

        <Section title="Tambah Blok Tarif">
          <form className="space-y-3" onSubmit={submitBlock}>
            <Field label="Versi terpilih">
              <input className={inputClass} value={selectedVersionId ?? ""} readOnly required />
            </Field>
            <Field label="Urutan blok">
              <input
                className={inputClass}
                type="number"
                min="1"
                value={blockForm.blockOrder}
                onChange={(event) => setBlockForm((prev) => ({ ...prev, blockOrder: event.target.value }))}
                required
              />
            </Field>
            <div className="grid gap-3 md:grid-cols-2">
              <Field label="Min m3">
                <input
                  className={inputClass}
                  type="number"
                  step="0.001"
                  value={blockForm.minM3}
                  onChange={(event) => setBlockForm((prev) => ({ ...prev, minM3: event.target.value }))}
                  required
                />
              </Field>
              <Field label="Max m3">
                <input
                  className={inputClass}
                  type="number"
                  step="0.001"
                  value={blockForm.maxM3}
                  onChange={(event) => setBlockForm((prev) => ({ ...prev, maxM3: event.target.value }))}
                />
              </Field>
            </div>
            <Field label="Harga per m3">
              <input
                className={inputClass}
                type="number"
                step="0.01"
                value={blockForm.pricePerM3}
                onChange={(event) => setBlockForm((prev) => ({ ...prev, pricePerM3: event.target.value }))}
                required
              />
            </Field>
            <Field label="Alasan audit">
              <textarea
                className={inputClass}
                value={blockForm.reason}
                onChange={(event) => setBlockForm((prev) => ({ ...prev, reason: event.target.value }))}
                required
              />
            </Field>
            <MutationError error={addBlockMutation.error} fallback="Gagal menambahkan blok tarif." />
            <button type="submit" className={primaryButtonClass} disabled={!authenticated || !selectedVersionId || addBlockMutation.isPending}>
              Tambah Blok
            </button>
          </form>
        </Section>

        <Section title="Workflow dan Simulasi">
          <form className="space-y-3" onSubmit={submitWorkflow}>
            <EntitySelector value={workflowForm.tariffVersionId} selectedOption={selectedEntityOption(versionsQuery.data?.items ?? [], workflowForm.tariffVersionId, (version) => ({ id: version.id, label: `Efektif ${formatDate(version.effectiveDate)}`, status: version.status }))} onChange={(value) => setWorkflowForm((prev) => ({ ...prev, tariffVersionId: value }))} loadOptions={tariffVersionLookupLoader} label="Versi tarif" ariaLabel="Pilih versi tarif untuk workflow" placeholder="Cari tanggal efektif versi tarif" required invalid={!workflowForm.tariffVersionId} />
            <Field label="Aksi">
              <select
                className={inputClass}
                value={workflowForm.workflow}
                onChange={(event) => setWorkflowForm((prev) => ({ ...prev, workflow: event.target.value as TariffVersionWorkflow }))}
              >
                <option value="activate">Aktifkan</option>
                <option value="archive">Arsipkan</option>
              </select>
            </Field>
            <Field label="Alasan audit">
              <textarea
                className={inputClass}
                value={workflowForm.reason}
                onChange={(event) => setWorkflowForm((prev) => ({ ...prev, reason: event.target.value }))}
                required
              />
            </Field>
            <MutationError error={workflowMutation.error} fallback="Workflow tarif gagal." />
            <button
              type="submit"
              className={workflowForm.workflow === "archive" ? dangerButtonClass : primaryButtonClass}
              disabled={!authenticated || workflowMutation.isPending}
            >
              Jalankan Workflow
            </button>
          </form>

          <form className="mt-5 space-y-3 border-t border-slate-200 pt-5" onSubmit={submitCalculation}>
            <Field label="Golongan tarif">
              <select
                className={inputClass}
                value={calculationForm.tariffGroupId}
                onChange={(event) => setCalculationForm((prev) => ({ ...prev, tariffGroupId: event.target.value }))}
                disabled={!tariffGroupsReady}
                required
              >
                <option value="">Pilih golongan</option>
                {(tariffGroupsQuery.data?.items ?? []).map((group) => (
                  <option key={group.id} value={group.id}>
                    {group.code} - {group.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Tanggal billing">
              <input
                className={inputClass}
                type="date"
                value={calculationForm.billingDate}
                onChange={(event) => setCalculationForm((prev) => ({ ...prev, billingDate: event.target.value }))}
                required
              />
            </Field>
            <Field label="Pemakaian m3">
              <input
                className={inputClass}
                type="number"
                step="0.001"
                value={calculationForm.usageM3}
                onChange={(event) => setCalculationForm((prev) => ({ ...prev, usageM3: event.target.value }))}
                required
              />
            </Field>
            <Field label="Tunggakan sebelumnya">
              <input
                className={inputClass}
                type="number"
                min="0"
                step="0.01"
                value={calculationForm.outstandingAmount}
                onChange={(event) => setCalculationForm((prev) => ({ ...prev, outstandingAmount: event.target.value }))}
                required
              />
            </Field>
            <MutationError error={calculationMutation.error} fallback="Kalkulasi tarif gagal." />
            <button type="submit" className={secondaryButtonClass} disabled={!tariffGroupsReady || calculationMutation.isPending}>
              Hitung Tarif
            </button>
            {calculationMutation.data ? (
              <div className="grid grid-cols-2 gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs font-semibold text-slate-700">
                <span>Air</span><span className="text-right">{formatMoney(calculationMutation.data.usageCharge)}</span>
                <span>Beban tetap</span><span className="text-right">{formatMoney(calculationMutation.data.fixedCharge)}</span>
                <span>Retribusi</span><span className="text-right">{formatMoney(calculationMutation.data.levyCharge)}</span>
                <span>Administrasi</span><span className="text-right">{formatMoney(calculationMutation.data.adminCharge)}</span>
                <span>Sampah</span><span className="text-right">{formatMoney(calculationMutation.data.wasteCharge)}</span>
                <span>Denda</span><span className="text-right">{formatMoney(calculationMutation.data.penaltyCharge)}</span>
                <span className="font-black text-slate-950">Total</span><span className="text-right font-black text-slate-950">{formatMoney(calculationMutation.data.total)}</span>
              </div>
            ) : null}
          </form>
        </Section>
      </section>
    </div>
  );
}

export function ReceivableAgingWorkspace() {
  const currentUserQuery = useCurrentUser();
  const authenticated = currentUserQuery.data?.authenticated ?? false;
  const [filters, setFilters] = useState({ page: 0, size: 10, period: "" });
  const [selectedSnapshotId, setSelectedSnapshotId] = useState<string | null>(null);
  const [lookupPeriod, setLookupPeriod] = useState("");
  const [submittedLookupPeriod, setSubmittedLookupPeriod] = useState<string | null>(null);
  const [generateForm, setGenerateForm] = useState({ period: "", asOfDate: "", reason: "" });

  const snapshotsQuery = useReceivableAgingSnapshots({
    page: filters.page,
    size: filters.size,
    period: filters.period || undefined
  });
  const snapshotQuery = useReceivableAgingSnapshot(selectedSnapshotId);
  const byPeriodQuery = useReceivableAgingSnapshotByPeriod(submittedLookupPeriod);
  const generateMutation = useGenerateReceivableAgingSnapshot();

  function submitGenerate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    generateMutation.mutate(generateForm);
  }

  if (snapshotsQuery.isLoading) {
    return <LoadingSkeleton />;
  }

  const selectedSnapshot = snapshotQuery.data ?? byPeriodQuery.data ?? null;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Aging Piutang"
        description="Frontend untuk generate snapshot, daftar snapshot, detail snapshot, dan lookup by-period."
      />
      <AuthNotice authenticated={authenticated} />

      <section className="grid gap-4 md:grid-cols-3">
        <SummaryCard label="Snapshot" value={String(snapshotsQuery.data?.totalItems ?? 0)} helper="Total snapshot sesuai filter." />
        <SummaryCard
          label="Outstanding Terpilih"
          value={selectedSnapshot ? formatMoney(selectedSnapshot.totalOutstandingAmount) : "-"}
          helper="Total piutang snapshot yang sedang dibaca."
          tone="warning"
        />
        <SummaryCard
          label="Generated"
          value={selectedSnapshot ? formatDateTime(selectedSnapshot.generatedAt) : "-"}
          helper="Waktu snapshot terakhir dipilih."
          tone="success"
        />
      </section>

      <section className="grid gap-4 xl:grid-cols-[1.2fr_.8fr]">
        <Section title="Daftar Snapshot">
          {snapshotsQuery.isError ? (
            <ErrorState message={apiErrorMessage(snapshotsQuery.error, "Snapshot aging piutang tidak tersedia.")} />
          ) : (
            <div className="space-y-4">
              <div className="grid gap-3 md:grid-cols-[1fr_120px]">
                <input
                  className={inputClass}
                  placeholder="YYYY-MM"
                  value={filters.period}
                  onChange={(event) => setFilters((prev) => ({ ...prev, page: 0, period: event.target.value }))}
                />
                <button type="button" className={secondaryButtonClass} onClick={() => void snapshotsQuery.refetch()}>
                  Muat ulang
                </button>
              </div>
              {(snapshotsQuery.data?.items ?? []).length === 0 ? (
                <EmptyState title="Snapshot belum tersedia" description="Generate aging piutang untuk periode laporan." />
              ) : (
                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y divide-slate-200 text-sm">
                    <thead className="bg-slate-50">
                      <tr>
                        <th className="px-4 py-3 text-left font-bold text-slate-700">Periode</th>
                        <th className="px-4 py-3 text-right font-bold text-slate-700">Current</th>
                        <th className="px-4 py-3 text-right font-bold text-slate-700">30</th>
                        <th className="px-4 py-3 text-right font-bold text-slate-700">60</th>
                        <th className="px-4 py-3 text-right font-bold text-slate-700">90+</th>
                        <th className="px-4 py-3 text-left font-bold text-slate-700">Aksi</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {(snapshotsQuery.data?.items ?? []).map((snapshot) => (
                        <tr key={snapshot.id} className="hover:bg-teal-50">
                          <td className="whitespace-nowrap px-4 py-3 font-bold text-slate-950">{snapshot.period}</td>
                          <td className="whitespace-nowrap px-4 py-3 text-right text-slate-700">
                            {formatMoney(snapshot.currentAmount)}
                          </td>
                          <td className="whitespace-nowrap px-4 py-3 text-right text-slate-700">
                            {formatMoney(snapshot.bucket30Amount)}
                          </td>
                          <td className="whitespace-nowrap px-4 py-3 text-right text-slate-700">
                            {formatMoney(snapshot.bucket60Amount)}
                          </td>
                          <td className="whitespace-nowrap px-4 py-3 text-right text-slate-700">
                            {formatMoney(snapshot.bucketOver90Amount)}
                          </td>
                          <td className="whitespace-nowrap px-4 py-3">
                            <button type="button" className={secondaryButtonClass} onClick={() => setSelectedSnapshotId(snapshot.id)}>
                              Detail
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </Section>

        <Section title="Generate dan Lookup">
          <form className="space-y-3" onSubmit={submitGenerate}>
            <Field label="Periode">
              <input
                className={inputClass}
                placeholder="YYYY-MM"
                value={generateForm.period}
                onChange={(event) => setGenerateForm((prev) => ({ ...prev, period: event.target.value }))}
                required
              />
            </Field>
            <Field label="Tanggal acuan">
              <input
                className={inputClass}
                type="date"
                value={generateForm.asOfDate}
                onChange={(event) => setGenerateForm((prev) => ({ ...prev, asOfDate: event.target.value }))}
                required
              />
            </Field>
            <Field label="Alasan audit">
              <textarea
                className={inputClass}
                value={generateForm.reason}
                onChange={(event) => setGenerateForm((prev) => ({ ...prev, reason: event.target.value }))}
                required
              />
            </Field>
            <MutationError error={generateMutation.error} fallback="Gagal generate aging piutang." />
            <button type="submit" className={primaryButtonClass} disabled={!authenticated || generateMutation.isPending}>
              Generate Snapshot
            </button>
          </form>

          <div className="mt-5 space-y-3 border-t border-slate-200 pt-5">
            <Field label="Lookup by period">
              <input
                className={inputClass}
                placeholder="YYYY-MM"
                value={lookupPeriod}
                onChange={(event) => setLookupPeriod(event.target.value)}
              />
            </Field>
            <button type="button" className={secondaryButtonClass} onClick={() => setSubmittedLookupPeriod(lookupPeriod)}>
              Cari Periode
            </button>
            {byPeriodQuery.isError ? <ErrorState message={apiErrorMessage(byPeriodQuery.error, "Snapshot periode tidak ditemukan.")} /> : null}
          </div>
        </Section>
      </section>

      <Section title="Detail Snapshot">
        {snapshotQuery.isLoading || byPeriodQuery.isLoading ? (
          <LoadingSkeleton />
        ) : selectedSnapshot ? (
          <div className="grid gap-3 md:grid-cols-5">
            <SummaryCard label="Current" value={formatMoney(selectedSnapshot.currentAmount)} helper={selectedSnapshot.period} />
            <SummaryCard label="30 Hari" value={formatMoney(selectedSnapshot.bucket30Amount)} helper={selectedSnapshot.period} />
            <SummaryCard label="60 Hari" value={formatMoney(selectedSnapshot.bucket60Amount)} helper={selectedSnapshot.period} />
            <SummaryCard label="90 Hari" value={formatMoney(selectedSnapshot.bucket90Amount)} helper={selectedSnapshot.period} />
            <SummaryCard label=">90 Hari" value={formatMoney(selectedSnapshot.bucketOver90Amount)} helper={selectedSnapshot.period} />
          </div>
        ) : (
          <EmptyState title="Belum ada snapshot dipilih" description="Pilih snapshot dari daftar atau cari berdasarkan periode." />
        )}
      </Section>
    </div>
  );
}

export function TrialBalanceWorkspace() {
  const today = new Date().toISOString().slice(0, 10);
  const firstDay = `${today.slice(0, 8)}01`;
  const [filters, setFilters] = useState({ fromDate: firstDay, toDate: today });
  const reportQuery = useTrialBalance(filters, Boolean(filters.fromDate && filters.toDate));

  const totals = useMemo(() => {
    const lines = reportQuery.data?.lines ?? [];
    return {
      accounts: lines.length,
      debit: reportQuery.data?.totalDebitBalance ?? 0,
      credit: reportQuery.data?.totalCreditBalance ?? 0
    };
  }, [reportQuery.data]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Laporan Neraca Saldo"
        description="Frontend untuk `/api/reports/trial-balance`, berbasis ledger posted dan menampilkan status balance."
      />

      <section className="grid gap-4 md:grid-cols-4">
        <SummaryCard label="Akun" value={String(totals.accounts)} helper="Jumlah akun dalam laporan." />
        <SummaryCard label="Debit" value={formatMoney(totals.debit)} helper="Total debit balance." tone="success" />
        <SummaryCard label="Kredit" value={formatMoney(totals.credit)} helper="Total credit balance." tone="warning" />
        <SummaryCard
          label="Status"
          value={reportQuery.data?.balanced ? "Balance" : "Belum balance"}
          helper="Validasi debit-kredit laporan."
          tone={reportQuery.data?.balanced ? "success" : "danger"}
        />
      </section>

      <Section title="Filter Laporan">
        <div className="grid gap-3 md:grid-cols-[180px_180px_120px]">
          <Field label="Dari tanggal">
            <input
              className={inputClass}
              type="date"
              value={filters.fromDate}
              onChange={(event) => setFilters((prev) => ({ ...prev, fromDate: event.target.value }))}
            />
          </Field>
          <Field label="Sampai tanggal">
            <input
              className={inputClass}
              type="date"
              value={filters.toDate}
              onChange={(event) => setFilters((prev) => ({ ...prev, toDate: event.target.value }))}
            />
          </Field>
          <div className="flex items-end">
            <button type="button" className={secondaryButtonClass} onClick={() => void reportQuery.refetch()}>
              Muat ulang
            </button>
          </div>
        </div>
      </Section>

      <Section title="Neraca Saldo">
        {reportQuery.isLoading ? (
          <LoadingSkeleton />
        ) : reportQuery.isError ? (
          <ErrorState message={apiErrorMessage(reportQuery.error, "Neraca saldo tidak tersedia.")} />
        ) : !reportQuery.data || reportQuery.data.lines.length === 0 ? (
          <EmptyState title="Belum ada saldo posted" description="Laporan akan terisi setelah jurnal posted membentuk ledger." />
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50">
                <tr>
                  <th className="px-4 py-3 text-left font-bold text-slate-700">Kode</th>
                  <th className="px-4 py-3 text-left font-bold text-slate-700">Akun</th>
                  <th className="px-4 py-3 text-left font-bold text-slate-700">Tipe</th>
                  <th className="px-4 py-3 text-right font-bold text-slate-700">Debit Mutasi</th>
                  <th className="px-4 py-3 text-right font-bold text-slate-700">Kredit Mutasi</th>
                  <th className="px-4 py-3 text-right font-bold text-slate-700">Debit Saldo</th>
                  <th className="px-4 py-3 text-right font-bold text-slate-700">Kredit Saldo</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {reportQuery.data.lines.map((line) => (
                  <tr key={line.accountId} className="hover:bg-teal-50">
                    <td className="whitespace-nowrap px-4 py-3 font-bold text-slate-950">{line.accountCode}</td>
                    <td className="px-4 py-3 text-slate-700">{line.accountName}</td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <StatusBadge label={line.accountType} tone={line.normalBalance === "DEBIT" ? "info" : "warning"} />
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right text-slate-700">{formatMoney(line.debitTotal)}</td>
                    <td className="whitespace-nowrap px-4 py-3 text-right text-slate-700">{formatMoney(line.creditTotal)}</td>
                    <td className="whitespace-nowrap px-4 py-3 text-right font-bold text-slate-950">
                      {formatMoney(line.debitBalance)}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right font-bold text-slate-950">
                      {formatMoney(line.creditBalance)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Section>
    </div>
  );
}
