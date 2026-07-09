"use client";

import {
  AlertCircle,
  CalendarClock,
  CheckCircle2,
  ClipboardCheck,
  Loader2,
  Play,
  Plus,
  RotateCcw,
  Search,
  XCircle
} from "lucide-react";
import { type FormEvent, useMemo, useState } from "react";
import { PermissionGate } from "@/components/auth/permission-gate";
import { PageHeader } from "@/components/common/page-header";
import { EmptyState } from "@/components/state/empty-state";
import { ErrorState } from "@/components/state/error-state";
import { LoadingSkeleton } from "@/components/state/loading-skeleton";
import { StatusBadge } from "@/components/status/status-badge";
import { useCurrentUser } from "@/features/auth/use-current-user";
import { apiErrorMessage } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import {
  allowedCollectionActionWorkflows,
  resolveCollectionActionPermissions,
  type CollectionActionPermissionState
} from "./collection-action-permissions";
import {
  collectionActionStatusValues,
  collectionActionTypeValues,
  type CollectionAction,
  type CollectionActionFilters,
  type CollectionActionStatus,
  type CollectionActionType,
  type CollectionActionWorkflow
} from "./collection-action-schema";
import {
  useCollectionActions,
  useCollectionActionWorkflow,
  useCreateCollectionAction
} from "./use-collection-actions";

type FilterStatus = CollectionActionStatus | "ALL";

type FilterState = {
  status: FilterStatus;
  customerId: string;
  invoiceId: string;
  page: number;
  size: number;
};

type CreateFormState = {
  customerId: string;
  invoiceId: string;
  actionType: CollectionActionType;
  scheduledAt: string;
  notes: string;
  reason: string;
};

type WorkflowDraft = {
  action: CollectionActionWorkflow;
  actionId: string;
  notes: string;
  reason: string;
};

const statusLabels: Record<CollectionActionStatus, string> = {
  OPEN: "Terbuka",
  IN_PROGRESS: "Berjalan",
  COMPLETED: "Selesai",
  CANCELLED: "Dibatalkan"
};

const statusTones: Record<CollectionActionStatus, "success" | "warning" | "danger" | "info" | "neutral"> = {
  OPEN: "warning",
  IN_PROGRESS: "info",
  COMPLETED: "success",
  CANCELLED: "neutral"
};

const actionTypeLabels: Record<CollectionActionType, string> = {
  REMINDER: "Pengingat",
  WARNING_LETTER: "Surat Peringatan",
  DISCONNECTION_NOTICE: "Pemberitahuan Pemutusan",
  FIELD_VISIT: "Kunjungan Lapangan",
  PHONE_CALL: "Telepon",
  PAYMENT_PROMISE: "Janji Bayar"
};

const workflowLabels: Record<CollectionActionWorkflow, string> = {
  start: "Mulai Aksi",
  complete: "Selesaikan Aksi",
  cancel: "Batalkan Aksi"
};

const invoiceRequiredTypes = new Set<CollectionActionType>(["REMINDER", "WARNING_LETTER", "DISCONNECTION_NOTICE"]);

function emptyCreateForm(): CreateFormState {
  return {
    customerId: "",
    invoiceId: "",
    actionType: "WARNING_LETTER",
    scheduledAt: toDateTimeLocal(new Date()),
    notes: "",
    reason: ""
  };
}

function toDateTimeLocal(date: Date): string {
  const localTime = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return localTime.toISOString().slice(0, 16);
}

