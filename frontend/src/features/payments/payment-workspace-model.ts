import type { Account } from "@/features/accounting/accounting-schema";
import type { PaymentCommandPermissionState } from "@/features/security/financial-command-permissions";
import type { PaymentWebhookStatus } from "./payment-schema";

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

type WebhookSummarySubject = {
  status: PaymentWebhookStatus;
  errorMessage: string | null;
};

export type PaymentWorkspaceSummary = {
  receivedEvents: number;
  processedEvents: number;
  failedEvents: number;
  ignoredEvents: number;
  unresolvedFailures: number;
};

export type CounterPaymentAllocationDraft = {
  invoiceId: string;
  amount: string;
};

export type CounterPaymentDraft = {
  externalReference: string;
  amount: string;
  paidAt: string;
  allocations: readonly CounterPaymentAllocationDraft[];
  cashAccountId: string;
  receivableAccountId: string;
  reason: string;
};

export type ReversePaymentDraft = {
  paymentId: string;
  cashAccountId: string;
  receivableAccountId: string;
  reason: string;
};

export function summarizePaymentWorkspace(events: readonly WebhookSummarySubject[]): PaymentWorkspaceSummary {
  return {
    receivedEvents: events.filter((event) => event.status === "RECEIVED").length,
    processedEvents: events.filter((event) => event.status === "PROCESSED").length,
    failedEvents: events.filter((event) => event.status === "FAILED").length,
    ignoredEvents: events.filter((event) => event.status === "IGNORED").length,
    unresolvedFailures: events.filter((event) => event.status === "FAILED" && Boolean(event.errorMessage?.trim())).length
  };
}

export function canSettleCounterPayment(permissions: PaymentCommandPermissionState): boolean {
  return permissions.canSettleCounterPayments;
}

export function canReversePayment(permissions: PaymentCommandPermissionState): boolean {
  return permissions.canReversePayments;
}

export function parseMoneyInput(value: string): number | null {
  const normalized = value.trim().replace(",", ".");
  if (!normalized) {
    return null;
  }

  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null;
  }

  return Math.round(parsed * 100) / 100;
}

export function allocationTotalAmount(allocations: readonly CounterPaymentAllocationDraft[]): number {
  const cents = allocations.reduce((total, allocation) => {
    const amount = parseMoneyInput(allocation.amount);
    return total + (amount === null ? 0 : Math.round(amount * 100));
  }, 0);

  return cents / 100;
}

export function counterPaymentErrors(input: {
  draft: CounterPaymentDraft;
  accounts: readonly Account[];
}): string[] {
  const errors: string[] = [];
  const amount = parseMoneyInput(input.draft.amount);
  const paidAt = input.draft.paidAt.trim();
  const externalReference = input.draft.externalReference.trim();
  const cashAccount = input.accounts.find((account) => account.id === input.draft.cashAccountId);
  const receivableAccount = input.accounts.find((account) => account.id === input.draft.receivableAccountId);

  if (externalReference.length > 128) {
    errors.push("Referensi eksternal maksimal 128 karakter.");
  }
  if (amount === null) {
    errors.push("Nominal pembayaran wajib lebih besar dari nol.");
  }
  if (!paidAt || Number.isNaN(new Date(paidAt).getTime())) {
    errors.push("Waktu bayar wajib valid.");
  }
  if (!cashAccount) {
    errors.push("Akun kas/bank wajib dipilih.");
  } else if (cashAccount.type !== "ASSET") {
    errors.push("Akun kas/bank wajib bertipe aset.");
  }
  if (!receivableAccount) {
    errors.push("Akun piutang wajib dipilih.");
  } else if (receivableAccount.type !== "ASSET") {
    errors.push("Akun piutang wajib bertipe aset.");
  }
  if (cashAccount && receivableAccount && cashAccount.id === receivableAccount.id) {
    errors.push("Akun kas/bank dan akun piutang tidak boleh sama.");
  }
  if (input.draft.allocations.length === 0) {
    errors.push("Minimal satu alokasi invoice wajib diisi.");
  }

  const invoiceIds = new Set<string>();
  input.draft.allocations.forEach((allocation, index) => {
    const invoiceId = allocation.invoiceId.trim();
    if (!uuidPattern.test(invoiceId)) {
      errors.push(`Invoice alokasi ${index + 1} wajib berupa UUID valid.`);
    } else if (invoiceIds.has(invoiceId)) {
      errors.push(`Invoice alokasi ${index + 1} duplikat.`);
    }
    invoiceIds.add(invoiceId);

    if (parseMoneyInput(allocation.amount) === null) {
      errors.push(`Nominal alokasi ${index + 1} wajib lebih besar dari nol.`);
    }
  });

  if (amount !== null) {
    const totalAllocationCents = Math.round(allocationTotalAmount(input.draft.allocations) * 100);
    const paymentCents = Math.round(amount * 100);
    if (totalAllocationCents !== paymentCents) {
      errors.push("Total alokasi invoice wajib sama dengan nominal pembayaran.");
    }
  }

  if (!input.draft.reason.trim()) {
    errors.push("Alasan audit wajib diisi.");
  }

  return errors;
}

export function reversePaymentErrors(input: {
  draft: ReversePaymentDraft;
  accounts: readonly Account[];
}): string[] {
  const errors: string[] = [];
  const paymentId = input.draft.paymentId.trim();
  const cashAccount = input.accounts.find((account) => account.id === input.draft.cashAccountId);
  const receivableAccount = input.accounts.find((account) => account.id === input.draft.receivableAccountId);

  if (!uuidPattern.test(paymentId)) {
    errors.push("Payment ID wajib berupa UUID valid.");
  }
  if (!cashAccount) {
    errors.push("Akun kas/bank wajib dipilih.");
  } else if (cashAccount.type !== "ASSET") {
    errors.push("Akun kas/bank wajib bertipe aset.");
  }
  if (!receivableAccount) {
    errors.push("Akun piutang wajib dipilih.");
  } else if (receivableAccount.type !== "ASSET") {
    errors.push("Akun piutang wajib bertipe aset.");
  }
  if (cashAccount && receivableAccount && cashAccount.id === receivableAccount.id) {
    errors.push("Akun kas/bank dan akun piutang tidak boleh sama.");
  }
  if (!input.draft.reason.trim()) {
    errors.push("Alasan reversal wajib diisi.");
  }

  return errors;
}
