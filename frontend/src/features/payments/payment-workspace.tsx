"use client";

import {
  AlertTriangle,
  CheckCircle2,
  Eye,
  FileSearch,
  ListFilter,
  Loader2,
  LockKeyhole,
  Plus,
  ReceiptText,
  RotateCcw,
  ShieldCheck,
  Trash2,
  Undo2,
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
  canReadPayments,
  canReversePayment,
  canSettleCounterPayment,
  counterPaymentErrors,
  parseMoneyInput,
  reversePaymentErrors,
  summarizePaymentList,
  summarizePaymentWorkspace,
  type CounterPaymentAllocationDraft,
  type CounterPaymentDraft,
  type ReversePaymentDraft
} from "./payment-workspace-model";
import type {
  PaymentSettlement,
  PaymentStatus,
  PaymentSummary,
  PaymentWebhookEvent,
  PaymentWebhookStatus,
  ReversePaymentPayload,
  SettleCounterPaymentPayload
} from "./payment-schema";
import { paymentStatusValues, paymentWebhookStatusValues } from "./payment-schema";
import { usePayment, usePaymentWebhookEvents, usePayments, useReversePayment, useSettleCounterPayment } from "./use-payments";

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

const webhookStatusTones: Record<PaymentWebhookStatus, "success" | "warning" | "danger" | "neutral" | "info"> = {
  RECEIVED: "info",
  PROCESSED: "success",
  FAILED: "danger",
  IGNORED: "neutral"
};

const inputClass =
  "mt-1 h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-sky-500 focus:ring-2 focus:ring-sky-100 disabled:bg-slate-100 disabled:text-slate-500";

const textareaClass =
  "mt-1 min-h-20 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-sky-500 focus:ring-2 focus:ring-sky-100 disabled:bg-slate-100 disabled:text-slate-500";

const primaryButtonClass =
  "inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-sky-700 px-4 text-sm font-bold text-white transition hover:bg-sky-800 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-600";

const secondaryButtonClass =
  "inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-4 text-sm font-bold text-slate-800 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500";

const dangerButtonClass =
  "inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-red-700 px-4 text-sm font-bold text-white transition hover:bg-red-800 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-600";

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

function normalizeInput(value: string): string {
  return value.trim();
}

function optionalString(value: string): string | null {
  const normalized = normalizeInput(value);
  return normalized.length > 0 ? normalized : null;
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

function accountLabel(account: Account): string {
  return `${account.code} - ${account.name}`;
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
  onChange
}: Readonly<{
  label: string;
  value: string;
  accounts: Account[];
  disabled: boolean;
  onChange: (value: string) => void;
}>) {
  return (
    <label className="block">
      <span className="text-xs font-bold uppercase text-slate-600">{label}</span>
      <select className={inputClass} value={value} disabled={disabled} onChange={(event) => onChange(event.target.value)}>
        <option value="">Pilih akun aset</option>
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
  const permissions = useMemo(
    () => resolveFinancialCommandPermissions(currentUserQuery.data?.authorities ?? []).payment,
    [currentUserQuery.data?.authorities]
  );
  const queryEnabled = currentUserQuery.isSuccess;
  const paymentsEnabled = queryEnabled && canReadPayments(permissions);
  const eventsEnabled = queryEnabled && permissions.canReadWebhookEvents;
  const accountsEnabled = queryEnabled && (permissions.canSettleCounterPayments || permissions.canReversePayments);
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
