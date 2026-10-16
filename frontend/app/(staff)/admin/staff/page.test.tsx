import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { json, requests } from "@/test/api-mock";
import { nav } from "@/test/navigation";
import { renderAsStaff, roles } from "@/test/staff";
import StaffPage from "./page";

vi.mock("next/navigation", () => import("@/test/navigation"));

const priya = {
  id: "u-priya", fullName: "Priya Sharma", phone: "+919811111111", status: "INVITED",
  role: { id: "r-c", code: "COUNSELLOR", name: "Counsellor" }, hasSigninCode: false,
};
const deepak = { ...priya, id: "u-admin", fullName: "Deepak Kumar", phone: "+919000000005", status: "ACTIVE",
  role: { id: "r-a", code: "ADMIN", name: "Admin" }, hasSigninCode: true, signinCodeSetAt: "2026-10-08T10:00:00Z" };

function backend(request: Request) {
  const path = new URL(request.url).pathname;
  if (path === "/api/v1/users") return json([deepak, priya]);
  if (path === "/api/v1/users/u-priya/signin-code") return json({ ...priya, hasSigninCode: true });
  return json([]);
}

async function openCodeDialog() {
  await renderAsStaff(<StaffPage />, roles.admin, backend);
  const row = (await screen.findByText("Priya Sharma")).closest("tr")!;
  expect(within(row).getByText("Not set")).toBeInTheDocument();
  await userEvent.click(within(row).getByRole("button", { name: "Actions" }));
  await userEvent.click(await screen.findByRole("menuitem", { name: "Set sign-in code" }));
  return screen.getByRole("dialog");
}

describe("Staff: sign-in codes", () => {
  beforeEach(() => {
    nav.path = "/admin/staff";
  });

  it("shows who has a code", async () => {
    await renderAsStaff(<StaffPage />, roles.admin, backend);
    const row = (await screen.findByText("Deepak Kumar")).closest("tr")!;
    expect(within(row).getByText(/^Set/)).toBeInTheDocument();
  });

  it("lets an admin set a code once and shows it to hand over", async () => {
    const dialog = await openCodeDialog();
    await userEvent.type(within(dialog).getByLabelText(/Sign-in code/), "482917");
    await userEvent.click(within(dialog).getByRole("button", { name: "Save code" }));

    expect(await screen.findByText("Sign-in code saved")).toBeInTheDocument();
    expect(screen.getByText("482917")).toBeInTheDocument();
    expect(requests.find((r) => r.method === "PUT")).toMatchObject({
      path: "/api/v1/users/u-priya/signin-code", body: { code: "482917" },
    });
  });

  it("refuses an easy code before asking the backend, and can generate a good one", async () => {
    const dialog = await openCodeDialog();
    const input = within(dialog).getByLabelText(/Sign-in code/);
    await userEvent.type(input, "123456");
    await userEvent.click(within(dialog).getByRole("button", { name: "Save code" }));
    expect(await within(dialog).findByText(/too easy to guess/)).toBeInTheDocument();
    expect(requests.some((r) => r.method === "PUT")).toBe(false);

    await userEvent.click(within(dialog).getByRole("button", { name: "Generate" }));
    expect((input as HTMLInputElement).value).toMatch(/^\d{6}$/);
  });
});
