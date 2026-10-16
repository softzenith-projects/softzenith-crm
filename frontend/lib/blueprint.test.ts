import { describe, expect, it } from "vitest";
import { emptyBlueprint, forSending, lines, parseStaffSheet, slugify, stepOfField, withoutSigninCodes } from "./blueprint";

describe("slugify", () => {
  it("turns a business name into a web address", () => {
    expect(slugify("Western World Visa Services")).toBe("western-world-visa-services");
    expect(slugify("  A & B Overseas!! ")).toBe("a-and-b-overseas");
    expect(slugify("x".repeat(60))).toHaveLength(50);
  });
});

describe("stepOfField", () => {
  it("maps each backend problem to the step that fixes it", () => {
    expect(stepOfField("business.slug")).toBe(0);
    expect(stepOfField("settings.features")).toBe(1);
    expect(stepOfField("branches[1].name")).toBe(2);
    expect(stepOfField("staff[3].phone")).toBe(3);
    expect(stepOfField("staff")).toBe(3);
    expect(stepOfField(null)).toBe(4);
  });
});

describe("lines", () => {
  it("keeps one trimmed option per non-empty line", () => {
    expect(lines(" Study Visa \n\nIELTS\r\n")).toEqual(["Study Visa", "IELTS"]);
  });
});

describe("parseStaffSheet", () => {
  it("reads rows copied from a spreadsheet, skipping the header", () => {
    const sheet = "Name\tMobile\tEmail\tRole\tBranch\nAnuj Kumar\t98765 43210\tanuj@ww.test\tAdmin\tRohtak\nPriya\t9876543211";
    expect(parseStaffSheet(sheet)).toEqual([
      { fullName: "Anuj Kumar", phone: "98765 43210", email: "anuj@ww.test", role: "Admin", branch: "Rohtak", designation: "", employeeCode: "" },
      { fullName: "Priya", phone: "9876543211", email: "", role: "Counsellor", branch: "", designation: "", employeeCode: "" },
    ]);
  });

  it("reads CSV too, with or without quotes", () => {
    expect(parseStaffSheet('"Indu",9876543212,,Counsellor,"Hisar",Senior Counsellor,WW-7')[0]).toMatchObject({
      fullName: "Indu", branch: "Hisar", designation: "Senior Counsellor", employeeCode: "WW-7",
    });
    expect(parseStaffSheet("  \n")).toEqual([]);
  });
});

describe("forSending", () => {
  it("leaves blank optional values out but keeps every row, so problem row numbers match the screen", () => {
    const bp = emptyBlueprint();
    bp.business = { ...bp.business, name: " Western World ", slug: "" };
    bp.branches = [{ name: "", city: " " }];
    bp.staff = [{ fullName: "", phone: "", email: " ", role: "Admin", branch: "" }];
    const sent = forSending(bp);
    expect(sent.business).toMatchObject({ name: "Western World", slug: undefined, defaultRegion: "IN" });
    expect(sent.settings?.leadNumberPrefix).toBeUndefined();
    expect(sent.branches).toEqual([{ name: "", city: undefined }]);
    expect(sent.staff).toHaveLength(1);
    expect(sent.staff![0].email).toBeUndefined();
  });
});

describe("sign-in codes in a blueprint", () => {
  const bp = { ...emptyBlueprint(), staff: [{ fullName: "Anuj", phone: "9876543210", role: "Admin", signinCode: " 739164 " }] };

  it("are sent to the backend (trimmed)", () => {
    expect(forSending(bp).staff![0].signinCode).toBe("739164");
  });

  it("are left out of what the browser keeps or saves to a file", () => {
    const kept = withoutSigninCodes(bp);
    expect(JSON.stringify(kept)).not.toContain("739164");
    expect(kept.staff![0].fullName).toBe("Anuj");
    expect(bp.staff[0].signinCode).toBe(" 739164 "); // the form itself keeps it
  });
});
