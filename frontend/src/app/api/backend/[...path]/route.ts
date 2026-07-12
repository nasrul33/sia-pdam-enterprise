import { getToken } from "next-auth/jwt";
import { NextRequest, NextResponse } from "next/server";

import {
  buildBasicAuthorization,
  buildBackendRequestHeaders,
  isUsableAccessToken,
  normalizeBackendPath
} from "@/features/auth/backend-proxy-policy";

const FORWARDED_RESPONSE_HEADERS = ["content-type", "content-disposition", "x-request-id"] as const;

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

function unauthorized(code: string, message: string) {
  return NextResponse.json({ code, message }, { status: 401 });
}

async function resolveAuthorization(request: NextRequest): Promise<string | NextResponse> {
  const authMode = process.env.AUTH_MODE ?? "basic";
  if (authMode === "basic") {
    const authorization = buildBasicAuthorization(
      process.env.DEV_BASIC_AUTH_USERNAME,
      process.env.DEV_BASIC_AUTH_PASSWORD
    );
    if (!authorization) {
      return NextResponse.json(
        { code: "BASIC_SERVER_MISCONFIGURED", message: "Local Basic Auth server configuration is incomplete." },
        { status: 503 }
      );
    }
    return authorization;
  }

  if (authMode !== "oidc") {
    return NextResponse.json(
      { code: "AUTH_MODE_UNSUPPORTED", message: "Authentication mode is not supported." },
      { status: 503 }
    );
  }

  const secret = process.env.AUTH_SECRET;
  if (!secret) {
    return NextResponse.json(
      { code: "OIDC_SERVER_MISCONFIGURED", message: "OIDC server configuration is incomplete." },
      { status: 503 }
    );
  }

  const token = await getToken({ req: request, secret });
  if (!isUsableAccessToken(token)) {
    return unauthorized(
      "OIDC_SESSION_REQUIRED",
      token ? "OIDC session has expired." : "OIDC session is missing."
    );
  }
  return `Bearer ${token.accessToken}`;
}

async function proxy(request: NextRequest, context: RouteContext) {
  const authorization = await resolveAuthorization(request);
  if (authorization instanceof NextResponse) {
    return authorization;
  }

  const { path } = await context.params;
  const normalizedPath = normalizeBackendPath(path);
  if (!normalizedPath) {
    return NextResponse.json({ code: "BACKEND_PATH_INVALID", message: "Backend path is invalid." }, { status: 400 });
  }

  const backendBaseUrl = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:18080";
  const backendUrl = new URL(`${backendBaseUrl.replace(/\/$/, "")}/${normalizedPath}`);
  backendUrl.search = request.nextUrl.search;

  const headers = buildBackendRequestHeaders(request.headers, authorization);

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
