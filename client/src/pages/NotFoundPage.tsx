import { Link } from "react-router";
import { MapPinOff } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";

export function NotFoundPage() {
  useDocumentTitle("Page not found");
  return (
    <div className="mx-auto flex max-w-md flex-col items-center px-6 py-16 text-center">
      <span className="mb-3 inline-flex size-11 items-center justify-center rounded-full bg-secondary text-muted-foreground">
        <MapPinOff className="size-5" aria-hidden="true" />
      </span>
      <h1 className="font-display text-xl font-semibold">Page not found</h1>
      <p className="mt-1 text-sm text-muted-foreground">The address may be mistyped, or the page has moved.</p>
      <Button asChild className="mt-4">
        <Link to="/">Go to your home page</Link>
      </Button>
    </div>
  );
}
