export function PermissionGate({
  allowed,
  children,
  message = "Anda tidak memiliki izin untuk mengakses aksi ini."
}: Readonly<{ allowed: boolean; children?: React.ReactNode; message?: string }>) {
  if (!allowed) {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm font-semibold text-amber-900">
        {message}
      </div>
    );
  }
  return <>{children}</>;
}
