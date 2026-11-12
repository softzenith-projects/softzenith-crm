"use client";

import { useState, type ChangeEvent } from "react";
import { Field } from "@/components/common";
import { Input } from "@/components/ui/input";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { cn } from "@/lib/utils";

/** A list entry meaning "none of these": picking it asks the person to type what they mean. */
export const isOther = (value: string) => /^others?$/i.test(value.trim());

/** Typed under "Other" but not something staff can act on. Compared lower-case, punctuation removed. */
const NON_ANSWERS = new Set([
  "other", "others", "na", "n a", "none", "nothing", "no", "nil", "any", "anything", "something", "not sure",
  "dont know", "do not know", "idk", "test", "testing", "abc", "xyz", "asdf", "qwerty",
]);
const TEXT = /^[\p{L}\p{M}][\p{L}\p{M}\d .,'’&()/+-]*$/u;

/**
 * Why a typed "Other" answer will not do, or "" when it is fine: at least two letters, mostly letters (a course may
 * carry a number, "German A2"), not a filler such as "na" or "other", not one key held down ("aaaa").
 */
export function otherProblem(raw: string, example: string) {
  const v = raw.trim().replace(/\s+/g, " ");
  if (!v) return `Please type it in, e.g. ${example}.`;
  if (!TEXT.test(v)) return `Letters, numbers and simple punctuation only, e.g. ${example}.`;
  const letters = v.replace(/[^\p{L}]/gu, "").toLowerCase();
  if (letters.length < 2) return `That is too short. Please type the name, e.g. ${example}.`;
  const said = v.toLowerCase().replace(/[^\p{L}\s]/gu, "").replace(/\s+/g, " ").trim();
  if (NON_ANSWERS.has(said) || /^(.)\1+$/u.test(letters) || v.replace(/\D/g, "").length > letters.length) {
    return `"${v}" does not say much. Please type the actual name, e.g. ${example}.`;
  }
  return "";
}

/**
 * The box shown under a list when "Other" is picked. It carries the list's field name, so the form sends what was
 * typed in place of "Other". The browser shows the problem (forms here use native validation).
 */
export function OtherInput({ name, label, example, maxLength, className }: {
  name: string; label: string; example: string; maxLength: number; className?: string;
}) {
  const check = (e: ChangeEvent<HTMLInputElement>) =>
    e.currentTarget.setCustomValidity(e.currentTarget.value.trim() ? otherProblem(e.currentTarget.value, example) : "");
  return (
    <Field label={label}>
      <Input name={name} required maxLength={maxLength} autoComplete="off" autoFocus placeholder={`e.g. ${example}`}
             onChange={check} className={className} />
    </Field>
  );
}

/**
 * One of the business's options (enquiry-form settings), or free text when it has none. When the business lists
 * "Other", picking it shows a box to type the actual value, which is what gets saved.
 */
export function ChoiceField({ name, label, otherLabel, values, example, maxLength, tall = false }: {
  name: string;
  label: string;
  /** Label of the "Other" box, e.g. "Which country? *". */
  otherLabel: string;
  values: string[] | undefined;
  /** Shown as the hint in the "Other" box, e.g. "Australia". */
  example: string;
  maxLength: number;
  /** Taller controls (the public enquiry form). */
  tall?: boolean;
}) {
  const input = tall ? "h-11" : undefined;
  const [value, setValue] = useState("");
  const other = isOther(value);
  if (!values || values.length === 0) {
    return <Field label={label}><Input name={name} maxLength={maxLength} className={input} /></Field>;
  }
  return (
    <div className="grid content-start gap-2">
      <Field label={label}>
        {/* While "Other" is picked the typed box below carries the name, so the select sends nothing. */}
        <NativeSelect name={other ? undefined : name} value={value} onChange={(e) => setValue(e.target.value)}
                      className={cn("w-full", tall && "[&_select]:h-11")}>
          <NativeSelectOption value="">Select…</NativeSelectOption>
          {values.map((v) => <NativeSelectOption key={v} value={v}>{v}</NativeSelectOption>)}
        </NativeSelect>
      </Field>
      {other && <OtherInput name={name} label={otherLabel} example={example} maxLength={maxLength} className={input} />}
    </div>
  );
}
