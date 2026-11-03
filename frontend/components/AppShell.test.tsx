import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { json, mockApi, requests } from "@/test/api-mock";
import { nav, router } from "@/test/navigation";
import { renderAsStaff, roles } from "@/test/staff";
import AppShell, { TEAM, can, hasFeature } from "./AppShell";
import { render } from "@testing-library/react";
import { session } from "@/lib/session";

vi.mock("next/navigation", () => import("@/test/navigation"));

const menu = () => screen.getAllByRole("link").map((a) => a.textContent);

describe("AppShell", () => {
  beforeEach(() => {
    nav.path = "/leads";
  });

  it("shows admins every module", async () => {
    await renderAsStaff(<p>page</p>, roles.admin);
    expect(menu()).toEqual(expect.arrayContaining(["Dashboard", "Leads", "Staff", "Branches"]));
    expect(screen.getByText("All branches")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /add lead/i })).toBeInTheDocument();
  });

  it("hides staff and branches from a business without Team & branches", async () => {
    await renderAsStaff(<p>page</p>, roles.soloAdmin);
    expect(menu()).toEqual(expect.arrayContaining(["Dashboard", "Leads"]));
    expect(menu()).not.toContain("Staff");
    expect(menu()).not.toContain("Branches");
    expect(screen.queryByText("All branches")).toBeNull();
    // Administration then holds only the lead import.
    expect(menu()).toContain("Import leads");
  });

  it("lets an admin change their own sign-in code, which signs them out", async () => {
    await renderAsStaff(<p>page</p>, roles.soloAdmin, () => json({}));
    await userEvent.click(screen.getAllByText("Anuj Admin")[0]);
    await userEvent.click(await screen.findByText("Change my sign-in code"));
    await userEvent.type(await screen.findByRole("textbox", { name: /sign-in code/i }), "739164");
    await userEvent.click(screen.getByRole("button", { name: /save and sign out/i }));

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/login"));
    expect(requests.find((r) => r.method === "PUT")).toMatchObject({ path: "/api/v1/users/u-admin/signin-code", body: { code: "739164" } });
    expect(session.token()).toBeNull();
  });

  it("does not offer a code change to people who cannot manage staff", async () => {
    await renderAsStaff(<p>page</p>, roles.receptionist);
    await userEvent.click(screen.getAllByText("Front Desk")[0]);
    await screen.findByText("Sign out");
    expect(screen.queryByText("Change my sign-in code")).toBeNull();
  });

  it("shows a counsellor only what their role allows, and their branch", async () => {
    await renderAsStaff(<p>page</p>, roles.counsellor);
    expect(menu()).toContain("Leads");
    expect(menu()).not.toContain("Dashboard");
    expect(menu()).not.toContain("Branches");
    expect(screen.getByText("Rohini")).toBeInTheDocument();
  });

  it("marks the current page in the menu", async () => {
    await renderAsStaff(<p>page</p>, roles.receptionist);
    expect(screen.getByRole("link", { name: "Leads" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Staff" })).not.toHaveAttribute("aria-current");
  });

  it("searches leads from the top bar", async () => {
    await renderAsStaff(<p>page</p>, roles.receptionist);
    await userEvent.type(screen.getByPlaceholderText(/search leads/i), "  Riya {enter}");
    expect(router.push).toHaveBeenCalledWith("/leads?q=Riya");
  });

  it("sends people who are not signed in to the login page", () => {
    render(<AppShell><p>page</p></AppShell>);
    expect(router.replace).toHaveBeenCalledWith("/login");
  });

  it("sends people whose sign-in the backend refuses to the login page", async () => {
    session.setToken("stale");
    mockApi(() => json({ detail: "Not staff here" }, 403));
    render(<AppShell><p>page</p></AppShell>);
    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/login"));
  });

  it("signs out", async () => {
    await renderAsStaff(<p>page</p>, roles.admin);
    await userEvent.click(screen.getAllByText("Anuj Admin")[0]);
    await userEvent.click(await screen.findByText("Sign out"));
    expect(session.token()).toBeNull();
    expect(router.replace).toHaveBeenCalledWith("/login");
  });
});

describe("hasFeature", () => {
  it("is true only for features the business's plan includes", () => {
    expect(hasFeature(roles.admin, TEAM)).toBe(true);
    expect(hasFeature(roles.soloAdmin, TEAM)).toBe(false);
    expect(hasFeature({}, TEAM)).toBe(false);
  });
});

describe("can", () => {
  it("is true only for permissions the user holds", () => {
    expect(can(roles.receptionist, "LEAD_ASSIGN")).toBe(true);
    expect(can(roles.receptionist, "LEAD_REOPEN")).toBe(false);
    expect(can({}, "LEAD_VIEW")).toBe(false);
  });
});
