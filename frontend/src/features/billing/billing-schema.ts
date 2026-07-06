import { z } from "zod";

export const billingBatchStatusValues = ["DRAFT", "RUNNING", "COMPLETED", "FAILED", "VOID"] as const;
export const invoiceStatusValues = ["DRAFT", "ISSUED", "PARTIAL_PAID", "PAID", "CORRECTED", "VOID"] as const;

export const billingBatchStatusSchema = z.enum(billingBatchStatusValues);
export const invoiceStatusSchema = z.enum(invoiceStatusValues);

export const billingBatchSchema = z.object({
  id: z.string().uuid(),
  batchNumber: z.string().min(1),
  period: z.string().regex(/^\d{4}-\d{2}$/),
  areaCode: z.string().min(1),
  status: billingBatchStatusSchema,
  createdAt: z.string().min(1),
  updatedAt: z.string().min(1)
});

export const invoiceSchema = z.object({
  id: z.string().uuid(),
  billingBatchId: z.string().uuid(),
  connectionId: z.string().uuid(),
  invoiceNumber: z.string().min(1),
  period: z.string().regex(/^\d{4}-\d{2}$/),
  status: invoiceStatusSchema,
  subtotal: z.coerce.number().nonnegative(),
  penaltyAmount: z.coerce.number().nonnegative(),
  paidAmount: z.coerce.number().nonnegative(),
  outstandingAmount: z.coerce.number().nonnegative(),
  issuedAt: z.string().min(1).nullable(),
  issueJournalEntryId: z.string().uuid().nullable(),
  dueDate: z.string().min(1),
  createdAt: z.string().min(1),
  updatedAt: z.string().min(1)
});

export const billingBatchPageSchema = z.object({
  items: z.array(billingBatchSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalItems: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative()
});

export const invoicePageSchema = z.object({
  items: z.array(invoiceSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalItems: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative()
});

export const billingBatchGenerationSchema = z.object({
  batch: billingBatchSchema,
  generatedInvoices: z.array(invoiceSchema),
  totalAmount: z.coerce.number().nonnegative()
});

export type BillingBatchStatus = z.infer<typeof billingBatchStatusSchema>;
export type InvoiceStatus = z.infer<typeof invoiceStatusSchema>;
export type BillingBatch = z.infer<typeof billingBatchSchema>;
export type Invoice = z.infer<typeof invoiceSchema>;
export type BillingBatchPage = z.infer<typeof billingBatchPageSchema>;
export type InvoicePage = z.infer<typeof invoicePageSchema>;
export type BillingBatchGeneration = z.infer<typeof billingBatchGenerationSchema>;

export type BillingPageFilters = {
  page: number;
  size: number;
  period?: string;
};

export type BillingBatchFilters = BillingPageFilters & {
  status?: BillingBatchStatus;
};

export type InvoiceFilters = BillingPageFilters & {
  status?: InvoiceStatus;
};

export type GenerateBillingBatchPayload = {
  period: string;
  areaCode: string;
  billingDate: string;
  dueDate: string;
  reason: string;
};

export type IssueInvoicePayload = {
  receivableAccountId: string;
  revenueAccountId: string;
  reason: string;
};
