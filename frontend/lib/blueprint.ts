import type { Schemas } from "./api";

/** What the onboarding form builds and the backend checks (`TenantBlueprint`). */
export type Blueprint = Schemas["TenantBlueprint"];
export type BlueprintStaff = Schemas["OnboardingStaff"];
export type BlueprintBranch = Schemas["OnboardingBranch"];

export const STEPS = ["Business", "Enquiry form & messages", "Branches", "Staff", "Review & go live"] as const;
export const REVIEW_STEP = STEPS.length - 1;

/** The default roles every new business starts with (backend identity/DefaultRoles). */
export const ROLES = ["Admin", "Branch Manager", "Counsellor", "Receptionist"] as const;

/** Features that exist today; later phases' gates are added here as they are built. */
export const FEATURES = [
  { code: "LEADS_CORE", label: "Lead capture", description: "Enquiries from every source, lead list, history, messages", required: true },
  { code: "TEAM_AND_STUDENTS", label: "Team & branches", description: "Branches, roles, assignment to counsellors, staff admin, dashboard", required: false },
] as const;

export function emptyBlueprint(): Blueprint {
  return {
    business: { name: "", slug: "", defaultRegion: "IN", timezone: "Asia/Kolkata" },
    settings: {
      leadNumberPrefix: "", serviceInterests: [], countries: [], senderName: "",
      welcomeEmail: true, welcomeWhatsApp: true, studentUpdates: true, features: ["LEADS_CORE", "TEAM_AND_STUDENTS"],
    },
    branches: [],
    staff: [],
  };
}

/** "Western World Visa Services" → "western-world-visa-services" (the backend's web-address rule). */
export function slugify(name: string): string {
  return name.toLowerCase().replace(/&/g, " and ").replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 50).replace(/-+$/, "");
}

/** Which step a backend problem belongs to, from its field ("staff[2].phone" → Staff). */
export function stepOfField(field?: string | null): number {
  if (!field) return REVIEW_STEP;
  if (field.startsWith("business")) return 0;
  if (field.startsWith("settings")) return 1;
  if (field.startsWith("branches")) return 2;
  if (field.startsWith("staff")) return 3;
  return REVIEW_STEP;
}

/** One option per line, blank lines dropped. */
export const lines = (text: string) => text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);

const HEADER = /name/i;

/**
 * Staff rows pasted from a spreadsheet (tab-separated, as Excel and Google Sheets copy) or CSV. Columns, in order:
 * Name, Mobile, Email, Role, Branch, Designation, Employee code. A header row is skipped. Missing cells stay empty;
 * the backend reports what is wrong, row by row.
 */
export function parseStaffSheet(text: string): BlueprintStaff[] {
  const rows = text.split(/\r?\n/).filter((r) => r.trim());
  if (rows.length === 0) return [];
  const delimiter = rows[0].includes("\t") ? "\t" : ",";
  const cells = rows.map((r) => r.split(delimiter).map((c) => c.trim().replace(/^"|"$/g, "")));
  const body = HEADER.test(cells[0][0] ?? "") && /mobile|phone/i.test(cells[0].join(" ")) ? cells.slice(1) : cells;
  return body.map(([fullName = "", phone = "", email = "", role = "", branch = "", designation = "", employeeCode = ""]) => ({
    fullName, phone, email, role: role || "Counsellor", branch, designation, employeeCode,
  }));
}

/**
 * What is sent: blank optional values left out, so the backend applies its defaults. Rows are never dropped, so a
 * problem's index ("staff[2]") is the same row the user sees.
 */
export function forSending(bp: Blueprint): Blueprint {
  const blank = (v?: string | null) => (v && v.trim() ? v.trim() : undefined);
  return {
    business: { ...bp.business, name: blank(bp.business?.name), slug: blank(bp.business?.slug) },
    settings: {
      ...bp.settings,
      leadNumberPrefix: blank(bp.settings?.leadNumberPrefix),
      senderName: blank(bp.settings?.senderName),
    },
    branches: (bp.branches ?? []).map((b) => ({ name: b.name, city: blank(b.city) })),
    staff: (bp.staff ?? []).map((s) => ({ ...s, email: blank(s.email), branch: blank(s.branch),
      designation: blank(s.designation), employeeCode: blank(s.employeeCode), joinedOn: blank(s.joinedOn),
      signinCode: blank(s.signinCode) })),
  };
}

/** The blueprint without sign-in codes: what may be kept in the browser's draft or saved to a file. */
export function withoutSigninCodes(bp: Blueprint): Blueprint {
  return { ...bp, staff: bp.staff?.map((s) => ({ ...s, signinCode: undefined })) };
}
