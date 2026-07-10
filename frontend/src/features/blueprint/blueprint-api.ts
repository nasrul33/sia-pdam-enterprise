import { apiGet, apiPost } from "@/lib/api/client";

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type EntityRecord = Record<string, unknown>;

export type FinancialStatementLine = {
  accountId: string;
  accountCode: string;
  accountName: string;
  accountType: string;
  amount: number;
};

export type FinancialStatementsReport = {
  fromDate: string;
  toDate: string;
  assets: FinancialStatementLine[];
  liabilities: FinancialStatementLine[];
  equity: FinancialStatementLine[];
  revenue: FinancialStatementLine[];
  expenses: FinancialStatementLine[];
  totalAssets: number;
  totalLiabilities: number;
  totalEquity: number;
  totalRevenue: number;
  totalExpenses: number;
  netIncome: number;
  trialBalanceBalanced: boolean;
  generatedAt: string;
};

export type TaxRecapReport = {
  fromDate: string;
  toDate: string;
  grossRevenue: number;
  deductibleExpenses: number;
  taxableIncome: number;
  incomeTaxRate: number;
  estimatedIncomeTax: number;
  generatedAt: string;
};

export async function listBlueprintRecords(endpoint: string): Promise<PageResponse<EntityRecord>> {
  return apiGet<PageResponse<EntityRecord>>(endpoint);
}

export async function postBlueprintCommand<TPayload extends Record<string, unknown>, TResponse = EntityRecord>(
  endpoint: string,
  payload: TPayload
): Promise<TResponse> {
  return apiPost<TPayload, TResponse>(endpoint, payload);
}

export async function getFinancialStatements(fromDate: string, toDate: string): Promise<FinancialStatementsReport> {
  const params = new URLSearchParams({ fromDate, toDate });
  return apiGet<FinancialStatementsReport>(`/api/reports/financial-statements?${params.toString()}`);
}

export async function getTaxRecap(fromDate: string, toDate: string): Promise<TaxRecapReport> {
  const params = new URLSearchParams({ fromDate, toDate });
  return apiGet<TaxRecapReport>(`/api/reports/tax-recap?${params.toString()}`);
}

export async function verifyAuditChain() {
  return apiGet<{ valid: boolean; entriesChecked: number; firstBreakSequence: number | null; status: string }>(
    "/api/audit-chain/verify"
  );
}
