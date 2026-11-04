import { api, errorMessage } from "./api";
import { supabase } from "./supabase";

/**
 * How staff sign in. "code": mobile + the sign-in code their Admin set (production default, no SMS). "dev": the
 * backend's development login (phone only; a code may still be typed to try the code sign-in). "supabase": phone OTP
 * when a Supabase project is configured.
 */
export function authMode(): "supabase" | "dev" | "code" {
  if (supabase) return "supabase";
  return process.env.NODE_ENV === "production" ? "code" : "dev";
}

/** Supabase wants E.164; assume India when a bare 10-digit number is typed. */
function toE164(phone: string) {
  const digits = phone.replace(/[^\d+]/g, "");
  return digits.startsWith("+") ? digits : digits.length === 10 ? `+91${digits}` : `+${digits}`;
}

export async function devLogin(phone: string): Promise<string> {
  const { data, error } = await api.POST("/api/v1/dev/login", { body: { phone } });
  if (error || !data) throw new Error(errorMessage(error));
  return data.accessToken!;
}

export async function codeLogin(phone: string, code: string): Promise<string> {
  const { data, error } = await api.POST("/api/v1/auth/phone-code", { body: { phone, code: code.trim() } });
  if (error || !data) throw new Error(errorMessage(error));
  return data.accessToken!;
}

export async function sendOtp(phone: string) {
  const { error } = await supabase!.auth.signInWithOtp({ phone: toE164(phone) });
  if (error) throw error;
}

export async function verifyOtp(phone: string, code: string): Promise<string> {
  const { data, error } = await supabase!.auth.verifyOtp({ phone: toE164(phone), token: code, type: "sms" });
  if (error || !data.session) throw error ?? new Error("Verification failed");
  return data.session.access_token;
}

export async function signOut() {
  await supabase?.auth.signOut();
}
