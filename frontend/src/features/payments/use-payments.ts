import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query/query-keys";
import { getPayment, listPaymentWebhookEvents, listPayments, reversePayment, settleCounterPayment } from "./payment-api";
import type {
  PaymentFilters,
  PaymentWebhookEventFilters,
  ReversePaymentPayload,
  SettleCounterPaymentPayload
} from "./payment-schema";

export function usePayments(filters: PaymentFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.payments, "list", filters],
    queryFn: () => listPayments(filters),
    enabled
  });
}

export function usePayment(paymentId: string | null, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.payments, "detail", paymentId],
    queryFn: () => getPayment(paymentId ?? ""),
    enabled: enabled && Boolean(paymentId)
  });
}

export function usePaymentWebhookEvents(filters: PaymentWebhookEventFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.payments, "webhook-events", filters],
    queryFn: () => listPaymentWebhookEvents(filters),
    enabled
  });
}

export function useSettleCounterPayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { payload: SettleCounterPaymentPayload; idempotencyKey: string }) =>
      settleCounterPayment(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.payments });
      void queryClient.invalidateQueries({ queryKey: queryKeys.invoices });
      void queryClient.invalidateQueries({ queryKey: queryKeys.journals });
    }
  });
}

export function useReversePayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { paymentId: string; payload: ReversePaymentPayload }) => reversePayment(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.payments });
      void queryClient.invalidateQueries({ queryKey: queryKeys.invoices });
      void queryClient.invalidateQueries({ queryKey: queryKeys.journals });
    }
  });
}
