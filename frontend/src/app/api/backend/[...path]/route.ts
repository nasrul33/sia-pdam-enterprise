import { getToken } from "next-auth/jwt";
import { NextRequest, NextResponse } from "next/server";

import {
  buildBackendRequestHeaders,
  isUsableAccessToken,
  normalizeBackendPath
} from "@/features/auth/backend-proxy-policy";

const FORWARDED_RESPONSE_HEADERS = ["content-type", "content-disposition", "x-request-id"] as const;

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

function unauthorized(message: string) {
  return NextResponse.json({ code: "OIDC_SESSION_REQUIRED", message }, { status: 401 });
}

async function proxy(request: NextRequest, context: RouteContext) {
  const secret = process.env.AUTH_SECRET;
  if (!secret) {
    return NextResponse.json(
      { code: "OIDC_SERVER_MISCONFIGURED", message: "OIDC server configuration is incomplete." },
      { status: 503 }
    );
  }

  const token = await getToken({ req: request, secret });
  if (!isUsableAccessToken(token)) {
    return unauthorized(token ? "OIDC session has expired." : "OIDC session is missing.");
  }

  const { path } = await context.params;
  const normalizedPath = normalizeBackendPath(path);
  if (!normalizedPath) {
    return NextResponse.json({ code: "BACKEND_PATH_INVALID", message: "Backend path is invalid." }, { status: 400 });
  }

  const backendBaseUrl = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:18080";
  const backendUrl = new URL(`${backendBaseUrl.replace(/\/$/, "")}/${normalizedPath}`);
  backendUrl.search = request.nextUrl.search;

  const headers = buildBackendRequestHeaders(request.headers, token.accessToken);

  const body = request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer();
  try {
    const response = await fetch(backendUrl, {
      method: request.method,
      headers,
      body: body && body.byteLength > 0 ? body : undefined,
      cache: "no-store",
      redirect: "manual"
    });
    const responseHeaders = new Headers();
    FORWARDED_RESPONSE_HEADERS.forEach((name) => {
      const value = response.headers.get(name);
      if (value) responseHeaders.set(name, value);
    });
    responseHeaders.set("Cache-Control", "no-store");
    return new NextResponse(response.body, {
      status: response.status,
      headers: responseHeaders
    });
  } catch {
    return NextResponse.json(
      { code: "BACKEND_UNAVAILABLE", message: "Backend service is unavailable." },
      { status: 502 }
    );
  }
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
