import assert from "node:assert/strict";
import { test } from "node:test";
import { financialCommandPermissions, resolveFinancialCommandPermissions } from "../security/financial-command-permissions.ts";
import {
  allocationTotalAmount,
  canManageReconciliationHandoffNotes,
  canReadPayments,
  canReconcilePayments,
  canReversePayment,
  canSettleCounterPayment,
  canSignOffPaymentReconciliations,
  counterPaymentErrors,
  bankStatementImportTemplate,
  parseBankStatementCsv,
  parseBankStatementImport,
  parseMoneyInput,
  reconciliationAdjustmentErrors,
  paymentReconciliationExportErrors,
  reconciliationEvidenceExportErrors,
  reconciliationReviewRegisterFilterErrors,
  reconciliationReviewRegisterExportFilename,
  reconciliationHandoffNoteErrors,
  reconciliationHandoffAgingBucketExportFilename,
  reconciliationHandoffOwnerDrilldownFilter,
  reconciliationHandoffOwnerSlaExportFilename,
  reconciliationHandoffWorkloadFilterErrors,
  reconciliationHandoffWorkloadExportFilename,
  summarizeReconciliationHandoffAgingBuckets,
  summarizeReconciliationHandoffWorkload,
  reconciliationSignOffErrors,
  reconciliationCompletionErrors,
  reconciliationResolutionErrors,
  reversePaymentErrors,
  summarizeReconciliationReviewRegister,
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
    financialCommandPermissions.paymentReconciliationHandoffNote,
    financialCommandPermissions.paymentReconciliationSignoff,
    financialCommandPermissions.paymentReverse
  ]).payment;

  assert.equal(canSettleCounterPayment(paymentPermissions), true);
  assert.equal(canReadPayments(paymentPermissions), true);
  assert.equal(canReconcilePayments(paymentPermissions), true);
  assert.equal(canManageReconciliationHandoffNotes(paymentPermissions), true);
  assert.equal(canSignOffPaymentReconciliations(paymentPermissions), true);
  assert.equal(canReversePayment(paymentPermissions), true);
  assert.equal(canReconcilePayments(resolveFinancialCommandPermissions([]).payment), false);
  assert.equal(canManageReconciliationHandoffNotes(resolveFinancialCommandPermissions([]).payment), false);
  assert.equal(canReadPayments(resolveFinancialCommandPermissions([]).payment), false);
  assert.equal(canSettleCounterPayment(resolveFinancialCommandPermissions([]).payment), false);
  assert.equal(canSignOffPaymentReconciliations(resolveFinancialCommandPermissions([]).payment), false);
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

test("parseBankStatementImport validates source-specific bank mutation and payment gateway templates", () => {
  assert.equal(
    bankStatementImportTemplate("BANK_MUTATION"),
    [
      "transaction_date;reference;description;debit;credit;channel",
      "2026-07-31T12:00:00Z;BANK-20260731-0001;Setoran loket;;100000.00;COUNTER"
    ].join("\n")
  );

  assert.deepEqual(
    parseBankStatementImport(
      [
        "tanggal;no_ref;keterangan;debit;kredit;kanal",
        "31/07/2026;BANK-001;Setoran loket;;100.000,50;counter",
        "31-07-2026 13:30;BANK-002;Setoran online;0;25000;bank"
      ].join("\n"),
      "BANK_MUTATION"
    ),
    {
      rows: [
        {
          statementReference: "BANK-001",
          amount: 100000.5,
          transactedAt: "2026-07-31T00:00:00.000Z",
          channel: "counter"
        },
        {
          statementReference: "BANK-002",
          amount: 25000,
          transactedAt: "2026-07-31T13:30:00.000Z",
          channel: "bank"
        }
      ],
      errors: []
    }
  );

  assert.deepEqual(
    parseBankStatementImport(
      [
        "external_reference,paid_amount,paid_at,channel",
        "PG-001,150000,2026-07-31T08:00:00Z,MOBILE"
      ].join("\n"),
      "PAYMENT_GATEWAY"
    ).rows,
    [
      {
        statementReference: "PG-001",
        amount: 150000,
        transactedAt: "2026-07-31T08:00:00.000Z",
        channel: "MOBILE"
      }
    ]
  );
});

