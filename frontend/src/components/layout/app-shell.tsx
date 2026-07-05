import Link from "next/link";
import {
  BadgeCheck,
  Banknote,
  BookOpenCheck,
  ClipboardList,
  Gauge,
  LayoutDashboard,
  LockKeyhole,
  ReceiptText,
  UserRoundCog,
  UsersRound,
  WalletCards
} from "lucide-react";

const navItems = [
  { label: "Dashboard", icon: LayoutDashboard },
  { label: "Pelanggan", icon: UsersRound },
  { label: "Sambungan", icon: BadgeCheck },
  { label: "Baca Meter", icon: Gauge },
  { label: "Billing", icon: ReceiptText },
  { label: "Pembayaran", icon: WalletCards },
  { label: "Piutang", icon: Banknote },
  { label: "Akuntansi", icon: BookOpenCheck },
  { label: "Laporan", icon: ClipboardList },
  { label: "Admin", icon: UserRoundCog }
] as const;

export function AppShell({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <div className="min-h-screen bg-[#f6f8fb]">
      <aside className="fixed inset-y-0 left-0 hidden w-72 border-r border-slate-200 bg-white p-6 lg:block">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-lg bg-slate-950 text-white">
            <LockKeyhole className="size-5" aria-hidden="true" />
          </div>
          <div>
            <div className="text-base font-bold text-slate-950">SIA-PDAM</div>
            <div className="text-xs font-medium text-slate-500">Enterprise Control</div>
          </div>
        </div>
        <nav className="mt-8 space-y-1">
          {navItems.map(({ label, icon: Icon }) => (
            <Link
              key={label}
              href="/"
              className="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-100 hover:text-slate-950"
            >
              <Icon className="size-4" aria-hidden="true" />
              {label}
            </Link>
          ))}
        </nav>
      </aside>
      <div className="lg:pl-72">
        <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/95 px-4 py-4 backdrop-blur sm:px-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm font-semibold text-slate-700">Enterprise Dashboard</p>
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-800">
              Financial-control first
            </div>
          </div>
        </header>
        <div className="p-4 sm:p-6">{children}</div>
      </div>
    </div>
  );
}
