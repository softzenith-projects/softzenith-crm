"use client";

import { KeyRound } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/common";
import { generateSigninCode } from "@/lib/signin-code";

/** Digits only, with a Generate button. The code stays visible so it can be noted down before saving. */
export function SigninCodeField({ code, onChange }: { code: string; onChange: (code: string) => void }) {
  return (
    <Field label="Sign-in code (6 to 8 digits)">
      <div className="flex gap-2">
        <Input inputMode="numeric" autoComplete="off" required maxLength={8} value={code} autoFocus
               onChange={(e) => onChange(e.target.value.replace(/\D/g, ""))} className="font-mono tracking-widest" />
        <Button type="button" variant="outline" onClick={() => onChange(generateSigninCode())}>
          <KeyRound /> Generate
        </Button>
      </div>
    </Field>
  );
}
