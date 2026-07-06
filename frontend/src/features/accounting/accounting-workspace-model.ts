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
    post: permissions.canPostJournals && journal.status === "DRAFT" && journal.availableActions.includes("POST")
  };
}
