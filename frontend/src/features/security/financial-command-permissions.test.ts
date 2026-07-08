import assert from "node:assert/strict";
import { test } from "node:test";
import {
  financialCommandPermissions,
  resolveFinancialCommandPermissions,
  visibleFinancialCommandGroups
} from "./financial-command-permissions.ts";

test("resolveFinancialCommandPermissions maps accounting command authorities", () => {
  assert.deepEqual(
    resolveFinancialCommandPermissions([
      financialCommandPermissions.accountManage,
      financialCommandPermissions.periodClose,
      financialCommandPermissions.journalPost
    ]).accounting,
    {
      canManageAccounts: true,
      canManagePeriods: false,
      canClosePeriods: true,
      canCreateJournals: false,
      canPostJournals: true
    }
  );
});

test("resolveFinancialCommandPermissions maps billing command authorities", () => {
  assert.deepEqual(
    resolveFinancialCommandPermissions([
      financialCommandPermissions.billingGenerate,
      financialCommandPermissions.invoiceIssue
    ]).billing,
    {
      canGenerateBilling: true,
      canIssueInvoices: true
    }
  );
});

test("resolveFinancialCommandPermissions maps payment command authorities", () => {
  assert.deepEqual(
    resolveFinancialCommandPermissions([
      financialCommandPermissions.paymentCounter,
      financialCommandPermissions.paymentRead,
      financialCommandPermissions.paymentReconcile,
      financialCommandPermissions.paymentReconciliationHandoffNote,
      financialCommandPermissions.paymentReconciliationSignoff,
      financialCommandPermissions.paymentWebhookRead
    ]).payment,
    {
      canSettleCounterPayments: true,
      canReadPayments: true,
      canReconcilePayments: true,
      canManageReconciliationHandoffNotes: true,
      canSignOffPaymentReconciliations: true,
      canReversePayments: false,
      canReadWebhookEvents: true
    }
  );
});

test("visibleFinancialCommandGroups marks individual commands by permission", () => {
  const state = resolveFinancialCommandPermissions([
    financialCommandPermissions.journalCreate,
    financialCommandPermissions.invoiceIssue,
    financialCommandPermissions.paymentReconcile,
    financialCommandPermissions.paymentReconciliationHandoffNote,
    financialCommandPermissions.paymentReconciliationSignoff,
    financialCommandPermissions.paymentReverse
  ]);

  assert.deepEqual(
    visibleFinancialCommandGroups(state).map((group) => ({
      title: group.title,
      allowed: group.commands.filter((command) => command.allowed).map((command) => command.permission)
    })),
    [
      {
        title: "Accounting",
        allowed: [financialCommandPermissions.journalCreate]
      },
      {
        title: "Billing",
        allowed: [financialCommandPermissions.invoiceIssue]
      },
      {
        title: "Payment",
        allowed: [
          financialCommandPermissions.paymentReconcile,
          financialCommandPermissions.paymentReconciliationHandoffNote,
          financialCommandPermissions.paymentReconciliationSignoff,
          financialCommandPermissions.paymentReverse
        ]
      }
    ]
  );
});

test("resolveFinancialCommandPermissions exposes aggregate access state", () => {
  assert.equal(resolveFinancialCommandPermissions([]).hasAnyFinancialCommand, false);
  assert.equal(
    resolveFinancialCommandPermissions([financialCommandPermissions.accountManage]).hasAnyFinancialCommand,
    true
  );
});
