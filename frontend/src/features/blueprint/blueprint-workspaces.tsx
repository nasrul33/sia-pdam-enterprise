"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  FileSpreadsheet,
  RefreshCw,
  Send,
  ShieldCheck
} from "lucide-react";
import { PageHeader } from "@/components/common/page-header";
import { EmptyState } from "@/components/state/empty-state";
import { ErrorState } from "@/components/state/error-state";
import { LoadingSkeleton } from "@/components/state/loading-skeleton";
import { StatusBadge } from "@/components/status/status-badge";
import { apiErrorMessage } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import {
  getFinancialStatements,
  getTaxRecap,
  listBlueprintRecords,
  postBlueprintCommand,
  verifyAuditChain,
  type FinancialStatementLine
} from "./blueprint-api";

type QuickCommand = {
  label: string;
  endpoint: string;
  payload: Record<string, unknown>;
  description: string;
};

type BlueprintWorkspaceConfig = {
  title: string;
  description: string;
  eyebrow: string;
  endpoint: string;
  columns: Array<{ key: string; label: string; type?: "money" | "date" | "status" | "id" }>;
  primaryMetricLabel: string;
  command?: QuickCommand;
};

const moneyFormatter = new Intl.NumberFormat("id-ID", {
  style: "currency",
  currency: "IDR",
  maximumFractionDigits: 0
});

function formatValue(value: unknown, type?: "money" | "date" | "status" | "id") {
  if (value === null || value === undefined || value === "") {
    return "-";
  }
  if (type === "money" && typeof value === "number") {
    return moneyFormatter.format(value);
  }
  if (type === "date" && typeof value === "string") {
    return new Date(value).toLocaleString("id-ID");
  }
  if (type === "id" && typeof value === "string") {
    return value.slice(0, 8);
  }
  return String(value);
}

function statusTone(value: unknown): "success" | "warning" | "danger" | "info" | "neutral" {
  const normalized = String(value ?? "").toUpperCase();
  if (["ACTIVE", "OPEN", "MATCHED", "PAID", "APPROVED", "VALID", "POSTED"].includes(normalized)) {
    return "success";
  }
  if (["DRAFT", "SUBMITTED", "SURVEYED", "UNMATCHED", "IN_PROGRESS"].includes(normalized)) {
    return "warning";
  }
  if (["REJECTED", "VOID", "CANCELLED", "DEFAULTED", "DISPOSED"].includes(normalized)) {
    return "danger";
  }
  return "info";
}

function SummaryCard({ label, value, helper }: Readonly<{ label: string; value: string; helper: string }>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <p className="text-xs font-black uppercase text-teal-700">{label}</p>
      <p className="mt-3 text-2xl font-black text-slate-950">{value}</p>
      <p className="mt-2 text-sm font-medium leading-6 text-slate-600">{helper}</p>
    </div>
  );
}

function CommandPanel({ command, queryKey }: Readonly<{ command?: QuickCommand; queryKey: readonly unknown[] }>) {
  const queryClient = useQueryClient();
  const [message, setMessage] = useState<string | null>(null);
  const mutation = useMutation({
    mutationFn: async () => {
      if (!command) {
        return null;
      }
      return postBlueprintCommand(command.endpoint, command.payload);
    },
    onSuccess: async () => {
      setMessage("Command berhasil diproses backend.");
      await queryClient.invalidateQueries({ queryKey });
    },
    onError: (error) => setMessage(apiErrorMessage(error, "Command gagal diproses."))
  });

  if (!command) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2">
          <ShieldCheck className="size-5 text-teal-700" aria-hidden="true" />
          <h2 className="text-base font-black text-slate-950">Kontrol Backend</h2>
        </div>
        <p className="mt-3 text-sm font-medium leading-6 text-slate-600">
          Modul ini sudah memiliki kontrak backend, RBAC, audit trail, dan empty/error state di frontend.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-black uppercase text-teal-700">Quick Command</p>
          <h2 className="mt-1 text-base font-black text-slate-950">{command.label}</h2>
          <p className="mt-2 text-sm font-medium leading-6 text-slate-600">{command.description}</p>
        </div>
        <button
          type="button"
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending}
          className="inline-flex items-center gap-2 rounded-lg bg-slate-950 px-3 py-2 text-sm font-black text-white shadow-sm transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
        >
          {mutation.isPending ? <RefreshCw className="size-4 animate-spin" aria-hidden="true" /> : <Send className="size-4" aria-hidden="true" />}
          Run
        </button>
      </div>
      {message ? <p className="mt-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-bold text-slate-700">{message}</p> : null}
    </div>
  );
}

