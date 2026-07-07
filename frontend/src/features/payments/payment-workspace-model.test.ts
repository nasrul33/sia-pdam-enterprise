import assert from "node:assert/strict";
import { test } from "node:test";
import { financialCommandPermissions, resolveFinancialCommandPermissions } from "../security/financial-command-permissions.ts";
import {
  allocationTotalAmount,
  canReadPayments,
  canReconcilePayments,
  canReversePayment,
  canSettleCounterPayment,
  counterPaymentErrors,
  parseBankStatementCsv,
  parseMoneyInput,
  paymentReconciliationExportErrors,
  reconciliationCompletionErrors,
  reconciliationResolutionErrors,
  reversePaymentErrors,
  summarizeReconciliationMatches,
  summarizeReconciliationSessionItems,
  summarizePaymentList,
  summarizePaymentWorkspace,
  toClosedResolutionStatus
} from "./payment-workspace-model.ts";

const assetCashAccount = {
  id: "11111111-1111-4111-8111-111111111111",
  code: "1-1100",
  name: "Kas Loket",
  type: "ASSET" as const,
  normalBalance: "DEBIT" as const,
  createdAt: "2026-07-07T00:00:00Z",
  updatedAt: "2026-07-07T00:00:00Z"
};

const assetReceivableAccount = {
  id: "22222222-2222-4222-8222-222222222222",
  code: "1-1200",
  name: "Piutang Air",
  type: "ASSET" as const,
  normalBalance: "DEBIT" as const,
  createdAt: "2026-07-07T00:00:00Z",
  updatedAt: "2026-07-07T00:00:00Z"
};

const revenueAccount = {
  id: "33333333-3333-4333-8333-333333333333",
  code: "4-1100",
  name: "Pendapatan Air",
  type: "REVENUE" as const,
  normalBalance: "CREDIT" as const,
  createdAt: "2026-07-07T00:00:00Z",
  updatedAt: "2026-07-07T00:00:00Z"
};

test("summarizePaymentWorkspace summarizes webhook states and failures", () => {
  assert.deepEqual(
    summarizePaymentWorkspace([
      { status: "RECEIVED", errorMessage: null },
      { status: "PROCESSED", errorMessage: null },
      { status: "FAILED", errorMessage: "signature invalid" },
      { status: "FAILED", errorMessage: "" },
      { status: "IGNORED", errorMessage: null }
    ]),
    {
      receivedEvents: 1,
      processedEvents: 1,
      failedEvents: 2,
      ignoredEvents: 1,
      unresolvedFailures: 1
    }
  );
});

test("payment command guards require matching authorities", () => {
  const paymentPermissions = resolveFinancialCommandPermissions([
    financialCommandPermissions.paymentCounter,
    financialCommandPermissions.paymentRead,
    financialCommandPermissions.paymentReconcile,
    financialCommandPermissions.paymentReverse
  ]).payment;

  assert.equal(canSettleCounterPayment(paymentPermissions), true);
  assert.equal(canReadPayments(paymentPermissions), true);
  assert.equal(canReconcilePayments(paymentPermissions), true);
  assert.equal(canReversePayment(paymentPermissions), true);
  assert.equal(canReconcilePayments(resolveFinancialCommandPermissions([]).payment), false);
  assert.equal(canReadPayments(resolveFinancialCommandPermissions([]).payment), false);
  assert.equal(canSettleCounterPayment(resolveFinancialCommandPermissions([]).payment), false);
});

test("summarizePaymentList calculates reconciliation counts and cash impact", () => {
  assert.deepEqual(
    summarizePaymentList([
      { status: "SETTLED", amount: 100000.25 },
      { status: "SETTLED", amount: 50000.25 },
      { status: "REVERSED", amount: 25000.25 },
      { status: "FAILED", amount: 1000 },
      { status: "PENDING", amount: 2000 }
    ]),
    {
      settledPayments: 2,
      reversedPayments: 1,
      pendingOrFailedPayments: 2,
      totalSettledAmount: 150000.5,
      totalReversedAmount: 25000.25,
      netCashImpact: 125000.25
    }
  );
});

test("parseBankStatementCsv accepts header rows, semicolon delimiter, and decimal comma", () => {
  assert.deepEqual(
    parseBankStatementCsv(
      [
        "reference;amount;transacted_at;channel",
        "BANK-001;100000,50;2026-07-31T12:00:00Z;counter",
        "BANK-002;25000;2026-07-31;bank"
      ].join("\n")
    ),
    {
      rows: [
        {
          statementReference: "BANK-001",
          amount: 100000.5,
          transactedAt: "2026-07-31T12:00:00.000Z",
          channel: "counter"
        },
        {
          statementReference: "BANK-002",
          amount: 25000,
          transactedAt: "2026-07-31T00:00:00.000Z",
          channel: "bank"
        }
      ],
      errors: []
    }
  );
});

