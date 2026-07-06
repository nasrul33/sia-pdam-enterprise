import type { CollectionActionStatus } from "./collection-action-schema";

export const collectionActionPermissions = {
  read: "collection-action.read",
  create: "collection-action.create",
  execute: "collection-action.execute",
  cancel: "collection-action.cancel"
} as const;

export type CollectionActionPermissionState = {
  canRead: boolean;
  canCreate: boolean;
  canExecute: boolean;
  canCancel: boolean;
};

type WorkflowSubject = {
  status: CollectionActionStatus;
};

export function resolveCollectionActionPermissions(authorities: readonly string[]): CollectionActionPermissionState {
  const authoritySet = new Set(authorities);
  return {
    canRead: authoritySet.has(collectionActionPermissions.read),
    canCreate: authoritySet.has(collectionActionPermissions.create),
    canExecute: authoritySet.has(collectionActionPermissions.execute),
    canCancel: authoritySet.has(collectionActionPermissions.cancel)
  };
}

export function allowedCollectionActionWorkflows(
  action: WorkflowSubject,
  permissions: CollectionActionPermissionState
) {
  const openOrInProgress = action.status === "OPEN" || action.status === "IN_PROGRESS";
  return {
    start: permissions.canExecute && action.status === "OPEN",
    complete: permissions.canExecute && openOrInProgress,
    cancel: permissions.canCancel && openOrInProgress
  };
}
