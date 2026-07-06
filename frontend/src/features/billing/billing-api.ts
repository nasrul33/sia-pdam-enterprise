import { apiGet, apiPost } from "@/lib/api/client";
import {
  billingBatchGenerationSchema,
  billingBatchPageSchema,
  invoicePageSchema,
  invoiceSchema,
  type BillingBatchFilters,
  type GenerateBillingBatchPayload,
  type InvoiceFilters,
  type IssueInvoicePayload
} from "./billing-schema";

function pageParams(filters: { page: number; size: number; period?: string; status?: string }): URLSearchParams {
  const params = new URLSearchParams();
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));

  if (filters.period) {
    params.set("period", filters.period);
  }
  if (filters.status) {
    params.set("status", filters.status);
  }

  return params;
}

export async function listBillingBatches(filters: BillingBatchFilters) {
  const payload = await apiGet<unknown>(`/api/billing-batches?${pageParams(filters).toString()}`);
  return billingBatchPageSchema.parse(payload);
}

export async function listInvoices(filters: InvoiceFilters) {
  const payload = await apiGet<unknown>(`/api/invoices?${pageParams(filters).toString()}`);
  return invoicePageSchema.parse(payload);
}

export async function generateBillingBatch(input: {
  payload: GenerateBillingBatchPayload;
  idempotencyKey: string;
}) {
  const response = await apiPost<GenerateBillingBatchPayload, unknown>("/api/billing-batches/generate", input.payload, {
    headers: { "Idempotency-Key": input.idempotencyKey }
  });
  return billingBatchGenerationSchema.parse(response);
}

export async function issueInvoice(input: { invoiceId: string; payload: IssueInvoicePayload }) {
  const response = await apiPost<IssueInvoicePayload, unknown>(`/api/invoices/${input.invoiceId}/issue`, input.payload);
  return invoiceSchema.parse(response);
}
