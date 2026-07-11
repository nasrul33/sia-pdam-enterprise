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
  user: { username: string },
  currentUsername: string | null,
  permissions: AdminUserPermissionState
): boolean {
  return permissions.canManageUsers && user.username.toLowerCase() !== currentUsername?.toLowerCase();
}

export function canReplaceUserRoles(permissions: AdminUserPermissionState): boolean {
  return permissions.canManageRoles;
}
