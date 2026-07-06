import assert from "node:assert/strict";
import { test } from "node:test";
import {
  allowedCollectionActionWorkflows,
  collectionActionPermissions,
  resolveCollectionActionPermissions
} from "./collection-action-permissions";

test("resolveCollectionActionPermissions maps authorities to collection action controls", () => {
  assert.deepEqual(
    resolveCollectionActionPermissions([
      "collection-action.read",
      "collection-action.create",
      "collection-action.execute"
    ]),
    {
      canRead: true,
      canCreate: true,
      canExecute: true,
      canCancel: false
    }
  );
});

test("allowedCollectionActionWorkflows hides actions without matching permission", () => {
  const permissions = resolveCollectionActionPermissions([
    collectionActionPermissions.read,
    collectionActionPermissions.execute
  ]);

  assert.deepEqual(allowedCollectionActionWorkflows({ status: "OPEN" }, permissions), {
    start: true,
    complete: true,
    cancel: false
  });
});

test("allowedCollectionActionWorkflows preserves status guards when permission exists", () => {
  const permissions = resolveCollectionActionPermissions([
    collectionActionPermissions.execute,
    collectionActionPermissions.cancel
  ]);

  assert.deepEqual(allowedCollectionActionWorkflows({ status: "COMPLETED" }, permissions), {
    start: false,
    complete: false,
    cancel: false
  });
});
