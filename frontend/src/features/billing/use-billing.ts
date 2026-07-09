import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query/query-keys";
import {
  generateBillingBatch,
  getBillingBatchIssueReadiness,
  issueInvoice,
  listBatchInvoices,
  listBillingBatches,
  listInvoices
} from "./billing-api";
import type {
  BillingBatchFilters,
  GenerateBillingBatchPayload,
  InvoiceFilters,
  IssueInvoicePayload
} from "./billing-schema";

export function useBillingBatches(filters: BillingBatchFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.billingBatches, "list", filters],
    queryFn: () => listBillingBatches(filters),
    enabled
  });
}

export function useInvoices(filters: InvoiceFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.invoices, "list", filters],
    queryFn: () => listInvoices(filters),
    enabled
  });
}

export function useBatchInvoices(batchId: string | null, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.invoices, "batch", batchId],
    queryFn: () => listBatchInvoices(batchId ?? ""),
    enabled: enabled && Boolean(batchId)
  });
}

export function useBillingBatchIssueReadiness(batchId: string | null, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.billingBatches, "issue-readiness", batchId],
    queryFn: () => getBillingBatchIssueReadiness(batchId ?? ""),
    enabled: enabled && Boolean(batchId)
  });
}

export function useGenerateBillingBatch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { payload: GenerateBillingBatchPayload; idempotencyKey: string }) => generateBillingBatch(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.billingBatches });
      void queryClient.invalidateQueries({ queryKey: queryKeys.invoices });
    }
  });
}

export function useIssueInvoice() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { invoiceId: string; payload: IssueInvoicePayload }) => issueInvoice(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.invoices });
      void queryClient.invalidateQueries({ queryKey: queryKeys.billingBatches });
      void queryClient.invalidateQueries({ queryKey: queryKeys.journals });
    }
  });
}
