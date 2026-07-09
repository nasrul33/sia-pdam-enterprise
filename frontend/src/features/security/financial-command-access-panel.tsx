"use client";

import { CheckCircle2, KeyRound, Loader2, LockKeyhole, ShieldAlert } from "lucide-react";
import { EmptyState } from "@/components/state/empty-state";
import { ErrorState } from "@/components/state/error-state";
import { StatusBadge } from "@/components/status/status-badge";
import { useCurrentUser } from "@/features/auth/use-current-user";
import { apiErrorMessage } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import {
  resolveFinancialCommandPermissions,
  visibleFinancialCommandGroups,
  type FinancialCommand
} from "./financial-command-permissions";

function CommandAccessRow({ command }: Readonly<{ command: FinancialCommand }>) {
  const tone = command.allowed ? "success" : "neutral";
  const riskTone = command.risk === "high" ? "danger" : "warning";

  return (
    <div
      className={cn(
        "flex items-center justify-between gap-3 border-b border-slate-100 px-4 py-3 last:border-b-0",
        command.allowed ? "bg-white" : "bg-slate-50"
      )}
    >
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          {command.allowed ? (
            <CheckCircle2 className="size-4 shrink-0 text-emerald-700" aria-hidden="true" />
          ) : (
            <LockKeyhole className="size-4 shrink-0 text-slate-500" aria-hidden="true" />
          )}
          <p className="truncate text-sm font-bold text-slate-950">{command.label}</p>
        </div>
        <p className="mt-1 font-mono text-xs font-semibold text-slate-600">{command.permission}</p>
      </div>
      <div className="flex shrink-0 items-center gap-2">
        <StatusBadge label={command.risk === "high" ? "High" : "Medium"} tone={riskTone} />
        <StatusBadge label={command.allowed ? "Aktif" : "Terkunci"} tone={tone} />
      </div>
    </div>
  );
}

export function FinancialCommandAccessPanel() {
  const currentUserQuery = useCurrentUser();

  if (currentUserQuery.isLoading) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2 text-sm font-bold text-slate-950">
          <Loader2 className="size-4 animate-spin text-slate-600" aria-hidden="true" />
          Memuat akses finansial
        </div>
        <div className="mt-4 space-y-2">
          <div className="h-10 animate-pulse rounded-lg bg-slate-100" />
          <div className="h-10 animate-pulse rounded-lg bg-slate-100" />
          <div className="h-10 animate-pulse rounded-lg bg-slate-100" />
        </div>
      </div>
    );
  }

  if (currentUserQuery.isError) {
    return (
      <div className="space-y-3 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2">
          <ShieldAlert className="size-5 text-amber-700" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Financial Command Access</h2>
        </div>
        <ErrorState message={apiErrorMessage(currentUserQuery.error, "Hak akses command finansial tidak tersedia.")} />
      </div>
    );
  }

  const permissionState = resolveFinancialCommandPermissions(currentUserQuery.data?.authorities ?? []);
  const groups = visibleFinancialCommandGroups(permissionState);

  return (
    <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 p-5">
        <div className="flex items-center gap-2">
          <KeyRound className="size-5 text-teal-700" aria-hidden="true" />
          <h2 className="text-base font-bold text-slate-950">Financial Command Access</h2>
        </div>
        <p className="mt-2 text-sm font-semibold text-slate-600">{currentUserQuery.data?.username}</p>
      </div>

      {!permissionState.hasAnyFinancialCommand ? (
        <div className="p-5">
          <EmptyState
            title="Tidak ada command finansial"
            description="Akun ini belum memiliki authority accounting atau billing command."
          />
        </div>
      ) : (
        <div className="divide-y divide-slate-200">
          {groups.map((group) => (
            <section key={group.title}>
              <div className="bg-slate-50 px-4 py-2 text-xs font-bold uppercase text-slate-600">{group.title}</div>
              {group.commands.map((command) => (
                <CommandAccessRow key={command.permission} command={command} />
              ))}
            </section>
          ))}
        </div>
      )}
    </div>
  );
}
