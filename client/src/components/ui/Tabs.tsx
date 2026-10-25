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
          "-mb-px inline-flex h-10 items-center gap-1.5 whitespace-nowrap border-b-2 border-transparent px-3 text-sm font-medium text-muted-foreground",
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
