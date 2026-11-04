"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ErrorText } from "@/components/common";
import { SigninCodeField } from "@/components/SigninCodeField";
import { api, errorMessage } from "@/lib/api";
import { signinCodeProblem } from "@/lib/signin-code";

/**
 * Changing your own code ends your own session, so there is no "saved" screen to come back to: the code is noted down
 * first and saving signs you out.
 */
export function MySigninCodeForm({ userId, phone, onSaved }: { userId: string; phone?: string; onSaved: () => void }) {
  const [code, setCode] = useState("");
  const [error, setError] = useState("");

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const problem = signinCodeProblem(code);
    if (problem) return setError(problem);
    const { error } = await api.PUT("/api/v1/users/{id}/signin-code", { params: { path: { id: userId } }, body: { code } });
    if (error) return setError(errorMessage(error));
    onSaved();
  };

  return (
    <>
      <DialogHeader>
        <DialogTitle>Change my sign-in code</DialogTitle>
        <DialogDescription>
          Note the new code down before saving: it cannot be shown again. You will be signed out and sign in with {phone} and
          the new code.
        </DialogDescription>
      </DialogHeader>
      <form onSubmit={submit} className="space-y-4">
        <SigninCodeField code={code} onChange={(c) => { setCode(c); setError(""); }} />
        <div className="flex items-center justify-end gap-3">
          <ErrorText>{error}</ErrorText>
          <Button type="submit">Save and sign out</Button>
        </div>
      </form>
    </>
  );
}
