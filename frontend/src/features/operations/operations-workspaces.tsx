"use client";

import { type FormEvent, useMemo, useState } from "react";
import { PageHeader } from "@/components/common/page-header";
import { EmptyState } from "@/components/state/empty-state";
import { ErrorState } from "@/components/state/error-state";
import { LoadingSkeleton } from "@/components/state/loading-skeleton";
import { StatusBadge } from "@/components/status/status-badge";
import { apiErrorMessage } from "@/lib/api/client";
import { useCurrentUser } from "@/features/auth/use-current-user";
import {
  connectionStatusValues,
  customerStatusValues,
  meterReadingStatusValues,
  tariffVersionStatusValues,
  type Connection,
  type ConnectionWorkflow,
  type CustomerSummary,
  type MeterReading,
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

export function MasterDataWorkspace() {
  const currentUserQuery = useCurrentUser();
  const authenticated = currentUserQuery.data?.authenticated ?? false;
  const [customerFilters, setCustomerFilters] = useState({ page: 0, size: 10, search: "", status: "" });
  const [connectionFilters, setConnectionFilters] = useState({ page: 0, size: 10, customerId: "", status: "" });
  const [selectedCustomerId, setSelectedCustomerId] = useState<string | null>(null);
  const [selectedConnectionId, setSelectedConnectionId] = useState<string | null>(null);
  const [customerForm, setCustomerForm] = useState({
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
  const [tariffGroupForm, setTariffGroupForm] = useState({ code: "", name: "", reason: "" });
  const [connectionForm, setConnectionForm] = useState({
    customerId: "",
    tariffGroupId: "",
    connectionNumber: "",
    meterNumber: "",
    installedAt: "",
    reason: ""
  });
  const [connectionWorkflow, setConnectionWorkflow] = useState<{
    connectionId: string;
    workflow: ConnectionWorkflow;
    reason: string;
  }>({ connectionId: "", workflow: "activate", reason: "" });

  const customersQuery = useCustomers({
    page: customerFilters.page,
    size: customerFilters.size,
    search: customerFilters.search || undefined,
    status: customerFilters.status ? (customerFilters.status as "ACTIVE" | "INACTIVE" | "BLACKLISTED") : undefined
  });
  const customerQuery = useCustomer(selectedCustomerId);
  const tariffGroupsQuery = useTariffGroups({ page: 0, size: 100 });
  const connectionsQuery = useConnections({
    page: connectionFilters.page,
    size: connectionFilters.size,
    customerId: connectionFilters.customerId || undefined,
    status: connectionFilters.status ? (connectionFilters.status as "DRAFT" | "ACTIVE" | "SUSPENDED" | "TERMINATED") : undefined
  });
  const connectionQuery = useConnection(selectedConnectionId);
  const createCustomerMutation = useCreateCustomer();
  const createTariffGroupMutation = useCreateTariffGroup();
  const createConnectionMutation = useCreateConnection();
  const connectionWorkflowMutation = useConnectionWorkflow();

  function submitCustomer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    createCustomerMutation.mutate({
      customerNumber: customerForm.customerNumber,
      fullName: customerForm.fullName,
      identityNumber: nullIfBlank(customerForm.identityNumber),
      phoneNumber: nullIfBlank(customerForm.phoneNumber),
      addressLine: customerForm.addressLine,
      areaCode: customerForm.areaCode,
      latitude: numberOrNull(customerForm.latitude),
      longitude: numberOrNull(customerForm.longitude),
      reason: customerForm.reason
    });
  }

  function submitTariffGroup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    createTariffGroupMutation.mutate(tariffGroupForm);
  }

  function submitConnection(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    createConnectionMutation.mutate({
      customerId: connectionForm.customerId,
      tariffGroupId: connectionForm.tariffGroupId,
      connectionNumber: connectionForm.connectionNumber,
      meterNumber: connectionForm.meterNumber,
      installedAt: nullIfBlank(connectionForm.installedAt),
      reason: connectionForm.reason
    });
  }

  function submitConnectionWorkflow(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    connectionWorkflowMutation.mutate({
      connectionId: connectionWorkflow.connectionId,
      workflow: connectionWorkflow.workflow,
      payload: { reason: connectionWorkflow.reason }
    });
  }

  if (customersQuery.isLoading || tariffGroupsQuery.isLoading || connectionsQuery.isLoading) {
    return <LoadingSkeleton />;
  }

  return (
    <main className="space-y-6">
      <PageHeader
        title="Master Pelanggan dan Sambungan"
        description="Frontend untuk customer, tariff group, connection list/detail, pembuatan data, dan workflow aktivasi/suspend/terminasi."
      />
      <AuthNotice authenticated={authenticated} />

      <section className="grid gap-4 md:grid-cols-3">
        <SummaryCard
          label="Pelanggan"
          value={String(customersQuery.data?.totalItems ?? 0)}
          helper="Total pelanggan sesuai filter aktif."
          tone="info"
        />
        <SummaryCard
          label="Sambungan"
          value={String(connectionsQuery.data?.totalItems ?? 0)}
          helper="Total sambungan sesuai filter aktif."
          tone="success"
        />
        <SummaryCard
          label="Golongan Tarif"
          value={String(tariffGroupsQuery.data?.totalItems ?? 0)}
          helper="Master tariff group untuk sambungan dan tarif."
          tone="warning"
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
                  value={customerFilters.search}
                  onChange={(event) => setCustomerFilters((prev) => ({ ...prev, page: 0, search: event.target.value }))}
                />
                <select
                  className={inputClass}
                  value={customerFilters.status}
                  onChange={(event) => setCustomerFilters((prev) => ({ ...prev, page: 0, status: event.target.value }))}
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

      <section className="grid gap-4 xl:grid-cols-3">
        <Section title="Tambah Pelanggan">
          <form className="space-y-3" onSubmit={submitCustomer}>
            <Field label="Nomor pelanggan">
              <input
                className={inputClass}
                value={customerForm.customerNumber}
                onChange={(event) => setCustomerForm((prev) => ({ ...prev, customerNumber: event.target.value }))}
                required
              />
            </Field>
            <Field label="Nama lengkap">
              <input
                className={inputClass}
                value={customerForm.fullName}
                onChange={(event) => setCustomerForm((prev) => ({ ...prev, fullName: event.target.value }))}
                required
              />
            </Field>
            <Field label="Nomor identitas">
              <input
                className={inputClass}
                value={customerForm.identityNumber}
                onChange={(event) => setCustomerForm((prev) => ({ ...prev, identityNumber: event.target.value }))}
              />
            </Field>
            <Field label="Telepon">
              <input
                className={inputClass}
                value={customerForm.phoneNumber}
                onChange={(event) => setCustomerForm((prev) => ({ ...prev, phoneNumber: event.target.value }))}
              />
            </Field>
            <Field label="Alamat">
              <textarea
                className={inputClass}
                value={customerForm.addressLine}
                onChange={(event) => setCustomerForm((prev) => ({ ...prev, addressLine: event.target.value }))}
                required
              />
            </Field>
            <div className="grid gap-3 md:grid-cols-3">
              <Field label="Area">
                <input
                  className={inputClass}
                  value={customerForm.areaCode}
                  onChange={(event) => setCustomerForm((prev) => ({ ...prev, areaCode: event.target.value }))}
                  required
                />
              </Field>
              <Field label="Latitude">
                <input
                  className={inputClass}
                  type="number"
                  step="0.0000001"
                  value={customerForm.latitude}
                  onChange={(event) => setCustomerForm((prev) => ({ ...prev, latitude: event.target.value }))}
                />
              </Field>
              <Field label="Longitude">
                <input
                  className={inputClass}
                  type="number"
                  step="0.0000001"
                  value={customerForm.longitude}
                  onChange={(event) => setCustomerForm((prev) => ({ ...prev, longitude: event.target.value }))}
                />
              </Field>
            </div>
            <Field label="Alasan audit">
              <textarea
                className={inputClass}
                value={customerForm.reason}
                onChange={(event) => setCustomerForm((prev) => ({ ...prev, reason: event.target.value }))}
                required
              />
            </Field>
            <MutationError error={createCustomerMutation.error} fallback="Gagal membuat pelanggan." />
            <button type="submit" className={primaryButtonClass} disabled={!authenticated || createCustomerMutation.isPending}>
              Simpan Pelanggan
            </button>
          </form>
        </Section>

        <Section title="Tambah Golongan Tarif">
          <form className="space-y-3" onSubmit={submitTariffGroup}>
            <Field label="Kode">
              <input
                className={inputClass}
                value={tariffGroupForm.code}
                onChange={(event) => setTariffGroupForm((prev) => ({ ...prev, code: event.target.value }))}
                required
              />
            </Field>
            <Field label="Nama">
              <input
                className={inputClass}
                value={tariffGroupForm.name}
                onChange={(event) => setTariffGroupForm((prev) => ({ ...prev, name: event.target.value }))}
                required
              />
            </Field>
            <Field label="Alasan audit">
              <textarea
                className={inputClass}
                value={tariffGroupForm.reason}
                onChange={(event) => setTariffGroupForm((prev) => ({ ...prev, reason: event.target.value }))}
                required
              />
            </Field>
            <MutationError error={createTariffGroupMutation.error} fallback="Gagal membuat golongan tarif." />
            <button
              type="submit"
              className={primaryButtonClass}
              disabled={!authenticated || createTariffGroupMutation.isPending}
            >
              Simpan Golongan
            </button>
          </form>
        </Section>

        <Section title="Tambah Sambungan">
          <form className="space-y-3" onSubmit={submitConnection}>
            <Field label="Pelanggan">
              <select
                className={inputClass}
                value={connectionForm.customerId}
                onChange={(event) => setConnectionForm((prev) => ({ ...prev, customerId: event.target.value }))}
                required
              >
                <option value="">Pilih pelanggan</option>
                {(customersQuery.data?.items ?? []).map((customer) => (
                  <option key={customer.id} value={customer.id}>
                    {customer.customerNumber} - {customer.fullName}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Golongan tarif">
              <select
                className={inputClass}
                value={connectionForm.tariffGroupId}
                onChange={(event) => setConnectionForm((prev) => ({ ...prev, tariffGroupId: event.target.value }))}
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
            <Field label="Nomor sambungan">
              <input
                className={inputClass}
                value={connectionForm.connectionNumber}
                onChange={(event) => setConnectionForm((prev) => ({ ...prev, connectionNumber: event.target.value }))}
                required
              />
            </Field>
            <Field label="Nomor meter">
              <input
                className={inputClass}
                value={connectionForm.meterNumber}
                onChange={(event) => setConnectionForm((prev) => ({ ...prev, meterNumber: event.target.value }))}
                required
              />
            </Field>
            <Field label="Tanggal pasang">
              <input
                className={inputClass}
                type="date"
                value={connectionForm.installedAt}
                onChange={(event) => setConnectionForm((prev) => ({ ...prev, installedAt: event.target.value }))}
              />
            </Field>
            <Field label="Alasan audit">
              <textarea
                className={inputClass}
                value={connectionForm.reason}
                onChange={(event) => setConnectionForm((prev) => ({ ...prev, reason: event.target.value }))}
                required
              />
            </Field>
            <MutationError error={createConnectionMutation.error} fallback="Gagal membuat sambungan." />
            <button type="submit" className={primaryButtonClass} disabled={!authenticated || createConnectionMutation.isPending}>
              Simpan Sambungan
            </button>
          </form>
        </Section>
      </section>

      <section className="grid gap-4 xl:grid-cols-[1.4fr_.8fr]">
        <Section title="Daftar Sambungan">
          {connectionsQuery.isError ? (
            <ErrorState message={apiErrorMessage(connectionsQuery.error, "Sambungan tidak tersedia.")} />
          ) : (
            <div className="space-y-4">
              <div className="grid gap-3 md:grid-cols-[1fr_180px_120px]">
                <input
                  className={inputClass}
                  placeholder="Filter customer ID"
                  value={connectionFilters.customerId}
                  onChange={(event) =>
                    setConnectionFilters((prev) => ({ ...prev, page: 0, customerId: event.target.value }))
                  }
                />
                <select
                  className={inputClass}
                  value={connectionFilters.status}
                  onChange={(event) => setConnectionFilters((prev) => ({ ...prev, page: 0, status: event.target.value }))}
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
                  setConnectionWorkflow((prev) => ({ ...prev, connectionId }));
                }}
              />
              <PageCount page={connectionsQuery.data?.page ?? 0} totalPages={connectionsQuery.data?.totalPages ?? 0} />
            </div>
          )}
        </Section>

        <Section title="Workflow Sambungan">
          <form className="space-y-3" onSubmit={submitConnectionWorkflow}>
            <Field label="ID Sambungan">
              <input
                className={inputClass}
                value={connectionWorkflow.connectionId}
                onChange={(event) => setConnectionWorkflow((prev) => ({ ...prev, connectionId: event.target.value }))}
                required
              />
            </Field>
            <Field label="Aksi">
              <select
                className={inputClass}
                value={connectionWorkflow.workflow}
                onChange={(event) =>
                  setConnectionWorkflow((prev) => ({ ...prev, workflow: event.target.value as ConnectionWorkflow }))
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
                value={connectionWorkflow.reason}
                onChange={(event) => setConnectionWorkflow((prev) => ({ ...prev, reason: event.target.value }))}
                required
              />
            </Field>
            <MutationError error={connectionWorkflowMutation.error} fallback="Workflow sambungan gagal." />
            <button
              type="submit"
              className={connectionWorkflow.workflow === "terminate" ? dangerButtonClass : primaryButtonClass}
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
    </main>
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
    status: readingFilters.status ? (readingFilters.status as "DRAFT" | "SUBMITTED" | "VERIFIED" | "REJECTED") : undefined
  });
  const readingQuery = useMeterReading(selectedReadingId);
  const createRouteMutation = useCreateMeterRoute();
  const createReadingMutation = useCreateMeterReading();
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

  if (routesQuery.isLoading || readingsQuery.isLoading) {
    return <LoadingSkeleton />;
  }

  return (
    <main className="space-y-6">
      <PageHeader
        title="Baca Meter"
        description="Frontend untuk meter route, input meter reading, detail reading, dan workflow submit/verify/reject."
      />
      <AuthNotice authenticated={authenticated} />

      <section className="grid gap-4 md:grid-cols-3">
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
                <input
                  className={inputClass}
                  placeholder="Route ID"
                  value={readingFilters.routeId}
                  onChange={(event) => setReadingFilters((prev) => ({ ...prev, page: 0, routeId: event.target.value }))}
                />
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
            <Field label="ID Reading">
              <input
                className={inputClass}
                value={workflowForm.readingId}
                onChange={(event) => setWorkflowForm((prev) => ({ ...prev, readingId: event.target.value }))}
                required
              />
            </Field>
            <Field label="Aksi">
              <select
                className={inputClass}
                value={workflowForm.workflow}
                onChange={(event) => setWorkflowForm((prev) => ({ ...prev, workflow: event.target.value as MeterReadingWorkflow }))}
              >
                <option value="submit">Submit</option>
                <option value="verify">Verifikasi</option>
                <option value="reject">Tolak</option>
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
                <p>Aksi tersedia: {readingQuery.data.availableActions.join(", ") || "-"}</p>
              </div>
            ) : null}
          </div>
        </Section>
      </section>

      <Section title="Input Baca Meter">
        <form className="grid gap-3 lg:grid-cols-4" onSubmit={submitReading}>
          <Field label="Connection ID">
            <input
              className={inputClass}
              value={readingForm.connectionId}
              onChange={(event) => setReadingForm((prev) => ({ ...prev, connectionId: event.target.value }))}
              required
            />
          </Field>
          <Field label="Rute">
            <select
              className={inputClass}
              value={readingForm.routeId}
              onChange={(event) => setReadingForm((prev) => ({ ...prev, routeId: event.target.value }))}
              required
            >
              <option value="">Pilih rute</option>
              {(routesQuery.data?.items ?? []).map((route) => (
                <option key={route.id} value={route.id}>
                  {route.routeCode} - {route.name}
                </option>
              ))}
            </select>
          </Field>
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
    </main>
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
  const [versionForm, setVersionForm] = useState({ tariffGroupId: "", effectiveDate: "", reason: "" });
  const [blockForm, setBlockForm] = useState({ blockOrder: "1", minM3: "0", maxM3: "", pricePerM3: "", reason: "" });
  const [workflowForm, setWorkflowForm] = useState<{ tariffVersionId: string; workflow: TariffVersionWorkflow; reason: string }>({
    tariffVersionId: "",
    workflow: "activate",
    reason: ""
  });
  const [calculationForm, setCalculationForm] = useState({ tariffGroupId: "", billingDate: "", usageM3: "" });

  const tariffGroupsQuery = useTariffGroups({ page: 0, size: 100 });
  const versionsQuery = useTariffVersions({
    page: filters.page,
    size: filters.size,
    tariffGroupId: filters.tariffGroupId || undefined,
    status: filters.status ? (filters.status as "DRAFT" | "ACTIVE" | "ARCHIVED") : undefined
  });
  const versionQuery = useTariffVersion(selectedVersionId);
  const blocksQuery = useTariffBlocks(selectedVersionId);
  const createVersionMutation = useCreateTariffVersion();
  const addBlockMutation = useAddTariffBlock();
  const workflowMutation = useTariffVersionWorkflow();
  const calculationMutation = useTariffCalculation();

  function submitVersion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    createVersionMutation.mutate(versionForm);
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
    calculationMutation.mutate({
      tariffGroupId: calculationForm.tariffGroupId,
      billingDate: calculationForm.billingDate,
      usageM3: requiredNumber(calculationForm.usageM3)
    });
  }

  if (versionsQuery.isLoading || tariffGroupsQuery.isLoading) {
    return <LoadingSkeleton />;
  }

  return (
    <main className="space-y-6">
      <PageHeader
        title="Tarif Air"
        description="Frontend untuk tariff group, versi tarif, blok progresif, aktivasi/arsip, dan simulasi kalkulasi."
      />
      <AuthNotice authenticated={authenticated} />

      <section className="grid gap-4 md:grid-cols-3">
        <SummaryCard label="Versi Tarif" value={String(versionsQuery.data?.totalItems ?? 0)} helper="Versi sesuai filter." />
        <SummaryCard
          label="Blok Dipilih"
          value={String(blocksQuery.data?.length ?? 0)}
          helper="Jumlah blok pada versi terpilih."
          tone="success"
        />
        <SummaryCard
          label="Subtotal Simulasi"
          value={calculationMutation.data ? formatMoney(calculationMutation.data.subtotal) : "-"}
          helper="Hasil kalkulasi tarif aktif."
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

      <section className="grid gap-4 xl:grid-cols-3">
        <Section title="Tambah Versi Tarif">
          <form className="space-y-3" onSubmit={submitVersion}>
            <Field label="Golongan tarif">
              <select
                className={inputClass}
                value={versionForm.tariffGroupId}
                onChange={(event) => setVersionForm((prev) => ({ ...prev, tariffGroupId: event.target.value }))}
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
            <Field label="Alasan audit">
              <textarea
                className={inputClass}
                value={versionForm.reason}
                onChange={(event) => setVersionForm((prev) => ({ ...prev, reason: event.target.value }))}
                required
              />
            </Field>
            <MutationError error={createVersionMutation.error} fallback="Gagal membuat versi tarif." />
            <button type="submit" className={primaryButtonClass} disabled={!authenticated || createVersionMutation.isPending}>
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
            <Field label="ID Versi Tarif">
              <input
                className={inputClass}
                value={workflowForm.tariffVersionId}
                onChange={(event) => setWorkflowForm((prev) => ({ ...prev, tariffVersionId: event.target.value }))}
                required
              />
            </Field>
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
            <MutationError error={calculationMutation.error} fallback="Kalkulasi tarif gagal." />
            <button type="submit" className={secondaryButtonClass} disabled={calculationMutation.isPending}>
              Hitung Tarif
            </button>
          </form>
        </Section>
      </section>
    </main>
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
    <main className="space-y-6">
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
    </main>
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
    <main className="space-y-6">
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
    </main>
  );
}
