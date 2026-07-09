import { apiGet, apiPost } from "@/lib/api/client";
import {
  connectionPageSchema,
  connectionSchema,
  customerPageSchema,
  customerSchema,
  meterReadingPageSchema,
  meterReadingSchema,
  meterRoutePageSchema,
  meterRouteSchema,
  receivableAgingSnapshotPageSchema,
  receivableAgingSnapshotSchema,
  tariffBlockSchema,
  tariffCalculationSchema,
  tariffGroupPageSchema,
  tariffGroupSchema,
  tariffVersionPageSchema,
  tariffVersionSchema,
  trialBalanceReportSchema,
  type ConnectionFilters,
  type ConnectionWorkflow,
  type CreateConnectionPayload,
  type CreateCustomerPayload,
  type CreateMeterReadingPayload,
  type CreateMeterRoutePayload,
  type CreateTariffBlockPayload,
  type CreateTariffGroupPayload,
  type CreateTariffVersionPayload,
  type CustomerFilters,
  type GenerateReceivableAgingSnapshotPayload,
  type MeterReadingFilters,
  type MeterReadingWorkflow,
  type MeterRouteFilters,
  type PageFilters,
  type ReceivableAgingFilters,
  type TariffCalculationPayload,
  type TariffVersionFilters,
  type TariffVersionWorkflow,
  type TrialBalanceFilters,
  type WorkflowReasonPayload
} from "./operations-schema";

function pageParams(filters: PageFilters): URLSearchParams {
  const params = new URLSearchParams();
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return params;
}

function setOptional(params: URLSearchParams, key: string, value: string | undefined): void {
  if (value && value.trim().length > 0) {
    params.set(key, value.trim());
  }
}

export async function listCustomers(filters: CustomerFilters) {
  const params = pageParams(filters);
  setOptional(params, "status", filters.status);
  setOptional(params, "search", filters.search);
  const payload = await apiGet<unknown>(`/api/customers?${params.toString()}`);
  return customerPageSchema.parse(payload);
}

export async function getCustomer(customerId: string) {
  const payload = await apiGet<unknown>(`/api/customers/${customerId}`);
  return customerSchema.parse(payload);
}

export async function createCustomer(payload: CreateCustomerPayload) {
  const response = await apiPost<CreateCustomerPayload, unknown>("/api/customers", payload);
  return customerSchema.parse(response);
}

export async function listTariffGroups(filters: PageFilters) {
  const payload = await apiGet<unknown>(`/api/tariff-groups?${pageParams(filters).toString()}`);
  return tariffGroupPageSchema.parse(payload);
}

export async function createTariffGroup(payload: CreateTariffGroupPayload) {
  const response = await apiPost<CreateTariffGroupPayload, unknown>("/api/tariff-groups", payload);
  return tariffGroupSchema.parse(response);
}

export async function listConnections(filters: ConnectionFilters) {
  const params = pageParams(filters);
  setOptional(params, "customerId", filters.customerId);
  setOptional(params, "status", filters.status);
  const payload = await apiGet<unknown>(`/api/connections?${params.toString()}`);
  return connectionPageSchema.parse(payload);
}

export async function getConnection(connectionId: string) {
  const payload = await apiGet<unknown>(`/api/connections/${connectionId}`);
  return connectionSchema.parse(payload);
}

export async function createConnection(payload: CreateConnectionPayload) {
  const response = await apiPost<CreateConnectionPayload, unknown>("/api/connections", payload);
  return connectionSchema.parse(response);
}

export async function submitConnectionWorkflow(input: {
  connectionId: string;
  workflow: ConnectionWorkflow;
  payload: WorkflowReasonPayload;
}) {
  const response = await apiPost<WorkflowReasonPayload, unknown>(
    `/api/connections/${input.connectionId}/${input.workflow}`,
    input.payload
  );
  return connectionSchema.parse(response);
}

export async function listMeterRoutes(filters: MeterRouteFilters) {
  const params = pageParams(filters);
  setOptional(params, "areaCode", filters.areaCode);
  const payload = await apiGet<unknown>(`/api/meter-routes?${params.toString()}`);
  return meterRoutePageSchema.parse(payload);
}

export async function getMeterRoute(routeId: string) {
  const payload = await apiGet<unknown>(`/api/meter-routes/${routeId}`);
  return meterRouteSchema.parse(payload);
}

export async function createMeterRoute(payload: CreateMeterRoutePayload) {
  const response = await apiPost<CreateMeterRoutePayload, unknown>("/api/meter-routes", payload);
  return meterRouteSchema.parse(response);
}

