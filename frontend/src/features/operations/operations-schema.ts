import { z } from "zod";

const uuidSchema = z.string().uuid();
const instantSchema = z.string().min(1);
const optionalTextSchema = z.string().nullable();
const decimalSchema = z.coerce.number();
const nullableDecimalSchema = z.coerce.number().nullable();

export const customerStatusValues = ["ACTIVE", "INACTIVE", "BLACKLISTED"] as const;
export const connectionStatusValues = ["DRAFT", "ACTIVE", "SUSPENDED", "TERMINATED"] as const;
export const meterReadingStatusValues = ["DRAFT", "SUBMITTED", "VERIFIED", "REJECTED", "LOCKED"] as const;
export const tariffVersionStatusValues = ["DRAFT", "ACTIVE", "ARCHIVED"] as const;
export const accountTypeValues = ["ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE"] as const;
export const normalBalanceValues = ["DEBIT", "CREDIT"] as const;

export const customerStatusSchema = z.enum(customerStatusValues);
export const connectionStatusSchema = z.enum(connectionStatusValues);
export const meterReadingStatusSchema = z.enum(meterReadingStatusValues);
export const tariffVersionStatusSchema = z.enum(tariffVersionStatusValues);
export const accountTypeSchema = z.enum(accountTypeValues);
export const normalBalanceSchema = z.enum(normalBalanceValues);

export const customerSummarySchema = z.object({
  id: uuidSchema,
  customerNumber: z.string().min(1),
  fullName: z.string().min(1),
  phoneNumber: optionalTextSchema,
  status: customerStatusSchema,
  createdAt: instantSchema,
  updatedAt: instantSchema
});

export const customerAddressSchema = z.object({
  id: uuidSchema,
  addressLine: z.string().min(1),
  areaCode: z.string().min(1),
  latitude: nullableDecimalSchema,
  longitude: nullableDecimalSchema,
  createdAt: instantSchema,
  updatedAt: instantSchema
});

export const customerSchema = customerSummarySchema.extend({
  identityNumber: optionalTextSchema,
  addresses: z.array(customerAddressSchema)
});

export const tariffGroupSchema = z.object({
  id: uuidSchema,
  code: z.string().min(1),
  name: z.string().min(1),
  createdAt: instantSchema,
  updatedAt: instantSchema
});

export const connectionSchema = z.object({
  id: uuidSchema,
  customerId: uuidSchema,
  tariffGroupId: uuidSchema,
  connectionNumber: z.string().min(1),
  meterNumber: z.string().min(1),
  status: connectionStatusSchema,
  installedAt: z.string().nullable(),
  availableActions: z.array(z.string().min(1)),
  createdAt: instantSchema,
  updatedAt: instantSchema
});

export const meterRouteSchema = z.object({
  id: uuidSchema,
  routeCode: z.string().min(1),
  name: z.string().min(1),
  areaCode: z.string().min(1),
  createdAt: instantSchema,
  updatedAt: instantSchema
});

export const meterReadingSchema = z.object({
  id: uuidSchema,
  connectionId: uuidSchema,
  routeId: uuidSchema,
  period: z.string().regex(/^\d{4}-\d{2}$/),
  previousReading: decimalSchema,
  currentReading: decimalSchema,
  usageM3: decimalSchema,
  status: meterReadingStatusSchema,
  readAt: instantSchema,
  readerId: uuidSchema.nullable(),
  anomalyFlag: z.boolean(),
  anomalyReason: optionalTextSchema,
  importBatchId: uuidSchema.nullable(),
  sourceDeviceId: optionalTextSchema,
  sourceRowNumber: z.number().int().positive().nullable(),
  lockedAt: z.string().nullable(),
  lockedBy: optionalTextSchema,
  availableActions: z.array(z.string().min(1)),
  createdAt: instantSchema,
  updatedAt: instantSchema
});

export const meterReadingImportItemStatusValues = ["IMPORTED", "SKIPPED", "INVALID"] as const;
export const meterReadingImportItemStatusSchema = z.enum(meterReadingImportItemStatusValues);

export const meterReadingImportItemSchema = z.object({
  rowNumber: z.number().int().positive(),
  connectionId: uuidSchema.nullable(),
  readingId: uuidSchema.nullable(),
  status: meterReadingImportItemStatusSchema,
  code: z.string().min(1),
  message: z.string().min(1)
});

