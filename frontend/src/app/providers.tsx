"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SessionProvider } from "next-auth/react";
import { useState } from "react";
import { isOidcAuthMode } from "@/features/auth/auth-mode";

export function Providers({ children }: Readonly<{ children: React.ReactNode }>) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            refetchOnWindowFocus: false,
            retry: 1,
            staleTime: 30_000
          }
        }
      })
  );

  const content = <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  return isOidcAuthMode(process.env.NEXT_PUBLIC_DEV_AUTH_MODE)
    ? <SessionProvider>{content}</SessionProvider>
    : content;
}
