import assert from "node:assert/strict";
import test from "node:test";
import {
  canChangeUserStatus,
  canReplaceUserRoles,
  resolveAdminUserPermissions
} from "./admin-user-permissions.ts";

test("resolveAdminUserPermissions maps granular administration authorities", () => {
  assert.deepEqual(resolveAdminUserPermissions(["user.read", "role.manage"]), {
    canReadUsers: true,
    canManageUsers: false,
    canManageRoles: true
  });
});

test("admin user guards block self status mutation and require role permission", () => {
  const fullPermissions = resolveAdminUserPermissions(["user.read", "user.manage", "role.manage"]);

  const localAdmin = { username: "admin", identityProviderStatus: "LOCAL_ONLY" };
  const localOperator = { username: "operator", identityProviderStatus: "LOCAL_ONLY" };
  const externalOperator = { username: "operator", identityProviderStatus: "EXTERNAL_MANAGED" };

  assert.equal(canChangeUserStatus(localAdmin, "admin", fullPermissions), false);
  assert.equal(canChangeUserStatus(localOperator, "admin", fullPermissions), true);
  assert.equal(canChangeUserStatus(externalOperator, "admin", fullPermissions), false);
  assert.equal(canReplaceUserRoles(localOperator, fullPermissions), true);
  assert.equal(canReplaceUserRoles(externalOperator, fullPermissions), false);
  assert.equal(canReplaceUserRoles(localOperator, resolveAdminUserPermissions(["user.read"])), false);
});
