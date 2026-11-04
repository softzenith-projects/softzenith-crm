import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { json, requests } from "@/test/api-mock";
import { nav } from "@/test/navigation";
import { renderAsStaff, roles } from "@/test/staff";
import { chooseField, columnHint, guessValues, rejectedRowsCsv, type ImportPreview } from "@/lib/lead-import";
import ImportPage from "./page";

vi.mock("next/navigation", () => import("@/test/navigation"));

/** The shape of Western World's "Portal Data Pattern.xlsx". */
const preview: ImportPreview = {
  fileName: "Portal Data Pattern.xlsx",
  headers: ["Sr.No.`", "Name", "Mobile No.", "Email ID", "Enquiry for( IELTS/PTE/Visa)", "Address", "Country Planned"],
  columns: ["IGNORE", "FULL_NAME", "PHONE", "EMAIL", "SERVICE_INTEREST", "EXTRA", "PREFERRED_COUNTRY"],
  rows: [
    { row: 3, cells: ["1", "Aman Verma", "9876500001", "aman@mail.test", "IELTS", "12 MG Road", "Canada"] },
    { row: 4, cells: ["2", "Bad Phone", "12345", "", "Visa", "", ""] },
  ],
};
const services = ["Study Abroad", "Student Visa", "Visitor Visa", "IELTS / PTE Coaching", "Other"];

const check = { dryRun: true, total: 2, added: 1, alreadyInCrm: 0, duplicates: 0, rejected: 1,
  problems: [{ row: 4, name: "Bad Phone", phone: "12345", outcome: "REJECTED", detail: "Not a valid mobile number: 12345" }] };

function backend(request: Request) {
  const path = new URL(request.url).pathname;
  if (path === "/api/v1/public/tenants/westernworld/enquiry-form") return json({ serviceInterests: services, countries: [] });
  if (path === "/api/v1/lead-imports/read") return json(preview);
  if (path === "/api/v1/lead-imports") {
    const dryRun = new URL(request.url).searchParams.get("dryRun") === "true";
    return json({ ...check, dryRun });
  }
  return json([]);
}

async function upload() {
  await renderAsStaff(<ImportPage />, roles.soloAdmin, backend);
  const file = new File(["x"], "Portal Data Pattern.xlsx");
  await userEvent.upload(await screen.findByLabelText("Spreadsheet file"), file);
  await screen.findByText(/2 rows/);
}

describe("Import leads", () => {
  beforeEach(() => {
    nav.path = "/import";
  });

  it("shows the suggested field for each column and matches values to the business's options", async () => {
    await upload();
    expect(screen.getByLabelText("Field for Name")).toHaveValue("FULL_NAME");
    expect(screen.getByLabelText("Field for Address")).toHaveValue("EXTRA");
    expect(screen.getAllByRole("button", { name: /^About / })).toHaveLength(preview.headers!.length);
    // "IELTS" fits one option; "Visa" fits two, so it is left as written.
    expect(screen.getByLabelText("IELTS")).toHaveValue("IELTS / PTE Coaching");
    expect(screen.getByLabelText("Visa")).toHaveValue("");
  });

  it("checks, then imports what the Admin chose", async () => {
    await upload();
    await userEvent.selectOptions(screen.getByLabelText("Visa"), "Student Visa");
    await userEvent.type(screen.getByLabelText(/Where these leads came from/), "Portal");
    await userEvent.click(screen.getByRole("button", { name: "Check" }));

    expect(await screen.findByText("Nothing has been saved yet.")).toBeInTheDocument();
    expect(screen.getByText(/Not a valid mobile number/)).toBeInTheDocument();
    const sent = requests.find((r) => r.path === "/api/v1/lead-imports?dryRun=true");
    expect(sent?.body).toMatchObject({
      sourceLabel: "Portal", columns: preview.columns,
      values: { SERVICE_INTEREST: { IELTS: "IELTS / PTE Coaching", Visa: "Student Visa" } },
    });

    await userEvent.click(screen.getByRole("button", { name: "Import 1 lead" }));
    expect(await screen.findByRole("link", { name: "Open leads" })).toBeInTheDocument();
    expect(requests.some((r) => r.path === "/api/v1/lead-imports?dryRun=false")).toBe(true);
  });

  it("needs a name and a mobile column before checking", async () => {
    await upload();
    await userEvent.selectOptions(screen.getByLabelText("Field for Mobile No."), "IGNORE");
    expect(screen.getByRole("button", { name: "Check" })).toBeDisabled();
    expect(screen.getByText(/Choose the column with the name/)).toBeInTheDocument();
  });

  it("is in the menu for an Admin, also without Team & branches", async () => {
    await renderAsStaff(<ImportPage />, roles.soloAdmin, backend);
    expect(within(screen.getByRole("navigation")).getByRole("link", { name: /Import leads/ })).toBeInTheDocument();
  });

  it("is not in a Receptionist's menu", async () => {
    await renderAsStaff(<p>leads</p>, roles.receptionist, backend);
    expect(within(screen.getByRole("navigation")).queryByRole("link", { name: /Import leads/ })).toBeNull();
  });
});

describe("lead-import helpers", () => {
  it("moves a single field to the column chosen last", () => {
    expect(chooseField(["FULL_NAME", "PHONE", "EXTRA"], 2, "PHONE")).toEqual(["FULL_NAME", "EXTRA", "PHONE"]);
    expect(chooseField(["EXTRA", "EXTRA"], 0, "EXTRA")).toEqual(["EXTRA", "EXTRA"]);
  });

  it("explains each field, and that a row-number column is not needed", () => {
    expect(columnHint("Sr.No.`", "IGNORE")).toMatch(/own lead number/);
    expect(columnHint("S. No", "EXTRA")).toMatch(/row number/);
    expect(columnHint("Mobile No.", "PHONE")).toMatch(/Required/);
    expect(columnHint("Address", "EXTRA")).toMatch(/column's name/);
    expect(columnHint("Number", "IGNORE")).toBe("This column is left out.");
  });

  it("matches only unambiguous values", () => {
    expect(guessValues(preview, preview.columns!, { SERVICE_INTEREST: services })).toEqual({
      SERVICE_INTEREST: { IELTS: "IELTS / PTE Coaching" },
    });
  });

  it("writes the rejected rows with their reason", () => {
    const csv = rejectedRowsCsv(preview, check.problems as never);
    expect(csv.split("\r\n")).toHaveLength(2);
    expect(csv).toContain('"Bad Phone","12345"');
    expect(csv).toContain('"Not a valid mobile number: 12345"');
  });
});
