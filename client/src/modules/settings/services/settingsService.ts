import { apiGet, apiSend } from "@/services/api";
import type { GstRegistration, GstRegistrationInput } from "@/modules/invoices/types/invoice.types";
import type { CompanySettings, CompanySettingsInput, Control, NumberSeries, PartnerTiers, SecurityPolicy, TierInput } from "../types/settings.types";

export const settingsService = {
  /** Settings read or Invoices read. */
  gstRegistration: (signal?: AbortSignal) => apiGet<GstRegistration>("/settings/gst-registration", undefined, signal),
  /** Settings write. Issued invoices keep the copy they took at issue. */
  updateGstRegistration: (input: GstRegistrationInput) => apiSend<GstRegistration>("put", "/settings/gst-registration", input),
  /** Settings read. */
  company: (signal?: AbortSignal) => apiGet<CompanySettings>("/settings/company", undefined, signal),
  /** Settings write. Applies to documents and leads from now on; stored documents keep their own values. */
  updateCompany: (input: CompanySettingsInput) => apiSend<CompanySettings>("put", "/settings/company", input),
  /** Settings write + Manage commercial settings. */
  updateCommercial: (leadProtectionDays: number | null, version: number) =>
    apiSend<CompanySettings>("put", "/settings/commercial", { leadProtectionDays, version }),
  /** Settings read or Partners read. */
  tiers: (signal?: AbortSignal) => apiGet<PartnerTiers>("/settings/tiers", undefined, signal),
  /** Settings write + Manage commercial settings; all four tiers at once. */
  updateTiers: (tiers: TierInput[]) => apiSend<PartnerTiers>("put", "/settings/tiers", { tiers }),
  numbering: (signal?: AbortSignal) => apiGet<NumberSeries[]>("/settings/numbering", undefined, signal),
  security: (signal?: AbortSignal) => apiGet<SecurityPolicy>("/settings/security", undefined, signal),
  controls: (signal?: AbortSignal) => apiGet<Control[]>("/settings/controls", undefined, signal),
};
