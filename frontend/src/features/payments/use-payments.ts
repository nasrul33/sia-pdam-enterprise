import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query/query-keys";
import { listPaymentWebhookEvents, reversePayment, settleCounterPayment } from "./payment-api";
import type {
  PaymentWebhookEventFilters,
  ReversePaymentPayload,
  SettleCounterPaymentPayload
} from "./payment-schema";

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
