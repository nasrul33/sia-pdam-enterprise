import type { NextAuthOptions } from "next-auth";
import KeycloakProvider from "next-auth/providers/keycloak";

const OIDC_MODE = "oidc";
type AuthEnvironment = Readonly<Record<string, string | undefined>>;
const createKeycloakProvider =
  typeof KeycloakProvider === "function"
    ? KeycloakProvider
    : (KeycloakProvider as unknown as { default: typeof KeycloakProvider }).default;

function requiredEnvironment(name: string, value: string | undefined): string {
  if (!value?.trim()) {
    throw new Error(`${name} is required when OIDC authentication is enabled.`);
  }
  return value.trim();
}

export function createAuthOptions(environment: AuthEnvironment = process.env): NextAuthOptions {
  const oidcEnabled = environment.AUTH_MODE === OIDC_MODE;
  const authSecret = oidcEnabled ? requiredEnvironment("AUTH_SECRET", environment.AUTH_SECRET) : environment.AUTH_SECRET;
  const providers = oidcEnabled
    ? [
        createKeycloakProvider({
          clientId: requiredEnvironment("KEYCLOAK_CLIENT_ID", environment.KEYCLOAK_CLIENT_ID),
          clientSecret: requiredEnvironment("KEYCLOAK_CLIENT_SECRET", environment.KEYCLOAK_CLIENT_SECRET),
          issuer: requiredEnvironment("KEYCLOAK_ISSUER_URI", environment.KEYCLOAK_ISSUER_URI)
        })
      ]
    : [];

  return {
    providers,
    secret: authSecret,
    session: { strategy: "jwt" },
    callbacks: {
      async jwt({ token, account }) {
        if (account) {
          token.accessToken = account.access_token;
          token.accessTokenExpiresAt = account.expires_at ? account.expires_at * 1000 : 0;
        }
        return token;
      }
    }
  };
}

export const authOptions = createAuthOptions();
