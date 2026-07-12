import assert from "node:assert/strict";
import test from "node:test";

import { parseStoredTheme, resolveTheme, THEME_STORAGE_KEY } from "./theme.ts";

test("stored light or dark preference overrides the operating system", () => {
  assert.equal(resolveTheme("light", true), "light");
  assert.equal(resolveTheme("dark", false), "dark");
});

test("missing or invalid preference follows the operating system", () => {
  assert.equal(resolveTheme(null, true), "dark");
  assert.equal(resolveTheme(undefined, false), "light");
  assert.equal(resolveTheme("unsupported", true), "dark");
});

test("theme storage contract accepts only supported values", () => {
  assert.equal(THEME_STORAGE_KEY, "sia-pdam-theme");
  assert.equal(parseStoredTheme("dark"), "dark");
  assert.equal(parseStoredTheme("light"), "light");
  assert.equal(parseStoredTheme("system"), null);
});
