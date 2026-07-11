import { accountPageSchema, accountingPeriodPageSchema } from "@/features/accounting/accounting-schema";
import { invoicePageSchema } from "@/features/billing/billing-schema";
import {
  connectionPageSchema,
  customerPageSchema,
  meterReadingPageSchema,
  meterRoutePageSchema,
  tariffGroupPageSchema,
  tariffVersionPageSchema
} from "@/features/operations/operations-schema";
import { paymentPageSchema } from "@/features/payments/payment-schema";
import { apiGet } from "@/lib/api/client";
import type { EntityOption } from "@/components/entity-selector/entity-selector-model";

export type EntityLookupKind =
  | "account"
  | "accounting-period"
  | "connection"
  | "customer"
  | "invoice"
  | "meter-reading"
  | "meter-route"
  | "payment"
  | "tariff-group"
  | "tariff-version";

function lookupPath(path: string, query: string): string {
  const params = new URLSearchParams({ page: "0", size: "20", search: query });
  return `${path}?${params.toString()}`;
}

function filterOptions(options: EntityOption[], query: string): EntityOption[] {
  const normalized = query.trim().toLocaleLowerCase("id-ID");
  return options.filter((option) =>
    [option.label, option.description, option.status]
      .filter((value): value is string => Boolean(value))
      .some((value) => value.toLocaleLowerCase("id-ID").includes(normalized))
  );
}

export async function loadEntityLookup(
  kind: EntityLookupKind,
  query: string,
  signal: AbortSignal
): Promise<EntityOption[]> {
  switch (kind) {
    case "account": {
      const payload = await apiGet<unknown>(lookupPath("/api/accounts", query), { signal });
      return accountPageSchema.parse(payload).items.map((account) => ({
        id: account.id,
        label: `${account.code} - ${account.name}`,
        description: `${account.type} · saldo normal ${account.normalBalance}`
      }));
    }
    case "accounting-period": {
      const payload = await apiGet<unknown>(lookupPath("/api/accounting-periods", query), { signal });
      return accountingPeriodPageSchema.parse(payload).items.map((period) => ({
        id: period.id,
        label: period.period,
        description: period.allowsPosting ? "Posting diizinkan" : "Posting ditutup",
        status: period.status
      }));
    }
    case "connection": {
      const payload = await apiGet<unknown>(lookupPath("/api/connections", query), { signal });
      return connectionPageSchema.parse(payload).items.map((connection) => ({
        id: connection.id,
        label: connection.connectionNumber,
        description: `Meter ${connection.meterNumber}`,
        status: connection.status
      }));
    }
    case "customer": {
      const payload = await apiGet<unknown>(lookupPath("/api/customers", query), { signal });
      return customerPageSchema.parse(payload).items.map((customer) => ({
        id: customer.id,
        label: `${customer.customerNumber} - ${customer.fullName}`,
        description: customer.phoneNumber ?? undefined,
        status: customer.status
      }));
    }
    case "invoice": {
      const payload = await apiGet<unknown>(lookupPath("/api/invoices", query), { signal });
      return invoicePageSchema.parse(payload).items.map((invoice) => ({
        id: invoice.id,
        label: invoice.invoiceNumber,
        description: `${invoice.period} · sisa Rp ${invoice.outstandingAmount.toLocaleString("id-ID")}`,
        status: invoice.status
      }));
    }
    case "meter-reading": {
      const path = /^\d{4}-\d{2}$/.test(query)
        ? `/api/meter-readings?page=0&size=20&period=${encodeURIComponent(query)}`
        : "/api/meter-readings?page=0&size=20";
      const payload = await apiGet<unknown>(path, { signal });
      const options = meterReadingPageSchema.parse(payload).items.map((reading) => ({
        id: reading.id,
        label: `${reading.period} · ${reading.usageM3.toLocaleString("id-ID")} m3`,
        description: `Dibaca ${new Intl.DateTimeFormat("id-ID", { dateStyle: "medium" }).format(new Date(reading.readAt))}`,
        status: reading.status
      }));
      return filterOptions(options, query);
    }
    case "meter-route": {
      const payload = await apiGet<unknown>(lookupPath("/api/meter-routes", query), { signal });
      return meterRoutePageSchema.parse(payload).items.map((route) => ({
        id: route.id,
        label: `${route.routeCode} - ${route.name}`,
        description: `Area ${route.areaCode}`
      }));
    }
    case "payment": {
      const payload = await apiGet<unknown>(lookupPath("/api/payments", query), { signal });
      return paymentPageSchema.parse(payload).items.map((payment) => ({
        id: payment.id,
        label: payment.paymentNumber,
        description: `${payment.channel} · Rp ${payment.amount.toLocaleString("id-ID")}`,
        status: payment.status
      }));
    }
    case "tariff-group": {
      const payload = await apiGet<unknown>(lookupPath("/api/tariff-groups", query), { signal });
      return tariffGroupPageSchema.parse(payload).items.map((group) => ({
        id: group.id,
        label: `${group.code} - ${group.name}`
      }));
    }
    case "tariff-version": {
      const payload = await apiGet<unknown>("/api/tariff-versions?page=0&size=20", { signal });
      const options = tariffVersionPageSchema.parse(payload).items.map((version) => ({
        id: version.id,
        label: `Efektif ${new Intl.DateTimeFormat("id-ID", { dateStyle: "medium" }).format(new Date(version.effectiveDate))}`,
        description: `Golongan ${version.tariffGroupId.slice(0, 8)}`,
        status: version.status
      }));
      return filterOptions(options, query);
    }
  }
}

export function entityLookupLoader(kind: EntityLookupKind) {
  return (query: string, signal: AbortSignal) => loadEntityLookup(kind, query, signal);
}
