import { apiGet, apiPatch, apiPut } from "@/lib/api/client";
import {
  adminRoleListSchema,
  adminUserPageSchema,
  adminUserSchema,
  type AdminUserFilters,
  type UpdateAdminUserRolesPayload,
  type UpdateAdminUserStatusPayload
} from "./admin-user-schema";

export async function listAdminUsers(filters: AdminUserFilters) {
  const params = new URLSearchParams({
    search: filters.search,
    page: String(filters.page),
    size: String(filters.size)
  });
  const payload = await apiGet<unknown>(`/api/admin/users?${params.toString()}`);
  return adminUserPageSchema.parse(payload);
}

export async function listAdminRoles() {
  const payload = await apiGet<unknown>("/api/admin/roles");
  return adminRoleListSchema.parse(payload);
}

export async function updateAdminUserStatus(input: {
  userId: string;
  payload: UpdateAdminUserStatusPayload;
}) {
  const payload = await apiPatch<UpdateAdminUserStatusPayload, unknown>(
    `/api/admin/users/${input.userId}/status`,
    input.payload
  );
  return adminUserSchema.parse(payload);
}

export async function replaceAdminUserRoles(input: {
  userId: string;
  payload: UpdateAdminUserRolesPayload;
}) {
  const payload = await apiPut<UpdateAdminUserRolesPayload, unknown>(
    `/api/admin/users/${input.userId}/roles`,
    input.payload
  );
  return adminUserSchema.parse(payload);
}
