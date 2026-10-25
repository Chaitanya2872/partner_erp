# IOTIQ Partner ERP: Client

React 19 + TypeScript 6 + Vite 8, with React Router 8, TanStack Query 5, React Hook Form + Zod, Axios, Radix (dialogs), Lucide icons and Sonner toasts. Styling reuses the original application's design tokens and component classes (`src/styles/globals.css`) on top of Tailwind 4.

## Run locally
```
npm install
npm run dev        # http://localhost:5173
```
The dev server proxies `/api` to `http://localhost:8080` (override with `VITE_API_PROXY_TARGET`), so the refresh cookie is same-origin. Start the server first (see `server/README.md`).

## Scripts
| Script | Purpose |
|---|---|
| `npm run build` | type-check (`tsc -b`) and production build |
| `npm test` | Vitest + Testing Library (jsdom) |
| `npm run lint` | ESLint (typescript-eslint, react-hooks) |

## Security model (client side)
- The access token lives **in memory only** (`services/storage.service.ts`); nothing is written to localStorage/sessionStorage. A reload restores the session through the HttpOnly refresh cookie.
- `services/api.ts` attaches the token, sends `X-Requested-With`, and on a 401 performs one refresh (single-flight in the tab, serialised across tabs with the Web Locks API so two tabs never replay the same rotating token), then retries once. If refresh fails the user is signed out with a "session ended" notice.
- `useAuth()` / `usePermissions()` expose `canRead("LEADS")`, `canWrite("ESTIMATES")`, `canPerform("PAYOUTS", "MARK_PAYOUT_PAID")`, computed from `/auth/me` authorities. They only decide what is shown; the server enforces every permission.
- Permissions are re-read when the window regains focus and after permission or role changes.

## Structure
- `app/`: providers, router (protected routes), navigation definition.
- `layouts/`: shell, permission-driven sidebar, header, `ProtectedRoute`.
- `modules/<module>/`: pages, components, services and types per business module.
- `components/`: shared UI (dialog, confirm dialog, form field, data table, states).
- `test/`: Vitest setup, fixtures and `renderApp` helper.

Modules whose screens belong to later phases show a labelled "not yet available (Phase N)" page.
