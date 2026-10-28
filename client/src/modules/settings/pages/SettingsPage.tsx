import { useSearchParams } from "react-router";
import { PageContainer, PageHeader } from "@/components/layout/Page";
import { Alert } from "@/components/ui/Alert";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import { TdsSettingsCard } from "@/modules/payouts/components/TdsSettingsCard";
import { GstRegistrationCard } from "../components/GstRegistrationCard";
import { BrandingNotice, ClawbackNotice, CompanySection } from "../components/CompanySettings";
import { CONTACT_FIELDS, DOCUMENT_FIELDS, GST_FIELDS, PROTECTION_FIELDS, SLA_FIELDS } from "../lib/companyFields";
import { PartnerTierSettings } from "../components/PartnerTierSettings";
import { AccessLinks, ControlsCard, GstTreatmentCard, NumberingCard, SecurityCard } from "../components/AdministrationCards";

const SECTIONS = [
  { value: "company", label: "Company" },
  { value: "documents", label: "Documents" },
  { value: "tax", label: "Tax & GST" },
  { value: "tds", label: "TDS" },
  { value: "commission", label: "Tiers & commission" },
  { value: "preferences", label: "Preferences" },
  { value: "controls", label: "Approvals" },
  { value: "security", label: "Security" },
  { value: "access", label: "Users & access" },
] as const;
type Section = (typeof SECTIONS)[number]["value"];

/**
 * Settings and Administration. Every section reads from the server and every change is checked there again: the
 * buttons shown here follow the permissions the server reports (Settings write; Manage commercial settings for tiers
 * and lead protection; Payouts approve to confirm TDS). The open section is kept in the URL.
 */
export function SettingsPage() {
  const [params, setParams] = useSearchParams();
  const raw = params.get("section");
  const section: Section = SECTIONS.some((s) => s.value === raw) ? (raw as Section) : "company";
  const select = (value: string) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        next.set("section", value);
        return next;
      },
      { replace: true },
    );

  return (
    <PageContainer>
      <PageHeader title="Settings" description="Company details, document defaults, tax, commission and administration." />
      <Tabs value={section} onValueChange={select}>
        <TabsList aria-label="Settings sections">
          {SECTIONS.map((s) => (
            <TabsTrigger key={s.value} value={s.value}>
              {s.label}
            </TabsTrigger>
          ))}
        </TabsList>

        <TabsContent value="company" className="space-y-4">
          <GstRegistrationCard />
          <CompanySection title="Contact details" description="Printed on estimates and invoices." fields={CONTACT_FIELDS} />
          <BrandingNotice />
        </TabsContent>

        <TabsContent value="documents" className="space-y-4">
          <CompanySection
            title="Document defaults"
            description="Used when an estimate or invoice is created."
            fields={DOCUMENT_FIELDS}
            note={<p className="text-sm text-muted-foreground">Existing documents keep their own dates and notes; a change here never alters them.</p>}
          />
          <NumberingCard />
        </TabsContent>

        <TabsContent value="tax" className="space-y-4">
          <Alert tone="info">The supplier&apos;s GST registration (legal name, address, state and GSTIN) is on the Company tab.</Alert>
          <CompanySection title="GST defaults" fields={GST_FIELDS} />
          <GstTreatmentCard />
        </TabsContent>

        <TabsContent value="tds" className="space-y-4">
          <TdsSettingsCard />
        </TabsContent>

        <TabsContent value="commission" className="space-y-4">
          <PartnerTierSettings />
          <CompanySection
            title="Lead protection"
            description="How long an acknowledged registration protects the partner's claim."
            fields={PROTECTION_FIELDS}
            target="commercial"
          />
          <Alert tone="neutral" title="Fee overrides">
            A referral fee different from the partner&apos;s default on an estimate or invoice needs Partners approve and a reason, and is recorded
            on the document and in the audit log.
          </Alert>
          <ClawbackNotice />
        </TabsContent>

        <TabsContent value="preferences" className="space-y-4">
          <CompanySection
            title="Lead service levels"
            description="Targets for the lead SLA badges and dashboard alerts. Changes apply to open leads straight away."
            fields={SLA_FIELDS}
            note={<Alert tone="warning">These targets are placeholders from the source. Set them from Annexure B of the partner agreement.</Alert>}
          />
        </TabsContent>

        <TabsContent value="controls" className="space-y-4">
          <ControlsCard />
        </TabsContent>

        <TabsContent value="security" className="space-y-4">
          <SecurityCard />
        </TabsContent>

        <TabsContent value="access" className="space-y-4">
          <AccessLinks />
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}
