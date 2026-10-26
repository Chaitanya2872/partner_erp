import { StatusBadge } from "@/components/data/StatusBadge";
import type { EstimateStatus } from "../types/estimate.types";

/** Stored status, shown as Expired when the server derived it (D-29: sent and past its validity date). */
export function EstimateStatusBadge({ status, expired }: { status: EstimateStatus; expired: boolean }) {
  return <StatusBadge domain="estimate" status={expired ? "EXPIRED" : status} />;
}
