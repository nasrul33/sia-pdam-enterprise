import { apiGet, apiGetText, apiPost } from "@/lib/api/client";
import {
  paymentReconciliationSessionPageSchema,
  paymentReconciliationSessionSchema,
  paymentReconciliationEvidenceReportSchema,
  paymentReconciliationHandoffAgingBucketReportSchema,
  paymentReconciliationHandoffNoteListSchema,
  paymentReconciliationHandoffNoteSchema,
  paymentReconciliationHandoffOwnerSlaReportSchema,
  paymentReconciliationHandoffWorkloadPageSchema,
  paymentReconciliationReviewRegisterPageSchema,
  paymentPageSchema,
  paymentReconciliationMatchReportSchema,
  paymentSettlementSchema,
  paymentWebhookEventPageSchema,
  type CompletePaymentReconciliationSessionPayload,
  type CreatePaymentReconciliationAdjustmentPayload,
  type CreatePaymentReconciliationSessionPayload,
  type PaymentFilters,
  type PaymentReconciliationExportFilters,
  type PaymentReconciliationHandoffNotePayload,
  type PaymentReconciliationHandoffOwnerSlaFilters,
  type PaymentReconciliationHandoffWorkloadFilters,
  type PaymentReconciliationMatchPayload,
  type PaymentReconciliationReviewRegisterFilters,
  type PaymentReconciliationSessionFilters,
  type PaymentWebhookEventFilters,
  type ResolvePaymentReconciliationItemPayload,
  type ReversePaymentPayload,
  type SettleCounterPaymentPayload,
  type SignOffPaymentReconciliationSessionPayload
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

function reconciliationReviewRegisterParams(filters: PaymentReconciliationReviewRegisterFilters): URLSearchParams {
  const params = new URLSearchParams();
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));

  if (filters.signOffStatus) {
    params.set("signOffStatus", filters.signOffStatus);
  }
  if (filters.completedFrom) {
    params.set("completedFrom", filters.completedFrom);
  }
  if (filters.completedTo) {
    params.set("completedTo", filters.completedTo);
  }

  return params;
}

function reconciliationReviewRegisterExportParams(filters: PaymentReconciliationReviewRegisterFilters): URLSearchParams {
  const params = new URLSearchParams();

  if (filters.signOffStatus) {
    params.set("signOffStatus", filters.signOffStatus);
  }
  if (filters.completedFrom) {
    params.set("completedFrom", filters.completedFrom);
  }
  if (filters.completedTo) {
    params.set("completedTo", filters.completedTo);
  }

  return params;
}

function reconciliationHandoffWorkloadParams(filters: PaymentReconciliationHandoffWorkloadFilters): URLSearchParams {
  const params = new URLSearchParams();
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));

  if (filters.handoffStatus) {
    params.set("handoffStatus", filters.handoffStatus);
  }
  if (filters.handoffOwner) {
    params.set("handoffOwner", filters.handoffOwner);
  }
  if (filters.unassignedOnly) {
    params.set("unassignedOnly", "true");
  }
  if (filters.dueFrom) {
    params.set("dueFrom", filters.dueFrom);
  }
  if (filters.dueTo) {
    params.set("dueTo", filters.dueTo);
  }

  return params;
}

function reconciliationHandoffWorkloadExportParams(filters: PaymentReconciliationHandoffWorkloadFilters): URLSearchParams {
  const params = new URLSearchParams();

  if (filters.handoffStatus) {
    params.set("handoffStatus", filters.handoffStatus);
  }
  if (filters.handoffOwner) {
    params.set("handoffOwner", filters.handoffOwner);
  }
  if (filters.unassignedOnly) {
    params.set("unassignedOnly", "true");
  }
  if (filters.dueFrom) {
    params.set("dueFrom", filters.dueFrom);
  }
  if (filters.dueTo) {
    params.set("dueTo", filters.dueTo);
  }

  return params;
}

