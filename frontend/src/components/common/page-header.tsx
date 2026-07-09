type PageHeaderProps = {
  title: string;
  description?: string;
  eyebrow?: string;
};

export function PageHeader({ title, description, eyebrow = "Command Surface" }: Readonly<PageHeaderProps>) {
  return (
    <div className="relative min-w-0 flex-1 overflow-hidden rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="absolute inset-y-0 left-0 w-1 bg-teal-600" />
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-xs font-black uppercase text-teal-700">{eyebrow}</p>
          <h1 className="mt-2 text-2xl font-black text-slate-950 sm:text-3xl">{title}</h1>
          {description ? <p className="mt-3 max-w-4xl text-sm font-medium leading-6 text-slate-600">{description}</p> : null}
        </div>
        <div className="hidden rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-black text-slate-700 sm:block">
          Enterprise-grade
        </div>
      </div>
    </div>
  );
}
