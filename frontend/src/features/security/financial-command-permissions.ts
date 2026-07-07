export const financialCommandPermissions = {
  accountManage: "account.manage",
  periodManage: "period.manage",
  periodClose: "period.close",
  journalCreate: "journal.create",
  journalPost: "journal.post",
  billingGenerate: "billing.generate",
  invoiceIssue: "invoice.issue",
  paymentCounter: "payment.counter",
  paymentRead: "payment.read",
  paymentReverse: "payment.reverse",
  paymentWebhookRead: "payment.webhook.read"
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

export type PaymentCommandPermissionState = {
  canSettleCounterPayments: boolean;
  canReadPayments: boolean;
  canReversePayments: boolean;
  canReadWebhookEvents: boolean;
};

export type FinancialCommandPermissionState = {
  accounting: AccountingCommandPermissionState;
  billing: BillingCommandPermissionState;
  payment: PaymentCommandPermissionState;
  hasAnyFinancialCommand: boolean;
};

export type FinancialCommand = {
  label: string;
  permission: string;
  allowed: boolean;
  risk: "medium" | "high";
};

export type FinancialCommandGroup = {
  title: "Accounting" | "Billing" | "Payment";
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
  const payment = {
    canSettleCounterPayments: authoritySet.has(financialCommandPermissions.paymentCounter),
    canReadPayments: authoritySet.has(financialCommandPermissions.paymentRead),
    canReversePayments: authoritySet.has(financialCommandPermissions.paymentReverse),
    canReadWebhookEvents: authoritySet.has(financialCommandPermissions.paymentWebhookRead)
  };

  return {
    accounting,
    billing,
    payment,
    hasAnyFinancialCommand:
      accounting.canManageAccounts ||
      accounting.canManagePeriods ||
      accounting.canClosePeriods ||
      accounting.canCreateJournals ||
      accounting.canPostJournals ||
      billing.canGenerateBilling ||
      billing.canIssueInvoices ||
      payment.canSettleCounterPayments ||
      payment.canReadPayments ||
      payment.canReversePayments ||
      payment.canReadWebhookEvents
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
    },
    {
      title: "Payment",
      commands: [
        {
          label: "Counter Settlement",
          permission: financialCommandPermissions.paymentCounter,
          allowed: state.payment.canSettleCounterPayments,
          risk: "high"
        },
        {
          label: "Payment Read",
          permission: financialCommandPermissions.paymentRead,
          allowed: state.payment.canReadPayments,
          risk: "medium"
        },
        {
          label: "Reversal Payment",
          permission: financialCommandPermissions.paymentReverse,
          allowed: state.payment.canReversePayments,
          risk: "high"
        },
        {
          label: "Webhook Event Read",
          permission: financialCommandPermissions.paymentWebhookRead,
          allowed: state.payment.canReadWebhookEvents,
          risk: "medium"
        }
      ]
    }
  ] as const;
}
