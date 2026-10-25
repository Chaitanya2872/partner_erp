import { Dialog as RadixDialog } from "radix-ui";
import { X } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

interface DialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
}

/** Accessible modal (focus trap, Esc to close) styled like the original application's dialogs. */
export function Dialog({ open, onOpenChange, title, description, children, footer, wide }: DialogProps) {
  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="mback" />
        <RadixDialog.Content className={cn("mbox", wide && "wide")}>
          <header>
            <div>
              <RadixDialog.Title asChild>
                <h3>{title}</h3>
              </RadixDialog.Title>
              {description ? (
                <RadixDialog.Description className="muted small" style={{ margin: "2px 0 0" }}>
                  {description}
                </RadixDialog.Description>
              ) : (
                <RadixDialog.Description className="sr-only">{title}</RadixDialog.Description>
              )}
            </div>
            <RadixDialog.Close className="x" aria-label="Close">
              <X size={18} />
            </RadixDialog.Close>
          </header>
          <div className="body">{children}</div>
          {footer ? <footer>{footer}</footer> : null}
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  );
}
