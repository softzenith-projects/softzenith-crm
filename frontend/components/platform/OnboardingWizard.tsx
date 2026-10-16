"use client";

import Link from "next/link";
import { useEffect, useState, type ChangeEvent, type ReactNode } from "react";
import { AlertTriangle, CheckCircle2, Download, KeyRound, Plus, Rocket, Trash2, Upload } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import { ErrorText, Field } from "@/components/common";
import { errorMessage, type Schemas } from "@/lib/api";
import {
  FEATURES, REVIEW_STEP, ROLES, STEPS, emptyBlueprint, forSending, lines, parseStaffSheet, slugify, stepOfField,
  withoutSigninCodes, type Blueprint, type BlueprintStaff,
} from "@/lib/blueprint";
import { generateSigninCode } from "@/lib/signin-code";
import { platformApi } from "@/lib/platform";
import { cn } from "@/lib/utils";

type Plan = Schemas["OnboardingPlan"];
type Onboarded = Schemas["Onboarded"];

/** Unsaved work survives a reload or an accidental close (per browser); sign-in codes are never kept in it. */
const DRAFT = "crm.platform.onboarding-draft";

function loadDraft(): Blueprint {
  try {
    const saved = localStorage.getItem(DRAFT);
    return saved ? { ...emptyBlueprint(), ...JSON.parse(saved) } : emptyBlueprint();
  } catch {
    return emptyBlueprint();
  }
}

/**
 * Onboard a business in five steps; the last one asks the backend for a preview (a full trial run, rolled back) and
 * only then offers "Go live". The whole setup can be saved to and loaded from a file.
 */
