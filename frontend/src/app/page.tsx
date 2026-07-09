"use client";

import { AlertTriangle, ArrowUpRight, CheckCircle2, Clock3, Database, ShieldCheck } from "lucide-react";
import { PageHeader } from "@/components/common/page-header";
import { EmptyState } from "@/components/state/empty-state";
import { ErrorState } from "@/components/state/error-state";
import { LoadingSkeleton } from "@/components/state/loading-skeleton";
import { StatusBadge } from "@/components/status/status-badge";
import { useDashboardOverview } from "@/features/dashboard/use-dashboard-overview";
import type { DashboardMetric, ModuleHealth, QualityGate, RiskItem } from "@/features/dashboard/dashboard-schema";
import { FinancialCommandAccessPanel } from "@/features/security/financial-command-access-panel";
import { cn } from "@/lib/utils";

const moduleStatusLabel: Record<ModuleHealth["status"], string> = {
  ready: "Ready",
  in_progress: "In Progress",
  blocked: "Blocked",
  planned: "Planned"
};

const moduleStatusTone: Record<ModuleHealth["status"], "success" | "warning" | "danger" | "info"> = {
  ready: "success",
  in_progress: "warning",
  blocked: "danger",
  planned: "info"
};

const gateTone: Record<QualityGate["status"], "success" | "warning" | "danger"> = {
  configured: "success",
  pending: "warning",
  blocked: "danger"
};

const riskTone: Record<RiskItem["severity"], "danger" | "warning" | "info" | "neutral"> = {
  critical: "danger",
  high: "warning",
  medium: "info",
  low: "neutral"
};

const metricAccent: Record<DashboardMetric["tone"], string> = {
  success: "bg-emerald-500",
  warning: "bg-amber-500",
  danger: "bg-red-500",
  info: "bg-teal-500",
  neutral: "bg-slate-500"
};

const metricPanel: Record<DashboardMetric["tone"], string> = {
  success: "border-emerald-200/80",
  warning: "border-amber-200/80",
  danger: "border-red-200/80",
  info: "border-teal-200/80",
  neutral: "border-slate-200"
};

function MetricCard({ metric }: Readonly<{ metric: DashboardMetric }>) {
  return (
    <div className={cn("relative overflow-hidden rounded-lg border bg-white p-5 shadow-sm", metricPanel[metric.tone])}>
      <div className={cn("absolute inset-x-0 top-0 h-1", metricAccent[metric.tone])} />
      <div className="flex items-start justify-between gap-3">
        <p className="max-w-40 text-sm font-black text-slate-700">{metric.label}</p>
        <span className="flex size-9 shrink-0 items-center justify-center rounded-lg border border-slate-200 bg-slate-50 text-slate-700">
          <ArrowUpRight className="size-4" aria-hidden="true" />
        </span>
      </div>
      <p className="mt-4 text-3xl font-black text-slate-950">{metric.value}</p>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <StatusBadge label={metric.tone.toUpperCase()} tone={metric.tone} />
      </div>
      <p className="mt-3 text-sm font-medium leading-6 text-slate-600">{metric.helper}</p>
    </div>
  );
}

