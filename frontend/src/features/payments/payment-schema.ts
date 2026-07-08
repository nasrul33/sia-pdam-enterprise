import { z } from "zod";

export const paymentStatusValues = ["PENDING", "SETTLED", "REVERSED", "FAILED"] as const;
export const paymentWebhookStatusValues = ["RECEIVED", "PROCESSED", "FAILED", "IGNORED"] as const;
export const paymentReconciliationSessionStatusValues = ["OPEN", "COMPLETED", "CANCELLED"] as const;
export const paymentReconciliationReviewStatusValues = ["PENDING_SIGN_OFF", "SIGNED_OFF"] as const;
export const paymentReconciliationHandoffStatusValues = ["OPEN", "IN_PROGRESS", "CLEARED"] as const;
export const paymentReconciliationResolutionStatusValues = ["OPEN", "ACCEPTED", "RESOLVED", "IGNORED"] as const;
export const paymentReconciliationMatchStatusValues = [
  "EXACT_MATCH",
  "PROBABLE_MATCH",
  "AMOUNT_VARIANCE",
  "REVERSED_PAYMENT",
  "MULTIPLE_CANDIDATES",
  "UNMATCHED"
] as const;

export const paymentStatusSchema = z.enum(paymentStatusValues);
export const paymentWebhookStatusSchema = z.enum(paymentWebhookStatusValues);
export const paymentReconciliationSessionStatusSchema = z.enum(paymentReconciliationSessionStatusValues);
export const paymentReconciliationReviewStatusSchema = z.enum(paymentReconciliationReviewStatusValues);
export const paymentReconciliationHandoffStatusSchema = z.enum(paymentReconciliationHandoffStatusValues);
export const paymentReconciliationResolutionStatusSchema = z.enum(paymentReconciliationResolutionStatusValues);
export const paymentReconciliationMatchStatusSchema = z.enum(paymentReconciliationMatchStatusValues);

export const paymentWebhookEventSchema = z.object({
  id: z.string().uuid(),
  provider: z.string().min(1),
  externalReference: z.string().min(1),
  idempotencyKey: z.string().min(1),
  status: paymentWebhookStatusSchema,
  receivedAt: z.string().min(1),
  processedAt: z.string().min(1).nullable(),
  errorMessage: z.string().nullable(),
  createdAt: z.string().min(1),
  updatedAt: z.string().min(1)
});

