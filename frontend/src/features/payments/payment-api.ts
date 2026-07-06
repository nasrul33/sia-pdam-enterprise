import { apiGet, apiPost } from "@/lib/api/client";
import {
  paymentSettlementSchema,
  paymentWebhookEventPageSchema,
  type PaymentWebhookEventFilters,
  type ReversePaymentPayload,
  type SettleCounterPaymentPayload
} from "./payment-schema";

function pageParams(filters: PaymentWebhookEventFilters): URLSearchParams {
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

export async function listPaymentWebhookEvents(filters: PaymentWebhookEventFilters) {
  const payload = await apiGet<unknown>(`/api/payment-webhook-events?${pageParams(filters).toString()}`);
  return paymentWebhookEventPageSchema.parse(payload);
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
