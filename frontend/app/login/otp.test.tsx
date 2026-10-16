import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { json, requests, routes } from "@/test/api-mock";
import { router } from "@/test/navigation";

vi.mock("next/navigation", () => import("@/test/navigation"));

// Production sign-in: Supabase phone OTP. Supabase itself is faked at its client boundary.
const supabase = vi.hoisted(() => ({
  auth: {
    signInWithOtp: vi.fn(),
    verifyOtp: vi.fn(),
    getSession: vi.fn(),
    signOut: vi.fn(),
  },
}));
vi.mock("@/lib/supabase", () => ({ supabase }));

const { default: LoginPage } = await import("./page");
const { authMode, sendOtp } = await import("@/lib/auth");
const { session } = await import("@/lib/session");

describe("Login (Supabase OTP)", () => {
  it("is the sign-in mode when Supabase is configured", () => {
    expect(authMode()).toBe("supabase");
  });

  it("sends a code, verifies it and signs in with Supabase's token", async () => {
    supabase.auth.signInWithOtp.mockResolvedValue({ error: null });
    supabase.auth.verifyOtp.mockResolvedValue({ data: { session: { access_token: "sb-token" } }, error: null });
    supabase.auth.getSession.mockResolvedValue({ data: { session: { access_token: "sb-token" } } });
    routes({ "GET /api/v1/me/memberships": () => json([{ tenant: { id: "t-1", name: "Western World" }, role: { name: "Admin" } }]) });
    render(<LoginPage />);

    await userEvent.type(screen.getByRole("textbox", { name: /mobile number/i }), "98765 43210");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
    expect(supabase.auth.signInWithOtp).toHaveBeenCalledWith({ phone: "+919876543210" });
    expect(screen.getByRole("textbox", { name: /mobile number/i })).toBeDisabled();

    await userEvent.type(await screen.findByRole("textbox", { name: /one-time code/i }), "123456");
    await userEvent.click(screen.getByRole("button", { name: "Verify code" }));

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/"));
    expect(supabase.auth.verifyOtp).toHaveBeenCalledWith({ phone: "+919876543210", token: "123456", type: "sms" });
    // The API call carried Supabase's (auto-refreshed) token, not a stored copy.
    expect(requests[0].headers.get("Authorization")).toBe("Bearer sb-token");
    expect(session.tenant()).toBe("t-1");
  });

  it("shows why a code was not accepted", async () => {
    supabase.auth.signInWithOtp.mockResolvedValue({ error: null });
    supabase.auth.verifyOtp.mockResolvedValue({ data: { session: null }, error: new Error("Token has expired or is invalid") });
    render(<LoginPage />);

    await userEvent.type(screen.getByRole("textbox", { name: /mobile number/i }), "9876543210");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
    await userEvent.type(await screen.findByRole("textbox", { name: /one-time code/i }), "000000");
    await userEvent.click(screen.getByRole("button", { name: "Verify code" }));

    expect(await screen.findByText("Token has expired or is invalid")).toBeInTheDocument();
  });

  it("normalises numbers typed with a country code or other digits", async () => {
    supabase.auth.signInWithOtp.mockResolvedValue({ error: null });
    await sendOtp("+44 7700 900123");
    expect(supabase.auth.signInWithOtp).toHaveBeenLastCalledWith({ phone: "+447700900123" });
    await sendOtp("447700900123");
    expect(supabase.auth.signInWithOtp).toHaveBeenLastCalledWith({ phone: "+447700900123" });
  });

  it("reports a failure to send the code", async () => {
    supabase.auth.signInWithOtp.mockResolvedValue({ error: new Error("SMS provider unavailable") });
    render(<LoginPage />);
    await userEvent.type(screen.getByRole("textbox", { name: /mobile number/i }), "9876543210");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
    expect(await screen.findByText("SMS provider unavailable")).toBeInTheDocument();
  });
});
