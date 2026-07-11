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

  assert.equal(canChangeUserStatus({ username: "admin" }, "admin", fullPermissions), false);
  assert.equal(canChangeUserStatus({ username: "operator" }, "admin", fullPermissions), true);
  assert.equal(canReplaceUserRoles(fullPermissions), true);
  assert.equal(canReplaceUserRoles(resolveAdminUserPermissions(["user.read"])), false);
});
