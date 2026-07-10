import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query/query-keys";
import {
  addTariffBlock,
  calculateTariff,
  createConnection,
  createCustomer,
  createMeterReading,
  createMeterRoute,
  createTariffGroup,
  createTariffVersion,
  generateReceivableAgingSnapshot,
  getConnection,
  getCustomer,
  getMeterReading,
  getMeterRoute,
  importMeterReadings,
  getReceivableAgingSnapshot,
  getReceivableAgingSnapshotByPeriod,
  getTariffVersion,
  getTrialBalance,
  listConnections,
  listCustomers,
  listMeterReadings,
  listMeterRoutes,
  listReceivableAgingSnapshots,
  listTariffBlocks,
  listTariffGroups,
  listTariffVersions,
  submitConnectionWorkflow,
  submitMeterReadingWorkflow,
  submitTariffVersionWorkflow
} from "./operations-api";
import type {
  ConnectionFilters,
  ConnectionWorkflow,
  CreateConnectionPayload,
  CreateCustomerPayload,
  CreateMeterReadingPayload,
  CreateMeterRoutePayload,
  ImportMeterReadingsPayload,
  CreateTariffBlockPayload,
  CreateTariffGroupPayload,
  CreateTariffVersionPayload,
  CustomerFilters,
  GenerateReceivableAgingSnapshotPayload,
  MeterReadingFilters,
  MeterReadingWorkflow,
  MeterRouteFilters,
  PageFilters,
  ReceivableAgingFilters,
  TariffCalculationPayload,
  TariffVersionFilters,
  TariffVersionWorkflow,
  TrialBalanceFilters,
  WorkflowReasonPayload
} from "./operations-schema";

export function useCustomers(filters: CustomerFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.customers, "list", filters],
    queryFn: () => listCustomers(filters),
    enabled
  });
}

export function useCustomer(customerId: string | null, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.customers, "detail", customerId],
    queryFn: () => getCustomer(customerId ?? ""),
    enabled: enabled && Boolean(customerId)
  });
}

export function useCreateCustomer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateCustomerPayload) => createCustomer(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.customers });
    }
  });
}

export function useTariffGroups(filters: PageFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.tariffGroups, "list", filters],
    queryFn: () => listTariffGroups(filters),
    enabled
  });
}

export function useCreateTariffGroup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateTariffGroupPayload) => createTariffGroup(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tariffGroups });
    }
  });
}

export function useConnections(filters: ConnectionFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.connections, "list", filters],
    queryFn: () => listConnections(filters),
    enabled
  });
}

export function useConnection(connectionId: string | null, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.connections, "detail", connectionId],
    queryFn: () => getConnection(connectionId ?? ""),
    enabled: enabled && Boolean(connectionId)
  });
}

export function useCreateConnection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateConnectionPayload) => createConnection(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.connections });
    }
  });
}

export function useConnectionWorkflow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { connectionId: string; workflow: ConnectionWorkflow; payload: WorkflowReasonPayload }) =>
      submitConnectionWorkflow(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.connections });
    }
  });
}

export function useMeterRoutes(filters: MeterRouteFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.meterRoutes, "list", filters],
    queryFn: () => listMeterRoutes(filters),
    enabled
  });
}

export function useMeterRoute(routeId: string | null, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.meterRoutes, "detail", routeId],
    queryFn: () => getMeterRoute(routeId ?? ""),
    enabled: enabled && Boolean(routeId)
  });
}

export function useCreateMeterRoute() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateMeterRoutePayload) => createMeterRoute(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.meterRoutes });
    }
  });
}

export function useMeterReadings(filters: MeterReadingFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.meterReadings, "list", filters],
    queryFn: () => listMeterReadings(filters),
    enabled
  });
}

export function useMeterReading(readingId: string | null, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.meterReadings, "detail", readingId],
    queryFn: () => getMeterReading(readingId ?? ""),
    enabled: enabled && Boolean(readingId)
  });
}

export function useCreateMeterReading() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateMeterReadingPayload) => createMeterReading(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.meterReadings });
    }
  });
}

export function useImportMeterReadings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ImportMeterReadingsPayload) => importMeterReadings(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.meterReadings });
    }
  });
}

export function useMeterReadingWorkflow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { readingId: string; workflow: MeterReadingWorkflow; payload: WorkflowReasonPayload }) =>
      submitMeterReadingWorkflow(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.meterReadings });
      void queryClient.invalidateQueries({ queryKey: queryKeys.billingBatches });
    }
  });
}

export function useTariffVersions(filters: TariffVersionFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.tariffVersions, "list", filters],
    queryFn: () => listTariffVersions(filters),
    enabled
  });
}

export function useTariffVersion(tariffVersionId: string | null, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.tariffVersions, "detail", tariffVersionId],
    queryFn: () => getTariffVersion(tariffVersionId ?? ""),
    enabled: enabled && Boolean(tariffVersionId)
  });
}

export function useCreateTariffVersion() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateTariffVersionPayload) => createTariffVersion(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tariffVersions });
    }
  });
}

export function useTariffBlocks(tariffVersionId: string | null, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.tariffBlocks, "version", tariffVersionId],
    queryFn: () => listTariffBlocks(tariffVersionId ?? ""),
    enabled: enabled && Boolean(tariffVersionId)
  });
}

export function useAddTariffBlock() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { tariffVersionId: string; payload: CreateTariffBlockPayload }) => addTariffBlock(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tariffBlocks });
      void queryClient.invalidateQueries({ queryKey: queryKeys.tariffVersions });
    }
  });
}

export function useTariffVersionWorkflow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { tariffVersionId: string; workflow: TariffVersionWorkflow; payload: WorkflowReasonPayload }) =>
      submitTariffVersionWorkflow(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tariffVersions });
    }
  });
}

export function useTariffCalculation() {
  return useMutation({
    mutationFn: (payload: TariffCalculationPayload) => calculateTariff(payload)
  });
}

export function useReceivableAgingSnapshots(filters: ReceivableAgingFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.receivableAgingSnapshots, "list", filters],
    queryFn: () => listReceivableAgingSnapshots(filters),
    enabled
  });
}

export function useReceivableAgingSnapshot(snapshotId: string | null, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.receivableAgingSnapshots, "detail", snapshotId],
    queryFn: () => getReceivableAgingSnapshot(snapshotId ?? ""),
    enabled: enabled && Boolean(snapshotId)
  });
}

export function useReceivableAgingSnapshotByPeriod(period: string | null, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.receivableAgingSnapshots, "period", period],
    queryFn: () => getReceivableAgingSnapshotByPeriod(period ?? ""),
    enabled: enabled && Boolean(period)
  });
}

export function useGenerateReceivableAgingSnapshot() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: GenerateReceivableAgingSnapshotPayload) => generateReceivableAgingSnapshot(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.receivableAgingSnapshots });
    }
  });
}

export function useTrialBalance(filters: TrialBalanceFilters, enabled = true) {
  return useQuery({
    queryKey: [...queryKeys.reports, "trial-balance", filters],
    queryFn: () => getTrialBalance(filters),
    enabled
  });
}
