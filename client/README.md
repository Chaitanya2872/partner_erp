# IOTIQ Partner ERP: Client

**Stack:** React 19, TypeScript 6 and Vite 8, with:
- React Router 8, TanStack Query 5, React Hook Form and Zod, and Axios.
- Radix UI primitives with class-variance-authority, styled with Tailwind 4.
- Lucide icons and Sonner toasts.

The IOTIQ design system (ivory, forest and gold; Bricolage Grotesque, Instrument Sans and IBM Plex Mono; light, dark and system themes) is defined as tokens in `src/styles/globals.css`.

- Design system: `../docs/frontend/DESIGN_SYSTEM.md`
- Architecture: `../docs/frontend/ARCHITECTURE.md`
- Components: `../docs/frontend/COMPONENTS.md`

## Run locally
```
npm install
npm run dev        # http://localhost:5173
```
The dev server proxies `/api` to `http://localhost:8080` (override with `VITE_API_PROXY_TARGET`), so the refresh cookie is same-origin. Start the server first (see `server/README.md`).

## Scripts
| Script | Purpose |
|---|---|
| `npm run build` | Type-check (`tsc -b`) and production build (route-level chunks plus stable vendor chunks) |
| `npm test` | Vitest and Testing Library (jsdom) |
| `npm run lint` | ESLint (typescript-eslint, react-hooks, React Compiler rules) |

## Security model (client side)
- **Access token:** kept **in memory only** (`services/storage.service.ts`). A reload restores the session through the HttpOnly refresh cookie. localStorage holds only two UI preferences: `iotiq.theme` and `iotiq.sidebar.collapsed`.
- **Requests and refresh:** `services/api.ts` attaches the token and sends `X-Requested-With`. On a 401 it performs one refresh, then retries once. The refresh is single-flight in the tab and serialised across tabs with the Web Locks API, so two tabs never replay the same rotating token. If refresh fails, the user is signed out with a "session ended" notice. Errors carry the server's `requestId`, and requests are cancelled when their page unmounts.
- **Permissions:** `useAuth()` and `usePermissions()` expose `canRead("LEADS")`, `canWrite("ESTIMATES")` and `canPerform("PAYOUTS", "MARK_PAYOUT_PAID")`, computed from `/auth/me` authorities. They only decide what is shown; the server enforces every permission. Navigation, actions and internal B2C columns follow them.
- **Freshness:** permissions are re-read when the window regains focus and after permission or role changes.

## Structure
- `app/`: providers, router (lazy routes, route handles for breadcrumbs), navigation definition.
- `layouts/`: responsive shell with:
  - a collapsible sidebar on desktop, an icon rail on tablet and a drawer on phones;
  - a top bar with breadcrumbs, the theme menu and the account menu;
  - `ProtectedRoute`.
- `components/`: the design-system components (`ui`, `forms`, `tables`, `data`, `feedback`, `layout`, `navigation`, `dashboard`).
- `modules/<module>/`: pages, components, services and types for each business module.
- `lib/`: errors, query keys, status registry, shared validation. `utils/`: INR, date and percentage formatting.
- `test/`: Vitest setup, viewport and theme helpers, fixtures, and the `renderApp` helper.

Modules whose screens belong to later phases show a labelled page saying which phase delivers them.
