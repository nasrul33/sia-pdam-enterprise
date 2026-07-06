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

test("visibleFinancialCommandGroups marks individual commands by permission", () => {
  const state = resolveFinancialCommandPermissions([
    financialCommandPermissions.journalCreate,
    financialCommandPermissions.invoiceIssue
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
