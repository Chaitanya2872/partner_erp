import { useModules } from "@/hooks/useModules";
import { PageContainer, PageHeader } from "@/components/layout/Page";
import { Card } from "@/components/ui/Card";
import { ComingSoon } from "@/components/feedback/States";
import type { ModuleCode } from "@/types/common.types";

/** Clearly labelled placeholder for modules whose screens are delivered in a later phase. */
export function ComingSoonPage({ module, phase }: { module: ModuleCode; phase: number }) {
  const { labelOf } = useModules();
  const label = labelOf(module);
  return (
    <PageContainer>
      <PageHeader title={label} description="Not yet available." />
      <Card>
        <ComingSoon title={label} phase={phase}>
          This module is delivered in Phase {phase}. Your account already has access to it; the screens will appear here once that
          phase is complete.
        </ComingSoon>
      </Card>
    </PageContainer>
  );
}
