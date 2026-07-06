import assert from "node:assert/strict";
import { test } from "node:test";
import { financialCommandPermissions, resolveFinancialCommandPermissions } from "../security/financial-command-permissions.ts";
import {
  allowedAccountingJournalWorkflows,
  allowedAccountingPeriodWorkflows,
  manualJournalDraftErrors,
  summarizeManualJournalDraft,
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
    allowedAccountingJournalWorkflows(
      { status: "DRAFT", availableActions: ["POST", "VOID"], periodAllowsPosting: true },
      fullAccountingPermissions
    ),
    { post: true }
  );

  assert.deepEqual(
    allowedAccountingJournalWorkflows({ status: "POSTED", availableActions: ["REVERSE"] }, fullAccountingPermissions),
    { post: false }
  );

  assert.deepEqual(
    allowedAccountingJournalWorkflows(
      { status: "DRAFT", availableActions: ["POST"], periodAllowsPosting: false },
      fullAccountingPermissions
    ),
    { post: false }
  );
});

test("summarizeManualJournalDraft validates balanced debit credit lines", () => {
  assert.deepEqual(
    summarizeManualJournalDraft([
      { accountId: "account-a", debit: "1500.25", credit: "" },
      { accountId: "account-b", debit: "", credit: "1500.25" }
    ]),
    {
      totalDebit: 1500.25,
      totalCredit: 1500.25,
      isBalanced: true,
      hasMinimumLines: true,
      hasDistinctAccounts: true,
      hasValidLineAmounts: true
    }
  );
});

test("manualJournalDraftErrors blocks incomplete or unbalanced manual journals", () => {
  assert.deepEqual(
    manualJournalDraftErrors({
      journalNumber: "",
      accountingPeriodId: "",
      description: "",
      reason: "",
      lines: [
        { accountId: "account-a", debit: "100", credit: "20" },
        { accountId: "account-a", debit: "", credit: "" }
      ]
    }),
    [
      "Nomor jurnal wajib diisi.",
      "Periode akuntansi wajib dipilih.",
      "Deskripsi jurnal wajib diisi.",
      "Alasan audit wajib diisi.",
      "Jurnal minimal menggunakan dua akun berbeda.",
      "Setiap baris wajib memilih salah satu sisi debit atau kredit saja.",
      "Total debit wajib sama dengan total kredit dan lebih dari nol."
    ]
  );
});