test("parseBankStatementImport returns row-level template errors before matching", () => {
  assert.deepEqual(
    parseBankStatementImport("tanggal;keterangan;debit\n2026-07-31;Mutasi keluar;1000", "BANK_MUTATION"),
    {
      rows: [],
      errors: [
        "Template BANK_MUTATION wajib memiliki kolom: reference, credit.",
        "Baris 2: referensi bank wajib diisi.",
        "Baris 2: nominal kredit masuk wajib lebih besar dari nol."
      ]
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
      {
        matchStatus: "UNMATCHED",
        resolutionStatus: "RESOLVED",
        amountVariance: null,
        adjustmentJournalEntryId: "66666666-6666-4666-8666-666666666666"
      },
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
      exceptionItems: 3,
      adjustedItems: 1
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

test("reconciliationAdjustmentErrors validates accepted exception journal input", () => {
  assert.deepEqual(
    reconciliationAdjustmentErrors({
      draft: {
        itemId: "bad",
        period: "2026/07",
        amount: "0",
        debitAccountId: assetCashAccount.id,
        creditAccountId: assetCashAccount.id,
        reason: ""
      },
      accounts: [assetCashAccount]
    }),
    [
      "Item exception wajib dipilih.",
      "Periode adjustment wajib format YYYY-MM.",
      "Nominal adjustment wajib lebih besar dari nol.",
      "Akun debit dan kredit adjustment tidak boleh sama.",
      "Alasan adjustment wajib diisi."
    ]
  );

  assert.deepEqual(
    reconciliationAdjustmentErrors({
      draft: {
        itemId: "77777777-7777-4777-8777-777777777777",
        period: "2026-07",
        amount: "2.500,50",
        debitAccountId: revenueAccount.id,
        creditAccountId: assetCashAccount.id,
        reason: "Adjustment biaya admin bank"
      },
      accounts: [assetCashAccount, revenueAccount]
    }),
    []
  );
});

test("reconciliationEvidenceExportErrors only allows completed sessions", () => {
  assert.deepEqual(
    reconciliationEvidenceExportErrors({
      sessionId: null,
      sessionStatus: null
    }),
    [
      "Session rekonsiliasi wajib dipilih.",
      "Evidence report hanya tersedia untuk session completed."
    ]
  );

  assert.deepEqual(
    reconciliationEvidenceExportErrors({
      sessionId: "77777777-7777-4777-8777-777777777777",
      sessionStatus: "OPEN"
    }),
    ["Evidence report hanya tersedia untuk session completed."]
  );

  assert.deepEqual(
    reconciliationEvidenceExportErrors({
      sessionId: "77777777-7777-4777-8777-777777777777",
      sessionStatus: "COMPLETED"
    }),
    []
  );
});

test("reconciliationSignOffErrors enforces completed evidence, reason, duplicate guard, and SoD", () => {
  assert.deepEqual(
    reconciliationSignOffErrors({
      reason: "",
      sessionStatus: "OPEN",
      signedOffAt: null,
      actor: null,
      createdBy: "finance.creator",
      completedBy: "finance.completer"
    }),
    [
      "Sign-off hanya tersedia untuk session completed.",
      "Actor sign-off wajib tersedia.",
      "Alasan sign-off wajib diisi."
    ]
  );

  assert.deepEqual(
    reconciliationSignOffErrors({
      reason: "Approved",
      sessionStatus: "COMPLETED",
      signedOffAt: "2026-07-31T12:00:00Z",
      actor: "finance.manager",
      createdBy: "finance.creator",
      completedBy: "finance.completer"
    }),
    ["Evidence rekonsiliasi sudah memiliki sign-off."]
  );

  assert.deepEqual(
    reconciliationSignOffErrors({
      reason: "Approved",
      sessionStatus: "COMPLETED",
      signedOffAt: null,
      actor: "FINANCE.COMPLETER",
      createdBy: "finance.creator",
      completedBy: "finance.completer"
    }),
    ["Actor sign-off harus berbeda dari pembuat dan penyelesai session."]
  );

  assert.deepEqual(
    reconciliationSignOffErrors({
      reason: "Evidence sudah sesuai saldo kas-bank.",
      sessionStatus: "COMPLETED",
      signedOffAt: null,
      actor: "finance.manager",
      createdBy: "finance.creator",
      completedBy: "finance.completer"
    }),
    []
  );
});

test("summarizeReconciliationReviewRegister tracks signed-off SLA and exception load", () => {
  assert.deepEqual(
    summarizeReconciliationReviewRegister([
      {
        reviewStatus: "PENDING_SIGN_OFF",
        exceptionItems: 3,
        adjustedItems: 1,
        pendingSignOffAgeDays: 4
      },
      {
        reviewStatus: "PENDING_SIGN_OFF",
        exceptionItems: 1,
        adjustedItems: 0,
        pendingSignOffAgeDays: 1
      },
      {
        reviewStatus: "SIGNED_OFF",
        exceptionItems: 2,
        adjustedItems: 2,
        pendingSignOffAgeDays: 0
      }
    ]),
    {
      totalEvidence: 3,
      pendingSignOff: 2,
      signedOff: 1,
      overduePendingSignOff: 1,
      exceptionItems: 6,
      adjustedItems: 3
    }
  );
});

test("reconciliationReviewRegisterFilterErrors validates review date range", () => {
  assert.deepEqual(
    reconciliationReviewRegisterFilterErrors({
      signOffStatus: "ALL",
      completedFrom: "bad-date",
      completedTo: "2026-07-31T00:00"
    }),
    ["Tanggal awal review wajib valid."]
  );

  assert.deepEqual(
    reconciliationReviewRegisterFilterErrors({
      signOffStatus: "PENDING_SIGN_OFF",
      completedFrom: "2026-08-01T00:00",
      completedTo: "2026-07-31T00:00"
    }),
    ["Tanggal akhir review tidak boleh sebelum tanggal awal."]
  );

  assert.deepEqual(
    reconciliationReviewRegisterFilterErrors({
      signOffStatus: "SIGNED_OFF",
      completedFrom: "2026-07-01T00:00",
      completedTo: "2026-07-31T23:59"
    }),
    []
  );
});

test("reconciliationReviewRegisterExportFilename includes status and date scope", () => {
  assert.equal(
    reconciliationReviewRegisterExportFilename({
      signOffStatus: "PENDING_SIGN_OFF",
      completedFrom: "2026-07-01T00:00",
      completedTo: "2026-07-31T23:59"
    }),
    "payment-reconciliation-review-register-pending-sign-off-2026-07-01-2026-07-31.csv"
  );

  assert.equal(
    reconciliationReviewRegisterExportFilename({
      signOffStatus: "ALL",
      completedFrom: "",
      completedTo: ""
    }),
    "payment-reconciliation-review-register-all-all-all.csv"
  );
});

test("reconciliationHandoffNoteErrors validates controlled note revisions", () => {
  assert.deepEqual(
    reconciliationHandoffNoteErrors({
      noteId: null,
      noteText: "",
      handoffOwner: "x".repeat(129),
      handoffDueDate: "bad-date",
      handoffStatus: "OPEN",
      reason: ""
    }),
    [
      "Catatan handoff wajib diisi.",
      "Owner handoff maksimal 128 karakter.",
      "Due date handoff wajib valid.",
      "Alasan perubahan handoff wajib diisi."
    ]
  );

  assert.deepEqual(
    reconciliationHandoffNoteErrors({
      noteId: "77777777-7777-4777-8777-777777777777",
      noteText: "Reviewer meminta bukti mutasi settlement provider.",
      handoffOwner: "finance.ops",
      handoffDueDate: "2026-08-03",
      handoffStatus: "IN_PROGRESS",
      reason: "Follow up hasil review register."
    }),
    []
  );
});

test("summarizeReconciliationHandoffWorkload tracks owner follow-up SLA", () => {
  assert.deepEqual(
    summarizeReconciliationHandoffWorkload([
      { handoffStatus: "OPEN", overdueDays: 2 },
      { handoffStatus: "IN_PROGRESS", overdueDays: 1 },
      { handoffStatus: "IN_PROGRESS", overdueDays: 0 },
      { handoffStatus: "CLEARED", overdueDays: 5 }
    ]),
    {
      totalNotes: 4,
      openNotes: 1,
      inProgressNotes: 2,
      clearedNotes: 1,
      overdueNotes: 2
    }
  );
});

test("reconciliationHandoffWorkloadFilterErrors validates due date scope", () => {
  assert.deepEqual(
    reconciliationHandoffWorkloadFilterErrors({
      handoffStatus: "ALL",
      handoffOwner: "finance.ops",
      unassignedOnly: false,
      dueFrom: "bad-date",
      dueTo: "2026-08-03"
    }),
    ["Tanggal awal due date wajib valid."]
  );

  assert.deepEqual(
    reconciliationHandoffWorkloadFilterErrors({
      handoffStatus: "OPEN",
      handoffOwner: "x".repeat(129),
      unassignedOnly: false,
      dueFrom: "2026-08-10",
      dueTo: "2026-08-01"
    }),
    [
      "Tanggal akhir due date tidak boleh sebelum tanggal awal.",
      "Filter owner handoff maksimal 128 karakter."
    ]
  );

  assert.deepEqual(
    reconciliationHandoffWorkloadFilterErrors({
      handoffStatus: "IN_PROGRESS",
      handoffOwner: "finance.ops",
      unassignedOnly: false,
      dueFrom: "2026-08-01",
      dueTo: "2026-08-31"
    }),
    []
  );

  assert.deepEqual(
    reconciliationHandoffWorkloadFilterErrors({
      handoffStatus: "ALL",
      handoffOwner: "finance.ops",
      unassignedOnly: true,
      dueFrom: "",
      dueTo: ""
    }),
    ["Filter owner tidak boleh diisi saat scope tanpa owner dipilih."]
  );
});

test("reconciliationHandoffWorkloadExportFilename includes status owner and due date scope", () => {
  assert.equal(
    reconciliationHandoffWorkloadExportFilename({
      handoffStatus: "IN_PROGRESS",
      handoffOwner: "Finance Ops",
      unassignedOnly: false,
      dueFrom: "2026-08-01",
      dueTo: "2026-08-31"
    }),
    "payment-reconciliation-handoff-workload-in-progress-finance-ops-2026-08-01-2026-08-31.csv"
  );

  assert.equal(
    reconciliationHandoffWorkloadExportFilename({
      handoffStatus: "ALL",
      handoffOwner: "",
      unassignedOnly: false,
      dueFrom: "",
      dueTo: ""
    }),
    "payment-reconciliation-handoff-workload-all-all-owner-all-all.csv"
  );

  assert.equal(
    reconciliationHandoffWorkloadExportFilename({
      handoffStatus: "ALL",
      handoffOwner: "",
      unassignedOnly: true,
      dueFrom: "",
      dueTo: ""
    }),
    "payment-reconciliation-handoff-workload-all-unassigned-all-all.csv"
  );
});

test("reconciliationHandoffOwnerSla helpers build escalation export and drilldown filter", () => {
  const current = {
    handoffStatus: "ALL" as const,
    handoffOwner: "",
    unassignedOnly: false,
    dueFrom: "2026-08-01",
    dueTo: "2026-08-31"
  };

  assert.equal(
    reconciliationHandoffOwnerSlaExportFilename({
      ...current,
      handoffStatus: "OPEN",
      handoffOwner: "Finance Ops"
    }),
    "payment-reconciliation-handoff-owner-sla-open-finance-ops-2026-08-01-2026-08-31.csv"
  );

  assert.deepEqual(
    reconciliationHandoffOwnerDrilldownFilter(current, {
      handoffOwner: "finance.ops",
      unassigned: false,
      handoffStatus: "IN_PROGRESS"
    }),
    {
      handoffStatus: "IN_PROGRESS",
      handoffOwner: "finance.ops",
      unassignedOnly: false,
      dueFrom: "2026-08-01",
      dueTo: "2026-08-31"
    }
  );

  assert.deepEqual(
    reconciliationHandoffOwnerDrilldownFilter(current, {
      handoffOwner: null,
      unassigned: true
    }),
    {
      handoffStatus: "ALL",
      handoffOwner: "",
      unassignedOnly: true,
      dueFrom: "2026-08-01",
      dueTo: "2026-08-31"
    }
  );
});

test("reconciliationHandoffAgingBucket helpers summarize stale queues and export filename", () => {
  assert.deepEqual(
    summarizeReconciliationHandoffAgingBuckets([
      {
        activeNotes: 4,
        dueTodayNotes: 1,
        overdue1To3Notes: 1,
        overdue4To7Notes: 1,
        overdueOver7Notes: 1,
        futureDueNotes: 0,
        noDueDateNotes: 0,
        staleNotes: 3
      },
      {
        activeNotes: 2,
        dueTodayNotes: 0,
        overdue1To3Notes: 0,
        overdue4To7Notes: 0,
        overdueOver7Notes: 0,
        futureDueNotes: 1,
        noDueDateNotes: 1,
        staleNotes: 0
      }
    ]),
    {
      activeNotes: 6,
      dueTodayNotes: 1,
      overdue1To3Notes: 1,
      overdue4To7Notes: 1,
      overdueOver7Notes: 1,
      futureDueNotes: 1,
      noDueDateNotes: 1,
      staleNotes: 3
    }
  );

  assert.equal(
    reconciliationHandoffAgingBucketExportFilename({
      handoffStatus: "ALL",
      handoffOwner: "",
      unassignedOnly: true,
      dueFrom: "2026-08-01",
      dueTo: "2026-08-31"
    }),
    "payment-reconciliation-handoff-aging-buckets-all-unassigned-2026-08-01-2026-08-31.csv"
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