export async function listMeterReadings(filters: MeterReadingFilters) {
  const params = pageParams(filters);
  setOptional(params, "routeId", filters.routeId);
  setOptional(params, "period", filters.period);
  setOptional(params, "status", filters.status);
  const payload = await apiGet<unknown>(`/api/meter-readings?${params.toString()}`);
  return meterReadingPageSchema.parse(payload);
}

export async function getMeterReading(readingId: string) {
  const payload = await apiGet<unknown>(`/api/meter-readings/${readingId}`);
  return meterReadingSchema.parse(payload);
}

export async function createMeterReading(payload: CreateMeterReadingPayload) {
  const response = await apiPost<CreateMeterReadingPayload, unknown>("/api/meter-readings", payload);
  return meterReadingSchema.parse(response);
}

export async function submitMeterReadingWorkflow(input: {
  readingId: string;
  workflow: MeterReadingWorkflow;
  payload: WorkflowReasonPayload;
}) {
  const response = await apiPost<WorkflowReasonPayload, unknown>(
    `/api/meter-readings/${input.readingId}/${input.workflow}`,
    input.payload
  );
  return meterReadingSchema.parse(response);
}

export async function listTariffVersions(filters: TariffVersionFilters) {
  const params = pageParams(filters);
  setOptional(params, "tariffGroupId", filters.tariffGroupId);
  setOptional(params, "status", filters.status);
  const payload = await apiGet<unknown>(`/api/tariff-versions?${params.toString()}`);
  return tariffVersionPageSchema.parse(payload);
}

export async function getTariffVersion(tariffVersionId: string) {
  const payload = await apiGet<unknown>(`/api/tariff-versions/${tariffVersionId}`);
  return tariffVersionSchema.parse(payload);
}

export async function createTariffVersion(payload: CreateTariffVersionPayload) {
  const response = await apiPost<CreateTariffVersionPayload, unknown>("/api/tariff-versions", payload);
  return tariffVersionSchema.parse(response);
}

export async function listTariffBlocks(tariffVersionId: string) {
  const payload = await apiGet<unknown>(`/api/tariff-versions/${tariffVersionId}/blocks`);
  return tariffBlockSchema.array().parse(payload);
}

export async function addTariffBlock(input: { tariffVersionId: string; payload: CreateTariffBlockPayload }) {
  const response = await apiPost<CreateTariffBlockPayload, unknown>(
    `/api/tariff-versions/${input.tariffVersionId}/blocks`,
    input.payload
  );
  return tariffBlockSchema.parse(response);
}

export async function submitTariffVersionWorkflow(input: {
  tariffVersionId: string;
  workflow: TariffVersionWorkflow;
  payload: WorkflowReasonPayload;
}) {
  const response = await apiPost<WorkflowReasonPayload, unknown>(
    `/api/tariff-versions/${input.tariffVersionId}/${input.workflow}`,
    input.payload
  );
  return tariffVersionSchema.parse(response);
}

export async function calculateTariff(payload: TariffCalculationPayload) {
  const response = await apiPost<TariffCalculationPayload, unknown>("/api/tariff-calculations", payload);
  return tariffCalculationSchema.parse(response);
}

export async function listReceivableAgingSnapshots(filters: ReceivableAgingFilters) {
  const params = pageParams(filters);
  setOptional(params, "period", filters.period);
  const payload = await apiGet<unknown>(`/api/receivable-aging-snapshots?${params.toString()}`);
  return receivableAgingSnapshotPageSchema.parse(payload);
}

export async function getReceivableAgingSnapshot(snapshotId: string) {
  const payload = await apiGet<unknown>(`/api/receivable-aging-snapshots/${snapshotId}`);
  return receivableAgingSnapshotSchema.parse(payload);
}

export async function getReceivableAgingSnapshotByPeriod(period: string) {
  const payload = await apiGet<unknown>(`/api/receivable-aging-snapshots/by-period/${period}`);
  return receivableAgingSnapshotSchema.parse(payload);
}

export async function generateReceivableAgingSnapshot(payload: GenerateReceivableAgingSnapshotPayload) {
  const response = await apiPost<GenerateReceivableAgingSnapshotPayload, unknown>(
    "/api/receivable-aging-snapshots/generate",
    payload
  );
  return receivableAgingSnapshotSchema.parse(response);
}

export async function getTrialBalance(filters: TrialBalanceFilters) {
  const params = new URLSearchParams();
  params.set("fromDate", filters.fromDate);
  params.set("toDate", filters.toDate);
  const payload = await apiGet<unknown>(`/api/reports/trial-balance?${params.toString()}`);
  return trialBalanceReportSchema.parse(payload);
}
