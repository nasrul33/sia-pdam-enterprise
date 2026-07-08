import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query/query-keys";
import {
  completePaymentReconciliationSession,
  createPaymentReconciliationAdjustment,
  createPaymentReconciliationSession,
  getPaymentReconciliationEvidenceReport,
  getPaymentReconciliationSession,
  getPayment,
  listPaymentReconciliationSessions,
  listPaymentWebhookEvents,
  listPayments,
  matchPaymentReconciliation,
  resolvePaymentReconciliationItem,
  reversePayment,
  settleCounterPayment
} from "./payment-api";
import type {
  CompletePaymentReconciliationSessionPayload,
  CreatePaymentReconciliationAdjustmentPayload,
  CreatePaymentReconciliationSessionPayload,
  PaymentFilters,
  PaymentReconciliationMatchPayload,
  PaymentReconciliationSessionFilters,
  PaymentWebhookEventFilters,
  ResolvePaymentReconciliationItemPayload,
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

export function useMatchPaymentReconciliation() {
  return useMutation({
    mutationFn: (payload: PaymentReconciliationMatchPayload) => matchPaymentReconciliation(payload)
  });
}

export function usePaymentReconciliationSessions(filters: PaymentReconciliationSessionFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.payments, "reconciliation-sessions", filters],
    queryFn: () => listPaymentReconciliationSessions(filters),
    enabled
  });
}

export function usePaymentReconciliationSession(sessionId: string | null, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.payments, "reconciliation-session", sessionId],
    queryFn: () => getPaymentReconciliationSession(sessionId ?? ""),
    enabled: enabled && Boolean(sessionId)
  });
}

export function usePaymentReconciliationEvidenceReport(sessionId: string | null, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.payments, "reconciliation-evidence", sessionId],
    queryFn: () => getPaymentReconciliationEvidenceReport(sessionId ?? ""),
    enabled: enabled && Boolean(sessionId)
  });
}

export function useCreatePaymentReconciliationSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreatePaymentReconciliationSessionPayload) => createPaymentReconciliationSession(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.payments });
    }
  });
}

export function useResolvePaymentReconciliationItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      sessionId: string;
      itemId: string;
      payload: ResolvePaymentReconciliationItemPayload;
    }) => resolvePaymentReconciliationItem(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.payments });
    }
  });
}

export function useCreatePaymentReconciliationAdjustment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      sessionId: string;
      itemId: string;
      payload: CreatePaymentReconciliationAdjustmentPayload;
    }) => createPaymentReconciliationAdjustment(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.payments });
      void queryClient.invalidateQueries({ queryKey: queryKeys.journals });
    }
  });
}

export function useCompletePaymentReconciliationSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { sessionId: string; payload: CompletePaymentReconciliationSessionPayload }) =>
      completePaymentReconciliationSession(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.payments });
    }
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
