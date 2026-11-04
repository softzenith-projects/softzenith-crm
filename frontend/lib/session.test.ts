import { describe, expect, it, vi } from "vitest";
import { json, mockApi, requests } from "@/test/api-mock";
import { authMode, codeLogin, devLogin } from "./auth";
import { session } from "./session";

describe("session", () => {
  it("keeps the token and tenant until cleared", async () => {
    session.setToken("t");
    session.setTenant("x");
    expect(session.token()).toBe("t");
    expect(session.tenant()).toBe("x");
    await expect(session.accessToken()).resolves.toBe("t"); // dev-login mode: the stored token

    session.clear();
    expect(session.token()).toBeNull();
    expect(session.tenant()).toBeNull();
  });
});

describe("dev login", () => {
  it("is the sign-in mode outside production builds when Supabase is not configured", () => {
    expect(authMode()).toBe("dev");
  });

  it("gives way to mobile + sign-in code in a production build", () => {
    vi.stubEnv("NODE_ENV", "production");
    try {
      expect(authMode()).toBe("code");
    } finally {
      vi.unstubAllEnvs();
    }
  });

  it("exchanges a phone number for a token", async () => {
    mockApi(() => json({ accessToken: "dev-token", expiresAt: "2026-10-01T00:00:00Z" }));
    await expect(devLogin("9000000001")).resolves.toBe("dev-token");
    expect(requests[0]).toMatchObject({ method: "POST", path: "/api/v1/dev/login", body: { phone: "9000000001" } });
  });

  it("surfaces the backend's reason when it refuses", async () => {
    mockApi(() => json({ detail: "Invalid phone number: 12" }, 400));
    await expect(devLogin("12")).rejects.toThrow("Invalid phone number: 12");
  });
});

describe("code login", () => {
  it("exchanges a phone and sign-in code for a token", async () => {
    mockApi(() => json({ accessToken: "code-token", expiresAt: "2026-10-01T00:00:00Z" }));
    await expect(codeLogin("9000000001", " 482917 ")).resolves.toBe("code-token");
    expect(requests.at(-1)).toMatchObject({ method: "POST", path: "/api/v1/auth/phone-code", body: { phone: "9000000001", code: "482917" } });
  });

  it("passes on the backend's refusal", async () => {
    mockApi(() => json({ detail: "Wrong mobile number or sign-in code" }, 401));
    await expect(codeLogin("9000000001", "739164")).rejects.toThrow("Wrong mobile number or sign-in code");
  });
});
