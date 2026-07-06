import { apiGet } from "@/lib/api/client";
import {
  accountingPeriodPageSchema,
  accountPageSchema,
  journalSummaryPageSchema,
  type JournalFilters,
  type PageFilters
} from "./accounting-schema";

function pageParams(filters: PageFilters): URLSearchParams {
  const params = new URLSearchParams();
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return params;
}

export async function listAccounts(filters: PageFilters) {
  const payload = await apiGet<unknown>(`/api/accounts?${pageParams(filters).toString()}`);
  return accountPageSchema.parse(payload);
}

export async function listAccountingPeriods(filters: PageFilters) {
  const payload = await apiGet<unknown>(`/api/accounting-periods?${pageParams(filters).toString()}`);
  return accountingPeriodPageSchema.parse(payload);
}

export async function listJournals(filters: JournalFilters) {
  const params = pageParams(filters);
  if (filters.status) {
    params.set("status", filters.status);
  }
  if (filters.accountingPeriodId) {
    params.set("accountingPeriodId", filters.accountingPeriodId);
  }

  const payload = await apiGet<unknown>(`/api/journals?${params.toString()}`);
  return journalSummaryPageSchema.parse(payload);
}
