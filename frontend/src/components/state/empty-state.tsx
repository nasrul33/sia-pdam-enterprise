import { Inbox } from "lucide-react";

export function EmptyState({ title, description }: Readonly<{ title: string; description?: string }>) {
  return (
    <div className="rounded-lg border border-dashed border-slate-300 bg-white p-8 text-center shadow-sm">
      <div className="mx-auto flex size-12 items-center justify-center rounded-lg border border-teal-100 bg-teal-50 text-teal-700">
        <Inbox className="size-6" aria-hidden="true" />
      </div>
      <h3 className="mt-4 text-base font-black text-slate-950">{title}</h3>
      {description ? <p className="mx-auto mt-2 max-w-xl text-sm font-medium leading-6 text-slate-600">{description}</p> : null}
    </div>
  );
}
