"use client";

import { useParams } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { CheckCircle2, Clock, MessageCircle, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { ChoiceField } from "@/components/ChoiceField";
import { ErrorText, Field } from "@/components/common";
import { Turnstile } from "@/components/Turnstile";
import { api, errorMessage, type Schemas } from "@/lib/api";

/**
 * Public enquiry form for any tenant: /enquiry/<tenant-slug>. Options come from the tenant's settings; when they include
 * "Other", picking it asks what is meant and the typed answer is sent instead.
 */
export default function EnquiryPage() {
  const { tenant } = useParams<{ tenant: string }>();
  const [form, setForm] = useState<Schemas["FormResponse"] | null>(null);
  const [loadError, setLoadError] = useState("");
  const [result, setResult] = useState<Schemas["EnquiryResponse"] | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    api.GET("/api/v1/public/tenants/{slug}/enquiry-form", { params: { path: { slug: tenant } } }).then(({ data, error }) =>
      data ? setForm(data) : setLoadError(errorMessage(error)));
  }, [tenant]);

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError("");
    setBusy(true);
    const f = Object.fromEntries(new FormData(e.currentTarget)) as Record<string, string>;
    const utm = new URLSearchParams(window.location.search);
    const { data, error } = await api.POST("/api/v1/public/tenants/{slug}/enquiries", {
      params: { path: { slug: tenant } },
      body: {
        fullName: f.fullName,
        phone: f.phone,
        email: f.email || undefined,
        serviceInterest: f.serviceInterest?.trim() || undefined,
        preferredCountry: f.preferredCountry?.trim() || undefined,
        branchId: f.branchId || undefined,
        message: f.message || undefined,
        website: f.website || undefined,
        captchaToken: f["cf-turnstile-response"] || undefined,
        utmSource: utm.get("utm_source") ?? undefined,
        utmMedium: utm.get("utm_medium") ?? undefined,
        utmCampaign: utm.get("utm_campaign") ?? undefined,
      },
    });
    setBusy(false);
    if (data) setResult(data);
    else {
      setError(errorMessage(error));
      setAttempt((n) => n + 1); // captcha tokens are single-use
    }
  };

  if (loadError) return <main className="p-10 text-center"><ErrorText>{loadError}</ErrorText></main>;
  if (!form) return <main className="p-10 text-center text-sm text-muted-foreground">Loading…</main>;

  return (
    <main className="min-h-screen bg-gradient-to-br from-sidebar via-sidebar to-teal-900">
      <div className="mx-auto grid max-w-6xl gap-10 px-4 py-10 lg:grid-cols-[1fr_520px] lg:py-20">
        <section className="text-sidebar-foreground lg:pt-8">
          <p className="text-sm font-bold tracking-widest text-sky-300 uppercase">{form.tenantName}</p>
          <h1 className="mt-4 text-4xl leading-tight font-semibold text-white lg:text-5xl">Tell us how we can help.</h1>
          <p className="mt-4 max-w-md text-sidebar-foreground/70">
            Share a few details and our team will get in touch. It takes under a minute.
          </p>
          <ul className="mt-8 space-y-4 text-sm">
            <li className="flex items-center gap-3"><Clock className="size-5 text-sky-300" /> We confirm your enquiry straight away</li>
            <li className="flex items-center gap-3"><MessageCircle className="size-5 text-sky-300" /> Someone from our team calls you back</li>
            <li className="flex items-center gap-3"><ShieldCheck className="size-5 text-sky-300" /> Your details are only used to answer your enquiry</li>
          </ul>
        </section>

        <Card className="shadow-2xl">
          <CardContent className="p-2 sm:p-4">
            {result ? (
              <div className="flex flex-col items-center gap-3 py-12 text-center">
                <CheckCircle2 className="size-14 text-emerald-500" />
                <h2 className="text-xl font-semibold">{result.message}</h2>
              </div>
            ) : (
              <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
                <div className="sm:col-span-2">
                  <h2 className="text-xl font-semibold">Send an enquiry</h2>
                  <p className="text-sm text-muted-foreground">Fields marked * are required.</p>
                </div>
                <Field label="Full name *" className="sm:col-span-2"><Input name="fullName" required maxLength={200} className="h-11" autoComplete="name" /></Field>
                <Field label="Mobile number *"><Input name="phone" type="tel" required placeholder="98765 43210" className="h-11" /></Field>
                <Field label="Email"><Input name="email" type="email" className="h-11" /></Field>
                <ChoiceField name="serviceInterest" label="Interested in" otherLabel="Which service? *" values={form.serviceInterests}
                             example="Spouse visa" maxLength={200} tall />
                <ChoiceField name="preferredCountry" label="Preferred country" otherLabel="Which country? *" values={form.countries}
                             example="Australia" maxLength={100} tall />
                {form.branches && form.branches.length > 0 && (
                  <Field label="Nearest branch" className="sm:col-span-2">
                    <NativeSelect name="branchId" defaultValue="" className="w-full [&_select]:h-11">
                      <NativeSelectOption value="">Select…</NativeSelectOption>
                      {form.branches.map((b) => <NativeSelectOption key={b.id} value={b.id}>{b.name}</NativeSelectOption>)}
                    </NativeSelect>
                  </Field>
                )}
                <Field label="Message" className="sm:col-span-2"><Textarea name="message" rows={3} maxLength={2000} /></Field>
                {/* Honeypot: hidden from people, bots fill it in. */}
                <input name="website" tabIndex={-1} autoComplete="off" className="absolute -left-[10000px]" aria-hidden="true" />
                <div className="space-y-2 sm:col-span-2">
                  <Turnstile key={attempt} />
                  <ErrorText>{error}</ErrorText>
                  <Button type="submit" disabled={busy} className="h-11 w-full text-base">{busy ? "Sending…" : "Submit enquiry"}</Button>
                </div>
              </form>
            )}
          </CardContent>
        </Card>
      </div>
    </main>
  );
}
