/** Wire types for /api/v1/leads (server: modules/leads/dto). Money arrives as JSON numbers or decimal strings. */

import type { Money } from "@/modules/partners/types/partner.types";

export type LeadStage = "NEW" | "REGISTERED" | "PRESALES" | "SITE_VISIT" | "ESTIMATE_SENT" | "NEGOTIATION" | "WON" | "LOST";
export type ProjectType = "VILLA" | "APARTMENT" | "MULTI_UNIT_PROJECT" | "OFFICE" | "RETROFIT_RENOVATION" | "OTHER";
export type LeadEvent = "CREATED" | "STAGE_CHANGED" | "ACKNOWLEDGED" | "AUTO_STAGE";

/** BR-L3: Won and Lost are closed; leaving them (reopening) needs Leads approve (D-04). */
export const CLOSED_STAGES: readonly LeadStage[] = ["WON", "LOST"];
export const isClosed = (stage: LeadStage) => CLOSED_STAGES.includes(stage);

export interface PartnerRef {
  id: string;
  name: string;
}

/** BUSINESS_RULES §4.1 clocks with the targets they were measured against. */
export interface SlaStatus {
  ackHours: number | null;
  ackBreach: boolean;
  estimateDays: number | null;
  estimateBreach: boolean;
  hasEstimate: boolean;
  ackTargetHours: number;
  estimateTargetDays: number;
  /** Days until protection ends (0 on its last day); null when not protected. */
  protectionDaysLeft: number | null;
}

export interface LeadSummary {
  id: string;
  clientName: string;
  clientPhone: string | null;
  city: string | null;
  projectType: ProjectType;
  projectTypeLabel: string;
  units: number | null;
  budget: Money | null;
  stage: LeadStage;
  stageLabel: string;
  partner: PartnerRef;
  acknowledgedAt: string | null;
  lockedFeePercent: Money | null;
  protectionUntil: string | null;
  protectionActive: boolean;
  conflict: boolean;
  sla: SlaStatus;
  createdAt: string;
  version: number;
}

export interface Lead extends Omit<LeadSummary, "sla"> {
  presalesOwner: string | null;
  expectedClose: string | null;
  source: string | null;
  notes: string | null;
  acknowledgedByName: string | null;
  /** The partner currently protecting the same phone, if any (BR-L6). */
  conflictPartnerName: string | null;
  sla: SlaStatus;
  updatedAt: string;
}

export interface LeadHistoryEntry {
  id: string;
  event: LeadEvent;
  eventLabel: string;
  fromStage: LeadStage | null;
  fromStageLabel: string | null;
  toStage: LeadStage | null;
  toStageLabel: string | null;
  reason: string | null;
  actorId: string | null;
  actorName: string | null;
  occurredAt: string;
}

export interface LeadOption<T extends string = string> {
  code: T;
  label: string;
}

export interface LeadPartnerOption {
  id: string;
  name: string;
  statusLabel: string;
  active: boolean;
  tierName: string;
  feePercent: Money;
}

export interface LeadMeta {
  stages: LeadOption<LeadStage>[];
  projectTypes: LeadOption<ProjectType>[];
  partners: LeadPartnerOption[];
  ackTargetHours: number;
  estimateTargetDays: number;
  protectionDays: number;
}

export interface LeadInput {
  clientName: string;
  clientPhone: string;
  city: string;
  partnerId: string;
  projectType: ProjectType | "";
  units: number | null;
  budget: string | null;
  stage: LeadStage;
  stageReason: string;
  presalesOwner: string;
  expectedClose: string | null;
  source: string;
  notes: string;
  version?: number;
}

export interface LeadFilters {
  q?: string;
  stage?: LeadStage | "";
  partnerId?: string;
  conflict?: string;
  slaBreach?: string;
  page: number;
  size: number;
  sort?: string;
}
