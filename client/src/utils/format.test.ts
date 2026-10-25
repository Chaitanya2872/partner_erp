import { describe, expect, it } from "vitest";
import {
  formatCurrency,
  formatDate,
  formatDocumentNumber,
  formatNumber,
  formatPercent,
  humanizeCode,
  initialsOf,
  NOT_AVAILABLE,
} from "./format";
import { sanitizeDecimal } from "./decimal";

describe("currency formatting", () => {
  it("uses the Indian numbering system with two decimals", () => {
    expect(formatCurrency(125000)).toBe("₹1,25,000.00");
    expect(formatCurrency(1250000)).toBe("₹12,50,000.00");
    expect(formatCurrency(5749)).toBe("₹5,749.00");
    expect(formatCurrency(0)).toBe("₹0.00");
  });

  it("formats decimal strings exactly, without floating-point loss", () => {
    expect(formatCurrency("125000.5")).toBe("₹1,25,000.50");
    // Beyond Number precision: the string is formatted as given.
    expect(formatCurrency("90071992547409.99")).toBe("₹9,00,71,99,25,47,409.99");
  });

  it("handles negative, missing and invalid values", () => {
    expect(formatCurrency(-2500)).toBe("-₹2,500.00");
    expect(formatCurrency(null)).toBe(NOT_AVAILABLE);
    expect(formatCurrency(undefined)).toBe(NOT_AVAILABLE);
    expect(formatCurrency("abc")).toBe(NOT_AVAILABLE);
    expect(formatCurrency(Number.NaN)).toBe(NOT_AVAILABLE);
  });

  it("can drop the paise for whole-rupee summaries", () => {
    expect(formatCurrency(1250000, { whole: true })).toBe("₹12,50,000");
  });
});

describe("number, percentage and date formatting", () => {
  it("formats numbers and percentages", () => {
    expect(formatNumber(1250000)).toBe("12,50,000");
    expect(formatPercent(18)).toBe("18.00%");
    expect(formatPercent("12.5")).toBe("12.50%");
    expect(formatPercent(null)).toBe(NOT_AVAILABLE);
  });

  it("formats calendar dates without timezone shifts", () => {
    expect(formatDate("2026-10-25")).toBe("25 Oct 2026");
    expect(formatDate(null)).toBe(NOT_AVAILABLE);
    expect(formatDate("not a date")).toBe(NOT_AVAILABLE);
  });

  it("shows document numbers as issued", () => {
    expect(formatDocumentNumber("  INV-2026-0042 ")).toBe("INV-2026-0042");
    expect(formatDocumentNumber("")).toBe(NOT_AVAILABLE);
  });

  it("humanises codes and builds initials", () => {
    expect(humanizeCode("PASSWORD_CHANGED")).toBe("Password changed");
    expect(initialsOf("Asha Rao")).toBe("AR");
    expect(initialsOf("  madhu  ")).toBe("M");
  });
});

describe("decimal input sanitising", () => {
  it("keeps digits and one decimal point with the given scale", () => {
    expect(sanitizeDecimal("1,25,000.555")).toBe("125000.55");
    expect(sanitizeDecimal("12a3.4.5")).toBe("123.45");
    expect(sanitizeDecimal("-50")).toBe("50");
    expect(sanitizeDecimal("-50", 2, true)).toBe("-50");
    expect(sanitizeDecimal("7.9", 0)).toBe("79");
  });
});
