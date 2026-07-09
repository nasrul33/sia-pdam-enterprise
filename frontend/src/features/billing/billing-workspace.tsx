"use client";

import {
  AlertTriangle,
  CheckCircle2,
  Eye,
  FileText,
  Loader2,
  LockKeyhole,
  ReceiptText,
  RotateCcw,
  Send,
  ShieldCheck,
  X
} from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { MoneyText } from "@/components/format/money-text";
import { PageHeader } from "@/components/common/page-header";
import { EmptyState } from "@/components/state/empty-state";
import { ErrorState } from "@/components/state/error-state";
import { LoadingSkeleton } from "@/components/state/loading-skeleton";
import { StatusBadge } from "@/components/status/status-badge";
import { useAccounts } from "@/features/accounting/use-accounting";
import type { Account } from "@/features/accounting/accounting-schema";
import { useCurrentUser } from "@/features/auth/use-current-user";
import { resolveFinancialCommandPermissions } from "@/features/security/financial-command-permissions";
import { apiErrorMessage } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import {
  billingIssueReadinessCopy,
  canIssueInvoice,
  canViewInvoiceDocument,
  canVoidInvoice,
  filterInvoicesByStatus,
  generateBillingBatchErrors,
  invoiceScopeTitle,
  issueInvoiceErrors,
  summarizeBillingWorkspace,
  voidInvoiceErrors
} from "./billing-workspace-model";
import type {
  BillingBatch,
  BillingBatchIssueReadiness,
  BillingBatchStatus,
  GenerateBillingBatchPayload,
  Invoice,
  InvoiceDocument,
  InvoiceStatus,
  IssueInvoicePayload,
  VoidInvoicePayload
} from "./billing-schema";
import { billingBatchStatusValues, invoiceStatusValues } from "./billing-schema";
import {
  useBatchInvoices,
  useBillingBatches,
  useBillingBatchIssueReadiness,
  useGenerateBillingBatch,
  useInvoiceDocument,
  useInvoices,
  useIssueInvoice,
  useVoidInvoice
} from "./use-billing";

type StatusFilter<TStatus extends string> = TStatus | "ALL";
type BillingPermissions = ReturnType<typeof resolveFinancialCommandPermissions>["billing"];
type FeedbackType = "success" | "error";

type GenerateBatchFormState = {
  period: string;
  areaCode: string;
  billingDate: string;
  dueDate: string;
  reason: string;
};

type IssueInvoiceDraft = {
  invoice: Invoice;
  receivableAccountId: string;
  revenueAccountId: string;
  reason: string;
};

type VoidInvoiceDraft = {
  invoice: Invoice;
  reason: string;
};

const defaultGenerateBatchForm: GenerateBatchFormState = {
  period: "",
  areaCode: "",
  billingDate: "",
  dueDate: "",
  reason: ""
};

const batchStatusLabels: Record<BillingBatchStatus, string> = {
  DRAFT: "Draft",
  RUNNING: "Running",
  COMPLETED: "Completed",
  FAILED: "Gagal",
  VOID: "Void"
};

const batchStatusTones: Record<BillingBatchStatus, "success" | "warning" | "danger" | "neutral" | "info"> = {
  DRAFT: "neutral",
  RUNNING: "info",
  COMPLETED: "success",
  FAILED: "danger",
  VOID: "danger"
};

const invoiceStatusLabels: Record<InvoiceStatus, string> = {
  DRAFT: "Draft",
  ISSUED: "Issued",
  PARTIAL_PAID: "Parsial",
  PAID: "Lunas",
  CORRECTED: "Koreksi",
  VOID: "Void"
};

const invoiceStatusTones: Record<InvoiceStatus, "success" | "warning" | "danger" | "neutral" | "info"> = {
  DRAFT: "warning",
  ISSUED: "info",
  PARTIAL_PAID: "warning",
  PAID: "success",
  CORRECTED: "neutral",
  VOID: "danger"
};

const inputClass =
  "mt-1 h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-teal-600 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-100 disabled:text-slate-500";

const textareaClass =
  "mt-1 min-h-20 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-teal-600 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-100 disabled:text-slate-500";

const primaryButtonClass =
  "inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-teal-700 px-4 text-sm font-black text-white shadow-[0_14px_28px_-20px_rgba(15,118,110,0.9)] transition hover:bg-teal-800 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-600";

const secondaryButtonClass =
  "inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-4 text-sm font-black text-slate-800 shadow-[0_12px_24px_-22px_rgba(15,23,42,0.8)] transition hover:border-teal-300 hover:bg-teal-50 hover:text-teal-900 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500";

const dangerButtonClass =
  "inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-red-700 px-4 text-sm font-black text-white shadow-[0_14px_28px_-20px_rgba(185,28,28,0.86)] transition hover:bg-red-800 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-600";

function normalizeInput(value: string): string {
  return value.trim();
}

function idempotencyKeyFor(payload: GenerateBillingBatchPayload): string {
  const entropy = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `billing-${payload.period}-${payload.areaCode}-${entropy}`;
}

