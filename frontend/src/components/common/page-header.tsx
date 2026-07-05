type PageHeaderProps = {
  title: string;
  description?: string;
};

export function PageHeader({ title, description }: Readonly<PageHeaderProps>) {
  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-950">{title}</h1>
      {description ? <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-700">{description}</p> : null}
    </div>
  );
}
