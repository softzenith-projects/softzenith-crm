/** Staff sign-in code rules, the same as the backend's SigninCodes: 6–8 digits, no repeated digit, no straight run. */
export function signinCodeProblem(code: string): string | null {
  if (!/^\d{6,8}$/.test(code)) return "The sign-in code must be 6 to 8 digits";
  const digits = [...code].map(Number);
  const run = (step: number) => digits.every((d, i) => i === 0 || d - digits[i - 1] === step);
  if (new Set(digits).size === 1 || run(1) || run(-1)) {
    return "The sign-in code is too easy to guess; avoid repeated digits and runs like 123456";
  }
  return null;
}

/** A random 6-digit code that passes the rules (cryptographically random, never Math.random). */
export function generateSigninCode(): string {
  for (;;) {
    const values = crypto.getRandomValues(new Uint32Array(6));
    const code = Array.from(values, (v) => String(v % 10)).join("");
    if (!signinCodeProblem(code)) return code;
  }
}
