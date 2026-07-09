"use client";

import {
  AlertTriangle,
  BookOpenCheck,
  CalendarDays,
  CheckCircle2,
  CirclePlus,
  Eye,
  FileText,
  Loader2,
  LockKeyhole,
  Plus,
  RotateCcw,
  Send,
  ShieldCheck,
  Trash2,
  X
} from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { PageHeader } from "@/components/common/page-header";
import { EmptyState } from "@/components/state/empty-state";
import { ErrorState } from "@/components/state/error-state";
import { LoadingSkeleton } from "@/components/state/loading-skeleton";
import { StatusBadge } from "@/components/status/status-badge";
import { useCurrentUser } from "@/features/auth/use-current-user";
import { resolveFinancialCommandPermissions } from "@/features/security/financial-command-permissions";
import { apiErrorMessage } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import {
  allowedAccountingJournalWorkflows,
  allowedAccountingPeriodWorkflows,
  manualJournalDraftErrors,
  summarizeAccountingWorkspace,
  summarizeJournalDetailLines,
  summarizeManualJournalDraft
} from "./accounting-workspace-model";
import type {
  Account,
  AccountingPeriod,
  AccountingPeriodWorkflow,
  AccountType,
  CreateJournalLinePayload,
  Journal,
  JournalStatus,
  JournalSummary
} from "./accounting-schema";
import { accountTypeValues, journalStatusValues } from "./accounting-schema";
import {
  useAccountingPeriodWorkflow,
  useAccountingPeriods,
  useAccounts,
  useCreateAccountingPeriod,
  useCreateAccount,
  useCreateJournal,
  useJournal,
  useJournals,
  usePostJournal
} from "./use-accounting";

type JournalStatusFilter = JournalStatus | "ALL";
type AccountingPermissions = ReturnType<typeof resolveFinancialCommandPermissions>["accounting"];
type FeedbackType = "success" | "error";

type AccountFormState = {
  code: string;
  name: string;
  type: AccountType;
  reason: string;
};

type PeriodFormState = {
  period: string;
  reason: string;
};

type JournalFormLineState = {
  localId: string;
  accountId: string;
  debit: string;
  credit: string;
  description: string;
};

type JournalFormState = {
  journalNumber: string;
  accountingPeriodId: string;
  description: string;
  reason: string;
  lines: JournalFormLineState[];
};

type PeriodWorkflowDraft = {
  period: AccountingPeriod;
  workflow: AccountingPeriodWorkflow;
  title: string;
  consequence: string;
  reason: string;
};

type JournalPostDraft = {
  journal: JournalSummary;
  reason: string;
};

const accountTypeLabels: Record<Account["type"], string> = {
  ASSET: "Aset",
  LIABILITY: "Liabilitas",
  EQUITY: "Ekuitas",
  REVENUE: "Pendapatan",
  EXPENSE: "Beban"
};

const normalBalanceLabels: Record<Account["normalBalance"], string> = {
  DEBIT: "Debit",
  CREDIT: "Kredit"
};

const periodStatusLabels: Record<AccountingPeriod["status"], string> = {
  OPEN: "Open",
  CLOSING_REVIEW: "Review Tutup",
  LOCKED: "Terkunci",
  REOPENED: "Reopened"
};

const periodStatusTones: Record<AccountingPeriod["status"], "success" | "warning" | "danger" | "info"> = {
  OPEN: "success",
  CLOSING_REVIEW: "warning",
  LOCKED: "danger",
  REOPENED: "info"
};

const journalStatusLabels: Record<JournalStatus, string> = {
  DRAFT: "Draft",
  POSTED: "Posted",
  REVERSED: "Reversed",
  VOID: "Void"
};

const journalStatusTones: Record<JournalStatus, "success" | "warning" | "danger" | "neutral"> = {
  DRAFT: "warning",
  POSTED: "success",
  REVERSED: "neutral",
  VOID: "danger"
};

const defaultAccountForm: AccountFormState = {
  code: "",
  name: "",
  type: "ASSET",
  reason: ""
};

const defaultPeriodForm: PeriodFormState = {
  period: "",
  reason: ""
};

