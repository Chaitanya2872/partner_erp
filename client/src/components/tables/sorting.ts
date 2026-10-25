export interface SortState {
  key: string;
  direction: "asc" | "desc";
}

/** `fullName,asc` for the API's `sort` parameter. */
export function toSortParam(sort: SortState | null | undefined): string | undefined {
  return sort ? `${sort.key},${sort.direction}` : undefined;
}
