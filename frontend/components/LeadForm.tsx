"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import useSWR from "swr";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { ChoiceField, OtherInput } from "@/components/ChoiceField";
import { ErrorText, Field, humanize } from "@/components/common";
import { api, errorMessage, unwrap, type Schemas } from "@/lib/api";

type Source = NonNullable<Schemas["LeadResponse"]["sourceType"]>;
export const STAFF_SOURCES: Source[] = ["WALK_IN", "PHONE", "REFERRAL", "OTHER"];

/**
 * Staff entry for walk-in / phone / referral enquiries. Uses the tenant's public form options, and goes through the
 * same intake as every other source (so a repeat enquiry lands on the existing open lead). "Other" in any list asks
 * what is meant: the typed source goes to sourceDetail, a typed interest or country replaces "Other".
 */
export function LeadForm({ tenantSlug, defaultBranchId, onSaved, footer }: {
  tenantSlug: string;
  defaultBranchId?: string;
  onSaved: (lead: Schemas["LeadResponse"]) => void;
  footer?: ReactNode;
}) {
  const { data: form } = useSWR(["enquiry-form", tenantSlug], () =>
    unwrap(api.GET("/api/v1/public/tenants/{slug}/enquiry-form", { params: { path: { slug: tenantSlug } } })));
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [source, setSource] = useState<Source>("WALK_IN");
  /** Bumped after a save: a fresh form (lists back to "Select…", "Other" boxes gone). */
  const [round, setRound] = useState(0);

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget)) as Record<string, string>;
    setBusy(true);
    const { data, error } = await api.POST("/api/v1/leads", {
      body: {
        fullName: f.fullName, phone: f.phone, email: f.email || undefined,
        serviceInterest: f.serviceInterest?.trim() || undefined, preferredCountry: f.preferredCountry?.trim() || undefined,
        branchId: f.branchId || undefined, message: f.message || undefined, sourceType: source,
        sourceDetail: source === "OTHER" ? f.sourceDetail?.trim() || undefined : undefined,
      },
    });
    setBusy(false);
    if (error || !data) setError(errorMessage(error));
    else {
      setError("");
      setSource("WALK_IN");
      setRound((n) => n + 1);
      onSaved(data);
    }
  };

  return (
    <form key={round} onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
      <Field label="Full name *"><Input name="fullName" required maxLength={200} /></Field>
      <Field label="Mobile number *"><Input name="phone" type="tel" required placeholder="98765 43210" /></Field>
      <Field label="Email"><Input name="email" type="email" /></Field>
      <div className="grid content-start gap-2">
        <Field label="Source *">
          <NativeSelect name="sourceType" value={source} onChange={(e) => setSource(e.target.value as Source)} className="w-full">
            {STAFF_SOURCES.map((s) => <NativeSelectOption key={s} value={s}>{humanize(s)}</NativeSelectOption>)}
          </NativeSelect>
        </Field>
        {source === "OTHER" && <OtherInput name="sourceDetail" label="Which source? *" example="Newspaper ad" maxLength={200} />}
      </div>
      <ChoiceField name="serviceInterest" label="Interested in" otherLabel="Which service? *" values={form?.serviceInterests}
                   example="Spouse visa" maxLength={200} />
      <ChoiceField name="preferredCountry" label="Preferred country" otherLabel="Which country? *" values={form?.countries}
                   example="Australia" maxLength={100} />
      {(form?.branches?.length ?? 0) > 0 && (
        <Field label="Branch" className="sm:col-span-2">
          {/* Re-mounted once the branches arrive: a default value set before its option exists would be lost. */}
          <NativeSelect name="branchId" defaultValue={defaultBranchId ?? ""} className="w-full"
                        key={`${defaultBranchId}-${form?.branches?.length ?? 0}`}>
            <NativeSelectOption value="">Select…</NativeSelectOption>
            {form?.branches?.map((b) => <NativeSelectOption key={b.id} value={b.id}>{b.name}</NativeSelectOption>)}
          </NativeSelect>
        </Field>
      )}
      <Field label="Notes" className="sm:col-span-2"><Textarea name="message" rows={2} maxLength={2000} /></Field>
      <div className="flex items-center justify-end gap-2 sm:col-span-2">
        <ErrorText>{error}</ErrorText>
        {footer}
        <Button type="submit" disabled={busy}>{busy ? "Saving…" : "Save lead"}</Button>
      </div>
    </form>
  );
}
