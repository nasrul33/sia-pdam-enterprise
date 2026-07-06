import type { BillingCommandPermissionState } from "@/features/security/financial-command-permissions";
import type { Account } from "@/features/accounting/accounting-schema";
import type { BillingBatchStatus, Invoice, InvoiceStatus } from "./billing-schema";

type BatchSummarySubject = {
  status: BillingBatchStatus;
};

type InvoiceSummarySubject = {
  status: InvoiceStatus;
  outstandingAmount: number;
};

export type BillingWorkspaceSummary = {
  completedBatches: number;
  runningOrFailedBatches: number;
  draftInvoices: number;
  issuedInvoices: number;
  totalOutstanding: number;
};

export type GenerateBillingBatchDraft = {
  period: string;
  areaCode: string;
  billingDate: string;
  dueDate: string;
  reason: string;
};

export type IssueInvoiceDraft = {
  receivableAccountId: string;
  revenueAccountId: string;
  reason: string;
};

export function summarizeBillingWorkspace(input: {
  batches: readonly BatchSummarySubject[];
  invoices: readonly InvoiceSummarySubject[];
}): BillingWorkspaceSummary {
  return {
    completedBatches: input.batches.filter((batch) => batch.status === "COMPLETED").length,
    runningOrFailedBatches: input.batches.filter((batch) => batch.status === "RUNNING" || batch.status === "FAILED").length,
    draftInvoices: input.invoices.filter((invoice) => invoice.status === "DRAFT").length,
    issuedInvoices: input.invoices.filter((invoice) => invoice.status === "ISSUED").length,
    totalOutstanding: input.invoices.reduce((total, invoice) => total + invoice.outstandingAmount, 0)
  };
}

export function canIssueInvoice(invoice: Pick<Invoice, "status">, permissions: BillingCommandPermissionState): boolean {
  return permissions.canIssueInvoices && invoice.status === "DRAFT";
}

export function generateBillingBatchErrors(input: GenerateBillingBatchDraft): string[] {
  const errors: string[] = [];
  const period = input.period.trim();
  const billingDate = input.billingDate.trim();
  const dueDate = input.dueDate.trim();

  if (!/^\d{4}-\d{2}$/.test(period)) {
    errors.push("Periode wajib menggunakan format yyyy-MM.");
  }
  if (!input.areaCode.trim()) {
    errors.push("Area wajib diisi.");
  }
  if (!billingDate) {
    errors.push("Tanggal billing wajib diisi.");
  }
  if (!dueDate) {
    errors.push("Tanggal jatuh tempo wajib diisi.");
  }
  if (billingDate && dueDate && dueDate < billingDate) {
    errors.push("Tanggal jatuh tempo tidak boleh sebelum tanggal billing.");
  }
  if (!input.reason.trim()) {
    errors.push("Alasan audit wajib diisi.");
  }

  return errors;
}

export function issueInvoiceErrors(input: {
  draft: IssueInvoiceDraft;
  accounts: readonly Account[];
}): string[] {
  const errors: string[] = [];
  const receivableAccount = input.accounts.find((account) => account.id === input.draft.receivableAccountId);
  const revenueAccount = input.accounts.find((account) => account.id === input.draft.revenueAccountId);

  if (!receivableAccount) {
    errors.push("Akun piutang wajib dipilih.");
  } else if (receivableAccount.type !== "ASSET") {
    errors.push("Akun piutang wajib bertipe aset.");
  }

  if (!revenueAccount) {
    errors.push("Akun pendapatan wajib dipilih.");
  } else if (revenueAccount.type !== "REVENUE") {
    errors.push("Akun pendapatan wajib bertipe pendapatan.");
  }

  if (!input.draft.reason.trim()) {
    errors.push("Alasan audit wajib diisi.");
  }

  return errors;
}
