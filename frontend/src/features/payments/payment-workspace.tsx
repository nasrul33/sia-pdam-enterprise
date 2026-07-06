"use client";

import {
  AlertTriangle,
  CheckCircle2,
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
  canReversePayment,
  canSettleCounterPayment,
  counterPaymentErrors,
  parseMoneyInput,
  reversePaymentErrors,
  summarizePaymentWorkspace,
  type CounterPaymentAllocationDraft,
  type CounterPaymentDraft,
  type ReversePaymentDraft
} from "./payment-workspace-model";
import type {
  PaymentSettlement,
  PaymentWebhookEvent,
  PaymentWebhookStatus,
  ReversePaymentPayload,
  SettleCounterPaymentPayload
} from "./payment-schema";
import { paymentWebhookStatusValues } from "./payment-schema";
import { usePaymentWebhookEvents, useReversePayment, useSettleCounterPayment } from "./use-payments";

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
  const [provider, setProvider] = useState("");
  const [status, setStatus] = useState<StatusFilter<PaymentWebhookStatus>>("ALL");
  const [page, setPage] = useState(0);
  const currentUserQuery = useCurrentUser();
  const permissions = useMemo(
    () => resolveFinancialCommandPermissions(currentUserQuery.data?.authorities ?? []).payment,
    [currentUserQuery.data?.authorities]
  );
  const queryEnabled = currentUserQuery.isSuccess;
  const eventsEnabled = queryEnabled && permissions.canReadWebhookEvents;
  const accountsEnabled = queryEnabled && (permissions.canSettleCounterPayments || permissions.canReversePayments);
  const normalizedProvider = provider.trim() || undefined;

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

  const events = useMemo(() => webhookEventsQuery.data?.items ?? [], [webhookEventsQuery.data?.items]);
  const accounts = useMemo(() => accountsQuery.data?.items ?? [], [accountsQuery.data?.items]);
  const summary = useMemo(() => summarizePaymentWorkspace(events), [events]);

  function resetFilters() {
    setProvider("");
    setStatus("ALL");
    setPage(0);
  }

  function refetchAll() {
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
    (eventsEnabled && webhookEventsQuery.isLoading) || (accountsEnabled && accountsQuery.isLoading);
  const hasError = (eventsEnabled && webhookEventsQuery.isError) || (accountsEnabled && accountsQuery.isError);
  const error = webhookEventsQuery.error ?? accountsQuery.error;

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
              label="Received"
              value={String(summary.receivedEvents)}
              helper="Event provider yang sudah diterima backend."
              tone="info"
            />
            <SummaryCard
              label="Processed"
              value={String(summary.processedEvents)}
              helper="Event yang selesai diproses tanpa error."
              tone="success"
            />
            <SummaryCard
              label="Failed"
              value={String(summary.failedEvents)}
              helper={`${summary.unresolvedFailures} failure masih memiliki pesan error.`}
              tone="warning"
            />
            <SummaryCard
              label="Ignored"
              value={String(summary.ignoredEvents)}
              helper="Event duplikat atau tidak relevan pada filter aktif."
              tone="neutral"
            />
          </section>

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

          <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_460px]">
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
