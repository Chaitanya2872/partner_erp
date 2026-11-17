import type { ReactNode } from "react";
import { Link } from "react-router";
import { ArrowLeft } from "lucide-react";
import { cn } from "@/lib/cn";
import { Card } from "@/components/ui/Card";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";

/**
 * Page layout primitives. Every routed page renders:
 *   <PageContainer>
 *     <PageHeader title=… description=… actions={<PageActions>…</PageActions>} />
 *     <PageSection>…</PageSection>
 *   </PageContainer>
 * Breadcrumbs come from the route configuration and are drawn by the top bar, so pages do not repeat them.
 */

const WIDTHS = {
  narrow: "max-w-2xl",
  default: "max-w-screen-2xl",
  full: "max-w-none",
};

export function PageContainer({ children, width = "default", className }: { children: ReactNode; width?: keyof typeof WIDTHS; className?: string }) {
  return <div className={cn("mx-auto w-full space-y-5 sm:space-y-6", WIDTHS[width], className)}>{children}</div>;
}

interface PageHeaderProps {
  title: ReactNode;
  description?: ReactNode;
  /** Primary and secondary actions (PageActions). Hidden actions must come from permission checks, not CSS. */
  actions?: ReactNode;
  /** Link back to a parent list (shown above the title). */
  back?: { to: string; label: string };
  /** Small badge next to the title (e.g. "Internal B2C view"). */
  badge?: ReactNode;
  /** Document title; defaults to the title when it is a string. */
  documentTitle?: string;
}

export function PageHeader({ title, description, actions, back, badge, documentTitle }: PageHeaderProps) {
  const docTitle = documentTitle ?? (typeof title === "string" ? title : undefined);
  useDocumentTitle(docTitle);
  return (
    <header className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end sm:justify-between">
      <div className="min-w-0">
        {back ? (
          <Link
            to={back.to}
            className="mb-1.5 inline-flex items-center gap-1 rounded-sm text-sm font-medium text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
          >
            <ArrowLeft className="size-3.5" aria-hidden="true" /> {back.label}
          </Link>
        ) : null}
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-xl font-semibold leading-tight tracking-tight sm:text-2xl">{title}</h1>
          {badge}
        </div>
        {description ? <div className="mt-1 text-sm text-muted-foreground">{description}</div> : null}
      </div>
      {actions}
    </header>
  );
}

export function PageActions({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("flex flex-wrap items-center gap-2", className)}>{children}</div>;
}

export function PageSection({ children, className, label }: { children: ReactNode; className?: string; label?: string }) {
  return (
    <section aria-label={label} className={cn("space-y-3", className)}>
      {children}
    </section>
  );
}

export function SectionHeader({ title, description, action, id }: { title: ReactNode; description?: ReactNode; action?: ReactNode; id?: string }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-2">
      <div className="min-w-0">
        <h2 id={id} className="text-base font-semibold">
          {title}
        </h2>
        {description ? <p className="mt-0.5 text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {action}
    </div>
  );
}

/** Card with an optional section header; the common container for page content. */
export function ContentCard({
  title,
  description,
  action,
  children,
  className,
  bodyClassName,
}: {
  title?: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
}) {
  return (
    <Card className={className}>
      {title ? (
        <div className="border-b border-border px-4 py-3.5 sm:px-5">
          <SectionHeader title={title} description={description} action={action} />
        </div>
      ) : null}
      <div className={cn("p-4 sm:p-5", bodyClassName)}>{children}</div>
    </Card>
  );
}

/** Filter/search row above a table. Wraps on small screens; the search input grows. */
export function Toolbar({ children, className, label = "Filters" }: { children: ReactNode; className?: string; label?: string }) {
  return (
    <div role="search" aria-label={label} className={cn("flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center", className)}>
      {children}
    </div>
  );
}