function ModuleTable({ modules }: Readonly<{ modules: ModuleHealth[] }>) {
  if (modules.length === 0) {
    return <EmptyState title="Belum ada modul" description="Backend belum mengirim daftar modul operasional." />;
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 bg-white px-5 py-4">
        <div>
          <p className="text-xs font-black uppercase text-teal-700">Operational Readiness</p>
          <h2 className="mt-1 text-base font-black text-slate-950">Kesiapan Modul</h2>
        </div>
        <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-black text-slate-700">
          {modules.length} modul
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50/80">
            <tr>
              <th className="px-5 py-3 text-left font-black text-slate-700">Modul</th>
              <th className="px-5 py-3 text-left font-black text-slate-700">Owner</th>
              <th className="px-5 py-3 text-left font-black text-slate-700">Status</th>
              <th className="px-5 py-3 text-left font-black text-slate-700">Guardrail</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {modules.map((module) => (
              <tr key={module.module} className="hover:bg-teal-50/40">
                <td className="whitespace-nowrap px-5 py-4 font-black text-slate-950">{module.module}</td>
                <td className="whitespace-nowrap px-5 py-4 font-semibold text-slate-700">{module.owner}</td>
                <td className="whitespace-nowrap px-5 py-4">
                  <StatusBadge label={moduleStatusLabel[module.status]} tone={moduleStatusTone[module.status]} />
                </td>
                <td className="min-w-80 px-5 py-4 font-medium leading-6 text-slate-700">{module.guardrail}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function QualityGateList({ gates }: Readonly<{ gates: QualityGate[] }>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <CheckCircle2 className="size-5 text-emerald-700" aria-hidden="true" />
          <h2 className="text-base font-black text-slate-950">Quality Gates</h2>
        </div>
        <span className="rounded-lg border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-xs font-black text-emerald-800">
          CI-ready
        </span>
      </div>
      <div className="mt-4 space-y-3">
        {gates.length === 0 ? (
          <EmptyState title="Gate belum dikonfigurasi" description="Tambahkan gate lint, typecheck, test, build, dan migration." />
        ) : (
          gates.map((gate) => (
            <div key={gate.name} className="rounded-lg border border-slate-200 bg-slate-50/70 p-3">
              <div className="flex items-center justify-between gap-3">
                <p className="font-black text-slate-950">{gate.name}</p>
                <StatusBadge label={gate.status} tone={gateTone[gate.status]} />
              </div>
              <code className="mt-2 block rounded bg-slate-950 px-3 py-2 text-xs font-semibold text-white">
                {gate.command}
              </code>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function RiskQueue({ risks }: Readonly<{ risks: RiskItem[] }>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <AlertTriangle className="size-5 text-amber-700" aria-hidden="true" />
          <h2 className="text-base font-black text-slate-950">Risk Queue</h2>
        </div>
        <span className="rounded-lg border border-amber-200 bg-amber-50 px-2.5 py-1 text-xs font-black text-amber-800">
          Watchlist
        </span>
      </div>
      <div className="mt-4 space-y-3">
        {risks.length === 0 ? (
          <EmptyState title="Tidak ada risiko terbuka" description="Backend tidak mengirim risiko yang perlu ditangani." />
        ) : (
          risks.map((risk) => (
            <div key={risk.code} className="rounded-lg border border-slate-200 bg-slate-50/70 p-3">
              <div className="flex items-start justify-between gap-3">
                <p className="font-black text-slate-950">{risk.code}</p>
                <StatusBadge label={risk.severity} tone={riskTone[risk.severity]} />
              </div>
              <p className="mt-2 text-sm font-medium leading-6 text-slate-700">{risk.description}</p>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

export default function HomePage() {
  const { data, isLoading, isError, error } = useDashboardOverview();

  if (isLoading) {
    return <LoadingSkeleton />;
  }

  if (isError) {
    return (
      <ErrorState
        message={error instanceof Error ? error.message : "Dashboard overview tidak tersedia. Periksa backend API."}
      />
    );
  }

  if (!data) {
    return <EmptyState title="Dashboard belum tersedia" description="Data ringkasan belum dikirim oleh backend." />;
  }

  return (
    <main className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <PageHeader
          title="SIA-PDAM Enterprise"
          description="Kontrol awal untuk fondasi akuntansi, audit trail, idempotency payment, dan kesiapan modul PDAM."
        />
        <div className="min-w-64 rounded-lg border border-slate-800 bg-slate-950 px-4 py-3 text-sm text-white shadow-sm">
          <div className="flex items-center gap-2 font-black">
            <Clock3 className="size-4 text-teal-300" aria-hidden="true" />
            Snapshot
          </div>
          <p className="mt-1 font-semibold text-slate-300">{new Date(data.generatedAt).toLocaleString("id-ID")}</p>
        </div>
      </div>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {data.metrics.map((metric) => (
          <MetricCard key={metric.label} metric={metric} />
        ))}
      </section>

      <section className="grid gap-4 xl:grid-cols-[1fr_360px]">
        <ModuleTable modules={data.modules} />
        <div className="space-y-4">
          <FinancialCommandAccessPanel />
          <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center gap-2">
              <ShieldCheck className="size-5 text-teal-700" aria-hidden="true" />
              <h2 className="text-base font-bold text-slate-950">Financial Guard</h2>
            </div>
            <p className="mt-3 text-sm leading-6 text-slate-700">
              Posted journal immutable, debit-credit balance, period lock, audit trail, dan payment idempotency menjadi
              gate sebelum billing/payment dipakai operasional.
            </p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center gap-2">
              <Database className="size-5 text-indigo-700" aria-hidden="true" />
              <h2 className="text-base font-bold text-slate-950">Database Source of Truth</h2>
            </div>
            <p className="mt-3 text-sm leading-6 text-slate-700">
              Constraint, index, unique key, dan trigger menjadi pengaman utama. UI tidak boleh menjadi satu-satunya
              lapisan validasi.
            </p>
          </div>
        </div>
      </section>

      <section className="grid gap-4 xl:grid-cols-2">
        <QualityGateList gates={data.qualityGates} />
        <RiskQueue risks={data.risks} />
      </section>
    </main>
  );
}