test("payment reconciliation helpers summarize match risk and validate export filters", () => {
  assert.deepEqual(
    summarizeReconciliationMatches([
      { status: "EXACT_MATCH", amountVariance: 0 },
      { status: "PROBABLE_MATCH", amountVariance: 0 },
      { status: "AMOUNT_VARIANCE", amountVariance: 5000.25 },
      { status: "REVERSED_PAYMENT", amountVariance: 0 },
      { status: "MULTIPLE_CANDIDATES", amountVariance: null },
      { status: "UNMATCHED", amountVariance: null }
    ]),
    {
      exactMatches: 1,
      probableMatches: 1,
      amountVariances: 1,
      reversedPayments: 1,
      multipleCandidates: 1,
      unmatchedRows: 1,
      totalVariance: 5000.25
    }
  );

  assert.deepEqual(
    paymentReconciliationExportErrors({
      status: "FAILED",
      paidAtFrom: "2026-08-01T00:00",
      paidAtTo: "2026-07-31T00:00"
    }),
    [
      "Export rekonsiliasi hanya mendukung payment Settled atau Reversed.",
      "Tanggal akhir export tidak boleh sebelum tanggal awal."
    ]
  );
});

test("payment reconciliation session helpers summarize resolution state and validate commands", () => {
  assert.deepEqual(
    summarizeReconciliationSessionItems([
      { matchStatus: "EXACT_MATCH", resolutionStatus: "ACCEPTED", amountVariance: 0 },
      { matchStatus: "AMOUNT_VARIANCE", resolutionStatus: "OPEN", amountVariance: 2500 },
      { matchStatus: "UNMATCHED", resolutionStatus: "RESOLVED", amountVariance: null },
      { matchStatus: "MULTIPLE_CANDIDATES", resolutionStatus: "IGNORED", amountVariance: null }
    ]),
    {
      exactMatches: 1,
      probableMatches: 0,
      amountVariances: 1,
      reversedPayments: 0,
      multipleCandidates: 1,
      unmatchedRows: 1,
      totalVariance: 2500,
      openItems: 1,
      acceptedItems: 1,
      resolvedItems: 1,
      ignoredItems: 1,
      exceptionItems: 3
    }
  );

  assert.deepEqual(
    reconciliationResolutionErrors({
      itemId: "bad",
      resolutionStatus: "OPEN",
      reason: ""
    }),
    [
      "Item rekonsiliasi wajib dipilih.",
      "Status resolution wajib menutup item.",
      "Alasan resolution wajib diisi."
    ]
  );
  assert.deepEqual(
    reconciliationCompletionErrors({ reason: "", openItems: 2 }),
    [
      "Semua item rekonsiliasi wajib ditutup sebelum session diselesaikan.",
      "Alasan completion wajib diisi."
    ]
  );
  assert.equal(toClosedResolutionStatus("OPEN"), null);
  assert.equal(toClosedResolutionStatus("RESOLVED"), "RESOLVED");
});

test("counterPaymentErrors validates amount, accounts, allocations, and audit reason", () => {
  assert.deepEqual(
    counterPaymentErrors({
      draft: {
        externalReference: "X".repeat(129),
        amount: "100000",
        paidAt: "",
        allocations: [
          { invoiceId: "not-a-uuid", amount: "75000" },
          { invoiceId: "44444444-4444-4444-8444-444444444444", amount: "10000" }
        ],
        cashAccountId: assetCashAccount.id,
        receivableAccountId: revenueAccount.id,
        reason: ""
      },
      accounts: [assetCashAccount, revenueAccount]
    }),
    [
      "Referensi eksternal maksimal 128 karakter.",
      "Waktu bayar wajib valid.",
      "Akun piutang wajib bertipe aset.",
      "Invoice alokasi 1 wajib berupa UUID valid.",
      "Total alokasi invoice wajib sama dengan nominal pembayaran.",
      "Alasan audit wajib diisi."
    ]
  );
});

test("counterPaymentErrors accepts balanced asset-backed allocations", () => {
  assert.deepEqual(
    counterPaymentErrors({
      draft: {
        externalReference: "LOKET-001",
        amount: "100000,50",
        paidAt: "2026-07-07T08:00",
        allocations: [
          { invoiceId: "44444444-4444-4444-8444-444444444444", amount: "75000.25" },
          { invoiceId: "55555555-5555-4555-8555-555555555555", amount: "25000.25" }
        ],
        cashAccountId: assetCashAccount.id,
        receivableAccountId: assetReceivableAccount.id,
        reason: "Pembayaran loket pagi"
      },
      accounts: [assetCashAccount, assetReceivableAccount]
    }),
    []
  );

  assert.equal(parseMoneyInput("100000,50"), 100000.5);
  assert.equal(
    allocationTotalAmount([
      { invoiceId: "44444444-4444-4444-8444-444444444444", amount: "75000.25" },
      { invoiceId: "55555555-5555-4555-8555-555555555555", amount: "25000.25" }
    ]),
    100000.5
  );
});

test("reversePaymentErrors requires payment id, distinct asset accounts, and reason", () => {
  assert.deepEqual(
    reversePaymentErrors({
      draft: {
        paymentId: "bad-id",
        cashAccountId: assetCashAccount.id,
        receivableAccountId: assetCashAccount.id,
        reason: ""
      },
      accounts: [assetCashAccount]
    }),
    [
      "Payment ID wajib berupa UUID valid.",
      "Akun kas/bank dan akun piutang tidak boleh sama.",
      "Alasan reversal wajib diisi."
    ]
  );
});
