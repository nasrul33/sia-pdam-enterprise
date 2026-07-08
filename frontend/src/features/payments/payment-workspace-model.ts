import type { Account } from "@/features/accounting/accounting-schema";
import type { PaymentCommandPermissionState } from "@/features/security/financial-command-permissions";
import type {
  ClosedPaymentReconciliationResolutionStatus,
  PaymentReconciliationMatchStatus,
  PaymentReconciliationResolutionStatus,
  PaymentReconciliationReviewStatus,
  PaymentReconciliationSessionStatus,
  PaymentStatus,
  PaymentWebhookStatus
} from "./payment-schema";

export const reconciliationSignOffSlaDays = 3;

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
  adjustmentJournalEntryId?: string | null;
};

type ReconciliationReviewRegisterSubject = {
  reviewStatus: PaymentReconciliationReviewStatus;
  exceptionItems: number;
  adjustedItems: number;
  pendingSignOffAgeDays: number;
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

export const bankStatementImportProfileValues = ["STANDARD", "BANK_MUTATION", "PAYMENT_GATEWAY"] as const;

export type BankStatementImportProfile = (typeof bankStatementImportProfileValues)[number];

export type BankStatementImportProfileOption = {
  value: BankStatementImportProfile;
  label: string;
  description: string;
};

type BankStatementImportField = "reference" | "amount" | "transactedAt" | "channel";

type BankStatementImportFieldDefinition = {
  canonical: string;
  aliases: readonly string[];
  required: boolean;
};

type BankStatementImportProfileDefinition = {
  value: BankStatementImportProfile;
  label: string;
  description: string;
  delimiter: "," | ";";
  requiresHeader: boolean;
  amountErrorLabel: string;
  fields: Record<BankStatementImportField, BankStatementImportFieldDefinition>;
  templateHeaders: readonly string[];
  templateExample: readonly string[];
};

type BankStatementHeaderMap = Partial<Record<BankStatementImportField, number>>;

const bankStatementImportProfileDefinitions: Record<BankStatementImportProfile, BankStatementImportProfileDefinition> = {
  STANDARD: {
    value: "STANDARD",
    label: "Standard PDAM",
    description: "Kolom reference, amount, transacted_at, dan channel opsional.",
    delimiter: ";",
    requiresHeader: false,
    amountErrorLabel: "nominal bank",
    fields: {
      reference: {
        canonical: "reference",
        aliases: ["reference", "statementreference", "statementref", "ref", "externalreference"],
        required: true
      },
      amount: {
        canonical: "amount",
        aliases: ["amount", "nominal", "nilai"],
        required: true
      },
      transactedAt: {
        canonical: "transacted_at",
        aliases: ["transactedat", "transactiondate", "tanggal", "date", "paidat"],
        required: true
      },
      channel: {
        canonical: "channel",
        aliases: ["channel", "kanal"],
        required: false
      }
    },
    templateHeaders: ["reference", "amount", "transacted_at", "channel"],
    templateExample: ["BANK-20260731-0001", "100000.00", "2026-07-31T12:00:00Z", "COUNTER"]
  },
  BANK_MUTATION: {
    value: "BANK_MUTATION",
    label: "Mutasi Bank Kredit",
    description: "Template mutasi rekening dengan kolom kredit sebagai nominal kas masuk.",
    delimiter: ";",
    requiresHeader: true,
    amountErrorLabel: "nominal kredit masuk",
    fields: {
      reference: {
        canonical: "reference",
        aliases: ["reference", "noref", "nomorreferensi", "ref", "mutasireference", "transactionid"],
        required: true
      },
      amount: {
        canonical: "credit",
        aliases: ["credit", "kredit", "creditamount", "mutasikredit", "amountin"],
        required: true
      },
      transactedAt: {
        canonical: "transaction_date",
        aliases: ["transactiondate", "tanggal", "tgltransaksi", "date", "transactedat"],
        required: true
      },
      channel: {
        canonical: "channel",
        aliases: ["channel", "kanal", "source"],
        required: false
      }
    },
    templateHeaders: ["transaction_date", "reference", "description", "debit", "credit", "channel"],
    templateExample: ["2026-07-31T12:00:00Z", "BANK-20260731-0001", "Setoran loket", "", "100000.00", "COUNTER"]
  },
  PAYMENT_GATEWAY: {
    value: "PAYMENT_GATEWAY",
    label: "Payment Gateway",
    description: "Template settlement provider dengan external reference, amount, timestamp, dan channel.",
    delimiter: ",",
    requiresHeader: true,
    amountErrorLabel: "nominal settlement",
    fields: {
      reference: {
        canonical: "external_reference",
        aliases: ["externalreference", "externalref", "reference", "paymentreference", "transactionid"],
        required: true
      },
      amount: {
        canonical: "paid_amount",
        aliases: ["paidamount", "amount", "grossamount", "settlementamount", "nominal"],
        required: true
      },
      transactedAt: {
        canonical: "paid_at",
        aliases: ["paidat", "settledat", "transactiondate", "transactedat", "tanggal"],
        required: true
      },
      channel: {
        canonical: "channel",
        aliases: ["channel", "paymentchannel", "kanal"],
        required: false
      }
    },
    templateHeaders: ["external_reference", "paid_amount", "paid_at", "channel"],
    templateExample: ["PG-20260731-0001", "100000.00", "2026-07-31T12:00:00Z", "MOBILE"]
  }
};

export const bankStatementImportProfileOptions: BankStatementImportProfileOption[] = bankStatementImportProfileValues.map((value) => {
  const definition = bankStatementImportProfileDefinitions[value];
  return {
    value,
    label: definition.label,
    description: definition.description
  };
});

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
  adjustedItems: number;
};

