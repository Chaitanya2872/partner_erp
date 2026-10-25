import { Skeleton } from "@/components/ui/Skeleton";

/** Placeholder while a lazily loaded page arrives. Announced once; the shapes are decorative. */
export function PageSkeleton() {
  return (
    <div role="status" aria-live="polite" aria-label="Loading page" className="mx-auto w-full max-w-screen-2xl space-y-6">
      <span className="sr-only">Loading page…</span>
      <div className="space-y-2">
        <Skeleton className="h-7 w-56" />
        <Skeleton className="h-4 w-80 max-w-full" />
      </div>
      <Skeleton className="h-9 w-full max-w-xl" />
      <div className="space-y-2 rounded-lg border border-border bg-surface p-4">
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-8 w-full" />
        ))}
      </div>
    </div>
  );
}
