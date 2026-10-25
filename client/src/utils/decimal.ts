/** Keeps digits and at most one decimal point with `scale` decimals; optionally a leading minus. */
export function sanitizeDecimal(raw: string, scale = 2, allowNegative = false): string {
  let out = "";
  let seenDot = false;
  let decimals = 0;
  for (const [i, ch] of [...raw.replaceAll(",", "")].entries()) {
    if (ch === "-" && allowNegative && i === 0) out += ch;
    else if (ch === "." && !seenDot && scale > 0) {
      seenDot = true;
      out += ch;
    } else if (/\d/.test(ch)) {
      if (seenDot) {
        if (decimals >= scale) continue;
        decimals += 1;
      }
      out += ch;
    }
  }
  return out;
}
