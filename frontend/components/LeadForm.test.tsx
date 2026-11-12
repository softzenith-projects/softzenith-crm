import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";
import { describe, expect, it, vi } from "vitest";
import { json, requests, routes } from "@/test/api-mock";
import { lead } from "@/test/leads";
import { LeadForm } from "./LeadForm";

const renderForm = (onSaved = vi.fn()) => {
  render(
    <SWRConfig value={{ provider: () => new Map() }}>
      <LeadForm tenantSlug="westernworld" onSaved={onSaved} />
    </SWRConfig>,
  );
  return onSaved;
};

describe("LeadForm", () => {
  it("offers the tenant's options as choices, or free text when it has none", async () => {
    routes({
      "GET /api/v1/public/tenants/westernworld/enquiry-form": () =>
        json({ tenantName: "Western World", branches: [{ id: "b1", name: "Rohtak" }], serviceInterests: ["Study Abroad", "IELTS"], countries: [] }),
    });
    renderForm();

    expect(await screen.findByRole("option", { name: "IELTS" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Rohtak" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /preferred country/i })).toBeInTheDocument();
  });

  it("sends only the filled-in fields and clears the form after saving", async () => {
    routes({
      "GET /api/v1/public/tenants/westernworld/enquiry-form": () => json({ tenantName: "E", branches: [], serviceInterests: [], countries: [] }),
      "POST /api/v1/leads": (body) => json(lead(body as object), 201),
    });
    const onSaved = renderForm();

    const name = await screen.findByRole("textbox", { name: /full name/i });
    await userEvent.type(name, "Asha Verma");
    await userEvent.type(screen.getByRole("textbox", { name: /mobile number/i }), "9876543210");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /source/i }), "PHONE");
    await userEvent.click(screen.getByRole("button", { name: "Save lead" }));

    await vi.waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(requests.find((r) => r.method === "POST")!.body).toEqual({ fullName: "Asha Verma", phone: "9876543210", sourceType: "PHONE" });
    // A fresh form after saving (the form is re-mounted, so look the field up again).
    expect(screen.getByRole("textbox", { name: /full name/i })).toHaveValue("");
  });

  it("asks what 'Other' means and sends the typed values in its place", async () => {
    routes({
      "GET /api/v1/public/tenants/westernworld/enquiry-form": () =>
        json({ tenantName: "E", branches: [], serviceInterests: ["Study visa", "Other"], countries: ["UK", "Other"] }),
      "POST /api/v1/leads": (body) => json(lead(body as object), 201),
    });
    const onSaved = renderForm();

    await userEvent.type(await screen.findByRole("textbox", { name: /full name/i }), "Asha Verma");
    await userEvent.type(screen.getByRole("textbox", { name: /mobile number/i }), "9876543210");
    expect(screen.queryByRole("textbox", { name: /which/i })).not.toBeInTheDocument();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /source/i }), "OTHER");
    await userEvent.type(screen.getByRole("textbox", { name: /which source/i }), "Newspaper ad");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /interested in/i }), "Other");
    await userEvent.type(screen.getByRole("textbox", { name: /which service/i }), "Spouse visa");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /preferred country/i }), "Other");
    await userEvent.type(screen.getByRole("textbox", { name: /which country/i }), "Australia");
    await userEvent.click(screen.getByRole("button", { name: "Save lead" }));

    await vi.waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(requests.find((r) => r.method === "POST")!.body).toEqual({
      fullName: "Asha Verma", phone: "9876543210", sourceType: "OTHER", sourceDetail: "Newspaper ad",
      serviceInterest: "Spouse visa", preferredCountry: "Australia",
    });
  });

  it("refuses an 'Other' answer that says nothing", async () => {
    routes({
      "GET /api/v1/public/tenants/westernworld/enquiry-form": () =>
        json({ tenantName: "E", branches: [], serviceInterests: [], countries: ["UK", "Other"] }),
      "POST /api/v1/leads": (body) => json(lead(body as object), 201),
    });
    const onSaved = renderForm();

    await userEvent.type(await screen.findByRole("textbox", { name: /full name/i }), "Asha Verma");
    await userEvent.type(screen.getByRole("textbox", { name: /mobile number/i }), "9876543210");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /preferred country/i }), "Other");
    const which = screen.getByRole("textbox", { name: /which country/i });
    await userEvent.type(which, "na");
    expect((which as HTMLInputElement).validationMessage).toMatch(/does not say much/);
    await userEvent.click(screen.getByRole("button", { name: "Save lead" }));

    expect(onSaved).not.toHaveBeenCalled();
    expect(requests.some((r) => r.method === "POST")).toBe(false);
  });

  it("shows the backend's validation message and keeps what was typed", async () => {
    routes({
      "GET /api/v1/public/tenants/westernworld/enquiry-form": () => json({ tenantName: "E", branches: [], serviceInterests: [], countries: [] }),
      "POST /api/v1/leads": () => json({ detail: "Invalid phone number: 12" }, 400),
    });
    const onSaved = renderForm();

    await userEvent.type(await screen.findByRole("textbox", { name: /full name/i }), "Asha");
    await userEvent.type(screen.getByRole("textbox", { name: /mobile number/i }), "12");
    await userEvent.click(screen.getByRole("button", { name: "Save lead" }));

    expect(await screen.findByText("Invalid phone number: 12")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /full name/i })).toHaveValue("Asha");
    expect(onSaved).not.toHaveBeenCalled();
  });
});
