import type { Schemas } from "./api";

export type ImportPreview = Schemas["ImportPreview"];
export type ImportField = NonNullable<ImportPreview["columns"]>[number];
export type ValueMap = Partial<Record<ImportField, Record<string, string>>>;

/** What each choice means, in the Admin's words, and what it does (the info icon). */
export const FIELDS: { value: ImportField; label: string; hint: string }[] = [
  { value: "FULL_NAME", label: "Name", hint: "The person's name. Required: a row without one is not imported." },
  { value: "PHONE", label: "Mobile number",
    hint: "Required. Numbers without a country code are read as your country's. Used to spot people already in the CRM, so nobody is added twice." },
  { value: "EMAIL", label: "Email", hint: "Optional. With the mobile number, it tells one person from another." },
  { value: "SERVICE_INTEREST", label: "Interested in",
    hint: "What they enquired about (IELTS, Student Visa…). You can match the values to your own options below." },
  { value: "PREFERRED_COUNTRY", label: "Country", hint: "The country they plan to go to." },
  { value: "MESSAGE", label: "Message / remarks", hint: "Free text, such as their question or your notes. Shown on the lead." },
  { value: "EXTERNAL_REF", label: "Their reference no.",
    hint: "An ID this enquiry has in another system (e.g. a portal's enquiry ID), kept so you can trace it back. Not needed for row numbers: every lead gets its own CRM lead number." },
  { value: "EXTRA", label: "Keep as extra detail",
    hint: "Kept on the lead under this column's name (e.g. Address) and shown with the enquiry." },
  { value: "IGNORE", label: "Don't import", hint: "This column is left out." },
];

/** "Sr.No.", "S. No", "Serial number", "#": a row count from the file, not something about the person. */
const SERIAL = /^(sr|s|sl|serial|row)(no|number|num)?$|^(no|#)$/;

/** The info text for a column: what its chosen field does, or why a row-number column isn't needed. */
export function columnHint(header: string, field: ImportField): string {
  const key = header.toLowerCase().replace(/[^\p{L}\p{N}#]/gu, "");
  if (SERIAL.test(key) && (field === "IGNORE" || field === "EXTERNAL_REF" || field === "EXTRA")) {
    return "Looks like a row number from your file. Not needed: the CRM gives every lead its own lead number, so you can leave this column out.";
  }
  return FIELDS.find((f) => f.value === field)?.hint ?? "";
}

/** Fields several columns may go to; every other field comes from one column only. */
const REPEATABLE: ImportField[] = ["EXTRA", "IGNORE"];

/** Puts a column on a field; a column that held that (single) field becomes an extra detail. */
export function chooseField(columns: ImportField[], index: number, field: ImportField): ImportField[] {
  const next = columns.map((c, i) => (i !== index && c === field && !REPEATABLE.includes(field) ? "EXTRA" : c));
  next[index] = field;
  return next;
}

/** Distinct non-blank values of a column, in file order. */
export function examples(preview: ImportPreview, index: number): string[] {
  const seen = new Set<string>();
  for (const row of preview.rows ?? []) {
    const v = row.cells?.[index]?.trim();
    if (v) seen.add(v);
  }
  return [...seen];
}

/**
 * Pre-selects the business's option for a value when only one option fits: the same text, or the only option that
 * contains it ("IELTS" → "IELTS / PTE Coaching"). Anything unclear stays "Keep as written".
 */
export function guessValues(preview: ImportPreview, columns: ImportField[],
                            options: Partial<Record<ImportField, string[]>>): ValueMap {
  const out: ValueMap = {};
  for (const [field, choices] of Object.entries(options) as [ImportField, string[]][]) {
    const index = columns.indexOf(field);
    if (index < 0 || choices.length === 0) continue;
    const map: Record<string, string> = {};
    for (const raw of examples(preview, index)) {
      const v = raw.toLowerCase();
      const exact = choices.find((c) => c.toLowerCase() === v);
      const containing = choices.filter((c) => c.toLowerCase().includes(v));
      const pick = exact ?? (containing.length === 1 ? containing[0] : undefined);
      if (pick) map[raw] = pick;
    }
    out[field] = map;
  }
  return out;
}

/** CSV of the rows that were not imported, with the reason, so they can be fixed and uploaded again. */
export function rejectedRowsCsv(preview: ImportPreview, problems: Schemas["ImportRowResult"][]): string {
  const reasons = new Map(problems.filter((p) => p.outcome === "REJECTED").map((p) => [p.row, p.detail ?? ""]));
  const quote = (s: string) => `"${s.replaceAll('"', '""')}"`;
  const lines = [[...(preview.headers ?? []), "Why not imported"].map(quote).join(",")];
  for (const row of preview.rows ?? []) {
    if (reasons.has(row.row)) lines.push([...(row.cells ?? []), reasons.get(row.row)!].map(quote).join(","));
  }
  return "﻿" + lines.join("\r\n");
}
