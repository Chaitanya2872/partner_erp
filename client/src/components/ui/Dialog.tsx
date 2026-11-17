import type { ReactNode } from "react";
import { Dialog as RadixDialog } from "radix-ui";
import { X } from "lucide-react";
import { cn } from "@/lib/cn";

interface DialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  /** Wider layout for detail views (e.g. audit before/after). */
  wide?: boolean;
}

/**
 * Modal dialog: focus is trapped, Esc and the close button dismiss, focus returns to the trigger. On phones it
 * fills the width and scrolls its body, so long forms stay usable.
 */
export function Dialog({ open, onOpenChange, title, description, children, footer, wide }: DialogProps) {
  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="fixed inset-0 z-50 bg-overlay backdrop-blur-[2px] data-[state=open]:animate-fade-in data-[state=closed]:animate-fade-out" />
        <RadixDialog.Content
          // Without a description there is nothing to reference (repeating the title made readers announce it twice).
          {...(description ? {} : { "aria-describedby": undefined })}
          className={cn(
            "fixed left-1/2 top-[max(1rem,6vh)] z-50 flex max-h-[calc(100dvh-2rem)] w-[calc(100vw-1.5rem)] -translate-x-1/2 flex-col",
            "rounded-xl border border-border bg-surface text-foreground shadow-lg focus-visible:outline-none",
            "data-[state=open]:animate-pop-in data-[state=closed]:animate-pop-out",
            wide ? "max-w-4xl" : "max-w-xl",
          )}
        >
          <header className="flex items-start justify-between gap-4 border-b border-border px-4 py-3.5 sm:px-5">
            <div className="min-w-0">
              <RadixDialog.Title className="text-base font-semibold sm:text-lg">{title}</RadixDialog.Title>
              {description ? (
                <RadixDialog.Description className="mt-0.5 text-sm text-muted-foreground">{description}</RadixDialog.Description>
              ) : null}
            </div>
            <RadixDialog.Close
              className="-mr-1.5 inline-flex size-8 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-hover hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring"
              aria-label="Close"
            >
              <X className="size-4" aria-hidden="true" />
            </RadixDialog.Close>
          </header>
          <div className="min-h-0 overflow-y-auto px-4 py-4 sm:px-5">{children}</div>
          {footer ? (
            <footer className="flex flex-col-reverse gap-2 border-t border-border px-4 py-3 sm:flex-row sm:justify-end sm:px-5">{footer}</footer>
          ) : null}
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  );
}

interface SheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  children: ReactNode;
  side?: "left" | "right";
  className?: string;
}

/** Side drawer built on the same accessible dialog primitive (used for phone navigation). */
export function Sheet({ open, onOpenChange, title, children, side = "left", className }: SheetProps) {
  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="fixed inset-0 z-50 bg-overlay data-[state=open]:animate-fade-in data-[state=closed]:animate-fade-out" />
        <RadixDialog.Content
          className={cn(
            "fixed inset-y-0 z-50 flex w-[min(18rem,85vw)] flex-col shadow-lg focus-visible:outline-none",
            "data-[state=open]:animate-sheet-in data-[state=closed]:animate-sheet-out",
            side === "left" ? "left-0 [--sheet-from:-100%]" : "right-0 [--sheet-from:100%]",
            className,
          )}
        >
          <RadixDialog.Title className="sr-only">{title}</RadixDialog.Title>
          <RadixDialog.Description className="sr-only">{title}</RadixDialog.Description>
          {children}
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  );
}

export const SheetClose = RadixDialog.Close;
