const FORWARDED_REQUEST_HEADERS = ["accept", "content-type", "idempotency-key", "x-request-id"] as const;
const TOKEN_EXPIRY_TOLERANCE_MS = 5_000;

export type BackendAccessToken = {
  accessToken?: unknown;
  accessTokenExpiresAt?: unknown;
};

export function normalizeBackendPath(segments: readonly string[]): string | null {
  if (
    segments.length === 0 ||
    segments.some(
      (segment) =>
        !segment ||
        segment === "." ||
        segment === ".." ||
        segment.includes("/") ||
        segment.includes("\\")
    )
  ) {
    return null;
  }

  return segments.map(encodeURIComponent).join("/");
}

export function isUsableAccessToken(
  token: BackendAccessToken | null | undefined,
  now = Date.now()
): token is { accessToken: string; accessTokenExpiresAt: number } {
  return (
    typeof token?.accessToken === "string" &&
    token.accessToken.length > 0 &&
    typeof token.accessTokenExpiresAt === "number" &&
    token.accessTokenExpiresAt > now + TOKEN_EXPIRY_TOLERANCE_MS
  );
}

export function buildBasicAuthorization(username: string | undefined, password: string | undefined): string | null {
  if (
    !username ||
    !password ||
    username.includes(":") ||
    /[\r\n]/.test(username) ||
    /[\r\n]/.test(password)
  ) {
    return null;
  }
  return `Basic ${Buffer.from(`${username}:${password}`, "utf8").toString("base64")}`;
}

export function buildBackendRequestHeaders(source: Headers, authorization: string): Headers {
  const headers = new Headers({ Authorization: authorization });
  FORWARDED_REQUEST_HEADERS.forEach((name) => {
    const value = source.get(name);
    if (value) {
      headers.set(name, value);
    }
  });
  return headers;
}
