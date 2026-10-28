import { Link } from "react-router";
import { ScrollText, ShieldCheck, Users } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { ContentCard } from "@/components/layout/Page";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { DescriptionList, DetailRow } from "@/components/data/DataDisplay";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { formatNumber } from "@/utils/format";
import { useControls, useNumbering, useSecurityPolicy } from "../hooks/useSettings";

/** Document number series. Formats are fixed in code; nothing here is editable. */
export function NumberingCard() {
  const numbering = useNumbering();
  return (
    <ContentCard title="Document numbering" description="Numbers are allocated by the server, never reused and never edited.">
      {numbering.isPending ? (
        <LoadingState label="Loading numbering…" />
      ) : numbering.isError ? (
        <ErrorState error={numbering.error} onRetry={() => numbering.refetch()} />
      ) : (
        <div className="space-y-3">
          <div className="overflow-x-auto rounded-md border border-border" role="region" aria-label="Number series, scrolls horizontally" tabIndex={0}>
            <table className="w-full min-w-[36rem] text-sm" aria-label="Number series">
              <thead>
                <tr className="border-b border-border bg-surface-sunken text-left text-2xs font-semibold uppercase tracking-wider text-muted-foreground">
                  <th scope="col" className="px-3 py-2">Document</th>
                  <th scope="col" className="px-3 py-2">Format</th>
                  <th scope="col" className="px-3 py-2">Allocated</th>
                  <th scope="col" className="px-3 py-2 text-right">Used in {numbering.data[0]?.year}</th>
                </tr>
              </thead>
              <tbody>
                {numbering.data.map((s) => (
                  <tr key={s.prefix} className="border-b border-border last:border-b-0">
                    <th scope="row" className="px-3 py-2 text-left font-medium">{s.document}</th>
                    <td className="px-3 py-2 font-mono text-xs">
                      {s.format}
                      <div className="text-muted-foreground">e.g. {s.example}</div>
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">{s.allocatedWhen}</td>
                    <td className="px-3 py-2 text-right tabular">
                      {formatNumber(s.lastValue)} <span className="text-muted-foreground">of {formatNumber(s.capacity)}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="text-xs text-muted-foreground">
            Each series restarts every calendar year (Asia/Kolkata). Numbers are taken inside the saving transaction under a row lock, so two
            saves can never get the same number and a failed save leaves no gap.
          </p>
        </div>
      )}
    </ContentCard>
  );
}

/** What GST treatment the system supports (Phase 9) and what stays blocked. Read-only. */
export function GstTreatmentCard() {
  return (
    <ContentCard title="GST treatment" description="Decided per invoice from the registered state and the place of supply.">
      <DescriptionList>
        <DetailRow label="Same state or UT">CGST + SGST, or CGST + UTGST in a Union Territory, each half the line rate</DetailRow>
        <DetailRow label="Different state">IGST at the line rate</DetailRow>
        <DetailRow label="Rounding">Per line, half up to the paisa</DetailRow>
        <DetailRow label="Not supported (blocked)">
          <div className="flex flex-wrap gap-1.5">
            {["Exports and SEZ supplies", "Reverse charge", "Composition scheme", "Cess", "E-invoicing (IRN/QR)", "Credit and debit notes"].map((s) => (
              <Badge key={s} tone="outline">
                {s}
              </Badge>
            ))}
          </div>
        </DetailRow>
      </DescriptionList>
    </ContentCard>
  );
}

export function SecurityCard() {
  const policy = useSecurityPolicy();
  const { canRead } = useAuth();
  return (
    <ContentCard title="Sign-in and sessions" description="The policy in force. It comes from the deployment configuration and can't be changed here.">
      {policy.isPending ? (
        <LoadingState label="Loading security policy…" />
      ) : policy.isError ? (
        <ErrorState error={policy.error} onRetry={() => policy.refetch()} />
      ) : (
        <div className="space-y-4">
          {policy.data.lockedAccounts > 0 ? (
            <Alert tone="warning" title={`${formatNumber(policy.data.lockedAccounts)} account${policy.data.lockedAccounts === 1 ? " is" : "s are"} locked`}>
              Too many wrong passwords. {policy.data.lockout.unlock}
              {canRead("USER_MANAGEMENT") ? (
                <>
                  {" "}
                  <Link className="font-medium underline underline-offset-2" to="/users?locked=true">
                    Show locked accounts
                  </Link>
                </>
              ) : null}
            </Alert>
          ) : null}
          <DescriptionList columns={2}>
            <DetailRow label="Passwords">
              <ul className="list-disc space-y-0.5 pl-4">
                {policy.data.password.rules.map((r) => (
                  <li key={r}>{r}</li>
                ))}
              </ul>
            </DetailRow>
            <DetailRow label="Lockout">
              After {policy.data.lockout.maxFailedAttempts} wrong passwords in a row, for {policy.data.lockout.lockoutMinutes} minutes.{" "}
              {policy.data.lockout.unlock}
            </DetailRow>
            <DetailRow label="Sessions">
              Access token {policy.data.sessions.accessTokenMinutes} minutes; refresh token {policy.data.sessions.refreshTokenDays} days.
              <ul className="mt-1 list-disc space-y-0.5 pl-4 text-muted-foreground">
                {policy.data.sessions.rules.map((r) => (
                  <li key={r}>{r}</li>
                ))}
              </ul>
            </DetailRow>
            <DetailRow label="Pending password changes">{formatNumber(policy.data.pendingPasswordChanges)} active accounts must change their password at the next sign-in</DetailRow>
          </DescriptionList>
        </div>
      )}
    </ContentCard>
  );
}

export function ControlsCard() {
  const controls = useControls();
  return (
    <ContentCard title="Approval and financial controls" description="Enforced by the server and the database. No setting can relax them.">
      {controls.isPending ? (
        <LoadingState label="Loading controls…" />
      ) : controls.isError ? (
        <ErrorState error={controls.error} onRetry={() => controls.refetch()} />
      ) : (
        <div className="overflow-x-auto rounded-md border border-border" role="region" aria-label="Controls, scrolls horizontally" tabIndex={0}>
          <table className="w-full min-w-[44rem] text-sm" aria-label="Approval and financial controls">
            <thead>
              <tr className="border-b border-border bg-surface-sunken text-left text-2xs font-semibold uppercase tracking-wider text-muted-foreground">
                <th scope="col" className="px-3 py-2">Area</th>
                <th scope="col" className="px-3 py-2">Control</th>
                <th scope="col" className="px-3 py-2">Permission</th>
                <th scope="col" className="px-3 py-2">Reference</th>
              </tr>
            </thead>
            <tbody>
              {controls.data.map((c) => (
                <tr key={`${c.area}-${c.control}`} className="border-b border-border last:border-b-0 align-top">
                  <th scope="row" className="px-3 py-2 text-left font-medium">{c.area}</th>
                  <td className="px-3 py-2">
                    {c.control}
                    <div className="text-xs text-muted-foreground">{c.enforcedBy}</div>
                  </td>
                  <td className="px-3 py-2 font-mono text-xs">{c.permission}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{c.reference}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </ContentCard>
  );
}

/** Links to the administration pages the user may open (each page checks access again, as does the server). */
export function AccessLinks() {
  const { canRead } = useAuth();
  const links = [
    { to: "/users", module: "USER_MANAGEMENT" as const, icon: Users, title: "Users", text: "Accounts, roles, lockouts, password resets and per-user permissions." },
    { to: "/roles", module: "ROLE_MANAGEMENT" as const, icon: ShieldCheck, title: "Roles and default permissions", text: "Role names and the permissions new accounts start with." },
    { to: "/audit-logs", module: "AUDIT_LOGS" as const, icon: ScrollText, title: "Audit log", text: "Every security, administration and financial change." },
  ].filter((l) => canRead(l.module));
  if (links.length === 0) {
    return <Alert tone="neutral">You don&apos;t have access to user, role or audit administration.</Alert>;
  }
  return (
    <div className="grid gap-3 md:grid-cols-3">
      {links.map(({ to, icon: Icon, title, text }) => (
        <ContentCard key={to}>
          <div className="flex h-full flex-col gap-3">
            <div className="flex items-center gap-2 font-semibold">
              <Icon className="size-4 text-muted-foreground" aria-hidden="true" /> {title}
            </div>
            <p className="flex-1 text-sm text-muted-foreground">{text}</p>
            <Button asChild size="sm" variant="outline" className="self-start">
              <Link to={to}>Open {title.toLowerCase()}</Link>
            </Button>
          </div>
        </ContentCard>
      ))}
    </div>
  );
}