export const paymentWebhookEventPageSchema = z.object({
  items: z.array(paymentWebhookEventSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalItems: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative()
});

export const paymentSummarySchema = z.object({
  id: z.string().uuid(),
  paymentNumber: z.string().min(1),
  channel: z.string().min(1),
  externalReference: z.string().nullable(),
  status: paymentStatusSchema,
  amount: z.coerce.number().positive(),
  paidAt: z.string().min(1),
  settledAt: z.string().min(1).nullable(),
  reversedAt: z.string().min(1).nullable(),
  reversalReason: z.string().nullable(),
  settlementJournalEntryId: z.string().uuid().nullable(),
  reversalJournalEntryId: z.string().uuid().nullable(),
  createdAt: z.string().min(1),
  updatedAt: z.string().min(1)
});

export const paymentPageSchema = z.object({
  items: z.array(paymentSummarySchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalItems: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative()
});

export const paymentAllocationSchema = z.object({
  id: z.string().uuid(),
  paymentId: z.string().uuid(),
  invoiceId: z.string().uuid(),
  amount: z.coerce.number().positive()
});

export const paymentReceiptSchema = z.object({
  id: z.string().uuid(),
  paymentId: z.string().uuid(),
  receiptNumber: z.string().min(1),
  issuedAt: z.string().min(1)
});

export const paymentSettlementSchema = z.object({
  id: z.string().uuid(),
  paymentNumber: z.string().min(1),
  idempotencyKey: z.string().min(1),
  channel: z.string().min(1),
  externalReference: z.string().nullable(),
  status: paymentStatusSchema,
  amount: z.coerce.number().positive(),
  paidAt: z.string().min(1),
  settledAt: z.string().min(1).nullable(),
  reversedAt: z.string().min(1).nullable(),
  reversalReason: z.string().nullable(),
  settlementJournalEntryId: z.string().uuid().nullable(),
  reversalJournalEntryId: z.string().uuid().nullable(),
  receipt: paymentReceiptSchema,
  allocations: z.array(paymentAllocationSchema),
  createdAt: z.string().min(1),
  updatedAt: z.string().min(1)
});

export const paymentReconciliationMatchResultSchema = z.object({
  rowNumber: z.number().int().positive(),
  statementReference: z.string().min(1),
  statementAmount: z.coerce.number().positive(),
  transactedAt: z.string().min(1),
  statementChannel: z.string().nullable(),
  status: paymentReconciliationMatchStatusSchema,
  amountVariance: z.coerce.number().nullable(),
  candidateCount: z.number().int().nonnegative(),
  matchedPaymentId: z.string().uuid().nullable(),
  matchedPaymentNumber: z.string().nullable(),
  matchedPaymentStatus: paymentStatusSchema.nullable(),
  matchedPaymentAmount: z.coerce.number().nullable(),
  matchedPaymentPaidAt: z.string().nullable(),
  matchedPaymentChannel: z.string().nullable(),
  settlementJournalEntryId: z.string().uuid().nullable(),
  reversalJournalEntryId: z.string().uuid().nullable(),
  message: z.string().min(1)
});

export const paymentReconciliationMatchReportSchema = z.object({
  matches: z.array(paymentReconciliationMatchResultSchema),
  summary: z.object({
    totalRows: z.number().int().nonnegative(),
    exactMatches: z.number().int().nonnegative(),
    probableMatches: z.number().int().nonnegative(),
    amountVariances: z.number().int().nonnegative(),
    reversedPayments: z.number().int().nonnegative(),
    multipleCandidates: z.number().int().nonnegative(),
    unmatchedRows: z.number().int().nonnegative(),
    totalVariance: z.coerce.number()
  })
});

const paymentReconciliationSessionSummaryBaseSchema = z.object({
  id: z.string().uuid(),
  sessionNumber: z.string().min(1),
  status: paymentReconciliationSessionStatusSchema,
  sourceFilename: z.string().nullable(),
  bankAccountReference: z.string().nullable(),
  createdBy: z.string().min(1),
  startedAt: z.string().min(1),
  completedAt: z.string().nullable(),
  signedOffBy: z.string().nullable(),
  signedOffAt: z.string().nullable(),
  signOffReason: z.string().nullable(),
  totalRows: z.number().int().nonnegative(),
  exactMatches: z.number().int().nonnegative(),
  probableMatches: z.number().int().nonnegative(),
  amountVariances: z.number().int().nonnegative(),
  reversedPayments: z.number().int().nonnegative(),
  multipleCandidates: z.number().int().nonnegative(),
  unmatchedRows: z.number().int().nonnegative(),
  totalVariance: z.coerce.number(),
  createdAt: z.string().min(1),
  updatedAt: z.string().min(1)
});

export const paymentReconciliationSessionSummarySchema = paymentReconciliationSessionSummaryBaseSchema;

export const paymentReconciliationSessionItemSchema = z.object({
  id: z.string().uuid(),
  sessionId: z.string().uuid(),
  rowNumber: z.number().int().positive(),
  statementReference: z.string().min(1),
  statementAmount: z.coerce.number().positive(),
  transactedAt: z.string().min(1),
  statementChannel: z.string().nullable(),
  matchStatus: paymentReconciliationMatchStatusSchema,
  amountVariance: z.coerce.number().nullable(),
  candidateCount: z.number().int().nonnegative(),
  matchedPaymentId: z.string().uuid().nullable(),
  matchedPaymentNumber: z.string().nullable(),
  matchedPaymentStatus: paymentStatusSchema.nullable(),
  matchedPaymentAmount: z.coerce.number().nullable(),
  matchedPaymentPaidAt: z.string().nullable(),
  matchedPaymentChannel: z.string().nullable(),
  settlementJournalEntryId: z.string().uuid().nullable(),
  reversalJournalEntryId: z.string().uuid().nullable(),
  adjustmentJournalEntryId: z.string().uuid().nullable(),
  adjustmentReason: z.string().nullable(),
  adjustedBy: z.string().nullable(),
  adjustedAt: z.string().nullable(),
  resolutionStatus: paymentReconciliationResolutionStatusSchema,
  resolutionReason: z.string().nullable(),
  resolvedBy: z.string().nullable(),
  resolvedAt: z.string().nullable(),
  message: z.string().min(1),
  createdAt: z.string().min(1),
  updatedAt: z.string().min(1)
});

export const paymentReconciliationSessionSchema = paymentReconciliationSessionSummaryBaseSchema.extend({
  items: z.array(paymentReconciliationSessionItemSchema)
});

export const paymentReconciliationEvidenceSummarySchema = z.object({
  totalRows: z.number().int().nonnegative(),
  exactMatches: z.number().int().nonnegative(),
  probableMatches: z.number().int().nonnegative(),
  amountVariances: z.number().int().nonnegative(),
  reversedPayments: z.number().int().nonnegative(),
  multipleCandidates: z.number().int().nonnegative(),
  unmatchedRows: z.number().int().nonnegative(),
  totalVariance: z.coerce.number(),
  acceptedItems: z.number().int().nonnegative(),
  resolvedItems: z.number().int().nonnegative(),
  ignoredItems: z.number().int().nonnegative(),
  adjustedItems: z.number().int().nonnegative()
});

export const paymentReconciliationEvidenceItemSchema = z.object({
  itemId: z.string().uuid(),
  rowNumber: z.number().int().positive(),
  statementReference: z.string().min(1),
  statementAmount: z.coerce.number().positive(),
  transactedAt: z.string().min(1),
  statementChannel: z.string().nullable(),
  matchStatus: paymentReconciliationMatchStatusSchema,
  amountVariance: z.coerce.number().nullable(),
  candidateCount: z.number().int().nonnegative(),
  matchedPaymentId: z.string().uuid().nullable(),
  matchedPaymentNumber: z.string().nullable(),
  matchedPaymentStatus: paymentStatusSchema.nullable(),
  matchedPaymentAmount: z.coerce.number().nullable(),
  matchedPaymentPaidAt: z.string().nullable(),
  matchedPaymentChannel: z.string().nullable(),
  settlementJournalEntryId: z.string().uuid().nullable(),
  reversalJournalEntryId: z.string().uuid().nullable(),
  adjustmentJournalEntryId: z.string().uuid().nullable(),
  adjustmentReason: z.string().nullable(),
  adjustedBy: z.string().nullable(),
  adjustedAt: z.string().nullable(),
  resolutionStatus: paymentReconciliationResolutionStatusSchema,
  resolutionReason: z.string().nullable(),
  resolvedBy: z.string().nullable(),
  resolvedAt: z.string().nullable(),
  message: z.string().min(1)
});

export const paymentReconciliationEvidenceReportSchema = z.object({
  sessionId: z.string().uuid(),
  sessionNumber: z.string().min(1),
  status: paymentReconciliationSessionStatusSchema,
  sourceFilename: z.string().nullable(),
  bankAccountReference: z.string().nullable(),
  createdBy: z.string().min(1),
  startedAt: z.string().min(1),
  completedAt: z.string().nullable(),
  completedBy: z.string().nullable(),
  completionReason: z.string().nullable(),
  signedOffAt: z.string().nullable(),
  signedOffBy: z.string().nullable(),
  signOffReason: z.string().nullable(),
  summary: paymentReconciliationEvidenceSummarySchema,
  items: z.array(paymentReconciliationEvidenceItemSchema),
  generatedAt: z.string().min(1)
});

export const paymentReconciliationSessionPageSchema = z.object({
  items: z.array(paymentReconciliationSessionSummarySchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalItems: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative()
});

export const paymentReconciliationReviewRegisterEntrySchema = z.object({
  sessionId: z.string().uuid(),
  sessionNumber: z.string().min(1),
  reviewStatus: paymentReconciliationReviewStatusSchema,
  sourceFilename: z.string().nullable(),
  bankAccountReference: z.string().nullable(),
  createdBy: z.string().min(1),
  startedAt: z.string().min(1),
  completedAt: z.string().nullable(),
  signedOffBy: z.string().nullable(),
  signedOffAt: z.string().nullable(),
  signOffReason: z.string().nullable(),
  totalRows: z.number().int().nonnegative(),
  exactMatches: z.number().int().nonnegative(),
  probableMatches: z.number().int().nonnegative(),
  exceptionItems: z.number().int().nonnegative(),
  amountVariances: z.number().int().nonnegative(),
  reversedPayments: z.number().int().nonnegative(),
  multipleCandidates: z.number().int().nonnegative(),
  unmatchedRows: z.number().int().nonnegative(),
  acceptedItems: z.number().int().nonnegative(),
  resolvedItems: z.number().int().nonnegative(),
  ignoredItems: z.number().int().nonnegative(),
  adjustedItems: z.number().int().nonnegative(),
  totalVariance: z.coerce.number(),
  pendingSignOffAgeDays: z.number().int().nonnegative(),
  handoffNoteCount: z.number().int().nonnegative(),
  reviewerNotes: z.string().nullable(),
  handoffOwner: z.string().nullable(),
  handoffDueDate: z.string().nullable(),
  handoffStatus: paymentReconciliationHandoffStatusSchema.nullable(),
  generatedAt: z.string().min(1)
});

export const paymentReconciliationReviewRegisterPageSchema = z.object({
  items: z.array(paymentReconciliationReviewRegisterEntrySchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalItems: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative()
});

export const paymentReconciliationHandoffNoteRevisionSchema = z.object({
  id: z.string().uuid(),
  noteId: z.string().uuid(),
  revisionNumber: z.number().int().positive(),
  noteText: z.string().min(1),
  handoffOwner: z.string().nullable(),
  handoffDueDate: z.string().nullable(),
  handoffStatus: paymentReconciliationHandoffStatusSchema,
  reason: z.string().min(1),
  changedBy: z.string().min(1),
  changedAt: z.string().min(1)
});

export const paymentReconciliationHandoffNoteSchema = z.object({
  id: z.string().uuid(),
  sessionId: z.string().uuid(),
  noteText: z.string().min(1),
  handoffOwner: z.string().nullable(),
  handoffDueDate: z.string().nullable(),
  handoffStatus: paymentReconciliationHandoffStatusSchema,
  createdBy: z.string().min(1),
  updatedBy: z.string().min(1),
  createdAt: z.string().min(1),
  updatedAt: z.string().min(1),
  revisions: z.array(paymentReconciliationHandoffNoteRevisionSchema)
});

export const paymentReconciliationHandoffNoteListSchema = z.array(paymentReconciliationHandoffNoteSchema);

export type PaymentStatus = z.infer<typeof paymentStatusSchema>;
export type PaymentWebhookStatus = z.infer<typeof paymentWebhookStatusSchema>;
export type PaymentReconciliationSessionStatus = z.infer<typeof paymentReconciliationSessionStatusSchema>;
export type PaymentReconciliationReviewStatus = z.infer<typeof paymentReconciliationReviewStatusSchema>;
export type PaymentReconciliationHandoffStatus = z.infer<typeof paymentReconciliationHandoffStatusSchema>;
export type PaymentReconciliationResolutionStatus = z.infer<typeof paymentReconciliationResolutionStatusSchema>;
export type PaymentReconciliationMatchStatus = z.infer<typeof paymentReconciliationMatchStatusSchema>;
export type PaymentWebhookEvent = z.infer<typeof paymentWebhookEventSchema>;
export type PaymentWebhookEventPage = z.infer<typeof paymentWebhookEventPageSchema>;
export type PaymentSummary = z.infer<typeof paymentSummarySchema>;
export type PaymentPage = z.infer<typeof paymentPageSchema>;
export type PaymentAllocation = z.infer<typeof paymentAllocationSchema>;
export type PaymentReceipt = z.infer<typeof paymentReceiptSchema>;
export type PaymentSettlement = z.infer<typeof paymentSettlementSchema>;
export type PaymentReconciliationMatchResult = z.infer<typeof paymentReconciliationMatchResultSchema>;
export type PaymentReconciliationMatchReport = z.infer<typeof paymentReconciliationMatchReportSchema>;
export type PaymentReconciliationSessionSummary = z.infer<typeof paymentReconciliationSessionSummarySchema>;
export type PaymentReconciliationSessionItem = z.infer<typeof paymentReconciliationSessionItemSchema>;
export type PaymentReconciliationSession = z.infer<typeof paymentReconciliationSessionSchema>;
export type PaymentReconciliationSessionPage = z.infer<typeof paymentReconciliationSessionPageSchema>;
export type PaymentReconciliationEvidenceSummary = z.infer<typeof paymentReconciliationEvidenceSummarySchema>;
export type PaymentReconciliationEvidenceItem = z.infer<typeof paymentReconciliationEvidenceItemSchema>;
export type PaymentReconciliationEvidenceReport = z.infer<typeof paymentReconciliationEvidenceReportSchema>;
export type PaymentReconciliationReviewRegisterEntry = z.infer<typeof paymentReconciliationReviewRegisterEntrySchema>;
export type PaymentReconciliationReviewRegisterPage = z.infer<typeof paymentReconciliationReviewRegisterPageSchema>;
export type PaymentReconciliationHandoffNoteRevision = z.infer<typeof paymentReconciliationHandoffNoteRevisionSchema>;
export type PaymentReconciliationHandoffNote = z.infer<typeof paymentReconciliationHandoffNoteSchema>;

export type PaymentFilters = {
  page: number;
  size: number;
  status?: PaymentStatus;
  channel?: string;
};

export type PaymentReconciliationExportFilters = {
  status?: PaymentStatus;
  channel?: string;
  paidAtFrom?: string;
  paidAtTo?: string;
};

export type PaymentReconciliationSessionFilters = {
  page: number;
  size: number;
  status?: PaymentReconciliationSessionStatus;
};

export type PaymentReconciliationReviewRegisterFilters = {
  page: number;
  size: number;
  signOffStatus?: PaymentReconciliationReviewStatus;
  completedFrom?: string;
  completedTo?: string;
};

export type PaymentReconciliationMatchPayload = {
  rows: {
    statementReference: string;
    amount: number;
    transactedAt: string;
    channel: string | null;
  }[];
};

export type CreatePaymentReconciliationSessionPayload = {
  sourceFilename: string | null;
  bankAccountReference: string | null;
  rows: PaymentReconciliationMatchPayload["rows"];
};

export type ClosedPaymentReconciliationResolutionStatus = Exclude<PaymentReconciliationResolutionStatus, "OPEN">;

export type ResolvePaymentReconciliationItemPayload = {
  resolutionStatus: ClosedPaymentReconciliationResolutionStatus;
  reason: string;
};

export type CreatePaymentReconciliationAdjustmentPayload = {
  period: string;
  amount: number;
  debitAccountId: string;
  creditAccountId: string;
  reason: string;
};

export type CompletePaymentReconciliationSessionPayload = {
  reason: string;
};

export type SignOffPaymentReconciliationSessionPayload = {
  reason: string;
};

export type PaymentReconciliationHandoffNotePayload = {
  noteText: string;
  handoffOwner: string | null;
  handoffDueDate: string | null;
  handoffStatus: PaymentReconciliationHandoffStatus;
  reason: string;
};

export type PaymentWebhookEventFilters = {
  page: number;
  size: number;
  provider?: string;
  status?: PaymentWebhookStatus;
};

export type SettleCounterPaymentPayload = {
  externalReference: string | null;
  amount: number;
  paidAt: string;
  allocations: {
    invoiceId: string;
    amount: number;
  }[];
  cashAccountId: string;
  receivableAccountId: string;
  reason: string;
};

export type ReversePaymentPayload = {
  cashAccountId: string;
  receivableAccountId: string;
  reason: string;
};
