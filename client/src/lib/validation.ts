import { z } from "zod";
import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import { ApiRequestError } from "@/services/api";

/*
 * Shared Zod schemas and React Hook Form helpers. Client-side rules mirror the server for fast feedback only;
 * the server validates again and its field errors are mapped back onto the form with applyServerErrors.
 */

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const emailSchema = z.string().trim().min(1, "Email is required.").regex(EMAIL, "Enter a valid email address.").max(254, "Use at most 254 characters.");

/** Server password policy (app.security.password): 12–128 characters with a letter and a digit. */
export const passwordPolicySchema = z
  .string()
  .min(12, "Use at least 12 characters.")
  .max(128, "Use at most 128 characters.")
  .regex(/[A-Za-z]/, "Include at least one letter.")
  .regex(/\d/, "Include at least one digit.");

/** The same policy as a checklist for live feedback while typing. */
export const PASSWORD_RULES: { label: string; test: (value: string) => boolean }[] = [
  { label: "12 to 128 characters", test: (v) => v.length >= 12 && v.length <= 128 },
  { label: "At least one letter", test: (v) => /[A-Za-z]/.test(v) },
  { label: "At least one digit", test: (v) => /\d/.test(v) },
];

/**
 * A decimal amount kept as a string (no floating point). `scale` limits decimals; `min`/`max` are compared as
 * decimal strings, which is exact for the magnitudes used here.
 */
export function decimalString(options: { required?: boolean; scale?: number; min?: string; max?: string; label?: string } = {}) {
  const { required = true, scale = 2, min, max, label = "Amount" } = options;
  const pattern = new RegExp(`^\\d+(\\.\\d{1,${scale}})?$`);
  return z
    .string()
    .trim()
    .refine((v) => !required || v.length > 0, { message: `${label} is required.` })
    .refine((v) => v.length === 0 || pattern.test(v), { message: `Enter a number with up to ${scale} decimals.` })
    .refine((v) => v.length === 0 || min === undefined || compareDecimal(v, min) >= 0, { message: `${label} must be at least ${min}.` })
    .refine((v) => v.length === 0 || max === undefined || compareDecimal(v, max) <= 0, { message: `${label} must be at most ${max}.` });
}

/** Compares two non-negative decimal strings exactly. */
export function compareDecimal(a: string, b: string): number {
  const [ai, af = ""] = a.split(".");
  const [bi, bf = ""] = b.split(".");
  const intA = ai.replace(/^0+(?=\d)/, "");
  const intB = bi.replace(/^0+(?=\d)/, "");
  if (intA.length !== intB.length) return intA.length < intB.length ? -1 : 1;
  if (intA !== intB) return intA < intB ? -1 : 1;
  const len = Math.max(af.length, bf.length);
  const fa = af.padEnd(len, "0");
  const fb = bf.padEnd(len, "0");
  return fa === fb ? 0 : fa < fb ? -1 : 1;
}

/**
 * Copies server field errors onto the form. Returns the messages that belong to no known field, for a FormError.
 * Non-validation errors are returned whole so the caller can show them.
 */
export function applyServerErrors<T extends FieldValues>(error: unknown, setError: UseFormSetError<T>, fields: readonly Path<T>[]): string[] {
  if (!(error instanceof ApiRequestError) || error.fieldErrors.length === 0) {
    return [];
  }
  const unmatched: string[] = [];
  let focused = false;
  for (const fe of error.fieldErrors) {
    if ((fields as readonly string[]).includes(fe.field)) {
      setError(fe.field as Path<T>, { type: "server", message: fe.message }, { shouldFocus: !focused });
      focused = true;
    } else {
      unmatched.push(fe.message);
    }
  }
  return unmatched;
}
