import { apiGet, apiGetText, apiPost } from "@/lib/api/client";
import {
  paymentReconciliationSessionPageSchema,
  paymentReconciliationSessionSchema,
  paymentPageSchema,
  paymentReconciliationMatchReportSchema,
  paymentSettlementSchema,
  paymentWebhookEventPageSchema,
  type CompletePaymentReconciliationSessionPayload,
  type CreatePaymentReconciliationSessionPayload,
  type PaymentFilters,
  type PaymentReconciliationExportFilters,
  type PaymentReconciliationMatchPayload,
  type PaymentReconciliationSessionFilters,
  type PaymentWebhookEventFilters,
  type ResolvePaymentReconciliationItemPayload,
  type ReversePaymentPayload,
  type SettleCounterPaymentPayload
} from "./payment-schema";

function paymentParams(filters: PaymentFilters): URLSearchParams {
  const params = new URLSearchParams();
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));

  if (filters.status) {
    params.set("status", filters.status);
  }
  if (filters.channel) {
    params.set("channel", filters.channel);
  }

  return params;
}

function webhookEventParams(filters: PaymentWebhookEventFilters): URLSearchParams {
  const params = new URLSearchParams();
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));

  if (filters.provider) {
    params.set("provider", filters.provider);
  }
  if (filters.status) {
    params.set("status", filters.status);
  }

  return params;
}

function reconciliationParams(filters: PaymentReconciliationExportFilters): URLSearchParams {
  const params = new URLSearchParams();

  if (filters.status) {
    params.set("status", filters.status);
  }
  if (filters.channel) {
    params.set("channel", filters.channel);
  }
  if (filters.paidAtFrom) {
    params.set("paidAtFrom", filters.paidAtFrom);
  }
  if (filters.paidAtTo) {
    params.set("paidAtTo", filters.paidAtTo);
  }

  return params;
}

function reconciliationSessionParams(filters: PaymentReconciliationSessionFilters): URLSearchParams {
  const params = new URLSearchParams();
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));

  if (filters.status) {
    params.set("status", filters.status);
  }

  return params;
}

export async function listPayments(filters: PaymentFilters) {
  const payload = await apiGet<unknown>(`/api/payments?${paymentParams(filters).toString()}`);
  return paymentPageSchema.parse(payload);
}

export async function getPayment(paymentId: string) {
  const payload = await apiGet<unknown>(`/api/payments/${paymentId}`);
  return paymentSettlementSchema.parse(payload);
}

export async function listPaymentWebhookEvents(filters: PaymentWebhookEventFilters) {
  const payload = await apiGet<unknown>(`/api/payment-webhook-events?${webhookEventParams(filters).toString()}`);
  return paymentWebhookEventPageSchema.parse(payload);
}

export async function exportPaymentReconciliationCsv(filters: PaymentReconciliationExportFilters) {
  const query = reconciliationParams(filters).toString();
  return apiGetText(`/api/payment-reconciliation/export${query ? `?${query}` : ""}`);
}

export async function matchPaymentReconciliation(payload: PaymentReconciliationMatchPayload) {
  const response = await apiPost<PaymentReconciliationMatchPayload, unknown>("/api/payment-reconciliation/match", payload);
  return paymentReconciliationMatchReportSchema.parse(response);
}

export async function listPaymentReconciliationSessions(filters: PaymentReconciliationSessionFilters) {
  const payload = await apiGet<unknown>(`/api/payment-reconciliation/sessions?${reconciliationSessionParams(filters).toString()}`);
  return paymentReconciliationSessionPageSchema.parse(payload);
}

export async function getPaymentReconciliationSession(sessionId: string) {
  const payload = await apiGet<unknown>(`/api/payment-reconciliation/sessions/${sessionId}`);
  return paymentReconciliationSessionSchema.parse(payload);
}

export async function createPaymentReconciliationSession(payload: CreatePaymentReconciliationSessionPayload) {
  const response = await apiPost<CreatePaymentReconciliationSessionPayload, unknown>(
    "/api/payment-reconciliation/sessions",
    payload
  );
  return paymentReconciliationSessionSchema.parse(response);
}

export async function resolvePaymentReconciliationItem(input: {
  sessionId: string;
  itemId: string;
  payload: ResolvePaymentReconciliationItemPayload;
}) {
  const response = await apiPost<ResolvePaymentReconciliationItemPayload, unknown>(
    `/api/payment-reconciliation/sessions/${input.sessionId}/items/${input.itemId}/resolve`,
    input.payload
  );
  return paymentReconciliationSessionSchema.parse(response);
}

export async function completePaymentReconciliationSession(input: {
  sessionId: string;
  payload: CompletePaymentReconciliationSessionPayload;
}) {
  const response = await apiPost<CompletePaymentReconciliationSessionPayload, unknown>(
    `/api/payment-reconciliation/sessions/${input.sessionId}/complete`,
    input.payload
  );
  return paymentReconciliationSessionSchema.parse(response);
}

export async function settleCounterPayment(input: {
  payload: SettleCounterPaymentPayload;
  idempotencyKey: string;
}) {
  const response = await apiPost<SettleCounterPaymentPayload, unknown>("/api/payments/counter", input.payload, {
    headers: { "Idempotency-Key": input.idempotencyKey }
  });
  return paymentSettlementSchema.parse(response);
}

export async function reversePayment(input: { paymentId: string; payload: ReversePaymentPayload }) {
  const response = await apiPost<ReversePaymentPayload, unknown>(
    `/api/payments/${input.paymentId}/reverse`,
    input.payload
  );
  return paymentSettlementSchema.parse(response);
}
