import assert from "node:assert/strict";
import { test } from "node:test";
import { financialCommandPermissions, resolveFinancialCommandPermissions } from "../security/financial-command-permissions.ts";
import {
  allowedAccountingJournalWorkflows,
  allowedAccountingPeriodWorkflows,
  summarizeAccountingWorkspace
} from "./accounting-workspace-model.ts";

const fullAccountingPermissions = resolveFinancialCommandPermissions([
  financialCommandPermissions.accountManage,
  financialCommandPermissions.periodManage,
  financialCommandPermissions.periodClose,
  financialCommandPermissions.journalCreate,
  financialCommandPermissions.journalPost
]).accounting;

test("summarizeAccountingWorkspace summarizes current page accounting controls", () => {
  assert.deepEqual(
    summarizeAccountingWorkspace({
      accounts: [
        { type: "ASSET" },
        { type: "REVENUE" },
        { type: "EXPENSE" }
      ],
      periods: [
        { status: "OPEN" },
        { status: "LOCKED" },
        { status: "REOPENED" }
      ],
      journals: [
        { status: "DRAFT" },
        { status: "POSTED" },
        { status: "POSTED" }
      ]
    }),
    {
      assetExpenseAccounts: 2,
      revenueLiabilityEquityAccounts: 1,
      openPostingPeriods: 2,
      lockedPeriods: 1,
      draftJournals: 1,
      postedJournals: 2
    }
  );
});

test("allowedAccountingPeriodWorkflows requires period.close authority and backend available action", () => {
  assert.deepEqual(
    allowedAccountingPeriodWorkflows(
      { status: "OPEN", availableActions: ["START_CLOSING_REVIEW"] },
      fullAccountingPermissions
    ),
    { startClosingReview: true, lock: false }
  );

  assert.deepEqual(
    allowedAccountingPeriodWorkflows(
      { status: "CLOSING_REVIEW", availableActions: ["LOCK"] },
      resolveFinancialCommandPermissions([]).accounting
    ),
    { startClosingReview: false, lock: false }
  );
});

test("allowedAccountingJournalWorkflows requires journal.post authority and draft backend action", () => {
  assert.deepEqual(
    allowedAccountingJournalWorkflows({ status: "DRAFT", availableActions: ["POST", "VOID"] }, fullAccountingPermissions),
    { post: true }
  );

  assert.deepEqual(
    allowedAccountingJournalWorkflows({ status: "POSTED", availableActions: ["REVERSE"] }, fullAccountingPermissions),
    { post: false }
  );
});
