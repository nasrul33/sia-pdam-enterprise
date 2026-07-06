import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query/query-keys";
import {
  createAccountingPeriod,
  createAccount,
  createJournal,
  getJournal,
  listAccountingPeriods,
  listAccounts,
  listJournals,
  postJournal,
  submitAccountingPeriodWorkflow
} from "./accounting-api";
import type {
  AccountingPeriodWorkflow,
  CreateAccountingPeriodPayload,
  CreateAccountPayload,
  CreateJournalPayload,
  JournalFilters,
  PageFilters,
  WorkflowReasonPayload
} from "./accounting-schema";

export function useAccounts(filters: PageFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.accounts, "list", filters],
    queryFn: () => listAccounts(filters),
    enabled
  });
}

export function useAccountingPeriods(filters: PageFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.accountingPeriods, "list", filters],
    queryFn: () => listAccountingPeriods(filters),
    enabled
  });
}

export function useJournals(filters: JournalFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.journals, "list", filters],
    queryFn: () => listJournals(filters),
    enabled
  });
}

export function useJournal(journalId: string | null, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.journals, "detail", journalId],
    queryFn: () => getJournal(journalId ?? ""),
    enabled: enabled && Boolean(journalId)
  });
}

export function useCreateAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateAccountPayload) => createAccount(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.accounts });
    }
  });
}

export function useCreateAccountingPeriod() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateAccountingPeriodPayload) => createAccountingPeriod(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.accountingPeriods });
    }
  });
}

export function useAccountingPeriodWorkflow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      periodId: string;
      workflow: AccountingPeriodWorkflow;
      payload: WorkflowReasonPayload;
    }) => submitAccountingPeriodWorkflow(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.accountingPeriods });
      void queryClient.invalidateQueries({ queryKey: queryKeys.journals });
    }
  });
}

export function useCreateJournal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateJournalPayload) => createJournal(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.journals });
    }
  });
}

export function usePostJournal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { journalId: string; payload: WorkflowReasonPayload }) => postJournal(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.journals });
    }
  });
}