const amountFormatter = new Intl.NumberFormat("id-ID", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

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

function decimalInputToNumber(value: string): number {
  const normalized = normalizeInput(value).replace(",", ".");
  return normalized ? Number(normalized) : 0;
}

function newJournalLine(): JournalFormLineState {
  return {
    localId: `line-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    accountId: "",
    debit: "",
    credit: "",
    description: ""
  };
}

function defaultJournalForm(): JournalFormState {
  return {
    journalNumber: "",
    accountingPeriodId: "",
    description: "",
    reason: "",
    lines: [newJournalLine(), newJournalLine()]
  };
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

function accountDisplay(accountId: string, accountById: ReadonlyMap<string, Account>): string {
  const account = accountById.get(accountId);
  return account ? `${account.code} - ${account.name}` : shortId(accountId);
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

function AccountingCommandPanel({ permissions }: Readonly<{ permissions: AccountingPermissions }>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 p-4">
        <div className="flex items-center gap-2">
          <BookOpenCheck className="size-5 text-teal-700" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Accounting Command</h2>
        </div>
      </div>
      <CommandStatus label="Kelola CoA" allowed={permissions.canManageAccounts} />
      <CommandStatus label="Kelola Periode" allowed={permissions.canManagePeriods} />
      <CommandStatus label="Tutup Periode" allowed={permissions.canClosePeriods} highRisk />
      <CommandStatus label="Buat Jurnal" allowed={permissions.canCreateJournals} />
      <CommandStatus label="Posting Jurnal" allowed={permissions.canPostJournals} highRisk />
    </div>
  );
}

function SectionTitle({ icon, title }: Readonly<{ icon: ReactNode; title: string }>) {
  return (
    <div className="flex items-center gap-2 border-b border-slate-200 px-5 py-4">
      {icon}
      <h2 className="text-base font-bold text-slate-950">{title}</h2>
    </div>
  );
}

function AccountCommandForm({ allowed }: Readonly<{ allowed: boolean }>) {
  const createAccountMutation = useCreateAccount();
  const [form, setForm] = useState<AccountFormState>(defaultAccountForm);
  const [localError, setLocalError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLocalError(null);
    setSuccessMessage(null);
    createAccountMutation.reset();

    if (!allowed) {
      setLocalError("User tidak memiliki permission account.manage.");
      return;
    }

    const code = normalizeInput(form.code);
    const name = normalizeInput(form.name);
    const reason = normalizeInput(form.reason);

    if (!code || !name || !reason) {
      setLocalError("Kode, nama akun, dan alasan audit wajib diisi.");
      return;
    }

    try {
      await createAccountMutation.mutateAsync({
        code,
        name,
        type: form.type,
        reason
      });
    } catch {
      return;
    }

    setForm(defaultAccountForm);
    setSuccessMessage(`Akun ${code} dibuat.`);
  }

  const errorMessage =
    localError ?? (createAccountMutation.isError ? apiErrorMessage(createAccountMutation.error, "Gagal membuat akun.") : null);

  return (
    <form onSubmit={handleSubmit} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-bold text-slate-950">Buat CoA</h3>
        <StatusBadge label={allowed ? "account.manage" : "Locked"} tone={allowed ? "success" : "neutral"} />
      </div>
      <div className="mt-4 grid gap-3">
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Kode</span>
          <input
            className={inputClass}
            value={form.code}
            maxLength={64}
            disabled={!allowed || createAccountMutation.isPending}
            onChange={(event) => setForm((current) => ({ ...current, code: event.target.value }))}
            placeholder="1-1100"
          />
        </label>
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Nama Akun</span>
          <input
            className={inputClass}
            value={form.name}
            maxLength={255}
            disabled={!allowed || createAccountMutation.isPending}
            onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
            placeholder="Kas Operasional"
          />
        </label>
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Tipe</span>
          <select
            className={inputClass}
            value={form.type}
            disabled={!allowed || createAccountMutation.isPending}
            onChange={(event) => setForm((current) => ({ ...current, type: event.target.value as AccountType }))}
          >
            {accountTypeValues.map((value) => (
              <option key={value} value={value}>
                {accountTypeLabels[value]}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Alasan Audit</span>
          <textarea
            className={textareaClass}
            value={form.reason}
            maxLength={500}
            disabled={!allowed || createAccountMutation.isPending}
            onChange={(event) => setForm((current) => ({ ...current, reason: event.target.value }))}
          />
        </label>
        {errorMessage ? <InlineMessage type="error" message={errorMessage} /> : null}
        {successMessage ? <InlineMessage type="success" message={successMessage} /> : null}
        <button type="submit" className={primaryButtonClass} disabled={!allowed || createAccountMutation.isPending}>
          {createAccountMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Plus className="size-4" aria-hidden="true" />}
          Simpan CoA
        </button>
      </div>
    </form>
  );
}

function PeriodCommandForm({ allowed }: Readonly<{ allowed: boolean }>) {
  const createPeriodMutation = useCreateAccountingPeriod();
  const [form, setForm] = useState<PeriodFormState>(defaultPeriodForm);
  const [localError, setLocalError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLocalError(null);
    setSuccessMessage(null);
    createPeriodMutation.reset();

    if (!allowed) {
      setLocalError("User tidak memiliki permission period.manage.");
      return;
    }

    const period = normalizeInput(form.period);
    const reason = normalizeInput(form.reason);

    if (!/^\d{4}-\d{2}$/.test(period)) {
      setLocalError("Periode wajib menggunakan format yyyy-MM.");
      return;
    }
    if (!reason) {
      setLocalError("Alasan audit wajib diisi.");
      return;
    }

    try {
      await createPeriodMutation.mutateAsync({ period, reason });
    } catch {
      return;
    }
    setForm(defaultPeriodForm);
    setSuccessMessage(`Periode ${period} dibuat.`);
  }

  const errorMessage =
    localError ??
    (createPeriodMutation.isError ? apiErrorMessage(createPeriodMutation.error, "Gagal membuat periode.") : null);

  return (
    <form onSubmit={handleSubmit} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-bold text-slate-950">Buat Periode</h3>
        <StatusBadge label={allowed ? "period.manage" : "Locked"} tone={allowed ? "success" : "neutral"} />
      </div>
      <div className="mt-4 grid gap-3">
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Periode</span>
          <input
            className={inputClass}
            value={form.period}
            maxLength={7}
            disabled={!allowed || createPeriodMutation.isPending}
            onChange={(event) => setForm((current) => ({ ...current, period: event.target.value }))}
            placeholder="2026-07"
          />
        </label>
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Alasan Audit</span>
          <textarea
            className={textareaClass}
            value={form.reason}
            maxLength={500}
            disabled={!allowed || createPeriodMutation.isPending}
            onChange={(event) => setForm((current) => ({ ...current, reason: event.target.value }))}
          />
        </label>
        {errorMessage ? <InlineMessage type="error" message={errorMessage} /> : null}
        {successMessage ? <InlineMessage type="success" message={successMessage} /> : null}
        <button type="submit" className={primaryButtonClass} disabled={!allowed || createPeriodMutation.isPending}>
          {createPeriodMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Plus className="size-4" aria-hidden="true" />}
          Simpan Periode
        </button>
      </div>
    </form>
  );
}

function JournalCommandForm({
  allowed,
  accounts,
  periods
}: Readonly<{ allowed: boolean; accounts: Account[]; periods: AccountingPeriod[] }>) {
  const createJournalMutation = useCreateJournal();
  const [form, setForm] = useState<JournalFormState>(() => defaultJournalForm());
  const [localError, setLocalError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const postingPeriods = useMemo(() => periods.filter((period) => period.allowsPosting), [periods]);
  const draftSummary = useMemo(() => summarizeManualJournalDraft(form.lines), [form.lines]);
  const validationErrors = useMemo(
    () =>
      manualJournalDraftErrors({
        journalNumber: form.journalNumber,
        accountingPeriodId: form.accountingPeriodId,
        description: form.description,
        reason: form.reason,
        lines: form.lines
      }),
    [form]
  );

  function updateLine(lineId: string, patch: Partial<JournalFormLineState>) {
    setForm((current) => ({
      ...current,
      lines: current.lines.map((line) => (line.localId === lineId ? { ...line, ...patch } : line))
    }));
  }

  function removeLine(lineId: string) {
    setForm((current) => ({
      ...current,
      lines: current.lines.length <= 2 ? current.lines : current.lines.filter((line) => line.localId !== lineId)
    }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLocalError(null);
    setSuccessMessage(null);
    createJournalMutation.reset();

    if (!allowed) {
      setLocalError("User tidak memiliki permission journal.create.");
      return;
    }
    if (accounts.length < 2) {
      setLocalError("Minimal dua akun CoA diperlukan untuk membuat jurnal.");
      return;
    }
    if (postingPeriods.length === 0) {
      setLocalError("Tidak ada periode yang mengizinkan posting.");
      return;
    }
    if (validationErrors.length > 0) {
      setLocalError(validationErrors[0]);
      return;
    }

    const lines: CreateJournalLinePayload[] = form.lines.map((line) => ({
      accountId: line.accountId,
      debit: decimalInputToNumber(line.debit),
      credit: decimalInputToNumber(line.credit),
      description: normalizeInput(line.description)
    }));

    try {
      await createJournalMutation.mutateAsync({
        journalNumber: normalizeInput(form.journalNumber),
        accountingPeriodId: form.accountingPeriodId,
        description: normalizeInput(form.description),
        lines,
        reason: normalizeInput(form.reason)
      });
    } catch {
      return;
    }

    const journalNumber = normalizeInput(form.journalNumber);
    setForm(defaultJournalForm());
    setSuccessMessage(`Draft jurnal ${journalNumber} dibuat.`);
  }

  const disabled = !allowed || createJournalMutation.isPending;
  const errorMessage =
    localError ?? (createJournalMutation.isError ? apiErrorMessage(createJournalMutation.error, "Gagal membuat jurnal.") : null);

  return (
    <form onSubmit={handleSubmit} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-bold text-slate-950">Buat Jurnal Manual</h3>
        <StatusBadge label={allowed ? "journal.create" : "Locked"} tone={allowed ? "success" : "neutral"} />
      </div>
      <div className="mt-4 grid gap-3">
        <div className="grid gap-3 md:grid-cols-2">
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Nomor Jurnal</span>
            <input
              className={inputClass}
              value={form.journalNumber}
              maxLength={64}
              disabled={disabled}
              onChange={(event) => setForm((current) => ({ ...current, journalNumber: event.target.value }))}
              placeholder="JRN-202607-001"
            />
          </label>
          <label className="block">
            <span className="text-xs font-bold uppercase text-slate-600">Periode</span>
            <select
              className={inputClass}
              value={form.accountingPeriodId}
              disabled={disabled || postingPeriods.length === 0}
              onChange={(event) => setForm((current) => ({ ...current, accountingPeriodId: event.target.value }))}
            >
              <option value="">Pilih periode</option>
              {postingPeriods.map((period) => (
                <option key={period.id} value={period.id}>
                  {period.period}
                </option>
              ))}
            </select>
          </label>
        </div>
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Deskripsi</span>
          <input
            className={inputClass}
            value={form.description}
            maxLength={255}
            disabled={disabled}
            onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
            placeholder="Penyesuaian operasional"
          />
        </label>
        <div className="overflow-hidden rounded-lg border border-slate-200">
          <div className="flex items-center justify-between gap-3 border-b border-slate-200 bg-slate-50 px-3 py-2">
            <p className="text-xs font-bold uppercase text-slate-600">Baris Jurnal</p>
            <button
              type="button"
              className="inline-flex h-8 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 text-xs font-bold text-slate-800 hover:bg-teal-50 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500"
              disabled={disabled}
              onClick={() => setForm((current) => ({ ...current, lines: [...current.lines, newJournalLine()] }))}
            >
              <Plus className="size-3.5" aria-hidden="true" />
              Baris
            </button>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-white">
                <tr>
                  <th className="px-3 py-2 text-left text-xs font-bold uppercase text-slate-600">Akun</th>
                  <th className="px-3 py-2 text-right text-xs font-bold uppercase text-slate-600">Debit</th>
                  <th className="px-3 py-2 text-right text-xs font-bold uppercase text-slate-600">Kredit</th>
                  <th className="px-3 py-2 text-left text-xs font-bold uppercase text-slate-600">Memo</th>
                  <th className="px-3 py-2 text-right text-xs font-bold uppercase text-slate-600">Aksi</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {form.lines.map((line) => (
                  <tr key={line.localId}>
                    <td className="min-w-56 px-3 py-2">
                      <select
                        className={inputClass}
                        value={line.accountId}
                        disabled={disabled || accounts.length === 0}
                        onChange={(event) => updateLine(line.localId, { accountId: event.target.value })}
                      >
                        <option value="">Pilih akun</option>
                        {accounts.map((account) => (
                          <option key={account.id} value={account.id}>
                            {account.code} - {account.name}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="min-w-36 px-3 py-2">
                      <input
                        className={cn(inputClass, "text-right")}
                        value={line.debit}
                        inputMode="decimal"
                        disabled={disabled}
                        onChange={(event) => updateLine(line.localId, { debit: event.target.value })}
                        placeholder="0.00"
                      />
                    </td>
                    <td className="min-w-36 px-3 py-2">
                      <input
                        className={cn(inputClass, "text-right")}
                        value={line.credit}
                        inputMode="decimal"
                        disabled={disabled}
                        onChange={(event) => updateLine(line.localId, { credit: event.target.value })}
                        placeholder="0.00"
                      />
                    </td>
                    <td className="min-w-52 px-3 py-2">
                      <input
                        className={inputClass}
                        value={line.description}
                        maxLength={255}
                        disabled={disabled}
                        onChange={(event) => updateLine(line.localId, { description: event.target.value })}
                      />
                    </td>
                    <td className="whitespace-nowrap px-3 py-2 text-right">
                      <button
                        type="button"
                        className="inline-flex size-9 items-center justify-center rounded-lg border border-slate-300 bg-white text-slate-700 hover:bg-teal-50 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400"
                        disabled={disabled || form.lines.length <= 2}
                        onClick={() => removeLine(line.localId)}
                        aria-label="Hapus baris jurnal"
                      >
                        <Trash2 className="size-4" aria-hidden="true" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="grid gap-2 border-t border-slate-200 bg-slate-50 px-3 py-3 text-sm font-bold text-slate-800 sm:grid-cols-3">
            <span>Debit: {amountFormatter.format(draftSummary.totalDebit)}</span>
            <span>Kredit: {amountFormatter.format(draftSummary.totalCredit)}</span>
            <span>
              <StatusBadge label={draftSummary.isBalanced ? "Balance" : "Belum Balance"} tone={draftSummary.isBalanced ? "success" : "warning"} />
            </span>
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
        {successMessage ? <InlineMessage type="success" message={successMessage} /> : null}
        <button type="submit" className={primaryButtonClass} disabled={disabled}>
          {createJournalMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <CirclePlus className="size-4" aria-hidden="true" />}
          Simpan Draft Jurnal
        </button>
      </div>
    </form>
  );
}

function AccountingCommandForms({
  permissions,
  accounts,
  periods
}: Readonly<{ permissions: AccountingPermissions; accounts: Account[]; periods: AccountingPeriod[] }>) {
  return (
    <div className="space-y-4">
      <AccountCommandForm allowed={permissions.canManageAccounts} />
      <PeriodCommandForm allowed={permissions.canManagePeriods} />
      <JournalCommandForm allowed={permissions.canCreateJournals} accounts={accounts} periods={periods} />
    </div>
  );
}

function AccountTable({ accounts }: Readonly<{ accounts: Account[] }>) {
  if (accounts.length === 0) {
    return <EmptyState title="CoA belum tersedia" description="Daftar akun akan muncul setelah CoA dibuat." />;
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <SectionTitle icon={<BookOpenCheck className="size-5 text-slate-700" aria-hidden="true" />} title="Chart of Accounts" />
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Kode</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Nama Akun</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Tipe</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Normal</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {accounts.map((account) => (
              <tr key={account.id} className="hover:bg-teal-50">
                <td className="whitespace-nowrap px-5 py-4 font-mono text-xs font-bold text-slate-800">{account.code}</td>
                <td className="min-w-64 px-5 py-4 font-semibold text-slate-950">{account.name}</td>
                <td className="whitespace-nowrap px-5 py-4 text-slate-700">{accountTypeLabels[account.type]}</td>
                <td className="whitespace-nowrap px-5 py-4">
                  <StatusBadge label={normalBalanceLabels[account.normalBalance]} tone="neutral" />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function PeriodTable({ periods, permissions }: Readonly<{ periods: AccountingPeriod[]; permissions: AccountingPermissions }>) {
  const periodWorkflowMutation = useAccountingPeriodWorkflow();
  const [draft, setDraft] = useState<PeriodWorkflowDraft | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  if (periods.length === 0) {
    return <EmptyState title="Periode belum tersedia" description="Periode akuntansi akan muncul setelah dibuat." />;
  }

  function openWorkflow(period: AccountingPeriod, workflow: AccountingPeriodWorkflow) {
    setLocalError(null);
    setSuccessMessage(null);
    periodWorkflowMutation.reset();
    setDraft({
      period,
      workflow,
      title: workflow === "start-closing-review" ? "Mulai Review Tutup Periode" : "Kunci Periode",
      consequence:
        workflow === "start-closing-review"
          ? "Periode masuk tahap review sebelum dikunci."
          : "Periode terkunci dan posting baru wajib ditolak backend.",
      reason: ""
    });
  }

  async function submitWorkflow() {
    if (!draft) {
      return;
    }

    setLocalError(null);
    const reason = normalizeInput(draft.reason);
    if (!reason) {
      setLocalError("Alasan audit wajib diisi.");
      return;
    }

    try {
      await periodWorkflowMutation.mutateAsync({
        periodId: draft.period.id,
        workflow: draft.workflow,
        payload: { reason }
      });
    } catch {
      return;
    }

    setSuccessMessage(`${draft.title} ${draft.period.period} berhasil.`);
    setDraft(null);
  }

  const errorMessage =
    localError ??
    (periodWorkflowMutation.isError
      ? apiErrorMessage(periodWorkflowMutation.error, "Gagal menjalankan workflow periode.")
      : null);

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <SectionTitle icon={<CalendarDays className="size-5 text-slate-700" aria-hidden="true" />} title="Periode Akuntansi" />
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Periode</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Status</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Posting</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Aksi</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {periods.map((period) => {
              const workflows = allowedAccountingPeriodWorkflows(period, permissions);
              return (
                <tr key={period.id} className="hover:bg-teal-50">
                  <td className="whitespace-nowrap px-5 py-4 font-bold text-slate-950">{period.period}</td>
                  <td className="whitespace-nowrap px-5 py-4">
                    <StatusBadge label={periodStatusLabels[period.status]} tone={periodStatusTones[period.status]} />
                  </td>
                  <td className="whitespace-nowrap px-5 py-4">
                    <StatusBadge label={period.allowsPosting ? "Allowed" : "Blocked"} tone={period.allowsPosting ? "success" : "danger"} />
                  </td>
                  <td className="min-w-80 px-5 py-4">
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        className={secondaryButtonClass}
                        disabled={!workflows.startClosingReview || periodWorkflowMutation.isPending}
                        onClick={() => openWorkflow(period, "start-closing-review")}
                      >
                        <CalendarDays className="size-4" aria-hidden="true" />
                        Review
                      </button>
                      <button
                        type="button"
                        className={dangerButtonClass}
                        disabled={!workflows.lock || periodWorkflowMutation.isPending}
                        onClick={() => openWorkflow(period, "lock")}
                      >
                        <LockKeyhole className="size-4" aria-hidden="true" />
                        Lock
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {draft ? (
        <div className="border-t border-slate-200 bg-slate-50 p-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-sm font-bold text-slate-950">{draft.title}</p>
              <p className="mt-1 text-sm font-semibold text-slate-600">
                {draft.period.period} - {draft.consequence}
              </p>
            </div>
            <button type="button" className={secondaryButtonClass} onClick={() => setDraft(null)}>
              <X className="size-4" aria-hidden="true" />
              Batal
            </button>
          </div>
          <label className="mt-4 block">
            <span className="text-xs font-bold uppercase text-slate-600">Alasan Audit</span>
            <textarea
              className={textareaClass}
              value={draft.reason}
              maxLength={500}
              disabled={periodWorkflowMutation.isPending}
              onChange={(event) => setDraft((current) => (current ? { ...current, reason: event.target.value } : current))}
            />
          </label>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <button type="button" className={draft.workflow === "lock" ? dangerButtonClass : primaryButtonClass} onClick={submitWorkflow} disabled={periodWorkflowMutation.isPending}>
              {periodWorkflowMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Send className="size-4" aria-hidden="true" />}
              Konfirmasi
            </button>
            <StatusBadge label={draft.workflow === "lock" ? "High Risk" : "Controlled"} tone={draft.workflow === "lock" ? "danger" : "warning"} />
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

function JournalDetailDrawer({
  journalId,
  accounts,
  periods,
  enabled,
  onClose
}: Readonly<{
  journalId: string | null;
  accounts: Account[];
  periods: AccountingPeriod[];
  enabled: boolean;
  onClose: () => void;
}>) {
  const journalQuery = useJournal(journalId, enabled);
  const accountById = useMemo(() => new Map(accounts.map((account) => [account.id, account])), [accounts]);
  const periodById = useMemo(() => new Map(periods.map((period) => [period.id, period])), [periods]);

  if (!journalId) {
    return null;
  }

  const journal = journalQuery.data;
  const detailSummary = journal ? summarizeJournalDetailLines(journal.lines) : null;
  const period = journal ? periodById.get(journal.accountingPeriodId) : null;

  return (
    <div className="fixed inset-0 z-40 bg-slate-950/30">
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Detail jurnal"
        className="ml-auto flex h-full w-full max-w-5xl flex-col overflow-hidden bg-white shadow-2xl"
      >
        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-200 px-5 py-4">
          <div>
            <div className="flex items-center gap-2">
              <FileText className="size-5 text-teal-700" aria-hidden="true" />
              <h2 className="text-lg font-bold text-slate-950">Detail Jurnal</h2>
            </div>
            <p className="mt-1 text-sm font-semibold text-slate-600">
              {journal?.journalNumber ?? "Memuat detail jurnal"}
            </p>
          </div>
          <button type="button" className={secondaryButtonClass} onClick={onClose}>
            <X className="size-4" aria-hidden="true" />
            Tutup
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-5">
          {journalQuery.isLoading ? <LoadingSkeleton /> : null}

          {journalQuery.isError ? (
            <div className="space-y-3">
              <ErrorState message={apiErrorMessage(journalQuery.error, "Detail jurnal tidak tersedia.")} />
              <button type="button" className={secondaryButtonClass} onClick={() => void journalQuery.refetch()}>
                <RotateCcw className="size-4" aria-hidden="true" />
                Muat Ulang
              </button>
            </div>
          ) : null}

          {!journalQuery.isLoading && !journalQuery.isError && journal ? (
            <div className="space-y-5">
              <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryCard
                  label="Total Debit"
                  value={amountFormatter.format(journal.totalDebit)}
                  helper={`${detailSummary?.lineCount ?? 0} baris jurnal.`}
                  tone="info"
                />
                <SummaryCard
                  label="Total Kredit"
                  value={amountFormatter.format(journal.totalCredit)}
                  helper="Nilai dari backend detail."
                  tone="neutral"
                />
                <SummaryCard
                  label="Balance"
                  value={journal.balanced ? "Balance" : "Tidak Balance"}
                  helper={
                    detailSummary?.isBalanced
                      ? "Line detail sesuai debit-kredit."
                      : "Line detail perlu review kontrol."
                  }
                  tone={journal.balanced ? "success" : "warning"}
                />
                <SummaryCard
                  label="One-sided Lines"
                  value={detailSummary?.hasOneSidedLines ? "Valid" : "Review"}
                  helper="Tidak ada baris debit dan kredit sekaligus."
                  tone={detailSummary?.hasOneSidedLines ? "success" : "warning"}
                />
              </section>

              <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
                <div className="grid gap-4 p-5 md:grid-cols-2 xl:grid-cols-3">
                  <FieldValue label="Nomor" value={journal.journalNumber} />
                  <FieldValue label="Status" value={<StatusBadge label={journalStatusLabels[journal.status]} tone={journalStatusTones[journal.status]} />} />
                  <FieldValue label="Periode" value={period?.period ?? shortId(journal.accountingPeriodId)} />
                  <FieldValue label="Source Module" value={journal.sourceModule ?? "MANUAL"} />
                  <FieldValue label="Source Document" value={journal.sourceDocumentNumber ?? shortId(journal.sourceRecordId)} />
                  <FieldValue label="Posted" value={formatDateTime(journal.postedAt)} />
                  <FieldValue label="Posted By" value={journal.postedBy ?? "-"} />
                  <FieldValue label="Created" value={formatDateTime(journal.createdAt)} />
                  <FieldValue label="Updated" value={formatDateTime(journal.updatedAt)} />
                </div>
                <div className="border-t border-slate-200 px-5 py-4">
                  <p className="text-xs font-bold uppercase text-slate-600">Deskripsi</p>
                  <p className="mt-1 text-sm font-semibold text-slate-950">{journal.description || "-"}</p>
                </div>
              </section>

              <JournalLineTable journal={journal} accountById={accountById} />
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function FieldValue({ label, value }: Readonly<{ label: string; value: ReactNode }>) {
  return (
    <div>
      <p className="text-xs font-bold uppercase text-slate-600">{label}</p>
      <div className="mt-1 text-sm font-semibold text-slate-950">{value}</div>
    </div>
  );
}

function JournalLineTable({
  journal,
  accountById
}: Readonly<{ journal: Journal; accountById: ReadonlyMap<string, Account> }>) {
  if (journal.lines.length === 0) {
    return <EmptyState title="Baris jurnal belum tersedia" description="Detail jurnal tidak memiliki line item." />;
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <SectionTitle icon={<BookOpenCheck className="size-5 text-slate-700" aria-hidden="true" />} title="Baris Jurnal" />
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Akun</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Normal</th>
              <th className="px-5 py-3 text-right font-bold text-slate-700">Debit</th>
              <th className="px-5 py-3 text-right font-bold text-slate-700">Kredit</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Memo</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {journal.lines.map((line) => {
              const account = accountById.get(line.accountId);
              return (
                <tr key={line.id} className="hover:bg-teal-50">
                  <td className="min-w-72 px-5 py-4 font-semibold text-slate-950">
                    {accountDisplay(line.accountId, accountById)}
                  </td>
                  <td className="whitespace-nowrap px-5 py-4">
                    <StatusBadge
                      label={account ? normalBalanceLabels[account.normalBalance] : "Unknown"}
                      tone="neutral"
                    />
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 text-right font-bold text-slate-950">
                    {amountFormatter.format(line.debit)}
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 text-right font-bold text-slate-950">
                    {amountFormatter.format(line.credit)}
                  </td>
                  <td className="min-w-64 px-5 py-4 text-slate-700">{line.description || "-"}</td>
                </tr>
              );
            })}
          </tbody>
          <tfoot className="border-t border-slate-200 bg-slate-50">
            <tr>
              <td className="px-5 py-3 text-sm font-bold text-slate-950" colSpan={2}>
                Total
              </td>
              <td className="px-5 py-3 text-right text-sm font-bold text-slate-950">{amountFormatter.format(journal.totalDebit)}</td>
              <td className="px-5 py-3 text-right text-sm font-bold text-slate-950">{amountFormatter.format(journal.totalCredit)}</td>
              <td className="px-5 py-3">
                <StatusBadge label={journal.balanced ? "Balance" : "Tidak Balance"} tone={journal.balanced ? "success" : "danger"} />
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  );
}

function JournalTable({
  journals,
  periods,
  permissions,
  isFetching,
  onSelectJournal
}: Readonly<{
  journals: JournalSummary[];
  periods: AccountingPeriod[];
  permissions: AccountingPermissions;
  isFetching: boolean;
  onSelectJournal: (journalId: string) => void;
}>) {
  const postJournalMutation = usePostJournal();
  const [draft, setDraft] = useState<JournalPostDraft | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const periodById = useMemo(() => new Map(periods.map((period) => [period.id, period])), [periods]);

  if (journals.length === 0) {
    return <EmptyState title="Jurnal belum tersedia" description="Jurnal akan muncul setelah draft dibuat atau source document diposting." />;
  }

  function openPost(journal: JournalSummary) {
    setLocalError(null);
    setSuccessMessage(null);
    postJournalMutation.reset();
    setDraft({ journal, reason: "" });
  }

  async function submitPost() {
    if (!draft) {
      return;
    }

    setLocalError(null);
    const reason = normalizeInput(draft.reason);
    if (!reason) {
      setLocalError("Alasan audit wajib diisi.");
      return;
    }

    try {
      await postJournalMutation.mutateAsync({
        journalId: draft.journal.id,
        payload: { reason }
      });
    } catch {
      return;
    }

    setSuccessMessage(`Jurnal ${draft.journal.journalNumber} diposting.`);
    setDraft(null);
  }

  const errorMessage =
    localError ?? (postJournalMutation.isError ? apiErrorMessage(postJournalMutation.error, "Gagal posting jurnal.") : null);

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
        <div className="flex items-center gap-2">
          <FileText className="size-5 text-slate-700" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Jurnal</h2>
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
              <th className="px-5 py-3 text-left font-bold text-slate-700">Nomor</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Deskripsi</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Status</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Source</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Posted</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Command</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {journals.map((journal) => {
              const period = periodById.get(journal.accountingPeriodId);
              const workflow = allowedAccountingJournalWorkflows(
                {
                  status: journal.status,
                  availableActions: journal.status === "DRAFT" ? ["POST"] : [],
                  periodAllowsPosting: period?.allowsPosting
                },
                permissions
              );
              return (
                <tr key={journal.id} className="hover:bg-teal-50">
                  <td className="whitespace-nowrap px-5 py-4 font-mono text-xs font-bold text-slate-800">{journal.journalNumber}</td>
                  <td className="min-w-72 px-5 py-4 font-semibold text-slate-950">{journal.description}</td>
                  <td className="whitespace-nowrap px-5 py-4">
                    <StatusBadge label={journalStatusLabels[journal.status]} tone={journalStatusTones[journal.status]} />
                  </td>
                  <td className="px-5 py-4 text-slate-700">
                    <div className="font-semibold">{journal.sourceModule ?? "MANUAL"}</div>
                    <div className="font-mono text-xs text-slate-500">{journal.sourceDocumentNumber ?? shortId(journal.sourceRecordId)}</div>
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 text-slate-700">{formatDateTime(journal.postedAt)}</td>
                  <td className="whitespace-nowrap px-5 py-4">
                    <div className="flex flex-wrap gap-2">
                      <button type="button" className={secondaryButtonClass} onClick={() => onSelectJournal(journal.id)}>
                        <Eye className="size-4" aria-hidden="true" />
                        Detail
                      </button>
                    <button
                      type="button"
                      className={dangerButtonClass}
                      disabled={!workflow.post || postJournalMutation.isPending}
                      onClick={() => openPost(journal)}
                    >
                      <Send className="size-4" aria-hidden="true" />
                      Posting
                    </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {draft ? (
        <div className="border-t border-slate-200 bg-red-50 p-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-sm font-bold text-red-950">Konfirmasi Posting Jurnal</p>
              <p className="mt-1 text-sm font-semibold text-red-800">
                {draft.journal.journalNumber} akan menjadi posted dan tidak boleh diedit destruktif.
              </p>
            </div>
            <button type="button" className={secondaryButtonClass} onClick={() => setDraft(null)}>
              <X className="size-4" aria-hidden="true" />
              Batal
            </button>
          </div>
          <label className="mt-4 block">
            <span className="text-xs font-bold uppercase text-red-800">Alasan Audit</span>
            <textarea
              className={textareaClass}
              value={draft.reason}
              maxLength={500}
              disabled={postJournalMutation.isPending}
              onChange={(event) => setDraft((current) => (current ? { ...current, reason: event.target.value } : current))}
            />
          </label>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <button type="button" className={dangerButtonClass} onClick={submitPost} disabled={postJournalMutation.isPending}>
              {postJournalMutation.isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Send className="size-4" aria-hidden="true" />}
              Posting
            </button>
            <StatusBadge label="High Risk" tone="danger" />
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

function JournalFilterToolbar({
  status,
  onStatusChange,
  onReset
}: Readonly<{ status: JournalStatusFilter; onStatusChange: (status: JournalStatusFilter) => void; onReset: () => void }>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="grid gap-3 md:grid-cols-[220px_auto]">
        <label className="block">
          <span className="text-xs font-bold uppercase text-slate-600">Status Jurnal</span>
          <select className={inputClass} value={status} onChange={(event) => onStatusChange(event.target.value as JournalStatusFilter)}>
            <option value="ALL">Semua</option>
            {journalStatusValues.map((value) => (
              <option key={value} value={value}>
                {journalStatusLabels[value]}
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

export function AccountingWorkspace() {
  const [journalStatus, setJournalStatus] = useState<JournalStatusFilter>("ALL");
  const [selectedJournalId, setSelectedJournalId] = useState<string | null>(null);
  const currentUserQuery = useCurrentUser();
  const queryEnabled = currentUserQuery.isSuccess;
  const permissions = useMemo(
    () => resolveFinancialCommandPermissions(currentUserQuery.data?.authorities ?? []).accounting,
    [currentUserQuery.data?.authorities]
  );

  const accountsQuery = useAccounts({ page: 0, size: 100 }, queryEnabled);
  const periodsQuery = useAccountingPeriods({ page: 0, size: 25 }, queryEnabled);
  const journalsQuery = useJournals(
    {
      status: journalStatus === "ALL" ? undefined : journalStatus,
      page: 0,
      size: 25
    },
    queryEnabled
  );

  const accounts = useMemo(() => accountsQuery.data?.items ?? [], [accountsQuery.data?.items]);
  const periods = useMemo(() => periodsQuery.data?.items ?? [], [periodsQuery.data?.items]);
  const journals = useMemo(() => journalsQuery.data?.items ?? [], [journalsQuery.data?.items]);
  const summary = useMemo(() => summarizeAccountingWorkspace({ accounts, periods, journals }), [accounts, periods, journals]);

  function refetchAll() {
    void accountsQuery.refetch();
    void periodsQuery.refetch();
    void journalsQuery.refetch();
  }

  if (currentUserQuery.isLoading) {
    return <LoadingSkeleton />;
  }

  if (currentUserQuery.isError) {
    return <ErrorState message={apiErrorMessage(currentUserQuery.error, "Sesi atau otorisasi accounting tidak tersedia.")} />;
  }

  const isInitialLoading = accountsQuery.isLoading || periodsQuery.isLoading || journalsQuery.isLoading;
  const hasError = accountsQuery.isError || periodsQuery.isError || journalsQuery.isError;

  return (
    <main className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <PageHeader
          title="Akuntansi"
          description="Workspace kontrol untuk CoA, periode akuntansi, dan jurnal yang menjadi sumber buku besar."
        />
        <div className="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm shadow-sm">
          <div className="flex items-center gap-2 font-bold text-slate-950">
            <ShieldCheck className="size-4 text-slate-600" aria-hidden="true" />
            Ledger Control
          </div>
          <p className="mt-1 font-semibold text-slate-600">{currentUserQuery.data?.username}</p>
        </div>
      </div>

      {isInitialLoading ? <LoadingSkeleton /> : null}

      {hasError ? (
        <div className="space-y-3">
          <ErrorState
            message={apiErrorMessage(
              accountsQuery.error ?? periodsQuery.error ?? journalsQuery.error,
              "Data accounting tidak tersedia."
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
            <SummaryCard label="Akun Aset/Beban" value={String(summary.assetExpenseAccounts)} helper="Normal debit di halaman ini." tone="info" />
            <SummaryCard label="Akun Kredit" value={String(summary.revenueLiabilityEquityAccounts)} helper="Pendapatan, liabilitas, dan ekuitas." tone="neutral" />
            <SummaryCard label="Periode Posting" value={String(summary.openPostingPeriods)} helper={`${summary.lockedPeriods} periode terkunci di halaman ini.`} tone="success" />
            <SummaryCard label="Jurnal Draft/Posted" value={`${summary.draftJournals}/${summary.postedJournals}`} helper="Draft dibanding posted di filter aktif." tone="warning" />
          </section>

          <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_430px]">
            <div className="space-y-4">
              <AccountTable accounts={accounts} />
              <PeriodTable periods={periods} permissions={permissions} />
              <JournalFilterToolbar status={journalStatus} onStatusChange={setJournalStatus} onReset={() => setJournalStatus("ALL")} />
              <JournalTable
                journals={journals}
                periods={periods}
                permissions={permissions}
                isFetching={journalsQuery.isFetching}
                onSelectJournal={setSelectedJournalId}
              />
            </div>
            <div className="space-y-4">
              <AccountingCommandPanel permissions={permissions} />
              <AccountingCommandForms permissions={permissions} accounts={accounts} periods={periods} />
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
                <div className="flex items-center gap-2 text-sm font-bold text-amber-900">
                  <LockKeyhole className="size-4" aria-hidden="true" />
                  Guardrail
                </div>
                <p className="mt-2 text-sm leading-6 text-amber-900">
                  Posting dan tutup periode tetap wajib melewati backend permission, period lock, audit reason, dan validasi debit-kredit.
                </p>
              </div>
            </div>
          </section>

          <JournalDetailDrawer
            journalId={selectedJournalId}
            accounts={accounts}
            periods={periods}
            enabled={queryEnabled}
            onClose={() => setSelectedJournalId(null)}
          />
        </>
      ) : null}
    </main>
  );
}
