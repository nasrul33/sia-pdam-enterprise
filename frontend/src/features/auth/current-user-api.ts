import { apiGet } from "@/lib/api/client";
import { currentUserSchema } from "./current-user-schema";

export async function getCurrentUser() {
  const payload = await apiGet<unknown>("/api/auth/me");
  return currentUserSchema.parse(payload);
}
