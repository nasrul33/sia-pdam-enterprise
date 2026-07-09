"use client";

import {
  AlertTriangle,
  CalendarClock,
  CheckCircle2,
  Download,
  Eye,
  FileSpreadsheet,
  FileSearch,
  ListFilter,
  Loader2,
  LockKeyhole,
  PencilLine,
  Plus,
  ReceiptText,
  RotateCcw,
  ShieldCheck,
  Trash2,
  Undo2,
  UploadCloud,
  UsersRound,
  WalletCards
} from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { MoneyText } from "@/components/format/money-text";
import { PageHeader } from "@/components/common/page-header";
import { EmptyState } from "@/components/state/empty-state";
import { ErrorState } from "@/components/state/error-state";
import { LoadingSkeleton } from "@/components/state/loading-skeleton";
import { StatusBadge } from "@/components/status/status-badge";
import type { Account } from "@/features/accounting/accounting-schema";
import { useAccounts } from "@/features/accounting/use-accounting";
import { useCurrentUser } from "@/features/auth/use-current-user";
import { apiErrorMessage } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import { resolveFinancialCommandPermissions } from "@/features/security/financial-command-permissions";
import {
  allocationTotalAmount,
  canManageReconciliationHandoffNotes,
  canReadPayments,
  canReconcilePayments,
  canReversePayment,
  canSettleCounterPayment,
  canSignOffPaymentReconciliations,
  counterPaymentErrors,
  bankStatementImportProfileOptions,
  bankStatementImportTemplate,
  parseBankStatementImport,
  parseMoneyInput,
  reconciliationAdjustmentErrors,
  reconciliationEvidenceExportErrors,
  reconciliationReviewRegisterExportFilename,
  reconciliationReviewRegisterFilterErrors,
  reconciliationHandoffNoteErrors,
  reconciliationHandoffAgingBucketExportFilename,
  reconciliationHandoffAgingEvidencePacketExportFilename,
  reconciliationHandoffOwnerDrilldownFilter,
  reconciliationHandoffOwnerSlaExportFilename,
  reconciliationHandoffWorkloadExportFilename,
  reconciliationHandoffWorkloadFilterErrors,
  reconciliationSignOffErrors,
  paymentReconciliationExportErrors,
  reconciliationCompletionErrors,
  reconciliationResolutionErrors,
  reversePaymentErrors,
  summarizeReconciliationHandoffAgingBuckets,
  summarizeReconciliationReviewRegister,
  summarizeReconciliationHandoffWorkload,
  summarizeReconciliationSessionItems,
  summarizeReconciliationMatches,
  summarizePaymentList,
  summarizePaymentWorkspace,
  toClosedResolutionStatus,
  type BankStatementImportProfile,
  type BankStatementCsvRow,
  type CounterPaymentAllocationDraft,
  type CounterPaymentDraft,
  type PaymentReconciliationHandoffNoteDraft,
  type PaymentReconciliationHandoffWorkloadFilterDraft,
  type PaymentReconciliationAdjustmentDraft,
  type ReversePaymentDraft
} from "./payment-workspace-model";
import type {
  ClosedPaymentReconciliationResolutionStatus,
  CreatePaymentReconciliationAdjustmentPayload,
  PaymentReconciliationEvidenceReport,
  PaymentReconciliationHandoffAgingBucketEntry,
  PaymentReconciliationHandoffEscalationPriority,
  PaymentReconciliationHandoffNote,
  PaymentReconciliationHandoffNotePayload,
  PaymentReconciliationHandoffOwnerSlaEntry,
  PaymentReconciliationHandoffStatus,
  PaymentReconciliationHandoffWorkloadEntry,
  PaymentReconciliationMatchReport,
  PaymentReconciliationMatchStatus,
  PaymentReconciliationResolutionStatus,
  PaymentReconciliationReviewRegisterEntry,
  PaymentReconciliationReviewStatus,
  PaymentReconciliationSessionItem,
  PaymentReconciliationSessionStatus,
  PaymentReconciliationSessionSummary,
  PaymentSettlement,
  PaymentStatus,
  PaymentSummary,
  PaymentWebhookEvent,
  PaymentWebhookStatus,
  ReversePaymentPayload,
  SettleCounterPaymentPayload
} from "./payment-schema";
import {
  paymentReconciliationHandoffStatusValues,
  paymentReconciliationReviewStatusValues,
  paymentStatusValues,
  paymentWebhookStatusValues
} from "./payment-schema";
import {
  exportPaymentReconciliationCsv,
  exportPaymentReconciliationEvidenceCsv,
  exportPaymentReconciliationHandoffAgingEvidencePacketCsv,
  exportPaymentReconciliationHandoffAgingBucketsCsv,
  exportPaymentReconciliationHandoffOwnerSlaCsv,
  exportPaymentReconciliationHandoffWorkloadCsv,
  exportPaymentReconciliationReviewRegisterCsv
} from "./payment-api";
import {
  useCompletePaymentReconciliationSession,
  useCreatePaymentReconciliationHandoffNote,
  useCreatePaymentReconciliationAdjustment,
  useCreatePaymentReconciliationSession,
  useMatchPaymentReconciliation,
  usePayment,
  usePaymentReconciliationEvidenceReport,
  usePaymentReconciliationHandoffAgingBuckets,
  usePaymentReconciliationHandoffNotes,
  usePaymentReconciliationHandoffOwnerSla,
  usePaymentReconciliationHandoffWorkload,
  usePaymentReconciliationReviewRegister,
  usePaymentReconciliationSession,
  usePaymentReconciliationSessions,
  usePaymentWebhookEvents,
  usePayments,
  useResolvePaymentReconciliationItem,
  useRevisePaymentReconciliationHandoffNote,
  useReversePayment,
  useSignOffPaymentReconciliationSession,
  useSettleCounterPayment
} from "./use-payments";

type StatusFilter<TStatus extends string> = TStatus | "ALL";
type PaymentPermissions = ReturnType<typeof resolveFinancialCommandPermissions>["payment"];
type FeedbackType = "success" | "error";

type CounterPaymentAllocationForm = CounterPaymentAllocationDraft & {
  clientId: string;
};

type CounterPaymentFormState = Omit<CounterPaymentDraft, "allocations"> & {
  allocations: CounterPaymentAllocationForm[];
};

const webhookStatusLabels: Record<PaymentWebhookStatus, string> = {
  RECEIVED: "Received",
  PROCESSED: "Processed",
  FAILED: "Gagal",
  IGNORED: "Ignored"
};

const paymentStatusLabels: Record<PaymentStatus, string> = {
  PENDING: "Pending",
  SETTLED: "Settled",
  REVERSED: "Reversed",
  FAILED: "Gagal"
};

const paymentStatusTones: Record<PaymentStatus, "success" | "warning" | "danger" | "neutral" | "info"> = {
  PENDING: "warning",
  SETTLED: "success",
  REVERSED: "neutral",
  FAILED: "danger"
};

const reconciliationStatusLabels: Record<PaymentReconciliationMatchStatus, string> = {
  EXACT_MATCH: "Exact",
  PROBABLE_MATCH: "Probable",
  AMOUNT_VARIANCE: "Variance",
  REVERSED_PAYMENT: "Reversed",
  MULTIPLE_CANDIDATES: "Multiple",
  UNMATCHED: "Unmatched"
};

const reconciliationStatusTones: Record<PaymentReconciliationMatchStatus, "success" | "warning" | "danger" | "neutral" | "info"> = {
  EXACT_MATCH: "success",
  PROBABLE_MATCH: "info",
  AMOUNT_VARIANCE: "warning",
  REVERSED_PAYMENT: "neutral",
  MULTIPLE_CANDIDATES: "warning",
  UNMATCHED: "danger"
};

const reconciliationSessionStatusLabels: Record<PaymentReconciliationSessionStatus, string> = {
  OPEN: "Open",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled"
};

const reconciliationSessionStatusTones: Record<PaymentReconciliationSessionStatus, "success" | "warning" | "danger" | "neutral" | "info"> = {
  OPEN: "warning",
  COMPLETED: "success",
  CANCELLED: "neutral"
};

const reconciliationReviewStatusLabels: Record<PaymentReconciliationReviewStatus, string> = {
  PENDING_SIGN_OFF: "Pending Sign-off",
  SIGNED_OFF: "Signed Off"
};

const reconciliationReviewStatusTones: Record<PaymentReconciliationReviewStatus, "success" | "warning" | "danger" | "neutral" | "info"> = {
  PENDING_SIGN_OFF: "warning",
  SIGNED_OFF: "success"
};

const reconciliationHandoffStatusLabels: Record<PaymentReconciliationHandoffStatus, string> = {
  OPEN: "Open",
  IN_PROGRESS: "In Progress",
  CLEARED: "Cleared"
};

const reconciliationHandoffStatusTones: Record<PaymentReconciliationHandoffStatus, "success" | "warning" | "danger" | "neutral" | "info"> = {
  OPEN: "warning",
  IN_PROGRESS: "info",
  CLEARED: "success"
};

const reconciliationHandoffPriorityLabels: Record<PaymentReconciliationHandoffEscalationPriority, string> = {
  CRITICAL: "Critical",
  OVERDUE: "Overdue",
  ACTIVE: "Active",
  CLEARED: "Cleared"
};

const reconciliationHandoffPriorityTones: Record<PaymentReconciliationHandoffEscalationPriority, "success" | "warning" | "danger" | "neutral" | "info"> = {
  CRITICAL: "danger",
  OVERDUE: "warning",
  ACTIVE: "info",
  CLEARED: "success"
};

const reconciliationResolutionStatusLabels: Record<PaymentReconciliationResolutionStatus, string> = {
  OPEN: "Open",
  ACCEPTED: "Accepted",
  RESOLVED: "Resolved",
  IGNORED: "Ignored"
};

const reconciliationResolutionStatusTones: Record<PaymentReconciliationResolutionStatus, "success" | "warning" | "danger" | "neutral" | "info"> = {
  OPEN: "warning",
  ACCEPTED: "success",
  RESOLVED: "info",
  IGNORED: "neutral"
};

const closedResolutionStatuses: ClosedPaymentReconciliationResolutionStatus[] = ["ACCEPTED", "RESOLVED", "IGNORED"];

const webhookStatusTones: Record<PaymentWebhookStatus, "success" | "warning" | "danger" | "neutral" | "info"> = {
  RECEIVED: "info",
  PROCESSED: "success",
  FAILED: "danger",
  IGNORED: "neutral"
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

function newClientId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function newAllocation(): CounterPaymentAllocationForm {
  return {
    clientId: newClientId(),
    invoiceId: "",
    amount: ""
  };
}

function defaultCounterPaymentForm(): CounterPaymentFormState {
  return {
    externalReference: "",
    amount: "",
    paidAt: "",
    allocations: [newAllocation()],
    cashAccountId: "",
    receivableAccountId: "",
    reason: ""
  };
}

const defaultReversePaymentForm: ReversePaymentDraft = {
  paymentId: "",
  cashAccountId: "",
  receivableAccountId: "",
  reason: ""
};

const defaultReconciliationAdjustmentForm: PaymentReconciliationAdjustmentDraft = {
  itemId: "",
  period: "",
  amount: "",
  debitAccountId: "",
  creditAccountId: "",
  reason: ""
};

const defaultReconciliationHandoffNoteForm: PaymentReconciliationHandoffNoteDraft = {
  noteId: null,
  noteText: "",
  handoffOwner: "",
  handoffDueDate: "",
  handoffStatus: "OPEN",
  reason: ""
};

const defaultReconciliationHandoffWorkloadFilter: PaymentReconciliationHandoffWorkloadFilterDraft = {
  handoffStatus: "ALL",
  handoffOwner: "",
  unassignedOnly: false,
  dueFrom: "",
  dueTo: ""
};

function normalizeInput(value: string): string {
  return value.trim();
}

function optionalString(value: string): string | null {
  const normalized = normalizeInput(value);
  return normalized.length > 0 ? normalized : null;
}

function sameActorValue(left: string | null, right: string | null): boolean {
  return Boolean(left?.trim() && right?.trim() && left.trim().toLowerCase() === right.trim().toLowerCase());
}

function counterPaymentIdempotencyKey(): string {
  return `payment-counter-${newClientId()}`.slice(0, 128);
}

function formatDateTime(value: string | null): string {
  if (!value) {
    return "-";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "-";
  }

  return new Intl.DateTimeFormat("id-ID", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}

function shortId(value: string | null): string {
  if (!value) {
    return "-";
  }
  return value.length <= 13 ? value : `${value.slice(0, 8)}...${value.slice(-4)}`;
}

function toInstant(value: string): string {
  return new Date(value).toISOString();
}

function toOptionalInstant(value: string): string | undefined {
  const normalized = value.trim();
  return normalized ? new Date(normalized).toISOString() : undefined;
}

function toSafeOptionalInstant(value: string): string | undefined {
  const normalized = value.trim();
  if (!normalized) {
    return undefined;
  }
  const time = new Date(normalized).getTime();
  return Number.isNaN(time) ? undefined : new Date(time).toISOString();
}

function periodFromInstant(value: string): string {
  const time = new Date(value).getTime();
  if (Number.isNaN(time)) {
    return "";
  }
  return new Date(time).toISOString().slice(0, 7);
}

function defaultAdjustmentAmount(item: PaymentReconciliationSessionItem): string {
  const variance = item.amountVariance === null ? 0 : Math.abs(item.amountVariance);
  const amount = variance > 0 ? variance : item.statementAmount;
  return amount > 0 ? String(amount) : "";
}

function handoffNoteFormFromNote(note: PaymentReconciliationHandoffNote): PaymentReconciliationHandoffNoteDraft {
  return {
    noteId: note.id,
    noteText: note.noteText,
    handoffOwner: note.handoffOwner ?? "",
    handoffDueDate: note.handoffDueDate ?? "",
    handoffStatus: note.handoffStatus,
    reason: ""
  };
}

function handoffNotePayload(form: PaymentReconciliationHandoffNoteDraft): PaymentReconciliationHandoffNotePayload {
  return {
    noteText: normalizeInput(form.noteText),
    handoffOwner: optionalString(form.handoffOwner),
    handoffDueDate: optionalString(form.handoffDueDate),
    handoffStatus: form.handoffStatus,
    reason: normalizeInput(form.reason)
  };
}

function isAdjustmentCandidate(item: PaymentReconciliationSessionItem): boolean {
  return (
    item.resolutionStatus === "ACCEPTED" &&
    item.adjustmentJournalEntryId === null &&
    item.matchStatus !== "EXACT_MATCH" &&
    item.matchStatus !== "PROBABLE_MATCH"
  );
}

function accountLabel(account: Account): string {
  return `${account.code} - ${account.name}`;
}

function downloadTextFile(filename: string, content: string, type: string) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
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

function PaymentCommandPanel({ permissions }: Readonly<{ permissions: PaymentPermissions }>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 p-4">
        <div className="flex items-center gap-2">
          <WalletCards className="size-5 text-sky-700" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Payment Command</h2>
        </div>
      </div>
      <CommandStatus label="Counter Settlement" allowed={permissions.canSettleCounterPayments} highRisk />
      <CommandStatus label="Payment Read" allowed={permissions.canReadPayments} />
      <CommandStatus label="Payment Reconcile" allowed={permissions.canReconcilePayments} />
      <CommandStatus label="Reconciliation Handoff Notes" allowed={permissions.canManageReconciliationHandoffNotes} />
      <CommandStatus label="Reconciliation Sign-off" allowed={permissions.canSignOffPaymentReconciliations} highRisk />
      <CommandStatus label="Payment Reversal" allowed={permissions.canReversePayments} highRisk />
      <CommandStatus label="Webhook Event Read" allowed={permissions.canReadWebhookEvents} />
    </div>
  );
}

function PaymentFilterToolbar({
  provider,
  status,
  canRead,
  onProviderChange,
  onStatusChange,
  onReset
}: Readonly<{
  provider: string;
  status: StatusFilter<PaymentWebhookStatus>;
  canRead: boolean;
  onProviderChange: (provider: string) => void;
  onStatusChange: (status: StatusFilter<PaymentWebhookStatus>) => void;
  onReset: () => void;
}>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="grid gap-3 md:grid-cols-[minmax(180px,1fr)_180px_auto]">
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Provider</span>
          <input
            className={inputClass}
            value={provider}
            maxLength={64}
            disabled={!canRead}
            onChange={(event) => onProviderChange(event.target.value)}
            placeholder="payment-gateway"
          />
        </label>
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Status Webhook</span>
          <select
            className={inputClass}
            value={status}
            disabled={!canRead}
            onChange={(event) => onStatusChange(event.target.value as StatusFilter<PaymentWebhookStatus>)}
          >
            <option value="ALL">Semua</option>
            {paymentWebhookStatusValues.map((value) => (
              <option key={value} value={value}>
                {webhookStatusLabels[value]}
              </option>
            ))}
          </select>
        </label>
        <div className="flex items-end">
          <button type="button" onClick={onReset} className={secondaryButtonClass} disabled={!canRead}>
            <RotateCcw className="size-4" aria-hidden="true" />
            Reset
          </button>
        </div>
      </div>
    </div>
  );
}

