import { forwardRef, type ComponentPropsWithoutRef, type ComponentRef } from "react";
import { DropdownMenu as RadixMenu } from "radix-ui";
import { Check } from "lucide-react";
import { cn } from "@/lib/cn";

export const DropdownMenu = RadixMenu.Root;
export const DropdownMenuTrigger = RadixMenu.Trigger;
export const DropdownMenuGroup = RadixMenu.Group;
export const DropdownMenuRadioGroup = RadixMenu.RadioGroup;

export const DropdownMenuContent = forwardRef<ComponentRef<typeof RadixMenu.Content>, ComponentPropsWithoutRef<typeof RadixMenu.Content>>(
  function DropdownMenuContent({ className, sideOffset = 6, align = "end", ...props }, ref) {
    return (
      <RadixMenu.Portal>
        <RadixMenu.Content
          ref={ref}
          sideOffset={sideOffset}
          align={align}
          collisionPadding={8}
          className={cn(
            "z-50 min-w-48 max-w-[calc(100vw-16px)] overflow-hidden rounded-lg border border-border bg-surface p-1 text-foreground shadow-md origin-[var(--radix-dropdown-menu-content-transform-origin)] data-[state=open]:animate-pop-in data-[state=closed]:animate-pop-out",
            className,
          )}
          {...props}
        />
      </RadixMenu.Portal>
    );
  },
);

const itemClass =
  "relative flex min-h-9 cursor-pointer select-none items-center gap-2 rounded-md px-2.5 py-1.5 text-sm outline-none transition-colors duration-100 " +
  "data-[highlighted]:bg-hover data-[disabled]:pointer-events-none data-[disabled]:opacity-50 " +
  "[&_svg]:size-4 [&_svg]:shrink-0 [&_svg]:text-muted-foreground";

export const DropdownMenuItem = forwardRef<
  ComponentRef<typeof RadixMenu.Item>,
  ComponentPropsWithoutRef<typeof RadixMenu.Item> & { destructive?: boolean }
>(function DropdownMenuItem({ className, destructive, ...props }, ref) {
  return (
    <RadixMenu.Item ref={ref} className={cn(itemClass, destructive ? "text-danger [&_svg]:text-danger" : undefined, className)} {...props} />
  );
});

const indicator = (
  <span className="absolute left-2.5 flex size-4 items-center justify-center">
    <RadixMenu.ItemIndicator>
      <Check className="!text-foreground" aria-hidden="true" />
    </RadixMenu.ItemIndicator>
  </span>
);

export const DropdownMenuCheckboxItem = forwardRef<
  ComponentRef<typeof RadixMenu.CheckboxItem>,
  ComponentPropsWithoutRef<typeof RadixMenu.CheckboxItem>
>(function DropdownMenuCheckboxItem({ className, children, ...props }, ref) {
  return (
    <RadixMenu.CheckboxItem ref={ref} className={cn(itemClass, "pl-8", className)} {...props}>
      {indicator}
      {children}
    </RadixMenu.CheckboxItem>
  );
});

export const DropdownMenuRadioItem = forwardRef<ComponentRef<typeof RadixMenu.RadioItem>, ComponentPropsWithoutRef<typeof RadixMenu.RadioItem>>(
  function DropdownMenuRadioItem({ className, children, ...props }, ref) {
    return (
      <RadixMenu.RadioItem ref={ref} className={cn(itemClass, "pl-8", className)} {...props}>
        {indicator}
        {children}
      </RadixMenu.RadioItem>
    );
  },
);

export function DropdownMenuLabel({ className, ...props }: ComponentPropsWithoutRef<typeof RadixMenu.Label>) {
  return (
    <RadixMenu.Label
      className={cn("px-2.5 py-1.5 text-2xs font-semibold uppercase tracking-wider text-muted-foreground", className)}
      {...props}
    />
  );
}

export function DropdownMenuSeparator({ className, ...props }: ComponentPropsWithoutRef<typeof RadixMenu.Separator>) {
  return <RadixMenu.Separator className={cn("-mx-1 my-1 h-px bg-border", className)} {...props} />;
}
