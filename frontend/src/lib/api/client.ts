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

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    cache: "no-store"
  });

  if (!response.ok) {
    const payload = await readPayload(response);
    throw new ApiClientError(`API request failed with status ${response.status}`, response.status, payload);
  }

  return response.json() as Promise<T>;
}
