import type { Account } from "@/features/accounting/accounting-schema";
import type { PaymentCommandPermissionState } from "@/features/security/financial-command-permissions";
import type {
  ClosedPaymentReconciliationResolutionStatus,
  PaymentReconciliationMatchStatus,
  PaymentReconciliationResolutionStatus,
  PaymentStatus,
  PaymentWebhookStatus
} from "./payment-schema";

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

type WebhookSummarySubject = {
  status: PaymentWebhookStatus;
  errorMessage: string | null;
};

type PaymentSummarySubject = {
  status: PaymentStatus;
  amount: number;
};

type ReconciliationMatchSubject = {
  status: PaymentReconciliationMatchStatus;
  amountVariance: number | null;
};

type ReconciliationSessionItemSubject = {
  matchStatus: PaymentReconciliationMatchStatus;
  resolutionStatus: PaymentReconciliationResolutionStatus;
  amountVariance: number | null;
};

export type PaymentWorkspaceSummary = {
  receivedEvents: number;
  processedEvents: number;
  failedEvents: number;
  ignoredEvents: number;
  unresolvedFailures: number;
};

export type PaymentListSummary = {
  settledPayments: number;
  reversedPayments: number;
  pendingOrFailedPayments: number;
  totalSettledAmount: number;
  totalReversedAmount: number;
  netCashImpact: number;
};

export type BankStatementCsvRow = {
  statementReference: string;
  amount: number;
  transactedAt: string;
  channel: string | null;
};

export type ParsedBankStatementCsv = {
  rows: BankStatementCsvRow[];
  errors: string[];
};

export type PaymentReconciliationMatchSummary = {
  exactMatches: number;
  probableMatches: number;
  amountVariances: number;
  reversedPayments: number;
  multipleCandidates: number;
  unmatchedRows: number;
  totalVariance: number;
};

export type PaymentReconciliationSessionItemSummary = PaymentReconciliationMatchSummary & {
  openItems: number;
  acceptedItems: number;
  resolvedItems: number;
  ignoredItems: number;
  exceptionItems: number;
};

export type PaymentReconciliationExportDraft = {
  status: PaymentStatus | "ALL";
  paidAtFrom: string;
  paidAtTo: string;
};

