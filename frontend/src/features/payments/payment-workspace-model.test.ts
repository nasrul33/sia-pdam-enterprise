import assert from "node:assert/strict";
import { test } from "node:test";
import { financialCommandPermissions, resolveFinancialCommandPermissions } from "../security/financial-command-permissions.ts";
import {
  allocationTotalAmount,
  canReadPayments,
  canReversePayment,
  canSettleCounterPayment,
  counterPaymentErrors,
  parseMoneyInput,
  reversePaymentErrors,
  summarizePaymentList,
  summarizePaymentWorkspace
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
    financialCommandPermissions.paymentReverse
  ]).payment;

  assert.equal(canSettleCounterPayment(paymentPermissions), true);
  assert.equal(canReadPayments(paymentPermissions), true);
  assert.equal(canReversePayment(paymentPermissions), true);
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
