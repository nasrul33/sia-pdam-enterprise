const AUTH_MODE = process.env.NEXT_PUBLIC_DEV_AUTH_MODE ?? "basic";
const API_BASE_URL = "/api/backend";

export class ApiClientError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly payload: unknown
  ) {
    super(message);
    this.name = "ApiClientError";
  }
}

async function readPayload(response: Response): Promise<unknown> {
  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    return response.json() as Promise<unknown>;
  }

  const text = await response.text();
  return text.length > 0 ? text : null;
}

function isErrorPayload(payload: unknown): payload is { message?: unknown; code?: unknown } {
  return typeof payload === "object" && payload !== null;
}

function applyDefaultHeaders(headers: Headers): Headers {
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  return headers;
}

async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = applyDefaultHeaders(new Headers(init?.headers));

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    credentials: "include",
    cache: "no-store"
  });

  if (!response.ok) {
    const payload = await readPayload(response);
    const message =
      isErrorPayload(payload) && typeof payload.message === "string"
        ? payload.message
        : `API request failed with status ${response.status}`;
    redirectToOidcSignIn(response.status);
    throw new ApiClientError(message, response.status, payload);
  }

  return response.json() as Promise<T>;
}

async function apiRequestText(path: string, init?: RequestInit): Promise<string> {
  const headers = applyDefaultHeaders(new Headers(init?.headers));

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    credentials: "include",
    cache: "no-store"
  });

  if (!response.ok) {
    const payload = await readPayload(response);
    const message =
      isErrorPayload(payload) && typeof payload.message === "string"
        ? payload.message
        : `API request failed with status ${response.status}`;
    redirectToOidcSignIn(response.status);
    throw new ApiClientError(message, response.status, payload);
  }

  return response.text();
}

function redirectToOidcSignIn(status: number): void {
  if (status !== 401 || AUTH_MODE !== "oidc" || typeof window === "undefined") {
    return;
  }
  const callbackUrl = `${window.location.pathname}${window.location.search}`;
  window.location.assign(`/api/auth/signin?callbackUrl=${encodeURIComponent(callbackUrl)}`);
}

export async function apiGet<T>(path: string, init?: RequestInit): Promise<T> {
  return apiRequest<T>(path, init);
}

export async function apiGetText(path: string): Promise<string> {
  return apiRequestText(path);
}

export async function apiPost<TRequest, TResponse>(path: string, body: TRequest, init?: RequestInit): Promise<TResponse> {
  return apiRequest<TResponse>(path, {
    ...init,
    method: "POST",
    body: JSON.stringify(body)
  });
}

export async function apiPatch<TRequest, TResponse>(path: string, body: TRequest): Promise<TResponse> {
  return apiRequest<TResponse>(path, {
    method: "PATCH",
    body: JSON.stringify(body)
  });
}

export async function apiPut<TRequest, TResponse>(path: string, body: TRequest): Promise<TResponse> {
  return apiRequest<TResponse>(path, {
    method: "PUT",
    body: JSON.stringify(body)
  });
}

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiClientError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return fallback;
}
