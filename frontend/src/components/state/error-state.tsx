export function ErrorState({ message }: Readonly<{ message: string }>) {
  return (
    <div className="rounded-lg border border-red-200 bg-red-50 p-4">
      <p className="text-sm font-semibold text-red-900">Gagal memuat data</p>
      <p className="mt-1 text-sm text-red-700">{message}</p>
    </div>
  );
}
