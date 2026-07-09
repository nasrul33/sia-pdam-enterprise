export function LoadingSkeleton() {
  return (
    <div className="animate-pulse space-y-5">
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="h-3 w-36 rounded bg-teal-100" />
        <div className="mt-4 h-8 w-72 max-w-full rounded bg-slate-200" />
        <div className="mt-3 h-4 w-full max-w-2xl rounded bg-slate-100" />
      </div>
      <div className="grid gap-4 md:grid-cols-4">
        <div className="h-32 rounded-lg border border-slate-200 bg-white shadow-sm" />
        <div className="h-32 rounded-lg border border-slate-200 bg-white shadow-sm" />
        <div className="h-32 rounded-lg border border-slate-200 bg-white shadow-sm" />
        <div className="h-32 rounded-lg border border-slate-200 bg-white shadow-sm" />
      </div>
      <div className="h-80 rounded-lg border border-slate-200 bg-white shadow-sm" />
    </div>
  );
}
