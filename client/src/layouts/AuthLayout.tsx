import type { ReactNode } from "react";
import { ThemeMenu } from "./Topbar";

/** Split sign-in layout: brand panel (hidden on phones) and the form. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
      <aside className="hidden flex-col justify-between bg-sidebar p-12 text-sidebar-foreground lg:flex">
        <div className="leading-none">
          <div className="font-display text-2xl font-bold tracking-wide">IOTIQ</div>
          <div className="mt-1.5 text-xs font-medium uppercase tracking-[0.14em] text-accent">Partner ERP</div>
        </div>
        <div className="max-w-md">
          <h2 className="font-display text-4xl font-semibold leading-[1.1]">Partners, leads and payouts in one place.</h2>
          <p className="mt-4 text-sidebar-muted">Sign in with the account your administrator created for you.</p>
        </div>
        <p className="text-xs text-sidebar-muted">© IOTIQ · Internal use only</p>
      </aside>
      <section className="relative flex flex-col">
        <div className="flex items-center justify-between px-5 pt-4 lg:justify-end">
          <div className="leading-none lg:hidden">
            <div className="font-display text-lg font-bold tracking-wide">IOTIQ</div>
            <div className="mt-1 text-2xs font-medium uppercase tracking-[0.14em] text-accent-ink">Partner ERP</div>
          </div>
          <ThemeMenu />
        </div>
        <div className="flex flex-1 items-center justify-center px-5 py-10">
          <main className="w-full max-w-sm">{children}</main>
        </div>
      </section>
    </div>
  );
}
