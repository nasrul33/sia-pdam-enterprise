import { z } from "zod";

export const dashboardMetricSchema = z.object({
  label: z.string().min(1),
  value: z.string().min(1),
  helper: z.string().min(1),
  tone: z.enum(["success", "warning", "danger", "info", "neutral"])
});

export const moduleHealthSchema = z.object({
  module: z.string().min(1),
  owner: z.string().min(1),
  status: z.enum(["ready", "in_progress", "blocked", "planned"]),
  guardrail: z.string().min(1)
});

export const qualityGateSchema = z.object({
  name: z.string().min(1),
  command: z.string().min(1),
  status: z.enum(["configured", "pending", "blocked"])
});

export const riskItemSchema = z.object({
  code: z.string().min(1),
  description: z.string().min(1),
  severity: z.enum(["critical", "high", "medium", "low"])
});

export const dashboardOverviewSchema = z.object({
  generatedAt: z.string().min(1),
  metrics: z.array(dashboardMetricSchema),
  modules: z.array(moduleHealthSchema),
  qualityGates: z.array(qualityGateSchema),
  risks: z.array(riskItemSchema)
});

export type DashboardMetric = z.infer<typeof dashboardMetricSchema>;
export type ModuleHealth = z.infer<typeof moduleHealthSchema>;
export type QualityGate = z.infer<typeof qualityGateSchema>;
export type RiskItem = z.infer<typeof riskItemSchema>;
export type DashboardOverview = z.infer<typeof dashboardOverviewSchema>;
