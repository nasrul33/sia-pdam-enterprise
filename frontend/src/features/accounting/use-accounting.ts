import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query/query-keys";
import { listAccountingPeriods, listAccounts, listJournals } from "./accounting-api";
import type { JournalFilters, PageFilters } from "./accounting-schema";

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
