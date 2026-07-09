import { AlertTriangle } from "lucide-react";

export function ErrorState({ message }: Readonly<{ message: string }>) {
  return (
    <div className="rounded-lg border border-red-200 bg-red-50 p-5 shadow-sm">
      <div className="flex items-start gap-3">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-red-100 text-red-700">
          <AlertTriangle className="size-5" aria-hidden="true" />
        </div>
        <div className="min-w-0">
          <p className="text-sm font-black text-red-950">Gagal memuat data</p>
          <p className="mt-1 text-sm font-medium leading-6 text-red-800">{message}</p>
        </div>
      </div>
    </div>
  );
}
