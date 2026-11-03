import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { json, requests } from "@/test/api-mock";
import { nav, router } from "@/test/navigation";
import { renderAsStaff, roles } from "@/test/staff";
import DashboardPage from "./page";

vi.mock("next/navigation", () => import("@/test/navigation"));

const stats = {
  total: 10, openUnassigned: 3,
  byStatus: { NEW: 4, CONTACTED: 2, ASSIGNED: 3, CLOSED: 1 },
  bySource: { WEBSITE_FORM: 7, WALK_IN: 3, PHONE: 0 },
  byAssignee: [{ userId: "u-c", name: "Natasha Counsellor", count: 3 }],
};

describe("Dashboard", () => {
  beforeEach(() => {
    nav.path = "/dashboard";
  });

  it("shows admins the counts, sources and team workload", async () => {
    await renderAsStaff(<DashboardPage />, roles.admin, () => json(stats));

    expect(await screen.findByText("Natasha Counsellor")).toBeInTheDocument();
    expect(screen.getByText("3 assigned")).toBeInTheDocument();
    expect(screen.getByText("Website form")).toBeInTheDocument();
    expect(screen.queryByText("Phone")).toBeNull(); // sources with no leads are not listed
    expect(screen.getByText("Open & unassigned").closest("a")).toHaveAttribute("href", "/leads?unassigned=true");
  });

  it("leaves out everything about a team for a business without Team & branches", async () => {
    await renderAsStaff(<DashboardPage />, roles.soloAdmin, () => json(stats));

    expect(await screen.findByText("Website form")).toBeInTheDocument();
    expect(screen.getByText("Contacted", { selector: "p" }).closest("a")).toHaveAttribute("href", "/leads?status=CONTACTED");
    expect(screen.getByText("Closed", { selector: "p" }).closest("a")).toHaveAttribute("href", "/leads?status=CLOSED");
    for (const hidden of ["Team workload", "Open & unassigned", "Assigned", "All branches", "Natasha Counsellor"]) {
      expect(screen.queryByText(hidden)).toBeNull();
    }
    expect(screen.queryByText(/counsellor/i)).toBeNull();
  });

  it("asks the backend for the chosen period", async () => {
    await renderAsStaff(<DashboardPage />, roles.admin, () => json(stats));
    await screen.findByText("Natasha Counsellor");
    expect(requests.at(-1)!.path).toBe("/api/v1/leads/stats");

    await userEvent.selectOptions(screen.getByDisplayValue("All time"), "week");
    await vi.waitFor(() => expect(requests.at(-1)!.path).toMatch(/\/api\/v1\/leads\/stats\?from=/));
  });

  it("says so when a period has no leads", async () => {
    await renderAsStaff(<DashboardPage />, roles.admin, () =>
      json({ total: 0, openUnassigned: 0, byStatus: {}, bySource: {}, byAssignee: [] }));
    expect(await screen.findByText("No leads in this period.")).toBeInTheDocument();
    expect(screen.getByText("No assigned leads in this period.")).toBeInTheDocument();
  });

  it("sends people without REPORTS_VIEW to their leads", async () => {
    await renderAsStaff(<DashboardPage />, roles.counsellor);
    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/leads"));
    expect(requests.some((r) => r.path.startsWith("/api/v1/leads/stats"))).toBe(false);
  });
});
