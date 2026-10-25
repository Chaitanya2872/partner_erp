import { cva, type VariantProps } from "class-variance-authority";

/** Class variants shared by Button and Badge (kept apart from the components so fast refresh works). */
export const buttonVariants = cva(
  "inline-flex shrink-0 items-center justify-center gap-1.5 whitespace-nowrap rounded-md border font-medium transition-colors " +
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring " +
    "disabled:pointer-events-none disabled:opacity-50 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        primary: "border-primary bg-primary text-primary-foreground shadow-xs hover:border-primary-hover hover:bg-primary-hover",
        secondary: "border-transparent bg-secondary text-secondary-foreground hover:bg-active",
        outline: "border-border-strong bg-surface text-foreground shadow-xs hover:border-accent hover:bg-hover",
        destructive: "border-danger-solid bg-danger-solid text-white shadow-xs hover:opacity-90",
        "destructive-outline": "border-border-strong bg-surface text-danger hover:border-danger hover:bg-danger-bg",
        ghost: "border-transparent bg-transparent text-foreground hover:bg-hover",
        link: "h-auto border-transparent bg-transparent p-0 text-accent-ink underline-offset-2 hover:underline",
      },
      size: {
        sm: "h-8 px-2.5 text-xs [&_svg]:size-3.5",
        md: "h-9 px-3.5 text-sm [&_svg]:size-4",
        lg: "h-11 px-5 text-sm [&_svg]:size-4",
        icon: "size-9 p-0 [&_svg]:size-4",
        "icon-sm": "size-8 p-0 [&_svg]:size-4",
      },
    },
    defaultVariants: { variant: "outline", size: "md" },
  },
);

export const badgeVariants = cva(
  "inline-flex max-w-full items-center gap-1 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-semibold [&_svg]:size-3 [&_svg]:shrink-0",
  {
    variants: {
      tone: {
        neutral: "bg-secondary text-secondary-foreground",
        success: "bg-success-bg text-success",
        warning: "bg-warning-bg text-warning",
        danger: "bg-danger-bg text-danger",
        info: "bg-info-bg text-info",
        accent: "bg-accent text-accent-foreground",
        outline: "border border-border-strong text-foreground",
      },
    },
    defaultVariants: { tone: "neutral" },
  },
);

export type BadgeTone = NonNullable<VariantProps<typeof badgeVariants>["tone"]>;
