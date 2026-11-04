"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import useSWR from "swr";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { ErrorText, Field, humanize } from "@/components/common";
import { api, errorMessage, unwrap, type Schemas } from "@/lib/api";

type Source = NonNullable<Schemas["LeadResponse"]["sourceType"]>;
export const STAFF_SOURCES: Source[] = ["WALK_IN", "PHONE", "REFERRAL", "OTHER"];

/**
 * Staff entry for walk-in / phone / referral enquiries. Uses the tenant's public form options, and goes through the
 * same intake as every other source (so a repeat enquiry lands on the existing open lead).
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

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const el = e.currentTarget;
    const f = Object.fromEntries(new FormData(el)) as Record<string, string>;
    setBusy(true);
    const { data, error } = await api.POST("/api/v1/leads", {
      body: {
        fullName: f.fullName, phone: f.phone, email: f.email || undefined,
        serviceInterest: f.serviceInterest || undefined, preferredCountry: f.preferredCountry || undefined,
        branchId: f.branchId || undefined, message: f.message || undefined, sourceType: f.sourceType as Source,
      },
    });
    setBusy(false);
    if (error || !data) setError(errorMessage(error));
    else {
      setError("");
      el.reset();
      onSaved(data);
    }
  };

  const choice = (values: string[] | undefined, name: string, label: string) => (
    <Field label={label}>
      {values && values.length > 0 ? (
        <NativeSelect name={name} defaultValue="" className="w-full">
          <NativeSelectOption value="">Select…</NativeSelectOption>
          {values.map((v) => <NativeSelectOption key={v}>{v}</NativeSelectOption>)}
        </NativeSelect>
      ) : <Input name={name} />}
    </Field>
  );

  return (
    <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
      <Field label="Full name *"><Input name="fullName" required maxLength={200} /></Field>
      <Field label="Mobile number *"><Input name="phone" type="tel" required placeholder="98765 43210" /></Field>
      <Field label="Email"><Input name="email" type="email" /></Field>
      <Field label="Source *">
        <NativeSelect name="sourceType" defaultValue="WALK_IN" className="w-full">
          {STAFF_SOURCES.map((s) => <NativeSelectOption key={s} value={s}>{humanize(s)}</NativeSelectOption>)}
        </NativeSelect>
      </Field>
      {choice(form?.serviceInterests, "serviceInterest", "Interested in")}
      {choice(form?.countries, "preferredCountry", "Preferred country")}
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