function PaymentListToolbar({
  channel,
  status,
  canRead,
  onChannelChange,
  onStatusChange,
  onReset
}: Readonly<{
  channel: string;
  status: StatusFilter<PaymentStatus>;
  canRead: boolean;
  onChannelChange: (channel: string) => void;
  onStatusChange: (status: StatusFilter<PaymentStatus>) => void;
  onReset: () => void;
}>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="mb-3 flex items-center gap-2">
        <ListFilter className="size-5 text-sky-700" aria-hidden="true" />
        <h2 className="text-base font-bold text-slate-950">Payment Register</h2>
      </div>
      <div className="grid gap-3 md:grid-cols-[minmax(180px,1fr)_180px_auto]">
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Channel</span>
          <input
            className={inputClass}
            value={channel}
            maxLength={64}
            disabled={!canRead}
            onChange={(event) => onChannelChange(event.target.value)}
            placeholder="COUNTER"
          />
        </label>
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Status Payment</span>
          <select
            className={inputClass}
            value={status}
            disabled={!canRead}
            onChange={(event) => onStatusChange(event.target.value as StatusFilter<PaymentStatus>)}
          >
            <option value="ALL">Semua</option>
            {paymentStatusValues.map((value) => (
              <option key={value} value={value}>
                {paymentStatusLabels[value]}
              </option>
            ))}
          </select>
        </label>
        <div className="flex items-end">
          <button type="button" onClick={onReset} className={secondaryButtonClass} disabled={!canRead}>
            <RotateCcw className="size-4" aria-hidden="true" />
            Reset
          </button>
        </div>
      </div>
    </div>
  );
}

