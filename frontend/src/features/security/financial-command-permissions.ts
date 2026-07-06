export const financialCommandPermissions = {
  accountManage: "account.manage",
  periodManage: "period.manage",
  periodClose: "period.close",
  journalCreate: "journal.create",
  journalPost: "journal.post",
  billingGenerate: "billing.generate",
  invoiceIssue: "invoice.issue"
} as const;

export type AccountingCommandPermissionState = {
  canManageAccounts: boolean;
  canManagePeriods: boolean;
  canClosePeriods: boolean;
  canCreateJournals: boolean;
  canPostJournals: boolean;
};

export type BillingCommandPermissionState = {
  canGenerateBilling: boolean;
  canIssueInvoices: boolean;
};

export type FinancialCommandPermissionState = {
  accounting: AccountingCommandPermissionState;
  billing: BillingCommandPermissionState;
  hasAnyFinancialCommand: boolean;
};

export type FinancialCommand = {
  label: string;
  permission: string;
  allowed: boolean;
  risk: "medium" | "high";
};

export type FinancialCommandGroup = {
  title: "Accounting" | "Billing";
  commands: readonly FinancialCommand[];
};

export function resolveFinancialCommandPermissions(authorities: readonly string[]): FinancialCommandPermissionState {
  const authoritySet = new Set(authorities);
  const accounting = {
    canManageAccounts: authoritySet.has(financialCommandPermissions.accountManage),
    canManagePeriods: authoritySet.has(financialCommandPermissions.periodManage),
    canClosePeriods: authoritySet.has(financialCommandPermissions.periodClose),
    canCreateJournals: authoritySet.has(financialCommandPermissions.journalCreate),
    canPostJournals: authoritySet.has(financialCommandPermissions.journalPost)
  };
  const billing = {
    canGenerateBilling: authoritySet.has(financialCommandPermissions.billingGenerate),
    canIssueInvoices: authoritySet.has(financialCommandPermissions.invoiceIssue)
  };

  return {
    accounting,
    billing,
    hasAnyFinancialCommand:
      accounting.canManageAccounts ||
      accounting.canManagePeriods ||
      accounting.canClosePeriods ||
      accounting.canCreateJournals ||
      accounting.canPostJournals ||
      billing.canGenerateBilling ||
      billing.canIssueInvoices
  };
}

export function visibleFinancialCommandGroups(state: FinancialCommandPermissionState): readonly FinancialCommandGroup[] {
  return [
    {
      title: "Accounting",
      commands: [
        {
          label: "Kelola CoA",
          permission: financialCommandPermissions.accountManage,
          allowed: state.accounting.canManageAccounts,
          risk: "medium"
        },
        {
          label: "Kelola Periode",
          permission: financialCommandPermissions.periodManage,
          allowed: state.accounting.canManagePeriods,
          risk: "medium"
        },
        {
          label: "Tutup Periode",
          permission: financialCommandPermissions.periodClose,
          allowed: state.accounting.canClosePeriods,
          risk: "high"
        },
        {
          label: "Buat Jurnal",
          permission: financialCommandPermissions.journalCreate,
          allowed: state.accounting.canCreateJournals,
          risk: "medium"
        },
        {
          label: "Posting Jurnal",
          permission: financialCommandPermissions.journalPost,
          allowed: state.accounting.canPostJournals,
          risk: "high"
        }
      ]
    },
    {
      title: "Billing",
      commands: [
        {
          label: "Generate Billing",
          permission: financialCommandPermissions.billingGenerate,
          allowed: state.billing.canGenerateBilling,
          risk: "medium"
        },
        {
          label: "Issue Invoice",
          permission: financialCommandPermissions.invoiceIssue,
          allowed: state.billing.canIssueInvoices,
          risk: "high"
        }
      ]
    }
  ] as const;
}