export const meterReadingImportSchema = z.object({
  batchId: uuidSchema,
  sourceDeviceId: z.string().min(1),
  sourceBatchReference: z.string().min(1),
  routeId: uuidSchema,
  period: z.string().regex(/^\d{4}-\d{2}$/),
  totalRows: z.number().int().positive(),
  importedRows: z.number().int().nonnegative(),
  skippedRows: z.number().int().nonnegative(),
  invalidRows: z.number().int().nonnegative(),
  importedBy: z.string().min(1),
  importedAt: instantSchema,
  items: z.array(meterReadingImportItemSchema)
});

export const tariffVersionSchema = z.object({
  id: uuidSchema,
  tariffGroupId: uuidSchema,
  effectiveDate: z.string().min(1),
  fixedCharge: decimalSchema,
  levyCharge: decimalSchema,
  adminCharge: decimalSchema,
  wasteCharge: decimalSchema,
  penaltyRate: decimalSchema,
  status: tariffVersionStatusSchema,
  availableActions: z.array(z.string().min(1)),
  createdAt: instantSchema,
  updatedAt: instantSchema
});

export const tariffBlockSchema = z.object({
  id: uuidSchema,
  tariffVersionId: uuidSchema,
  blockOrder: z.number().int().positive(),
  minM3: decimalSchema,
  maxM3: nullableDecimalSchema,
  pricePerM3: decimalSchema,
  createdAt: instantSchema,
  updatedAt: instantSchema
});

export const tariffCalculationLineSchema = z.object({
  blockOrder: z.number().int().positive(),
  minM3: decimalSchema,
  maxM3: nullableDecimalSchema,
  quantityM3: decimalSchema,
  pricePerM3: decimalSchema,
  amount: decimalSchema
});

export const tariffCalculationSchema = z.object({
  tariffVersionId: uuidSchema,
  tariffGroupId: uuidSchema,
  effectiveDate: z.string().min(1),
  billingDate: z.string().min(1),
  usageM3: decimalSchema,
  lines: z.array(tariffCalculationLineSchema),
  usageCharge: decimalSchema,
  fixedCharge: decimalSchema,
  levyCharge: decimalSchema,
  adminCharge: decimalSchema,
  wasteCharge: decimalSchema,
  penaltyCharge: decimalSchema,
  subtotal: decimalSchema,
  total: decimalSchema
});

export const receivableAgingSnapshotSchema = z.object({
  id: uuidSchema,
  period: z.string().regex(/^\d{4}-\d{2}$/),
  currentAmount: decimalSchema,
  bucket30Amount: decimalSchema,
  bucket60Amount: decimalSchema,
  bucket90Amount: decimalSchema,
  bucketOver90Amount: decimalSchema,
  totalOutstandingAmount: decimalSchema,
  generatedAt: instantSchema,
  createdAt: instantSchema,
  updatedAt: instantSchema
});

export const trialBalanceLineSchema = z.object({
  accountId: uuidSchema,
  accountCode: z.string().min(1),
  accountName: z.string().min(1),
  accountType: accountTypeSchema,
  normalBalance: normalBalanceSchema,
  debitTotal: decimalSchema,
  creditTotal: decimalSchema,
  debitBalance: decimalSchema,
  creditBalance: decimalSchema
});

export const trialBalanceReportSchema = z.object({
  fromDate: z.string().min(1),
  toDate: z.string().min(1),
  lines: z.array(trialBalanceLineSchema),
  totalDebitBalance: decimalSchema,
  totalCreditBalance: decimalSchema,
  balanced: z.boolean(),
  generatedAt: instantSchema
});

