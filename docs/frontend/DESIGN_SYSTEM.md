# IOTIQ Partner ERP: Design System

The design system carries over the original application's identity (`docs/reference/IOTIQ_Partner_ERP_original.html`): an ivory page, forest-green ink and navigation, and gold kept for emphasis. It is implemented as CSS custom properties in `client/src/styles/globals.css`. Tailwind 4 maps them to utilities through `@theme inline`.

**Rule:** components use semantic utilities (`bg-surface`, `text-muted-foreground`, `border-border`, `bg-danger-bg text-danger` and so on), never raw hex values. A token changes in one place and both themes follow.

## Brand colours

| Brand colour | Hex | Used as |
|---|---|---|
| Ivory | `#F4EFE2` | `--background` (light page) and text on the sidebar |
| Paper | `#FBF8EF` | `--surface` (cards, dialogs, inputs) |
| Forest | `#11271C` | `--foreground`, `--primary`, `--sidebar` (light) |
| Gold | `#C39C67` | `--accent`, active sidebar item, `--primary` in dark mode |

## Semantic tokens

| Token | Light | Dark | Purpose |
|---|---|---|---|
| `background` | `#f4efe2` | `#0c1a13` | Page |
| `surface` / `surface-muted` / `surface-sunken` | `#fbf8ef` / `#f7f2e5` / `#efe8d6` | `#12291d` / `#0f2217` / `#0a1610` | Cards, table headers, wells |
| `foreground` / `muted-foreground` | `#11271c` / `#5d6b61` | `#f1ede1` / `#a3b2a7` | Text |
| `border` / `border-strong` | `#ddd5c0` / `#c9bfa5` | `#27412f` / `#375a44` | Dividers; emphasised borders |
| `input` | `#978b6f` | `#5d7f6b` | Form-control borders (≥ 3:1 against the surface, WCAG 1.4.11) |
| `primary` (+`-foreground`, `-hover`) | forest | gold | Primary buttons and links |
| `secondary` | `#ece5d3` | `#1b3626` | Secondary buttons |
| `accent` / `accent-ink` | `#c39c67` / `#7d5f2c` | `#c39c67` / `#d9b887` | Gold fills; gold text (AA on surfaces) |
| `hover` / `active` / `disabled` | translucent ink | translucent ivory | Interaction states |
| `ring` | `#7d5f2c` | `#d9b887` | Focus ring (2 px, offset) |
| `overlay` | 55 % forest-black | 65 % black | Dialog and drawer backdrops |
| `success` / `warning` / `danger` / `info` (+`-bg`) | `#286b43` / `#8a5408` / `#9c3526` / `#2f5f8a` | `#6cc08a` / `#e0a54d` / `#e8836f` / `#7fb0dc` | Status text on their tinted backgrounds (AA, 4.5:1) |
| `danger-solid` | `#b3402f` | `#c4513e` | Destructive button fill |
| `sidebar*` | forest / ivory / gold | near-black / ivory / gold | Navigation rail |
| `chart-1…5`, `chart-grid` | forest, gold, blue, green, amber | lighter equivalents | Chart series (Phase 12) |

The status colours were darkened from the original's brighter tones so that text on the tinted badge backgrounds meets 4.5:1. In dark mode the primary becomes gold on forest, because forest on near-black would not be visible.

## Typography

| Role | Family | Utility |
|---|---|---|
| Display (page titles, metrics) | Inter 500/600 (same family as body; hierarchy from size and weight) | `font-display` |
| Body and UI | Inter 400/500/600 | `font-sans` (default) |
| Codes, IDs, amounts in tables | IBM Plex Mono 400/500 | `font-mono` (with `tabular-nums` for figures) |

The fonts load from Google Fonts with `display=swap` and fall back to Segoe UI or system fonts. The scale is Tailwind's (`text-xs` … `text-3xl`) plus `text-2xs` (11 px) for overline labels. `cn()` registers `text-2xs` with tailwind-merge so it doesn't clash with colour classes.

## Spacing, radius, elevation

