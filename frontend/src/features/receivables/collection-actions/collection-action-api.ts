import { apiGet, apiPost } from "@/lib/api/client";
import {
  collectionActionPageSchema,
  collectionActionSchema,
  type CollectionAction,
  type CollectionActionFilters,
  type CollectionActionWorkflow,
  type CollectionActionWorkflowPayload,
  type CreateCollectionActionPayload
} from "./collection-action-schema";

function collectionActionListPath(filters: CollectionActionFilters): string {
  const params = new URLSearchParams();
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));

  if (filters.status) {
    params.set("status", filters.status);
  }
  if (filters.customerId) {
    params.set("customerId", filters.customerId);
  }
  if (filters.invoiceId) {
    params.set("invoiceId", filters.invoiceId);
  }

  return `/api/collection-actions?${params.toString()}`;
}

export async function listCollectionActions(filters: CollectionActionFilters) {
  const payload = await apiGet<unknown>(collectionActionListPath(filters));
  return collectionActionPageSchema.parse(payload);
}

export async function createCollectionAction(payload: CreateCollectionActionPayload): Promise<CollectionAction> {
  const response = await apiPost<CreateCollectionActionPayload, unknown>("/api/collection-actions", payload);
  return collectionActionSchema.parse(response);
}

export async function submitCollectionActionWorkflow(input: {
  actionId: string;
  workflow: CollectionActionWorkflow;
  payload: CollectionActionWorkflowPayload;
}): Promise<CollectionAction> {
  const response = await apiPost<CollectionActionWorkflowPayload, unknown>(
    `/api/collection-actions/${input.actionId}/${input.workflow}`,
    input.payload
  );
  return collectionActionSchema.parse(response);
}
