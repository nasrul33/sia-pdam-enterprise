export const adminUserPermissionCodes = {
  read: "user.read",
  manage: "user.manage",
  roleManage: "role.manage"
} as const;

export type AdminUserPermissionState = {
  canReadUsers: boolean;
  canManageUsers: boolean;
  canManageRoles: boolean;
};

export function resolveAdminUserPermissions(authorities: readonly string[]): AdminUserPermissionState {
  const authoritySet = new Set(authorities);
  return {
    canReadUsers: authoritySet.has(adminUserPermissionCodes.read),
    canManageUsers: authoritySet.has(adminUserPermissionCodes.manage),
    canManageRoles: authoritySet.has(adminUserPermissionCodes.roleManage)
  };
}

export function canChangeUserStatus(
  user: { username: string; identityProviderStatus: string },
  currentUsername: string | null,
  permissions: AdminUserPermissionState
): boolean {
  return permissions.canManageUsers &&
    user.identityProviderStatus !== "EXTERNAL_MANAGED" &&
    user.username.toLowerCase() !== currentUsername?.toLowerCase();
}

export function canReplaceUserRoles(
  user: { identityProviderStatus: string },
  permissions: AdminUserPermissionState
): boolean {
  return permissions.canManageRoles && user.identityProviderStatus !== "EXTERNAL_MANAGED";
}
