import assert from "node:assert/strict";
import test from "node:test";

import { createAuthOptions } from "../../auth.ts";

test("basic mode does not require OIDC credentials", () => {
  const options = createAuthOptions({ AUTH_MODE: "basic" });

  assert.equal(options.providers.length, 0);
  assert.equal(options.session?.strategy, "jwt");
});

test("OIDC mode rejects incomplete server configuration", () => {
  assert.throws(
    () => createAuthOptions({ AUTH_MODE: "oidc" }),
    /AUTH_SECRET is required/
  );
  assert.throws(
    () =>
      createAuthOptions({
        AUTH_MODE: "oidc",
        AUTH_SECRET: "test-auth-secret-with-at-least-32-characters",
        KEYCLOAK_CLIENT_ID: "sia-pdam",
        KEYCLOAK_CLIENT_SECRET: "client-secret",
        KEYCLOAK_ISSUER_URI: ""
      }),
    /KEYCLOAK_ISSUER_URI is required/
  );
});

test("OIDC mode creates the Keycloak provider with an encrypted JWT session", () => {
  const options = createAuthOptions({
    AUTH_MODE: "oidc",
    AUTH_SECRET: "test-auth-secret-with-at-least-32-characters",
    KEYCLOAK_CLIENT_ID: "sia-pdam",
    KEYCLOAK_CLIENT_SECRET: "client-secret",
    KEYCLOAK_ISSUER_URI: "https://identity.example.test/realms/sia"
  });

  assert.equal(options.providers.length, 1);
  assert.equal(options.providers[0]?.id, "keycloak");
  assert.equal(options.session?.strategy, "jwt");
});
