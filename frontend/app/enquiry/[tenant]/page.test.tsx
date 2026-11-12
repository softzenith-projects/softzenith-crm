import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { json, requests, routes } from "@/test/api-mock";
import { nav } from "@/test/navigation";
import EnquiryPage from "./page";

vi.mock("next/navigation", () => import("@/test/navigation"));

const FORM = "GET /api/v1/public/tenants/westernworld/enquiry-form";
const SUBMIT = "POST /api/v1/public/tenants/westernworld/enquiries";
const form = { tenantName: "Western World", branches: [{ id: "b1", name: "Rohtak" }], serviceInterests: ["Study Abroad"], countries: ["Canada", "UK"] };

async function fillAndSend() {
  await userEvent.type(await screen.findByRole("textbox", { name: /full name/i }), "Riya Sharma");
  await userEvent.type(screen.getByRole("textbox", { name: /mobile number/i }), "9876543210");
  await userEvent.selectOptions(screen.getByRole("combobox", { name: /preferred country/i }), "Canada");
  await userEvent.click(screen.getByRole("button", { name: "Submit enquiry" }));
}

describe("Public enquiry page", () => {
  beforeEach(() => {
    nav.params = { tenant: "westernworld" };
    window.history.pushState({}, "", "/enquiry/westernworld?utm_source=facebook&utm_campaign=sep-intake");
  });

  it("shows the tenant's form and thanks the enquirer after sending", async () => {
    routes({ [FORM]: () => json(form), [SUBMIT]: () => json({ message: "Thank you! Someone from the Western World team will get in touch." }, 201) });
    render(<EnquiryPage />);

    expect(await screen.findByText("Western World")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Rohtak" })).toBeInTheDocument();
    await fillAndSend();

    expect(await screen.findByRole("heading", { name: /will get in touch/ })).toBeInTheDocument();
    expect(requests.find((r) => r.method === "POST")!.body).toEqual({
      fullName: "Riya Sharma", phone: "9876543210", preferredCountry: "Canada", utmSource: "facebook", utmCampaign: "sep-intake",
    });
  });

  it("keeps the form and shows why when the enquiry is refused", async () => {
    routes({ [FORM]: () => json(form), [SUBMIT]: () => json({ detail: "Too many enquiries for this phone number; please try again later" }, 429) });
    render(<EnquiryPage />);
    await fillAndSend();

    expect(await screen.findByText(/Too many enquiries/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Submit enquiry" })).toBeEnabled();
  });

  it("asks what 'Other' means and sends the typed country", async () => {
    routes({ [FORM]: () => json({ ...form, countries: ["Canada", "Other"] }), [SUBMIT]: () => json({ reference: "LD-1", message: "Thanks" }, 201) });
    render(<EnquiryPage />);

    await userEvent.type(await screen.findByRole("textbox", { name: /full name/i }), "Riya Sharma");
    await userEvent.type(screen.getByRole("textbox", { name: /mobile number/i }), "9876543210");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /preferred country/i }), "Other");
    await userEvent.type(screen.getByRole("textbox", { name: /which country/i }), "  Australia ");
    await userEvent.click(screen.getByRole("button", { name: "Submit enquiry" }));

    await screen.findByRole("heading", { name: "Thanks" });
    expect(requests.find((r) => r.method === "POST")!.body).toMatchObject({ preferredCountry: "Australia" });
  });

  it("shows an error for an unknown organisation", async () => {
    routes({ [FORM]: () => json({ detail: "Tenant westernworld not found" }, 404) });
    render(<EnquiryPage />);
    expect(await screen.findByText("Tenant westernworld not found")).toBeInTheDocument();
  });

  it("has a honeypot field that people cannot see or tab to", async () => {
    routes({ [FORM]: () => json({ ...form, branches: [] }) });
    const { container } = render(<EnquiryPage />);
    await screen.findByText("Western World");
    const honeypot = container.querySelector("input[name=website]")!;
    expect(honeypot).toHaveAttribute("tabindex", "-1");
    expect(honeypot).toHaveAttribute("aria-hidden", "true");
    expect(screen.queryByRole("combobox", { name: /nearest branch/i })).toBeNull(); // no branches, no branch picker
  });
});
