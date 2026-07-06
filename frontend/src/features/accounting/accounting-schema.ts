import { z } from "zod";

export const accountTypeValues = ["ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE"] as const;
export const normalBalanceValues = ["DEBIT", "CREDIT"] as const;
export const periodStatusValues = ["OPEN", "CLOSING_REVIEW", "LOCKED", "REOPENED"] as const;
export const journalStatusValues = ["DRAFT", "POSTED", "REVERSED", "VOID"] as const;

export const accountTypeSchema = z.enum(accountTypeValues);
export const normalBalanceSchema = z.enum(normalBalanceValues);
export const periodStatusSchema = z.enum(periodStatusValues);
export const journalStatusSchema = z.enum(journalStatusValues);

export const accountSchema = z.object({
  id: z.string().uuid(),
  code: z.string().min(1),
  name: z.string().min(1),
  type: accountTypeSchema,
  normalBalance: normalBalanceSchema,
  createdAt: z.string().min(1),
  updatedAt: z.string().min(1)
});

export const accountingPeriodSchema = z.object({
  id: z.string().uuid(),
  period: z.string().regex(/^\d{4}-\d{2}$/),
  status: periodStatusSchema,
  allowsPosting: z.boolean(),
  availableActions: z.array(z.string().min(1)),
  createdAt: z.string().min(1),
  updatedAt: z.string().min(1)
});

export const journalSummarySchema = z.object({
  id: z.string().uuid(),
  journalNumber: z.string().min(1),
  accountingPeriodId: z.string().uuid(),
  description: z.string().min(1),
  status: journalStatusSchema,
  postedAt: z.string().min(1).nullable(),
  postedBy: z.string().nullable(),
  sourceModule: z.string().nullable(),
  sourceRecordId: z.string().uuid().nullable(),
  sourceDocumentNumber: z.string().nullable(),
  createdAt: z.string().min(1),
  updatedAt: z.string().min(1)
});

export const accountPageSchema = z.object({
  items: z.array(accountSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalItems: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative()
});

export const journalLineSchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  debit: z.coerce.number().nonnegative(),
  credit: z.coerce.number().nonnegative(),
  description: z.string().nullable()
});

export const journalSchema = z.object({
  id: z.string().uuid(),
  journalNumber: z.string().min(1),
  accountingPeriodId: z.string().uuid(),
  description: z.string(),
  status: journalStatusSchema,
  totalDebit: z.coerce.number().nonnegative(),
  totalCredit: z.coerce.number().nonnegative(),
  balanced: z.boolean(),
  postedAt: z.string().min(1).nullable(),
  postedBy: z.string().nullable(),
  sourceModule: z.string().nullable(),
  sourceRecordId: z.string().uuid().nullable(),
  sourceDocumentNumber: z.string().nullable(),
  lines: z.array(journalLineSchema),
  availableActions: z.array(z.string().min(1)),
  createdAt: z.string().min(1),
  updatedAt: z.string().min(1)
});

export const accountingPeriodPageSchema = z.object({
  items: z.array(accountingPeriodSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalItems: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative()
});

export const journalSummaryPageSchema = z.object({
  items: z.array(journalSummarySchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalItems: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative()
});

export type AccountType = z.infer<typeof accountTypeSchema>;
export type NormalBalance = z.infer<typeof normalBalanceSchema>;
export type PeriodStatus = z.infer<typeof periodStatusSchema>;
export type JournalStatus = z.infer<typeof journalStatusSchema>;
export type Account = z.infer<typeof accountSchema>;
export type AccountingPeriod = z.infer<typeof accountingPeriodSchema>;
export type JournalSummary = z.infer<typeof journalSummarySchema>;
export type JournalLine = z.infer<typeof journalLineSchema>;
export type Journal = z.infer<typeof journalSchema>;
export type AccountPage = z.infer<typeof accountPageSchema>;
export type AccountingPeriodPage = z.infer<typeof accountingPeriodPageSchema>;
export type JournalSummaryPage = z.infer<typeof journalSummaryPageSchema>;

export type PageFilters = {
  page: number;
  size: number;
};

export type JournalFilters = PageFilters & {
  status?: JournalStatus;
  accountingPeriodId?: string;
};

export type WorkflowReasonPayload = {
  reason: string;
};

export type CreateAccountPayload = WorkflowReasonPayload & {
  code: string;
  name: string;
  type: AccountType;
};

export type CreateAccountingPeriodPayload = WorkflowReasonPayload & {
  period: string;
};

export type CreateJournalLinePayload = {
  accountId: string;
  debit: number;
  credit: number;
  description: string;
};

export type CreateJournalPayload = WorkflowReasonPayload & {
  journalNumber: string;
  accountingPeriodId: string;
  description: string;
  lines: CreateJournalLinePayload[];
};

export type AccountingPeriodWorkflow = "start-closing-review" | "lock";