export function OnboardingWizard() {
  // Rendered in the browser only (see the page), so the saved draft can be the initial state.
  const [bp, setBp] = useState<Blueprint>(loadDraft);
  const [step, setStep] = useState(0);
  const [plan, setPlan] = useState<Plan | null>(null);
  const [checkedFor, setCheckedFor] = useState("");
  const [checking, setChecking] = useState(false);
  const [done, setDone] = useState<Onboarded | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    try {
      localStorage.setItem(DRAFT, JSON.stringify(withoutSigninCodes(bp)));
    } catch {
      // Storage unavailable (private mode): the form still works, the draft is just not kept.
    }
  }, [bp]);

  const sent = JSON.stringify(forSending(bp));
  const upToDate = plan !== null && checkedFor === sent;

  const check = async () => {
    setChecking(true);
    setError("");
    const body = forSending(bp);
    const { data, error } = await platformApi.POST("/api/platform/tenants/preview", { body });
    setChecking(false);
    if (!data) return setError(errorMessage(error));
    setPlan(data);
    setCheckedFor(JSON.stringify(body));
  };

  /** Opening the review step checks the current setup with the backend. */
  const goTo = (next: number) => {
    setStep(next);
    if (next === REVIEW_STEP && !upToDate && !checking) void check();
  };

  const goLive = async () => {
    if (!window.confirm(`Create ${bp.business?.name} on the platform now? Staff are invited but not messaged.`)) return;
    setError("");
    const { data, error } = await platformApi.POST("/api/platform/tenants", { body: forSending(bp) });
    if (!data) return setError(errorMessage(error));
    setDone(data);
    try {
      localStorage.removeItem(DRAFT);
    } catch {
      // ignore
    }
  };

  const patch = (change: Partial<Blueprint>) => setBp((b) => ({ ...b, ...change }));
  const business = bp.business ?? {};
  const settings = bp.settings ?? {};
  const setBusiness = (c: Partial<NonNullable<Blueprint["business"]>>) => patch({ business: { ...business, ...c } });
  const setSettings = (c: Partial<NonNullable<Blueprint["settings"]>>) => patch({ settings: { ...settings, ...c } });

  const problemsFor = (s: number) => (plan?.problems ?? []).filter((p) => stepOfField(p.field) === s);

  if (done) return <LiveNow result={done} />;

  const saveFile = () => {
    const url = URL.createObjectURL(new Blob([JSON.stringify(withoutSigninCodes(bp), null, 2)], { type: "application/json" }));
    const a = Object.assign(document.createElement("a"), { href: url, download: `${business.slug || "new-business"}.blueprint.json` });
    a.click();
    URL.revokeObjectURL(url);
  };
  const loadFile = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    try {
      setBp({ ...emptyBlueprint(), ...JSON.parse(await file.text()) });
      setPlan(null);
      setStep(0);
    } catch {
      setError(`${file.name} is not a saved onboarding file`);
    }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[240px_1fr]">
      <nav aria-label="Onboarding steps" className="space-y-1">
        {STEPS.map((label, i) => (
          <button key={label} type="button" onClick={() => goTo(i)} aria-current={step === i ? "step" : undefined}
                  className={cn("flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm",
                    step === i ? "bg-primary text-primary-foreground" : "hover:bg-muted")}>
            <span className="flex size-6 shrink-0 items-center justify-center rounded-full border text-xs">{i + 1}</span>
            <span className="flex-1">{label}</span>
            {i < REVIEW_STEP && problemsFor(i).length > 0 && (
              <span className="rounded-full bg-destructive px-1.5 text-xs text-white" aria-label={problemsFor(i).length === 1 ? "1 problem" : `${problemsFor(i).length} problems`}>
                {problemsFor(i).length}
              </span>
            )}
          </button>
        ))}
        <div className="flex gap-2 pt-4">
          <Button type="button" variant="outline" size="sm" onClick={saveFile}><Download /> Save file</Button>
          <label className={buttonVariants({ variant: "outline", size: "sm" })}>
            <Upload /> Load file
            <input type="file" accept="application/json,.json" className="sr-only" onChange={loadFile} aria-label="Load a saved onboarding file" />
          </label>
        </div>
      </nav>

      <Card>
        <CardHeader><CardTitle><h2>{STEPS[step]}</h2></CardTitle></CardHeader>
        <CardContent className="space-y-6">
          {step < REVIEW_STEP && <Problems items={problemsFor(step)} />}

          {step === 0 && (
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Business name *" className="sm:col-span-2">
                <Input value={business.name ?? ""} onChange={(e) => {
                  const name = e.target.value;
                  // Keep suggesting the web address until someone edits it by hand.
                  const auto = !business.slug || business.slug === slugify(business.name ?? "");
                  setBusiness({ name, ...(auto ? { slug: slugify(name) } : {}) });
                }} placeholder="e.g. Western World Visa Services" />
              </Field>
              <Field label="Web address *" hint=" (their enquiry form: /enquiry/…)" className="sm:col-span-2">
                <Input value={business.slug ?? ""} onChange={(e) => setBusiness({ slug: e.target.value.toLowerCase() })} placeholder="westernworld" />
              </Field>
              <Field label="Country code" hint=" (for phone numbers typed without +91)">
                <Input value={business.defaultRegion ?? ""} maxLength={2} onChange={(e) => setBusiness({ defaultRegion: e.target.value.toUpperCase() })} />
              </Field>
              <Field label="Time zone">
                <Input value={business.timezone ?? ""} onChange={(e) => setBusiness({ timezone: e.target.value })} />
              </Field>
            </div>
          )}

          {step === 1 && (
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Lead number prefix" hint=" (e.g. WWV → WWV-000001)">
                <Input value={settings.leadNumberPrefix ?? ""} maxLength={10} onChange={(e) => setSettings({ leadNumberPrefix: e.target.value.toUpperCase() })} placeholder="LD" />
              </Field>
              <Field label="Email sender name" hint=" (defaults to the business name)">
                <Input value={settings.senderName ?? ""} onChange={(e) => setSettings({ senderName: e.target.value })} />
              </Field>
              <Field label="Services offered" hint=" (one per line; shown on the enquiry form)">
                <Textarea rows={6} defaultValue={(settings.serviceInterests ?? []).join("\n")} key={`s-${(settings.serviceInterests ?? []).join("|")}`}
                          onBlur={(e) => setSettings({ serviceInterests: lines(e.target.value) })} />
              </Field>
              <Field label="Countries" hint=" (one per line; empty = free text)">
                <Textarea rows={6} defaultValue={(settings.countries ?? []).join("\n")} key={`c-${(settings.countries ?? []).join("|")}`}
                          onBlur={(e) => setSettings({ countries: lines(e.target.value) })} />
              </Field>
              <fieldset className="space-y-2 sm:col-span-2">
                <legend className="mb-1 text-xs font-medium">Messages to students</legend>
                <Toggle checked={settings.welcomeEmail !== false} onChange={(v) => setSettings({ welcomeEmail: v })}>Welcome email on a new enquiry</Toggle>
                <Toggle checked={settings.welcomeWhatsApp !== false} onChange={(v) => setSettings({ welcomeWhatsApp: v })}>Welcome WhatsApp on a new enquiry</Toggle>
                <Toggle checked={settings.studentUpdates !== false} onChange={(v) => setSettings({ studentUpdates: v })}>Updates when assigned, contacted, closed or reopened</Toggle>
              </fieldset>
              <fieldset className="space-y-2 sm:col-span-2">
                <legend className="mb-1 text-xs font-medium">Features</legend>
                {FEATURES.map((f) => (
                  <Toggle key={f.code} disabled={f.required} checked={f.required || (settings.features ?? []).includes(f.code)}
                          onChange={(v) => setSettings({ features: v ? [...new Set([...(settings.features ?? []), f.code])]
                            : (settings.features ?? []).filter((x) => x !== f.code) })}>
                    <span className="font-medium">{f.label}</span> <span className="text-muted-foreground">– {f.description}</span>
                  </Toggle>
                ))}
              </fieldset>
            </div>
          )}

          {step === 2 && (
            <Rows
              empty="No branches yet. A business with one office can skip this."
              addLabel="Add branch"
              onAdd={() => patch({ branches: [...(bp.branches ?? []), { name: "", city: "" }] })}
            >
              {(bp.branches ?? []).map((b, i) => (
                <div key={i} className="flex items-center gap-2">
                  <Input aria-label={`Branch ${i + 1} name`} placeholder="Branch name" value={b.name ?? ""}
                         onChange={(e) => patch({ branches: bp.branches!.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)) })} />
                  <Input aria-label={`Branch ${i + 1} city`} placeholder="City" value={b.city ?? ""} className="w-48"
                         onChange={(e) => patch({ branches: bp.branches!.map((x, j) => (j === i ? { ...x, city: e.target.value } : x)) })} />
                  <Button type="button" variant="ghost" size="icon" aria-label={`Remove branch ${i + 1}`}
                          onClick={() => patch({ branches: bp.branches!.filter((_, j) => j !== i) })}><Trash2 /></Button>
                </div>
              ))}
            </Rows>
          )}

          {step === 3 && <StaffStep bp={bp} patch={patch} />}

          {step === REVIEW_STEP && (
            <Review plan={plan} upToDate={upToDate} checking={checking} onCheck={check} onGoLive={goLive} onFix={setStep} />
          )}
          <ErrorText>{error}</ErrorText>

          <div className="flex justify-between border-t pt-4">
            <Button type="button" variant="outline" disabled={step === 0} onClick={() => goTo(step - 1)}>Back</Button>
            {step < REVIEW_STEP && <Button type="button" onClick={() => goTo(step + 1)}>Next: {STEPS[step + 1]}</Button>}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function StaffStep({ bp, patch }: { bp: Blueprint; patch: (c: Partial<Blueprint>) => void }) {
  const staff = bp.staff ?? [];
  const branchNames = (bp.branches ?? []).map((b) => b.name?.trim()).filter(Boolean) as string[];
  const [sheet, setSheet] = useState("");
  const set = (i: number, c: Partial<BlueprintStaff>) => patch({ staff: staff.map((s, j) => (j === i ? { ...s, ...c } : s)) });

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Everyone signs in with their mobile number and a sign-in code. They are invited, not messaged: they become active
        on their first sign-in. Add at least one Admin and give them a code (they set everyone else&apos;s after going live);
        hand codes over privately. Codes are not kept in the draft or the saved file. Admins and Receptionists with an
        email get new-enquiry alerts.
      </p>
      <Rows empty="No staff yet." addLabel="Add person"
            onAdd={() => patch({ staff: [...staff, { fullName: "", phone: "", email: "", role: staff.length === 0 ? "Admin" : "Counsellor", branch: "" }] })}>
        {staff.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-muted-foreground">
                <tr><th className="p-1">Name</th><th className="p-1">Mobile</th><th className="p-1">Email</th><th className="p-1">Role</th><th className="p-1">Branch</th><th className="p-1">Designation</th><th className="p-1">Sign-in code</th><th /></tr>
              </thead>
              <tbody>
                {staff.map((s, i) => (
                  <tr key={i}>
                    <td className="p-1"><Input aria-label={`Row ${i + 1} name`} value={s.fullName ?? ""} onChange={(e) => set(i, { fullName: e.target.value })} /></td>
                    <td className="p-1"><Input aria-label={`Row ${i + 1} mobile`} type="tel" value={s.phone ?? ""} onChange={(e) => set(i, { phone: e.target.value })} /></td>
                    <td className="p-1"><Input aria-label={`Row ${i + 1} email`} type="email" value={s.email ?? ""} onChange={(e) => set(i, { email: e.target.value })} /></td>
                    <td className="p-1">
                      <NativeSelect aria-label={`Row ${i + 1} role`} value={s.role ?? ""} onChange={(e) => set(i, { role: e.target.value })}>
                        {!ROLES.includes(s.role as (typeof ROLES)[number]) && <NativeSelectOption value={s.role ?? ""}>{s.role || "Choose…"}</NativeSelectOption>}
                        {ROLES.map((r) => <NativeSelectOption key={r} value={r}>{r}</NativeSelectOption>)}
                      </NativeSelect>
                    </td>
                    <td className="p-1">
                      <NativeSelect aria-label={`Row ${i + 1} branch`} value={s.branch ?? ""} onChange={(e) => set(i, { branch: e.target.value })}>
                        <NativeSelectOption value="">—</NativeSelectOption>
                        {s.branch && !branchNames.includes(s.branch) && <NativeSelectOption value={s.branch}>{s.branch} (not in branches)</NativeSelectOption>}
                        {branchNames.map((b) => <NativeSelectOption key={b} value={b}>{b}</NativeSelectOption>)}
                      </NativeSelect>
                    </td>
                    <td className="p-1"><Input aria-label={`Row ${i + 1} designation`} value={s.designation ?? ""} onChange={(e) => set(i, { designation: e.target.value })} /></td>
                    <td className="p-1">
                      <div className="flex gap-1">
                        <Input aria-label={`Row ${i + 1} sign-in code`} inputMode="numeric" autoComplete="off" maxLength={8}
                               className="w-28 font-mono" value={s.signinCode ?? ""}
                               onChange={(e) => set(i, { signinCode: e.target.value.replace(/\D/g, "") })} />
                        <Button type="button" variant="ghost" size="icon" aria-label={`Generate row ${i + 1} sign-in code`}
                                onClick={() => set(i, { signinCode: generateSigninCode() })}><KeyRound /></Button>
                      </div>
                    </td>
                    <td className="p-1">
                      <Button type="button" variant="ghost" size="icon" aria-label={`Remove row ${i + 1}`}
                              onClick={() => patch({ staff: staff.filter((_, j) => j !== i) })}><Trash2 /></Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Rows>
      <details className="rounded-lg border p-3">
        <summary className="cursor-pointer text-sm font-medium">Paste from a spreadsheet</summary>
        <div className="mt-3 space-y-2">
          <p className="text-xs text-muted-foreground">
            Copy the rows from Excel or Google Sheets (or type CSV) with the columns: Name, Mobile, Email, Role, Branch,
            Designation, Employee code. A header row is ignored.
          </p>
          <Field label="Rows to add">
            <Textarea rows={5} value={sheet} onChange={(e) => setSheet(e.target.value)} />
          </Field>
          <Button type="button" variant="outline" disabled={!sheet.trim()}
                  onClick={() => { patch({ staff: [...staff, ...parseStaffSheet(sheet)] }); setSheet(""); }}>
            <Plus /> Add these rows
          </Button>
        </div>
      </details>
    </div>
  );
}

function Review({ plan, upToDate, checking, onCheck, onGoLive, onFix }: {
  plan: Plan | null; upToDate: boolean; checking: boolean; onCheck: () => void; onGoLive: () => void; onFix: (step: number) => void;
}) {
  if (checking || !plan) return <p className="text-sm text-muted-foreground">Checking the setup…</p>;
  const s = plan.summary;
  return (
    <div className="space-y-6">
      {!upToDate && (
        <p className="flex items-center gap-3 text-sm">
          The setup changed since the last check. <Button type="button" variant="outline" size="sm" onClick={onCheck}>Check again</Button>
        </p>
      )}
      {(plan.problems ?? []).length > 0 && (
        <div className="space-y-2">
          <p className="font-medium">Fix these before going live:</p>
          <ul className="space-y-1 text-sm">
            {plan.problems!.map((p, i) => (
              <li key={i} className="flex items-start gap-2 text-destructive">
                <AlertTriangle className="mt-0.5 size-4 shrink-0" />
                <span className="flex-1">{p.message}</span>
                {stepOfField(p.field) < REVIEW_STEP && (
                  <Button type="button" variant="link" size="sm" className="h-auto p-0" onClick={() => onFix(stepOfField(p.field))}>
                    Go to {STEPS[stepOfField(p.field)]}
                  </Button>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
      {(plan.warnings ?? []).length > 0 && (
        <ul className="space-y-1 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
          {plan.warnings!.map((w) => <li key={w}>{w}</li>)}
        </ul>
      )}
      {s && (
        <dl className="grid gap-4 text-sm sm:grid-cols-2">
          <Item label="Business">{s.name}</Item>
          <Item label="Enquiry form">{s.publicFormPath}</Item>
          <Item label="First lead number">{s.leadNumberExample}</Item>
          <Item label="Region / time zone">{s.defaultRegion} · {s.timezone}</Item>
          <Item label="Roles">{s.roles?.join(", ")}</Item>
          <Item label="Features">{s.features?.join(", ")}</Item>
          <Item label={`Branches (${s.branches?.length ?? 0})`}>{s.branches?.join(", ") || "None"}</Item>
          <Item label={`Staff invited (${s.staff?.length ?? 0})`}>
            <ul>{s.staff?.map((p) => (
              <li key={p.phone}>
                {p.fullName} · {p.role}{p.branch ? ` · ${p.branch}` : ""} · {p.phone} · {p.hasSigninCode ? "sign-in code set" : "no sign-in code"}
              </li>
            ))}</ul>
          </Item>
        </dl>
      )}
      <Button type="button" size="lg" disabled={!plan.ready || !upToDate} onClick={onGoLive}><Rocket /> Go live</Button>
    </div>
  );
}

function LiveNow({ result }: { result: Onboarded }) {
  const form = `/enquiry/${result.slug}`;
  return (
    <Card>
      <CardContent className="space-y-6 py-10">
        <div className="flex flex-col items-center gap-3 text-center">
          <CheckCircle2 className="size-14 text-emerald-500" />
          <h2 className="text-2xl font-semibold">{result.name} is live</h2>
          <p className="text-sm text-muted-foreground">{result.branches} branches and {result.staff} staff invited.</p>
        </div>
        <ol className="mx-auto max-w-xl list-decimal space-y-2 pl-5 text-sm">
          <li>Their enquiry form: <a href={form} target="_blank" rel="noreferrer" className="text-primary underline">{form}</a></li>
          <li>Their website: set <code>NEXT_PUBLIC_CRM_TENANT={result.slug}</code> so its forms send enquiries here.</li>
          <li>Staff sign in at <code>/login</code> with their mobile number and sign-in code; the first sign-in activates them.</li>
          <li>Admins add more staff and branches themselves under Staff and Branches.</li>
        </ol>
        <div className="flex justify-center"><Link href="/platform" className={buttonVariants({ variant: "outline" })}>Back to businesses</Link></div>
      </CardContent>
    </Card>
  );
}

function Problems({ items }: { items: Plan["problems"] }) {
  if (!items || items.length === 0) return null;
  return (
    <ul className="space-y-1 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
      {items.map((p, i) => <li key={i}>{p.message}</li>)}
    </ul>
  );
}

function Rows({ children, empty, addLabel, onAdd }: { children: ReactNode; empty: string; addLabel: string; onAdd: () => void }) {
  const hasRows = Array.isArray(children) ? children.length > 0 : Boolean(children);
  return (
    <div className="space-y-3">
      {hasRows ? children : <p className="text-sm text-muted-foreground">{empty}</p>}
      <Button type="button" variant="outline" onClick={onAdd}><Plus /> {addLabel}</Button>
    </div>
  );
}

function Toggle({ checked, onChange, disabled, children }: {
  checked: boolean; onChange: (v: boolean) => void; disabled?: boolean; children: ReactNode;
}) {
  return (
    <label className="flex items-start gap-2 text-sm">
      <input type="checkbox" className="mt-0.5 size-4" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />
      <span>{children}</span>
    </label>
  );
}

function Item({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-semibold tracking-wider text-muted-foreground uppercase">{label}</dt>
      <dd className="mt-1">{children}</dd>
    </div>
  );
}
