import { forwardRef, type ComponentPropsWithoutRef, type ComponentRef } from "react";
import { Tabs as RadixTabs } from "radix-ui";
import { cn } from "@/lib/cn";

/** Accessible tabs (arrow keys move between tabs). Ready for detail screens in later phases. */
export const Tabs = RadixTabs.Root;

export const TabsList = forwardRef<ComponentRef<typeof RadixTabs.List>, ComponentPropsWithoutRef<typeof RadixTabs.List>>(
  function TabsList({ className, ...props }, ref) {
    return <RadixTabs.List ref={ref} className={cn("relative flex max-w-full gap-1 overflow-x-auto border-b border-border", className)} {...props} />;
  },
);

export const TabsTrigger = forwardRef<ComponentRef<typeof RadixTabs.Trigger>, ComponentPropsWithoutRef<typeof RadixTabs.Trigger>>(
  function TabsTrigger({ className, ...props }, ref) {
    return (
      <RadixTabs.Trigger
        ref={ref}
        className={cn(
          "-mb-px inline-flex h-10 items-center gap-1.5 whitespace-nowrap border-b-2 border-transparent px-3 text-sm font-medium text-muted-foreground transition-colors duration-150",
          "hover:text-foreground focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ring",
          "data-[state=active]:border-accent data-[state=active]:text-foreground",
          className,
        )}
        {...props}
      />
    );
  },
);

export const TabsContent = forwardRef<ComponentRef<typeof RadixTabs.Content>, ComponentPropsWithoutRef<typeof RadixTabs.Content>>(
  function TabsContent({ className, ...props }, ref) {
    return <RadixTabs.Content ref={ref} className={cn("pt-4", className)} {...props} />;
  },
);

/**
 * Switch-style tabs: a rounded track with a raised active segment. Same accessible Radix tabs underneath (arrow keys,
 * roving focus); use for a handful of peer views, and the underline `TabsList` for long detail-page tab rows.
 */
export const SegmentedList = forwardRef<ComponentRef<typeof RadixTabs.List>, ComponentPropsWithoutRef<typeof RadixTabs.List>>(
  function SegmentedList({ className, ...props }, ref) {
    return (
      <RadixTabs.List
        ref={ref}
        className={cn("inline-flex max-w-full gap-0.5 overflow-x-auto rounded-lg bg-secondary p-1 [scrollbar-width:none]", className)}
        {...props}
      />
    );
  },
);

export const SegmentedTrigger = forwardRef<ComponentRef<typeof RadixTabs.Trigger>, ComponentPropsWithoutRef<typeof RadixTabs.Trigger>>(
  function SegmentedTrigger({ className, ...props }, ref) {
    return (
      <RadixTabs.Trigger
        ref={ref}
        className={cn(
          "inline-flex h-8 items-center gap-2 whitespace-nowrap rounded-md px-3 text-sm font-medium text-muted-foreground",
          "transition-[background-color,color,box-shadow] duration-150 ease-out-soft",
          "hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-ring",
          "data-[state=active]:bg-surface data-[state=active]:text-foreground data-[state=active]:shadow-sm",
          className,
        )}
        {...props}
      />
    );
  },
);
