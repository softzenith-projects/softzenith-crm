import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { json, requests } from "@/test/api-mock";
import { lead } from "@/test/leads";
import { nav } from "@/test/navigation";
import { renderAsStaff, roles } from "@/test/staff";
import type { Schemas } from "@/lib/api";
import LeadDetailPage from "./page";

vi.mock("next/navigation", () => import("@/test/navigation"));

type Lead = Schemas["LeadResponse"];

const counsellor = { id: "u-counsellor", fullName: "Natasha Counsellor", role: { name: "Counsellor" }, status: "ACTIVE" };
const disabled = { id: "u-gone", fullName: "Gone Person", role: { name: "Counsellor" }, status: "DISABLED" };

/** The backend for one lead: GET lead/activities/notifications, plus assignment and status changes. */
function backend(current: Lead, extra: Partial<Record<string, (body: unknown) => Response>> = {}) {
  return (request: Request, body: unknown) => {
    const key = `${request.method} ${new URL(request.url).pathname}`;
    const custom = extra[key];
    if (custom) return custom(body);
    switch (key) {
      case `GET /api/v1/leads/${current.id}`: return json(current);
      case `GET /api/v1/leads/${current.id}/activities`: return json([
        { id: "a1", type: "CREATED", toValue: "WEBSITE_FORM", at: "2026-09-29T10:00:00Z" },
        { id: "a2", type: "UNASSIGNED", fromValue: "Natasha Counsellor", note: "Reopened", actor: { name: "Anuj Admin" }, at: "2026-09-29T11:00:00Z" },
      ]);
      case `GET /api/v1/leads/${current.id}/notifications`: return json([
        { id: "n1", channel: "EMAIL", kind: "LEAD_WELCOME", recipient: "riya@mail.test", subject: "Thank you", body: "Hi Riya", status: "SENT", at: "2026-09-29T10:00:05Z" },
        { id: "n2", channel: "WHATSAPP", kind: "LEAD_WELCOME", recipient: "+919876543210", body: "Hi Riya", status: "FAILED", error: "provider down", at: "2026-09-29T10:00:06Z" },
      ]);
      case "GET /api/v1/users": return json([counsellor, disabled]);
      case `PUT /api/v1/leads/${current.id}/assignment`: return json({ ...current, status: "ASSIGNED" });
      case `POST /api/v1/leads/${current.id}/status`: return json(current);
      default: return json({ detail: `unexpected ${key}` }, 599);
    }
  };
}

