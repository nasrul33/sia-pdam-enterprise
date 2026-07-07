import { z } from "zod";

export const paymentStatusValues = ["PENDING", "SETTLED", "REVERSED", "FAILED"] as const;
export const paymentWebhookStatusValues = ["RECEIVED", "PROCESSED", "FAILED", "IGNORED"] as const;

export const paymentStatusSchema = z.enum(paymentStatusValues);
export const paymentWebhookStatusSchema = z.enum(paymentWebhookStatusValues);

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

export type PaymentStatus = z.infer<typeof paymentStatusSchema>;
export type PaymentWebhookStatus = z.infer<typeof paymentWebhookStatusSchema>;
export type PaymentWebhookEvent = z.infer<typeof paymentWebhookEventSchema>;
export type PaymentWebhookEventPage = z.infer<typeof paymentWebhookEventPageSchema>;
export type PaymentSummary = z.infer<typeof paymentSummarySchema>;
export type PaymentPage = z.infer<typeof paymentPageSchema>;
export type PaymentAllocation = z.infer<typeof paymentAllocationSchema>;
export type PaymentReceipt = z.infer<typeof paymentReceiptSchema>;
export type PaymentSettlement = z.infer<typeof paymentSettlementSchema>;

export type PaymentFilters = {
  page: number;
  size: number;
  status?: PaymentStatus;
  channel?: string;
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
