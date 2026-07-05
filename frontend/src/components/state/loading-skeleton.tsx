export function LoadingSkeleton() {
  return (
    <div className="animate-pulse space-y-4">
      <div className="h-7 w-64 rounded bg-slate-200" />
      <div className="grid gap-4 md:grid-cols-4">
        <div className="h-28 rounded-lg bg-slate-100" />
        <div className="h-28 rounded-lg bg-slate-100" />
        <div className="h-28 rounded-lg bg-slate-100" />
        <div className="h-28 rounded-lg bg-slate-100" />
      </div>
      <div className="h-72 rounded-lg bg-slate-100" />
    </div>
  );
}
