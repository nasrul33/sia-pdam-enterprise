"use client";

import { AlertTriangle, CheckCircle2, Loader2, RefreshCw, Search, ShieldCheck, UserCog, UserRoundCheck, UserRoundX, X } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { PageHeader } from "@/components/common/page-header";
import { EmptyState } from "@/components/state/empty-state";
import { ErrorState } from "@/components/state/error-state";
import { LoadingSkeleton } from "@/components/state/loading-skeleton";
import { StatusBadge } from "@/components/status/status-badge";
import { useCurrentUser } from "@/features/auth/use-current-user";
import { apiErrorMessage } from "@/lib/api/client";
import {
  canChangeUserStatus,
  canReplaceUserRoles,
  resolveAdminUserPermissions
} from "./admin-user-permissions";
import type { AdminRole, AdminUser } from "./admin-user-schema";
import {
  useAdminRoles,
  useAdminUsers,
  useReplaceAdminUserRoles,
  useUpdateAdminUserStatus
} from "./use-admin-users";

const inputClass = "h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-slate-950 outline-none transition focus:border-teal-600 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-100";
const primaryButtonClass = "inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-teal-700 px-4 text-sm font-black text-white transition hover:bg-teal-800 disabled:cursor-not-allowed disabled:bg-slate-300";
const secondaryButtonClass = "inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-4 text-sm font-black text-slate-800 transition hover:border-teal-300 hover:bg-teal-50 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500";
const dangerButtonClass = "inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-red-700 px-4 text-sm font-black text-white transition hover:bg-red-800 disabled:cursor-not-allowed disabled:bg-slate-300";

type StatusCommand = { user: AdminUser; enabled: boolean; reason: string };
type RoleCommand = { user: AdminUser; selectedRoles: Set<string>; reason: string };

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("id-ID", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function providerLabel(status: AdminUser["identityProviderStatus"]): { label: string; tone: "neutral" | "success" | "danger" } {
  if (status === "SYNCED") return { label: "IdP Synced", tone: "success" };
  if (status === "SYNC_ERROR") return { label: "IdP Error", tone: "danger" };
  return { label: "Lokal", tone: "neutral" };
}

function Summary({ label, value, helper }: Readonly<{ label: string; value: string; helper: string }>) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-black uppercase text-slate-500">{label}</p>
      <p className="mt-2 text-2xl font-black text-slate-950">{value}</p>
      <p className="mt-1 text-sm font-semibold text-slate-600">{helper}</p>
    </div>
  );
}

function Feedback({ type, message }: Readonly<{ type: "success" | "error"; message: string }>) {
  const Icon = type === "success" ? CheckCircle2 : AlertTriangle;
  return (
    <div className={`flex items-start gap-2 rounded-lg border p-3 text-sm font-semibold ${type === "success" ? "border-emerald-200 bg-emerald-50 text-emerald-900" : "border-red-200 bg-red-50 text-red-900"}`}>
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      {message}
    </div>
  );
}

function StatusDialog({ command, pending, onClose, onChange, onSubmit }: Readonly<{
  command: StatusCommand;
  pending: boolean;
  onClose: () => void;
  onChange: (reason: string) => void;
  onSubmit: () => void;
}>) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-4" role="dialog" aria-modal="true" aria-label="Konfirmasi status pengguna">
      <div className="w-full max-w-lg rounded-lg border border-slate-200 bg-white p-5 shadow-2xl">
        <div className="flex items-start justify-between gap-3">
          <div><p className="text-lg font-black text-slate-950">{command.enabled ? "Aktifkan" : "Nonaktifkan"} Pengguna</p><p className="mt-1 text-sm font-semibold text-slate-600">{command.user.username} ({command.user.email})</p></div>
          <button type="button" className={secondaryButtonClass} onClick={onClose} disabled={pending} aria-label="Tutup"><X className="size-4" /></button>
        </div>
        <label className="mt-4 block text-xs font-black uppercase text-slate-600">Alasan Audit
          <textarea className={`${inputClass} mt-1 min-h-24 py-2`} maxLength={500} value={command.reason} onChange={(event) => onChange(event.target.value)} disabled={pending} />
        </label>
        <div className="mt-4 flex justify-end gap-2"><button type="button" className={secondaryButtonClass} onClick={onClose} disabled={pending}>Batal</button><button type="button" className={command.enabled ? primaryButtonClass : dangerButtonClass} onClick={onSubmit} disabled={pending || !command.reason.trim()}>{pending ? <Loader2 className="size-4 animate-spin" /> : command.enabled ? <UserRoundCheck className="size-4" /> : <UserRoundX className="size-4" />}{command.enabled ? "Aktifkan" : "Nonaktifkan"}</button></div>
      </div>
    </div>
  );
}

