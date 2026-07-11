import assert from "node:assert/strict";
import { test } from "node:test";
import { financialCommandPermissions, resolveFinancialCommandPermissions } from "../security/financial-command-permissions.ts";
import {
  billingIssueReadinessCopy,
  canIssueInvoice,
  canViewInvoiceDocument,
  canVoidInvoice,
  filterInvoicesByStatus,
  generateBillingBatchErrors,
  invoiceScopeTitle,
  issueInvoiceErrors,
  summarizeBillingWorkspace,
  voidInvoiceErrors
} from "./billing-workspace-model.ts";

const billingPermissions = resolveFinancialCommandPermissions([
  financialCommandPermissions.billingGenerate,
  financialCommandPermissions.invoiceView,
  financialCommandPermissions.invoiceIssue,
  financialCommandPermissions.invoiceCorrectApprove
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

test("invoice document and void guards follow permissions and accounting trace state", () => {
  assert.equal(canViewInvoiceDocument(billingPermissions), true);
  assert.equal(canViewInvoiceDocument(resolveFinancialCommandPermissions([]).billing), false);
  assert.equal(
    canVoidInvoice(
      {
        status: "ISSUED",
        paidAmount: 0,
        issueJournalEntryId: "00000000-0000-0000-0000-000000000001"
      },
      billingPermissions
    ),
    true
  );
  assert.equal(
    canVoidInvoice(
      {
        status: "PARTIAL_PAID",
        paidAmount: 1000,
        issueJournalEntryId: "00000000-0000-0000-0000-000000000001"
      },
      billingPermissions
    ),
    false
  );
  assert.equal(
    canVoidInvoice(
      {
        status: "ISSUED",
        paidAmount: 0,
        issueJournalEntryId: null
      },
      billingPermissions
    ),
    false
  );
});

test("filterInvoicesByStatus scopes batch drill-down invoices by selected status", () => {
  assert.deepEqual(
    filterInvoicesByStatus(
      [
        { id: "invoice-1", status: "DRAFT" },
        { id: "invoice-2", status: "ISSUED" },
        { id: "invoice-3", status: "DRAFT" }
      ],
      "DRAFT"
    ),
    [
      { id: "invoice-1", status: "DRAFT" },
      { id: "invoice-3", status: "DRAFT" }
    ]
  );

  assert.deepEqual(filterInvoicesByStatus([{ id: "invoice-1", status: "PAID" }], undefined), [
    { id: "invoice-1", status: "PAID" }
  ]);
});

test("invoiceScopeTitle identifies all-invoice and selected batch surfaces", () => {
  assert.equal(invoiceScopeTitle(null), "Invoice");
  assert.equal(invoiceScopeTitle({ batchNumber: "BIL-202607-AREA-01" }), "Invoice Batch BIL-202607-AREA-01");
});

test("billingIssueReadinessCopy surfaces readiness and journal trace risks", () => {
  assert.deepEqual(billingIssueReadinessCopy(null), {
    label: "Belum dipilih",
    tone: "neutral",
    description: "Pilih billing batch untuk membaca readiness issue invoice."
  });
  assert.deepEqual(
    billingIssueReadinessCopy({
      readyToIssue: false,
      draftInvoices: 1,
      missingJournalTraceInvoices: 1,
      totalInvoices: 2
    }),
    {
      label: "Trace bermasalah",
      tone: "danger",
      description: "Ada invoice berdampak finansial yang belum punya journal trace."
    }
  );
  assert.deepEqual(
    billingIssueReadinessCopy({
      readyToIssue: true,
      draftInvoices: 3,
      missingJournalTraceInvoices: 0,
      totalInvoices: 4
    }),
    {
      label: "Siap issue",
      tone: "success",
      description: "3 invoice draft siap masuk workflow issue."
    }
  );
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
        nonAirRevenueAccountId: "",
        penaltyRevenueAccountId: "",
        reason: ""
      },
      invoice: {
        fixedCharge: 0,
        levyCharge: 0,
        adminCharge: 0,
        wasteCharge: 0,
        penaltyAmount: 0
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
    ["Akun pendapatan air wajib bertipe pendapatan.", "Alasan audit wajib diisi."]
  );
});

test("issueInvoiceErrors requires component revenue accounts when invoice components are positive", () => {
  const revenueAccount = {
    id: "revenue-account",
    code: "4-1100",
    name: "Pendapatan Air",
    type: "REVENUE" as const,
    normalBalance: "CREDIT" as const,
    createdAt: "2026-07-06T00:00:00Z",
    updatedAt: "2026-07-06T00:00:00Z"
  };

  assert.deepEqual(
    issueInvoiceErrors({
      draft: {
        receivableAccountId: "asset-account",
        revenueAccountId: revenueAccount.id,
        nonAirRevenueAccountId: "",
        penaltyRevenueAccountId: "",
        reason: "issue invoice"
      },
      invoice: {
        fixedCharge: 5000,
        levyCharge: 2000,
        adminCharge: 2500,
        wasteCharge: 3000,
        penaltyAmount: 1500
      },
      accounts: [
        {
          ...revenueAccount,
          id: "asset-account",
          code: "1-1200",
          name: "Piutang Air",
          type: "ASSET",
          normalBalance: "DEBIT"
        },
        revenueAccount
      ]
    }),
    ["Akun pendapatan non-air wajib dipilih.", "Akun pendapatan denda wajib dipilih."]
  );
});

test("voidInvoiceErrors blocks paid or trace-less invoice and requires reason", () => {
  assert.deepEqual(
    voidInvoiceErrors({
      invoice: {
        status: "PARTIAL_PAID",
        paidAmount: 1000,
        issueJournalEntryId: null
      },
      draft: { reason: "" }
    }),
    [
      "Void hanya boleh untuk invoice issued yang belum dibayar.",
      "Invoice yang sudah dibayar harus reversal pembayaran terlebih dahulu.",
      "Invoice issued wajib memiliki journal trace sebelum void.",
      "Alasan audit wajib diisi."
    ]
  );
});
