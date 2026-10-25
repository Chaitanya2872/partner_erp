import { useState, type ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "sonner";
import { AuthProvider } from "@/context/AuthContext";
import { ApiRequestError } from "@/services/api";

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        // Authorization and validation failures are final; only transient errors are retried.
        retry: (count, error) =>
          count < 2 && !(error instanceof ApiRequestError && error.status >= 400 && error.status < 500),
      },
    },
  });
}

export function AppProviders({ children, queryClient }: { children: ReactNode; queryClient?: QueryClient }) {
  const [client] = useState(() => queryClient ?? createQueryClient());
  return (
    <QueryClientProvider client={client}>
      <AuthProvider>{children}</AuthProvider>
      <Toaster position="bottom-center" richColors closeButton />
    </QueryClientProvider>
  );
}
