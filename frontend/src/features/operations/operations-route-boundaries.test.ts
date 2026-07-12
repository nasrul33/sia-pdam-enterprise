import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

function readRoute(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

test("customer and connection routes use separate domain workspaces", () => {
  const customerRoute = readRoute("../../app/customers/page.tsx");
  const connectionRoute = readRoute("../../app/connections/page.tsx");

  assert.match(customerRoute, /CustomerWorkspace/);
  assert.doesNotMatch(customerRoute, /ConnectionWorkspace|MasterDataWorkspace/);
  assert.match(connectionRoute, /ConnectionWorkspace/);
  assert.doesNotMatch(connectionRoute, /CustomerWorkspace|MasterDataWorkspace/);
});

test("tariff route remains owned by TariffWorkspace", () => {
  const tariffRoute = readRoute("../../app/tariffs/page.tsx");

  assert.match(tariffRoute, /TariffWorkspace/);
  assert.doesNotMatch(tariffRoute, /CustomerWorkspace|ConnectionWorkspace|MasterDataWorkspace/);
});
