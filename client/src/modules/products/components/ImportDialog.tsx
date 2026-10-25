import { useId, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Download } from "lucide-react";
import { Dialog } from "@/components/ui/Dialog";
import { Button } from "@/components/ui/Button";
import { Badge, type BadgeTone } from "@/components/ui/Badge";
import { Alert } from "@/components/ui/Alert";
import { Label } from "@/components/ui/Controls";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { saveTextFile } from "@/utils/download";
import { productService } from "../services/productService";
import type { ImportOutcome, ProductImportResult } from "../types/product.types";

const OUTCOME: Record<ImportOutcome, { label: string; tone: BadgeTone }> = {
  CREATE: { label: "New", tone: "success" },
  UPDATE: { label: "Update", tone: "info" },
  UNCHANGED: { label: "No change", tone: "neutral" },
  ERROR: { label: "Error", tone: "danger" },
};

interface ImportDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * CSV import with an explicit preview → confirm step (D-18). The preview saves nothing; confirming saves every
 * valid row in one transaction and skips rows with errors. A missing column or blank cell keeps the stored value.
 */
export function ImportDialog({ open, onOpenChange }: ImportDialogProps) {
  const queryClient = useQueryClient();
  const inputId = useId();
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ProductImportResult | null>(null);
  const [busy, setBusy] = useState<"preview" | "commit" | "template" | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setFile(null);
    setPreview(null);
    setError(null);
    setBusy(null);
  };

  const close = (next: boolean) => {
    if (busy === "commit") return;
    if (!next) reset();
    onOpenChange(next);
  };

  const runPreview = async () => {
    if (!file) return;
    setBusy("preview");
    setError(null);
    try {
      setPreview(await productService.importCsv(file, true));
    } catch (e) {
      setPreview(null);
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  const commit = async () => {
    if (!file) return;
    setBusy("commit");
    setError(null);
    try {
      const result = await productService.importCsv(file, false);
      const parts = [`${result.created} created`, `${result.updated} updated`];
      if (result.unchanged) parts.push(`${result.unchanged} unchanged`);
      if (result.failed) parts.push(`${result.failed} skipped with errors`);
      toast.success(`Import finished: ${parts.join(", ")}.`);
      await queryClient.invalidateQueries({ queryKey: queryKeys.products.all });
      reset();
      onOpenChange(false);
    } catch (e) {
      setError(errorMessage(e));
      setBusy(null);
    }
  };

  const downloadTemplate = async () => {
    setBusy("template");
    try {
      const { text, filename } = await productService.template();
      saveTextFile(text, filename);
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  const toSave = preview ? preview.created + preview.updated : 0;

  return (
    <Dialog
      open={open}
      onOpenChange={close}
      wide
      title="Import price sheet CSV"
      description="Rows are matched by SKU, ignoring case. A missing column or a blank cell keeps the stored value."
      footer={
        <>
          <Button onClick={() => close(false)} disabled={busy === "commit"}>
            Cancel
          </Button>
          {preview ? (
            <Button variant="primary" onClick={commit} loading={busy === "commit"} loadingText="Importing…" disabled={toSave === 0 || busy !== null}>
              {toSave === 0 ? "Nothing to import" : `Import ${toSave} ${toSave === 1 ? "row" : "rows"}`}
            </Button>
          ) : (
            <Button variant="primary" onClick={runPreview} loading={busy === "preview"} loadingText="Checking…" disabled={!file || busy !== null}>
              Preview
            </Button>
          )}
        </>
      }
    >
      <div className="space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div className="space-y-1.5">
            <Label htmlFor={inputId}>CSV file</Label>
            <input
              id={inputId}
              type="file"
              accept=".csv,text/csv"
              className="block w-full text-sm file:mr-3 file:rounded-md file:border file:border-border file:bg-secondary file:px-3 file:py-1.5 file:text-sm file:font-medium"
              onChange={(e) => {
                setFile(e.target.files?.[0] ?? null);
                setPreview(null);
                setError(null);
              }}
            />
            <p className="text-xs text-muted-foreground">Columns sku, b2c_name and cp_name are required. Up to 5,000 rows, 1 MB.</p>
          </div>
          <Button size="sm" onClick={downloadTemplate} loading={busy === "template"}>
            <Download aria-hidden="true" /> Download template
          </Button>
        </div>

        {error ? (
          <Alert tone="danger" title="The file can't be imported">
            {error}
          </Alert>
        ) : null}

        {preview ? <PreviewTable result={preview} /> : null}
      </div>
    </Dialog>
  );
}

function PreviewTable({ result }: { result: ProductImportResult }) {
  return (
    <div className="space-y-3">
      <p className="text-sm" role="status">
        {result.total} {result.total === 1 ? "row" : "rows"}: {result.created} new, {result.updated} to update, {result.unchanged} unchanged,{" "}
        {result.failed} with errors{result.failed ? " (skipped)" : ""}. Nothing has been saved yet.
      </p>
      <p className="text-xs text-muted-foreground">Recognised columns: {result.columns.join(", ")}.</p>
      <div className="max-h-80 overflow-auto rounded-lg border border-border" tabIndex={0} role="region" aria-label="Import preview">
        <table className="w-full text-sm">
          <caption className="sr-only">Import preview</caption>
          <thead className="sticky top-0 bg-secondary text-left text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="px-3 py-2">
                Row
              </th>
              <th scope="col" className="px-3 py-2">
                SKU
              </th>
              <th scope="col" className="px-3 py-2">
                Result
              </th>
              <th scope="col" className="px-3 py-2">
                Details
              </th>
            </tr>
          </thead>
          <tbody>
            {result.rows.map((r) => (
              <tr key={r.row} className="border-t border-border align-top">
                <td className="px-3 py-2 font-mono tabular">{r.row}</td>
                <td className="px-3 py-2 font-mono">{r.sku ?? "—"}</td>
                <td className="px-3 py-2">
                  <Badge tone={OUTCOME[r.outcome].tone}>{OUTCOME[r.outcome].label}</Badge>
                </td>
                <td className="px-3 py-2">
                  <ul className="space-y-0.5">
                    {r.errors.map((m) => (
                      <li key={m} className="text-danger">
                        {m}
                      </li>
                    ))}
                    {r.changes.map((m) => (
                      <li key={m}>{m}</li>
                    ))}
                    {r.warnings.map((m) => (
                      <li key={m} className="text-warning">
                        {m}
                      </li>
                    ))}
                  </ul>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
