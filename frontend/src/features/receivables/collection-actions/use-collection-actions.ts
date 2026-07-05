import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query/query-keys";
import {
  createCollectionAction,
  listCollectionActions,
  submitCollectionActionWorkflow
} from "./collection-action-api";
import type {
  CollectionActionFilters,
  CollectionActionWorkflow,
  CollectionActionWorkflowPayload,
  CreateCollectionActionPayload
} from "./collection-action-schema";

export function useCollectionActions(filters: CollectionActionFilters) {
  return useQuery({
    queryKey: [...queryKeys.collectionActions, "list", filters],
    queryFn: () => listCollectionActions(filters)
  });
}

export function useCreateCollectionAction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateCollectionActionPayload) => createCollectionAction(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.collectionActions });
    }
  });
}

export function useCollectionActionWorkflow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      actionId: string;
      workflow: CollectionActionWorkflow;
      payload: CollectionActionWorkflowPayload;
    }) => submitCollectionActionWorkflow(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.collectionActions });
    }
  });
}
