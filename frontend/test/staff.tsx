import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { SWRConfig } from "swr";
import AppShell from "@/components/AppShell";
import type { Schemas } from "@/lib/api";
import { session } from "@/lib/session";
import { json, mockApi } from "./api-mock";

type Me = Schemas["MeResponse"];

const tenant = { id: "t-1", slug: "westernworld", name: "Western World" };
const rohini = { id: "b-rohini", name: "Rohini" };
const features = ["LEADS_CORE", "TEAM_AND_STUDENTS"];
const adminPermissions = ["BRANCH_MANAGE", "LEAD_ASSIGN", "LEAD_CHANGE_STATUS", "LEAD_CREATE", "LEAD_EDIT", "LEAD_NEW_ALERT",
  "LEAD_REOPEN", "LEAD_SOURCE_MANAGE", "LEAD_VIEW", "REPORTS_VIEW", "ROLE_MANAGE", "TENANT_SETTINGS_MANAGE",
  "USER_MANAGE", "USER_VIEW"];

/** Signed-in users with the backend's default permissions per role (identity/DefaultRoles + V3–V5 migrations). */
export const roles = {
  admin: {
    userId: "u-admin", fullName: "Anuj Admin", phone: "+919000000001", tenant, role: { code: "ADMIN", name: "Admin" },
    dataScope: "ALL", features, permissions: adminPermissions,
  },
  /** The Admin of a business whose plan has no Team & branches: they run it alone. */
  soloAdmin: {
    userId: "u-admin", fullName: "Anuj Admin", phone: "+919000000001", tenant, role: { code: "ADMIN", name: "Admin" },
    dataScope: "ALL", features: ["LEADS_CORE"], permissions: adminPermissions,
  },
  receptionist: {
    userId: "u-reception", fullName: "Front Desk", phone: "+919000000004", tenant,
    role: { code: "RECEPTIONIST", name: "Receptionist" }, dataScope: "ALL", features,
    permissions: ["LEAD_ASSIGN", "LEAD_CREATE", "LEAD_EDIT", "LEAD_NEW_ALERT", "LEAD_VIEW", "USER_VIEW"],
  },
  counsellor: {
    userId: "u-counsellor", fullName: "Natasha Counsellor", phone: "+919000000003", tenant, branch: rohini,
    role: { code: "COUNSELLOR", name: "Counsellor" }, dataScope: "OWN", features,
    permissions: ["LEAD_CHANGE_STATUS", "LEAD_CREATE", "LEAD_EDIT", "LEAD_VIEW", "USER_VIEW"],
  },
  branchManager: {
    userId: "u-manager", fullName: "Naman Manager", phone: "+919000000002", tenant, branch: rohini,
    role: { code: "BRANCH_MANAGER", name: "Branch Manager" }, dataScope: "BRANCH", features,
    permissions: ["LEAD_CREATE", "LEAD_EDIT", "LEAD_VIEW", "USER_VIEW"],
  },
} satisfies Record<string, Me>;

/**
 * Renders a staff page the way the app does: inside AppShell, signed in as `me`. `api` answers every other call;
 * /api/v1/me is answered with `me`. Each render gets a fresh SWR cache.
 */
export async function renderAsStaff(ui: ReactNode, me: Me, api: (request: Request, body: unknown) => Response = () => json({}, 599)) {
  session.setToken("test-token");
  session.setTenant(tenant.id);
  mockApi((request, body) => (new URL(request.url).pathname === "/api/v1/me" ? json(me) : api(request, body)));
  const result = render(
    <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
      <AppShell>{ui}</AppShell>
    </SWRConfig>,
  );
  await screen.findAllByText(me.fullName!);
  return result;
}
