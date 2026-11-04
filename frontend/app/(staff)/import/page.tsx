"use client";

import Link from "next/link";
import { useState, type ChangeEvent } from "react";
import useSWR from "swr";
import { ArrowLeft, Download, FileSpreadsheet, Info, Upload } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useMe } from "@/components/AppShell";
import { ErrorText, Field, PageHeader } from "@/components/common";
import { api, errorMessage, unwrap, type Schemas } from "@/lib/api";
import {
  FIELDS, chooseField, columnHint, examples, guessValues, rejectedRowsCsv, type ImportField, type ImportPreview, type ValueMap,
} from "@/lib/lead-import";

type Report = Schemas["ImportReport"];
type Problem = Schemas["ImportRowResult"];

const OUTCOMES: Record<string, string> = {
  REJECTED: "Not imported",
  DUPLICATE_IN_FILE: "Repeated in the file",
  ALREADY_IN_CRM: "Already in the CRM",
};

/**
 * Lead import (IMPORT1): upload a spreadsheet, match its columns to lead fields (and its values to the business's
 * options), check, then import. Nothing is saved until "Import"; imported leads send no alerts or messages.
 */
export default function ImportPage() {
  const me = useMe();
  const slug = me.tenant!.slug!;
  const { data: form } = useSWR(["enquiry-form", slug], () =>
    unwrap(api.GET("/api/v1/public/tenants/{slug}/enquiry-form", { params: { path: { slug } } })));

  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [columns, setColumns] = useState<ImportField[]>([]);
  const [values, setValues] = useState<ValueMap>({});
  const [label, setLabel] = useState("");
  const [check, setCheck] = useState<Report | null>(null);
  const [done, setDone] = useState<Report | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const options: Partial<Record<ImportField, string[]>> = {
    SERVICE_INTEREST: form?.serviceInterests ?? [],
    PREFERRED_COUNTRY: form?.countries ?? [],
  };

  const upload = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    const body = new FormData();
    body.append("file", file);
    setBusy(true);
    const { data, error } = await api.POST("/api/v1/lead-imports/read", {
      body: { file: "" },
      bodySerializer: () => body,
    }).catch((e: unknown) => ({ data: undefined, error: e }));
    setBusy(false);
    if (error || !data) { setError(errorMessage(error)); return; }
    setError("");
    setPreview(data);
    const cols = data.columns ?? [];
    setColumns(cols);
    setValues(guessValues(data, cols, options));
    setCheck(null);
    setDone(null);
  };

  const choose = (index: number, field: ImportField) => {
    const next = chooseField(columns, index, field);
    setColumns(next);
    setValues((v) => ({ ...guessValues(preview!, next, options), ...v }));
  };

  const run = async (dryRun: boolean) => {
    if (!preview) return;
    setBusy(true);
    const { data, error } = await api.POST("/api/v1/lead-imports", {
      params: { query: { dryRun } },
      body: {
        fileName: preview.fileName, sourceLabel: label || undefined,
        headers: preview.headers, columns, rows: preview.rows, values,
      },
    });
    setBusy(false);
    if (error || !data) { setError(errorMessage(error)); return; }
    setError("");
    if (dryRun) setCheck(data); else setDone(data);
  };

  const restart = () => { setPreview(null); setCheck(null); setDone(null); setError(""); };
  const missing = !columns.includes("FULL_NAME") || !columns.includes("PHONE");

  return (
    <>
      <PageHeader
        icon={<FileSpreadsheet />}
        title="Import leads"
        description="Load enquiries you already have from an Excel (.xlsx) or CSV file. Imported leads send no alerts or messages."
      />

      {!preview && (
        <Card>
          <CardContent className="grid gap-4">
            <p className="text-sm text-muted-foreground">
              The first sheet is read. Its first filled row should hold the column names (Name, Mobile No., …); every row
              under it is one enquiry. Up to 5,000 rows per file.
            </p>
            <label className="flex w-fit cursor-pointer items-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground">
              <Upload className="size-4" /> {busy ? "Reading…" : "Choose a file"}
              <input type="file" accept=".xlsx,.csv" className="sr-only" aria-label="Spreadsheet file" onChange={upload} disabled={busy} />
            </label>
            <ErrorText>{error}</ErrorText>
          </CardContent>
        </Card>
      )}

      {preview && !check && !done && (
        <Card>
          <CardContent className="grid gap-5">
            <p className="text-sm">
              <span className="font-medium">{preview.fileName}</span>: {preview.rows?.length ?? 0} rows. Check what each column holds.
            </p>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Column in your file</TableHead>
                  <TableHead>Example</TableHead>
                  <TableHead>Goes to</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(preview.headers ?? []).map((header, i) => (
                  <TableRow key={i}>
                    <TableCell className="font-medium">{header}</TableCell>
                    <TableCell className="max-w-64 truncate text-muted-foreground">{examples(preview, i).slice(0, 2).join(" · ")}</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <NativeSelect aria-label={`Field for ${header}`} value={columns[i]} onChange={(e) => choose(i, e.target.value as ImportField)}>
                          {FIELDS.map((f) => <NativeSelectOption key={f.value} value={f.value}>{f.label}</NativeSelectOption>)}
                        </NativeSelect>
                        <Tooltip>
                          <TooltipTrigger
                            aria-label={`About ${header}`}
                            className="rounded-full text-muted-foreground hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
                          >
                            <Info className="size-4" />
                          </TooltipTrigger>
                          <TooltipContent className="max-w-72">{columnHint(header, columns[i])}</TooltipContent>
                        </Tooltip>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {(["SERVICE_INTEREST", "PREFERRED_COUNTRY"] as ImportField[]).map((field) => {
              const index = columns.indexOf(field);
              const choices = options[field] ?? [];
              if (index < 0 || choices.length === 0) return null;
              return (
                <div key={field} className="grid gap-2">
                  <p className="text-sm font-medium">
                    {FIELDS.find((f) => f.value === field)!.label}: match the values in “{preview.headers![index]}” to your options
                  </p>
                  <div className="grid gap-2 sm:grid-cols-2">
                    {examples(preview, index).slice(0, 30).map((raw) => (
                      <Field key={raw} label={raw}>
                        <NativeSelect
                          className="w-full"
                          value={values[field]?.[raw] ?? ""}
                          onChange={(e) => setValues((v) => ({ ...v, [field]: { ...v[field], [raw]: e.target.value } }))}
                        >
                          <NativeSelectOption value="">Keep as written</NativeSelectOption>
                          {choices.map((c) => <NativeSelectOption key={c} value={c}>{c}</NativeSelectOption>)}
                        </NativeSelect>
                      </Field>
                    ))}
                  </div>
                </div>
              );
            })}

            <Field label="Where these leads came from" hint="shown as the lead's source, e.g. Portal">
              <Input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="Import" maxLength={100} className="max-w-sm" />
            </Field>

            {missing && <p className="text-sm text-destructive">Choose the column with the name and the one with the mobile number.</p>}
            <ErrorText>{error}</ErrorText>
            <div className="flex gap-2">
              <Button variant="outline" onClick={restart}><ArrowLeft /> Another file</Button>
              <Button onClick={() => run(true)} disabled={busy || missing}>{busy ? "Checking…" : "Check"}</Button>
            </div>
          </CardContent>
        </Card>
      )}

      {check && !done && (
        <Card>
          <CardContent className="grid gap-5">
            <Counts report={check} />
            <Problems problems={check.problems ?? []} />
            <p className="text-sm text-muted-foreground">Nothing has been saved yet.</p>
            <ErrorText>{error}</ErrorText>
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => setCheck(null)}><ArrowLeft /> Change the matching</Button>
              <Button onClick={() => run(false)} disabled={busy || !check.added}>
                {busy ? "Importing…" : `Import ${check.added} lead${check.added === 1 ? "" : "s"}`}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {done && (
        <Card>
          <CardContent className="grid gap-5">
            <Counts report={done} />
            <Problems problems={done.problems ?? []} />
            <div className="flex flex-wrap gap-2">
              <Link href="/leads" className={buttonVariants()}>Open leads</Link>
              {!!done.rejected && (
                <Button variant="outline" onClick={() => downloadRejected(preview!, done.problems ?? [])}>
                  <Download /> Rows not imported (.csv)
                </Button>
              )}
              <Button variant="outline" onClick={restart}>Import another file</Button>
            </div>
          </CardContent>
        </Card>
      )}
    </>
  );
}

function Counts({ report }: { report: Report }) {
  const items = [
    [report.dryRun ? "Will be added" : "Added", report.added],
    ["Already in the CRM", report.alreadyInCrm],
    ["Repeated in the file", report.duplicates],
    ["Not imported", report.rejected],
  ] as const;
  return (
    <div className="grid gap-3 sm:grid-cols-4">
      {items.map(([title, n]) => (
        <div key={title} className="rounded-lg border p-3">
          <p className="text-xs text-muted-foreground">{title}</p>
          <p className="text-2xl font-semibold">{n ?? 0}</p>
        </div>
      ))}
    </div>
  );
}

function Problems({ problems }: { problems: Problem[] }) {
  if (problems.length === 0) return null;
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Row</TableHead>
          <TableHead>Name</TableHead>
          <TableHead>Mobile</TableHead>
          <TableHead>What happens</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {problems.map((p) => (
          <TableRow key={p.row}>
            <TableCell>{p.row}</TableCell>
            <TableCell>{p.name ?? "—"}</TableCell>
            <TableCell>{p.phone ?? "—"}</TableCell>
            <TableCell>
              {OUTCOMES[p.outcome ?? ""]}
              {p.detail && <span className="text-muted-foreground">: {p.outcome === "ALREADY_IN_CRM" ? `lead ${p.detail}` : p.detail}</span>}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function downloadRejected(preview: ImportPreview, problems: Problem[]) {
  const url = URL.createObjectURL(new Blob([rejectedRowsCsv(preview, problems)], { type: "text/csv" }));
  const a = document.createElement("a");
  a.href = url;
  a.download = (preview.fileName ?? "import").replace(/\.\w+$/, "") + " - not imported.csv";
  a.click();
  URL.revokeObjectURL(url);
}