function normalizeInput(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

function formatDateTime(value: string | null): string {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat("id-ID", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}

function shortId(value: string | null): string {
  if (!value) {
    return "-";
  }
  return value.length <= 13 ? value : `${value.slice(0, 8)}...${value.slice(-4)}`;
}

function SummaryCard({
  label,
  value,
  helper,
  tone
}: Readonly<{ label: string; value: string; helper: string; tone: "success" | "warning" | "info" | "neutral" }>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-semibold text-slate-600">{label}</p>
        <StatusBadge label={tone.toUpperCase()} tone={tone} />
      </div>
      <p className="mt-3 text-2xl font-bold text-slate-950">{value}</p>
      <p className="mt-1 text-sm leading-6 text-slate-600">{helper}</p>
    </div>
  );
}

function FormField({
  label,
  children,
  helper
}: Readonly<{ label: string; children: React.ReactNode; helper?: string }>) {
  return (
    <label className="block">
      <span className="text-sm font-bold text-slate-800">{label}</span>
      <div className="mt-1">{children}</div>
      {helper ? <span className="mt-1 block text-xs font-medium text-slate-600">{helper}</span> : null}
    </label>
  );
}

function baseInputClass(hasError = false): string {
  return cn(
    "h-10 w-full rounded-lg border bg-white px-3 text-sm font-semibold text-slate-950 outline-none transition focus:ring-2",
    hasError ? "border-red-300 focus:ring-red-100" : "border-slate-300 focus:border-teal-600 focus:ring-teal-100"
  );
}

function CollectionActionForm() {
  const [form, setForm] = useState<CreateFormState>(() => emptyCreateForm());
  const [localError, setLocalError] = useState<string | null>(null);
  const createMutation = useCreateCollectionAction();
  const requiresInvoice = invoiceRequiredTypes.has(form.actionType);
  const mutationError = createMutation.isError
    ? apiErrorMessage(createMutation.error, "Aksi penagihan gagal dibuat.")
    : null;

  function updateForm<K extends keyof CreateFormState>(key: K, value: CreateFormState[K]) {
    setLocalError(null);
    createMutation.reset();
    setForm((current) => ({ ...current, [key]: value }));
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLocalError(null);

    const customerId = normalizeInput(form.customerId);
    const invoiceId = normalizeInput(form.invoiceId);
    const reason = normalizeInput(form.reason);
    const scheduledAt = new Date(form.scheduledAt);

    if (!customerId) {
      setLocalError("Customer ID wajib diisi.");
      return;
    }
    if (requiresInvoice && !invoiceId) {
      setLocalError("Invoice ID wajib untuk aksi dunning.");
      return;
    }
    if (Number.isNaN(scheduledAt.getTime())) {
      setLocalError("Jadwal aksi tidak valid.");
      return;
    }
    if (!reason) {
      setLocalError("Alasan audit wajib diisi.");
      return;
    }

    createMutation.mutate(
      {
        customerId,
        invoiceId,
        actionType: form.actionType,
        scheduledAt: scheduledAt.toISOString(),
        notes: normalizeInput(form.notes),
        reason
      },
      {
        onSuccess: () => {
          setForm(emptyCreateForm());
        }
      }
    );
  }

  return (
    <form onSubmit={submit} className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center gap-2">
        <Plus className="size-5 text-teal-700" aria-hidden="true" />
        <h2 className="text-base font-bold text-slate-950">Aksi Penagihan Baru</h2>
      </div>

      <div className="mt-5 grid gap-4 md:grid-cols-2">
        <FormField label="Customer ID">
          <input
            className={baseInputClass()}
            value={form.customerId}
            onChange={(event) => updateForm("customerId", event.target.value)}
            placeholder="UUID pelanggan"
            autoComplete="off"
          />
        </FormField>
        <FormField
          label="Invoice ID"
          helper={requiresInvoice ? "Wajib untuk pengingat, surat peringatan, dan pemutusan." : undefined}
        >
          <input
            className={baseInputClass(requiresInvoice && form.invoiceId.trim().length === 0)}
            value={form.invoiceId}
            onChange={(event) => updateForm("invoiceId", event.target.value)}
            placeholder="UUID invoice"
            autoComplete="off"
          />
        </FormField>
        <FormField label="Jenis Aksi">
          <select
            className={baseInputClass()}
            value={form.actionType}
            onChange={(event) => updateForm("actionType", event.target.value as CollectionActionType)}
          >
            {collectionActionTypeValues.map((type) => (
              <option key={type} value={type}>
                {actionTypeLabels[type]}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="Jadwal">
          <input
            className={baseInputClass()}
            type="datetime-local"
            value={form.scheduledAt}
            onChange={(event) => updateForm("scheduledAt", event.target.value)}
          />
        </FormField>
        <div className="md:col-span-2">
          <FormField label="Catatan">
            <textarea
              className="min-h-24 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-950 outline-none transition focus:border-teal-600 focus:ring-2 focus:ring-teal-100"
              value={form.notes}
              onChange={(event) => updateForm("notes", event.target.value)}
              placeholder="Ringkasan konteks penagihan"
            />
          </FormField>
        </div>
        <div className="md:col-span-2">
          <FormField label="Alasan Audit">
            <input
              className={baseInputClass()}
              value={form.reason}
              onChange={(event) => updateForm("reason", event.target.value)}
              placeholder="Contoh: jadwalkan SP1 Juli"
              autoComplete="off"
            />
          </FormField>
        </div>
      </div>

      {localError || mutationError ? (
        <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-800">
          {localError ?? mutationError}
        </div>
      ) : null}

      <div className="mt-5 flex justify-end">
        <button
          type="submit"
          disabled={createMutation.isPending}
          className="inline-flex h-10 items-center gap-2 rounded-lg bg-slate-950 px-4 text-sm font-bold text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
        >
          {createMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Plus className="size-4" aria-hidden="true" />}
          Simpan Aksi
        </button>
      </div>
    </form>
  );
}

function FilterToolbar({
  filters,
  onChange
}: Readonly<{ filters: FilterState; onChange: (next: FilterState) => void }>) {
  function update<K extends keyof FilterState>(key: K, value: FilterState[K]) {
    onChange({ ...filters, [key]: value, page: key === "page" ? Number(value) : 0 });
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="grid gap-3 md:grid-cols-[180px_1fr_1fr_120px_auto]">
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Status</span>
          <select
            className={cn(baseInputClass(), "mt-1")}
            value={filters.status}
            onChange={(event) => update("status", event.target.value as FilterStatus)}
          >
            <option value="ALL">Semua</option>
            {collectionActionStatusValues.map((status) => (
              <option key={status} value={status}>
                {statusLabels[status]}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Customer ID</span>
          <div className="relative mt-1">
            <Search className="pointer-events-none absolute left-3 top-3 size-4 text-slate-500" aria-hidden="true" />
            <input
              className={cn(baseInputClass(), "pl-9")}
              value={filters.customerId}
              onChange={(event) => update("customerId", event.target.value)}
              placeholder="Filter UUID pelanggan"
            />
          </div>
        </label>
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Invoice ID</span>
          <input
            className={cn(baseInputClass(), "mt-1")}
            value={filters.invoiceId}
            onChange={(event) => update("invoiceId", event.target.value)}
            placeholder="Filter UUID invoice"
          />
        </label>
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Size</span>
          <select
            className={cn(baseInputClass(), "mt-1")}
            value={filters.size}
            onChange={(event) => update("size", Number(event.target.value))}
          >
            {[10, 25, 50, 100].map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </select>
        </label>
        <div className="flex items-end">
          <button
            type="button"
            onClick={() => onChange({ status: "ALL", customerId: "", invoiceId: "", page: 0, size: 25 })}
            className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-3 text-sm font-bold text-slate-800 hover:bg-teal-50"
          >
            <RotateCcw className="size-4" aria-hidden="true" />
            Reset
          </button>
        </div>
      </div>
    </div>
  );
}

function WorkflowButton({
  children,
  icon,
  disabled,
  tone,
  onClick
}: Readonly<{
  children: React.ReactNode;
  icon: React.ReactNode;
  disabled: boolean;
  tone: "primary" | "success" | "danger";
  onClick: () => void;
}>) {
  const toneClass: Record<typeof tone, string> = {
    primary: "border-teal-200 bg-teal-50 text-teal-900 hover:bg-teal-100",
    success: "border-emerald-200 bg-emerald-50 text-emerald-800 hover:bg-emerald-100",
    danger: "border-red-200 bg-red-50 text-red-800 hover:bg-red-100"
  };

  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "inline-flex h-9 items-center gap-2 rounded-lg border px-3 text-xs font-bold disabled:cursor-not-allowed disabled:border-slate-200 disabled:bg-slate-50 disabled:text-slate-400",
        toneClass[tone]
      )}
    >
      {icon}
      {children}
    </button>
  );
}

function CollectionActionTable({
  actions,
  isFetching,
  permissions,
  onWorkflow
}: Readonly<{
  actions: CollectionAction[];
  isFetching: boolean;
  permissions: CollectionActionPermissionState;
  onWorkflow: (draft: WorkflowDraft) => void;
}>) {
  if (actions.length === 0) {
    return (
      <EmptyState
        title="Belum ada aksi penagihan"
        description="Aksi akan muncul setelah penjadwalan penagihan atau dunning berhasil dibuat."
      />
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
        <div className="flex items-center gap-2">
          <ClipboardCheck className="size-5 text-slate-700" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Daftar Aksi</h2>
        </div>
        {isFetching ? (
          <span className="inline-flex items-center gap-2 text-sm font-semibold text-slate-600">
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Memperbarui
          </span>
        ) : null}
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Status</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Jenis</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Jadwal</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Pelanggan</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Invoice</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Catatan</th>
              <th className="px-5 py-3 text-right font-bold text-slate-700">Aksi</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {actions.map((action) => {
              const workflows = allowedCollectionActionWorkflows(action, permissions);
              const hasVisibleWorkflow = permissions.canExecute || permissions.canCancel;

              return (
                <tr key={action.id} className="hover:bg-teal-50">
                  <td className="whitespace-nowrap px-5 py-4">
                    <StatusBadge label={statusLabels[action.status]} tone={statusTones[action.status]} />
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 font-semibold text-slate-950">
                    {actionTypeLabels[action.actionType]}
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 text-slate-700">{formatDateTime(action.scheduledAt)}</td>
                  <td className="px-5 py-4">
                    <span title={action.customerId} className="font-mono text-xs font-bold text-slate-700">
                      {shortId(action.customerId)}
                    </span>
                  </td>
                  <td className="px-5 py-4">
                    <span title={action.invoiceId ?? undefined} className="font-mono text-xs font-bold text-slate-700">
                      {shortId(action.invoiceId)}
                    </span>
                  </td>
                  <td className="min-w-72 px-5 py-4 text-slate-700">{action.notes ?? "-"}</td>
                  <td className="px-5 py-4">
                    {hasVisibleWorkflow ? (
                      <div className="flex justify-end gap-2">
                        {permissions.canExecute ? (
                          <>
                            <WorkflowButton
                              disabled={!workflows.start}
                              tone="primary"
                              icon={<Play className="size-4" aria-hidden="true" />}
                              onClick={() =>
                                onWorkflow({ action: "start", actionId: action.id, notes: action.notes ?? "", reason: "" })
                              }
                            >
                              Mulai
                            </WorkflowButton>
                            <WorkflowButton
                              disabled={!workflows.complete}
                              tone="success"
                              icon={<CheckCircle2 className="size-4" aria-hidden="true" />}
                              onClick={() =>
                                onWorkflow({ action: "complete", actionId: action.id, notes: action.notes ?? "", reason: "" })
                              }
                            >
                              Selesai
                            </WorkflowButton>
                          </>
                        ) : null}
                        {permissions.canCancel ? (
                          <WorkflowButton
                            disabled={!workflows.cancel}
                            tone="danger"
                            icon={<XCircle className="size-4" aria-hidden="true" />}
                            onClick={() =>
                              onWorkflow({ action: "cancel", actionId: action.id, notes: action.notes ?? "", reason: "" })
                            }
                          >
                            Batal
                          </WorkflowButton>
                        ) : null}
                      </div>
                    ) : (
                      <p className="text-right text-xs font-bold text-slate-500">Tidak tersedia</p>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function WorkflowModal({
  draft,
  onChange,
  onClose,
  onSubmit,
  isPending,
  error
}: Readonly<{
  draft: WorkflowDraft;
  onChange: (draft: WorkflowDraft) => void;
  onClose: () => void;
  onSubmit: () => void;
  isPending: boolean;
  error: string | null;
}>) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="w-full max-w-lg rounded-lg border border-slate-200 bg-white p-5 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-bold text-slate-950">{workflowLabels[draft.action]}</h2>
            <p className="mt-1 font-mono text-xs font-bold text-slate-600">{draft.actionId}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isPending}
            className="rounded-lg border border-slate-200 p-2 text-slate-600 hover:bg-teal-50 disabled:opacity-60"
            aria-label="Tutup modal"
          >
            <XCircle className="size-4" aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 space-y-4">
          <FormField label="Catatan">
            <textarea
              className="min-h-24 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-950 outline-none transition focus:border-teal-600 focus:ring-2 focus:ring-teal-100"
              value={draft.notes}
              onChange={(event) => onChange({ ...draft, notes: event.target.value })}
            />
          </FormField>
          <FormField label="Alasan Audit">
            <input
              className={baseInputClass()}
              value={draft.reason}
              onChange={(event) => onChange({ ...draft, reason: event.target.value })}
              placeholder="Alasan workflow"
            />
          </FormField>
        </div>

        {error ? (
          <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-800">
            {error}
          </div>
        ) : null}

        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            disabled={isPending}
            className="h-10 rounded-lg border border-slate-300 bg-white px-4 text-sm font-bold text-slate-800 hover:bg-teal-50 disabled:opacity-60"
          >
            Tutup
          </button>
          <button
            type="button"
            onClick={onSubmit}
            disabled={isPending}
            className={cn(
              "inline-flex h-10 items-center gap-2 rounded-lg px-4 text-sm font-bold text-white disabled:cursor-not-allowed disabled:bg-slate-400",
              draft.action === "cancel" ? "bg-red-700 hover:bg-red-800" : "bg-slate-950 hover:bg-slate-800"
            )}
          >
            {isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : null}
            Konfirmasi
          </button>
        </div>
      </div>
    </div>
  );
}

function PaginationBar({
  page,
  size,
  totalItems,
  totalPages,
  onPageChange
}: Readonly<{
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}>) {
  const canPrevious = page > 0;
  const canNext = page + 1 < totalPages;

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm shadow-sm">
      <p className="font-semibold text-slate-700">
        Halaman {totalPages === 0 ? 0 : page + 1} dari {totalPages} - {totalItems} data
      </p>
      <div className="flex items-center gap-2">
        <span className="text-slate-600">Size {size}</span>
        <button
          type="button"
          disabled={!canPrevious}
          onClick={() => onPageChange(page - 1)}
          className="h-9 rounded-lg border border-slate-300 px-3 font-bold text-slate-800 hover:bg-teal-50 disabled:cursor-not-allowed disabled:text-slate-400"
        >
          Sebelumnya
        </button>
        <button
          type="button"
          disabled={!canNext}
          onClick={() => onPageChange(page + 1)}
          className="h-9 rounded-lg border border-slate-300 px-3 font-bold text-slate-800 hover:bg-teal-50 disabled:cursor-not-allowed disabled:text-slate-400"
        >
          Berikutnya
        </button>
      </div>
    </div>
  );
}

export function CollectionActionWorkspace() {
  const [filters, setFilters] = useState<FilterState>({
    status: "ALL",
    customerId: "",
    invoiceId: "",
    page: 0,
    size: 25
  });
  const [workflowDraft, setWorkflowDraft] = useState<WorkflowDraft | null>(null);
  const [workflowLocalError, setWorkflowLocalError] = useState<string | null>(null);
  const currentUserQuery = useCurrentUser();

  const permissions = useMemo(
    () => resolveCollectionActionPermissions(currentUserQuery.data?.authorities ?? []),
    [currentUserQuery.data?.authorities]
  );

  const queryFilters = useMemo<CollectionActionFilters>(
    () => ({
      status: filters.status === "ALL" ? undefined : filters.status,
      customerId: normalizeInput(filters.customerId),
      invoiceId: normalizeInput(filters.invoiceId),
      page: filters.page,
      size: filters.size
    }),
    [filters]
  );

  const actionsQuery = useCollectionActions(queryFilters, currentUserQuery.isSuccess && permissions.canRead);
  const workflowMutation = useCollectionActionWorkflow();
  const actions = useMemo(() => actionsQuery.data?.items ?? [], [actionsQuery.data?.items]);

  const summary = useMemo(() => {
    const open = actions.filter((action) => action.status === "OPEN").length;
    const progress = actions.filter((action) => action.status === "IN_PROGRESS").length;
    const completed = actions.filter((action) => action.status === "COMPLETED").length;
    return { open, progress, completed };
  }, [actions]);

  function submitWorkflow() {
    if (!workflowDraft) {
      return;
    }
    const reason = normalizeInput(workflowDraft.reason);
    if (!reason) {
      setWorkflowLocalError("Alasan audit wajib diisi.");
      return;
    }
    setWorkflowLocalError(null);
    workflowMutation.mutate(
      {
        actionId: workflowDraft.actionId,
        workflow: workflowDraft.action,
        payload: {
          notes: normalizeInput(workflowDraft.notes),
          reason
        }
      },
      {
        onSuccess: () => {
          setWorkflowDraft(null);
        }
      }
    );
  }

  return (
    <main className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <PageHeader
          title="Penagihan Piutang"
          description="Workspace operasional untuk menjadwalkan, mengeksekusi, dan menutup aksi penagihan berbasis invoice overdue."
        />
        <div className="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm shadow-sm">
          <div className="flex items-center gap-2 font-bold text-slate-950">
            <CalendarClock className="size-4 text-slate-600" aria-hidden="true" />
            Collection Control
          </div>
          <p className="mt-1 font-semibold text-slate-600">Dunning guarded by backend</p>
        </div>
      </div>

      {currentUserQuery.isLoading ? <LoadingSkeleton /> : null}
      {currentUserQuery.isError ? (
        <ErrorState message={apiErrorMessage(currentUserQuery.error, "Sesi atau otorisasi pengguna tidak tersedia.")} />
      ) : null}
      {currentUserQuery.isSuccess && !permissions.canRead ? (
        <PermissionGate allowed={false} message="Anda tidak memiliki izin membaca aksi penagihan." />
      ) : null}

      {currentUserQuery.isSuccess && permissions.canRead ? (
        <>
          <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <SummaryCard
              label="Total Terfilter"
              value={String(actionsQuery.data?.totalItems ?? 0)}
              helper="Jumlah data sesuai filter aktif."
              tone="neutral"
            />
            <SummaryCard
              label="Terbuka"
              value={String(summary.open)}
              helper="Aksi menunggu eksekusi di halaman ini."
              tone="warning"
            />
            <SummaryCard label="Berjalan" value={String(summary.progress)} helper="Aksi sedang diproses petugas." tone="info" />
            <SummaryCard label="Selesai" value={String(summary.completed)} helper="Aksi sudah ditutup di halaman ini." tone="success" />
          </section>

          <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_420px]">
            <div className="space-y-4">
              <FilterToolbar filters={filters} onChange={setFilters} />

              {actionsQuery.isLoading ? <LoadingSkeleton /> : null}
              {actionsQuery.isError ? (
                <div className="space-y-3">
                  <ErrorState message={apiErrorMessage(actionsQuery.error, "Daftar aksi penagihan tidak tersedia.")} />
                  <button
                    type="button"
                    onClick={() => void actionsQuery.refetch()}
                    className="inline-flex h-10 items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 text-sm font-bold text-slate-800 hover:bg-teal-50"
                  >
                    <RotateCcw className="size-4" aria-hidden="true" />
                    Muat Ulang
                  </button>
                </div>
              ) : null}
              {actionsQuery.data ? (
                <>
                  <CollectionActionTable
                    actions={actions}
                    isFetching={actionsQuery.isFetching}
                    permissions={permissions}
                    onWorkflow={(draft) => {
                      workflowMutation.reset();
                      setWorkflowLocalError(null);
                      setWorkflowDraft(draft);
                    }}
                  />
                  <PaginationBar
                    page={actionsQuery.data.page}
                    size={actionsQuery.data.size}
                    totalItems={actionsQuery.data.totalItems}
                    totalPages={actionsQuery.data.totalPages}
                    onPageChange={(page) => setFilters((current) => ({ ...current, page }))}
                  />
                </>
              ) : null}
            </div>

            <div className="space-y-4">
              <PermissionGate allowed={permissions.canCreate} message="Anda tidak memiliki izin membuat aksi penagihan.">
                <CollectionActionForm />
              </PermissionGate>
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
                <div className="flex items-center gap-2 text-sm font-bold text-amber-900">
                  <AlertCircle className="size-4" aria-hidden="true" />
                  Guardrail
                </div>
                <p className="mt-2 text-sm leading-6 text-amber-900">
                  Backend menolak customer nonaktif, invoice yang belum overdue, dan duplicate action aktif untuk invoice
                  dan tipe aksi yang sama.
                </p>
              </div>
            </div>
          </section>

          {workflowDraft ? (
            <WorkflowModal
              draft={workflowDraft}
              onChange={setWorkflowDraft}
              onClose={() => {
                if (!workflowMutation.isPending) {
                  setWorkflowDraft(null);
                }
              }}
              onSubmit={submitWorkflow}
              isPending={workflowMutation.isPending}
              error={
                workflowLocalError ??
                (workflowMutation.isError
                  ? apiErrorMessage(workflowMutation.error, "Workflow aksi penagihan gagal diproses.")
                  : null)
              }
            />
          ) : null}
        </>
      ) : null}
    </main>
  );
}
