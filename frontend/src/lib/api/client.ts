const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

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

async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    cache: "no-store",
    ...init
  });

  if (!response.ok) {
    const payload = await readPayload(response);
    const message =
      isErrorPayload(payload) && typeof payload.message === "string"
        ? payload.message
        : `API request failed with status ${response.status}`;
    throw new ApiClientError(message, response.status, payload);
  }

  return response.json() as Promise<T>;
}

export async function apiGet<T>(path: string): Promise<T> {
  return apiRequest<T>(path);
}

export async function apiPost<TRequest, TResponse>(path: string, body: TRequest): Promise<TResponse> {
  return apiRequest<TResponse>(path, {
    method: "POST",
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