function RoleDialog({ command, roles, pending, onClose, onToggle, onReason, onSubmit }: Readonly<{
  command: RoleCommand;
  roles: AdminRole[];
  pending: boolean;
  onClose: () => void;
  onToggle: (code: string) => void;
  onReason: (reason: string) => void;
  onSubmit: () => void;
}>) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-4" role="dialog" aria-modal="true" aria-label="Kelola role pengguna">
      <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-lg border border-slate-200 bg-white p-5 shadow-2xl">
        <div className="flex items-start justify-between gap-3"><div><p className="text-lg font-black text-slate-950">Role Pengguna</p><p className="mt-1 text-sm font-semibold text-slate-600">{command.user.username}</p></div><button type="button" className={secondaryButtonClass} onClick={onClose} disabled={pending} aria-label="Tutup"><X className="size-4" /></button></div>
        <div className="mt-4 grid gap-2 sm:grid-cols-2">
          {roles.map((role) => <label key={role.id} className="flex cursor-pointer items-start gap-3 rounded-lg border border-slate-200 p-3 hover:border-teal-300 hover:bg-teal-50"><input type="checkbox" className="mt-1 size-4 accent-teal-700" checked={command.selectedRoles.has(role.code)} onChange={() => onToggle(role.code)} disabled={pending} /><span><span className="block text-sm font-black text-slate-950">{role.name}</span><span className="block text-xs font-semibold text-slate-500">{role.code} · {role.permissions.length} permission</span></span></label>)}
        </div>
        <label className="mt-4 block text-xs font-black uppercase text-slate-600">Alasan Audit<textarea className={`${inputClass} mt-1 min-h-24 py-2`} maxLength={500} value={command.reason} onChange={(event) => onReason(event.target.value)} disabled={pending} /></label>
        <div className="mt-4 flex justify-end gap-2"><button type="button" className={secondaryButtonClass} onClick={onClose} disabled={pending}>Batal</button><button type="button" className={primaryButtonClass} onClick={onSubmit} disabled={pending || command.selectedRoles.size === 0 || !command.reason.trim()}>{pending ? <Loader2 className="size-4 animate-spin" /> : <ShieldCheck className="size-4" />}Simpan Role</button></div>
      </div>
    </div>
  );
}

