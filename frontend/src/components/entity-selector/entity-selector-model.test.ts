import assert from "node:assert/strict";
import test from "node:test";

import {
  initialLookupState,
  mergeSelectedOption,
  normalizeLookupQuery,
  reduceLookup,
  type EntityOption
} from "./entity-selector-model.ts";

const oldOptions: EntityOption[] = [{ id: "old", label: "Invoice Lama" }];
const latestOptions: EntityOption[] = [{ id: "latest", label: "INV-02" }];

test("stale lookup response cannot replace latest query", () => {
  const firstRequest = reduceLookup(initialLookupState, { type: "request", requestId: 1, query: "INV-01" });
  const latestRequest = reduceLookup(firstRequest, { type: "request", requestId: 2, query: "INV-02" });
  const staleResult = reduceLookup(latestRequest, { type: "success", requestId: 1, options: oldOptions });

  assert.deepEqual(staleResult, latestRequest);
  assert.deepEqual(
    reduceLookup(latestRequest, { type: "success", requestId: 2, options: latestOptions }).options,
    latestOptions
  );
});

test("selected option remains visible outside current search page", () => {
  const selected = { id: "selected", label: "Pelanggan Terpilih" } satisfies EntityOption;

  assert.deepEqual(mergeSelectedOption([], selected), [selected]);
  assert.deepEqual(mergeSelectedOption([{ id: "selected", label: "Pelanggan Terpilih" }], selected), [selected]);
});

test("normalizeLookupQuery trims query and enforces minimum length", () => {
  assert.equal(normalizeLookupQuery(" A "), null);
  assert.equal(normalizeLookupQuery("  INV-02  "), "INV-02");
});

test("latest error replaces loading without discarding selected option", () => {
  const selected = { id: "selected", label: "Akun Kas" } satisfies EntityOption;
  const loading = reduceLookup(
    { ...initialLookupState, selected },
    { type: "request", requestId: 3, query: "kas" }
  );
  const failed = reduceLookup(loading, { type: "error", requestId: 3, message: "Lookup gagal." });

  assert.equal(failed.loading, false);
  assert.equal(failed.error, "Lookup gagal.");
  assert.deepEqual(failed.selected, selected);
});
