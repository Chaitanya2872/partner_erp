# IOTIQ Partner ERP: Frontend Architecture

**Stack:** React 19, TypeScript 6 and Vite 8 (rolldown). Supporting libraries:
- React Router 8 (data router) for routing.
- TanStack Query 5 for data fetching.
- React Hook Form 7 and Zod 4 for forms.
- Axios for HTTP.
- Tailwind 4 for styling.
- Radix UI primitives (the unified `radix-ui` package) for components.
- class-variance-authority for component variants.
- Lucide for icons and Sonner for toasts.

## Layers

```
src/
  app/          providers, router, navigation definition, breadcrumbs
  layouts/      MainLayout (shell), Sidebar, Topbar, AuthLayout, ProtectedRoute
  components/
    ui/         primitives: Button, Input/Select/Textarea, Checkbox, Switch, RadioGroup, Dialog/Sheet,
                DropdownMenu, Tooltip, Tabs, Card, Badge, Alert, Avatar, Skeleton
    forms/      FormField, ErrorSummary, currency/percentage/number/date/password inputs,
                SearchableSelect, SearchInput, UnsavedChangesGuard
    tables/     DataTable (server-side pagination and sorting)
    data/       StatusBadge, DescriptionList, KeyValueCard, Timeline
    dashboard/  MetricCard, ChartContainer, ActivityList, AlertCard, SummaryPanel
    feedback/   Empty, Error, Loading, Success, PermissionDenied and ComingSoon states, ConfirmDialog,
                ErrorBoundary, PageSkeleton
    layout/     PageContainer, PageHeader, PageSection, ContentCard, Toolbar
    navigation/ Breadcrumbs, Pagination
  context/      AuthContext, ThemeContext
  hooks/        useAuth, usePermissions, useTheme, useMediaQuery, useDocumentTitle
  lib/          cn, errors, queryKeys, status registry, validation, storage, theme
  services/     api client (axios), auth service, in-memory token storage
  modules/<m>/  pages, components, services and types for each business module
  utils/        format (INR, dates, percentages), decimal input sanitising
```

## Routing

- `app/router.tsx` defines one data router. Every page component, including `MainLayout` and `LoginPage`, is a `React.lazy` import wrapped in `Suspense` with a `PageSkeleton` fallback.
- Each module route carries a `handle: { nav, crumb }`. `useBreadcrumbs` builds the trail from the matched routes, using the navigation group and the module's display name from the database.
- `ProtectedRoute` waits for session restore. It then:
  - sends anonymous users to `/login`, remembering the requested page so they return to it after signing in;
  - forces `/account/password` while a password change is required;
  - renders `PermissionDenied` when the user lacks READ on the route's module.
  The server enforces the same rules. The client check only avoids showing a page whose data calls would fail.
- Modules delivered in later phases are generated from `NAV_ITEMS` as Coming Soon routes that say which phase delivers them.
- `errorElement` (`RouterErrorPage`) catches routing and loader errors. `RouteErrorBoundary` inside the shell is reset on navigation, so a crashed page doesn't take the sidebar down. A failed chunk download (after a new deployment) shows "A new version is available" with a reload button.

## Navigation

`app/navigation.ts` is the single source of truth for the sidebar. It lists Dashboard, then the groups **Funnel** (Partners, Leads), **Sales** (Estimates, Invoices, Payments, Payouts), **Master** (Products / Price Sheet, Reports, Settings) and **Administration** (User Management, Role Management, Audit Logs).
- An item appears only when the user can read its module. A group disappears when it has no visible items.
- Labels are the module display names from `/auth/modules`.
- Internal B2C is not a sidebar module. It is a permission that reveals internal fields inside Sales and Products screens.

## Responsive shell

| Width | Sidebar | Notes |
|---|---|---|
| ≥ 1024 px | Full sidebar, collapsible to a 64 px icon rail | Collapsed state persisted in `localStorage["iotiq.sidebar.collapsed"]`; rail items have tooltips and aria-labels |
| 768–1023 px | Always the icon rail | More room for tables |
| < 768 px | Hidden; a menu button opens a left drawer (Radix Dialog) | Focus trapped; Escape, the backdrop or a link click closes it |

The top bar holds breadcrumbs (collapsed to the current page on phones), the theme menu and the account menu. The account menu shows the name, email and role display name, with Change password and Sign out. There are no placeholder search or notification controls. A skip link jumps to `#main-content`.

## Data access

- `services/api.ts` provides the Axios instance with the in-memory access token and `X-Requested-With`. It also handles:
  - **401s:** one refresh, single-flight within a tab and serialised across tabs with the Web Locks API, then one retry. If refresh fails, `endSession()` signs the user out and the login page explains that the session ended.
  - **Errors:** every failure becomes an `ApiRequestError` with `status`, `code`, `fieldErrors` and the server's `requestId`. Cancelled requests are tagged `CANCELLED` and never shown as errors.
  - **Cancellation:** `apiGet`/`apiPage` accept an `AbortSignal`. Query functions pass TanStack Query's signal, so leaving a page cancels its requests.
- `lib/errors.ts#describeError` turns errors into user-facing titles and messages (network, validation, unauthorised, forbidden, not found, conflict, server). Internal server messages are never shown. The request ID is shown as a support reference.
- Query keys come from the factories in `lib/queryKeys.ts` (`[domain, "list", filters]`, `[domain, id]`, …), so invalidation is predictable.

## Forms

- Every form uses React Hook Form with a Zod schema (`lib/validation.ts` has shared email, password-policy and decimal-string schemas that mirror the server's rules).
- `FormField` wires the label, hint, error, `aria-invalid`, `aria-describedby` and `aria-required`. RHF focuses the first invalid field. `ErrorSummary` lists the errors on long forms.
- `applyServerErrors` maps a `400` response's field errors onto the form and returns any unmatched messages for an Alert.
- `UnsavedChangesGuard` blocks in-app navigation (`useBlocker`) and tab close (`beforeunload`) while a form is dirty.

## Security on the client

- The access token is kept in memory only. Refresh happens through the HttpOnly `iotiq_rt` cookie.
- localStorage holds only the two UI preferences (theme and sidebar).
- Permissions (`usePermissions`) only decide what is shown; the server authorises every request.
- Internal B2C fields render only when the server returns them, which it does only for holders of `PRODUCTS:VIEW_INTERNAL_B2C`. The UI also checks the permission before rendering B2C columns.

## Performance

- **Route-level splitting:** every page and the shell are separate chunks.
- **Stable vendor chunks** (`vite.config.ts`): `vendor-react`, `vendor-router`, `vendor-query` and `vendor-forms`. They stay cached across releases that change only application code.
- **Radix primitives** are left to automatic splitting, so pages load only those they use.
- Measurements are recorded in `docs/IMPLEMENTATION_PROGRESS.md` (Phase 4, Performance).

## Testing

- Vitest, Testing Library and jsdom.
- `test/viewport.ts` drives `matchMedia` for breakpoint and OS-theme tests.
- `test/renderApp.tsx` renders the real router with mocked services.
- `test/fixtures.ts` mirrors the database module catalogue and role templates (tests only).
