import { PageContainer, PageHeader } from "@/components/layout/Page";
import { Card } from "@/components/ui/Card";
import { ComingSoon } from "@/components/feedback/States";
import { GstRegistrationCard } from "../components/GstRegistrationCard";

/**
 * Settings. Phase 9 brings the GST registration forward because invoices need it; the remaining company settings
 * (tiers, SLAs, TDS, terms) are delivered in Phase 13.
 */
export function SettingsPage() {
  return (
    <PageContainer>
      <PageHeader title="Settings" description="Company details used across documents." />
      <GstRegistrationCard />
      <Card>
        <ComingSoon title="Other settings" phase={13}>
          Partner tiers, SLAs, TDS, default terms and the remaining company details are delivered in Phase 13.
        </ComingSoon>
      </Card>
    </PageContainer>
  );
}
