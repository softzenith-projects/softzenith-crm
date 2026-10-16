import { describe, expect, it } from "vitest";
import { generateSigninCode, signinCodeProblem } from "./signin-code";

describe("sign-in code rules (same as the backend)", () => {
  it("accepts 6 to 8 digits that are not obvious", () => {
    for (const ok of ["482917", "48291735", "112233"]) expect(signinCodeProblem(ok)).toBeNull();
  });

  it("refuses the wrong length or anything but digits", () => {
    for (const bad of ["", "48291", "482917351", "48a917", " 482917"]) expect(signinCodeProblem(bad)).toMatch(/6 to 8 digits/);
  });

  it("refuses repeated digits and straight runs", () => {
    for (const easy of ["000000", "123456", "012345", "654321", "98765432"]) expect(signinCodeProblem(easy)).toMatch(/too easy/);
  });

  it("generates valid, different codes", () => {
    const codes = new Set(Array.from({ length: 50 }, generateSigninCode));
    for (const code of codes) expect(signinCodeProblem(code)).toBeNull();
    expect(codes.size).toBeGreaterThan(40);
  });
});
