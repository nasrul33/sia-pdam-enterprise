import assert from "node:assert/strict";
import test from "node:test";

import { isOidcAuthMode } from "./auth-mode.ts";

test("only explicit oidc mode enables the NextAuth browser session", () => {
  assert.equal(isOidcAuthMode("oidc"), true);
  assert.equal(isOidcAuthMode("basic"), false);
  assert.equal(isOidcAuthMode(undefined), false);
});