export function BlueprintListWorkspace({ config }: Readonly<{ config: BlueprintWorkspaceConfig }>) {
  const queryKey = ["blueprint-list", config.endpoint] as const;
  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey,
    queryFn: () => listBlueprintRecords(config.endpoint)
  });

  const rows = useMemo(() => data?.content ?? [], [data?.content]);
  const statusCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const row of rows) {
      const status = String(row.status ?? "UNKNOWN");
      counts.set(status, (counts.get(status) ?? 0) + 1);
    }
    return [...counts.entries()];
  }, [rows]);

  if (isLoading) {
    return <LoadingSkeleton />;
  }

  if (isError) {
    return <ErrorState message={apiErrorMessage(error, "Data modul blueprint tidak tersedia.")} />;
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <PageHeader title={config.title} description={config.description} eyebrow={config.eyebrow} />
        <button
          type="button"
          onClick={() => void refetch()}
          className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm font-black text-slate-800 shadow-sm transition hover:border-teal-200 hover:text-teal-800"
        >
          <RefreshCw className={cn("size-4", isFetching ? "animate-spin" : "")} aria-hidden="true" />
          Refresh
        </button>
      </div>

      <section className="grid gap-4 md:grid-cols-3">
        <SummaryCard label={config.primaryMetricLabel} value={String(data?.totalElements ?? rows.length)} helper="Total record dari backend dengan pagination." />
        <SummaryCard label="Status aktif" value={String(statusCounts[0]?.[1] ?? 0)} helper={statusCounts[0]?.[0] ?? "Belum ada status dominan."} />
        <SummaryCard label="RBAC" value="Guarded" helper="Akses tetap mengikuti authority backend." />
      </section>

      <section className="grid gap-4 xl:grid-cols-[1fr_360px]">
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
            <div>
              <p className="text-xs font-black uppercase text-teal-700">Register</p>
              <h2 className="mt-1 text-base font-black text-slate-950">{config.title}</h2>
            </div>
            <StatusBadge label={`${rows.length} rows`} tone="neutral" />
          </div>
          {rows.length === 0 ? (
            <div className="p-5">
              <EmptyState title="Belum ada data" description="Data akan tampil setelah workflow backend mulai dipakai." />
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-sm">
                <thead className="bg-slate-50">
                  <tr>
                    {config.columns.map((column) => (
                      <th key={column.key} className="px-5 py-3 text-left font-black text-slate-700">
                        {column.label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {rows.map((row, rowIndex) => (
                    <tr key={String(row.id ?? rowIndex)} className="hover:bg-teal-50/40">
                      {config.columns.map((column) => (
                        <td key={`${String(row.id ?? rowIndex)}-${column.key}`} className="px-5 py-4 font-semibold text-slate-700">
                          {column.type === "status" ? (
                            <StatusBadge label={formatValue(row[column.key], column.type)} tone={statusTone(row[column.key])} />
                          ) : (
                            formatValue(row[column.key], column.type)
                          )}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        <CommandPanel command={config.command} queryKey={queryKey} />
      </section>
    </div>
  );
}

const commonReason = "UAT blueprint parity check";

export const blueprintWorkspaces: Record<string, BlueprintWorkspaceConfig> = {
  payables: {
    title: "AP Payables & Suppliers",
    description: "Register utang usaha, supplier, dan settlement AP yang sebelumnya belum punya frontend.",
    eyebrow: "AccountingAgent",
    endpoint: "/api/accounting/payables?page=0&size=25",
    primaryMetricLabel: "Payables",
    columns: [
      { key: "payableNumber", label: "Nomor" },
      { key: "period", label: "Periode" },
      { key: "status", label: "Status", type: "status" },
      { key: "amount", label: "Nilai", type: "money" },
      { key: "recordedAt", label: "Dicatat", type: "date" }
    ]
  },
  assets: {
    title: "Fixed Assets",
    description: "Aset tetap, biaya perolehan, akumulasi penyusutan, dan disposal journal trace.",
    eyebrow: "AccountingAgent",
    endpoint: "/api/accounting/fixed-assets?page=0&size=25",
    primaryMetricLabel: "Aset",
    columns: [
      { key: "assetCode", label: "Kode" },
      { key: "name", label: "Nama" },
      { key: "status", label: "Status", type: "status" },
      { key: "acquisitionCost", label: "Harga Perolehan", type: "money" },
      { key: "netBookValue", label: "Nilai Buku", type: "money" }
    ]
  },
  "bank-mutations": {
    title: "Bank Mutations",
    description: "Import mutasi bank persisten dan rekonsiliasi harian ke payment register.",
    eyebrow: "PaymentAgent",
    endpoint: "/api/bank-mutations?page=0&size=25",
    primaryMetricLabel: "Mutasi",
    columns: [
      { key: "externalReference", label: "Referensi" },
      { key: "bankAccountReference", label: "Rekening" },
      { key: "status", label: "Status", type: "status" },
      { key: "amount", label: "Nilai", type: "money" },
      { key: "transactedAt", label: "Tanggal", type: "date" }
    ],
    command: {
      label: "Reconcile Today",
      endpoint: "/api/bank-mutations/reconcile-daily",
      description: "Menjalankan rekonsiliasi mutasi hari ini jika ada mutasi UNMATCHED.",
      payload: {
        date: new Date().toISOString().slice(0, 10),
        bankAccountReference: "OPERASIONAL",
        sourceFilename: "manual-daily-run.csv"
      }
    }
  },
  installments: {
    title: "Installment Plans",
    description: "Lifecycle cicilan piutang untuk invoice issued atau partial paid.",
    eyebrow: "ReceivableAgent",
    endpoint: "/api/receivables/installment-plans?page=0&size=25",
    primaryMetricLabel: "Plan",
    columns: [
      { key: "planNumber", label: "Nomor Plan" },
      { key: "status", label: "Status", type: "status" },
      { key: "totalAmount", label: "Total", type: "money" },
      { key: "installmentCount", label: "Tenor" },
      { key: "createdAt", label: "Dibuat", type: "date" }
    ]
  },
  settings: {
    title: "Application Settings",
    description: "Parameter admin seperti identitas PDAM, tarif pajak estimasi, dan konfigurasi operasional.",
    eyebrow: "SecurityAgent",
    endpoint: "/api/admin/settings?page=0&size=50",
    primaryMetricLabel: "Setting",
    columns: [
      { key: "settingKey", label: "Key" },
      { key: "settingValue", label: "Value" },
      { key: "valueType", label: "Type", type: "status" },
      { key: "updatedBy", label: "Updated By" },
      { key: "updatedAt", label: "Updated", type: "date" }
    ],
    command: {
      label: "Seed PDAM Name",
      endpoint: "/api/admin/settings",
      description: "Membuat/menyegarkan setting nama PDAM untuk uji kontrak settings.",
      payload: {
        settingKey: "pdam.name",
        settingValue: "PERUMDAM Enterprise",
        valueType: "STRING",
        description: "Nama instansi untuk header dokumen",
        reason: commonReason
      }
    }
  },
  "connection-requests": {
    title: "Connection Requests",
    description: "Workflow permohonan sambungan baru: submit, survey, approve/reject, activate.",
    eyebrow: "CustomerAgent",
    endpoint: "/api/connection-requests?page=0&size=25",
    primaryMetricLabel: "Request",
    columns: [
      { key: "requestNumber", label: "Nomor" },
      { key: "applicantName", label: "Pemohon" },
      { key: "areaCode", label: "Area" },
      { key: "status", label: "Status", type: "status" },
      { key: "requestedAt", label: "Masuk", type: "date" }
    ]
  }
};

function StatementSection({ title, lines }: Readonly<{ title: string; lines: FinancialStatementLine[] }>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 px-5 py-4">
        <h2 className="text-base font-black text-slate-950">{title}</h2>
      </div>
      {lines.length === 0 ? (
        <div className="p-5">
          <EmptyState title="Belum ada saldo" description="Tidak ada akun posted pada kelompok ini." />
        </div>
      ) : (
        <div className="divide-y divide-slate-100">
          {lines.map((line) => (
            <div key={line.accountId} className="flex items-center justify-between gap-4 px-5 py-3 text-sm">
              <div>
                <p className="font-black text-slate-950">{line.accountCode}</p>
                <p className="font-medium text-slate-600">{line.accountName}</p>
              </div>
              <p className="font-black text-slate-950">{moneyFormatter.format(line.amount)}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export function FinancialStatementsWorkspace() {
  const today = new Date();
  const fromDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-01`;
  const toDate = today.toISOString().slice(0, 10);
  const statementsQuery = useQuery({
    queryKey: ["financial-statements", fromDate, toDate],
    queryFn: () => getFinancialStatements(fromDate, toDate)
  });
  const taxQuery = useQuery({
    queryKey: ["tax-recap", fromDate, toDate],
    queryFn: () => getTaxRecap(fromDate, toDate)
  });
  const auditQuery = useQuery({
    queryKey: ["audit-chain-verify"],
    queryFn: verifyAuditChain
  });

  if (statementsQuery.isLoading) {
    return <LoadingSkeleton />;
  }
  if (statementsQuery.isError) {
    return <ErrorState message={apiErrorMessage(statementsQuery.error, "Laporan keuangan tidak tersedia.")} />;
  }
  const statements = statementsQuery.data;
  if (!statements) {
    return <EmptyState title="Laporan kosong" description="Belum ada posted ledger untuk periode berjalan." />;
  }

  return (
    <div className="space-y-5">
      <PageHeader
        title="Financial Statements"
        eyebrow="ReportingAgent"
        description="Laporan posisi, laba rugi, tax recap, dan audit-chain verification berbasis posted ledger."
      />
      <section className="grid gap-4 md:grid-cols-4">
        <SummaryCard label="Aset" value={moneyFormatter.format(statements.totalAssets)} helper="Total aset dari ledger posted." />
        <SummaryCard label="Liabilitas" value={moneyFormatter.format(statements.totalLiabilities)} helper="Total kewajiban posted." />
        <SummaryCard label="Pendapatan" value={moneyFormatter.format(statements.totalRevenue)} helper="Revenue posted periode berjalan." />
        <SummaryCard label="Laba/Rugi" value={moneyFormatter.format(statements.netIncome)} helper="Pendapatan dikurangi beban." />
      </section>
      <section className="grid gap-4 xl:grid-cols-2">
        <StatementSection title="Aset" lines={statements.assets} />
        <StatementSection title="Liabilitas & Ekuitas" lines={[...statements.liabilities, ...statements.equity]} />
        <StatementSection title="Pendapatan" lines={statements.revenue} />
        <StatementSection title="Beban" lines={statements.expenses} />
      </section>
      <section className="grid gap-4 xl:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center gap-2">
            <FileSpreadsheet className="size-5 text-teal-700" aria-hidden="true" />
            <h2 className="text-base font-black text-slate-950">Tax Recap</h2>
          </div>
          {taxQuery.data ? (
            <div className="mt-4 grid gap-3 sm:grid-cols-3">
              <SummaryCard label="Taxable" value={moneyFormatter.format(taxQuery.data.taxableIncome)} helper="Estimasi laba kena pajak." />
              <SummaryCard label="Rate" value={`${(taxQuery.data.incomeTaxRate * 100).toFixed(2)}%`} helper="Parameter estimasi." />
              <SummaryCard label="Tax" value={moneyFormatter.format(taxQuery.data.estimatedIncomeTax)} helper="Estimasi PPh." />
            </div>
          ) : (
            <p className="mt-3 text-sm font-semibold text-slate-600">Tax recap belum tersedia.</p>
          )}
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center gap-2">
            <ShieldCheck className="size-5 text-emerald-700" aria-hidden="true" />
            <h2 className="text-base font-black text-slate-950">Audit Chain</h2>
          </div>
          <div className="mt-4 flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
            <div>
              <p className="font-black text-slate-950">{auditQuery.data?.status ?? "UNKNOWN"}</p>
              <p className="text-sm font-medium text-slate-600">{auditQuery.data?.entriesChecked ?? 0} entries checked</p>
            </div>
            <StatusBadge label={auditQuery.data?.valid ? "VALID" : "CHECK"} tone={auditQuery.data?.valid ? "success" : "warning"} />
          </div>
        </div>
      </section>
    </div>
  );
}