export type PaymentReconciliationResolutionDraft = {
  itemId: string;
  resolutionStatus: PaymentReconciliationResolutionStatus;
  reason: string;
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

export function summarizePaymentList(payments: readonly PaymentSummarySubject[]): PaymentListSummary {
  const settledPayments = payments.filter((payment) => payment.status === "SETTLED");
  const reversedPayments = payments.filter((payment) => payment.status === "REVERSED");
  const pendingOrFailedPayments = payments.filter((payment) => payment.status === "PENDING" || payment.status === "FAILED");
  const totalSettledAmount = roundMoney(settledPayments.reduce((total, payment) => total + payment.amount, 0));
  const totalReversedAmount = roundMoney(reversedPayments.reduce((total, payment) => total + payment.amount, 0));

  return {
    settledPayments: settledPayments.length,
    reversedPayments: reversedPayments.length,
    pendingOrFailedPayments: pendingOrFailedPayments.length,
    totalSettledAmount,
    totalReversedAmount,
    netCashImpact: roundMoney(totalSettledAmount - totalReversedAmount)
  };
}

export function canSettleCounterPayment(permissions: PaymentCommandPermissionState): boolean {
  return permissions.canSettleCounterPayments;
}

export function canReadPayments(permissions: PaymentCommandPermissionState): boolean {
  return permissions.canReadPayments;
}

export function canReconcilePayments(permissions: PaymentCommandPermissionState): boolean {
  return permissions.canReconcilePayments;
}

export function canReversePayment(permissions: PaymentCommandPermissionState): boolean {
  return permissions.canReversePayments;
}

function roundMoney(value: number): number {
  return Math.round(value * 100) / 100;
}

export function summarizeReconciliationMatches(
  matches: readonly ReconciliationMatchSubject[]
): PaymentReconciliationMatchSummary {
  const totalVariance = matches.reduce((total, match) => total + (match.amountVariance ?? 0), 0);

  return {
    exactMatches: matches.filter((match) => match.status === "EXACT_MATCH").length,
    probableMatches: matches.filter((match) => match.status === "PROBABLE_MATCH").length,
    amountVariances: matches.filter((match) => match.status === "AMOUNT_VARIANCE").length,
    reversedPayments: matches.filter((match) => match.status === "REVERSED_PAYMENT").length,
    multipleCandidates: matches.filter((match) => match.status === "MULTIPLE_CANDIDATES").length,
    unmatchedRows: matches.filter((match) => match.status === "UNMATCHED").length,
    totalVariance: roundMoney(totalVariance)
  };
}

export function summarizeReconciliationSessionItems(
  items: readonly ReconciliationSessionItemSubject[]
): PaymentReconciliationSessionItemSummary {
  return {
    ...summarizeReconciliationMatches(items.map((item) => ({ status: item.matchStatus, amountVariance: item.amountVariance }))),
    openItems: items.filter((item) => item.resolutionStatus === "OPEN").length,
    acceptedItems: items.filter((item) => item.resolutionStatus === "ACCEPTED").length,
    resolvedItems: items.filter((item) => item.resolutionStatus === "RESOLVED").length,
    ignoredItems: items.filter((item) => item.resolutionStatus === "IGNORED").length,
    exceptionItems: items.filter((item) => item.matchStatus !== "EXACT_MATCH" && item.matchStatus !== "PROBABLE_MATCH").length
  };
}

export function paymentReconciliationExportErrors(draft: PaymentReconciliationExportDraft): string[] {
  const errors: string[] = [];
  const fromTime = parseOptionalDateTime(draft.paidAtFrom);
  const toTime = parseOptionalDateTime(draft.paidAtTo);

  if (draft.status === "PENDING" || draft.status === "FAILED") {
    errors.push("Export rekonsiliasi hanya mendukung payment Settled atau Reversed.");
  }
  if (draft.paidAtFrom.trim() && fromTime === null) {
    errors.push("Tanggal awal export wajib valid.");
  }
  if (draft.paidAtTo.trim() && toTime === null) {
    errors.push("Tanggal akhir export wajib valid.");
  }
  if (fromTime !== null && toTime !== null && toTime < fromTime) {
    errors.push("Tanggal akhir export tidak boleh sebelum tanggal awal.");
  }

  return errors;
}

export function reconciliationResolutionErrors(draft: PaymentReconciliationResolutionDraft): string[] {
  const errors: string[] = [];
  const itemId = draft.itemId.trim();
  const reason = draft.reason.trim();

  if (!uuidPattern.test(itemId)) {
    errors.push("Item rekonsiliasi wajib dipilih.");
  }
  if (draft.resolutionStatus === "OPEN") {
    errors.push("Status resolution wajib menutup item.");
  }
  if (!reason) {
    errors.push("Alasan resolution wajib diisi.");
  }
  if (reason.length > 500) {
    errors.push("Alasan resolution maksimal 500 karakter.");
  }

  return errors;
}

export function reconciliationCompletionErrors(input: { reason: string; openItems: number }): string[] {
  const errors: string[] = [];
  const reason = input.reason.trim();

  if (input.openItems > 0) {
    errors.push("Semua item rekonsiliasi wajib ditutup sebelum session diselesaikan.");
  }
  if (!reason) {
    errors.push("Alasan completion wajib diisi.");
  }
  if (reason.length > 500) {
    errors.push("Alasan completion maksimal 500 karakter.");
  }

  return errors;
}

export function toClosedResolutionStatus(
  status: PaymentReconciliationResolutionStatus
): ClosedPaymentReconciliationResolutionStatus | null {
  return status === "OPEN" ? null : status;
}

export function parseBankStatementCsv(value: string): ParsedBankStatementCsv {
  const errors: string[] = [];
  const lines = value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);

  if (lines.length === 0) {
    return { rows: [], errors: ["CSV bank statement wajib berisi minimal satu baris."] };
  }

  const delimiter = lines[0].includes(";") ? ";" : ",";
  const firstColumns = parseCsvLine(lines[0], delimiter);
  const headerMap = headerIndexes(firstColumns);
  const hasHeader = headerMap !== null;
  const dataLines = hasHeader ? lines.slice(1) : lines;
  const rows: BankStatementCsvRow[] = [];

  dataLines.forEach((line, index) => {
    const lineNumber = hasHeader ? index + 2 : index + 1;
    const columns = parseCsvLine(line, delimiter);
    const reference = columnValue(columns, headerMap, "reference", 0);
    const amountText = columnValue(columns, headerMap, "amount", 1);
    const transactedAtText = columnValue(columns, headerMap, "transactedAt", 2);
    const channel = optionalCsvValue(columnValue(columns, headerMap, "channel", 3));
    const amount = parseMoneyInput(amountText);
    const transactedAt = parseOptionalDateTime(transactedAtText);

    if (!reference.trim()) {
      errors.push(`Baris ${lineNumber}: referensi bank wajib diisi.`);
    }
    if (amount === null) {
      errors.push(`Baris ${lineNumber}: nominal bank wajib lebih besar dari nol.`);
    }
    if (transactedAt === null) {
      errors.push(`Baris ${lineNumber}: tanggal transaksi bank wajib valid.`);
    }

    if (reference.trim() && amount !== null && transactedAt !== null) {
      rows.push({
        statementReference: reference.trim(),
        amount,
        transactedAt: new Date(transactedAt).toISOString(),
        channel
      });
    }
  });

  if (rows.length > 200) {
    errors.push("CSV bank statement maksimal 200 baris per proses match.");
  }

  return { rows, errors };
}

