/**
 * Saves text as a file through a temporary link. CSV gets a UTF-8 byte-order mark so spreadsheet programs read
 * ₹ and non-ASCII names correctly (the browser drops the server's mark when decoding).
 */
export function saveTextFile(text: string, filename: string, type = "text/csv;charset=utf-8"): void {
  const body = type.startsWith("text/csv") && !text.startsWith("﻿") ? "﻿" + text : text;
  const url = URL.createObjectURL(new Blob([body], { type }));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
