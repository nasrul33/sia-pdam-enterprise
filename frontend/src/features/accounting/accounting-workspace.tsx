"use client";

import {
  BookOpenCheck,
  CalendarDays,
  FileText,
  Loader2,
  LockKeyhole,
  RotateCcw,
  ShieldCheck
} from "lucide-react";
import { useMemo, useState } from "react";
import { PageHeader } from "@/components/common/page-header";
import { EmptyState } from "@/components/state/empty-state";
import { ErrorState } from "@/components/state/error-state";
import { LoadingSkeleton } from "@/components/state/loading-skeleton";
import { StatusBadge } from "@/components/status/status-badge";
import { useCurrentUser } from "@/features/auth/use-current-user";
import { resolveFinancialCommandPermissions } from "@/features/security/financial-command-permissions";
import { apiErrorMessage } from "@/lib/api/client";
import {
  allowedAccountingJournalWorkflows,
  allowedAccountingPeriodWorkflows,
  summarizeAccountingWorkspace
} from "./accounting-workspace-model";
import type { Account, AccountingPeriod, JournalStatus, JournalSummary } from "./accounting-schema";
import { journalStatusValues } from "./accounting-schema";
import { useAccountingPeriods, useAccounts, useJournals } from "./use-accounting";

type JournalStatusFilter = JournalStatus | "ALL";

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

