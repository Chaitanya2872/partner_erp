/** One audit event as the server returns it. Secrets are never recorded; ipAddress may be withheld. */
export interface AuditLog {
  id: string;
  createdAt: string;
  userId: string | null;
  actorName: string | null;
  actorEmail: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  oldValues: unknown;
  newValues: unknown;
  ipAddress: string | null;
  requestId: string | null;
}

/** A module of the audit viewer and the actions it records (GET /audit-logs/catalog). */
export interface AuditModule {
  code: string;
  label: string;
  actions: string[];
}

export interface AuditActor {
  id: string;
  fullName: string;
  email: string;
}

export interface AuditFilters {
  module?: string;
  action?: string;
  userId?: string;
  from?: string;
  to?: string;
  q?: string;
}
