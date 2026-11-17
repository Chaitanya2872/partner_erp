import { Controller, type Control } from "react-hook-form";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { sanitizeDecimal } from "@/utils/decimal";
import { formatCurrency } from "@/utils/format";
import type { LineKind } from "../types/estimate.types";

/** The line fields both document editors share; installation lines use description and unit as typed text. */
export interface DocumentLineValues {
  key: string;
  id?: string;
  kind: LineKind;
  productId: string;
  description: string;
  unit: string;
  quantity: string;
  unitPrice: string;
  gstPercent: string;
}

export interface InstallationFormValues {
  lines: DocumentLineValues[];
}

export interface InstallationRow {
  /** Position in the form's `lines` array (installation lines sit after the product lines). */
  index: number;
  fieldKey: string;
  line: DocumentLineValues;
  errors?: Partial<Record<keyof DocumentLineValues, { message?: string }>>;
  /** Server figures from the preview, when there is one. */
  amount?: string | number;
  tax?: string | number;
  total?: string | number;
}

interface InstallationLinesProps {
  control: Control<InstallationFormValues>;
  rows: InstallationRow[];
  /** Invoices show the tax and the line total instead of the amount. */
  showTax?: boolean;
  /** The line's GST rate can't be edited (Invoices without approve keep a saved line's rate). */
  gstLocked?: (line: DocumentLineValues) => boolean;
  /** Installation subtotal from the server preview (sum of the lines' amounts before discount). */
  subtotal?: string | number;
  subtotalLabel?: string;
  canAdd: boolean;
  onAdd: () => void;
  onRemove: (index: number) => void;
}

/**
 * The "Installation" section of an estimate or invoice editor: priced work lines (description, quantity, unit, rate,
 * GST %) that have no product. Totals and tax come from the server, like every other line.
 */
export function InstallationLines({ control, rows, showTax, gstLocked, subtotal, subtotalLabel = "Installation subtotal", canAdd, onAdd, onRemove }: InstallationLinesProps) {
  return (
    <div className="space-y-3">
      {rows.length > 0 ? (
        <div className="relative overflow-x-auto rounded-lg border border-border">
          <table className="w-full min-w-[760px] text-sm">
            <caption className="sr-only">Installation lines</caption>
            <thead className="bg-surface-sunken text-left text-xs text-muted-foreground">
              <tr>
                <th scope="col" className="px-3 py-2 font-medium">Description</th>
                <th scope="col" className="w-24 px-3 py-2 font-medium">Qty</th>
                <th scope="col" className="w-24 px-3 py-2 font-medium">Unit</th>
                <th scope="col" className="w-32 px-3 py-2 font-medium">Rate (₹)</th>
                <th scope="col" className="w-24 px-3 py-2 font-medium">GST %</th>
                {showTax ? (
                  <>
                    <th scope="col" className="w-28 px-3 py-2 text-right font-medium">Tax</th>
                    <th scope="col" className="w-32 px-3 py-2 text-right font-medium">Total</th>
                  </>
                ) : (
                  <th scope="col" className="w-32 px-3 py-2 text-right font-medium">Amount</th>
                )}
                <th scope="col" className="w-12 px-3 py-2"><span className="sr-only">Remove</span></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, n) => (
                <InstallationRowView key={row.fieldKey} row={row} position={n + 1} control={control} showTax={showTax} gstLocked={gstLocked?.(row.line)} onRemove={() => onRemove(row.index)} />
              ))}
            </tbody>
            {subtotal !== undefined ? (
              <tfoot>
                <tr className="border-t border-border bg-surface-sunken">
                  <th scope="row" colSpan={showTax ? 7 : 6} className="px-3 py-2 text-right text-xs font-medium text-muted-foreground">
                    {subtotalLabel}
                  </th>
                  <td className="px-3 py-2 text-right font-mono tabular whitespace-nowrap font-semibold">{formatCurrency(subtotal)}</td>
                  <td />
                </tr>
              </tfoot>
            ) : null}
          </table>
        </div>
      ) : (
        <p className="rounded-lg border border-dashed border-border px-4 py-6 text-center text-sm text-muted-foreground">
          No installation lines. Add one if installation is part of this document; it is optional.
        </p>
      )}
      {canAdd ? (
        <Button onClick={onAdd}>
          <Plus aria-hidden="true" /> Add installation line
        </Button>
      ) : null}
    </div>
  );
}

