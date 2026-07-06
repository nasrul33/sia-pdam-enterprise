import { apiGet, apiPost } from "@/lib/api/client";
import {
  accountingPeriodPageSchema,
  accountingPeriodSchema,
  accountPageSchema,
  accountSchema,
  journalSchema,
  journalSummaryPageSchema,
  type AccountingPeriodWorkflow,
  type CreateAccountingPeriodPayload,
  type CreateAccountPayload,
  type CreateJournalPayload,
  type JournalFilters,
  type PageFilters,
  type WorkflowReasonPayload
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

export async function getJournal(journalId: string) {
  const payload = await apiGet<unknown>(`/api/journals/${journalId}`);
  return journalSchema.parse(payload);
}

export async function createAccount(payload: CreateAccountPayload) {
  const response = await apiPost<CreateAccountPayload, unknown>("/api/accounts", payload);
  return accountSchema.parse(response);
}

export async function createAccountingPeriod(payload: CreateAccountingPeriodPayload) {
  const response = await apiPost<CreateAccountingPeriodPayload, unknown>("/api/accounting-periods", payload);
  return accountingPeriodSchema.parse(response);
}

export async function submitAccountingPeriodWorkflow(input: {
  periodId: string;
  workflow: AccountingPeriodWorkflow;
  payload: WorkflowReasonPayload;
}) {
  const response = await apiPost<WorkflowReasonPayload, unknown>(
    `/api/accounting-periods/${input.periodId}/${input.workflow}`,
    input.payload
  );
  return accountingPeriodSchema.parse(response);
}

export async function createJournal(payload: CreateJournalPayload) {
  const response = await apiPost<CreateJournalPayload, unknown>("/api/journals", payload);
  return journalSchema.parse(response);
}

export async function postJournal(input: { journalId: string; payload: WorkflowReasonPayload }) {
  const response = await apiPost<WorkflowReasonPayload, unknown>(`/api/journals/${input.journalId}/post`, input.payload);
  return journalSchema.parse(response);
}