function parseCsvLine(line: string, delimiter: "," | ";"): string[] {
  const values: string[] = [];
  let current = "";
  let quoted = false;

  for (let index = 0; index < line.length; index++) {
    const character = line[index];
    const next = line[index + 1];

    if (character === '"' && quoted && next === '"') {
      current += '"';
      index += 1;
    } else if (character === '"') {
      quoted = !quoted;
    } else if (character === delimiter && !quoted) {
      values.push(current.trim());
      current = "";
    } else {
      current += character;
    }
  }

  values.push(current.trim());
  return values;
}

function headerIndexes(columns: readonly string[]): Record<"reference" | "amount" | "transactedAt" | "channel", number> | null {
  const normalized = columns.map((column) => normalizeHeader(column));
  const reference = findHeader(normalized, ["reference", "statementreference", "statementref", "ref", "externalreference"]);
  const amount = findHeader(normalized, ["amount", "nominal", "nilai"]);
  const transactedAt = findHeader(normalized, ["transactedat", "transactiondate", "tanggal", "date", "paidat"]);
  const channel = findHeader(normalized, ["channel", "kanal"]);

  if (reference === -1 || amount === -1 || transactedAt === -1) {
    return null;
  }

  return { reference, amount, transactedAt, channel };
}

function normalizeHeader(value: string): string {
  return value.trim().toLowerCase().replace(/[^a-z0-9]/g, "");
}

function findHeader(headers: readonly string[], candidates: readonly string[]): number {
  return headers.findIndex((header) => candidates.includes(header));
}

function columnValue(
  columns: readonly string[],
  headers: Record<"reference" | "amount" | "transactedAt" | "channel", number> | null,
  key: "reference" | "amount" | "transactedAt" | "channel",
  fallbackIndex: number
): string {
  const index = headers?.[key] ?? fallbackIndex;
  return columns[index] ?? "";
}

function optionalCsvValue(value: string): string | null {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}

function parseOptionalDateTime(value: string): number | null {
  const normalized = value.trim();
  if (!normalized) {
    return null;
  }
  const time = new Date(normalized).getTime();
  return Number.isNaN(time) ? null : time;
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
