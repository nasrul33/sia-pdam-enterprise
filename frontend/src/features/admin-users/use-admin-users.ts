import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query/query-keys";
import {
  listAdminRoles,
  listAdminUsers,
  replaceAdminUserRoles,
  updateAdminUserStatus
} from "./admin-user-api";
import type {
  AdminUserFilters,
  UpdateAdminUserRolesPayload,
  UpdateAdminUserStatusPayload
} from "./admin-user-schema";

export function useAdminUsers(filters: AdminUserFilters, enabled: boolean) {
  return useQuery({
    queryKey: [...queryKeys.adminUsers, "list", filters],
    queryFn: () => listAdminUsers(filters),
    enabled
  });
}

export function useAdminRoles(enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.adminRoles,
    queryFn: listAdminRoles,
    enabled
  });
}

export function useUpdateAdminUserStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { userId: string; payload: UpdateAdminUserStatusPayload }) => updateAdminUserStatus(input),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.adminUsers })
  });
}

export function useReplaceAdminUserRoles() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { userId: string; payload: UpdateAdminUserRolesPayload }) => replaceAdminUserRoles(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminUsers });
      void queryClient.invalidateQueries({ queryKey: queryKeys.currentUser });
    }
  });
}
