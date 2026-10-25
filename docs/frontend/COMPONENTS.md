# IOTIQ Partner ERP: Component Guide

All components live in `client/src/components`. They are built on Radix primitives and cva variants, and styled only with design tokens (`DESIGN_SYSTEM.md`). This file says which component to use for which job. Props are typed and documented in the source.

## Page structure (`layout/Page.tsx`)

| Component | Use |
|---|---|
| `PageContainer` | Outer wrapper. Sets max width and gutters. |
| `PageHeader` | Title (`h1`), description, optional back link and badge, and `actions`. It also sets `document.title`. |
| `PageActions` | Right-aligned button group. It wraps on phones. |
| `PageSection` / `SectionHeader` | A titled section (`h2`) inside a page. |
| `ContentCard` | Card with an optional header and footer. |
| `Toolbar` | Filters row above a table (`role="search"`). |

## Buttons and controls (`ui/`)

- **`Button`:**
  - Variants: `primary`, `secondary`, `outline`, `ghost`, `link`, `destructive`, `destructive-outline`.
  - Sizes: `sm`, `md`, `lg`, `icon`, `icon-sm`.
  - `loading` shows a spinner and sets `aria-busy`. `asChild` renders a link styled as a button.
  - Icon-only buttons must have an `aria-label`.
- **Inputs:**
  - `Input`, `Textarea`, `Select` (native, so it works with mobile pickers and screen readers), `AdornedInput` (prefix and suffix), `Checkbox`, `Switch`, `RadioGroup`.
  - `Label` puts the required asterisk outside the label text (it is `aria-hidden`; `aria-required` carries the meaning).

## Forms (`forms/`)

- **`FormField`** connects a control to its label, hint and error. Put any control inside it: it injects `id`, `aria-describedby`, `aria-invalid` and `aria-required`.
- **Layout and messages:**
  - `FormGrid` (1 column on phones, 2 on wider screens) and `FormSection` (fieldset with a legend).
  - `FormActions` (right-aligned buttons; the primary action stacks on top on phones).
  - `FormError` (form-level message) and `ErrorSummary` (list of field errors; each entry moves focus to its field).
- **Specialised inputs:**
  - `CurrencyInput` (₹ prefix; keeps a decimal string).
  - `PercentageInput` (% suffix), `NumberInput`, `DateInput` (ISO `yyyy-mm-dd`).
  - `PasswordInput` (show/hide toggle).
- **Pickers:** `SearchableSelect` is an accessible combobox for long option lists. `SearchInput` is a search box with a clear button. Callers debounce or submit as they need.
- **`UnsavedChangesGuard`:** render it with `when={isDirty}` to confirm before leaving.

## Data display

- **`tables/DataTable`:**
  - Server-driven table: columns (`ColumnDef`), `page` meta and `onPageChange`/`onPageSizeChange`.
  - `sort` with `onSortChange` (`aria-sort` on headers); `toSortParam` turns sort state into the API's `sort=field,dir` parameter.
  - Column visibility menu (`hideable`, `defaultHidden`).
  - `rowActions`, `toolbar`.
  - `loading` (skeleton rows and `aria-busy`), `fetching` (subtle refresh indicator), `empty`, `error` with `onRetry`.
  - The table scrolls horizontally inside a focusable region on small screens.
- **Pagination** (`navigation/Pagination`): previous/next, a windowed page list and a range summary.
- **`data/StatusBadge`:** `<StatusBadge domain="invoice" status={invoice.status} />`. Pass backend codes only.
- **`DescriptionList`, `DetailRow`, `KeyValueCard`:** detail views. **`Timeline`:** history.

## Feedback (`feedback/`)

| Component | Use |
|---|---|
| `LoadingState`, `PageSkeleton`, `ui/Skeleton` | Loading placeholders (`role="status"`) |
| `EmptyState` | No records, with an optional action |
| `ErrorState` | API error, described for users, with the request reference and retry |
| `SuccessState` | Completion screens |
| `PermissionDenied` | Route without READ permission |
| `ComingSoon` | Modules delivered in later phases |
| `ConfirmDialog` | Destructive or significant actions (alertdialog) |
| `ErrorBoundary` | Crash fallback. `RouteErrorBoundary` resets on navigation. |
| `ui/Alert` | Inline info, success, warning or danger messages |
| Toasts (`sonner`) | Short confirmations after a mutation |

## Dashboard (`dashboard/`)

- **`MetricCard`:** label, value, optional `TrendIndicator` and link. Show only values the API returns. When a metric is unavailable, say so rather than showing a zero.
- **`ChartContainer`:** a titled card with loading, empty and unavailable states. It is library-agnostic; the chart library arrives in Phase 12.
- **`DashboardSection`, `ActivityList`, `AlertCard`, `SummaryPanel`:** compose the dashboard.

## Navigation (`navigation/`, `layouts/`)

- **`Breadcrumbs`** come from route handles.
- **`Sidebar`, `Topbar` and `MainLayout`** form the responsive shell (`ARCHITECTURE.md`).
- **`ui/Tabs`** for in-page sections and **`ui/DropdownMenu`** for menus.
