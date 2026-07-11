"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import {
  Activity,
  BadgeCheck,
  Banknote,
  BookOpenCheck,
  CalendarDays,
  ClipboardList,
  Droplets,
  Gauge,
  Landmark,
  LayoutDashboard,
  LockKeyhole,
  ReceiptText,
  Settings,
  ShieldCheck,
  UsersRound,
  WalletCards,
  type LucideIcon
} from "lucide-react";
import { cn } from "@/lib/utils";

type NavItem = {
  label: string;
  href: string;
  icon: LucideIcon;
  description: string;
};

type NavSection = {
  title: string;
  items: NavItem[];
};

const navSections: NavSection[] = [
  {
    title: "Command",
    items: [
      { label: "Dashboard", href: "/", icon: LayoutDashboard, description: "Ringkasan kendali" }
    ]
  },
  {
    title: "Operasi PDAM",
    items: [
      { label: "Pelanggan", href: "/customers", icon: UsersRound, description: "Master pelanggan" },
      { label: "Sambungan", href: "/connections", icon: BadgeCheck, description: "Status layanan" },
      { label: "Permohonan", href: "/connections/requests", icon: ClipboardList, description: "Request sambungan" },
      { label: "Baca Meter", href: "/metering", icon: Gauge, description: "Route dan validasi" },
      { label: "Tarif", href: "/tariffs", icon: ReceiptText, description: "Versi dan blok" }
    ]
  },
  {
    title: "Keuangan",
    items: [
      { label: "Billing", href: "/billing", icon: ReceiptText, description: "Batch dan invoice" },
      { label: "Pembayaran", href: "/payments", icon: WalletCards, description: "Kas dan rekonsiliasi" },
      { label: "Mutasi Bank", href: "/payments/bank-mutations", icon: Landmark, description: "Import & match" },
      { label: "Piutang", href: "/receivables/collection-actions", icon: Banknote, description: "Penagihan" },
      { label: "Aging Piutang", href: "/receivables/aging", icon: Banknote, description: "Umur piutang" },
      { label: "Cicilan", href: "/receivables/installments", icon: Banknote, description: "Installment plan" },
      { label: "Akuntansi", href: "/accounting", icon: BookOpenCheck, description: "Jurnal dan ledger" },
      { label: "AP Payables", href: "/accounting/payables", icon: Banknote, description: "Utang usaha" },
      { label: "Aset Tetap", href: "/accounting/assets", icon: BookOpenCheck, description: "Depresiasi" },
      { label: "Neraca Saldo", href: "/reports/trial-balance", icon: ClipboardList, description: "Laporan posted" },
      { label: "Laporan Keuangan", href: "/reports/financial-statements", icon: ClipboardList, description: "FS & tax recap" }
    ]
  },
  {
    title: "Administrasi",
    items: [
      { label: "Pengguna", href: "/admin/users", icon: UsersRound, description: "User dan role" },
      { label: "Settings", href: "/admin/settings", icon: Settings, description: "Parameter sistem" }
    ]
  }
];

const navItems = navSections.flatMap((section) => section.items);

function isActiveRoute(pathname: string, href: string) {
  if (href === "/") {
    return pathname === "/";
  }

  return pathname === href || pathname.startsWith(`${href}/`);
}

function SidebarNavItem({ item, active }: Readonly<{ item: NavItem; active: boolean }>) {
  const Icon = item.icon;

  return (
    <Link
      href={item.href}
      aria-current={active ? "page" : undefined}
      className={cn(
        "group flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm transition",
        active
          ? "border border-teal-300/30 bg-white text-slate-950 shadow-[0_12px_28px_-18px_rgba(20,184,166,0.8)]"
          : "border border-transparent text-slate-300 hover:border-white/10 hover:bg-white/[0.08] hover:text-white"
      )}
    >
      <span
        className={cn(
          "flex size-8 shrink-0 items-center justify-center rounded-lg transition",
          active
            ? "bg-teal-600 text-white"
            : "bg-white/[0.08] text-slate-300 group-hover:bg-white/[0.12] group-hover:text-white"
        )}
      >
        <Icon className="size-4" aria-hidden="true" />
      </span>
      <span className="min-w-0">
        <span className="block truncate font-bold">{item.label}</span>
        <span className={cn("block truncate text-xs", active ? "text-slate-600" : "text-slate-500")}>
          {item.description}
        </span>
      </span>
    </Link>
  );
}

function MobileNavItem({ item, active }: Readonly<{ item: NavItem; active: boolean }>) {
  const Icon = item.icon;

  return (
    <Link
      href={item.href}
      aria-current={active ? "page" : undefined}
      className={cn(
        "inline-flex shrink-0 items-center gap-2 rounded-lg border px-3 py-2 text-sm font-bold transition",
        active
          ? "border-teal-200 bg-teal-50 text-teal-900"
          : "border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:text-slate-950"
      )}
    >
      <Icon className="size-4" aria-hidden="true" />
      {item.label}
    </Link>
  );
}

