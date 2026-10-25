/**
 * TanStack Query key factories. Keys are hierarchical arrays so a prefix invalidates everything below it:
 * `invalidateQueries({ queryKey: queryKeys.users.all })` refreshes lists, details and permission matrices.
 * Add a factory here for every new resource instead of writing literal keys in components.
 */
export const queryKeys = {
  modules: ["modules"] as const,
  roles: ["roles"] as const,
  users: {
    all: ["users"] as const,
    list: (filters: Record<string, unknown>) => ["users", "list", filters] as const,
    detail: (id: string) => ["users", id] as const,
    permissions: (id: string) => ["users", id, "permissions"] as const,
  },
  products: {
    all: ["products"] as const,
    list: (filters: Record<string, unknown>) => ["products", "list", filters] as const,
  },
  auditLogs: {
    all: ["audit-logs"] as const,
    list: (filters: Record<string, unknown>) => ["audit-logs", "list", filters] as const,
  },
};