function reconciliationHandoffOwnerSlaParams(filters: PaymentReconciliationHandoffOwnerSlaFilters): URLSearchParams {
  const params = new URLSearchParams();

  if (filters.handoffStatus) {
    params.set("handoffStatus", filters.handoffStatus);
  }
  if (filters.handoffOwner) {
    params.set("handoffOwner", filters.handoffOwner);
  }
  if (filters.unassignedOnly) {
    params.set("unassignedOnly", "true");
  }
  if (filters.dueFrom) {
    params.set("dueFrom", filters.dueFrom);
  }
  if (filters.dueTo) {
    params.set("dueTo", filters.dueTo);
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

export async function getPaymentReconciliationEvidenceReport(sessionId: string) {
  const payload = await apiGet<unknown>(`/api/reports/payment-reconciliation-evidence/${sessionId}`);
  return paymentReconciliationEvidenceReportSchema.parse(payload);
}

export async function listPaymentReconciliationReviewRegister(filters: PaymentReconciliationReviewRegisterFilters) {
  const payload = await apiGet<unknown>(
    `/api/reports/payment-reconciliation-review-register?${reconciliationReviewRegisterParams(filters).toString()}`
  );
  return paymentReconciliationReviewRegisterPageSchema.parse(payload);
}

export async function exportPaymentReconciliationReviewRegisterCsv(filters: PaymentReconciliationReviewRegisterFilters) {
  const query = reconciliationReviewRegisterExportParams(filters).toString();
  return apiGetText(`/api/reports/payment-reconciliation-review-register/export${query ? `?${query}` : ""}`);
}

export async function listPaymentReconciliationHandoffWorkload(filters: PaymentReconciliationHandoffWorkloadFilters) {
  const payload = await apiGet<unknown>(
    `/api/reports/payment-reconciliation-handoff-notes?${reconciliationHandoffWorkloadParams(filters).toString()}`
  );
  return paymentReconciliationHandoffWorkloadPageSchema.parse(payload);
}

export async function exportPaymentReconciliationHandoffWorkloadCsv(filters: PaymentReconciliationHandoffWorkloadFilters) {
  const query = reconciliationHandoffWorkloadExportParams(filters).toString();
  return apiGetText(`/api/reports/payment-reconciliation-handoff-notes/export${query ? `?${query}` : ""}`);
}

export async function getPaymentReconciliationHandoffOwnerSla(filters: PaymentReconciliationHandoffOwnerSlaFilters) {
  const query = reconciliationHandoffOwnerSlaParams(filters).toString();
  const payload = await apiGet<unknown>(
    `/api/reports/payment-reconciliation-handoff-notes/owner-sla${query ? `?${query}` : ""}`
  );
  return paymentReconciliationHandoffOwnerSlaReportSchema.parse(payload);
}

export async function exportPaymentReconciliationHandoffOwnerSlaCsv(
  filters: PaymentReconciliationHandoffOwnerSlaFilters
) {
  const query = reconciliationHandoffOwnerSlaParams(filters).toString();
  return apiGetText(`/api/reports/payment-reconciliation-handoff-notes/owner-sla/export${query ? `?${query}` : ""}`);
}

export async function getPaymentReconciliationHandoffAgingBuckets(filters: PaymentReconciliationHandoffOwnerSlaFilters) {
  const query = reconciliationHandoffOwnerSlaParams(filters).toString();
  const payload = await apiGet<unknown>(
    `/api/reports/payment-reconciliation-handoff-notes/aging-buckets${query ? `?${query}` : ""}`
  );
  return paymentReconciliationHandoffAgingBucketReportSchema.parse(payload);
}

export async function exportPaymentReconciliationHandoffAgingBucketsCsv(
  filters: PaymentReconciliationHandoffOwnerSlaFilters
) {
  const query = reconciliationHandoffOwnerSlaParams(filters).toString();
  return apiGetText(`/api/reports/payment-reconciliation-handoff-notes/aging-buckets/export${query ? `?${query}` : ""}`);
}

export async function exportPaymentReconciliationHandoffAgingEvidencePacketCsv(
  filters: PaymentReconciliationHandoffOwnerSlaFilters
) {
  const query = reconciliationHandoffOwnerSlaParams(filters).toString();
  return apiGetText(
    `/api/reports/payment-reconciliation-handoff-notes/aging-buckets/evidence-packet/export${query ? `?${query}` : ""}`
  );
}

export async function listPaymentReconciliationHandoffNotes(sessionId: string) {
  const payload = await apiGet<unknown>(`/api/reports/payment-reconciliation-review-register/${sessionId}/handoff-notes`);
  return paymentReconciliationHandoffNoteListSchema.parse(payload);
}

export async function createPaymentReconciliationHandoffNote(input: {
  sessionId: string;
  payload: PaymentReconciliationHandoffNotePayload;
}) {
  const response = await apiPost<PaymentReconciliationHandoffNotePayload, unknown>(
    `/api/reports/payment-reconciliation-review-register/${input.sessionId}/handoff-notes`,
    input.payload
  );
  return paymentReconciliationHandoffNoteSchema.parse(response);
}

export async function revisePaymentReconciliationHandoffNote(input: {
  sessionId: string;
  noteId: string;
  payload: PaymentReconciliationHandoffNotePayload;
}) {
  const response = await apiPost<PaymentReconciliationHandoffNotePayload, unknown>(
    `/api/reports/payment-reconciliation-review-register/${input.sessionId}/handoff-notes/${input.noteId}/revisions`,
    input.payload
  );
  return paymentReconciliationHandoffNoteSchema.parse(response);
}

export async function exportPaymentReconciliationEvidenceCsv(sessionId: string) {
  return apiGetText(`/api/reports/payment-reconciliation-evidence/${sessionId}/export`);
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

export async function createPaymentReconciliationAdjustment(input: {
  sessionId: string;
  itemId: string;
  payload: CreatePaymentReconciliationAdjustmentPayload;
}) {
  const response = await apiPost<CreatePaymentReconciliationAdjustmentPayload, unknown>(
    `/api/payment-reconciliation/sessions/${input.sessionId}/items/${input.itemId}/adjust`,
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

export async function signOffPaymentReconciliationSession(input: {
  sessionId: string;
  payload: SignOffPaymentReconciliationSessionPayload;
}) {
  const response = await apiPost<SignOffPaymentReconciliationSessionPayload, unknown>(
    `/api/payment-reconciliation/sessions/${input.sessionId}/sign-off`,
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
