import { apiGet } from "@/lib/api/client";
import { dashboardOverviewSchema, type DashboardOverview } from "./dashboard-schema";

export async function getDashboardOverview(): Promise<DashboardOverview> {
  const payload = await apiGet<unknown>("/api/dashboard/overview");
  return dashboardOverviewSchema.parse(payload);
}
