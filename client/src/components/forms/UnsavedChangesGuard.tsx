import { useEffect } from "react";
import { useBlocker } from "react-router";
import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";

/**
 * Warns before leaving a page with unsaved edits: in-app navigation shows a confirmation dialog, and closing or
 * reloading the tab triggers the browser's own prompt.
 */
export function UnsavedChangesGuard({ when, message = "Your changes have not been saved and will be lost." }: { when: boolean; message?: string }) {
  const blocker = useBlocker(({ currentLocation, nextLocation }) => when && currentLocation.pathname !== nextLocation.pathname);

  useEffect(() => {
    if (!when) return;
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [when]);

  return (
    <ConfirmDialog
      open={blocker.state === "blocked"}
      title="Leave without saving?"
      confirmLabel="Leave page"
      danger
      onCancel={() => blocker.reset?.()}
      onConfirm={() => blocker.proceed?.()}
    >
      <p>{message}</p>
    </ConfirmDialog>
  );
}
