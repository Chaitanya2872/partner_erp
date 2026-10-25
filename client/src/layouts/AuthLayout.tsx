import type { ReactNode } from "react";

/** Split sign-in layout: brand panel (hidden on small screens) and the form. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="auth">
      <aside className="side">
        <div className="brand" style={{ padding: 0 }}>
          IOTIQ<small>Partner ERP</small>
        </div>
        <div>
          <h2>Partners, leads and payouts in one place.</h2>
          <p>Sign in with the account your administrator created for you.</p>
        </div>
        <div className="small" style={{ color: "var(--nav-mute)" }}>
          © IOTIQ
        </div>
      </aside>
      <section className="form">{children}</section>
    </div>
  );
}
