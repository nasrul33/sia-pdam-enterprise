import { z } from "zod";

export const collectionActionStatusValues = ["OPEN", "IN_PROGRESS", "COMPLETED", "CANCELLED"] as const;
export const collectionActionTypeValues = [
  "REMINDER",
  "WARNING_LETTER",
  "DISCONNECTION_NOTICE",
  "FIELD_VISIT",
  "PHONE_CALL",
  "PAYMENT_PROMISE"
] as const;

export const collectionActionStatusSchema = z.enum(collectionActionStatusValues);
export const collectionActionTypeSchema = z.enum(collectionActionTypeValues);

export const collectionActionSchema = z.object({
  id: z.string().uuid(),
  customerId: z.string().uuid(),
  invoiceId: z.string().uuid().nullable(),
  status: collectionActionStatusSchema,
  actionType: collectionActionTypeSchema,
  notes: z.string().nullable(),
  scheduledAt: z.string().min(1),
  completedAt: z.string().nullable(),
  createdAt: z.string().min(1),
  updatedAt: z.string().min(1)
});

export const collectionActionPageSchema = z.object({
  items: z.array(collectionActionSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalItems: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative()
});

export type CollectionActionStatus = z.infer<typeof collectionActionStatusSchema>;
export type CollectionActionType = z.infer<typeof collectionActionTypeSchema>;
export type CollectionAction = z.infer<typeof collectionActionSchema>;
export type CollectionActionPage = z.infer<typeof collectionActionPageSchema>;

export type CollectionActionFilters = {
  status?: CollectionActionStatus;
  customerId?: string;
  invoiceId?: string;
  page: number;
  size: number;
};

export type CreateCollectionActionPayload = {
  customerId: string;
  invoiceId?: string;
  actionType: CollectionActionType;
  scheduledAt: string;
  notes?: string;
  reason: string;
};

export type CollectionActionWorkflowPayload = {
  notes?: string;
  reason: string;
};

export type CollectionActionWorkflow = "start" | "complete" | "cancel";
