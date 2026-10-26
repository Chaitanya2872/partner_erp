import { apiGet, apiSend } from "@/services/api";
import type { GstRegistration, GstRegistrationInput } from "@/modules/invoices/types/invoice.types";

export const settingsService = {
  /** Settings read or Invoices read. */
  gstRegistration: (signal?: AbortSignal) => apiGet<GstRegistration>("/settings/gst-registration", undefined, signal),
  /** Settings write. Issued invoices keep the copy they took at issue. */
  updateGstRegistration: (input: GstRegistrationInput) => apiSend<GstRegistration>("put", "/settings/gst-registration", input),
};
