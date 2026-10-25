import { render } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { AppProviders } from "@/app/providers";
import { routes } from "@/app/router";
import { QueryClient } from "@tanstack/react-query";

/** Renders the real route tree at `path` with a fresh query client (services are mocked per test file). */
export function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  const utils = render(
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return { ...utils, router };
}
