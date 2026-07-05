import { useQuery } from "@tanstack/react-query";
import { getDashboardOverview } from "./dashboard-api";

export function useDashboardOverview() {
  return useQuery({
    queryKey: ["dashboard", "overview"],
    queryFn: getDashboardOverview
  });
}