function pageSchema<TItem extends z.ZodTypeAny>(itemSchema: TItem) {
  return z.object({
    items: z.array(itemSchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalItems: z.number().int().nonnegative(),
    totalPages: z.number().int().nonnegative()
  });
}

export const customerPageSchema = pageSchema(customerSummarySchema);
export const tariffGroupPageSchema = pageSchema(tariffGroupSchema);
export const connectionPageSchema = pageSchema(connectionSchema);
export const meterRoutePageSchema = pageSchema(meterRouteSchema);
export const meterReadingPageSchema = pageSchema(meterReadingSchema);
export const tariffVersionPageSchema = pageSchema(tariffVersionSchema);
export const receivableAgingSnapshotPageSchema = pageSchema(receivableAgingSnapshotSchema);

export type CustomerStatus = z.infer<typeof customerStatusSchema>;
export type ConnectionStatus = z.infer<typeof connectionStatusSchema>;
export type MeterReadingStatus = z.infer<typeof meterReadingStatusSchema>;
export type TariffVersionStatus = z.infer<typeof tariffVersionStatusSchema>;
export type CustomerSummary = z.infer<typeof customerSummarySchema>;
export type Customer = z.infer<typeof customerSchema>;
export type TariffGroup = z.infer<typeof tariffGroupSchema>;
export type Connection = z.infer<typeof connectionSchema>;
export type MeterRoute = z.infer<typeof meterRouteSchema>;
export type MeterReading = z.infer<typeof meterReadingSchema>;
export type MeterReadingImport = z.infer<typeof meterReadingImportSchema>;
export type TariffVersion = z.infer<typeof tariffVersionSchema>;
export type TariffBlock = z.infer<typeof tariffBlockSchema>;
export type TariffCalculation = z.infer<typeof tariffCalculationSchema>;
export type ReceivableAgingSnapshot = z.infer<typeof receivableAgingSnapshotSchema>;
export type TrialBalanceReport = z.infer<typeof trialBalanceReportSchema>;

export type PageFilters = {
  page: number;
  size: number;
};

export type CustomerFilters = PageFilters & {
  status?: CustomerStatus;
  search?: string;
};

export type ConnectionFilters = PageFilters & {
  customerId?: string;
  status?: ConnectionStatus;
};

export type MeterRouteFilters = PageFilters & {
  areaCode?: string;
};

export type MeterReadingFilters = PageFilters & {
  routeId?: string;
  period?: string;
  status?: MeterReadingStatus;
};

export type TariffVersionFilters = PageFilters & {
  tariffGroupId?: string;
  status?: TariffVersionStatus;
};

export type ReceivableAgingFilters = PageFilters & {
  period?: string;
};

export type WorkflowReasonPayload = {
  reason: string;
};

export type CreateCustomerPayload = WorkflowReasonPayload & {
  customerNumber: string;
  fullName: string;
  identityNumber: string | null;
  phoneNumber: string | null;
  addressLine: string;
  areaCode: string;
  latitude: number | null;
  longitude: number | null;
};

export type CreateTariffGroupPayload = WorkflowReasonPayload & {
  code: string;
  name: string;
};

export type CreateConnectionPayload = WorkflowReasonPayload & {
  customerId: string;
  tariffGroupId: string;
  connectionNumber: string;
  meterNumber: string;
  installedAt: string | null;
};

export type ConnectionWorkflow = "activate" | "suspend" | "terminate";

export type CreateMeterRoutePayload = WorkflowReasonPayload & {
  routeCode: string;
  name: string;
  areaCode: string;
};

export type CreateMeterReadingPayload = WorkflowReasonPayload & {
  connectionId: string;
  routeId: string;
  period: string;
  previousReading: number;
  currentReading: number;
  readAt: string;
  readerId: string | null;
  anomalyFlag: boolean;
  anomalyReason: string | null;
};

export type ImportMeterReadingRowPayload = {
  connectionId: string;
  previousReading: number;
  currentReading: number;
  readAt: string;
  readerId: string | null;
  anomalyFlag: boolean;
  anomalyReason: string | null;
};

export type ImportMeterReadingsPayload = WorkflowReasonPayload & {
  sourceDeviceId: string;
  sourceBatchReference: string;
  routeId: string;
  period: string;
  rows: ImportMeterReadingRowPayload[];
};

export type MeterReadingWorkflow = "submit" | "verify" | "reject" | "lock";

export type CreateTariffVersionPayload = WorkflowReasonPayload & {
  tariffGroupId: string;
  effectiveDate: string;
  fixedCharge: number;
  levyCharge: number;
  adminCharge: number;
  wasteCharge: number;
  penaltyRate: number;
};

export type CreateTariffBlockPayload = WorkflowReasonPayload & {
  blockOrder: number;
  minM3: number;
  maxM3: number | null;
  pricePerM3: number;
};

export type TariffVersionWorkflow = "activate" | "archive";

export type TariffCalculationPayload = {
  tariffGroupId: string;
  billingDate: string;
  usageM3: number;
  outstandingAmount: number;
};

export type GenerateReceivableAgingSnapshotPayload = WorkflowReasonPayload & {
  period: string;
  asOfDate: string;
};

export type TrialBalanceFilters = {
  fromDate: string;
  toDate: string;
};
