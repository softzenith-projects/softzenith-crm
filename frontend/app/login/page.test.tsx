import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { json, requests, routes } from "@/test/api-mock";
import { router } from "@/test/navigation";
import { session } from "@/lib/session";
import LoginPage from "./page";

vi.mock("next/navigation", () => import("@/test/navigation"));

const membership = (id: string, name: string, role: string) => ({ tenant: { id, name, slug: id }, role: { name: role } });

async function signIn(phone: string) {
  render(<LoginPage />);
  await userEvent.type(screen.getByRole("textbox", { name: /mobile number/i }), phone);
  await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
}

describe("Login (dev mode)", () => {
  it("signs a one-organisation user straight in", async () => {
    routes({
      "POST /api/v1/dev/login": () => json({ accessToken: "tok" }),
      "GET /api/v1/me/memberships": () => json([membership("t-1", "Western World", "Admin")]),
    });
    await signIn("9000000001");

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/"));
    expect(session.token()).toBe("tok");
    expect(session.tenant()).toBe("t-1");
  });

  it("lets someone who works for several organisations pick one", async () => {
    routes({
      "POST /api/v1/dev/login": () => json({ accessToken: "tok" }),
      "GET /api/v1/me/memberships": () => json([membership("t-1", "Demo Visas", "Admin"), membership("t-2", "Western World", "Counsellor")]),
    });
    await signIn("9000000001");

    await userEvent.click(await screen.findByRole("button", { name: /Western World/ }));
    expect(session.tenant()).toBe("t-2");
    expect(router.replace).toHaveBeenCalledWith("/");
  });

  it("explains when the phone is not staff anywhere", async () => {
    routes({
      "POST /api/v1/dev/login": () => json({ accessToken: "tok" }),
      "GET /api/v1/me/memberships": () => json([]),
    });
    await signIn("9811111111");
    expect(await screen.findByText(/not registered as staff/)).toBeInTheDocument();
  });

  it("shows the backend's reason for a bad number", async () => {
    routes({ "POST /api/v1/dev/login": () => json({ detail: "Invalid phone number: 12" }, 400) });
    await signIn("12");
    expect(await screen.findByText("Invalid phone number: 12")).toBeInTheDocument();
  });

  it("fills the phone from the demo users list", async () => {
    render(<LoginPage />);
    await userEvent.click(screen.getByRole("button", { name: /9000000004 · Receptionist/ }));
    expect(screen.getByRole("textbox", { name: /mobile number/i })).toHaveValue("9000000004");
  });
});

describe("Login with a sign-in code", () => {
  it("in development, uses the code sign-in when a code is typed", async () => {
    routes({
      "POST /api/v1/auth/phone-code": () => json({ accessToken: "code-tok" }),
      "GET /api/v1/me/memberships": () => json([membership("t-1", "Demo Visas", "Admin")]),
    });
    render(<LoginPage />);
    await userEvent.type(screen.getByRole("textbox", { name: /mobile number/i }), "9000000001");
    await userEvent.type(screen.getByLabelText(/sign-in code/i), "482917");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/"));
    expect(session.token()).toBe("code-tok");
    expect(requests.find((r) => r.path === "/api/v1/auth/phone-code")?.body).toEqual({ phone: "9000000001", code: "482917" });
  });

  describe("in a production build", () => {
    beforeEach(() => vi.stubEnv("NODE_ENV", "production"));
    afterEach(() => vi.unstubAllEnvs());

    it("needs the code and offers no development sign-in", async () => {
      render(<LoginPage />);
      expect(screen.getByLabelText(/sign-in code/i)).toBeRequired();
      expect(screen.queryByText(/Demo users/)).toBeNull();
      expect(screen.getByText(/sign-in code your administrator gave you/)).toBeInTheDocument();
    });

    it("shows the backend's refusal", async () => {
      routes({ "POST /api/v1/auth/phone-code": () => json({ detail: "Wrong mobile number or sign-in code" }, 401) });
      render(<LoginPage />);
      await userEvent.type(screen.getByRole("textbox", { name: /mobile number/i }), "9000000001");
      await userEvent.type(screen.getByLabelText(/sign-in code/i), "739164");
      await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
      expect(await screen.findByText("Wrong mobile number or sign-in code")).toBeInTheDocument();
      expect(requests.some((r) => r.path === "/api/v1/dev/login")).toBe(false);
    });
  });
});