function formatDate(value: string | null): string {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat("id-ID", { dateStyle: "medium" }).format(new Date(value));
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

function InlineMessage({ type, message }: Readonly<{ type: FeedbackType; message: string }>) {
  return (
    <div
      className={cn(
        "rounded-lg border p-3 text-sm font-semibold",
        type === "success" ? "border-emerald-200 bg-emerald-50 text-emerald-800" : "border-red-200 bg-red-50 text-red-800"
      )}
    >
      <div className="flex items-start gap-2">
        {type === "success" ? (
          <CheckCircle2 className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
        ) : (
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
        )}
        <span>{message}</span>
      </div>
    </div>
  );
}

function SummaryCard({
  label,
  value,
  helper,
  tone
}: Readonly<{ label: string; value: ReactNode; helper: string; tone: "success" | "warning" | "info" | "neutral" }>) {
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

function BillingCommandPanel({ permissions }: Readonly<{ permissions: BillingPermissions }>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 p-4">
        <div className="flex items-center gap-2">
          <ReceiptText className="size-5 text-teal-700" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Billing Command</h2>
        </div>
      </div>
      <CommandStatus label="Generate Billing" allowed={permissions.canGenerateBilling} />
      <CommandStatus label="Lihat Dokumen Invoice" allowed={permissions.canViewInvoices} />
      <CommandStatus label="Issue Invoice" allowed={permissions.canIssueInvoices} highRisk />
      <CommandStatus label="Void/Koreksi Invoice" allowed={permissions.canCorrectInvoices} highRisk />
    </div>
  );
}

function CommandStatus({
  label,
  allowed,
  highRisk = false
}: Readonly<{ label: string; allowed: boolean; highRisk?: boolean }>) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-slate-100 px-4 py-3 last:border-b-0">
      <div className="flex items-center gap-2">
        {allowed ? (
          <ShieldCheck className="size-4 text-emerald-700" aria-hidden="true" />
        ) : (
          <LockKeyhole className="size-4 text-slate-500" aria-hidden="true" />
        )}
        <p className="text-sm font-bold text-slate-950">{label}</p>
      </div>
      <div className="flex items-center gap-2">
        {highRisk ? <StatusBadge label="High" tone="danger" /> : null}
        <StatusBadge label={allowed ? "Aktif" : "Terkunci"} tone={allowed ? "success" : "neutral"} />
      </div>
    </div>
  );
}

