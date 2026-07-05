export function PermissionGate({ allowed, children }: Readonly<{ allowed: boolean; children: React.ReactNode }>) {
  if (!allowed) {
    return (
      <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
        Anda tidak memiliki izin untuk mengakses aksi ini.
      </div>
    );
  }
  return <>{children}</>;
}