export type PaymentReconciliationReviewRegisterSummary = {
  totalEvidence: number;
  pendingSignOff: number;
  signedOff: number;
  overduePendingSignOff: number;
  exceptionItems: number;
  adjustedItems: number;
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

export type PaymentReconciliationAdjustmentDraft = {
  itemId: string;
  period: string;
  amount: string;
  debitAccountId: string;
  creditAccountId: string;
  reason: string;
};

export type PaymentReconciliationEvidenceExportDraft = {
  sessionId: string | null;
  sessionStatus: "OPEN" | "COMPLETED" | "CANCELLED" | null;
};

export type PaymentReconciliationSignOffDraft = {
  reason: string;
  sessionStatus: PaymentReconciliationSessionStatus | null;
  signedOffAt: string | null;
  actor: string | null;
  createdBy: string | null;
  completedBy: string | null;
};

export type PaymentReconciliationReviewRegisterFilterDraft = {
  signOffStatus: PaymentReconciliationReviewStatus | "ALL";
  completedFrom: string;
  completedTo: string;
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

export function canSignOffPaymentReconciliations(permissions: PaymentCommandPermissionState): boolean {
  return permissions.canSignOffPaymentReconciliations;
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
    exceptionItems: items.filter((item) => item.matchStatus !== "EXACT_MATCH" && item.matchStatus !== "PROBABLE_MATCH").length,
    adjustedItems: items.filter((item) => Boolean(item.adjustmentJournalEntryId)).length
  };
}

export function summarizeReconciliationReviewRegister(
  entries: readonly ReconciliationReviewRegisterSubject[]
): PaymentReconciliationReviewRegisterSummary {
  return {
    totalEvidence: entries.length,
    pendingSignOff: entries.filter((entry) => entry.reviewStatus === "PENDING_SIGN_OFF").length,
    signedOff: entries.filter((entry) => entry.reviewStatus === "SIGNED_OFF").length,
    overduePendingSignOff: entries.filter((entry) =>
      entry.reviewStatus === "PENDING_SIGN_OFF" && entry.pendingSignOffAgeDays >= reconciliationSignOffSlaDays
    ).length,
    exceptionItems: entries.reduce((total, entry) => total + entry.exceptionItems, 0),
    adjustedItems: entries.reduce((total, entry) => total + entry.adjustedItems, 0)
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

export function reconciliationAdjustmentErrors(input: {
  draft: PaymentReconciliationAdjustmentDraft;
  accounts: readonly Account[];
}): string[] {
  const errors: string[] = [];
  const itemId = input.draft.itemId.trim();
  const period = input.draft.period.trim();
  const amount = parseMoneyInput(input.draft.amount);
  const debitAccount = input.accounts.find((account) => account.id === input.draft.debitAccountId);
  const creditAccount = input.accounts.find((account) => account.id === input.draft.creditAccountId);
  const reason = input.draft.reason.trim();

  if (!uuidPattern.test(itemId)) {
    errors.push("Item exception wajib dipilih.");
  }
  if (!/^\d{4}-\d{2}$/.test(period)) {
    errors.push("Periode adjustment wajib format YYYY-MM.");
  }
  if (amount === null) {
    errors.push("Nominal adjustment wajib lebih besar dari nol.");
  }
  if (!debitAccount) {
    errors.push("Akun debit adjustment wajib dipilih.");
  }
  if (!creditAccount) {
    errors.push("Akun kredit adjustment wajib dipilih.");
  }
  if (debitAccount && creditAccount && debitAccount.id === creditAccount.id) {
    errors.push("Akun debit dan kredit adjustment tidak boleh sama.");
  }
  if (!reason) {
    errors.push("Alasan adjustment wajib diisi.");
  }
  if (reason.length > 500) {
    errors.push("Alasan adjustment maksimal 500 karakter.");
  }

  return errors;
}

export function reconciliationEvidenceExportErrors(draft: PaymentReconciliationEvidenceExportDraft): string[] {
  const errors: string[] = [];

  if (!draft.sessionId || !uuidPattern.test(draft.sessionId)) {
    errors.push("Session rekonsiliasi wajib dipilih.");
  }
  if (draft.sessionStatus !== "COMPLETED") {
    errors.push("Evidence report hanya tersedia untuk session completed.");
  }

  return errors;
}

export function reconciliationSignOffErrors(draft: PaymentReconciliationSignOffDraft): string[] {
  const errors: string[] = [];
  const reason = draft.reason.trim();
  const actor = normalizeActor(draft.actor);
  const createdBy = normalizeActor(draft.createdBy);
  const completedBy = normalizeActor(draft.completedBy);

  if (draft.sessionStatus !== "COMPLETED") {
    errors.push("Sign-off hanya tersedia untuk session completed.");
  }
  if (draft.signedOffAt) {
    errors.push("Evidence rekonsiliasi sudah memiliki sign-off.");
  }
  if (!actor) {
    errors.push("Actor sign-off wajib tersedia.");
  }
  if (actor && (actor === createdBy || actor === completedBy)) {
    errors.push("Actor sign-off harus berbeda dari pembuat dan penyelesai session.");
  }
  if (!reason) {
    errors.push("Alasan sign-off wajib diisi.");
  }
  if (reason.length > 500) {
    errors.push("Alasan sign-off maksimal 500 karakter.");
  }

  return errors;
}

export function reconciliationReviewRegisterFilterErrors(
  draft: PaymentReconciliationReviewRegisterFilterDraft
): string[] {
  const errors: string[] = [];
  const fromTime = parseOptionalDateTime(draft.completedFrom);
  const toTime = parseOptionalDateTime(draft.completedTo);

  if (draft.completedFrom.trim() && fromTime === null) {
    errors.push("Tanggal awal review wajib valid.");
  }
  if (draft.completedTo.trim() && toTime === null) {
    errors.push("Tanggal akhir review wajib valid.");
  }
  if (fromTime !== null && toTime !== null && toTime < fromTime) {
    errors.push("Tanggal akhir review tidak boleh sebelum tanggal awal.");
  }

  return errors;
}

export function reconciliationReviewRegisterExportFilename(
  draft: PaymentReconciliationReviewRegisterFilterDraft
): string {
  const statusSegment = draft.signOffStatus.toLowerCase().replaceAll("_", "-");
  const fromSegment = filenameDateSegment(draft.completedFrom);
  const toSegment = filenameDateSegment(draft.completedTo);
  return `payment-reconciliation-review-register-${statusSegment}-${fromSegment}-${toSegment}.csv`;
}

export function toClosedResolutionStatus(
  status: PaymentReconciliationResolutionStatus
): ClosedPaymentReconciliationResolutionStatus | null {
  return status === "OPEN" ? null : status;
}

export function parseBankStatementCsv(value: string): ParsedBankStatementCsv {
  return parseBankStatementImport(value, "STANDARD");
}

export function bankStatementImportTemplate(profile: BankStatementImportProfile): string {
  const definition = bankStatementImportProfileDefinitions[profile];
  return [
    definition.templateHeaders.join(definition.delimiter),
    definition.templateExample.join(definition.delimiter)
  ].join("\n");
}

export function parseBankStatementImport(
  value: string,
  profile: BankStatementImportProfile
): ParsedBankStatementCsv {
  const definition = bankStatementImportProfileDefinitions[profile];
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
  const headerResult = headerIndexes(firstColumns, definition);
  const hasHeader = headerResult.missingRequiredFields.length === 0 || definition.requiresHeader;
  const headerMap = headerResult.indexes;
  if (definition.requiresHeader && headerResult.missingRequiredFields.length > 0) {
    errors.push(
      `Template ${profile} wajib memiliki kolom: ${headerResult.missingRequiredFields.join(", ")}.`
    );
  }
  const dataLines = hasHeader ? lines.slice(1) : lines;
  const useFallbackColumns = !definition.requiresHeader && headerResult.missingRequiredFields.length > 0;
  const rows: BankStatementCsvRow[] = [];

  dataLines.forEach((line, index) => {
    const lineNumber = hasHeader ? index + 2 : index + 1;
    const columns = parseCsvLine(line, delimiter);
    const reference = columnValue(columns, headerMap, "reference", 0, useFallbackColumns);
    const amountText = columnValue(columns, headerMap, "amount", 1, useFallbackColumns);
    const transactedAtText = columnValue(columns, headerMap, "transactedAt", 2, useFallbackColumns);
    const channel = optionalCsvValue(columnValue(columns, headerMap, "channel", 3, useFallbackColumns));
    const amount = parseMoneyInput(amountText);
    const transactedAt = parseOptionalDateTime(transactedAtText);

    if (!reference.trim()) {
      errors.push(`Baris ${lineNumber}: referensi bank wajib diisi.`);
    }
    if (amount === null) {
      errors.push(`Baris ${lineNumber}: ${definition.amountErrorLabel} wajib lebih besar dari nol.`);
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

function headerIndexes(
  columns: readonly string[],
  definition: BankStatementImportProfileDefinition
): { indexes: BankStatementHeaderMap; missingRequiredFields: string[] } {
  const normalized = columns.map((column) => normalizeHeader(column));
  const indexes: BankStatementHeaderMap = {};
  const missingRequiredFields: string[] = [];

  bankStatementImportFields().forEach((field) => {
    const fieldDefinition = definition.fields[field];
    const index = findHeader(normalized, fieldDefinition.aliases);
    if (index !== -1) {
      indexes[field] = index;
    } else if (fieldDefinition.required) {
      missingRequiredFields.push(fieldDefinition.canonical);
    }
  });

  return { indexes, missingRequiredFields };
}

function bankStatementImportFields(): BankStatementImportField[] {
  return ["reference", "amount", "transactedAt", "channel"];
}

function normalizeHeader(value: string): string {
  return value.trim().toLowerCase().replace(/[^a-z0-9]/g, "");
}

function normalizeActor(value: string | null): string {
  return value?.trim().toLowerCase() ?? "";
}

function filenameDateSegment(value: string): string {
  const normalized = value.trim();
  if (!normalized) {
    return "all";
  }
  const datePrefix = normalized.match(/^(\d{4}-\d{2}-\d{2})/);
  if (datePrefix) {
    return datePrefix[1];
  }
  const time = new Date(normalized).getTime();
  if (Number.isNaN(time)) {
    return "invalid";
  }
  return new Date(time).toISOString().slice(0, 10);
}

function findHeader(headers: readonly string[], candidates: readonly string[]): number {
  return headers.findIndex((header) => candidates.includes(header));
}

function columnValue(
  columns: readonly string[],
  headers: BankStatementHeaderMap,
  key: BankStatementImportField,
  fallbackIndex: number,
  useFallbackColumns: boolean
): string {
  const mappedIndex = headers[key];
  if (mappedIndex !== undefined) {
    return columns[mappedIndex] ?? "";
  }
  if (!useFallbackColumns) {
    return "";
  }
  const index = fallbackIndex;
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

  const localDateMatch = normalized.match(/^(\d{1,2})[/-](\d{1,2})[/-](\d{4})(?:[ T](\d{1,2}):(\d{2})(?::(\d{2}))?)?$/);
  if (localDateMatch) {
    const [, dayText, monthText, yearText, hourText = "0", minuteText = "0", secondText = "0"] = localDateMatch;
    const year = Number(yearText);
    const month = Number(monthText);
    const day = Number(dayText);
    const hour = Number(hourText);
    const minute = Number(minuteText);
    const second = Number(secondText);
    const time = Date.UTC(year, month - 1, day, hour, minute, second);
    const date = new Date(time);
    if (
      date.getUTCFullYear() === year &&
      date.getUTCMonth() === month - 1 &&
      date.getUTCDate() === day &&
      date.getUTCHours() === hour &&
      date.getUTCMinutes() === minute &&
      date.getUTCSeconds() === second
    ) {
      return time;
    }
    return null;
  }

  const time = new Date(normalized).getTime();
  return Number.isNaN(time) ? null : time;
}

export function parseMoneyInput(value: string): number | null {
  const normalized = normalizeMoneyInput(value);
  if (!normalized) {
    return null;
  }

  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null;
  }

  return Math.round(parsed * 100) / 100;
}

function normalizeMoneyInput(value: string): string {
  const cleaned = value
    .trim()
    .replace(/\s/g, "")
    .replace(/^Rp\.?/i, "")
    .replace(/[^0-9,.-]/g, "");
  if (!cleaned) {
    return "";
  }

  const lastComma = cleaned.lastIndexOf(",");
  const lastDot = cleaned.lastIndexOf(".");
  if (lastComma !== -1 && lastDot !== -1) {
    const decimalSeparator = lastComma > lastDot ? "," : ".";
    const thousandsSeparator = decimalSeparator === "," ? "." : ",";
    return cleaned
      .replaceAll(thousandsSeparator, "")
      .replace(decimalSeparator, ".");
  }

  if (lastComma !== -1) {
    return normalizeSingleSeparatorMoney(cleaned, ",");
  }
  if (lastDot !== -1) {
    return normalizeSingleSeparatorMoney(cleaned, ".");
  }

  return cleaned;
}

function normalizeSingleSeparatorMoney(value: string, separator: "," | "."): string {
  const parts = value.split(separator);
  const lastPart = parts[parts.length - 1] ?? "";

  if (parts.length === 2) {
    const [integerPart = "", fractionalPart = ""] = parts;
    if (fractionalPart.length === 3 && integerPart.length <= 3) {
      return `${integerPart}${fractionalPart}`;
    }
    return `${integerPart}.${fractionalPart}`;
  }

  if (lastPart.length > 0 && lastPart.length <= 2) {
    return `${parts.slice(0, -1).join("")}.${lastPart}`;
  }

  return parts.join("");
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