- **Spacing:** Tailwind's 4 px scale. Page gutters are `px-4` on phones, `px-6` on tablets and `px-8` on desktops. Card padding is `p-4`/`p-5`.
- **Radius:** `sm` 4 px (badges), `md` 6 px (controls), `lg` 8 px (cards), `xl` 10 px (dialogs).
- **Shadows:** `shadow-xs` on cards, `shadow-sm` on raised controls, `shadow-lg` on menus and popovers, `shadow-xl` on dialogs. All shadows are warm-tinted and softer in light mode.
- **Touch targets:** controls are at least 36 px (`h-9`) and navigation links 40 px (`min-h-10`). WCAG 2.5.8 needs 24 px.

## Themes

- There are three choices: **Light**, **Dark** and **System**. The choice is stored per device in `localStorage["iotiq.theme"]`. It is a preference, not a secret.
- An inline script in `index.html` sets `<html data-theme>` before the first paint, so the page never flashes the wrong theme. `ThemeProvider` (`context/ThemeContext.tsx`) keeps it in sync and follows the OS while System is selected. Each theme sets `color-scheme`, so native controls and scrollbars match.
- **Print:** `@media print` forces the light token values whatever the theme, hides navigation (`.no-print`) and gives `.print-document` a white page. Dark mode never reaches a printed or PDF document.
- **Reduced motion:** `prefers-reduced-motion` turns off transitions and animations.

## Status badges

Statuses come from the backend lifecycle enums only (`lib/status.ts`). Each has a label and a tone (neutral, info, success, warning, danger, accent). The label is always shown as text, so meaning never depends on colour. States the server computes at read time (estimate EXPIRED, invoice PAID/PARTIAL/OVERDUE, payout DUE/CLAWBACK) carry a visually hidden "(calculated)" marker. Unknown codes render neutrally as humanised text rather than disappearing.

## Number formats

- Currency uses `en-IN` grouping with two decimals: `₹1,25,000.00`. Percentages always show two decimals: `18.00%`. Missing values render as `—`.
- Decimal strings from the API are formatted as strings, never parsed into floats, so values beyond `Number` precision stay exact (`utils/format.ts`). Form validation compares decimal strings digit by digit (`compareDecimal`).


## Motion

Opacity and transform only; `prefers-reduced-motion` collapses every duration. Tokens live in `globals.css`:
micro 120-180ms (buttons, hover, focus), standard 180-250ms (menus, dialogs, page enter), layout 250-350ms
(sidebar width, sheet). Easing is `--ease-out-soft`. Utilities: `animate-pop-in/out` (menus, tooltips, dialogs,
select popovers; use with `data-[state=open|closed]`), `animate-fade-in/out` (overlays), `animate-sheet-in/out`
(drawers, set `--sheet-from`), `animate-page-in` (route change), `animate-shimmer` (Skeleton).

## Page patterns (Phase 2)

- **Filtered-empty states:** pass `onClearFilters` to `EmptyState` whenever filters are active. It swaps in a "no matches"
  icon, the default hint "Try a different search, or clear the filters.", and a Clear filters button. Never leave a user on an
  empty result with no way back. Genuinely empty lists keep their own message and primary action.
- **Date filters in toolbars:** use `FilterDate` (visible "From" / "To" label plus the `aria-label`); a bare native
  `mm/dd/yyyy` field is never anonymous. Stack the pair on phones (`flex-col sm:flex-row`) to avoid horizontal overflow.
- **Row actions (`DataTable.rowActions`):** on hover-capable devices buttons rest borderless and gain their border when the row
  is hovered or focused; touch devices and keyboard focus always see them fully. Nothing is hover-only. Rows with several actions
  (Users) use icon-only buttons with an `sr-only` label and a `title`, so the accessible name is unchanged.
- **Alerts on the dashboard (`AlertCard`):** neutral card, severity as a left accent bar plus icon, "Review" link. Cards size to
  their content (`items-start` grid) rather than stretching to equal height.
- **Confirm dialogs:** the title asks the question, the body names the record and states the consequence, and the confirm button
  repeats the verb and the object ("Delete lead", "Deactivate user"). Destructive actions use `danger`.
- **Tooltips:** `Tooltip enabled={false}` keeps the trigger mounted (it only suppresses the popup), so toggling it never remounts
  or drops focus from the wrapped control.