function AccountingCommandPanel({
  permissions
}: Readonly<{ permissions: ReturnType<typeof resolveFinancialCommandPermissions>["accounting"] }>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 p-4">
        <div className="flex items-center gap-2">
          <BookOpenCheck className="size-5 text-sky-700" aria-hidden="true" />
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

function AccountTable({ accounts }: Readonly<{ accounts: Account[] }>) {
  if (accounts.length === 0) {
    return <EmptyState title="CoA belum tersedia" description="Daftar akun akan muncul setelah CoA dibuat." />;
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex items-center gap-2 border-b border-slate-200 px-5 py-4">
        <BookOpenCheck className="size-5 text-slate-700" aria-hidden="true" />
        <h2 className="text-base font-bold text-slate-950">Chart of Accounts</h2>
      </div>
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
              <tr key={account.id} className="hover:bg-slate-50">
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

function PeriodTable({
  periods,
  permissions
}: Readonly<{ periods: AccountingPeriod[]; permissions: ReturnType<typeof resolveFinancialCommandPermissions>["accounting"] }>) {
  if (periods.length === 0) {
    return <EmptyState title="Periode belum tersedia" description="Periode akuntansi akan muncul setelah dibuat." />;
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex items-center gap-2 border-b border-slate-200 px-5 py-4">
        <CalendarDays className="size-5 text-slate-700" aria-hidden="true" />
        <h2 className="text-base font-bold text-slate-950">Periode Akuntansi</h2>
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Periode</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Status</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Posting</th>
              <th className="px-5 py-3 text-left font-bold text-slate-700">Command</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {periods.map((period) => {
              const workflows = allowedAccountingPeriodWorkflows(period, permissions);
              return (
                <tr key={period.id} className="hover:bg-slate-50">
                  <td className="whitespace-nowrap px-5 py-4 font-bold text-slate-950">{period.period}</td>
                  <td className="whitespace-nowrap px-5 py-4">
                    <StatusBadge label={periodStatusLabels[period.status]} tone={periodStatusTones[period.status]} />
                  </td>
                  <td className="whitespace-nowrap px-5 py-4">
                    <StatusBadge label={period.allowsPosting ? "Allowed" : "Blocked"} tone={period.allowsPosting ? "success" : "danger"} />
                  </td>
                  <td className="min-w-72 px-5 py-4">
                    <div className="flex flex-wrap gap-2">
                      <StatusBadge
                        label={workflows.startClosingReview ? "Review tersedia" : "Review terkunci"}
                        tone={workflows.startClosingReview ? "warning" : "neutral"}
                      />
                      <StatusBadge label={workflows.lock ? "Lock tersedia" : "Lock terkunci"} tone={workflows.lock ? "danger" : "neutral"} />
                    </div>
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

function JournalTable({
  journals,
  permissions,
  isFetching
}: Readonly<{
  journals: JournalSummary[];
  permissions: ReturnType<typeof resolveFinancialCommandPermissions>["accounting"];
  isFetching: boolean;
}>) {
  if (journals.length === 0) {
    return <EmptyState title="Jurnal belum tersedia" description="Jurnal akan muncul setelah draft dibuat atau source document diposting." />;
  }

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
              const workflow = allowedAccountingJournalWorkflows(
                { status: journal.status, availableActions: journal.status === "DRAFT" ? ["POST"] : [] },
                permissions
              );
              return (
                <tr key={journal.id} className="hover:bg-slate-50">
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
                    <StatusBadge label={workflow.post ? "Posting tersedia" : "Posting terkunci"} tone={workflow.post ? "danger" : "neutral"} />
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
          <select
            className="mt-1 h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-slate-950 outline-none transition focus:border-sky-500 focus:ring-2 focus:ring-sky-100"
            value={status}
            onChange={(event) => onStatusChange(event.target.value as JournalStatusFilter)}
          >
            <option value="ALL">Semua</option>
            {journalStatusValues.map((value) => (
              <option key={value} value={value}>
                {journalStatusLabels[value]}
              </option>
            ))}
          </select>
        </label>
        <div className="flex items-end">
          <button
            type="button"
            onClick={onReset}
            className="inline-flex h-10 items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 text-sm font-bold text-slate-800 hover:bg-slate-50"
          >
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
  const currentUserQuery = useCurrentUser();
  const queryEnabled = currentUserQuery.isSuccess;
  const permissions = useMemo(
    () => resolveFinancialCommandPermissions(currentUserQuery.data?.authorities ?? []).accounting,
    [currentUserQuery.data?.authorities]
  );

  const accountsQuery = useAccounts({ page: 0, size: 10 }, queryEnabled);
  const periodsQuery = useAccountingPeriods({ page: 0, size: 10 }, queryEnabled);
  const journalsQuery = useJournals(
    {
      status: journalStatus === "ALL" ? undefined : journalStatus,
      page: 0,
      size: 10
    },
    queryEnabled
  );

  const accounts = useMemo(() => accountsQuery.data?.items ?? [], [accountsQuery.data?.items]);
  const periods = useMemo(() => periodsQuery.data?.items ?? [], [periodsQuery.data?.items]);
  const journals = useMemo(() => journalsQuery.data?.items ?? [], [journalsQuery.data?.items]);
  const summary = useMemo(
    () => summarizeAccountingWorkspace({ accounts, periods, journals }),
    [accounts, periods, journals]
  );

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
          <button
            type="button"
            onClick={refetchAll}
            className="inline-flex h-10 items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 text-sm font-bold text-slate-800 hover:bg-slate-50"
          >
            <RotateCcw className="size-4" aria-hidden="true" />
            Muat Ulang
          </button>
        </div>
      ) : null}

      {!isInitialLoading && !hasError ? (
        <>
          <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <SummaryCard
              label="Akun Aset/Beban"
              value={String(summary.assetExpenseAccounts)}
              helper="Normal debit di halaman ini."
              tone="info"
            />
            <SummaryCard
              label="Akun Kredit"
              value={String(summary.revenueLiabilityEquityAccounts)}
              helper="Pendapatan, liabilitas, dan ekuitas."
              tone="neutral"
            />
            <SummaryCard
              label="Periode Posting"
              value={String(summary.openPostingPeriods)}
              helper={`${summary.lockedPeriods} periode terkunci di halaman ini.`}
              tone="success"
            />
            <SummaryCard
              label="Jurnal Draft/Posted"
              value={`${summary.draftJournals}/${summary.postedJournals}`}
              helper="Draft dibanding posted di filter aktif."
              tone="warning"
            />
          </section>

          <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
            <div className="space-y-4">
              <AccountTable accounts={accounts} />
              <PeriodTable periods={periods} permissions={permissions} />
              <JournalFilterToolbar
                status={journalStatus}
                onStatusChange={setJournalStatus}
                onReset={() => setJournalStatus("ALL")}
              />
              <JournalTable journals={journals} permissions={permissions} isFetching={journalsQuery.isFetching} />
            </div>
            <div className="space-y-4">
              <AccountingCommandPanel permissions={permissions} />
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
                <div className="flex items-center gap-2 text-sm font-bold text-amber-900">
                  <LockKeyhole className="size-4" aria-hidden="true" />
                  Guardrail
                </div>
                <p className="mt-2 text-sm leading-6 text-amber-900">
                  Posting dan tutup periode tetap wajib melewati backend permission, period lock, audit reason, dan
                  validasi debit-kredit.
                </p>
              </div>
            </div>
          </section>
        </>
      ) : null}
    </main>
  );
}
