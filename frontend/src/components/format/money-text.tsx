export function MoneyText({ value }: { value: number | string }) {
  const numeric = typeof value === "string" ? Number(value) : value;
  return <span>{new Intl.NumberFormat("id-ID", { style: "currency", currency: "IDR" }).format(numeric)}</span>;
}