describe("Lead detail", () => {
  beforeEach(() => {
    nav.params = { id: "lead-1" };
    nav.path = "/leads/lead-1";
  });

  it("shows the lead, its history in words and the messages sent", async () => {
    await renderAsStaff(<LeadDetailPage />, roles.admin, backend(lead({ message: "MS in Canada", utmSource: "google", utmCampaign: "sep" })));

    expect(await screen.findByRole("heading", { name: "Riya Sharma" })).toBeInTheDocument();
    expect(screen.getByText("MS in Canada")).toBeInTheDocument();
    expect(screen.getByText("Campaign: google / sep")).toBeInTheDocument();
    expect(await screen.findByText("Unassigned", { selector: "p" })).toBeInTheDocument();
    expect(screen.getByText("Natasha Counsellor → (Reopened)")).toBeInTheDocument();
    expect(screen.getAllByText("Website form")).toHaveLength(2); // the Source field and the CREATED activity, humanised
    expect(await screen.findByText("Thank you")).toBeInTheDocument();
    expect(screen.getByText("provider down")).toBeInTheDocument();
  });

  it("gives the admin of a business without Team & branches status changes and no assignment", async () => {
    await renderAsStaff(<LeadDetailPage />, roles.soloAdmin, backend(lead()));

    await userEvent.click(await screen.findByRole("button", { name: "Update status" }));
    await vi.waitFor(() => expect(requests.find((r) => r.method === "POST")?.body).toEqual({ status: "CONTACTED" }));

    expect(screen.queryByRole("combobox", { name: /assign to/i })).toBeNull();
    expect(screen.queryByText("Assigned to")).toBeNull();
    expect(screen.queryByText("Branch")).toBeNull();
    expect(requests.some((r) => r.path.startsWith("/api/v1/users"))).toBe(false);
  });

  it("lets reception assign to an active counsellor only", async () => {
    await renderAsStaff(<LeadDetailPage />, roles.receptionist, backend(lead()));
    const select = await screen.findByRole("combobox", { name: /assign to/i });
    expect(within(select).queryByText(/Gone Person/)).toBeNull();

    await userEvent.selectOptions(select, "u-counsellor");
    await userEvent.click(screen.getByRole("button", { name: "Assign" }));

    await vi.waitFor(() => expect(requests.find((r) => r.method === "PUT")?.body).toEqual({ userId: "u-counsellor" }));
    // Reception cannot change status.
    expect(screen.queryByRole("button", { name: /update status/i })).toBeNull();
  });

  it("shows the backend's reason when an assignment is refused", async () => {
    await renderAsStaff(<LeadDetailPage />, roles.receptionist, backend(lead(), {
      "PUT /api/v1/leads/lead-1/assignment": () => json({ detail: "Natasha Counsellor (Counsellor) cannot be assigned leads" }, 409),
    }));
    await userEvent.selectOptions(await screen.findByRole("combobox", { name: /assign to/i }), "u-counsellor");
    await userEvent.click(screen.getByRole("button", { name: "Assign" }));
    expect(await screen.findByText(/cannot be assigned leads/)).toBeInTheDocument();
  });

  it("asks for a reason when closing and sends it", async () => {
    await renderAsStaff(<LeadDetailPage />, roles.counsellor, backend(lead({ status: "ASSIGNED", assignedTo: { id: "u-counsellor", name: "Natasha Counsellor" } })));
    await userEvent.selectOptions(await screen.findByRole("combobox", { name: /change status to/i }), "CLOSED");
    await userEvent.type(screen.getByPlaceholderText(/not interested/i), "Budget");
    await userEvent.click(screen.getByRole("button", { name: /update status/i }));

    await vi.waitFor(() => expect(requests.find((r) => r.method === "POST")?.body).toEqual({ status: "CLOSED", reason: "Budget" }));
    // A counsellor has no LEAD_ASSIGN: no assignment control.
    expect(screen.queryByRole("combobox", { name: /assign to/i })).toBeNull();
  });

  it("offers reopening a closed lead to admins only", async () => {
    const closed = lead({ status: "CLOSED", closedReason: "No response", closedAt: "2026-09-29T12:00:00Z" });
    await renderAsStaff(<LeadDetailPage />, roles.admin, backend(closed));
    expect(await screen.findByRole("button", { name: "Reopen" })).toBeInTheDocument();
    expect(screen.getByText(/No response/)).toBeInTheDocument();
    // Closed leads cannot be assigned until reopened.
    expect(screen.queryByRole("combobox", { name: /assign to/i })).toBeNull();
  });

  it("can reopen straight after closing, without reloading the page", async () => {
    // Regression: the status form kept "CLOSED" selected after the lead closed, so Reopen re-sent CLOSED (409).
    let current = lead();
    await renderAsStaff(<LeadDetailPage />, roles.admin, backend(current, {
      "GET /api/v1/leads/lead-1": () => json(current),
      "POST /api/v1/leads/lead-1/status": (body) => {
        const { status, reason } = body as { status: Lead["status"]; reason?: string };
        current = { ...current, status, closedReason: reason };
        return json(current);
      },
    }));

    await userEvent.selectOptions(await screen.findByRole("combobox", { name: /change status to/i }), "CLOSED");
    await userEvent.type(screen.getByPlaceholderText(/not interested/i), "No response");
    await userEvent.click(screen.getByRole("button", { name: /update status/i }));
    await userEvent.click(await screen.findByRole("button", { name: "Reopen" }));

    await vi.waitFor(() => expect(requests.filter((r) => r.method === "POST").map((r) => r.body)).toEqual([
      { status: "CLOSED", reason: "No response" },
      { status: "NEW" },
    ]));
  });

  it("does not offer a reopen button to a counsellor, who cannot reopen", async () => {
    await renderAsStaff(<LeadDetailPage />, roles.counsellor, backend(lead({ status: "CLOSED", closedReason: "Budget" })));
    await screen.findByRole("heading", { name: "Riya Sharma" });
    expect(screen.queryByRole("button", { name: "Reopen" })).toBeNull();
    expect(screen.queryByText("Actions")).toBeNull();
  });

  it("shows a not-found lead as an error", async () => {
    await renderAsStaff(<LeadDetailPage />, roles.counsellor, () => json({ detail: "Lead lead-1 not found" }, 404));
    expect(await screen.findByText("Lead lead-1 not found")).toBeInTheDocument();
  });
});