export function AppShell({ children }: Readonly<{ children: ReactNode }>) {
  const pathname = usePathname();
  const activeItem =
    navItems
      .filter((item) => isActiveRoute(pathname, item.href))
      .sort((left, right) => right.href.length - left.href.length)[0] ?? navItems[0];
  const today = new Intl.DateTimeFormat("id-ID", {
    day: "2-digit",
    month: "short",
    year: "numeric"
  }).format(new Date());

  return (
    <div className="min-h-screen bg-[#eef3f8] text-slate-950">
      <aside className="app-scrollbar fixed inset-y-0 left-0 z-40 hidden w-[19rem] overflow-y-auto border-r border-slate-950 bg-[#111827] text-white shadow-[18px_0_44px_-34px_rgba(15,23,42,0.92)] lg:block">
        <div className="sticky top-0 z-10 h-1 bg-[linear-gradient(90deg,#14b8a6,#2563eb,#f59e0b)]" />
        <div className="px-4 py-5">
          <div className="flex items-center gap-3 px-1">
            <div className="flex size-10 items-center justify-center rounded-lg bg-teal-500 text-white shadow-[0_16px_34px_-20px_rgba(20,184,166,0.95)]">
              <Droplets className="size-5" aria-hidden="true" />
            </div>
            <div className="min-w-0">
              <div className="truncate text-base font-black text-white">SIA-PDAM Enterprise</div>
              <div className="truncate text-xs font-semibold text-slate-400">PERUMDAM Command Center</div>
            </div>
          </div>

          <div className="mt-4 rounded-lg border border-white/10 bg-white/[0.06] p-3">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-xs font-semibold text-slate-400">Runtime lokal</p>
                <p className="mt-1 text-sm font-black text-white">Kontrol finansial aktif</p>
              </div>
              <div className="flex size-9 items-center justify-center rounded-lg bg-emerald-400/[0.12] text-emerald-300">
                <Activity className="size-5" aria-hidden="true" />
              </div>
            </div>
            <div className="mt-3 grid grid-cols-3 gap-2 text-center">
              <div className="rounded-lg bg-white/[0.08] px-2 py-1.5">
                <div className="text-sm font-black text-white">11</div>
                <div className="text-xs font-semibold text-slate-400">Route</div>
              </div>
              <div className="rounded-lg bg-white/[0.08] px-2 py-1.5">
                <div className="text-sm font-black text-white">RBAC</div>
                <div className="text-xs font-semibold text-slate-400">Guard</div>
              </div>
              <div className="rounded-lg bg-white/[0.08] px-2 py-1.5">
                <div className="text-sm font-black text-white">Audit</div>
                <div className="text-xs font-semibold text-slate-400">Trail</div>
              </div>
            </div>
          </div>

          <nav className="mt-4 space-y-4" aria-label="Navigasi utama">
            {navSections.map((section) => (
              <section key={section.title} className="space-y-2">
                <div className="px-2.5 text-xs font-black uppercase text-slate-400">{section.title}</div>
                <div className="space-y-1">
                  {section.items.map((item) => (
                    <SidebarNavItem key={`${section.title}-${item.label}`} item={item} active={activeItem.href === item.href} />
                  ))}
                </div>
              </section>
            ))}
          </nav>

          <div className="mt-4 rounded-lg border border-white/10 bg-slate-950/[0.34] p-3">
            <div className="flex items-center gap-2 text-sm font-black text-white">
              <LockKeyhole className="size-4 text-amber-300" aria-hidden="true" />
              Mode kontrol
            </div>
            <p className="mt-2 text-xs leading-5 text-slate-300">
              Semua command sensitif tetap harus melewati authority backend, audit reason, dan guard periode.
            </p>
          </div>
        </div>
      </aside>

      <div className="lg:pl-[19rem]">
        <header className="sticky top-0 z-30 border-b border-slate-200/80 bg-white/90 backdrop-blur-xl">
          <div className="px-4 py-3 sm:px-6 lg:px-8">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2 text-xs font-black uppercase text-teal-700">
                  <ShieldCheck className="size-4" aria-hidden="true" />
                  Enterprise Operations
                </div>
                <div className="mt-1 flex flex-wrap items-center gap-2">
                  <h1 className="text-xl font-black text-slate-950 sm:text-2xl">{activeItem.label}</h1>
                  <span className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-bold text-slate-600">
                    {activeItem.description}
                  </span>
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <div className="hidden items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-bold text-emerald-800 sm:flex">
                  <Activity className="size-4" aria-hidden="true" />
                  Online
                </div>
                <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-bold text-slate-700 shadow-sm">
                  <CalendarDays className="size-4 text-slate-500" aria-hidden="true" />
                  {today}
                </div>
              </div>
            </div>

            <nav className="app-scrollbar mt-3 flex gap-2 overflow-x-auto pb-1 lg:hidden" aria-label="Navigasi modul">
              {navItems.map((item) => (
                <MobileNavItem key={`mobile-${item.label}`} item={item} active={activeItem.href === item.href} />
              ))}
            </nav>
          </div>
        </header>

        <main className="px-4 py-5 sm:px-6 lg:px-8 lg:py-8">{children}</main>
      </div>
    </div>
  );
}
