import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import ts from "typescript";

function readRoute(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

const workspaceSource = readRoute("./operations-workspaces.tsx");
const workspaceAst = ts.createSourceFile(
  "operations-workspaces.tsx",
  workspaceSource,
  ts.ScriptTarget.Latest,
  true,
  ts.ScriptKind.TSX
);

function findFunction(name: string): ts.FunctionDeclaration {
  let result: ts.FunctionDeclaration | undefined;

  function visit(node: ts.Node): void {
    if (ts.isFunctionDeclaration(node) && node.name?.text === name) {
      result = node;
      return;
    }
    ts.forEachChild(node, visit);
  }

  visit(workspaceAst);
  if (!result) {
    assert.fail(`Function ${name} was not found.`);
  }
  return result;
}

function calledIdentifiers(functionName: string): Set<string> {
  const calls = new Set<string>();

  function visit(node: ts.Node): void {
    if (ts.isCallExpression(node) && ts.isIdentifier(node.expression)) {
      calls.add(node.expression.text);
    }
    ts.forEachChild(node, visit);
  }

  visit(findFunction(functionName));
  return calls;
}

function countJsxTags(functionName: string, tagName: string): number {
  let count = 0;

  function visit(node: ts.Node): void {
    if (
      (ts.isJsxOpeningElement(node) || ts.isJsxSelfClosingElement(node)) &&
      node.tagName.getText(workspaceAst) === tagName
    ) {
      count += 1;
    }
    ts.forEachChild(node, visit);
  }

  visit(findFunction(functionName));
  return count;
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

test("customer and connection workspaces do not call cross-domain management hooks", () => {
  const customerCalls = calledIdentifiers("CustomerWorkspace");
  const connectionCalls = calledIdentifiers("ConnectionWorkspace");

  for (const forbiddenCall of [
    "useConnections",
    "useConnection",
    "useCreateConnection",
    "useConnectionWorkflow",
    "useTariffGroups",
    "useCreateTariffGroup"
  ]) {
    assert.equal(customerCalls.has(forbiddenCall), false, `CustomerWorkspace must not call ${forbiddenCall}.`);
  }

  for (const forbiddenCall of ["useCustomers", "useCustomer", "useCreateCustomer", "useTariffGroups", "useCreateTariffGroup"]) {
    assert.equal(connectionCalls.has(forbiddenCall), false, `ConnectionWorkspace must not call ${forbiddenCall}.`);
  }
});

test("tariff workspace owns tariff-group management and handles read failures", () => {
  const tariffCalls = calledIdentifiers("TariffWorkspace");
  const tariffWorkspace = findFunction("TariffWorkspace").getText(workspaceAst);

  assert.equal(tariffCalls.has("useTariffGroups"), true);
  assert.equal(tariffCalls.has("useCreateTariffGroup"), true);
  assert.match(tariffWorkspace, /tariffGroupsQuery\.isError/);
  assert.match(tariffWorkspace, /Golongan tarif tidak tersedia\./);
  assert.match(tariffWorkspace, /tariffGroupsQuery\.refetch/);
  assert.match(tariffWorkspace, /const tariffGroupsReady = tariffGroupsQuery\.isSuccess/);
  assert.ok(
    (tariffWorkspace.match(/disabled=\{[^}]*!tariffGroupsReady/g) ?? []).length >= 3,
    "Tariff-dependent controls must remain disabled until tariff groups load successfully."
  );
});

test("operations tables keep header and row cell counts aligned", () => {
  assert.equal(countJsxTags("CustomerTable", "th"), countJsxTags("CustomerTable", "td"));
  assert.equal(countJsxTags("MeterReadingTable", "th"), countJsxTags("MeterReadingTable", "td"));

  assert.doesNotMatch(workspaceSource, />Lock<\/th>/);
});
