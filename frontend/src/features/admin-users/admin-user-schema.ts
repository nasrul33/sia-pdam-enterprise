import { z } from "zod";

const databaseUuidSchema = z.string().regex(
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/,
  "Invalid database UUID"
);

export const identityProviderStatusSchema = z.enum(["LOCAL_ONLY", "SYNCED", "SYNC_ERROR"]);

export const adminUserSchema = z.object({
  id: databaseUuidSchema,
  username: z.string().min(1),
  email: z.string().email(),
  enabled: z.boolean(),
  roles: z.array(z.string().min(1)),
  authorities: z.array(z.string().min(1)),
  identityProviderStatus: identityProviderStatusSchema,
  updatedAt: z.string().datetime()
});

export const adminRoleSchema = z.object({
  id: databaseUuidSchema,
  code: z.string().min(1),
  name: z.string().min(1),
  permissions: z.array(z.string().min(1))
});

export const adminUserPageSchema = z.object({
  items: z.array(adminUserSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalItems: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative()
});

export const adminRoleListSchema = z.array(adminRoleSchema);

export type AdminUser = z.infer<typeof adminUserSchema>;
export type AdminRole = z.infer<typeof adminRoleSchema>;
export type AdminUserPage = z.infer<typeof adminUserPageSchema>;

export type AdminUserFilters = {
  search: string;
  page: number;
  size: number;
};

export type UpdateAdminUserStatusPayload = {
  enabled: boolean;
  reason: string;
};

export type UpdateAdminUserRolesPayload = {
  roleCodes: string[];
  reason: string;
};