export function AdminUserWorkspace() {
  const currentUserQuery = useCurrentUser();
  const permissions = useMemo(() => resolveAdminUserPermissions(currentUserQuery.data?.authorities ?? []), [currentUserQuery.data?.authorities]);
  const [searchInput, setSearchInput] = useState("");
  const [filters, setFilters] = useState({ search: "", page: 0, size: 25 });
  const enabled = Boolean(currentUserQuery.data?.authenticated && permissions.canReadUsers);
  const usersQuery = useAdminUsers(filters, enabled);
  const rolesQuery = useAdminRoles(enabled);
  const statusMutation = useUpdateAdminUserStatus();
  const rolesMutation = useReplaceAdminUserRoles();
  const [statusCommand, setStatusCommand] = useState<StatusCommand | null>(null);
  const [roleCommand, setRoleCommand] = useState<RoleCommand | null>(null);
  const [feedback, setFeedback] = useState<{ type: "success" | "error"; message: string } | null>(null);

  function submitSearch(event: FormEvent) { event.preventDefault(); setFilters((current) => ({ ...current, search: searchInput.trim(), page: 0 })); }
  async function submitStatus() {
    if (!statusCommand) return;
    try { await statusMutation.mutateAsync({ userId: statusCommand.user.id, payload: { enabled: statusCommand.enabled, reason: statusCommand.reason.trim() } }); setFeedback({ type: "success", message: `Status ${statusCommand.user.username} berhasil diperbarui.` }); setStatusCommand(null); }
    catch (error) { setFeedback({ type: "error", message: apiErrorMessage(error, "Gagal memperbarui status pengguna.") }); }
  }
  async function submitRoles() {
    if (!roleCommand) return;
    try { await rolesMutation.mutateAsync({ userId: roleCommand.user.id, payload: { roleCodes: [...roleCommand.selectedRoles].sort(), reason: roleCommand.reason.trim() } }); setFeedback({ type: "success", message: `Role ${roleCommand.user.username} berhasil diperbarui.` }); setRoleCommand(null); }
    catch (error) { setFeedback({ type: "error", message: apiErrorMessage(error, "Gagal memperbarui role pengguna.") }); }
  }

  if (currentUserQuery.isLoading) return <LoadingSkeleton />;
  if (currentUserQuery.isError) return <ErrorState message={apiErrorMessage(currentUserQuery.error, "Sesi admin tidak tersedia.")} />;
  if (!permissions.canReadUsers) return <ErrorState message="Anda tidak memiliki permission user.read." />;
  const users = usersQuery.data?.items ?? [];
  const activeUsers = users.filter((user) => user.enabled).length;
  const syncedUsers = users.filter((user) => user.identityProviderStatus === "SYNCED").length;

  return (
    <main className="space-y-5">
      <PageHeader title="Administrasi Pengguna" description="Kelola status akses dan role pengguna dengan kontrol self-lockout, last super-admin, sinkronisasi identitas, dan audit trail." eyebrow="Security Administration" />
      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4"><Summary label="Total Pengguna" value={String(usersQuery.data?.totalItems ?? 0)} helper="Sesuai filter aktif." /><Summary label="Aktif" value={String(activeUsers)} helper="Pada halaman saat ini." /><Summary label="IdP Synced" value={String(syncedUsers)} helper="Pada halaman saat ini." /><Summary label="Role Tersedia" value={String(rolesQuery.data?.length ?? 0)} helper="Katalog role sistem." /></section>
      <form className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:flex-row" onSubmit={submitSearch}><label className="min-w-0 flex-1 text-xs font-black uppercase text-slate-600">Cari Pengguna<div className="relative mt-1"><Search className="pointer-events-none absolute left-3 top-3 size-4 text-slate-400" /><input className={`${inputClass} pl-9`} value={searchInput} maxLength={128} placeholder="Username atau email" onChange={(event) => setSearchInput(event.target.value)} /></div></label><div className="flex items-end gap-2"><button type="submit" className={primaryButtonClass}><Search className="size-4" />Cari</button><button type="button" className={secondaryButtonClass} onClick={() => { setSearchInput(""); setFilters({ search: "", page: 0, size: 25 }); }}><RefreshCw className="size-4" />Reset</button></div></form>
      {feedback ? <Feedback {...feedback} /> : null}
      {usersQuery.isLoading || rolesQuery.isLoading ? <LoadingSkeleton /> : null}
      {usersQuery.isError || rolesQuery.isError ? <div className="space-y-3"><ErrorState message={apiErrorMessage(usersQuery.error ?? rolesQuery.error, "Data administrasi tidak tersedia.")} /><button type="button" className={secondaryButtonClass} onClick={() => { void usersQuery.refetch(); void rolesQuery.refetch(); }}><RefreshCw className="size-4" />Muat Ulang</button></div> : null}
      {!usersQuery.isLoading && !rolesQuery.isLoading && !usersQuery.isError && !rolesQuery.isError && users.length === 0 ? <EmptyState title="Pengguna tidak ditemukan" description="Ubah kata pencarian atau pastikan akun telah diprovisioning melalui proses bootstrap/identity provider." /> : null}
      {users.length > 0 ? <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm"><div className="overflow-x-auto"><table className="min-w-full divide-y divide-slate-200 text-sm"><thead className="bg-slate-50"><tr><th className="px-4 py-3 text-left font-black text-slate-700">Pengguna</th><th className="px-4 py-3 text-left font-black text-slate-700">Status</th><th className="px-4 py-3 text-left font-black text-slate-700">Role</th><th className="px-4 py-3 text-left font-black text-slate-700">Identity</th><th className="px-4 py-3 text-left font-black text-slate-700">Diperbarui</th><th className="px-4 py-3 text-left font-black text-slate-700">Aksi</th></tr></thead><tbody className="divide-y divide-slate-100">{users.map((user) => { const provider = providerLabel(user.identityProviderStatus); return <tr key={user.id} className="hover:bg-teal-50"><td className="px-4 py-4"><p className="font-black text-slate-950">{user.username}</p><p className="mt-1 text-xs font-semibold text-slate-500">{user.email}</p></td><td className="px-4 py-4"><StatusBadge label={user.enabled ? "Aktif" : "Nonaktif"} tone={user.enabled ? "success" : "danger"} /></td><td className="px-4 py-4"><div className="flex max-w-sm flex-wrap gap-1">{user.roles.length ? user.roles.map((role) => <span key={role} className="rounded border border-slate-200 bg-slate-50 px-2 py-1 text-xs font-bold text-slate-700">{role}</span>) : <span className="text-xs font-semibold text-amber-700">Tanpa role</span>}</div></td><td className="px-4 py-4"><StatusBadge label={provider.label} tone={provider.tone} /></td><td className="whitespace-nowrap px-4 py-4 text-xs font-semibold text-slate-600">{formatDateTime(user.updatedAt)}</td><td className="px-4 py-4"><div className="flex flex-wrap gap-2"><button type="button" className={user.enabled ? dangerButtonClass : primaryButtonClass} disabled={!canChangeUserStatus(user, currentUserQuery.data?.username ?? null, permissions) || statusMutation.isPending} onClick={() => { setFeedback(null); setStatusCommand({ user, enabled: !user.enabled, reason: "" }); }}>{user.enabled ? <UserRoundX className="size-4" /> : <UserRoundCheck className="size-4" />}{user.enabled ? "Nonaktifkan" : "Aktifkan"}</button><button type="button" className={secondaryButtonClass} disabled={!canReplaceUserRoles(permissions) || rolesMutation.isPending} onClick={() => { setFeedback(null); setRoleCommand({ user, selectedRoles: new Set(user.roles), reason: "" }); }}><UserCog className="size-4" />Role</button></div></td></tr>; })}</tbody></table></div></section> : null}
      {statusCommand ? <StatusDialog command={statusCommand} pending={statusMutation.isPending} onClose={() => setStatusCommand(null)} onChange={(reason) => setStatusCommand((current) => current ? { ...current, reason } : current)} onSubmit={() => void submitStatus()} /> : null}
      {roleCommand ? <RoleDialog command={roleCommand} roles={rolesQuery.data ?? []} pending={rolesMutation.isPending} onClose={() => setRoleCommand(null)} onToggle={(code) => setRoleCommand((current) => { if (!current) return current; const selectedRoles = new Set(current.selectedRoles); if (selectedRoles.has(code)) selectedRoles.delete(code); else selectedRoles.add(code); return { ...current, selectedRoles }; })} onReason={(reason) => setRoleCommand((current) => current ? { ...current, reason } : current)} onSubmit={() => void submitRoles()} /> : null}
    </main>
  );
}
