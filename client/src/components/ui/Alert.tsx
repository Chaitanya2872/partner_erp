import type { HTMLAttributes, ReactNode } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { AlertTriangle, CheckCircle2, Info, XCircle } from "lucide-react";
import { cn } from "@/lib/cn";

const alertVariants = cva("flex gap-3 rounded-lg border px-3.5 py-3 text-sm [&>svg]:mt-0.5 [&>svg]:size-4 [&>svg]:shrink-0", {
  variants: {
    tone: {
      info: "border-transparent bg-info-bg text-info",
      success: "border-transparent bg-success-bg text-success",
      warning: "border-transparent bg-warning-bg text-warning",
      danger: "border-transparent bg-danger-bg text-danger",
      neutral: "border-border bg-surface text-foreground",
    },
  },
  defaultVariants: { tone: "info" },
});

const ICONS = { info: Info, success: CheckCircle2, warning: AlertTriangle, danger: XCircle, neutral: Info };

interface AlertProps extends Omit<HTMLAttributes<HTMLDivElement>, "title">, VariantProps<typeof alertVariants> {
  title?: ReactNode;
  action?: ReactNode;
}

/**
 * Inline message with an icon and text, so status never depends on colour alone. Danger alerts are announced
 * (role="alert"); pass role="status" for polite updates.
 */
export function Alert({ tone, title, action, className, children, role, ...props }: AlertProps) {
  const resolved = tone ?? "info";
  const Icon = ICONS[resolved];
  return (
    <div role={role ?? (resolved === "danger" ? "alert" : undefined)} className={cn(alertVariants({ tone: resolved }), className)} {...props}>
      <Icon aria-hidden="true" />
      <div className="min-w-0 flex-1">
        {title ? <div className="font-semibold">{title}</div> : null}
        {children ? <div className={cn(title ? "mt-0.5" : undefined, "[&_ul]:list-disc [&_ul]:pl-5")}>{children}</div> : null}
      </div>
      {action ? <div className="shrink-0 self-center">{action}</div> : null}
    </div>
  );
}
