import assert from "node:assert/strict";
import { test } from "node:test";
import { financialCommandPermissions, resolveFinancialCommandPermissions } from "../security/financial-command-permissions.ts";
import {
  canIssueInvoice,
  generateBillingBatchErrors,
  issueInvoiceErrors,
  summarizeBillingWorkspace
} from "./billing-workspace-model.ts";

const billingPermissions = resolveFinancialCommandPermissions([
  financialCommandPermissions.billingGenerate,
  financialCommandPermissions.invoiceIssue
]).billing;

test("summarizeBillingWorkspace summarizes batch and invoice control states", () => {
  assert.deepEqual(
    summarizeBillingWorkspace({
      batches: [{ status: "COMPLETED" }, { status: "FAILED" }, { status: "RUNNING" }],
      invoices: [
        { status: "DRAFT", outstandingAmount: 1000 },
        { status: "ISSUED", outstandingAmount: 2500 },
        { status: "PAID", outstandingAmount: 0 }
      ]
    }),
    {
      completedBatches: 1,
      runningOrFailedBatches: 2,
      draftInvoices: 1,
      issuedInvoices: 1,
      totalOutstanding: 3500
    }
  );
});

test("canIssueInvoice requires invoice.issue authority and draft status", () => {
  assert.equal(canIssueInvoice({ status: "DRAFT" }, billingPermissions), true);
  assert.equal(canIssueInvoice({ status: "ISSUED" }, billingPermissions), false);
  assert.equal(canIssueInvoice({ status: "DRAFT" }, resolveFinancialCommandPermissions([]).billing), false);
});

test("generateBillingBatchErrors validates period, dates, area, and audit reason", () => {
  assert.deepEqual(
    generateBillingBatchErrors({
      period: "202607",
      areaCode: "",
      billingDate: "2026-07-20",
      dueDate: "2026-07-10",
      reason: ""
    }),
    [
      "Periode wajib menggunakan format yyyy-MM.",
      "Area wajib diisi.",
      "Tanggal jatuh tempo tidak boleh sebelum tanggal billing.",
      "Alasan audit wajib diisi."
    ]
  );
});

test("issueInvoiceErrors requires asset receivable and revenue accounts", () => {
  assert.deepEqual(
    issueInvoiceErrors({
      draft: {
        receivableAccountId: "asset-account",
        revenueAccountId: "liability-account",
        reason: ""
      },
      accounts: [
        {
          id: "asset-account",
          code: "1-1200",
          name: "Piutang Air",
          type: "ASSET",
          normalBalance: "DEBIT",
          createdAt: "2026-07-06T00:00:00Z",
          updatedAt: "2026-07-06T00:00:00Z"
        },
        {
          id: "liability-account",
          code: "2-1100",
          name: "Utang",
          type: "LIABILITY",
          normalBalance: "CREDIT",
          createdAt: "2026-07-06T00:00:00Z",
          updatedAt: "2026-07-06T00:00:00Z"
        }
      ]
    }),
    ["Akun pendapatan wajib bertipe pendapatan.", "Alasan audit wajib diisi."]
  );
});