function BillingIssueReadinessPanel({
  selectedBatch,
  readiness,
  isLoading,
  error,
  onRetry
}: Readonly<{
  selectedBatch: BillingBatch | null;
  readiness: BillingBatchIssueReadiness | null;
  isLoading: boolean;
  error: unknown;
  onRetry: () => void;
}>) {
  const copy = billingIssueReadinessCopy(readiness);

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <ShieldCheck className="size-5 text-teal-700" aria-hidden="true" />
            <h3 className="text-sm font-bold text-slate-950">Issue Readiness</h3>
          </div>
          <p className="mt-1 text-sm font-semibold text-slate-600">
            {selectedBatch ? selectedBatch.batchNumber : "Pilih batch untuk membaca kontrol issue."}
          </p>
        </div>
        <StatusBadge label={copy.label} tone={copy.tone} />
      </div>

      {isLoading ? (
        <div className="mt-4 flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm font-bold text-slate-700">
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Membaca readiness
        </div>
      ) : null}

      {error ? (
        <div className="mt-4 space-y-3">
          <InlineMessage type="error" message={apiErrorMessage(error, "Readiness billing tidak tersedia.")} />
          <button type="button" className={secondaryButtonClass} onClick={onRetry}>
            <RotateCcw className="size-4" aria-hidden="true" />
            Muat Ulang
          </button>
        </div>
      ) : null}

      {!isLoading && !error ? (
        <div className="mt-4 space-y-3">
          <p className="text-sm leading-6 font-semibold text-slate-700">{copy.description}</p>
          <div className="grid grid-cols-2 gap-2">
            <ReadinessMetric label="Total" value={readiness?.totalInvoices ?? 0} />
            <ReadinessMetric label="Draft" value={readiness?.draftInvoices ?? 0} />
            <ReadinessMetric label="Blocked" value={readiness?.blockedInvoices ?? 0} />
            <ReadinessMetric label="Missing Trace" value={readiness?.missingJournalTraceInvoices ?? 0} danger />
          </div>
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
            <div className="flex items-center justify-between gap-3 text-sm">
              <span className="font-bold text-slate-700">Draft Amount</span>
              <span className="font-black text-slate-950">
                <MoneyText value={readiness?.draftAmount ?? 0} />
              </span>
            </div>
            <div className="mt-2 flex items-center justify-between gap-3 text-sm">
              <span className="font-bold text-slate-700">Outstanding</span>
              <span className="font-black text-slate-950">
                <MoneyText value={readiness?.outstandingAmount ?? 0} />
              </span>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function ReadinessMetric({
  label,
  value,
  danger = false
}: Readonly<{ label: string; value: number; danger?: boolean }>) {
  return (
    <div className={cn("rounded-lg border p-3", danger && value > 0 ? "border-red-200 bg-red-50" : "border-slate-200 bg-slate-50")}>
      <p className={cn("text-xs font-bold uppercase", danger && value > 0 ? "text-red-800" : "text-slate-600")}>{label}</p>
      <p className={cn("mt-1 text-lg font-black", danger && value > 0 ? "text-red-900" : "text-slate-950")}>{value}</p>
    </div>
  );
}

function BillingFilterToolbar({
  period,
  batchStatus,
  invoiceStatus,
  onPeriodChange,
  onBatchStatusChange,
  onInvoiceStatusChange,
  onReset
}: Readonly<{
  period: string;
  batchStatus: StatusFilter<BillingBatchStatus>;
  invoiceStatus: StatusFilter<InvoiceStatus>;
  onPeriodChange: (period: string) => void;
  onBatchStatusChange: (status: StatusFilter<BillingBatchStatus>) => void;
  onInvoiceStatusChange: (status: StatusFilter<InvoiceStatus>) => void;
  onReset: () => void;
}>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="grid gap-3 md:grid-cols-[160px_180px_180px_auto]">
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Periode</span>
          <input className={inputClass} value={period} maxLength={7} onChange={(event) => onPeriodChange(event.target.value)} placeholder="2026-07" />
        </label>
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Status Batch</span>
          <select className={inputClass} value={batchStatus} onChange={(event) => onBatchStatusChange(event.target.value as StatusFilter<BillingBatchStatus>)}>
            <option value="ALL">Semua</option>
            {billingBatchStatusValues.map((value) => (
              <option key={value} value={value}>
                {batchStatusLabels[value]}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Status Invoice</span>
          <select className={inputClass} value={invoiceStatus} onChange={(event) => onInvoiceStatusChange(event.target.value as StatusFilter<InvoiceStatus>)}>
            <option value="ALL">Semua</option>
            {invoiceStatusValues.map((value) => (
              <option key={value} value={value}>
                {invoiceStatusLabels[value]}
              </option>
            ))}
          </select>
        </label>
        <div className="flex items-end">
          <button type="button" onClick={onReset} className={secondaryButtonClass}>
            <RotateCcw className="size-4" aria-hidden="true" />
            Reset
          </button>
        </div>
      </div>
    </div>
  );
}

function GenerateBatchForm({ allowed }: Readonly<{ allowed: boolean }>) {
  const generateMutation = useGenerateBillingBatch();
  const [form, setForm] = useState<GenerateBatchFormState>(defaultGenerateBatchForm);
  const [localError, setLocalError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLocalError(null);
    setSuccessMessage(null);
    generateMutation.reset();

    if (!allowed) {
      setLocalError("User tidak memiliki permission billing.generate.");
      return;
    }

    const payload: GenerateBillingBatchPayload = {
      period: normalizeInput(form.period),
      areaCode: normalizeInput(form.areaCode),
      billingDate: normalizeInput(form.billingDate),
      dueDate: normalizeInput(form.dueDate),
      reason: normalizeInput(form.reason)
    };

    const errors = generateBillingBatchErrors(payload);
    if (errors.length > 0) {
      setLocalError(errors[0]);
      return;
    }

    try {
      const result = await generateMutation.mutateAsync({
        payload,
        idempotencyKey: idempotencyKeyFor(payload)
      });
      setForm(defaultGenerateBatchForm);
      setSuccessMessage(`${result.generatedInvoices.length} invoice dibuat untuk batch ${result.batch.batchNumber}.`);
    } catch {
      return;
    }
  }

  const disabled = !allowed || generateMutation.isPending;
  const errorMessage =
    localError ?? (generateMutation.isError ? apiErrorMessage(generateMutation.error, "Gagal generate billing.") : null);

  return (
    <form onSubmit={handleSubmit} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-bold text-slate-950">Generate Billing Batch</h3>
        <StatusBadge label={allowed ? "billing.generate" : "Locked"} tone={allowed ? "success" : "neutral"} />
      </div>
      <div className="mt-4 grid gap-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Periode</span>
            <input className={inputClass} value={form.period} maxLength={7} disabled={disabled} onChange={(event) => setForm((current) => ({ ...current, period: event.target.value }))} placeholder="2026-07" />
          </label>
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Area</span>
            <input className={inputClass} value={form.areaCode} maxLength={64} disabled={disabled} onChange={(event) => setForm((current) => ({ ...current, areaCode: event.target.value }))} placeholder="AREA-01" />
          </label>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Tanggal Billing</span>
            <input type="date" className={inputClass} value={form.billingDate} disabled={disabled} onChange={(event) => setForm((current) => ({ ...current, billingDate: event.target.value }))} />
          </label>
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Jatuh Tempo</span>
            <input type="date" className={inputClass} value={form.dueDate} disabled={disabled} onChange={(event) => setForm((current) => ({ ...current, dueDate: event.target.value }))} />
          </label>
        </div>
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Alasan Audit</span>
          <textarea className={textareaClass} value={form.reason} maxLength={500} disabled={disabled} onChange={(event) => setForm((current) => ({ ...current, reason: event.target.value }))} />
        </label>
        {errorMessage ? <InlineMessage type="error" message={errorMessage} /> : null}
        {successMessage ? <InlineMessage type="success" message={successMessage} /> : null}
        <button type="submit" className={primaryButtonClass} disabled={disabled}>
          {generateMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <ReceiptText className="size-4" aria-hidden="true" />}
          Generate Batch
        </button>
      </div>
    </form>
  );
}

function BatchTable({
  batches,
  isFetching,
  selectedBatchId,
  onSelectBatch,
  onClearSelection
}: Readonly<{
  batches: BillingBatch[];
  isFetching: boolean;
  selectedBatchId: string | null;
  onSelectBatch: (batch: BillingBatch) => void;
  onClearSelection: () => void;
}>) {
  if (batches.length === 0) {
    return <EmptyState title="Batch billing belum tersedia" description="Generate batch akan membuat invoice draft dari baca meter terverifikasi." />;
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
        <div className="flex items-center gap-2">
          <ReceiptText className="size-5 text-slate-700" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Billing Batch</h2>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          {selectedBatchId ? (
            <button type="button" className={secondaryButtonClass} onClick={onClearSelection}>
              <X className="size-4" aria-hidden="true" />
              Semua Invoice
            </button>
          ) : null}
          {isFetching ? (
            <span className="inline-flex items-center gap-2 text-sm font-semibold text-slate-600">
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Memperbarui
            </span>
          ) : null}
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Nomor Batch</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Periode</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Area</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Status</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Dibuat</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Drill-down</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {batches.map((batch) => (
              <tr key={batch.id} className={cn("hover:bg-teal-50", selectedBatchId === batch.id ? "bg-teal-50" : "bg-white")}>
                <td className="whitespace-nowrap px-5 py-4 font-mono text-xs font-bold text-slate-800">{batch.batchNumber}</td>
                <td className="whitespace-nowrap px-5 py-4 font-bold text-slate-950">{batch.period}</td>
                <td className="whitespace-nowrap px-5 py-4 font-semibold text-slate-700">{batch.areaCode}</td>
                <td className="whitespace-nowrap px-5 py-4">
                  <StatusBadge label={batchStatusLabels[batch.status]} tone={batchStatusTones[batch.status]} />
                </td>
                <td className="whitespace-nowrap px-5 py-4 text-slate-700">{formatDateTime(batch.createdAt)}</td>
                <td className="whitespace-nowrap px-5 py-4">
                  <button
                    type="button"
                    className={selectedBatchId === batch.id ? primaryButtonClass : secondaryButtonClass}
                    onClick={() => onSelectBatch(batch)}
                  >
                    <Eye className="size-4" aria-hidden="true" />
                    {selectedBatchId === batch.id ? "Terpilih" : "Lihat Invoice"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function InvoiceDocumentPanel({
  invoice,
  document,
  isLoading,
  error,
  onRetry,
  onClose
}: Readonly<{
  invoice: Invoice;
  document: InvoiceDocument | null;
  isLoading: boolean;
  error: unknown;
  onRetry: () => void;
  onClose: () => void;
}>) {
  return (
    <div className="border-t border-slate-200 bg-slate-50 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-black text-slate-950">Dokumen Rekening {invoice.invoiceNumber}</p>
          <p className="mt-1 text-sm font-semibold text-slate-600">
            Preview data rekening terstruktur dari invoice, sambungan, pelanggan, dan baris tagihan.
          </p>
        </div>
        <button type="button" className={secondaryButtonClass} onClick={onClose}>
          <X className="size-4" aria-hidden="true" />
          Tutup
        </button>
      </div>

      {isLoading ? (
        <div className="mt-4 flex items-center gap-2 rounded-lg border border-slate-200 bg-white p-4 text-sm font-bold text-slate-700">
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Memuat dokumen rekening
        </div>
      ) : null}

      {error ? (
        <div className="mt-4 rounded-lg border border-red-200 bg-red-50 p-4">
          <InlineMessage type="error" message={apiErrorMessage(error, "Gagal memuat dokumen rekening.")} />
          <button type="button" className={secondaryButtonClass + " mt-3"} onClick={onRetry}>
            <RotateCcw className="size-4" aria-hidden="true" />
            Coba lagi
          </button>
        </div>
      ) : null}

      {document ? (
        <div className="mt-4 grid gap-4 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-black text-slate-950">{document.customer.fullName}</p>
              <StatusBadge label={invoiceStatusLabels[document.status]} tone={invoiceStatusTones[document.status]} />
            </div>
            <div className="mt-4 grid gap-3 text-sm">
              <DocumentField label="Nomor Pelanggan" value={document.customer.customerNumber} />
              <DocumentField label="Nomor Sambungan" value={document.connection.connectionNumber} />
              <DocumentField label="Meter" value={document.connection.meterNumber} />
              <DocumentField label="Alamat" value={document.customer.addressLine ?? "-"} />
              <DocumentField label="Area" value={document.customer.areaCode ?? "-"} />
              <DocumentField label="Jatuh Tempo" value={formatDate(document.dueDate)} />
              <DocumentField label="Jurnal Issue" value={shortId(document.issueJournalEntryId)} mono />
              <DocumentField label="Jurnal Void" value={shortId(document.voidJournalEntryId)} mono />
              <DocumentField label="Void At" value={formatDateTime(document.voidedAt)} />
            </div>
            <div className="mt-4 grid grid-cols-2 gap-3">
              <DocumentMetric label="Subtotal" value={<MoneyText value={document.subtotal} />} />
              <DocumentMetric label="Outstanding" value={<MoneyText value={document.outstandingAmount} />} />
              <DocumentMetric label="Denda" value={<MoneyText value={document.penaltyAmount} />} />
              <DocumentMetric label="Dibayar" value={<MoneyText value={document.paidAmount} />} />
            </div>
          </div>

          <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
            <div className="border-b border-slate-200 px-4 py-3">
              <p className="text-sm font-black text-slate-950">Komponen Rekening</p>
            </div>
            {document.lines.length === 0 ? (
              <div className="p-4 text-sm font-semibold text-slate-600">Belum ada baris komponen pada invoice ini.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-slate-200 text-sm">
                  <thead className="bg-slate-50">
                    <tr>
                      <th className="px-4 py-3 text-left font-bold text-slate-700">Tipe</th>
                      <th className="px-4 py-3 text-left font-bold text-slate-700">Deskripsi</th>
                      <th className="px-4 py-3 text-right font-bold text-slate-700">Qty</th>
                      <th className="px-4 py-3 text-right font-bold text-slate-700">Harga</th>
                      <th className="px-4 py-3 text-right font-bold text-slate-700">Amount</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {document.lines.map((line) => (
                      <tr key={`${line.lineType}-${line.description}-${line.amount}`}>
                        <td className="whitespace-nowrap px-4 py-3 font-mono text-xs font-bold text-slate-700">{line.lineType}</td>
                        <td className="px-4 py-3 font-semibold text-slate-800">{line.description}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-right font-semibold text-slate-700">{line.quantity.toLocaleString("id-ID")}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-right font-semibold text-slate-700">
                          <MoneyText value={line.unitPrice} />
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-right font-black text-slate-950">
                          <MoneyText value={line.amount} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
}

function DocumentField({ label, value, mono = false }: Readonly<{ label: string; value: string; mono?: boolean }>) {
  return (
    <div className="flex items-start justify-between gap-3 border-b border-slate-100 pb-2 last:border-b-0 last:pb-0">
      <span className="font-semibold text-slate-500">{label}</span>
      <span className={cn("text-right font-bold text-slate-950", mono ? "font-mono text-xs" : "")}>{value}</span>
    </div>
  );
}

function DocumentMetric({ label, value }: Readonly<{ label: string; value: ReactNode }>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <p className="text-xs font-bold uppercase text-slate-500">{label}</p>
      <p className="mt-1 text-sm font-black text-slate-950">{value}</p>
    </div>
  );
}

function InvoiceTable({
  title,
  description,
  invoices,
  accounts,
  permissions,
  isFetching,
  selectedBatch,
  onClearBatch
}: Readonly<{
  title: string;
  description: string;
  invoices: Invoice[];
  accounts: Account[];
  permissions: BillingPermissions;
  isFetching: boolean;
  selectedBatch: BillingBatch | null;
  onClearBatch: () => void;
}>) {
  const issueMutation = useIssueInvoice();
  const voidMutation = useVoidInvoice();
  const [draft, setDraft] = useState<IssueInvoiceDraft | null>(null);
  const [voidDraft, setVoidDraft] = useState<VoidInvoiceDraft | null>(null);
  const [documentInvoice, setDocumentInvoice] = useState<Invoice | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const documentQuery = useInvoiceDocument(documentInvoice?.id ?? null, Boolean(documentInvoice));
  const receivableAccounts = useMemo(() => accounts.filter((account) => account.type === "ASSET"), [accounts]);
  const revenueAccounts = useMemo(() => accounts.filter((account) => account.type === "REVENUE"), [accounts]);

  if (isFetching && invoices.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2 text-sm font-bold text-slate-950">
          <Loader2 className="size-4 animate-spin text-slate-600" aria-hidden="true" />
          Memuat invoice
        </div>
        <p className="mt-2 text-sm leading-6 text-slate-600">{description}</p>
      </div>
    );
  }

  if (invoices.length === 0) {
    return (
      <EmptyState
        title={selectedBatch ? "Invoice batch tidak ditemukan" : "Invoice belum tersedia"}
        description={
          selectedBatch
            ? "Batch terpilih belum memiliki invoice pada status filter aktif."
            : "Invoice draft akan muncul setelah billing batch berhasil dibuat."
        }
      />
    );
  }

  function openIssue(invoice: Invoice) {
    setLocalError(null);
    setSuccessMessage(null);
    issueMutation.reset();
    setVoidDraft(null);
    setDraft({
      invoice,
      receivableAccountId: "",
      revenueAccountId: "",
      reason: ""
    });
  }

  function openDocument(invoice: Invoice) {
    setLocalError(null);
    setSuccessMessage(null);
    setDocumentInvoice(invoice);
  }

  function openVoid(invoice: Invoice) {
    setLocalError(null);
    setSuccessMessage(null);
    voidMutation.reset();
    setDraft(null);
    setVoidDraft({
      invoice,
      reason: ""
    });
  }

  async function submitIssue() {
    if (!draft) {
      return;
    }

    setLocalError(null);
    const payload: IssueInvoicePayload = {
      receivableAccountId: draft.receivableAccountId,
      revenueAccountId: draft.revenueAccountId,
      reason: normalizeInput(draft.reason)
    };
    const errors = issueInvoiceErrors({ draft: payload, accounts });
    if (errors.length > 0) {
      setLocalError(errors[0]);
      return;
    }

    try {
      await issueMutation.mutateAsync({ invoiceId: draft.invoice.id, payload });
      setSuccessMessage(`Invoice ${draft.invoice.invoiceNumber} berhasil di-issue.`);
      setDraft(null);
    } catch {
      return;
    }
  }

  async function submitVoid() {
    if (!voidDraft) {
      return;
    }

    setLocalError(null);
    const payload: VoidInvoicePayload = {
      reason: normalizeInput(voidDraft.reason)
    };
    const errors = voidInvoiceErrors({ invoice: voidDraft.invoice, draft: payload });
    if (errors.length > 0) {
      setLocalError(errors[0]);
      return;
    }

    try {
      await voidMutation.mutateAsync({ invoiceId: voidDraft.invoice.id, payload });
      setSuccessMessage(`Invoice ${voidDraft.invoice.invoiceNumber} berhasil di-void dengan jurnal koreksi.`);
      setVoidDraft(null);
      if (documentInvoice?.id === voidDraft.invoice.id) {
        void documentQuery.refetch();
      }
    } catch {
      return;
    }
  }

  const errorMessage =
    localError ??
    (issueMutation.isError ? apiErrorMessage(issueMutation.error, "Gagal issue invoice.") : null) ??
    (voidMutation.isError ? apiErrorMessage(voidMutation.error, "Gagal void invoice.") : null);

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
        <div className="flex items-center gap-2">
          <FileText className="size-5 text-slate-700" aria-hidden="true" />
          <div>
            <h2 className="text-base font-bold text-slate-950">{title}</h2>
            <p className="mt-1 text-sm font-semibold text-slate-600">{description}</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          {selectedBatch ? (
            <button type="button" className={secondaryButtonClass} onClick={onClearBatch}>
              <X className="size-4" aria-hidden="true" />
              Semua Invoice
            </button>
          ) : null}
          {isFetching ? (
            <span className="inline-flex items-center gap-2 text-sm font-semibold text-slate-600">
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Memperbarui
            </span>
          ) : null}
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Nomor</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Periode</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Status</th>
              <th className="px-5 py-3 text-right font-bold text-slate-700">Outstanding</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Jatuh Tempo</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Jurnal</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Command</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {invoices.map((invoice) => (
              <tr key={invoice.id} className="hover:bg-teal-50">
                <td className="whitespace-nowrap px-5 py-4 font-mono text-xs font-bold text-slate-800">{invoice.invoiceNumber}</td>
                <td className="whitespace-nowrap px-5 py-4 font-bold text-slate-950">{invoice.period}</td>
                <td className="whitespace-nowrap px-5 py-4">
                  <StatusBadge label={invoiceStatusLabels[invoice.status]} tone={invoiceStatusTones[invoice.status]} />
                </td>
                <td className="whitespace-nowrap px-5 py-4 text-right font-bold text-slate-950">
                  <MoneyText value={invoice.outstandingAmount} />
                </td>
                <td className="whitespace-nowrap px-5 py-4 text-slate-700">{formatDate(invoice.dueDate)}</td>
                <td className="whitespace-nowrap px-5 py-4 font-mono text-xs text-slate-600">{shortId(invoice.issueJournalEntryId)}</td>
                <td className="whitespace-nowrap px-5 py-4">
                  <div className="flex flex-wrap items-center gap-2">
                    <button
                      type="button"
                      className={secondaryButtonClass}
                      disabled={!canViewInvoiceDocument(permissions)}
                      onClick={() => openDocument(invoice)}
                    >
                      <FileText className="size-4" aria-hidden="true" />
                      Dokumen
                    </button>
                    <button
                      type="button"
                      className={dangerButtonClass}
                      disabled={!canIssueInvoice(invoice, permissions) || issueMutation.isPending}
                      onClick={() => openIssue(invoice)}
                    >
                      <Send className="size-4" aria-hidden="true" />
                      Issue
                    </button>
                    <button
                      type="button"
                      className={dangerButtonClass}
                      disabled={!canVoidInvoice(invoice, permissions) || voidMutation.isPending}
                      onClick={() => openVoid(invoice)}
                    >
                      <RotateCcw className="size-4" aria-hidden="true" />
                      Void
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {documentInvoice ? (
        <InvoiceDocumentPanel
          invoice={documentInvoice}
          document={documentQuery.data ?? null}
          isLoading={documentQuery.isLoading}
          error={documentQuery.error}
          onRetry={() => void documentQuery.refetch()}
          onClose={() => setDocumentInvoice(null)}
        />
      ) : null}
      {draft ? (
        <div className="border-t border-slate-200 bg-red-50 p-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-sm font-bold text-red-950">Konfirmasi Issue Invoice</p>
              <p className="mt-1 text-sm font-semibold text-red-800">
                {draft.invoice.invoiceNumber} akan membuat jurnal piutang dan pendapatan.
              </p>
            </div>
            <button type="button" className={secondaryButtonClass} onClick={() => setDraft(null)}>
              <X className="size-4" aria-hidden="true" />
              Batal
            </button>
          </div>
          <div className="mt-4 grid gap-3 md:grid-cols-2">
            <label className="block">
              <span className="text-xs font-bold uppercase text-red-800">Akun Piutang</span>
              <select
                className={inputClass}
                value={draft.receivableAccountId}
                disabled={issueMutation.isPending}
                onChange={(event) => setDraft((current) => (current ? { ...current, receivableAccountId: event.target.value } : current))}
              >
                <option value="">Pilih akun aset</option>
                {receivableAccounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.code} - {account.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="block">
              <span className="text-xs font-bold uppercase text-red-800">Akun Pendapatan</span>
              <select
                className={inputClass}
                value={draft.revenueAccountId}
                disabled={issueMutation.isPending}
                onChange={(event) => setDraft((current) => (current ? { ...current, revenueAccountId: event.target.value } : current))}
              >
                <option value="">Pilih akun pendapatan</option>
                {revenueAccounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.code} - {account.name}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <label className="mt-4 block">
            <span className="text-xs font-bold uppercase text-red-800">Alasan Audit</span>
            <textarea
              className={textareaClass}
              value={draft.reason}
              maxLength={500}
              disabled={issueMutation.isPending}
              onChange={(event) => setDraft((current) => (current ? { ...current, reason: event.target.value } : current))}
            />
          </label>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <button type="button" className={dangerButtonClass} onClick={submitIssue} disabled={issueMutation.isPending}>
              {issueMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Send className="size-4" aria-hidden="true" />}
              Issue Invoice
            </button>
            <StatusBadge label="High Risk" tone="danger" />
          </div>
        </div>
      ) : null}
      {voidDraft ? (
        <div className="border-t border-slate-200 bg-red-50 p-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-sm font-bold text-red-950">Konfirmasi Void Invoice</p>
              <p className="mt-1 text-sm font-semibold text-red-800">
                {voidDraft.invoice.invoiceNumber} akan membalik jurnal issue asli dan menghapus outstanding dari piutang aktif.
              </p>
            </div>
            <button type="button" className={secondaryButtonClass} onClick={() => setVoidDraft(null)}>
              <X className="size-4" aria-hidden="true" />
              Batal
            </button>
          </div>
          <div className="mt-4 grid gap-3 md:grid-cols-3">
            <div className="rounded-lg border border-red-200 bg-white p-3">
              <p className="text-xs font-bold uppercase text-red-700">Outstanding</p>
              <p className="mt-1 text-sm font-black text-red-950">
                <MoneyText value={voidDraft.invoice.outstandingAmount} />
              </p>
            </div>
            <div className="rounded-lg border border-red-200 bg-white p-3">
              <p className="text-xs font-bold uppercase text-red-700">Paid</p>
              <p className="mt-1 text-sm font-black text-red-950">
                <MoneyText value={voidDraft.invoice.paidAmount} />
              </p>
            </div>
            <div className="rounded-lg border border-red-200 bg-white p-3">
              <p className="text-xs font-bold uppercase text-red-700">Journal Trace</p>
              <p className="mt-1 font-mono text-xs font-black text-red-950">{shortId(voidDraft.invoice.issueJournalEntryId)}</p>
            </div>
          </div>
          <label className="mt-4 block">
            <span className="text-xs font-bold uppercase text-red-800">Alasan Audit</span>
            <textarea
              className={textareaClass}
              value={voidDraft.reason}
              maxLength={500}
              disabled={voidMutation.isPending}
              onChange={(event) => setVoidDraft((current) => (current ? { ...current, reason: event.target.value } : current))}
            />
          </label>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <button type="button" className={dangerButtonClass} onClick={submitVoid} disabled={voidMutation.isPending}>
              {voidMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <RotateCcw className="size-4" aria-hidden="true" />}
              Void Invoice
            </button>
            <StatusBadge label="High Risk" tone="danger" />
            <StatusBadge label="Jurnal Pembalik" tone="warning" />
          </div>
        </div>
      ) : null}
      <div className="space-y-3 border-t border-slate-200 p-4">
        {errorMessage ? <InlineMessage type="error" message={errorMessage} /> : null}
        {successMessage ? <InlineMessage type="success" message={successMessage} /> : null}
      </div>
    </div>
  );
}

export function BillingWorkspace() {
  const [period, setPeriod] = useState("");
  const [batchStatus, setBatchStatus] = useState<StatusFilter<BillingBatchStatus>>("ALL");
  const [invoiceStatus, setInvoiceStatus] = useState<StatusFilter<InvoiceStatus>>("ALL");
  const [selectedBatch, setSelectedBatch] = useState<BillingBatch | null>(null);
  const currentUserQuery = useCurrentUser();
  const queryEnabled = currentUserQuery.isSuccess;
  const permissions = useMemo(
    () => resolveFinancialCommandPermissions(currentUserQuery.data?.authorities ?? []).billing,
    [currentUserQuery.data?.authorities]
  );

  const normalizedPeriod = /^\d{4}-\d{2}$/.test(period.trim()) ? period.trim() : undefined;
  const globalInvoiceQueryEnabled = queryEnabled && !selectedBatch;
  const batchesQuery = useBillingBatches(
    {
      period: normalizedPeriod,
      status: batchStatus === "ALL" ? undefined : batchStatus,
      page: 0,
      size: 25
    },
    queryEnabled
  );
  const invoicesQuery = useInvoices(
    {
      period: normalizedPeriod,
      status: invoiceStatus === "ALL" ? undefined : invoiceStatus,
      page: 0,
      size: 25
    },
    globalInvoiceQueryEnabled
  );
  const batchInvoicesQuery = useBatchInvoices(selectedBatch?.id ?? null, queryEnabled && Boolean(selectedBatch));
  const issueReadinessQuery = useBillingBatchIssueReadiness(
    selectedBatch?.id ?? null,
    queryEnabled && Boolean(selectedBatch)
  );
  const accountsQuery = useAccounts({ page: 0, size: 100 }, queryEnabled);

  const batches = useMemo(() => batchesQuery.data?.items ?? [], [batchesQuery.data?.items]);
  const globalInvoices = useMemo(() => invoicesQuery.data?.items ?? [], [invoicesQuery.data?.items]);
  const batchInvoices = useMemo(() => batchInvoicesQuery.data ?? [], [batchInvoicesQuery.data]);
  const invoices = useMemo(() => {
    if (!selectedBatch) {
      return globalInvoices;
    }

    return filterInvoicesByStatus(batchInvoices, invoiceStatus === "ALL" ? undefined : invoiceStatus);
  }, [batchInvoices, globalInvoices, invoiceStatus, selectedBatch]);
  const accounts = useMemo(() => accountsQuery.data?.items ?? [], [accountsQuery.data?.items]);
  const summary = useMemo(() => summarizeBillingWorkspace({ batches, invoices }), [batches, invoices]);
  const invoiceTitle = invoiceScopeTitle(selectedBatch);
  const invoiceDescription = selectedBatch
    ? `${selectedBatch.period} / ${selectedBatch.areaCode}; status invoice difilter pada daftar batch ini.`
    : "Menampilkan invoice berdasarkan filter periode dan status global.";

  function refetchAll() {
    void batchesQuery.refetch();
    if (selectedBatch) {
      void batchInvoicesQuery.refetch();
      void issueReadinessQuery.refetch();
    } else {
      void invoicesQuery.refetch();
    }
    void accountsQuery.refetch();
  }

  if (currentUserQuery.isLoading) {
    return <LoadingSkeleton />;
  }

  if (currentUserQuery.isError) {
    return <ErrorState message={apiErrorMessage(currentUserQuery.error, "Sesi atau otorisasi billing tidak tersedia.")} />;
  }

  const invoiceQuery = selectedBatch ? batchInvoicesQuery : invoicesQuery;
  const isInitialLoading = batchesQuery.isLoading || (!selectedBatch && invoicesQuery.isLoading) || accountsQuery.isLoading;
  const hasError = batchesQuery.isError || invoiceQuery.isError || accountsQuery.isError;

  return (
    <main className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <PageHeader
          title="Billing"
          description="Workspace kontrol untuk generate billing batch, invoice draft, dan issue invoice ke jurnal piutang."
        />
        <div className="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm shadow-sm">
          <div className="flex items-center gap-2 font-bold text-slate-950">
            <ShieldCheck className="size-4 text-slate-600" aria-hidden="true" />
            Billing Control
          </div>
          <p className="mt-1 font-semibold text-slate-600">{currentUserQuery.data?.username}</p>
        </div>
      </div>

      {isInitialLoading ? <LoadingSkeleton /> : null}

      {hasError ? (
        <div className="space-y-3">
          <ErrorState
            message={apiErrorMessage(
              batchesQuery.error ?? invoiceQuery.error ?? accountsQuery.error,
              "Data billing tidak tersedia."
            )}
          />
          <button type="button" onClick={refetchAll} className={secondaryButtonClass}>
            <RotateCcw className="size-4" aria-hidden="true" />
            Muat Ulang
          </button>
        </div>
      ) : null}

      {!isInitialLoading && !hasError ? (
        <>
          <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <SummaryCard label="Batch Completed" value={String(summary.completedBatches)} helper={`${summary.runningOrFailedBatches} running/gagal di halaman ini.`} tone="success" />
            <SummaryCard label="Invoice Draft" value={String(summary.draftInvoices)} helper="Siap di-issue jika akun piutang dan pendapatan valid." tone="warning" />
            <SummaryCard label="Invoice Issued" value={String(summary.issuedInvoices)} helper="Sudah punya dampak piutang dan pendapatan." tone="info" />
            <SummaryCard label="Outstanding" value={<MoneyText value={summary.totalOutstanding} />} helper="Total outstanding pada filter aktif." tone="neutral" />
          </section>

          <BillingFilterToolbar
            period={period}
            batchStatus={batchStatus}
            invoiceStatus={invoiceStatus}
            onPeriodChange={(value) => {
              setPeriod(value);
              setSelectedBatch(null);
            }}
            onBatchStatusChange={(value) => {
              setBatchStatus(value);
              setSelectedBatch(null);
            }}
            onInvoiceStatusChange={setInvoiceStatus}
            onReset={() => {
              setPeriod("");
              setBatchStatus("ALL");
              setInvoiceStatus("ALL");
              setSelectedBatch(null);
            }}
          />

          <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_420px]">
            <div className="space-y-4">
              <BatchTable
                batches={batches}
                isFetching={batchesQuery.isFetching}
                selectedBatchId={selectedBatch?.id ?? null}
                onSelectBatch={setSelectedBatch}
                onClearSelection={() => setSelectedBatch(null)}
              />
              <InvoiceTable
                title={invoiceTitle}
                description={invoiceDescription}
                invoices={invoices}
                accounts={accounts}
                permissions={permissions}
                isFetching={selectedBatch ? batchInvoicesQuery.isFetching : invoicesQuery.isFetching}
                selectedBatch={selectedBatch}
                onClearBatch={() => setSelectedBatch(null)}
              />
            </div>
            <div className="space-y-4">
              <BillingCommandPanel permissions={permissions} />
              <BillingIssueReadinessPanel
                selectedBatch={selectedBatch}
                readiness={issueReadinessQuery.data ?? null}
                isLoading={issueReadinessQuery.isLoading}
                error={issueReadinessQuery.error}
                onRetry={() => void issueReadinessQuery.refetch()}
              />
              <GenerateBatchForm allowed={permissions.canGenerateBilling} />
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
                <div className="flex items-center gap-2 text-sm font-bold text-amber-900">
                  <LockKeyhole className="size-4" aria-hidden="true" />
                  Guardrail
                </div>
                <p className="mt-2 text-sm leading-6 text-amber-900">
                  Generate batch memakai idempotency key. Issue invoice tetap wajib melewati backend permission, akun piutang aset, akun pendapatan, period posting, dan audit reason.
                </p>
              </div>
            </div>
          </section>
        </>
      ) : null}
    </main>
  );
}
