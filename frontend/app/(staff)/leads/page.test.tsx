import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { json, requests } from "@/test/api-mock";
import { lead, page } from "@/test/leads";
import { nav, router } from "@/test/navigation";
import { renderAsStaff, roles } from "@/test/staff";
import LeadsPage from "./page";

vi.mock("next/navigation", () => import("@/test/navigation"));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const form = { tenantName: "Western World", branches: [{ id: "b-rohini", name: "Rohini" }], serviceInterests: ["Study Abroad"], countries: [] };
const leadsCalls = () => requests.filter((r) => r.method === "GET" && r.path.startsWith("/api/v1/leads"));

describe("Leads list", () => {
  beforeEach(() => {
    nav.path = "/leads";
    nav.search = new URLSearchParams();
  });

  it("lists leads with repeat and unassigned markers", async () => {
    await renderAsStaff(<LeadsPage />, roles.receptionist, () => json(page([
      lead({ enquiryCount: 3 }),
      lead({ id: "lead-2", fullName: "Kabir Das", assignedTo: { id: "u-c", name: "Natasha Counsellor" }, status: "ASSIGNED" }),
    ])));

    expect(await screen.findByText("Riya Sharma")).toBeInTheDocument();
    expect(screen.getByText("Repeat ×3")).toBeInTheDocument();
    expect(screen.getByText("Natasha Counsellor")).toBeInTheDocument();
    expect(screen.getByText("Unassigned")).toBeInTheDocument();
    expect(screen.getByText("2 leads")).toBeInTheDocument();
  });

  it("leaves out assignment and branches for a business without Team & branches", async () => {
    await renderAsStaff(<LeadsPage />, roles.soloAdmin, () => json(page([lead()])));

    expect(await screen.findByText("Riya Sharma")).toBeInTheDocument();
    for (const hidden of ["Assigned to", "Branch", "Unassigned", "Unassigned only"]) {
      expect(screen.queryByText(hidden)).toBeNull();
    }
    expect(screen.queryByRole("option", { name: "Assigned" })).toBeNull();
    expect(screen.getByRole("option", { name: "Contacted" })).toBeInTheDocument();
  });

  it("offers no branch in the add-lead form when the business has no branches", async () => {
    nav.search = new URLSearchParams("new=1");
    await renderAsStaff(<LeadsPage />, roles.soloAdmin, (request) =>
      new URL(request.url).pathname.includes("enquiry-form") ? json({ ...form, branches: [] }) : json(page([])));

    expect(await screen.findByRole("heading", { name: "Add a lead" })).toBeInTheDocument();
    await vi.waitFor(() => expect(requests.some((r) => r.path.includes("enquiry-form"))).toBe(true));
    expect(screen.queryByRole("combobox", { name: /branch/i })).toBeNull();
  });

  it("takes filters from the URL and sends them to the backend", async () => {
    nav.search = new URLSearchParams("q=riya&status=NEW&unassigned=true");
    await renderAsStaff(<LeadsPage />, roles.receptionist, () => json(page([])));

    expect(await screen.findByText("No leads match these filters.")).toBeInTheDocument();
    const query = new URLSearchParams(leadsCalls()[0].path.split("?")[1]);
    expect(query.get("q")).toBe("riya");
    expect(query.get("status")).toBe("NEW");
    expect(query.get("unassigned")).toBe("true");
  });

  it("filters by source and pages through results", async () => {
    await renderAsStaff(<LeadsPage />, roles.admin, () => json(page([lead()], { totalElements: 60, totalPages: 3 })));
    await screen.findByText("Page 1 of 3");

    await userEvent.click(screen.getByRole("button", { name: "Next page" }));
    await screen.findByText("Page 2 of 3");
    expect(leadsCalls().at(-1)!.path).toContain("page=1");

    await userEvent.selectOptions(screen.getByDisplayValue("All sources"), "WALK_IN");
    await vi.waitFor(() => expect(leadsCalls().at(-1)!.path).toContain("sourceType=WALK_IN"));
    expect(leadsCalls().at(-1)!.path).toContain("page=0"); // a new filter starts from page 1
  });

  it("opens a lead when its row is clicked", async () => {
    await renderAsStaff(<LeadsPage />, roles.admin, () => json(page([lead()])));
    await userEvent.click(await screen.findByText("Study Abroad · Canada"));
    expect(router.push).toHaveBeenCalledWith("/leads/lead-1");
  });

  it("lets a counsellor add a walk-in and explains it goes to the unassigned queue", async () => {
    nav.search = new URLSearchParams("new=1");
    await renderAsStaff(<LeadsPage />, roles.counsellor, (request, body) => {
      const path = new URL(request.url).pathname;
      if (path.endsWith("/enquiry-form")) return json(form);
      if (request.method === "POST") return json(lead({ id: "lead-9", leadNumber: "LD-000009", ...(body as object) }), 201);
      return json(page([]));
    });

    await userEvent.type(await screen.findByRole("textbox", { name: /full name/i }), "Walk In");
    await userEvent.type(screen.getByRole("textbox", { name: /mobile number/i }), "9811111111");
    await userEvent.click(screen.getByRole("button", { name: "Save lead" }));

    await vi.waitFor(() => expect(toast.success).toHaveBeenCalledWith(expect.stringContaining("unassigned queue")));
    expect(requests.find((r) => r.method === "POST")!.body).toMatchObject({
      fullName: "Walk In", phone: "9811111111", sourceType: "WALK_IN", branchId: "b-rohini",
    });
    expect(router.replace).toHaveBeenCalledWith("/leads");
  });

  it("hides Add lead from roles without LEAD_CREATE", async () => {
    await renderAsStaff(<LeadsPage />, { ...roles.receptionist, permissions: ["LEAD_VIEW"] }, () => json(page([])));
    await screen.findByText("No leads match these filters.");
    expect(screen.queryByRole("button", { name: /add lead/i })).toBeNull();
  });
});
