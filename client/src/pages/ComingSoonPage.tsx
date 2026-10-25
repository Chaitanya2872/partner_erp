import { useModules } from "@/hooks/useModules";
import type { ModuleCode } from "@/types/common.types";

/** Clearly labelled placeholder for modules whose screens are delivered in a later phase. */
export function ComingSoonPage({ module, phase }: { module: ModuleCode; phase: number }) {
  const { labelOf } = useModules();
  return (
    <>
      <div className="top">
        <div>
          <h1>{labelOf(module)}</h1>
          <p>Not yet available.</p>
        </div>
      </div>
      <div className="notice info">
        This module is delivered in Phase {phase}. Your account already has access to it; the screens will appear here
        once that phase is complete.
      </div>
    </>
  );
}
