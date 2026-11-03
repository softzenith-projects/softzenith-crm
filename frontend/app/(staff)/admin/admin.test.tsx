import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { json, requests } from "@/test/api-mock";
import { nav, router } from "@/test/navigation";
import { renderAsStaff, roles } from "@/test/staff";
import AdminLayout from "./layout";
import BranchesPage from "./branches/page";
import StaffPage from "./staff/page";

vi.mock("next/navigation", () => import("@/test/navigation"));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const people = [
  { id: "u-admin", fullName: "Anuj Admin", phone: "+919000000001", role: { id: "r-admin", name: "Admin" }, status: "ACTIVE" },
  { id: "u-c", fullName: "Natasha Counsellor", phone: "+919000000003", role: { id: "r-c", name: "Counsellor" },
    branch: { id: "b1", name: "Rohini" }, status: "ACTIVE", designation: "Senior Counsellor" },
  { id: "u-new", fullName: "New Joiner", phone: "+919000000009", role: { id: "r-c", name: "Counsellor" }, status: "INVITED" },
];
const roleList = [{ id: "r-admin", name: "Admin" }, { id: "r-c", name: "Counsellor" }];
const branchList = [{ id: "b1", name: "Rohini", city: "Delhi", active: true }, { id: "b2", name: "Old Office", active: false }];

function staffBackend(request: Request) {
  const key = `${request.method} ${new URL(request.url).pathname}`;
  if (key === "GET /api/v1/users") return json(people);
  if (key === "GET /api/v1/roles") return json(roleList);
  if (key === "GET /api/v1/branches") return json(branchList);
  if (request.method === "POST" || request.method === "PUT") return json(people[1]);
  return json({}, 599);
}

/** The table row of a person (their name also appears in the sidebar for the signed-in user). */
const rowOf = (name: string) => screen.getAllByText(name).map((el) => el.closest("tr")).find(Boolean)!;

describe("Admin pages without Team & branches", () => {
  beforeEach(() => {
    nav.path = "/admin/staff";
  });

  it("sends a typed-in address to the leads without loading staff or roles", async () => {
    await renderAsStaff(<AdminLayout><StaffPage /></AdminLayout>, roles.soloAdmin, staffBackend);
    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/leads"));
    expect(requests.some((r) => r.path.startsWith("/api/v1/users") || r.path.startsWith("/api/v1/roles"))).toBe(false);
    expect(screen.queryByRole("heading", { name: "Staff" })).toBeNull();
  });

  it("opens as usual for a business with it", async () => {
    await renderAsStaff(<AdminLayout><StaffPage /></AdminLayout>, roles.admin, staffBackend);
    expect(await screen.findByText("Natasha Counsellor")).toBeInTheDocument();
    expect(router.replace).not.toHaveBeenCalledWith("/leads");
  });
});

describe("Staff admin", () => {
  beforeEach(() => {
    nav.path = "/admin/staff";
  });

  it("lists staff with their role, branch and status", async () => {
    await renderAsStaff(<StaffPage />, roles.admin, staffBackend);
    await screen.findByText("Natasha Counsellor");
    expect(within(rowOf("Natasha Counsellor")).getByText("Rohini")).toBeInTheDocument();
    expect(within(rowOf("New Joiner")).getByText("Invited")).toBeInTheDocument();
  });

  it("invites a new staff member with only active branches offered", async () => {
    await renderAsStaff(<StaffPage />, roles.admin, staffBackend);
    await userEvent.click(await screen.findByRole("button", { name: /add staff member/i }));

    const branch = await screen.findByRole("combobox", { name: /branch/i });
    expect(within(branch).queryByText("Old Office")).toBeNull();
    await userEvent.type(screen.getByRole("textbox", { name: /full name/i }), "Priya");
    await userEvent.type(screen.getByRole("textbox", { name: /mobile/i }), "9000000007");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /role/i }), "r-c");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await vi.waitFor(() => expect(requests.find((r) => r.method === "POST")?.body).toEqual({
      fullName: "Priya", phone: "9000000007", roleId: "r-c",
    }));
  });

  it("keeps an active person's phone (their sign-in) locked when editing", async () => {
    await renderAsStaff(<StaffPage />, roles.admin, staffBackend);
    await screen.findByText("Natasha Counsellor");
    await userEvent.click(within(rowOf("Natasha Counsellor")).getByRole("button", { name: "Actions" }));
    await userEvent.click(await screen.findByText("Edit"));

    expect(await screen.findByRole("textbox", { name: /mobile/i })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    await vi.waitFor(() => expect(requests.find((r) => r.method === "PUT")?.body).toMatchObject({ phone: "+919000000003" }));
  });

  it("does not let you disable yourself", async () => {
    await renderAsStaff(<StaffPage />, roles.admin, staffBackend);
    await screen.findByText("Natasha Counsellor");
    await userEvent.click(within(rowOf("Anuj Admin")).getByRole("button", { name: "Actions" }));
    expect(await screen.findByText("Edit")).toBeInTheDocument();
    expect(screen.queryByText("Disable")).toBeNull();
  });

  it("is read-only for roles that can only view staff", async () => {
    await renderAsStaff(<StaffPage />, roles.receptionist, staffBackend);
    await screen.findByText("Natasha Counsellor");
    expect(screen.queryByRole("button", { name: /add staff member/i })).toBeNull();
    expect(screen.queryByRole("button", { name: "Actions" })).toBeNull();
  });
});

describe("Branches admin", () => {
  beforeEach(() => {
    nav.path = "/admin/branches";
  });

  it("adds a branch and toggles one on and off", async () => {
    await renderAsStaff(<BranchesPage />, roles.admin, (request) =>
      request.method === "GET" ? json(branchList) : json(branchList[0]));

    expect(await screen.findByText("Old Office")).toBeInTheDocument();
    expect(screen.getByText("Inactive")).toBeInTheDocument();

    await userEvent.type(screen.getByPlaceholderText(/branch name/i), "Bahadurgarh");
    await userEvent.click(screen.getByRole("button", { name: /add branch/i }));
    await vi.waitFor(() => expect(requests.find((r) => r.method === "POST")?.body).toEqual({ name: "Bahadurgarh" }));

    await userEvent.click(screen.getByRole("button", { name: "Deactivate" }));
    await vi.waitFor(() => expect(requests.find((r) => r.method === "PUT")?.body).toEqual({ name: "Rohini", city: "Delhi", active: false }));
  });

  it("shows why a branch could not be added", async () => {
    await renderAsStaff(<BranchesPage />, roles.admin, (request) =>
      request.method === "GET" ? json([]) : json({ detail: "name: must not be blank" }, 400));
    await userEvent.type(await screen.findByPlaceholderText(/branch name/i), " ");
    await userEvent.click(screen.getByRole("button", { name: /add branch/i }));
    expect(await screen.findByText("name: must not be blank")).toBeInTheDocument();
  });
});
