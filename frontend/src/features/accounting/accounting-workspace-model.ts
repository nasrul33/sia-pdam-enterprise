import type { AccountingCommandPermissionState } from "@/features/security/financial-command-permissions";
import type { AccountType, JournalStatus, PeriodStatus } from "./accounting-schema";

type AccountSummarySubject = {
  type: AccountType;
};

type PeriodSummarySubject = {
  status: PeriodStatus;
};

type JournalSummarySubject = {
  status: JournalStatus;
};

export type AccountingWorkspaceSummary = {
  assetExpenseAccounts: number;
  revenueLiabilityEquityAccounts: number;
  openPostingPeriods: number;
  lockedPeriods: number;
  draftJournals: number;
  postedJournals: number;
};

export type PeriodWorkflowSubject = {
  status: PeriodStatus;
  availableActions: readonly string[];
};

export type JournalWorkflowSubject = {
  status: JournalStatus;
  availableActions: readonly string[];
  periodAllowsPosting?: boolean;
};

export type ManualJournalDraftLineInput = {
  accountId: string;
  debit: string | number;
  credit: string | number;
};

export type ManualJournalDraftInput = {
  journalNumber: string;
  accountingPeriodId: string;
  description: string;
  reason: string;
  lines: readonly ManualJournalDraftLineInput[];
};

export type ManualJournalDraftSummary = {
  totalDebit: number;
  totalCredit: number;
  isBalanced: boolean;
  hasMinimumLines: boolean;
  hasDistinctAccounts: boolean;
  hasValidLineAmounts: boolean;
};

export type JournalDetailLineSubject = {
  debit: number;
  credit: number;
};

export type JournalDetailLineSummary = {
  lineCount: number;
  totalDebit: number;
  totalCredit: number;
  isBalanced: boolean;
  hasOneSidedLines: boolean;
};

export function summarizeAccountingWorkspace(input: {
  accounts: readonly AccountSummarySubject[];
  periods: readonly PeriodSummarySubject[];
  journals: readonly JournalSummarySubject[];
}): AccountingWorkspaceSummary {
  return {
    assetExpenseAccounts: input.accounts.filter((account) => account.type === "ASSET" || account.type === "EXPENSE").length,
    revenueLiabilityEquityAccounts: input.accounts.filter(
      (account) => account.type === "REVENUE" || account.type === "LIABILITY" || account.type === "EQUITY"
    ).length,
    openPostingPeriods: input.periods.filter((period) => period.status === "OPEN" || period.status === "REOPENED").length,
    lockedPeriods: input.periods.filter((period) => period.status === "LOCKED").length,
    draftJournals: input.journals.filter((journal) => journal.status === "DRAFT").length,
    postedJournals: input.journals.filter((journal) => journal.status === "POSTED").length
  };
}

export function allowedAccountingPeriodWorkflows(
  period: PeriodWorkflowSubject,
  permissions: AccountingCommandPermissionState
) {
  return {
    startClosingReview: permissions.canClosePeriods && period.availableActions.includes("START_CLOSING_REVIEW"),
    lock: permissions.canClosePeriods && period.availableActions.includes("LOCK")
  };
}

export function allowedAccountingJournalWorkflows(
  journal: JournalWorkflowSubject,
  permissions: AccountingCommandPermissionState
) {
  return {
    post:
      permissions.canPostJournals &&
      journal.status === "DRAFT" &&
      journal.availableActions.includes("POST") &&
      journal.periodAllowsPosting !== false
  };
}

function amountToCents(value: string | number): number | null {
  const normalized = String(value).trim().replace(",", ".");
  if (!normalized) {
    return 0;
  }
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) {
    return null;
  }

  const [whole, fraction = ""] = normalized.split(".");
  const cents = Number.parseInt(whole, 10) * 100 + Number.parseInt(fraction.padEnd(2, "0"), 10);
  return Number.isFinite(cents) ? cents : null;
}

function centsToAmount(cents: number): number {
  return cents / 100;
}

export function summarizeManualJournalDraft(lines: readonly ManualJournalDraftLineInput[]): ManualJournalDraftSummary {
  let totalDebitCents = 0;
  let totalCreditCents = 0;
  let hasInvalidAmount = false;
  const accountIds = new Set<string>();

  for (const line of lines) {
    const debit = amountToCents(line.debit);
    const credit = amountToCents(line.credit);
    const accountId = line.accountId.trim();

    if (accountId) {
      accountIds.add(accountId);
    }

    if (debit === null || credit === null) {
      hasInvalidAmount = true;
      continue;
    }

    if ((debit > 0 && credit > 0) || (debit === 0 && credit === 0)) {
      hasInvalidAmount = true;
    }

    totalDebitCents += debit;
    totalCreditCents += credit;
  }

  return {
    totalDebit: centsToAmount(totalDebitCents),
    totalCredit: centsToAmount(totalCreditCents),
    isBalanced: totalDebitCents > 0 && totalDebitCents === totalCreditCents,
    hasMinimumLines: lines.length >= 2,
    hasDistinctAccounts: accountIds.size >= 2,
    hasValidLineAmounts: !hasInvalidAmount
  };
}

export function manualJournalDraftErrors(input: ManualJournalDraftInput): string[] {
  const errors: string[] = [];
  const summary = summarizeManualJournalDraft(input.lines);

  if (!input.journalNumber.trim()) {
    errors.push("Nomor jurnal wajib diisi.");
  }
  if (!input.accountingPeriodId.trim()) {
    errors.push("Periode akuntansi wajib dipilih.");
  }
  if (!input.description.trim()) {
    errors.push("Deskripsi jurnal wajib diisi.");
  }
  if (!input.reason.trim()) {
    errors.push("Alasan audit wajib diisi.");
  }
  if (!summary.hasMinimumLines) {
    errors.push("Jurnal minimal memiliki dua baris.");
  }
  if (!summary.hasDistinctAccounts) {
    errors.push("Jurnal minimal menggunakan dua akun berbeda.");
  }
  if (!summary.hasValidLineAmounts) {
    errors.push("Setiap baris wajib memilih salah satu sisi debit atau kredit saja.");
  }
  if (!summary.isBalanced) {
    errors.push("Total debit wajib sama dengan total kredit dan lebih dari nol.");
  }

  return errors;
}

export function summarizeJournalDetailLines(lines: readonly JournalDetailLineSubject[]): JournalDetailLineSummary {
  const totalDebit = lines.reduce((total, line) => total + line.debit, 0);
  const totalCredit = lines.reduce((total, line) => total + line.credit, 0);

  return {
    lineCount: lines.length,
    totalDebit,
    totalCredit,
    isBalanced: totalDebit > 0 && totalDebit === totalCredit,
    hasOneSidedLines: lines.every((line) => (line.debit > 0 && line.credit === 0) || (line.credit > 0 && line.debit === 0))
  };
}
