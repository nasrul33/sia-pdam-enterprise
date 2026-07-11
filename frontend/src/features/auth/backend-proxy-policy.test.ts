import assert from "node:assert/strict";
import test from "node:test";

import {
  buildBackendRequestHeaders,
  isUsableAccessToken,
  normalizeBackendPath
} from "./backend-proxy-policy.ts";

test("normalizeBackendPath rejects traversal and empty segments", () => {
  assert.equal(normalizeBackendPath([]), null);
  assert.equal(normalizeBackendPath(["api", "..", "users"]), null);
  assert.equal(normalizeBackendPath(["api", "", "users"]), null);
  assert.equal(normalizeBackendPath(["api", "users/admin"]), null);
  assert.equal(normalizeBackendPath(["api", "users\\admin"]), null);
});

test("normalizeBackendPath encodes each accepted path segment", () => {
  assert.equal(normalizeBackendPath(["api", "billing batches", "A-01"]), "api/billing%20batches/A-01");
});

test("isUsableAccessToken rejects missing and near-expiry tokens", () => {
  const now = 1_000_000;

  assert.equal(isUsableAccessToken(undefined, now), false);
  assert.equal(isUsableAccessToken({ accessToken: "", accessTokenExpiresAt: now + 60_000 }, now), false);
  assert.equal(isUsableAccessToken({ accessToken: "token", accessTokenExpiresAt: now + 4_999 }, now), false);
  assert.equal(isUsableAccessToken({ accessToken: "token", accessTokenExpiresAt: now + 5_001 }, now), true);
});

test("buildBackendRequestHeaders forwards only allowlisted headers and injects bearer token", () => {
  const source = new Headers({
    accept: "application/json",
    cookie: "session=secret",
    authorization: "Basic leaked",
    "content-type": "application/json",
    "idempotency-key": "idem-1",
    "x-forwarded-for": "127.0.0.1",
    "x-request-id": "request-1"
  });

  const result = buildBackendRequestHeaders(source, "server-token");

  assert.equal(result.get("authorization"), "Bearer server-token");
  assert.equal(result.get("accept"), "application/json");
  assert.equal(result.get("content-type"), "application/json");
  assert.equal(result.get("idempotency-key"), "idem-1");
  assert.equal(result.get("x-request-id"), "request-1");
  assert.equal(result.has("cookie"), false);
  assert.equal(result.has("x-forwarded-for"), false);
});