function InstallationRowView({
  row,
  position,
  control,
  showTax,
  gstLocked,
  onRemove,
}: {
  row: InstallationRow;
  position: number;
  control: Control<InstallationFormValues>;
  showTax?: boolean;
  gstLocked?: boolean;
  onRemove: () => void;
}) {
  const { index, errors } = row;
  const name = `installation line ${position}`;
  const errorId = `installation-${index}-error`;
  const messages = [errors?.description?.message, errors?.quantity?.message, errors?.unit?.message, errors?.unitPrice?.message, errors?.gstPercent?.message].filter(Boolean);
  const decimal = (field: "quantity" | "unitPrice" | "gstPercent", label: string, scale: number, readOnly = false) => (
    <Controller
      control={control}
      name={`lines.${index}.${field}`}
      render={({ field: f }) => (
        <Input
          ref={f.ref}
          value={f.value}
          onChange={(e) => f.onChange(sanitizeDecimal(e.target.value, scale))}
          onBlur={f.onBlur}
          readOnly={readOnly}
          inputMode="decimal"
          autoComplete="off"
          aria-label={`${label} of ${name}`}
          aria-invalid={errors?.[field] ? true : undefined}
          aria-describedby={errors?.[field] ? errorId : undefined}
          className="font-mono tabular"
        />
      )}
    />
  );
  const text = (field: "description" | "unit", label: string, maxLength: number) => (
    <Controller
      control={control}
      name={`lines.${index}.${field}`}
      render={({ field: f }) => (
        <Input
          ref={f.ref}
          value={f.value}
          onChange={f.onChange}
          onBlur={f.onBlur}
          maxLength={maxLength}
          autoComplete="off"
          aria-label={`${label} of ${name}`}
          aria-invalid={errors?.[field] ? true : undefined}
          aria-describedby={errors?.[field] ? errorId : undefined}
        />
      )}
    />
  );
  return (
    <tr className="border-t border-border align-top">
      <td className="px-3 py-2">
        {text("description", "Description", 300)}
        {messages.length > 0 ? (
          <p id={errorId} className="mt-1 text-xs text-danger">
            {messages.join(" ")}
          </p>
        ) : null}
      </td>
      <td className="px-3 py-2">{decimal("quantity", "Quantity", 3)}</td>
      <td className="px-3 py-2">{text("unit", "Unit", 20)}</td>
      <td className="px-3 py-2">{decimal("unitPrice", "Rate", 2)}</td>
      <td className="px-3 py-2">{decimal("gstPercent", "GST", 3, gstLocked)}</td>
      {showTax ? (
        <>
          <td className="px-3 py-2 text-right font-mono tabular whitespace-nowrap">{row.tax === undefined ? "—" : formatCurrency(row.tax)}</td>
          <td className="px-3 py-2 text-right font-mono tabular whitespace-nowrap">{row.total === undefined ? "—" : formatCurrency(row.total)}</td>
        </>
      ) : (
        <td className="px-3 py-2 text-right font-mono tabular whitespace-nowrap">{row.amount === undefined ? "—" : formatCurrency(row.amount)}</td>
      )}
      <td className="px-3 py-2 text-right">
        <Button size="icon-sm" variant="ghost" onClick={onRemove} aria-label={`Remove ${name}`}>
          <Trash2 aria-hidden="true" />
        </Button>
      </td>
    </tr>
  );
}