function PaymentTable({
  payments,
  canRead,
  isFetching,
  selectedPaymentId,
  page,
  totalPages,
  totalItems,
  onSelect,
  onPrevious,
  onNext
}: Readonly<{
  payments: PaymentSummary[];
  canRead: boolean;
  isFetching: boolean;
  selectedPaymentId: string | null;
  page: number;
  totalPages: number;
  totalItems: number;
  onSelect: (paymentId: string) => void;
  onPrevious: () => void;
  onNext: () => void;
}>) {
  if (!canRead) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2">
          <LockKeyhole className="size-5 text-slate-600" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Payment Register Terkunci</h2>
        </div>
        <p className="mt-2 text-sm leading-6 text-slate-700">
          Authority <span className="font-mono font-bold">payment.read</span> diperlukan untuk membaca settlement,
          reversal, receipt, allocation, dan jejak jurnal.
        </p>
      </div>
    );
  }

  if (payments.length === 0) {
    return (
      <EmptyState
        title="Payment register belum tersedia"
        description="Settlement loket atau payment provider yang sudah masuk akan muncul sesuai filter aktif."
      />
    );
  }

  const currentPage = page + 1;
  const pageCount = Math.max(totalPages, 1);

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
        <div className="flex items-center gap-2">
          <ReceiptText className="size-5 text-slate-700" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Payment List</h2>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-sm font-semibold text-slate-600">{totalItems} payment</span>
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
              <th className="px-5 py-3 text-left font-bold text-slate-700">Payment</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Channel</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Status</th>
              <th className="px-5 py-3 text-right font-bold text-slate-700">Nominal</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Paid</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Settlement Journal</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Reversal Journal</th>
              <th className="px-5 py-3 text-right font-bold text-slate-700">Aksi</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {payments.map((payment) => (
              <tr key={payment.id} className={cn("hover:bg-slate-50", selectedPaymentId === payment.id ? "bg-sky-50" : "")}>
                <td className="whitespace-nowrap px-5 py-4">
                  <p className="font-bold text-slate-950">{payment.paymentNumber}</p>
                  <p className="font-mono text-xs text-slate-500">{shortId(payment.externalReference)}</p>
                </td>
                <td className="whitespace-nowrap px-5 py-4 font-bold text-slate-800">{payment.channel}</td>
                <td className="whitespace-nowrap px-5 py-4">
                  <StatusBadge label={paymentStatusLabels[payment.status]} tone={paymentStatusTones[payment.status]} />
                </td>
                <td className="whitespace-nowrap px-5 py-4 text-right font-bold text-slate-950">
                  <MoneyText value={payment.amount} />
                </td>
                <td className="whitespace-nowrap px-5 py-4 text-slate-700">{formatDateTime(payment.paidAt)}</td>
                <td className="whitespace-nowrap px-5 py-4 font-mono text-xs text-slate-600">
                  {shortId(payment.settlementJournalEntryId)}
                </td>
                <td className="whitespace-nowrap px-5 py-4 font-mono text-xs text-slate-600">
                  {shortId(payment.reversalJournalEntryId)}
                </td>
                <td className="whitespace-nowrap px-5 py-4 text-right">
                  <button type="button" className={secondaryButtonClass} onClick={() => onSelect(payment.id)}>
                    <Eye className="size-4" aria-hidden="true" />
                    Detail
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 px-5 py-4">
        <p className="text-sm font-semibold text-slate-600">
          Halaman {currentPage} dari {pageCount}
        </p>
        <div className="flex items-center gap-2">
          <button type="button" className={secondaryButtonClass} disabled={page === 0} onClick={onPrevious}>
            Sebelumnya
          </button>
          <button type="button" className={secondaryButtonClass} disabled={totalPages === 0 || currentPage >= totalPages} onClick={onNext}>
            Berikutnya
          </button>
        </div>
      </div>
    </div>
  );
}

function PaymentDetailPanel({
  payment,
  canRead,
  selectedPaymentId,
  isLoading,
  isFetching,
  error,
  onClear
}: Readonly<{
  payment: PaymentSettlement | undefined;
  canRead: boolean;
  selectedPaymentId: string | null;
  isLoading: boolean;
  isFetching: boolean;
  error: unknown;
  onClear: () => void;
}>) {
  if (!canRead) {
    return null;
  }

  if (!selectedPaymentId) {
    return (
      <div className="rounded-lg border border-dashed border-slate-300 bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2">
          <FileSearch className="size-5 text-slate-600" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Detail Payment</h2>
        </div>
        <p className="mt-2 text-sm leading-6 text-slate-700">
          Pilih payment dari register untuk melihat receipt, allocation invoice, dan journal traceability.
        </p>
      </div>
    );
  }

  if (isLoading) {
    return <LoadingSkeleton />;
  }

  if (error) {
    return <ErrorState message={apiErrorMessage(error, "Detail payment tidak tersedia.")} />;
  }

  if (!payment) {
    return null;
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-200 p-5">
        <div>
          <div className="flex items-center gap-2">
            <FileSearch className="size-5 text-sky-700" aria-hidden="true" />
            <h2 className="text-base font-bold text-slate-950">Detail Payment</h2>
          </div>
          <p className="mt-1 font-mono text-sm font-semibold text-slate-600">{payment.id}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {isFetching ? (
            <span className="inline-flex items-center gap-2 text-sm font-semibold text-slate-600">
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Memperbarui
            </span>
          ) : null}
          <StatusBadge label={paymentStatusLabels[payment.status]} tone={paymentStatusTones[payment.status]} />
          <button type="button" className={secondaryButtonClass} onClick={onClear}>
            Tutup
          </button>
        </div>
      </div>

      <div className="grid gap-4 p-5 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <dl className="grid gap-3 sm:grid-cols-2">
            <TraceItem label="Payment Number" value={payment.paymentNumber} />
            <TraceItem label="Channel" value={payment.channel} />
            <TraceItem label="External Reference" value={payment.externalReference ?? "-"} />
            <TraceItem label="Idempotency" value={payment.idempotencyKey} />
            <TraceItem label="Paid At" value={formatDateTime(payment.paidAt)} />
            <TraceItem label="Settled At" value={formatDateTime(payment.settledAt)} />
            <TraceItem label="Reversed At" value={formatDateTime(payment.reversedAt)} />
            <TraceItem label="Receipt" value={payment.receipt.receiptNumber} />
            <TraceItem label="Settlement Journal" value={payment.settlementJournalEntryId ?? "-"} />
            <TraceItem label="Reversal Journal" value={payment.reversalJournalEntryId ?? "-"} />
          </dl>
          {payment.reversalReason ? (
            <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm font-semibold text-amber-900">
              {payment.reversalReason}
            </div>
          ) : null}
        </div>
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
          <p className="text-xs font-bold uppercase text-slate-600">Nominal Payment</p>
          <p className="mt-2 text-2xl font-bold text-slate-950">
            <MoneyText value={payment.amount} />
          </p>
          <p className="mt-3 text-xs font-bold uppercase text-slate-600">Receipt Issued</p>
          <p className="mt-1 text-sm font-semibold text-slate-700">{formatDateTime(payment.receipt.issuedAt)}</p>
        </div>
      </div>

      <div className="border-t border-slate-200 p-5">
        <h3 className="text-sm font-bold text-slate-950">Allocation Trace</h3>
        {payment.allocations.length === 0 ? (
          <p className="mt-2 text-sm font-semibold text-slate-600">Tidak ada allocation untuk payment ini.</p>
        ) : (
          <div className="mt-3 overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50">
                <tr>
                  <th className="px-4 py-3 text-left font-bold text-slate-700">Invoice ID</th>
                  <th className="px-4 py-3 text-left font-bold text-slate-700">Allocation ID</th>
                  <th className="px-4 py-3 text-right font-bold text-slate-700">Nominal</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {payment.allocations.map((allocation) => (
                  <tr key={allocation.id}>
                    <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-slate-700">{allocation.invoiceId}</td>
                    <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-slate-600">{allocation.id}</td>
                    <td className="whitespace-nowrap px-4 py-3 text-right font-bold text-slate-950">
                      <MoneyText value={allocation.amount} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

function TraceItem({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <dt className="text-xs font-bold uppercase text-slate-600">{label}</dt>
      <dd className="mt-1 break-words font-mono text-xs font-semibold text-slate-900">{value}</dd>
    </div>
  );
}

function PaymentReconciliationReviewRegisterPanel({
  allowed,
  canManageNotes
}: Readonly<{ allowed: boolean; canManageNotes: boolean }>) {
  const [signOffStatus, setSignOffStatus] = useState<StatusFilter<PaymentReconciliationReviewStatus>>("ALL");
  const [completedFrom, setCompletedFrom] = useState("");
  const [completedTo, setCompletedTo] = useState("");
  const [page, setPage] = useState(0);
  const [isExportingHandoff, setIsExportingHandoff] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [selectedReviewSessionId, setSelectedReviewSessionId] = useState<string | null>(null);
  const filterErrors = reconciliationReviewRegisterFilterErrors({ signOffStatus, completedFrom, completedTo });
  const filtersValid = filterErrors.length === 0;
  const filters = useMemo(
    () => ({
      page,
      size: 5,
      signOffStatus: signOffStatus === "ALL" ? undefined : signOffStatus,
      completedFrom: filtersValid ? toSafeOptionalInstant(completedFrom) : undefined,
      completedTo: filtersValid ? toSafeOptionalInstant(completedTo) : undefined
    }),
    [completedFrom, completedTo, filtersValid, page, signOffStatus]
  );
  const reviewRegisterQuery = usePaymentReconciliationReviewRegister(filters, allowed && filtersValid);
  const entries = useMemo(() => reviewRegisterQuery.data?.items ?? [], [reviewRegisterQuery.data?.items]);
  const selectedReviewEntry = useMemo(
    () => entries.find((entry) => entry.sessionId === selectedReviewSessionId) ?? null,
    [entries, selectedReviewSessionId]
  );
  const summary = useMemo(() => summarizeReconciliationReviewRegister(entries), [entries]);

  function resetFilters() {
    setSignOffStatus("ALL");
    setCompletedFrom("");
    setCompletedTo("");
    setPage(0);
    setExportError(null);
    setSelectedReviewSessionId(null);
  }

  async function handleExportHandoff() {
    setExportError(null);
    if (!filtersValid) {
      setExportError(filterErrors[0] ?? "Filter export review register tidak valid.");
      return;
    }

    setIsExportingHandoff(true);
    try {
      const csv = await exportPaymentReconciliationReviewRegisterCsv(filters);
      downloadTextFile(
        reconciliationReviewRegisterExportFilename({ signOffStatus, completedFrom, completedTo }),
        csv,
        "text/csv;charset=utf-8"
      );
    } catch (error) {
      setExportError(apiErrorMessage(error, "Gagal export handoff register rekonsiliasi."));
    } finally {
      setIsExportingHandoff(false);
    }
  }

  if (!allowed) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2">
          <LockKeyhole className="size-5 text-slate-600" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Review Register Terkunci</h2>
        </div>
        <p className="mt-2 text-sm leading-6 text-slate-700">
          Authority <span className="font-mono font-bold">payment.reconcile</span> diperlukan untuk membaca register review rekonsiliasi.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 p-4">
        <div className="flex items-center gap-2">
          <FileSearch className="size-5 text-sky-700" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Reconciliation Review Register</h2>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {reviewRegisterQuery.isFetching ? (
            <span className="inline-flex items-center gap-2 text-sm font-semibold text-slate-600">
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Memperbarui
            </span>
          ) : null}
          <button
            type="button"
            className={secondaryButtonClass}
            disabled={!filtersValid || isExportingHandoff}
            onClick={handleExportHandoff}
          >
            {isExportingHandoff ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Download className="size-4" aria-hidden="true" />}
            Export Handoff
          </button>
        </div>
      </div>

      <div className="grid gap-3 p-4">
        <div className="grid gap-2 md:grid-cols-3 xl:grid-cols-6">
          <MetricPill label="Evidence" value={summary.totalEvidence} tone="info" />
          <MetricPill label="Pending" value={summary.pendingSignOff} tone="warning" />
          <MetricPill label="Signed" value={summary.signedOff} tone="success" />
          <MetricPill label="SLA" value={summary.overduePendingSignOff} tone={summary.overduePendingSignOff > 0 ? "danger" : "success"} />
          <MetricPill label="Exception" value={summary.exceptionItems} tone={summary.exceptionItems > 0 ? "warning" : "success"} />
          <MetricPill label="Adjusted" value={summary.adjustedItems} tone={summary.adjustedItems > 0 ? "info" : "neutral"} />
        </div>

        <div className="grid gap-3 md:grid-cols-[180px_1fr_1fr_auto]">
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Sign-off</span>
            <select
              className={inputClass}
              value={signOffStatus}
              onChange={(event) => {
                setSignOffStatus(event.target.value as StatusFilter<PaymentReconciliationReviewStatus>);
                setPage(0);
              }}
            >
              <option value="ALL">Semua</option>
              {paymentReconciliationReviewStatusValues.map((value) => (
                <option key={value} value={value}>
                  {reconciliationReviewStatusLabels[value]}
                </option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Completed From</span>
            <input
              type="datetime-local"
              className={inputClass}
              value={completedFrom}
              onChange={(event) => {
                setCompletedFrom(event.target.value);
                setPage(0);
              }}
            />
          </label>
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Completed To</span>
            <input
              type="datetime-local"
              className={inputClass}
              value={completedTo}
              onChange={(event) => {
                setCompletedTo(event.target.value);
                setPage(0);
              }}
            />
          </label>
          <div className="flex items-end">
            <button type="button" className={secondaryButtonClass} onClick={resetFilters}>
              <RotateCcw className="size-4" aria-hidden="true" />
              Reset
            </button>
          </div>
        </div>

        {filterErrors.length > 0 ? <InlineMessage type="error" message={filterErrors[0]} /> : null}
        {exportError ? <InlineMessage type="error" message={exportError} /> : null}

        {reviewRegisterQuery.isLoading ? <LoadingSkeleton /> : null}

        {reviewRegisterQuery.isError ? (
          <div className="grid gap-3">
            <InlineMessage
              type="error"
              message={apiErrorMessage(reviewRegisterQuery.error, "Register review rekonsiliasi tidak tersedia.")}
            />
            <button type="button" className={secondaryButtonClass} onClick={() => void reviewRegisterQuery.refetch()}>
              <RotateCcw className="size-4" aria-hidden="true" />
              Muat Ulang
            </button>
          </div>
        ) : null}

        {!reviewRegisterQuery.isLoading && !reviewRegisterQuery.isError && entries.length === 0 ? (
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <p className="text-sm font-bold text-slate-950">Register review belum memiliki evidence</p>
            <p className="mt-1 text-sm leading-6 text-slate-600">
              Completed reconciliation session akan muncul setelah evidence tersedia.
            </p>
          </div>
        ) : null}

        {!reviewRegisterQuery.isLoading && !reviewRegisterQuery.isError && entries.length > 0 ? (
          <>
            <ReconciliationReviewRegisterTable
              entries={entries}
              selectedSessionId={selectedReviewSessionId}
              onSelect={setSelectedReviewSessionId}
            />
            <ReconciliationHandoffNotesPanel
              key={selectedReviewEntry?.sessionId ?? "no-reconciliation-review-selection"}
              entry={selectedReviewEntry}
              canManageNotes={canManageNotes}
            />
          </>
        ) : null}

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-3">
          <span className="text-sm font-semibold text-slate-600">
            {reviewRegisterQuery.data?.totalItems ?? 0} evidence
          </span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className={secondaryButtonClass}
              disabled={page <= 0 || reviewRegisterQuery.isFetching}
              onClick={() => setPage((current) => Math.max(current - 1, 0))}
            >
              Previous
            </button>
            <span className="text-sm font-bold text-slate-700">
              {(reviewRegisterQuery.data?.page ?? page) + 1} / {Math.max(reviewRegisterQuery.data?.totalPages ?? 1, 1)}
            </span>
            <button
              type="button"
              className={secondaryButtonClass}
              disabled={(reviewRegisterQuery.data?.page ?? page) + 1 >= Math.max(reviewRegisterQuery.data?.totalPages ?? 1, 1) || reviewRegisterQuery.isFetching}
              onClick={() => setPage((current) => current + 1)}
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function ReconciliationReviewRegisterTable({
  entries,
  selectedSessionId,
  onSelect
}: Readonly<{
  entries: PaymentReconciliationReviewRegisterEntry[];
  selectedSessionId: string | null;
  onSelect: (sessionId: string) => void;
}>) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50">
          <tr>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Session</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Completion</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Review</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">Exception</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">Adjustment</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">Variance</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">SLA</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Handoff</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">Action</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 bg-white">
          {entries.map((entry) => (
            <tr
              key={entry.sessionId}
              className={cn("hover:bg-slate-50", selectedSessionId === entry.sessionId ? "bg-sky-50" : "bg-white")}
            >
              <td className="min-w-56 px-4 py-3">
                <p className="font-mono text-xs font-bold text-slate-950">{entry.sessionNumber}</p>
                <p className="mt-1 text-xs font-semibold text-slate-600">{entry.bankAccountReference ?? "-"}</p>
              </td>
              <td className="min-w-44 px-4 py-3 text-slate-700">
                <p className="text-xs font-semibold">{formatDateTime(entry.completedAt)}</p>
                <p className="mt-1 text-xs text-slate-600">Creator: {entry.createdBy}</p>
              </td>
              <td className="min-w-44 px-4 py-3">
                <StatusBadge
                  label={reconciliationReviewStatusLabels[entry.reviewStatus]}
                  tone={reconciliationReviewStatusTones[entry.reviewStatus]}
                />
                <p className="mt-1 text-xs text-slate-600">{entry.signedOffBy ?? "Belum approval"}</p>
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-right font-bold text-slate-950">{entry.exceptionItems}</td>
              <td className="whitespace-nowrap px-4 py-3 text-right font-bold text-slate-950">{entry.adjustedItems}</td>
              <td className="whitespace-nowrap px-4 py-3 text-right font-bold text-slate-950">
                <MoneyText value={entry.totalVariance} />
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-right">
                <StatusBadge
                  label={entry.reviewStatus === "SIGNED_OFF" ? "Closed" : `${entry.pendingSignOffAgeDays} hari`}
                  tone={entry.reviewStatus === "SIGNED_OFF" ? "success" : entry.pendingSignOffAgeDays >= 3 ? "danger" : "warning"}
                />
              </td>
              <td className="min-w-56 px-4 py-3">
                {entry.handoffStatus ? (
                  <div className="grid gap-1">
                    <StatusBadge
                      label={reconciliationHandoffStatusLabels[entry.handoffStatus]}
                      tone={reconciliationHandoffStatusTones[entry.handoffStatus]}
                    />
                    <p className="max-w-72 break-words text-xs font-semibold text-slate-700">{entry.reviewerNotes}</p>
                    <p className="text-xs text-slate-600">
                      {entry.handoffOwner ?? "No owner"} {entry.handoffDueDate ? `- ${entry.handoffDueDate}` : ""}
                    </p>
                  </div>
                ) : (
                  <StatusBadge label="No Note" tone="neutral" />
                )}
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-right">
                <button type="button" className={secondaryButtonClass} onClick={() => onSelect(entry.sessionId)}>
                  <Eye className="size-4" aria-hidden="true" />
                  Notes
                  {entry.handoffNoteCount > 0 ? ` (${entry.handoffNoteCount})` : ""}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ReconciliationHandoffNotesPanel({
  entry,
  canManageNotes
}: Readonly<{
  entry: PaymentReconciliationReviewRegisterEntry | null;
  canManageNotes: boolean;
}>) {
  const [form, setForm] = useState<PaymentReconciliationHandoffNoteDraft>(defaultReconciliationHandoffNoteForm);
  const [localError, setLocalError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const notesQuery = usePaymentReconciliationHandoffNotes(entry?.sessionId ?? null, Boolean(entry));
  const createMutation = useCreatePaymentReconciliationHandoffNote();
  const reviseMutation = useRevisePaymentReconciliationHandoffNote();
  const notes = notesQuery.data ?? [];
  const isRevisionMode = Boolean(form.noteId);
  const busy = createMutation.isPending || reviseMutation.isPending;
  const disabled = !entry || !canManageNotes || busy;
  const errorMessage =
    localError ??
    (createMutation.isError ? apiErrorMessage(createMutation.error, "Gagal menyimpan handoff note.") : null) ??
    (reviseMutation.isError ? apiErrorMessage(reviseMutation.error, "Gagal merevisi handoff note.") : null);

  function resetForm() {
    setForm(defaultReconciliationHandoffNoteForm);
    setLocalError(null);
    setSuccessMessage(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLocalError(null);
    setSuccessMessage(null);
    createMutation.reset();
    reviseMutation.reset();

    if (!entry) {
      setLocalError("Pilih session review register terlebih dahulu.");
      return;
    }
    if (!canManageNotes) {
      setLocalError("User tidak memiliki permission payment.reconciliation.handoff-note.");
      return;
    }

    const errors = reconciliationHandoffNoteErrors(form);
    if (errors.length > 0) {
      setLocalError(errors[0]);
      return;
    }

    const payload = handoffNotePayload(form);
    try {
      if (form.noteId) {
        await reviseMutation.mutateAsync({
          sessionId: entry.sessionId,
          noteId: form.noteId,
          payload
        });
        setSuccessMessage("Handoff note berhasil direvisi dan revision history tersimpan.");
      } else {
        await createMutation.mutateAsync({
          sessionId: entry.sessionId,
          payload
        });
        setSuccessMessage("Handoff note berhasil dibuat.");
      }
      setForm(defaultReconciliationHandoffNoteForm);
    } catch {
      return;
    }
  }

  if (!entry) {
    return (
      <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
        <div className="flex items-center gap-2">
          <ReceiptText className="size-5 text-slate-600" aria-hidden="true" />
          <p className="text-sm font-bold text-slate-950">Pilih session untuk melihat handoff notes</p>
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-200 p-4">
        <div>
          <div className="flex items-center gap-2">
            <ReceiptText className="size-5 text-sky-700" aria-hidden="true" />
            <h3 className="text-sm font-bold text-slate-950">Controlled Handoff Notes</h3>
          </div>
          <p className="mt-1 font-mono text-xs font-semibold text-slate-600">{entry.sessionNumber}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {notesQuery.isFetching ? (
            <span className="inline-flex items-center gap-2 text-xs font-semibold text-slate-600">
              <Loader2 className="size-3 animate-spin" aria-hidden="true" />
              Refresh
            </span>
          ) : null}
          <StatusBadge label={canManageNotes ? "Mutation Aktif" : "Read Only"} tone={canManageNotes ? "success" : "neutral"} />
        </div>
      </div>

      <div className="grid gap-4 p-4">
        {notesQuery.isLoading ? <LoadingSkeleton /> : null}
        {notesQuery.isError ? (
          <div className="grid gap-3">
            <InlineMessage type="error" message={apiErrorMessage(notesQuery.error, "Handoff notes tidak tersedia.")} />
            <button type="button" className={secondaryButtonClass} onClick={() => void notesQuery.refetch()}>
              <RotateCcw className="size-4" aria-hidden="true" />
              Muat Ulang
            </button>
          </div>
        ) : null}

        {!notesQuery.isLoading && !notesQuery.isError && notes.length === 0 ? (
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <p className="text-sm font-bold text-slate-950">Belum ada handoff note</p>
            <p className="mt-1 text-sm leading-6 text-slate-600">
              Catatan reviewer akan tersimpan dengan actor, timestamp, reason, dan revision history.
            </p>
          </div>
        ) : null}

        {!notesQuery.isLoading && !notesQuery.isError && notes.length > 0 ? (
          <div className="grid gap-3">
            {notes.map((note) => (
              <div key={note.id} className="rounded-lg border border-slate-200 bg-white p-3">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <StatusBadge
                        label={reconciliationHandoffStatusLabels[note.handoffStatus]}
                        tone={reconciliationHandoffStatusTones[note.handoffStatus]}
                      />
                      <span className="text-xs font-semibold text-slate-600">
                        {note.handoffOwner ?? "No owner"} {note.handoffDueDate ? `- due ${note.handoffDueDate}` : ""}
                      </span>
                    </div>
                    <p className="mt-2 text-sm font-semibold leading-6 text-slate-950">{note.noteText}</p>
                    <p className="mt-1 text-xs font-semibold text-slate-600">
                      Updated by {note.updatedBy} - {formatDateTime(note.updatedAt)}
                    </p>
                  </div>
                  {canManageNotes ? (
                    <button
                      type="button"
                      className={secondaryButtonClass}
                      disabled={busy}
                      onClick={() => {
                        setForm(handoffNoteFormFromNote(note));
                        setLocalError(null);
                        setSuccessMessage(null);
                      }}
                    >
                      <PencilLine className="size-4" aria-hidden="true" />
                      Revise
                    </button>
                  ) : null}
                </div>
                {note.revisions.length > 0 ? (
                  <div className="mt-3 overflow-x-auto rounded-lg border border-slate-200">
                    <table className="min-w-full divide-y divide-slate-200 text-xs">
                      <thead className="bg-slate-50">
                        <tr>
                          <th className="px-3 py-2 text-left font-bold text-slate-700">Rev</th>
                          <th className="px-3 py-2 text-left font-bold text-slate-700">Reason</th>
                          <th className="px-3 py-2 text-left font-bold text-slate-700">Actor</th>
                          <th className="px-3 py-2 text-left font-bold text-slate-700">Changed</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100 bg-white">
                        {note.revisions.map((revision) => (
                          <tr key={revision.id}>
                            <td className="whitespace-nowrap px-3 py-2 font-mono font-bold text-slate-950">
                              #{revision.revisionNumber}
                            </td>
                            <td className="min-w-64 px-3 py-2 font-semibold text-slate-700">{revision.reason}</td>
                            <td className="whitespace-nowrap px-3 py-2 text-slate-600">{revision.changedBy}</td>
                            <td className="whitespace-nowrap px-3 py-2 text-slate-600">{formatDateTime(revision.changedAt)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : null}
              </div>
            ))}
          </div>
        ) : null}

        {canManageNotes ? (
          <form onSubmit={handleSubmit} className="grid gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-xs font-bold uppercase text-slate-600">
                  {isRevisionMode ? "Revise Handoff Note" : "Create Handoff Note"}
                </p>
                <p className="mt-1 text-sm font-semibold text-slate-700">
                  {isRevisionMode ? shortId(form.noteId) : "New controlled note"}
                </p>
              </div>
              <button type="button" className={secondaryButtonClass} disabled={busy} onClick={resetForm}>
                <RotateCcw className="size-4" aria-hidden="true" />
                Reset
              </button>
            </div>
            <label className="block">
              <span className="text-xs font-bold uppercase text-slate-600">Reviewer Note</span>
              <textarea
                className={textareaClass}
                value={form.noteText}
                maxLength={2000}
                disabled={disabled}
                onChange={(event) => setForm((current) => ({ ...current, noteText: event.target.value }))}
              />
            </label>
            <div className="grid gap-3 md:grid-cols-3">
              <label className="block">
                <span className="text-xs font-bold uppercase text-slate-600">Owner</span>
                <input
                  className={inputClass}
                  value={form.handoffOwner}
                  maxLength={128}
                  disabled={disabled}
                  onChange={(event) => setForm((current) => ({ ...current, handoffOwner: event.target.value }))}
                />
              </label>
              <label className="block">
                <span className="text-xs font-bold uppercase text-slate-600">Due Date</span>
                <input
                  type="date"
                  className={inputClass}
                  value={form.handoffDueDate}
                  disabled={disabled}
                  onChange={(event) => setForm((current) => ({ ...current, handoffDueDate: event.target.value }))}
                />
              </label>
              <label className="block">
                <span className="text-xs font-bold uppercase text-slate-600">Status</span>
                <select
                  className={inputClass}
                  value={form.handoffStatus}
                  disabled={disabled}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      handoffStatus: event.target.value as PaymentReconciliationHandoffStatus
                    }))
                  }
                >
                  {paymentReconciliationHandoffStatusValues.map((value) => (
                    <option key={value} value={value}>
                      {reconciliationHandoffStatusLabels[value]}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <label className="block">
              <span className="text-xs font-bold uppercase text-slate-600">Reason</span>
              <textarea
                className={textareaClass}
                value={form.reason}
                maxLength={500}
                disabled={disabled}
                onChange={(event) => setForm((current) => ({ ...current, reason: event.target.value }))}
              />
            </label>
            {errorMessage ? <InlineMessage type="error" message={errorMessage} /> : null}
            {successMessage ? <InlineMessage type="success" message={successMessage} /> : null}
            <button type="submit" className={primaryButtonClass} disabled={disabled}>
              {busy ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Plus className="size-4" aria-hidden="true" />}
              {isRevisionMode ? "Simpan Revisi" : "Simpan Note"}
            </button>
          </form>
        ) : (
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
            <p className="text-sm font-bold text-slate-950">Mode baca saja</p>
            <p className="mt-1 text-sm leading-6 text-slate-600">
              Authority <span className="font-mono font-bold">payment.reconciliation.handoff-note</span> diperlukan untuk membuat atau merevisi catatan.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

function PaymentReconciliationHandoffWorkloadPanel({ allowed }: Readonly<{ allowed: boolean }>) {
  const [filter, setFilter] = useState<PaymentReconciliationHandoffWorkloadFilterDraft>(
    defaultReconciliationHandoffWorkloadFilter
  );
  const [page, setPage] = useState(0);
  const [isExporting, setIsExporting] = useState(false);
  const [isExportingOwnerSla, setIsExportingOwnerSla] = useState(false);
  const [isExportingAgingBuckets, setIsExportingAgingBuckets] = useState(false);
  const [isExportingEvidencePacket, setIsExportingEvidencePacket] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const filterErrors = reconciliationHandoffWorkloadFilterErrors(filter);
  const filtersValid = filterErrors.length === 0;
  const ownerSlaFilters = useMemo(
    () => ({
      handoffStatus: filter.handoffStatus === "ALL" ? undefined : filter.handoffStatus,
      handoffOwner: filtersValid && !filter.unassignedOnly ? optionalString(filter.handoffOwner) ?? undefined : undefined,
      unassignedOnly: filter.unassignedOnly || undefined,
      dueFrom: filtersValid ? optionalString(filter.dueFrom) ?? undefined : undefined,
      dueTo: filtersValid ? optionalString(filter.dueTo) ?? undefined : undefined
    }),
    [filter, filtersValid]
  );
  const filters = useMemo(
    () => ({
      page,
      size: 8,
      ...ownerSlaFilters
    }),
    [ownerSlaFilters, page]
  );
  const workloadQuery = usePaymentReconciliationHandoffWorkload(filters, allowed && filtersValid);
  const ownerSlaQuery = usePaymentReconciliationHandoffOwnerSla(ownerSlaFilters, allowed && filtersValid);
  const agingBucketQuery = usePaymentReconciliationHandoffAgingBuckets(ownerSlaFilters, allowed && filtersValid);
  const entries = useMemo(() => workloadQuery.data?.items ?? [], [workloadQuery.data?.items]);
  const summary = useMemo(() => summarizeReconciliationHandoffWorkload(entries), [entries]);
  const ownerSlaEntries = useMemo(() => ownerSlaQuery.data?.owners ?? [], [ownerSlaQuery.data?.owners]);
  const agingBucketEntries = useMemo(() => agingBucketQuery.data?.owners ?? [], [agingBucketQuery.data?.owners]);
  const agingBucketSummary = useMemo(
    () => summarizeReconciliationHandoffAgingBuckets(agingBucketEntries),
    [agingBucketEntries]
  );

  function resetFilters() {
    setFilter(defaultReconciliationHandoffWorkloadFilter);
    setPage(0);
    setExportError(null);
  }

  async function handleExport() {
    setExportError(null);
    if (!filtersValid) {
      setExportError(filterErrors[0] ?? "Filter export handoff workload tidak valid.");
      return;
    }

    setIsExporting(true);
    try {
      const csv = await exportPaymentReconciliationHandoffWorkloadCsv(filters);
      downloadTextFile(
        reconciliationHandoffWorkloadExportFilename(filter),
        csv,
        "text/csv;charset=utf-8"
      );
    } catch (error) {
      setExportError(apiErrorMessage(error, "Gagal export workload handoff rekonsiliasi."));
    } finally {
      setIsExporting(false);
    }
  }

  async function handleOwnerSlaExport() {
    setExportError(null);
    if (!filtersValid) {
      setExportError(filterErrors[0] ?? "Filter export escalation handoff tidak valid.");
      return;
    }

    setIsExportingOwnerSla(true);
    try {
      const csv = await exportPaymentReconciliationHandoffOwnerSlaCsv(ownerSlaFilters);
      downloadTextFile(
        reconciliationHandoffOwnerSlaExportFilename(filter),
        csv,
        "text/csv;charset=utf-8"
      );
    } catch (error) {
      setExportError(apiErrorMessage(error, "Gagal export escalation owner handoff."));
    } finally {
      setIsExportingOwnerSla(false);
    }
  }

  async function handleAgingBucketExport() {
    setExportError(null);
    if (!filtersValid) {
      setExportError(filterErrors[0] ?? "Filter export stale bucket handoff tidak valid.");
      return;
    }

    setIsExportingAgingBuckets(true);
    try {
      const csv = await exportPaymentReconciliationHandoffAgingBucketsCsv(ownerSlaFilters);
      downloadTextFile(
        reconciliationHandoffAgingBucketExportFilename(filter),
        csv,
        "text/csv;charset=utf-8"
      );
    } catch (error) {
      setExportError(apiErrorMessage(error, "Gagal export stale bucket handoff."));
    } finally {
      setIsExportingAgingBuckets(false);
    }
  }

  async function handleAgingEvidencePacketExport() {
    setExportError(null);
    if (!filtersValid) {
      setExportError(filterErrors[0] ?? "Filter export evidence packet handoff tidak valid.");
      return;
    }

    setIsExportingEvidencePacket(true);
    try {
      const csv = await exportPaymentReconciliationHandoffAgingEvidencePacketCsv(ownerSlaFilters);
      downloadTextFile(
        reconciliationHandoffAgingEvidencePacketExportFilename(filter),
        csv,
        "text/csv;charset=utf-8"
      );
    } catch (error) {
      setExportError(apiErrorMessage(error, "Gagal export evidence packet handoff."));
    } finally {
      setIsExportingEvidencePacket(false);
    }
  }

  function applyOwnerDrilldown(
    entry: { handoffOwner: string | null; unassigned: boolean },
    handoffStatus: PaymentReconciliationHandoffStatus | "ALL" = "ALL"
  ) {
    setFilter((current) => reconciliationHandoffOwnerDrilldownFilter(current, {
      handoffOwner: entry.handoffOwner,
      unassigned: entry.unassigned,
      handoffStatus
    }));
    setPage(0);
    setExportError(null);
  }

  if (!allowed) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2">
          <LockKeyhole className="size-5 text-slate-600" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Handoff Workload Terkunci</h2>
        </div>
        <p className="mt-2 text-sm leading-6 text-slate-700">
          Authority <span className="font-mono font-bold">payment.reconcile</span> diperlukan untuk membaca workload handoff rekonsiliasi.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 p-4">
        <div className="flex items-center gap-2">
          <CalendarClock className="size-5 text-sky-700" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Handoff SLA Workload</h2>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {workloadQuery.isFetching || ownerSlaQuery.isFetching || agingBucketQuery.isFetching ? (
            <span className="inline-flex items-center gap-2 text-sm font-semibold text-slate-600">
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Memperbarui
            </span>
          ) : null}
          <button
            type="button"
            className={secondaryButtonClass}
            disabled={!filtersValid || isExportingEvidencePacket}
            title="Export detail stale handoff per owner dan aging bucket"
            onClick={handleAgingEvidencePacketExport}
          >
            {isExportingEvidencePacket ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <FileSearch className="size-4" aria-hidden="true" />}
            Export Packet
          </button>
          <button
            type="button"
            className={secondaryButtonClass}
            disabled={!filtersValid || isExportingAgingBuckets}
            onClick={handleAgingBucketExport}
          >
            {isExportingAgingBuckets ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <AlertTriangle className="size-4" aria-hidden="true" />}
            Export Stale
          </button>
          <button
            type="button"
            className={secondaryButtonClass}
            disabled={!filtersValid || isExportingOwnerSla}
            onClick={handleOwnerSlaExport}
          >
            {isExportingOwnerSla ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <UsersRound className="size-4" aria-hidden="true" />}
            Export Escalation
          </button>
          <button
            type="button"
            className={secondaryButtonClass}
            disabled={!filtersValid || isExporting}
            onClick={handleExport}
          >
            {isExporting ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Download className="size-4" aria-hidden="true" />}
            Export Workload
          </button>
        </div>
      </div>

      <div className="grid gap-3 p-4">
        <div className="grid gap-2 md:grid-cols-3 xl:grid-cols-5">
          <MetricPill label="Notes" value={summary.totalNotes} tone="info" />
          <MetricPill label="Open" value={summary.openNotes} tone={summary.openNotes > 0 ? "warning" : "success"} />
          <MetricPill label="Progress" value={summary.inProgressNotes} tone="info" />
          <MetricPill label="Cleared" value={summary.clearedNotes} tone="success" />
          <MetricPill label="Overdue" value={summary.overdueNotes} tone={summary.overdueNotes > 0 ? "danger" : "success"} />
        </div>
        <div className="grid gap-2 md:grid-cols-3 xl:grid-cols-6">
          <MetricPill label="Active" value={agingBucketSummary.activeNotes} tone="info" />
          <MetricPill label="Due Today" value={agingBucketSummary.dueTodayNotes} tone={agingBucketSummary.dueTodayNotes > 0 ? "warning" : "success"} />
          <MetricPill label="1-3 Hari" value={agingBucketSummary.overdue1To3Notes} tone={agingBucketSummary.overdue1To3Notes > 0 ? "warning" : "success"} />
          <MetricPill label="4-7 Hari" value={agingBucketSummary.overdue4To7Notes} tone={agingBucketSummary.overdue4To7Notes > 0 ? "danger" : "success"} />
          <MetricPill label=">7 Hari" value={agingBucketSummary.overdueOver7Notes} tone={agingBucketSummary.overdueOver7Notes > 0 ? "danger" : "success"} />
          <MetricPill label="Stale" value={agingBucketSummary.staleNotes} tone={agingBucketSummary.staleNotes > 0 ? "danger" : "success"} />
        </div>

        <div className="grid gap-3 lg:grid-cols-[170px_1fr_150px_1fr_1fr_auto]">
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Status</span>
            <select
              className={inputClass}
              value={filter.handoffStatus}
              onChange={(event) => {
                setFilter((current) => ({
                  ...current,
                  handoffStatus: event.target.value as PaymentReconciliationHandoffStatus | "ALL"
                }));
                setPage(0);
              }}
            >
              <option value="ALL">Semua</option>
              {paymentReconciliationHandoffStatusValues.map((value) => (
                <option key={value} value={value}>
                  {reconciliationHandoffStatusLabels[value]}
                </option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Owner</span>
            <input
              className={inputClass}
              value={filter.handoffOwner}
              maxLength={128}
              disabled={filter.unassignedOnly}
              placeholder="finance.ops"
              onChange={(event) => {
                setFilter((current) => ({ ...current, handoffOwner: event.target.value, unassignedOnly: false }));
                setPage(0);
              }}
            />
          </label>
          <label className="flex items-end gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
            <input
              type="checkbox"
              className="size-4 rounded border-slate-300"
              checked={filter.unassignedOnly}
              onChange={(event) => {
                setFilter((current) => ({
                  ...current,
                  handoffOwner: event.target.checked ? "" : current.handoffOwner,
                  unassignedOnly: event.target.checked
                }));
                setPage(0);
              }}
            />
            <span className="text-sm font-bold text-slate-700">Tanpa owner</span>
          </label>
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Due From</span>
            <input
              type="date"
              className={inputClass}
              value={filter.dueFrom}
              onChange={(event) => {
                setFilter((current) => ({ ...current, dueFrom: event.target.value }));
                setPage(0);
              }}
            />
          </label>
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Due To</span>
            <input
              type="date"
              className={inputClass}
              value={filter.dueTo}
              onChange={(event) => {
                setFilter((current) => ({ ...current, dueTo: event.target.value }));
                setPage(0);
              }}
            />
          </label>
          <div className="flex items-end">
            <button type="button" className={secondaryButtonClass} onClick={resetFilters}>
              <RotateCcw className="size-4" aria-hidden="true" />
              Reset
            </button>
          </div>
        </div>

        {filterErrors.length > 0 ? <InlineMessage type="error" message={filterErrors[0]} /> : null}
        {exportError ? <InlineMessage type="error" message={exportError} /> : null}
        {ownerSlaQuery.data?.truncated ? (
          <InlineMessage
            type="error"
            message="Owner escalation dibatasi 10.000 baris pertama. Persempit filter due date atau owner sebelum dipakai untuk eskalasi resmi."
          />
        ) : null}
        {agingBucketQuery.data?.truncated ? (
          <InlineMessage
            type="error"
            message="Aging bucket dibatasi 10.000 baris pertama. Persempit filter sebelum export stale queue resmi."
          />
        ) : null}
        {workloadQuery.isLoading ? <LoadingSkeleton /> : null}

        {ownerSlaQuery.isLoading ? <LoadingSkeleton /> : null}
        {agingBucketQuery.isLoading ? <LoadingSkeleton /> : null}

        {ownerSlaQuery.isError ? (
          <div className="grid gap-3">
            <InlineMessage
              type="error"
              message={apiErrorMessage(ownerSlaQuery.error, "Owner escalation handoff tidak tersedia.")}
            />
            <button type="button" className={secondaryButtonClass} onClick={() => void ownerSlaQuery.refetch()}>
              <RotateCcw className="size-4" aria-hidden="true" />
              Muat Ulang Owner
            </button>
          </div>
        ) : null}

        {!ownerSlaQuery.isLoading && !ownerSlaQuery.isError && ownerSlaEntries.length > 0 ? (
          <ReconciliationHandoffOwnerSlaTable entries={ownerSlaEntries} onDrillDown={applyOwnerDrilldown} />
        ) : null}

        {agingBucketQuery.isError ? (
          <div className="grid gap-3">
            <InlineMessage
              type="error"
              message={apiErrorMessage(agingBucketQuery.error, "Aging bucket handoff tidak tersedia.")}
            />
            <button type="button" className={secondaryButtonClass} onClick={() => void agingBucketQuery.refetch()}>
              <RotateCcw className="size-4" aria-hidden="true" />
              Muat Ulang Aging
            </button>
          </div>
        ) : null}

        {!agingBucketQuery.isLoading && !agingBucketQuery.isError && agingBucketEntries.length > 0 ? (
          <ReconciliationHandoffAgingBucketTable entries={agingBucketEntries} onDrillDown={applyOwnerDrilldown} />
        ) : null}

        {workloadQuery.isError ? (
          <div className="grid gap-3">
            <InlineMessage
              type="error"
              message={apiErrorMessage(workloadQuery.error, "Workload handoff rekonsiliasi tidak tersedia.")}
            />
            <button type="button" className={secondaryButtonClass} onClick={() => void workloadQuery.refetch()}>
              <RotateCcw className="size-4" aria-hidden="true" />
              Muat Ulang
            </button>
          </div>
        ) : null}

        {!workloadQuery.isLoading && !workloadQuery.isError && entries.length === 0 ? (
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <p className="text-sm font-bold text-slate-950">Tidak ada workload handoff</p>
            <p className="mt-1 text-sm leading-6 text-slate-600">
              Catatan handoff yang dibuat dari review register akan muncul sesuai filter owner, status, dan due date.
            </p>
          </div>
        ) : null}

        {!workloadQuery.isLoading && !workloadQuery.isError && entries.length > 0 ? (
          <ReconciliationHandoffWorkloadTable entries={entries} />
        ) : null}

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-3">
          <span className="text-sm font-semibold text-slate-600">
            {workloadQuery.data?.totalItems ?? 0} notes
          </span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className={secondaryButtonClass}
              disabled={page <= 0 || workloadQuery.isFetching}
              onClick={() => setPage((current) => Math.max(current - 1, 0))}
            >
              Previous
            </button>
            <span className="text-sm font-bold text-slate-700">
              {(workloadQuery.data?.page ?? page) + 1} / {Math.max(workloadQuery.data?.totalPages ?? 1, 1)}
            </span>
            <button
              type="button"
              className={secondaryButtonClass}
              disabled={(workloadQuery.data?.page ?? page) + 1 >= Math.max(workloadQuery.data?.totalPages ?? 1, 1) || workloadQuery.isFetching}
              onClick={() => setPage((current) => current + 1)}
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function ReconciliationHandoffOwnerSlaTable({
  entries,
  onDrillDown
}: Readonly<{
  entries: PaymentReconciliationHandoffOwnerSlaEntry[];
  onDrillDown: (
    entry: PaymentReconciliationHandoffOwnerSlaEntry,
    handoffStatus?: PaymentReconciliationHandoffStatus | "ALL"
  ) => void;
}>) {
  const smallButtonClass =
    "inline-flex h-8 items-center justify-center rounded-md border border-slate-300 bg-white px-2 text-xs font-bold text-slate-800 transition hover:bg-slate-50";

  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50">
          <tr>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Owner Queue</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Priority</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">Status Breakdown</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">SLA</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Action</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 bg-white">
          {entries.map((entry) => (
            <tr key={`${entry.ownerLabel}-${entry.unassigned ? "unassigned" : "assigned"}`} className="hover:bg-slate-50">
              <td className="min-w-56 px-4 py-3">
                <p className="text-sm font-bold text-slate-950">{entry.ownerLabel}</p>
                <p className="mt-1 text-xs font-semibold text-slate-600">{entry.totalNotes} notes</p>
              </td>
              <td className="min-w-36 px-4 py-3">
                <StatusBadge
                  label={reconciliationHandoffPriorityLabels[entry.escalationPriority]}
                  tone={reconciliationHandoffPriorityTones[entry.escalationPriority]}
                />
              </td>
              <td className="min-w-72 px-4 py-3">
                <div className="flex flex-wrap justify-end gap-2">
                  <button type="button" className={smallButtonClass} onClick={() => onDrillDown(entry, "OPEN")}>
                    Open {entry.openNotes}
                  </button>
                  <button type="button" className={smallButtonClass} onClick={() => onDrillDown(entry, "IN_PROGRESS")}>
                    Progress {entry.inProgressNotes}
                  </button>
                  <button type="button" className={smallButtonClass} onClick={() => onDrillDown(entry, "CLEARED")}>
                    Cleared {entry.clearedNotes}
                  </button>
                </div>
              </td>
              <td className="min-w-48 px-4 py-3">
                <p className="text-sm font-bold text-slate-950">
                  {entry.overdueNotes} overdue / max {entry.maxOverdueDays} hari
                </p>
                <p className="mt-1 text-xs font-semibold text-slate-600">
                  Due terdekat: {entry.nearestDueDate ?? "-"}
                </p>
                <p className="mt-1 text-xs text-slate-600">Update: {formatDateTime(entry.latestUpdatedAt)}</p>
              </td>
              <td className="min-w-36 px-4 py-3">
                <button type="button" className={secondaryButtonClass} onClick={() => onDrillDown(entry)}>
                  <ListFilter className="size-4" aria-hidden="true" />
                  Buka Queue
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ReconciliationHandoffAgingBucketTable({
  entries,
  onDrillDown
}: Readonly<{
  entries: PaymentReconciliationHandoffAgingBucketEntry[];
  onDrillDown: (
    entry: { handoffOwner: string | null; unassigned: boolean },
    handoffStatus?: PaymentReconciliationHandoffStatus | "ALL"
  ) => void;
}>) {
  const smallButtonClass =
    "inline-flex h-8 items-center justify-center rounded-md border border-slate-300 bg-white px-2 text-xs font-bold text-slate-800 transition hover:bg-slate-50";

  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50">
          <tr>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Aging Owner</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">Due Today</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">1-3 Hari</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">4-7 Hari</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">&gt;7 Hari</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">Other Active</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Action</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 bg-white">
          {entries.map((entry) => (
            <tr key={`${entry.ownerLabel}-${entry.unassigned ? "unassigned" : "aging"}`} className="hover:bg-slate-50">
              <td className="min-w-56 px-4 py-3">
                <p className="text-sm font-bold text-slate-950">{entry.ownerLabel}</p>
                <p className="mt-1 text-xs font-semibold text-slate-600">
                  {entry.activeNotes} aktif, {entry.staleNotes} stale
                </p>
                <p className="mt-1 text-xs text-slate-600">Due terdekat: {entry.nearestDueDate ?? "-"}</p>
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-right">
                <button type="button" className={smallButtonClass} onClick={() => onDrillDown(entry)}>
                  {entry.dueTodayNotes}
                </button>
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-right font-bold text-slate-950">{entry.overdue1To3Notes}</td>
              <td className="whitespace-nowrap px-4 py-3 text-right font-bold text-slate-950">{entry.overdue4To7Notes}</td>
              <td className="whitespace-nowrap px-4 py-3 text-right font-bold text-red-700">{entry.overdueOver7Notes}</td>
              <td className="min-w-44 px-4 py-3 text-right text-xs font-semibold text-slate-700">
                Future {entry.futureDueNotes} / No due {entry.noDueDateNotes}
              </td>
              <td className="min-w-36 px-4 py-3">
                <button type="button" className={secondaryButtonClass} onClick={() => onDrillDown(entry)}>
                  <ListFilter className="size-4" aria-hidden="true" />
                  Buka Owner
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ReconciliationHandoffWorkloadTable({
  entries
}: Readonly<{ entries: PaymentReconciliationHandoffWorkloadEntry[] }>) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50">
          <tr>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Session</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Owner / Due</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Status</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Note</th>
            <th className="px-4 py-3 text-right font-bold text-slate-700">Revision</th>
            <th className="px-4 py-3 text-left font-bold text-slate-700">Updated</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 bg-white">
          {entries.map((entry) => (
            <tr key={entry.noteId} className="hover:bg-slate-50">
              <td className="min-w-56 px-4 py-3">
                <p className="font-mono text-xs font-bold text-slate-950">{entry.sessionNumber}</p>
                <p className="mt-1 text-xs font-semibold text-slate-600">{entry.bankAccountReference ?? "-"}</p>
                <p className="mt-1 text-xs text-slate-600">Completed: {formatDateTime(entry.completedAt)}</p>
              </td>
              <td className="min-w-44 px-4 py-3">
                <p className="text-sm font-bold text-slate-950">{entry.handoffOwner ?? "No owner"}</p>
                <p className="mt-1 text-xs font-semibold text-slate-600">{entry.handoffDueDate ?? "No due date"}</p>
              </td>
              <td className="min-w-40 px-4 py-3">
                <div className="grid gap-2">
                  <StatusBadge
                    label={reconciliationHandoffStatusLabels[entry.handoffStatus]}
                    tone={reconciliationHandoffStatusTones[entry.handoffStatus]}
                  />
                  <StatusBadge
                    label={entry.overdueDays > 0 ? `${entry.overdueDays} hari overdue` : "On Track"}
                    tone={entry.overdueDays > 0 ? "danger" : "success"}
                  />
                  <StatusBadge
                    label={reconciliationReviewStatusLabels[entry.reviewStatus]}
                    tone={reconciliationReviewStatusTones[entry.reviewStatus]}
                  />
                </div>
              </td>
              <td className="min-w-80 px-4 py-3">
                <p className="max-w-xl break-words text-sm font-semibold leading-6 text-slate-900">{entry.noteText}</p>
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-right font-bold text-slate-950">{entry.revisionCount}</td>
              <td className="min-w-44 px-4 py-3">
                <p className="text-xs font-semibold text-slate-700">{entry.updatedBy}</p>
                <p className="mt-1 text-xs text-slate-600">{formatDateTime(entry.updatedAt)}</p>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PaymentReconciliationPanel({
  allowed,
  canAdjust,
  canSignOff,
  accounts,
  channel,
  status,
  currentActor
}: Readonly<{
  allowed: boolean;
  canAdjust: boolean;
  canSignOff: boolean;
  accounts: Account[];
  channel: string;
  status: StatusFilter<PaymentStatus>;
  currentActor: string | null;
}>) {
  const matchMutation = useMatchPaymentReconciliation();
  const createSessionMutation = useCreatePaymentReconciliationSession();
  const resolveItemMutation = useResolvePaymentReconciliationItem();
  const adjustmentMutation = useCreatePaymentReconciliationAdjustment();
  const completeSessionMutation = useCompletePaymentReconciliationSession();
  const signOffMutation = useSignOffPaymentReconciliationSession();
  const [paidAtFrom, setPaidAtFrom] = useState("");
  const [paidAtTo, setPaidAtTo] = useState("");
  const [statementCsv, setStatementCsv] = useState("");
  const [importProfile, setImportProfile] = useState<BankStatementImportProfile>("STANDARD");
  const [sourceFilename, setSourceFilename] = useState("");
  const [bankAccountReference, setBankAccountReference] = useState("");
  const [localError, setLocalError] = useState<string | null>(null);
  const [importErrors, setImportErrors] = useState<string[]>([]);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [isExporting, setIsExporting] = useState(false);
  const [matchReport, setMatchReport] = useState<PaymentReconciliationMatchReport | null>(null);
  const [lastParsedRows, setLastParsedRows] = useState<BankStatementCsvRow[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
  const [resolutionItemId, setResolutionItemId] = useState("");
  const [resolutionStatus, setResolutionStatus] = useState<PaymentReconciliationResolutionStatus>("RESOLVED");
  const [resolutionReason, setResolutionReason] = useState("");
  const [adjustmentForm, setAdjustmentForm] = useState<PaymentReconciliationAdjustmentDraft>(
    defaultReconciliationAdjustmentForm
  );
  const [completeReason, setCompleteReason] = useState("");
  const [signOffReason, setSignOffReason] = useState("");
  const [isExportingEvidence, setIsExportingEvidence] = useState(false);
  const sessionFilters = useMemo(() => ({ page: 0, size: 5 }), []);
  const sessionsQuery = usePaymentReconciliationSessions(sessionFilters, allowed);
  const selectedSessionQuery = usePaymentReconciliationSession(selectedSessionId, allowed && Boolean(selectedSessionId));
  const exportDraft = { status, paidAtFrom, paidAtTo };
  const exportErrors = paymentReconciliationExportErrors(exportDraft);
  const matchSummary = useMemo(
    () => (matchReport ? summarizeReconciliationMatches(matchReport.matches) : null),
    [matchReport]
  );
  const selectedSession = selectedSessionQuery.data ?? null;
  const selectedSessionSummary = useMemo(
    () => (selectedSession ? summarizeReconciliationSessionItems(selectedSession.items) : null),
    [selectedSession]
  );
  const evidenceEnabled = allowed && Boolean(selectedSessionId) && selectedSession?.status === "COMPLETED";
  const evidenceQuery = usePaymentReconciliationEvidenceReport(selectedSessionId, evidenceEnabled);
  const adjustableItems = useMemo(
    () => (selectedSession ? selectedSession.items.filter(isAdjustmentCandidate) : []),
    [selectedSession]
  );

  async function handleExport() {
    setLocalError(null);
    setImportErrors([]);
    setSuccessMessage(null);

    if (!allowed) {
      setLocalError("User tidak memiliki permission payment.reconcile.");
      return;
    }
    if (exportErrors.length > 0) {
      setLocalError(exportErrors[0]);
      return;
    }

    setIsExporting(true);
    try {
      const csv = await exportPaymentReconciliationCsv({
        status: status === "ALL" ? undefined : status,
        channel: optionalString(channel) ?? undefined,
        paidAtFrom: toOptionalInstant(paidAtFrom),
        paidAtTo: toOptionalInstant(paidAtTo)
      });
      downloadTextFile(`payment-reconciliation-${new Date().toISOString().slice(0, 10)}.csv`, csv, "text/csv;charset=utf-8");
      setSuccessMessage("Export CSV rekonsiliasi berhasil dibuat.");
    } catch (error) {
      setLocalError(apiErrorMessage(error, "Gagal export rekonsiliasi pembayaran."));
    } finally {
      setIsExporting(false);
    }
  }

  async function handleMatch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLocalError(null);
    setImportErrors([]);
    setSuccessMessage(null);
    setMatchReport(null);
    setLastParsedRows([]);
    matchMutation.reset();

    if (!allowed) {
      setLocalError("User tidak memiliki permission payment.reconcile.");
      return;
    }

    const parsed = parseBankStatementImport(statementCsv, importProfile);
    if (parsed.errors.length > 0) {
      setImportErrors(parsed.errors);
      setLocalError(parsed.errors[0]);
      return;
    }

    try {
      const report = await matchMutation.mutateAsync({ rows: parsed.rows });
      setMatchReport(report);
      setLastParsedRows(parsed.rows);
      setSuccessMessage(`Match selesai untuk ${report.summary.totalRows} baris bank statement.`);
    } catch {
      return;
    }
  }

  async function handleCreateSession() {
    setLocalError(null);
    setImportErrors([]);
    setSuccessMessage(null);
    createSessionMutation.reset();

    if (!allowed) {
      setLocalError("User tidak memiliki permission payment.reconcile.");
      return;
    }
    if (lastParsedRows.length === 0) {
      setLocalError("Jalankan match bank statement sebelum menyimpan session.");
      return;
    }

    try {
      const session = await createSessionMutation.mutateAsync({
        sourceFilename: optionalString(sourceFilename),
        bankAccountReference: optionalString(bankAccountReference),
        rows: lastParsedRows
      });
      setSelectedSessionId(session.id);
      setResolutionItemId(session.items.find((item) => item.resolutionStatus === "OPEN")?.id ?? "");
      setSuccessMessage(`Session ${session.sessionNumber} tersimpan untuk review exception.`);
    } catch {
      return;
    }
  }

  async function handleResolveItem(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLocalError(null);
    setImportErrors([]);
    setSuccessMessage(null);
    resolveItemMutation.reset();

    if (!selectedSessionId || !selectedSession) {
      setLocalError("Session rekonsiliasi wajib dipilih.");
      return;
    }
    if (selectedSession.status !== "OPEN") {
      setLocalError("Session rekonsiliasi sudah tidak open.");
      return;
    }

    const errors = reconciliationResolutionErrors({
      itemId: resolutionItemId,
      resolutionStatus,
      reason: resolutionReason
    });
    if (errors.length > 0) {
      setLocalError(errors[0]);
      return;
    }

    const closedStatus = toClosedResolutionStatus(resolutionStatus);
    if (!closedStatus) {
      setLocalError("Status resolution wajib menutup item.");
      return;
    }

    try {
      const session = await resolveItemMutation.mutateAsync({
        sessionId: selectedSessionId,
        itemId: resolutionItemId,
        payload: {
          resolutionStatus: closedStatus,
          reason: normalizeInput(resolutionReason)
        }
      });
      setResolutionItemId(session.items.find((item) => item.resolutionStatus === "OPEN")?.id ?? "");
      setResolutionStatus("RESOLVED");
      setResolutionReason("");
      const nextAdjustmentItem = session.items.find(isAdjustmentCandidate);
      setAdjustmentForm((current) => ({
        ...current,
        itemId: nextAdjustmentItem?.id ?? current.itemId,
        period: nextAdjustmentItem ? periodFromInstant(nextAdjustmentItem.transactedAt) : current.period,
        amount: nextAdjustmentItem ? defaultAdjustmentAmount(nextAdjustmentItem) : current.amount
      }));
      setSuccessMessage(`Item rekonsiliasi pada session ${session.sessionNumber} berhasil ditutup.`);
    } catch {
      return;
    }
  }

  async function handleCreateAdjustment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLocalError(null);
    setImportErrors([]);
    setSuccessMessage(null);
    adjustmentMutation.reset();

    if (!selectedSessionId || !selectedSession) {
      setLocalError("Session rekonsiliasi wajib dipilih.");
      return;
    }
    if (!canAdjust) {
      setLocalError("User wajib memiliki permission payment.reconcile dan journal.post.");
      return;
    }
    if (selectedSession.status !== "OPEN") {
      setLocalError("Session rekonsiliasi sudah tidak open.");
      return;
    }

    const errors = reconciliationAdjustmentErrors({ draft: adjustmentForm, accounts });
    if (errors.length > 0) {
      setLocalError(errors[0]);
      return;
    }

    const amount = parseMoneyInput(adjustmentForm.amount);
    if (amount === null) {
      setLocalError("Nominal adjustment wajib valid.");
      return;
    }

    const payload: CreatePaymentReconciliationAdjustmentPayload = {
      period: normalizeInput(adjustmentForm.period),
      amount,
      debitAccountId: adjustmentForm.debitAccountId,
      creditAccountId: adjustmentForm.creditAccountId,
      reason: normalizeInput(adjustmentForm.reason)
    };

    try {
      const session = await adjustmentMutation.mutateAsync({
        sessionId: selectedSessionId,
        itemId: adjustmentForm.itemId,
        payload
      });
      const nextAdjustmentItem = session.items.find(isAdjustmentCandidate);
      setAdjustmentForm({
        ...defaultReconciliationAdjustmentForm,
        itemId: nextAdjustmentItem?.id ?? "",
        period: nextAdjustmentItem ? periodFromInstant(nextAdjustmentItem.transactedAt) : "",
        amount: nextAdjustmentItem ? defaultAdjustmentAmount(nextAdjustmentItem) : ""
      });
      setSuccessMessage(`Adjustment journal dibuat untuk session ${session.sessionNumber}.`);
    } catch {
      return;
    }
  }

  async function handleCompleteSession(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLocalError(null);
    setImportErrors([]);
    setSuccessMessage(null);
    completeSessionMutation.reset();

    if (!selectedSessionId || !selectedSession || !selectedSessionSummary) {
      setLocalError("Session rekonsiliasi wajib dipilih.");
      return;
    }
    if (selectedSession.status !== "OPEN") {
      setLocalError("Session rekonsiliasi sudah tidak open.");
      return;
    }

    const errors = reconciliationCompletionErrors({
      reason: completeReason,
      openItems: selectedSessionSummary.openItems
    });
    if (errors.length > 0) {
      setLocalError(errors[0]);
      return;
    }

    try {
      const session = await completeSessionMutation.mutateAsync({
        sessionId: selectedSessionId,
        payload: { reason: normalizeInput(completeReason) }
      });
      setCompleteReason("");
      setSuccessMessage(`Session ${session.sessionNumber} selesai dan terkunci untuk audit.`);
    } catch {
      return;
    }
  }

  async function handleExportEvidence() {
    setLocalError(null);
    setImportErrors([]);
    setSuccessMessage(null);

    const errors = reconciliationEvidenceExportErrors({
      sessionId: selectedSessionId,
      sessionStatus: selectedSession?.status ?? null
    });
    if (errors.length > 0) {
      setLocalError(errors[0]);
      return;
    }

    setIsExportingEvidence(true);
    try {
      const csv = await exportPaymentReconciliationEvidenceCsv(selectedSessionId ?? "");
      const safeSessionNumber = evidenceQuery.data?.sessionNumber ?? selectedSession?.sessionNumber ?? "session";
      downloadTextFile(
        `payment-reconciliation-evidence-${safeSessionNumber}.csv`,
        csv,
        "text/csv;charset=utf-8"
      );
      setSuccessMessage("Evidence report rekonsiliasi berhasil dibuat.");
    } catch (error) {
      setLocalError(apiErrorMessage(error, "Gagal export evidence report rekonsiliasi."));
    } finally {
      setIsExportingEvidence(false);
    }
  }

  async function handleSignOffSession(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLocalError(null);
    setImportErrors([]);
    setSuccessMessage(null);
    signOffMutation.reset();

    if (!canSignOff) {
      setLocalError("User tidak memiliki permission payment.reconciliation.signoff.");
      return;
    }
    if (!selectedSessionId || !selectedSession) {
      setLocalError("Session rekonsiliasi wajib dipilih.");
      return;
    }
    if (evidenceQuery.isLoading || evidenceQuery.isFetching) {
      setLocalError("Evidence report masih dimuat.");
      return;
    }
    if (evidenceQuery.error || !evidenceQuery.data) {
      setLocalError("Evidence report wajib berhasil dimuat sebelum sign-off.");
      return;
    }

    const errors = reconciliationSignOffErrors({
      reason: signOffReason,
      sessionStatus: selectedSession.status,
      signedOffAt: selectedSession.signedOffAt,
      actor: currentActor,
      createdBy: selectedSession.createdBy,
      completedBy: evidenceQuery.data.completedBy
    });
    if (errors.length > 0) {
      setLocalError(errors[0]);
      return;
    }

    try {
      const session = await signOffMutation.mutateAsync({
        sessionId: selectedSessionId,
        payload: { reason: normalizeInput(signOffReason) }
      });
      setSignOffReason("");
      setSuccessMessage(`Evidence session ${session.sessionNumber} sudah mendapat sign-off.`);
    } catch {
      return;
    }
  }

  const busy =
    isExporting ||
    isExportingEvidence ||
    matchMutation.isPending ||
    createSessionMutation.isPending ||
    resolveItemMutation.isPending ||
    adjustmentMutation.isPending ||
    completeSessionMutation.isPending ||
    signOffMutation.isPending;
  const disabled = !allowed || busy;
  const sessionCommandDisabled = disabled || selectedSession?.status !== "OPEN";
  const adjustmentCommandDisabled = !canAdjust || busy || selectedSession?.status !== "OPEN";
  const errorMessage =
    localError ??
    (matchMutation.isError ? apiErrorMessage(matchMutation.error, "Gagal match bank statement.") : null) ??
    (createSessionMutation.isError ? apiErrorMessage(createSessionMutation.error, "Gagal menyimpan session rekonsiliasi.") : null) ??
    (resolveItemMutation.isError ? apiErrorMessage(resolveItemMutation.error, "Gagal menutup item rekonsiliasi.") : null) ??
    (adjustmentMutation.isError ? apiErrorMessage(adjustmentMutation.error, "Gagal membuat jurnal adjustment rekonsiliasi.") : null) ??
    (completeSessionMutation.isError ? apiErrorMessage(completeSessionMutation.error, "Gagal menyelesaikan session rekonsiliasi.") : null) ??
    (signOffMutation.isError ? apiErrorMessage(signOffMutation.error, "Gagal sign-off evidence rekonsiliasi.") : null);
  const recentSessions = sessionsQuery.data?.items ?? [];
  const selectedImportProfile = bankStatementImportProfileOptions.find((option) => option.value === importProfile);

  function handleDownloadImportTemplate() {
    setLocalError(null);
    setImportErrors([]);
    const csv = bankStatementImportTemplate(importProfile);
    downloadTextFile(
      `payment-reconciliation-template-${importProfile.toLowerCase()}.csv`,
      csv,
      "text/csv;charset=utf-8"
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <FileSpreadsheet className="size-5 text-sky-700" aria-hidden="true" />
          <h3 className="text-sm font-bold text-slate-950">Payment Reconciliation</h3>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge label={allowed ? "payment.reconcile" : "Locked"} tone={allowed ? "success" : "neutral"} />
          <StatusBadge label={canAdjust ? "journal.post" : "No journal.post"} tone={canAdjust ? "success" : "neutral"} />
        </div>
      </div>
      {!allowed ? (
        <p className="mt-3 text-sm leading-6 text-slate-700">
          Authority <span className="font-mono font-bold">payment.reconcile</span> diperlukan untuk export dan matching bank.
        </p>
      ) : null}

      <div className="mt-4 grid gap-3">
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-xs font-bold uppercase text-slate-600">Export Register Slice</p>
              <p className="mt-1 text-sm font-semibold text-slate-700">
                Filter aktif: {status === "ALL" ? "SETTLED/REVERSED" : paymentStatusLabels[status]} - {optionalString(channel) ?? "Semua channel"}
              </p>
            </div>
            <button type="button" className={secondaryButtonClass} disabled={disabled} onClick={handleExport}>
              {isExporting ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Download className="size-4" aria-hidden="true" />}
              Export CSV
            </button>
          </div>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <label className="block">
              <span className="text-xs font-bold uppercase text-slate-600">Paid From</span>
              <input
                type="datetime-local"
                className={inputClass}
                value={paidAtFrom}
                disabled={disabled}
                onChange={(event) => setPaidAtFrom(event.target.value)}
              />
            </label>
            <label className="block">
              <span className="text-xs font-bold uppercase text-slate-600">Paid To</span>
              <input
                type="datetime-local"
                className={inputClass}
                value={paidAtTo}
                disabled={disabled}
                onChange={(event) => setPaidAtTo(event.target.value)}
              />
            </label>
          </div>
        </div>

        <form onSubmit={handleMatch} className="grid gap-3">
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
            <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto]">
              <label className="block">
                <span className="text-xs font-bold uppercase text-slate-600">Import Profile</span>
                <select
                  className={inputClass}
                  value={importProfile}
                  disabled={disabled}
                  onChange={(event) => {
                    setImportProfile(event.target.value as BankStatementImportProfile);
                    setLastParsedRows([]);
                    setImportErrors([]);
                  }}
                >
                  {bankStatementImportProfileOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <div className="flex items-end">
                <button type="button" className={secondaryButtonClass} disabled={disabled} onClick={handleDownloadImportTemplate}>
                  <Download className="size-4" aria-hidden="true" />
                  Template
                </button>
              </div>
            </div>
            <p className="mt-2 text-sm font-semibold leading-6 text-slate-700">
              {selectedImportProfile?.description ?? "Template import bank statement."}
            </p>
          </div>
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Bank Statement CSV</span>
            <textarea
              className={textareaClass}
              value={statementCsv}
              disabled={disabled}
              onChange={(event) => {
                setStatementCsv(event.target.value);
                setLastParsedRows([]);
                setImportErrors([]);
              }}
              placeholder="reference;amount;transacted_at;channel"
            />
          </label>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block">
              <span className="text-xs font-bold uppercase text-slate-600">Source Filename</span>
              <input
                className={inputClass}
                value={sourceFilename}
                maxLength={255}
                disabled={disabled}
                onChange={(event) => setSourceFilename(event.target.value)}
                placeholder="bank-statement-2026-07.csv"
              />
            </label>
            <label className="block">
              <span className="text-xs font-bold uppercase text-slate-600">Bank Account Ref</span>
              <input
                className={inputClass}
                value={bankAccountReference}
                maxLength={128}
                disabled={disabled}
                onChange={(event) => setBankAccountReference(event.target.value)}
                placeholder="BCA-OPERASIONAL"
              />
            </label>
          </div>
          {errorMessage ? <InlineMessage type="error" message={errorMessage} /> : null}
          {importErrors.length > 1 ? (
            <div className="rounded-lg border border-red-200 bg-red-50 p-3">
              <p className="text-sm font-bold text-red-900">Import validation</p>
              <ul className="mt-2 list-disc space-y-1 pl-5 text-sm font-semibold text-red-800">
                {importErrors.slice(0, 8).map((error) => (
                  <li key={error}>{error}</li>
                ))}
              </ul>
              {importErrors.length > 8 ? (
                <p className="mt-2 text-xs font-semibold text-red-800">{importErrors.length - 8} error lain tidak ditampilkan.</p>
              ) : null}
            </div>
          ) : null}
          {successMessage ? <InlineMessage type="success" message={successMessage} /> : null}
          <button type="submit" className={primaryButtonClass} disabled={disabled}>
            {matchMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <UploadCloud className="size-4" aria-hidden="true" />}
            Match Bank Rows
          </button>
        </form>

        {matchReport && matchSummary ? (
          <div className="rounded-lg border border-slate-200 bg-white">
            <div className="grid gap-2 border-b border-slate-200 p-3 text-sm sm:grid-cols-3">
              <MetricPill label="Exact" value={matchSummary.exactMatches} tone="success" />
              <MetricPill label="Variance" value={matchSummary.amountVariances} tone="warning" />
              <MetricPill label="Unmatched" value={matchSummary.unmatchedRows} tone="danger" />
              <MetricPill label="Probable" value={matchSummary.probableMatches} tone="info" />
              <MetricPill label="Reversed" value={matchSummary.reversedPayments} tone="neutral" />
              <MetricPill label="Multiple" value={matchSummary.multipleCandidates} tone="warning" />
            </div>
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 p-3">
              <p className="text-sm font-semibold text-slate-700">
                {lastParsedRows.length} baris siap disimpan sebagai session audit.
              </p>
              <button
                type="button"
                className={secondaryButtonClass}
                disabled={disabled || lastParsedRows.length === 0}
                onClick={handleCreateSession}
              >
                {createSessionMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <FileSearch className="size-4" aria-hidden="true" />}
                Save Session
              </button>
            </div>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-sm">
                <thead className="bg-slate-50">
                  <tr>
                    <th className="px-4 py-3 text-left font-bold text-slate-700">Row</th>
                    <th className="px-4 py-3 text-left font-bold text-slate-700">Reference</th>
                    <th className="px-4 py-3 text-left font-bold text-slate-700">Status</th>
                    <th className="px-4 py-3 text-right font-bold text-slate-700">Bank Amount</th>
                    <th className="px-4 py-3 text-right font-bold text-slate-700">Variance</th>
                    <th className="px-4 py-3 text-left font-bold text-slate-700">Payment</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {matchReport.matches.map((match) => (
                    <tr key={`${match.rowNumber}-${match.statementReference}`} className="hover:bg-slate-50">
                      <td className="whitespace-nowrap px-4 py-3 font-bold text-slate-950">{match.rowNumber}</td>
                      <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-slate-700">{match.statementReference}</td>
                      <td className="whitespace-nowrap px-4 py-3">
                        <StatusBadge label={reconciliationStatusLabels[match.status]} tone={reconciliationStatusTones[match.status]} />
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-right font-bold text-slate-950">
                        <MoneyText value={match.statementAmount} />
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-right font-bold text-slate-950">
                        {match.amountVariance === null ? "-" : <MoneyText value={match.amountVariance} />}
                      </td>
                      <td className="min-w-48 px-4 py-3 text-slate-700">
                        <p className="font-mono text-xs font-semibold">{match.matchedPaymentNumber ?? "-"}</p>
                        <p className="mt-1 text-xs leading-5 text-slate-600">{match.message}</p>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ) : null}

        <ReconciliationSessionList
          sessions={recentSessions}
          selectedSessionId={selectedSessionId}
          isLoading={sessionsQuery.isLoading}
          isFetching={sessionsQuery.isFetching}
          error={sessionsQuery.error}
          onSelect={(sessionId) => {
            setSelectedSessionId(sessionId);
            setResolutionItemId("");
            setResolutionReason("");
            setAdjustmentForm(defaultReconciliationAdjustmentForm);
            setCompleteReason("");
          }}
        />

        {selectedSessionId ? (
          <div className="rounded-lg border border-slate-200 bg-white">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 p-3">
              <div className="flex items-center gap-2">
                <FileSearch className="size-5 text-slate-700" aria-hidden="true" />
                <h4 className="text-sm font-bold text-slate-950">Session Review</h4>
              </div>
              {selectedSessionQuery.isFetching ? (
                <span className="inline-flex items-center gap-2 text-sm font-semibold text-slate-600">
                  <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                  Memperbarui
                </span>
              ) : null}
            </div>

            {selectedSessionQuery.isLoading ? (
              <div className="p-3">
                <LoadingSkeleton />
              </div>
            ) : null}

            {selectedSessionQuery.isError ? (
              <div className="p-3">
                <InlineMessage
                  type="error"
                  message={apiErrorMessage(selectedSessionQuery.error, "Detail session rekonsiliasi tidak tersedia.")}
                />
              </div>
            ) : null}

            {selectedSession && selectedSessionSummary ? (
              <div className="grid gap-3 p-3">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="font-mono text-sm font-bold text-slate-950">{selectedSession.sessionNumber}</p>
                    <p className="mt-1 text-xs font-semibold text-slate-600">
                      {selectedSession.sourceFilename ?? "Tanpa file"} - {selectedSession.bankAccountReference ?? "Semua rekening"}
                    </p>
                  </div>
                  <StatusBadge
                    label={reconciliationSessionStatusLabels[selectedSession.status]}
                    tone={reconciliationSessionStatusTones[selectedSession.status]}
                  />
                </div>

                <div className="grid gap-2 text-sm sm:grid-cols-3">
                  <MetricPill label="Open" value={selectedSessionSummary.openItems} tone="warning" />
                  <MetricPill label="Resolved" value={selectedSessionSummary.resolvedItems} tone="info" />
                  <MetricPill label="Exception" value={selectedSessionSummary.exceptionItems} tone="danger" />
                  <MetricPill label="Adjusted" value={selectedSessionSummary.adjustedItems} tone="success" />
                </div>

                <div className="overflow-x-auto rounded-lg border border-slate-200">
                  <table className="min-w-full divide-y divide-slate-200 text-sm">
                    <thead className="bg-slate-50">
                      <tr>
                        <th className="px-4 py-3 text-left font-bold text-slate-700">Row</th>
                        <th className="px-4 py-3 text-left font-bold text-slate-700">Reference</th>
                        <th className="px-4 py-3 text-left font-bold text-slate-700">Match</th>
                        <th className="px-4 py-3 text-left font-bold text-slate-700">Resolution</th>
                        <th className="px-4 py-3 text-right font-bold text-slate-700">Bank Amount</th>
                        <th className="px-4 py-3 text-left font-bold text-slate-700">Payment</th>
                        <th className="px-4 py-3 text-left font-bold text-slate-700">Adjustment</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 bg-white">
                      {selectedSession.items.map((item) => (
                        <ReconciliationSessionItemRow key={item.id} item={item} />
                      ))}
                    </tbody>
                  </table>
                </div>

                <form onSubmit={handleResolveItem} className="grid gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3">
                  <div className="grid gap-3 sm:grid-cols-2">
                    <label className="block">
                      <span className="text-xs font-bold uppercase text-slate-600">Item</span>
                      <select
                        className={inputClass}
                        value={resolutionItemId}
                        disabled={sessionCommandDisabled || selectedSessionSummary.openItems === 0}
                        onChange={(event) => setResolutionItemId(event.target.value)}
                      >
                        <option value="">Pilih item</option>
                        {selectedSession.items.filter((item) => item.resolutionStatus === "OPEN").map((item) => (
                          <option key={item.id} value={item.id}>
                            Row {item.rowNumber} - {item.statementReference} - {reconciliationResolutionStatusLabels[item.resolutionStatus]}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="block">
                      <span className="text-xs font-bold uppercase text-slate-600">Resolution Status</span>
                      <select
                        className={inputClass}
                        value={resolutionStatus}
                        disabled={sessionCommandDisabled || selectedSessionSummary.openItems === 0}
                        onChange={(event) => setResolutionStatus(event.target.value as ClosedPaymentReconciliationResolutionStatus)}
                      >
                        {closedResolutionStatuses.map((value) => (
                          <option key={value} value={value}>
                            {reconciliationResolutionStatusLabels[value]}
                          </option>
                        ))}
                      </select>
                    </label>
                  </div>
                  <label className="block">
                    <span className="text-xs font-bold uppercase text-slate-600">Resolution Reason</span>
                    <textarea
                      className={textareaClass}
                      value={resolutionReason}
                      maxLength={500}
                    disabled={sessionCommandDisabled || selectedSessionSummary.openItems === 0}
                    onChange={(event) => setResolutionReason(event.target.value)}
                  />
                </label>
                  <button type="submit" className={secondaryButtonClass} disabled={sessionCommandDisabled || selectedSessionSummary.openItems === 0}>
                    {resolveItemMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <CheckCircle2 className="size-4" aria-hidden="true" />}
                    Resolve Item
                  </button>
                </form>

                <form onSubmit={handleCreateAdjustment} className="grid gap-3 rounded-lg border border-sky-200 bg-sky-50 p-3">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <p className="text-sm font-bold text-sky-950">Adjustment Journal</p>
                      <p className="mt-1 text-xs font-semibold text-sky-800">
                        {adjustableItems.length} accepted exception belum memiliki journal adjustment.
                      </p>
                    </div>
                    <StatusBadge label={canAdjust ? "Ready" : "Locked"} tone={canAdjust ? "success" : "neutral"} />
                  </div>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <label className="block">
                      <span className="text-xs font-bold uppercase text-sky-800">Accepted Exception</span>
                      <select
                        className={inputClass}
                        value={adjustmentForm.itemId}
                        disabled={adjustmentCommandDisabled || adjustableItems.length === 0}
                        onChange={(event) => {
                          const item = selectedSession.items.find((candidate) => candidate.id === event.target.value);
                          setAdjustmentForm((current) => ({
                            ...current,
                            itemId: event.target.value,
                            period: item ? periodFromInstant(item.transactedAt) : current.period,
                            amount: item ? defaultAdjustmentAmount(item) : current.amount
                          }));
                        }}
                      >
                        <option value="">Pilih item accepted</option>
                        {adjustableItems.map((item) => (
                          <option key={item.id} value={item.id}>
                            Row {item.rowNumber} - {item.statementReference} - {reconciliationStatusLabels[item.matchStatus]}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="block">
                      <span className="text-xs font-bold uppercase text-sky-800">Periode</span>
                      <input
                        className={inputClass}
                        value={adjustmentForm.period}
                        maxLength={7}
                        disabled={adjustmentCommandDisabled || adjustableItems.length === 0}
                        onChange={(event) => setAdjustmentForm((current) => ({ ...current, period: event.target.value }))}
                        placeholder="2026-07"
                      />
                    </label>
                  </div>
                  <div className="grid gap-3 sm:grid-cols-3">
                    <label className="block">
                      <span className="text-xs font-bold uppercase text-sky-800">Nominal</span>
                      <input
                        type="number"
                        min="0.01"
                        step="0.01"
                        className={inputClass}
                        value={adjustmentForm.amount}
                        disabled={adjustmentCommandDisabled || adjustableItems.length === 0}
                        onChange={(event) => setAdjustmentForm((current) => ({ ...current, amount: event.target.value }))}
                        placeholder="2500"
                      />
                    </label>
                    <AccountSelect
                      label="Akun Debit"
                      value={adjustmentForm.debitAccountId}
                      accounts={accounts}
                      disabled={adjustmentCommandDisabled || adjustableItems.length === 0}
                      placeholder="Pilih akun debit"
                      onChange={(value) => setAdjustmentForm((current) => ({ ...current, debitAccountId: value }))}
                    />
                    <AccountSelect
                      label="Akun Kredit"
                      value={adjustmentForm.creditAccountId}
                      accounts={accounts}
                      disabled={adjustmentCommandDisabled || adjustableItems.length === 0}
                      placeholder="Pilih akun kredit"
                      onChange={(value) => setAdjustmentForm((current) => ({ ...current, creditAccountId: value }))}
                    />
                  </div>
                  <label className="block">
                    <span className="text-xs font-bold uppercase text-sky-800">Adjustment Reason</span>
                    <textarea
                      className={textareaClass}
                      value={adjustmentForm.reason}
                      maxLength={500}
                      disabled={adjustmentCommandDisabled || adjustableItems.length === 0}
                      onChange={(event) => setAdjustmentForm((current) => ({ ...current, reason: event.target.value }))}
                    />
                  </label>
                  <button
                    type="submit"
                    className={primaryButtonClass}
                    disabled={adjustmentCommandDisabled || adjustableItems.length === 0}
                  >
                    {adjustmentMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <ReceiptText className="size-4" aria-hidden="true" />}
                    Create Adjustment Journal
                  </button>
                </form>

                <form onSubmit={handleCompleteSession} className="grid gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3">
                  <label className="block">
                    <span className="text-xs font-bold uppercase text-emerald-800">Completion Reason</span>
                    <textarea
                      className={textareaClass}
                      value={completeReason}
                      maxLength={500}
                      disabled={sessionCommandDisabled || selectedSessionSummary.openItems > 0}
                      onChange={(event) => setCompleteReason(event.target.value)}
                    />
                  </label>
                  <button
                    type="submit"
                    className={primaryButtonClass}
                    disabled={sessionCommandDisabled || selectedSessionSummary.openItems > 0}
                  >
                    {completeSessionMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <CheckCircle2 className="size-4" aria-hidden="true" />}
                    Complete Session
                  </button>
                  {selectedSessionSummary.openItems > 0 ? (
                    <p className="text-xs font-semibold text-emerald-900">
                      {selectedSessionSummary.openItems} item masih open dan harus ditutup sebelum session selesai.
                    </p>
                  ) : null}
                </form>

                {selectedSession.status === "COMPLETED" ? (
                  <>
                    <ReconciliationEvidencePanel
                      report={evidenceQuery.data ?? null}
                      isLoading={evidenceQuery.isLoading}
                      isFetching={evidenceQuery.isFetching}
                      error={evidenceQuery.error}
                      isExporting={isExportingEvidence}
                      onRetry={() => void evidenceQuery.refetch()}
                      onExport={handleExportEvidence}
                    />
                    <ReconciliationSignOffPanel
                      session={selectedSession}
                      report={evidenceQuery.data ?? null}
                      canSignOff={canSignOff}
                      currentActor={currentActor}
                      reason={signOffReason}
                      isSubmitting={signOffMutation.isPending}
                      isEvidenceLoading={evidenceQuery.isLoading || evidenceQuery.isFetching}
                      onReasonChange={setSignOffReason}
                      onSubmit={handleSignOffSession}
                    />
                  </>
                ) : null}
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  );
}

function ReconciliationSessionList({
  sessions,
  selectedSessionId,
  isLoading,
  isFetching,
  error,
  onSelect
}: Readonly<{
  sessions: PaymentReconciliationSessionSummary[];
  selectedSessionId: string | null;
  isLoading: boolean;
  isFetching: boolean;
  error: unknown;
  onSelect: (sessionId: string) => void;
}>) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-3">
        <span className="inline-flex items-center gap-2 text-sm font-semibold text-slate-600">
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Memuat session rekonsiliasi
        </span>
      </div>
    );
  }

  if (error) {
    return <InlineMessage type="error" message={apiErrorMessage(error, "Session rekonsiliasi tidak tersedia.")} />;
  }

  if (sessions.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-3">
        <p className="text-sm font-bold text-slate-950">Belum ada session rekonsiliasi</p>
        <p className="mt-1 text-sm leading-6 text-slate-600">Session baru muncul setelah hasil match disimpan.</p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 p-3">
        <h4 className="text-sm font-bold text-slate-950">Recent Sessions</h4>
        {isFetching ? (
          <span className="inline-flex items-center gap-2 text-xs font-semibold text-slate-600">
            <Loader2 className="size-3 animate-spin" aria-hidden="true" />
            Refresh
          </span>
        ) : null}
      </div>
      <div className="divide-y divide-slate-100">
        {sessions.map((session) => (
          <button
            key={session.id}
            type="button"
            className={cn(
              "flex w-full items-center justify-between gap-3 px-3 py-3 text-left transition hover:bg-slate-50",
              selectedSessionId === session.id ? "bg-sky-50" : ""
            )}
            onClick={() => onSelect(session.id)}
          >
            <span>
              <span className="block font-mono text-xs font-bold text-slate-950">{session.sessionNumber}</span>
              <span className="mt-1 block text-xs font-semibold text-slate-600">
                {session.totalRows} row - {formatDateTime(session.startedAt)}
              </span>
            </span>
            <span className="flex flex-col items-end gap-1">
              <StatusBadge label={reconciliationSessionStatusLabels[session.status]} tone={reconciliationSessionStatusTones[session.status]} />
              {session.status === "COMPLETED" ? (
                <StatusBadge label={session.signedOffAt ? "Signed" : "Pending Sign-off"} tone={session.signedOffAt ? "success" : "warning"} />
              ) : null}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}

function ReconciliationSessionItemRow({ item }: Readonly<{ item: PaymentReconciliationSessionItem }>) {
  return (
    <tr className="hover:bg-slate-50">
      <td className="whitespace-nowrap px-4 py-3 font-bold text-slate-950">{item.rowNumber}</td>
      <td className="whitespace-nowrap px-4 py-3">
        <p className="font-mono text-xs font-semibold text-slate-800">{item.statementReference}</p>
        <p className="mt-1 text-xs text-slate-600">{formatDateTime(item.transactedAt)}</p>
      </td>
      <td className="whitespace-nowrap px-4 py-3">
        <StatusBadge label={reconciliationStatusLabels[item.matchStatus]} tone={reconciliationStatusTones[item.matchStatus]} />
      </td>
      <td className="whitespace-nowrap px-4 py-3">
        <StatusBadge
          label={reconciliationResolutionStatusLabels[item.resolutionStatus]}
          tone={reconciliationResolutionStatusTones[item.resolutionStatus]}
        />
      </td>
      <td className="whitespace-nowrap px-4 py-3 text-right font-bold text-slate-950">
        <MoneyText value={item.statementAmount} />
      </td>
      <td className="min-w-56 px-4 py-3 text-slate-700">
        <p className="font-mono text-xs font-semibold">{item.matchedPaymentNumber ?? "-"}</p>
        <p className="mt-1 text-xs leading-5 text-slate-600">{item.message}</p>
      </td>
      <td className="min-w-48 px-4 py-3 text-slate-700">
        <p className="font-mono text-xs font-semibold">{shortId(item.adjustmentJournalEntryId)}</p>
        <p className="mt-1 text-xs leading-5 text-slate-600">
          {item.adjustedAt ? `${formatDateTime(item.adjustedAt)} - ${item.adjustedBy ?? "-"}` : item.adjustmentReason ?? "-"}
        </p>
      </td>
    </tr>
  );
}

function ReconciliationSignOffPanel({
  session,
  report,
  canSignOff,
  currentActor,
  reason,
  isSubmitting,
  isEvidenceLoading,
  onReasonChange,
  onSubmit
}: Readonly<{
  session: PaymentReconciliationSessionSummary;
  report: PaymentReconciliationEvidenceReport | null;
  canSignOff: boolean;
  currentActor: string | null;
  reason: string;
  isSubmitting: boolean;
  isEvidenceLoading: boolean;
  onReasonChange: (reason: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}>) {
  if (session.signedOffAt) {
    return (
      <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-sm font-bold text-emerald-950">Month-end Sign-off</p>
            <p className="mt-1 text-xs font-semibold text-emerald-900">{session.sessionNumber}</p>
          </div>
          <StatusBadge label="Approved" tone="success" />
        </div>
        <div className="mt-3 grid gap-2 sm:grid-cols-2">
          <TraceItem label="Signed Off By" value={session.signedOffBy ?? "-"} />
          <TraceItem label="Signed Off At" value={formatDateTime(session.signedOffAt)} />
        </div>
        <div className="mt-3 rounded-lg border border-emerald-200 bg-white p-3">
          <p className="text-xs font-bold uppercase text-emerald-800">Sign-off Reason</p>
          <p className="mt-1 text-sm leading-6 text-emerald-950">{session.signOffReason ?? "-"}</p>
        </div>
      </div>
    );
  }

  const completionActor = report?.completedBy ?? null;
  const blockedBySod =
    sameActorValue(currentActor, session.createdBy) || sameActorValue(currentActor, completionActor);
  const disabled = !canSignOff || !currentActor || isSubmitting || isEvidenceLoading || !report || blockedBySod;

  return (
    <form onSubmit={onSubmit} className="grid gap-3 rounded-lg border border-indigo-200 bg-indigo-50 p-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm font-bold text-indigo-950">Month-end Sign-off</p>
          <p className="mt-1 text-xs font-semibold text-indigo-800">{session.sessionNumber}</p>
        </div>
        <StatusBadge label={canSignOff ? "Ready" : "Locked"} tone={canSignOff ? "success" : "neutral"} />
      </div>
      <div className="grid gap-2 sm:grid-cols-3">
        <TraceItem label="Created By" value={session.createdBy} />
        <TraceItem label="Completed By" value={completionActor ?? "-"} />
        <TraceItem label="Approver" value={currentActor ?? "-"} />
      </div>
      <label className="block">
        <span className="text-xs font-bold uppercase text-indigo-800">Sign-off Reason</span>
        <textarea
          className={textareaClass}
          value={reason}
          maxLength={500}
          disabled={disabled}
          onChange={(event) => onReasonChange(event.target.value)}
        />
      </label>
      <button type="submit" className={primaryButtonClass} disabled={disabled}>
        {isSubmitting ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <ShieldCheck className="size-4" aria-hidden="true" />}
        Sign Off Evidence
      </button>
      {!canSignOff ? (
        <p className="text-xs font-semibold text-indigo-900">
          Authority <span className="font-mono font-bold">payment.reconciliation.signoff</span> diperlukan untuk approval.
        </p>
      ) : null}
      {blockedBySod ? (
        <p className="text-xs font-semibold text-indigo-900">
          Actor sign-off harus berbeda dari pembuat dan penyelesai session.
        </p>
      ) : null}
      {!report ? (
        <p className="text-xs font-semibold text-indigo-900">
          Evidence report harus tersedia sebelum approval.
        </p>
      ) : null}
    </form>
  );
}

function ReconciliationEvidencePanel({
  report,
  isLoading,
  isFetching,
  error,
  isExporting,
  onRetry,
  onExport
}: Readonly<{
  report: PaymentReconciliationEvidenceReport | null;
  isLoading: boolean;
  isFetching: boolean;
  error: unknown;
  isExporting: boolean;
  onRetry: () => void;
  onExport: () => void;
}>) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-3">
        <LoadingSkeleton />
      </div>
    );
  }

  if (error) {
    return (
      <div className="grid gap-3 rounded-lg border border-red-200 bg-white p-3">
        <InlineMessage type="error" message={apiErrorMessage(error, "Evidence report rekonsiliasi tidak tersedia.")} />
        <button type="button" className={secondaryButtonClass} onClick={onRetry}>
          <RotateCcw className="size-4" aria-hidden="true" />
          Muat Ulang
        </button>
      </div>
    );
  }

  if (!report) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-3">
        <p className="text-sm font-bold text-slate-950">Evidence report belum tersedia</p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 p-3">
        <div>
          <p className="text-sm font-bold text-slate-950">Evidence Report</p>
          <p className="mt-1 font-mono text-xs font-semibold text-slate-600">{report.sessionNumber}</p>
        </div>
        <div className="flex items-center gap-2">
          {isFetching ? (
            <span className="inline-flex items-center gap-2 text-xs font-semibold text-slate-600">
              <Loader2 className="size-3 animate-spin" aria-hidden="true" />
              Refresh
            </span>
          ) : null}
          <button type="button" className={secondaryButtonClass} disabled={isExporting} onClick={onExport}>
            {isExporting ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Download className="size-4" aria-hidden="true" />}
            Export Evidence
          </button>
        </div>
      </div>

      <div className="grid gap-3 p-3">
        <div className="grid gap-2 text-sm sm:grid-cols-4">
          <MetricPill label="Rows" value={report.summary.totalRows} tone="info" />
          <MetricPill label="Accepted" value={report.summary.acceptedItems} tone="success" />
          <MetricPill label="Adjusted" value={report.summary.adjustedItems} tone="warning" />
          <MetricPill label="Unmatched" value={report.summary.unmatchedRows} tone="danger" />
        </div>

        <div className="grid gap-2 text-sm sm:grid-cols-3">
          <TraceItem label="Completed By" value={report.completedBy ?? "-"} />
          <TraceItem label="Completed At" value={formatDateTime(report.completedAt)} />
          <TraceItem label="Generated At" value={formatDateTime(report.generatedAt)} />
          <TraceItem label="Signed Off By" value={report.signedOffBy ?? "-"} />
          <TraceItem label="Signed Off At" value={formatDateTime(report.signedOffAt)} />
          <TraceItem label="Sign-off Reason" value={report.signOffReason ?? "-"} />
        </div>

        {report.items.length === 0 ? (
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
            <p className="text-sm font-bold text-slate-950">Tidak ada item evidence</p>
          </div>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50">
                <tr>
                  <th className="px-4 py-3 text-left font-bold text-slate-700">Row</th>
                  <th className="px-4 py-3 text-left font-bold text-slate-700">Reference</th>
                  <th className="px-4 py-3 text-left font-bold text-slate-700">Resolution</th>
                  <th className="px-4 py-3 text-left font-bold text-slate-700">Payment</th>
                  <th className="px-4 py-3 text-left font-bold text-slate-700">Adjustment</th>
                  <th className="px-4 py-3 text-left font-bold text-slate-700">Audit</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {report.items.map((item) => (
                  <tr key={item.itemId} className="hover:bg-slate-50">
                    <td className="whitespace-nowrap px-4 py-3 font-bold text-slate-950">{item.rowNumber}</td>
                    <td className="min-w-48 px-4 py-3">
                      <p className="font-mono text-xs font-semibold text-slate-800">{item.statementReference}</p>
                      <p className="mt-1 text-xs text-slate-600">
                        <MoneyText value={item.statementAmount} /> - {formatDateTime(item.transactedAt)}
                      </p>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <StatusBadge
                        label={reconciliationResolutionStatusLabels[item.resolutionStatus]}
                        tone={reconciliationResolutionStatusTones[item.resolutionStatus]}
                      />
                    </td>
                    <td className="min-w-44 px-4 py-3 text-slate-700">
                      <p className="font-mono text-xs font-semibold">{item.matchedPaymentNumber ?? "-"}</p>
                      <p className="mt-1 text-xs text-slate-600">{shortId(item.settlementJournalEntryId ?? item.reversalJournalEntryId)}</p>
                    </td>
                    <td className="min-w-44 px-4 py-3 text-slate-700">
                      <p className="font-mono text-xs font-semibold">{shortId(item.adjustmentJournalEntryId)}</p>
                      <p className="mt-1 text-xs text-slate-600">{item.adjustmentReason ?? "-"}</p>
                    </td>
                    <td className="min-w-52 px-4 py-3 text-slate-700">
                      <p className="text-xs font-semibold">Resolved: {item.resolvedBy ?? "-"}</p>
                      <p className="mt-1 text-xs text-slate-600">{formatDateTime(item.resolvedAt)}</p>
                      {item.adjustedBy ? (
                        <p className="mt-1 text-xs text-slate-600">Adjusted: {item.adjustedBy}</p>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

function MetricPill({
  label,
  value,
  tone
}: Readonly<{ label: string; value: number; tone: "success" | "warning" | "danger" | "neutral" | "info" }>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-2">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-bold uppercase text-slate-600">{label}</span>
        <StatusBadge label={String(value)} tone={tone} />
      </div>
    </div>
  );
}

function WebhookEventTable({
  events,
  canRead,
  isFetching,
  page,
  totalPages,
  totalItems,
  onPrevious,
  onNext
}: Readonly<{
  events: PaymentWebhookEvent[];
  canRead: boolean;
  isFetching: boolean;
  page: number;
  totalPages: number;
  totalItems: number;
  onPrevious: () => void;
  onNext: () => void;
}>) {
  if (!canRead) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2">
          <LockKeyhole className="size-5 text-slate-600" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Webhook Event Terkunci</h2>
        </div>
        <p className="mt-2 text-sm leading-6 text-slate-700">
          Authority <span className="font-mono font-bold">payment.webhook.read</span> diperlukan untuk membaca event provider.
        </p>
      </div>
    );
  }

  if (events.length === 0) {
    return (
      <EmptyState
        title="Event webhook belum tersedia"
        description="Event provider akan muncul setelah callback HMAC tervalidasi oleh backend."
      />
    );
  }

  const currentPage = page + 1;
  const pageCount = Math.max(totalPages, 1);

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
        <div className="flex items-center gap-2">
          <ReceiptText className="size-5 text-slate-700" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Payment Webhook Events</h2>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-sm font-semibold text-slate-600">{totalItems} event</span>
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
              <th className="px-5 py-3 text-left font-bold text-slate-700">Provider</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">External Ref</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Idempotency</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Status</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Received</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Processed</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Error</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {events.map((event) => (
              <tr key={event.id} className="hover:bg-slate-50">
                <td className="whitespace-nowrap px-5 py-4 font-bold text-slate-950">{event.provider}</td>
                <td className="whitespace-nowrap px-5 py-4 font-mono text-xs font-bold text-slate-800">
                  {event.externalReference}
                </td>
                <td className="whitespace-nowrap px-5 py-4 font-mono text-xs text-slate-600">
                  {shortId(event.idempotencyKey)}
                </td>
                <td className="whitespace-nowrap px-5 py-4">
                  <StatusBadge label={webhookStatusLabels[event.status]} tone={webhookStatusTones[event.status]} />
                </td>
                <td className="whitespace-nowrap px-5 py-4 text-slate-700">{formatDateTime(event.receivedAt)}</td>
                <td className="whitespace-nowrap px-5 py-4 text-slate-700">{formatDateTime(event.processedAt)}</td>
                <td className="min-w-64 px-5 py-4 text-slate-700">{event.errorMessage ?? "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 px-5 py-4">
        <p className="text-sm font-semibold text-slate-600">
          Halaman {currentPage} dari {pageCount}
        </p>
        <div className="flex items-center gap-2">
          <button type="button" className={secondaryButtonClass} disabled={page === 0} onClick={onPrevious}>
            Sebelumnya
          </button>
          <button type="button" className={secondaryButtonClass} disabled={totalPages === 0 || currentPage >= totalPages} onClick={onNext}>
            Berikutnya
          </button>
        </div>
      </div>
    </div>
  );
}

function AccountSelect({
  label,
  value,
  accounts,
  disabled,
  placeholder = "Pilih akun aset",
  onChange
}: Readonly<{
  label: string;
  value: string;
  accounts: Account[];
  disabled: boolean;
  placeholder?: string;
  onChange: (value: string) => void;
}>) {
  return (
    <label className="block">
      <span className="text-xs font-bold uppercase text-slate-600">{label}</span>
      <select className={inputClass} value={value} disabled={disabled} onChange={(event) => onChange(event.target.value)}>
        <option value="">{placeholder}</option>
        {accounts.map((account) => (
          <option key={account.id} value={account.id}>
            {accountLabel(account)}
          </option>
        ))}
      </select>
    </label>
  );
}

function SettlementResultPanel({ result }: Readonly<{ result: PaymentSettlement }>) {
  return (
    <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm font-semibold text-emerald-900">
      <div className="flex items-center gap-2 font-bold">
        <CheckCircle2 className="size-4" aria-hidden="true" />
        Settlement berhasil
      </div>
      <div className="mt-2 grid gap-2 text-xs sm:grid-cols-2">
        <div>
          <span className="text-emerald-700">Payment</span>
          <p className="font-mono text-emerald-950">{result.paymentNumber}</p>
        </div>
        <div>
          <span className="text-emerald-700">Receipt</span>
          <p className="font-mono text-emerald-950">{result.receipt.receiptNumber}</p>
        </div>
        <div>
          <span className="text-emerald-700">Payment ID</span>
          <p className="font-mono text-emerald-950">{result.id}</p>
        </div>
        <div>
          <span className="text-emerald-700">Jurnal</span>
          <p className="font-mono text-emerald-950">{shortId(result.settlementJournalEntryId)}</p>
        </div>
      </div>
    </div>
  );
}

function CounterSettlementForm({ allowed, accounts }: Readonly<{ allowed: boolean; accounts: Account[] }>) {
  const settleMutation = useSettleCounterPayment();
  const [form, setForm] = useState<CounterPaymentFormState>(() => defaultCounterPaymentForm());
  const [localError, setLocalError] = useState<string | null>(null);
  const [result, setResult] = useState<PaymentSettlement | null>(null);
  const assetAccounts = useMemo(() => accounts.filter((account) => account.type === "ASSET"), [accounts]);
  const allocationTotal = useMemo(() => allocationTotalAmount(form.allocations), [form.allocations]);

  function updateAllocation(clientId: string, patch: Partial<CounterPaymentAllocationDraft>) {
    setForm((current) => ({
      ...current,
      allocations: current.allocations.map((allocation) =>
        allocation.clientId === clientId ? { ...allocation, ...patch } : allocation
      )
    }));
  }

  function removeAllocation(clientId: string) {
    setForm((current) => ({
      ...current,
      allocations: current.allocations.length === 1
        ? current.allocations
        : current.allocations.filter((allocation) => allocation.clientId !== clientId)
    }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLocalError(null);
    setResult(null);
    settleMutation.reset();

    if (!allowed) {
      setLocalError("User tidak memiliki permission payment.counter.");
      return;
    }

    const errors = counterPaymentErrors({ draft: form, accounts });
    if (errors.length > 0) {
      setLocalError(errors[0]);
      return;
    }

    const amount = parseMoneyInput(form.amount);
    const allocations = form.allocations.map((allocation) => ({
      invoiceId: normalizeInput(allocation.invoiceId),
      amount: parseMoneyInput(allocation.amount)
    }));

    if (amount === null || allocations.some((allocation) => allocation.amount === null)) {
      setLocalError("Nominal pembayaran dan alokasi wajib valid.");
      return;
    }

    const payload: SettleCounterPaymentPayload = {
      externalReference: optionalString(form.externalReference),
      amount,
      paidAt: toInstant(form.paidAt),
      allocations: allocations.map((allocation) => ({
        invoiceId: allocation.invoiceId,
        amount: allocation.amount ?? 0
      })),
      cashAccountId: form.cashAccountId,
      receivableAccountId: form.receivableAccountId,
      reason: normalizeInput(form.reason)
    };

    try {
      const response = await settleMutation.mutateAsync({
        payload,
        idempotencyKey: counterPaymentIdempotencyKey()
      });
      setForm(defaultCounterPaymentForm());
      setResult(response);
    } catch {
      return;
    }
  }

  const disabled = !allowed || settleMutation.isPending;
  const errorMessage =
    localError ?? (settleMutation.isError ? apiErrorMessage(settleMutation.error, "Gagal settlement pembayaran.") : null);

  return (
    <form onSubmit={handleSubmit} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-bold text-slate-950">Counter Settlement</h3>
        <StatusBadge label={allowed ? "payment.counter" : "Locked"} tone={allowed ? "success" : "neutral"} />
      </div>
      <div className="mt-4 grid gap-3">
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">External Reference</span>
          <input
            className={inputClass}
            value={form.externalReference}
            maxLength={128}
            disabled={disabled}
            onChange={(event) => setForm((current) => ({ ...current, externalReference: event.target.value }))}
            placeholder="LOKET-20260707-001"
          />
        </label>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Nominal</span>
            <input
              type="number"
              min="0.01"
              step="0.01"
              className={inputClass}
              value={form.amount}
              disabled={disabled}
              onChange={(event) => setForm((current) => ({ ...current, amount: event.target.value }))}
              placeholder="100000"
            />
          </label>
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Waktu Bayar</span>
            <input
              type="datetime-local"
              className={inputClass}
              value={form.paidAt}
              disabled={disabled}
              onChange={(event) => setForm((current) => ({ ...current, paidAt: event.target.value }))}
            />
          </label>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <AccountSelect
            label="Akun Kas/Bank"
            value={form.cashAccountId}
            accounts={assetAccounts}
            disabled={disabled}
            onChange={(value) => setForm((current) => ({ ...current, cashAccountId: value }))}
          />
          <AccountSelect
            label="Akun Piutang"
            value={form.receivableAccountId}
            accounts={assetAccounts}
            disabled={disabled}
            onChange={(value) => setForm((current) => ({ ...current, receivableAccountId: value }))}
          />
        </div>
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-xs font-bold uppercase text-slate-600">Alokasi Invoice</p>
            <button
              type="button"
              className={secondaryButtonClass}
              disabled={disabled}
              onClick={() => setForm((current) => ({ ...current, allocations: [...current.allocations, newAllocation()] }))}
            >
              <Plus className="size-4" aria-hidden="true" />
              Tambah
            </button>
          </div>
          <div className="mt-3 space-y-3">
            {form.allocations.map((allocation, index) => (
              <div key={allocation.clientId} className="grid gap-3 md:grid-cols-[minmax(0,1fr)_160px_auto]">
                <label className="block">
                  <span className="text-xs font-bold uppercase text-slate-600">Invoice ID {index + 1}</span>
                  <input
                    className={inputClass}
                    value={allocation.invoiceId}
                    disabled={disabled}
                    onChange={(event) => updateAllocation(allocation.clientId, { invoiceId: event.target.value })}
                    placeholder="UUID invoice"
                  />
                </label>
                <label className="block">
                  <span className="text-xs font-bold uppercase text-slate-600">Nominal</span>
                  <input
                    type="number"
                    min="0.01"
                    step="0.01"
                    className={inputClass}
                    value={allocation.amount}
                    disabled={disabled}
                    onChange={(event) => updateAllocation(allocation.clientId, { amount: event.target.value })}
                    placeholder="0"
                  />
                </label>
                <div className="flex items-end">
                  <button
                    type="button"
                    className={secondaryButtonClass}
                    disabled={disabled || form.allocations.length === 1}
                    onClick={() => removeAllocation(allocation.clientId)}
                    aria-label={`Hapus alokasi ${index + 1}`}
                  >
                    <Trash2 className="size-4" aria-hidden="true" />
                  </button>
                </div>
              </div>
            ))}
          </div>
          <div className="mt-3 flex justify-end text-sm font-bold text-slate-950">
            Total alokasi: <span className="ml-2"><MoneyText value={allocationTotal} /></span>
          </div>
        </div>
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Alasan Audit</span>
          <textarea
            className={textareaClass}
            value={form.reason}
            maxLength={500}
            disabled={disabled}
            onChange={(event) => setForm((current) => ({ ...current, reason: event.target.value }))}
          />
        </label>
        {errorMessage ? <InlineMessage type="error" message={errorMessage} /> : null}
        {result ? <SettlementResultPanel result={result} /> : null}
        <button type="submit" className={primaryButtonClass} disabled={disabled}>
          {settleMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <WalletCards className="size-4" aria-hidden="true" />}
          Settle Payment
        </button>
      </div>
    </form>
  );
}

function ReversePaymentForm({ allowed, accounts }: Readonly<{ allowed: boolean; accounts: Account[] }>) {
  const reverseMutation = useReversePayment();
  const [form, setForm] = useState<ReversePaymentDraft>(defaultReversePaymentForm);
  const [localError, setLocalError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const assetAccounts = useMemo(() => accounts.filter((account) => account.type === "ASSET"), [accounts]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLocalError(null);
    setSuccessMessage(null);
    reverseMutation.reset();

    if (!allowed) {
      setLocalError("User tidak memiliki permission payment.reverse.");
      return;
    }

    const errors = reversePaymentErrors({ draft: form, accounts });
    if (errors.length > 0) {
      setLocalError(errors[0]);
      return;
    }

    const payload: ReversePaymentPayload = {
      cashAccountId: form.cashAccountId,
      receivableAccountId: form.receivableAccountId,
      reason: normalizeInput(form.reason)
    };

    try {
      const response = await reverseMutation.mutateAsync({
        paymentId: normalizeInput(form.paymentId),
        payload
      });
      setForm(defaultReversePaymentForm);
      setSuccessMessage(`Payment ${response.paymentNumber} berhasil di-reverse.`);
    } catch {
      return;
    }
  }

  const disabled = !allowed || reverseMutation.isPending;
  const errorMessage =
    localError ?? (reverseMutation.isError ? apiErrorMessage(reverseMutation.error, "Gagal reversal pembayaran.") : null);

  return (
    <form onSubmit={handleSubmit} className="rounded-lg border border-red-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-bold text-red-950">Payment Reversal</h3>
        <div className="flex items-center gap-2">
          <StatusBadge label="High Risk" tone="danger" />
          <StatusBadge label={allowed ? "payment.reverse" : "Locked"} tone={allowed ? "success" : "neutral"} />
        </div>
      </div>
      <div className="mt-4 grid gap-3">
        <label className="block">
          <span className="text-xs font-bold uppercase text-red-800">Payment ID</span>
          <input
            className={inputClass}
            value={form.paymentId}
            disabled={disabled}
            onChange={(event) => setForm((current) => ({ ...current, paymentId: event.target.value }))}
            placeholder="UUID payment"
          />
        </label>
        <div className="grid gap-3 sm:grid-cols-2">
          <AccountSelect
            label="Akun Kas/Bank"
            value={form.cashAccountId}
            accounts={assetAccounts}
            disabled={disabled}
            onChange={(value) => setForm((current) => ({ ...current, cashAccountId: value }))}
          />
          <AccountSelect
            label="Akun Piutang"
            value={form.receivableAccountId}
            accounts={assetAccounts}
            disabled={disabled}
            onChange={(value) => setForm((current) => ({ ...current, receivableAccountId: value }))}
          />
        </div>
        <label className="block">
          <span className="text-xs font-bold uppercase text-red-800">Alasan Reversal</span>
          <textarea
            className={textareaClass}
            value={form.reason}
            maxLength={500}
            disabled={disabled}
            onChange={(event) => setForm((current) => ({ ...current, reason: event.target.value }))}
          />
        </label>
        {errorMessage ? <InlineMessage type="error" message={errorMessage} /> : null}
        {successMessage ? <InlineMessage type="success" message={successMessage} /> : null}
        <button type="submit" className={dangerButtonClass} disabled={disabled}>
          {reverseMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Undo2 className="size-4" aria-hidden="true" />}
          Reverse Payment
        </button>
      </div>
    </form>
  );
}

export function PaymentWorkspace() {
  const [paymentChannel, setPaymentChannel] = useState("");
  const [paymentStatus, setPaymentStatus] = useState<StatusFilter<PaymentStatus>>("ALL");
  const [paymentPage, setPaymentPage] = useState(0);
  const [selectedPaymentId, setSelectedPaymentId] = useState<string | null>(null);
  const [provider, setProvider] = useState("");
  const [status, setStatus] = useState<StatusFilter<PaymentWebhookStatus>>("ALL");
  const [page, setPage] = useState(0);
  const currentUserQuery = useCurrentUser();
  const financialPermissions = useMemo(
    () => resolveFinancialCommandPermissions(currentUserQuery.data?.authorities ?? []),
    [currentUserQuery.data?.authorities]
  );
  const permissions = financialPermissions.payment;
  const queryEnabled = currentUserQuery.isSuccess;
  const paymentsEnabled = queryEnabled && canReadPayments(permissions);
  const eventsEnabled = queryEnabled && permissions.canReadWebhookEvents;
  const canCreateReconciliationAdjustments =
    permissions.canReconcilePayments && financialPermissions.accounting.canPostJournals;
  const canSignOffReconciliationEvidence =
    permissions.canReconcilePayments && canSignOffPaymentReconciliations(permissions);
  const canManageReconciliationNotes =
    permissions.canReconcilePayments && canManageReconciliationHandoffNotes(permissions);
  const accountsEnabled =
    queryEnabled &&
    (permissions.canSettleCounterPayments || permissions.canReversePayments || canCreateReconciliationAdjustments);
  const normalizedPaymentChannel = paymentChannel.trim() || undefined;
  const normalizedProvider = provider.trim() || undefined;

  const paymentsQuery = usePayments(
    {
      channel: normalizedPaymentChannel,
      status: paymentStatus === "ALL" ? undefined : paymentStatus,
      page: paymentPage,
      size: 25
    },
    paymentsEnabled
  );
  const paymentDetailQuery = usePayment(selectedPaymentId, paymentsEnabled && Boolean(selectedPaymentId));
  const webhookEventsQuery = usePaymentWebhookEvents(
    {
      provider: normalizedProvider,
      status: status === "ALL" ? undefined : status,
      page,
      size: 25
    },
    eventsEnabled
  );
  const accountsQuery = useAccounts({ page: 0, size: 100 }, accountsEnabled);

  const payments = useMemo(() => paymentsQuery.data?.items ?? [], [paymentsQuery.data?.items]);
  const events = useMemo(() => webhookEventsQuery.data?.items ?? [], [webhookEventsQuery.data?.items]);
  const accounts = useMemo(() => accountsQuery.data?.items ?? [], [accountsQuery.data?.items]);
  const paymentSummary = useMemo(() => summarizePaymentList(payments), [payments]);
  const summary = useMemo(() => summarizePaymentWorkspace(events), [events]);

  function resetPaymentFilters() {
    setPaymentChannel("");
    setPaymentStatus("ALL");
    setPaymentPage(0);
    setSelectedPaymentId(null);
  }

  function resetFilters() {
    setProvider("");
    setStatus("ALL");
    setPage(0);
  }

  function refetchAll() {
    if (paymentsEnabled) {
      void paymentsQuery.refetch();
    }
    if (paymentsEnabled && selectedPaymentId) {
      void paymentDetailQuery.refetch();
    }
    if (eventsEnabled) {
      void webhookEventsQuery.refetch();
    }
    if (accountsEnabled) {
      void accountsQuery.refetch();
    }
  }

  if (currentUserQuery.isLoading) {
    return <LoadingSkeleton />;
  }

  if (currentUserQuery.isError) {
    return <ErrorState message={apiErrorMessage(currentUserQuery.error, "Sesi atau otorisasi pembayaran tidak tersedia.")} />;
  }

  const isInitialLoading =
    (paymentsEnabled && paymentsQuery.isLoading) ||
    (eventsEnabled && webhookEventsQuery.isLoading) ||
    (accountsEnabled && accountsQuery.isLoading);
  const hasError =
    (paymentsEnabled && paymentsQuery.isError) ||
    (eventsEnabled && webhookEventsQuery.isError) ||
    (accountsEnabled && accountsQuery.isError);
  const error = paymentsQuery.error ?? webhookEventsQuery.error ?? accountsQuery.error;

  return (
    <main className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <PageHeader
          title="Pembayaran"
          description="Workspace kontrol untuk settlement loket, reversal pembayaran, dan monitoring webhook provider."
        />
        <div className="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm shadow-sm">
          <div className="flex items-center gap-2 font-bold text-slate-950">
            <ShieldCheck className="size-4 text-slate-600" aria-hidden="true" />
            Payment Control
          </div>
          <p className="mt-1 font-semibold text-slate-600">{currentUserQuery.data?.username}</p>
        </div>
      </div>

      {isInitialLoading ? <LoadingSkeleton /> : null}

      {hasError ? (
        <div className="space-y-3">
          <ErrorState message={apiErrorMessage(error, "Data pembayaran tidak tersedia.")} />
          <button type="button" onClick={refetchAll} className={secondaryButtonClass}>
            <RotateCcw className="size-4" aria-hidden="true" />
            Muat Ulang
          </button>
        </div>
      ) : null}

      {!isInitialLoading && !hasError ? (
        <>
          <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <SummaryCard
              label="Settled"
              value={String(paymentSummary.settledPayments)}
              helper={`Total settled pada halaman aktif: ${new Intl.NumberFormat("id-ID").format(paymentSummary.totalSettledAmount)}.`}
              tone="success"
            />
            <SummaryCard
              label="Reversed"
              value={String(paymentSummary.reversedPayments)}
              helper={`Total reversal pada halaman aktif: ${new Intl.NumberFormat("id-ID").format(paymentSummary.totalReversedAmount)}.`}
              tone="neutral"
            />
            <SummaryCard
              label="Net Cash"
              value={<MoneyText value={paymentSummary.netCashImpact} />}
              helper="Settled dikurangi reversal pada halaman aktif."
              tone="info"
            />
            <SummaryCard
              label="Webhook Failed"
              value={String(summary.failedEvents)}
              helper={`${summary.unresolvedFailures} failure masih memiliki pesan error.`}
              tone="warning"
            />
          </section>

          <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_460px]">
            <div className="space-y-4">
              <PaymentListToolbar
                channel={paymentChannel}
                status={paymentStatus}
                canRead={permissions.canReadPayments}
                onChannelChange={(value) => {
                  setPaymentChannel(value);
                  setPaymentPage(0);
                  setSelectedPaymentId(null);
                }}
                onStatusChange={(value) => {
                  setPaymentStatus(value);
                  setPaymentPage(0);
                  setSelectedPaymentId(null);
                }}
                onReset={resetPaymentFilters}
              />
              <PaymentTable
                payments={payments}
                canRead={permissions.canReadPayments}
                isFetching={paymentsQuery.isFetching}
                selectedPaymentId={selectedPaymentId}
                page={paymentsQuery.data?.page ?? paymentPage}
                totalPages={paymentsQuery.data?.totalPages ?? 0}
                totalItems={paymentsQuery.data?.totalItems ?? 0}
                onSelect={setSelectedPaymentId}
                onPrevious={() => setPaymentPage((current) => Math.max(current - 1, 0))}
                onNext={() => setPaymentPage((current) => current + 1)}
              />
              <PaymentDetailPanel
                payment={paymentDetailQuery.data}
                canRead={permissions.canReadPayments}
                selectedPaymentId={selectedPaymentId}
                isLoading={paymentDetailQuery.isLoading}
                isFetching={paymentDetailQuery.isFetching}
                error={paymentDetailQuery.error}
                onClear={() => setSelectedPaymentId(null)}
              />
              <PaymentReconciliationReviewRegisterPanel
                allowed={canReconcilePayments(permissions)}
                canManageNotes={canManageReconciliationNotes}
              />
              <PaymentReconciliationHandoffWorkloadPanel allowed={canReconcilePayments(permissions)} />
              <PaymentFilterToolbar
                provider={provider}
                status={status}
                canRead={permissions.canReadWebhookEvents}
                onProviderChange={(value) => {
                  setProvider(value);
                  setPage(0);
                }}
                onStatusChange={(value) => {
                  setStatus(value);
                  setPage(0);
                }}
                onReset={resetFilters}
              />
              <WebhookEventTable
                events={events}
                canRead={permissions.canReadWebhookEvents}
                isFetching={webhookEventsQuery.isFetching}
                page={webhookEventsQuery.data?.page ?? page}
                totalPages={webhookEventsQuery.data?.totalPages ?? 0}
                totalItems={webhookEventsQuery.data?.totalItems ?? 0}
                onPrevious={() => setPage((current) => Math.max(current - 1, 0))}
                onNext={() => setPage((current) => current + 1)}
              />
            </div>
            <div className="space-y-4">
              <PaymentCommandPanel permissions={permissions} />
              <PaymentReconciliationPanel
                allowed={canReconcilePayments(permissions)}
                canAdjust={canCreateReconciliationAdjustments}
                canSignOff={canSignOffReconciliationEvidence}
                accounts={accounts}
                channel={paymentChannel}
                status={paymentStatus}
                currentActor={currentUserQuery.data?.username ?? null}
              />
              <CounterSettlementForm
                allowed={canSettleCounterPayment(permissions)}
                accounts={accounts}
              />
              <ReversePaymentForm
                allowed={canReversePayment(permissions)}
                accounts={accounts}
              />
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
                <div className="flex items-center gap-2 text-sm font-bold text-amber-900">
                  <LockKeyhole className="size-4" aria-hidden="true" />
                  Guardrail
                </div>
                <p className="mt-2 text-sm leading-6 text-amber-900">
                  Settlement memakai idempotency key dan total alokasi wajib sama dengan nominal bayar. Reversal wajib
                  memakai alasan audit dan tetap diposting sebagai jurnal balik oleh backend.
                </p>
              </div>
            </div>
          </section>
        </>
      ) : null}
    </main>
  );
}
