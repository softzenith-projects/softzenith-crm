"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { ArrowRight, Building2, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ErrorText, Field } from "@/components/common";
import { api, errorMessage, type Schemas } from "@/lib/api";
import { authMode, codeLogin, devLogin, sendOtp, verifyOtp } from "@/lib/auth";
import { session } from "@/lib/session";

export default function LoginPage() {
  const mode = authMode();
  const router = useRouter();
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [otpSent, setOtpSent] = useState(false);
  const [memberships, setMemberships] = useState<Schemas["MembershipResponse"][]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const enter = async (token: string) => {
    session.clear();
    session.setToken(token);
    const { data, error } = await api.GET("/api/v1/me/memberships");
    if (error || !data) throw new Error(errorMessage(error));
    if (data.length === 0) throw new Error("This phone number is not registered as staff anywhere.");
    if (data.length === 1) return choose(data[0].tenant!.id!);
    setMemberships(data);
  };

  const choose = (tenantId: string) => {
    session.setTenant(tenantId);
    router.replace("/");
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      if (mode === "code" || (mode === "dev" && code.trim())) await enter(await codeLogin(phone, code));
      else if (mode === "dev") await enter(await devLogin(phone));
      else if (!otpSent) {
        await sendOtp(phone);
        setOtpSent(true);
      } else await enter(await verifyOtp(phone, code));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="grid min-h-screen lg:grid-cols-2">
      <section className="hidden flex-col justify-between bg-sidebar p-12 text-sidebar-foreground lg:flex">
        <p className="text-lg font-bold tracking-wide">SoftZenith CRM</p>
        <div className="max-w-md space-y-4">
          <h1 className="text-4xl font-semibold leading-tight text-white">Every enquiry answered. Every student followed through.</h1>
          <p className="text-sidebar-foreground/70">
            Leads, counselling, tasks and documents for your whole team, across every branch.
          </p>
        </div>
        <p className="flex items-center gap-2 text-sm text-sidebar-foreground/60">
          <ShieldCheck className="size-4" /> Sign-in with your registered mobile number
        </p>
      </section>

      <section className="flex items-center justify-center p-6">
        <div className="w-full max-w-sm space-y-6">
          <div>
            <h2 className="text-2xl font-semibold tracking-tight">Staff sign in</h2>
            <p className="text-sm text-muted-foreground">
              {mode === "code"
                ? "Use your mobile number and the sign-in code your administrator gave you."
                : mode === "dev"
                  ? "Development sign-in: mobile number only, or add a sign-in code to try the real sign-in."
                  : "We will send a one-time code to your mobile."}
            </p>
          </div>

          {memberships.length > 0 ? (
            <div className="space-y-2">
              <p className="text-sm">You work for several organisations. Choose one:</p>
              {memberships.map((m) => (
                <button
                  key={m.tenant!.id}
                  onClick={() => choose(m.tenant!.id!)}
                  className="flex w-full items-center gap-3 rounded-xl border bg-card p-4 text-left transition hover:border-primary hover:shadow-sm"
                >
                  <Building2 className="size-5 text-primary" />
                  <span className="flex-1">
                    <span className="block font-medium">{m.tenant!.name}</span>
                    <span className="block text-xs text-muted-foreground">{m.role!.name}</span>
                  </span>
                  <ArrowRight className="size-4 text-muted-foreground" />
                </button>
              ))}
            </div>
          ) : (
            <form onSubmit={submit} className="space-y-4">
              <Field label="Mobile number">
                <Input type="tel" required value={phone} onChange={(e) => setPhone(e.target.value)}
                       placeholder="98765 43210" disabled={otpSent} className="h-11" autoFocus />
              </Field>
              {mode !== "supabase" && (
                <Field label={mode === "dev" ? "Sign-in code (optional here)" : "Sign-in code"}>
                  <Input type="password" inputMode="numeric" autoComplete="current-password" required={mode === "code"}
                         value={code} onChange={(e) => setCode(e.target.value)} className="h-11" />
                </Field>
              )}
              {otpSent && (
                <Field label="One-time code">
                  <Input inputMode="numeric" required value={code} onChange={(e) => setCode(e.target.value)} className="h-11" autoFocus />
                </Field>
              )}
              <ErrorText>{error}</ErrorText>
              <Button type="submit" disabled={busy} className="h-11 w-full">
                {busy ? "Please wait…" : mode !== "supabase" ? "Sign in" : otpSent ? "Verify code" : "Send code"}
              </Button>
            </form>
          )}

          {mode === "dev" && (
            <div className="rounded-xl border border-dashed p-4 text-xs text-muted-foreground">
              <p className="mb-2 font-medium text-foreground">Demo users <span className="font-normal">(sign-in code 482917)</span></p>
              <ul className="grid grid-cols-2 gap-1">
                {[["9000000001", "Admin"], ["9000000002", "Branch Manager"], ["9000000003", "Counsellor"], ["9000000004", "Receptionist"]].map(
                  ([p, r]) => (
                    <li key={p}>
                      <button type="button" className="hover:text-primary hover:underline" onClick={() => setPhone(p)}>
                        {p} · {r}
                      </button>
                    </li>
                  ),
                )}
              </ul>
            </div>
          )}
        </div>
      </section>
    </main>
  );
}
