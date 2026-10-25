/** Response envelope produced by the backend for every JSON response (docs/architecture/API_DESIGN.md §1). */
export interface ApiEnvelope<T> {
  success: boolean;
  data?: T;
  meta?: PageMeta;
  error?: ApiErrorBody;
  timestamp: string;
}

export interface ApiErrorBody {
  type?: string;
  title?: string;
  status: number;
  code: string;
  detail?: string;
  fieldErrors?: FieldError[];
  requestId?: string;
}

export interface FieldError {
  field: string;
  message: string;
}

export interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Page<T> {
  content: T[];
  meta: PageMeta;
}
