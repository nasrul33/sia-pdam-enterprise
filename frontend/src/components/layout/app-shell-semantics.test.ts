import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const workspaceFiles = [
  "../../app/page.tsx",
  "../../features/operations/operations-workspaces.tsx",
  "../../features/accounting/accounting-workspace.tsx",
  "../../features/admin-users/admin-user-workspace.tsx",
  "../../features/billing/billing-workspace.tsx",
  "../../features/blueprint/blueprint-workspaces.tsx",
  "../../features/payments/payment-workspace.tsx",
  "../../features/receivables/collection-actions/collection-action-workspace.tsx"
] as const;

function readSource(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

test("AppShell exclusively owns the document main landmark and route title is not an h1", () => {
  const appShellSource = readSource("./app-shell.tsx");

  assert.equal(appShellSource.match(/<main\b/g)?.length, 1);
  assert.doesNotMatch(appShellSource, /<h1\b/);
});

test("AppShell page and workspace children do not render nested main landmarks", () => {
  for (const workspaceFile of workspaceFiles) {
    const workspaceSource = readSource(workspaceFile);

    assert.doesNotMatch(workspaceSource, /<\/?main\b/);
  }
});
