import { useState } from "react";
import { RouterProvider } from "react-router";
import { AppProviders } from "./providers";
import { createAppRouter } from "./router";
import { ErrorBoundary } from "@/components/feedback/ErrorBoundary";

export function App() {
  const [router] = useState(createAppRouter);
  return (
    <ErrorBoundary>
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>
    </ErrorBoundary>
  );
}
