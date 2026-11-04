# CLAUDE.md

Project context for Claude Code (and any other agent, see `AGENTS.md`). Read this, then `HANDOFF.md`, before doing anything else.

---

## What this project is

A **multi-tenant CRM platform** built by SoftZenith Softwares. Each customer business is a tenant; the
first tenant is **Western World Visa Services**, a study-abroad / student-visa consultancy (owner, 30 Sep 2026).
Tenants are created through the SoftZenith platform onboarding flow, never seeded; the dev profile seeds only a
neutral demo tenant ("Demo Visas", slug `demo`). The platform must stay generic (leads, contacts, accounts, tasks,
roles) so further businesses can be onboarded, with consultancy-specific objects (student, application, visa case,
documents) layered on as their phases arrive.

Requirements (held by the owner, not in the repo): *Western World PRD v1.0*, *Phase 1 HLD & LLD*,
*Data Model Proposal*, all dated 22 Sep 2026. Reference PRD phases in commits rather than restating them.

PRD phases: **1 Don't lose leads** → 2 Counsellors + branches + Student Master → 3 Tasks/feed/remarks
→ 4 Applications/documents/visa → 5 Automation/reporting. **Current: Phase 1 → 2** (REST backend + role-based UI; slice roadmap in `HANDOFF.md`).

**Golden rule (owner): keep everything centralised so any tenant can be onboarded without code changes.**
Tenant differences are data (tenant settings, roles/permissions, branches), never `if (tenant == ...)` code.

**This repo is the CRM only** (owner, 5 Oct 2026). A tenant's own website lives in its own repository (Western World:
`softzenith-projects/westernworld-website`) and connects only through the public enquiry API
(`/api/v1/public/tenants/{slug}/enquiries`, `/enquiry-form`). Keep that API backward compatible: a breaking change ships
as a new version (`v2`). Tenant-specific CRM configuration (settings, roles, blueprints) is still data here; tenant
website code is not.

---

## Ground rules

1. **Plan first, then one small task at a time.** The task list lives in `HANDOFF.md`. For anything larger
   than a task in that list, propose the breakdown to the owner before writing code. Do not build ahead
   of the current phase: later-phase objects are added as later migrations, not scaffolded now.
2. **Reuse open source; don't reinvent.** Prefer a mature library (Spring, Hibernate, Modulith, JobRunr,
   libphonenumber, Bucket4j, MapStruct, springdoc, Envers) over hand-rolled code. A new dependency is
   justified in `HANDOFF.md`.
3. **Update `HANDOFF.md` at the end of every task or session**, using the template below. It is how
   Claude Code, Codex, or a human picks up where the last one stopped.
4. **Git: commit or push only when the owner asks.** The repo is public on GitHub
   (`softzenith-projects/softzenith-crm`, owner account `codewithnmn`; remote and credential settings are pinned per
   repo so the owner's other account for another project is unaffected). Branches: `main` = releases,
   `develop` = continuous development. When asked: branch `feat/<slug>` / `fix/<slug>` from `develop`,
   conventional commits, PR into `develop`; `develop` → `main` only for a release. Never push to `main`
   unless the owner explicitly asks for it in that message (e.g. a README/docs publish to both branches).
5. **Never commit secrets.** New secrets go in `.env.example` with an empty value, noted in `HANDOFF.md`.
6. **Ambiguity:** if the owner is present, ask. If running unattended, pick the reading most consistent
   with the PRD, implement it, and record the assumption in `HANDOFF.md`.
7. **No live third-party spend or messages without the owner's go-ahead**: no real SMS/WhatsApp/email
   sends, no creating paid cloud projects. Test with the provider mocked at the boundary.

---

## Session Continuity Protocol

- Sessions may end on expiry or usage limits. On "the previous session expired, let's resume", or when
  given a pasted state: acknowledge without re-introducing yourself, read `HANDOFF.md`, ask only for
  information that is truly missing, keep answers concise, and confirm the current objective in one sentence.
- Continue from `HANDOFF.md` and the working tree. Do not re-plan, re-read the whole codebase, or redo
  finished steps.

---

## Stack

| Layer | Choice | Notes |
|---|---|---|
| Backend | Java 21, **Spring Boot 4.1.1**, Maven wrapper | REST, springdoc OpenAPI at `/swagger-ui.html` |
| Modules | **Spring Modulith** | Module boundaries verified by `ModularityTests`; cross-module events via the JPA event registry (outbox) |
| Database | **PostgreSQL 18**, Flyway | `ddl-auto=validate`; schema only through migrations |
| ORM | Spring Data JPA / Hibernate 7 | `@TenantId` discriminator multi-tenancy; lead history in `lead_activities` |
| Auth | **Phone + sign-in code** (our backend, AUTH1); Supabase Auth optional (email, AUTH2) | Code set by the tenant Admin, bcrypt hash only; our own HS256 token. Supabase JWTs verified via JWKS when `SUPABASE_URL` is set. Tenant/role/permissions come from our DB |
| Phones | libphonenumber | Everything stored as E.164 |
| Events | Spring Modulith event registry + `@Async @TransactionalEventListener` | Lead events → notifications after commit, re-delivered if incomplete |
| Email | Spring Mail (SMTP) + **Mustache** templates | Local: **Mailpit** (SMTP 1025, inbox http://localhost:8025) |
| WhatsApp | `WhatsAppSender` interface, `DemoWhatsAppSender` (logs, status DEMO) | No WhatsApp Business API yet |
| Jobs | JobRunr (when a delay is needed) | Welcome is currently immediate |
| Tests | JUnit 5, MockMvc, spring-security-test, **zonky embedded Postgres**, GreenMail, Awaitility, **JaCoCo** (coverage gate) | No Docker needed |
| Frontend tests | **Vitest** + Testing Library (jsdom), **Playwright** (browser journeys) | Fake backend: `frontend/test/api-mock.ts` |
| Frontend | **Next.js 16** (App Router, client components), **Tailwind v4 + shadcn/ui**, **SWR**, **openapi-typescript + openapi-fetch** | Role-based workspaces (see HANDOFF roadmap); `/api/*` proxied to the backend |

**Do not introduce:** Lombok, a Salesforce-style generic metadata/custom-object engine, a second backend
language, or a message broker before the Modulith event registry is outgrown.

---

## Commands

```bash
cd backend
./mvnw test                                              # full suite (embedded Postgres + GreenMail, ~2 min); coverage report → target/site/jacoco/index.html
./mvnw verify                                            # same + fails below the coverage gate (pom: coverage.lines / coverage.branches)
./mvnw test -Dtest=LeadFlowTests                         # one class
./mvnw test -Dtest='LeadEndpointTests$Assign'            # one endpoint's nested tests
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev    # API on :8081, seeded demo tenant, dev login on

cd frontend
npm run dev                                              # UI on :3000 (needs the backend running)
npm run gen:api                                          # regenerate lib/api-schema.d.ts from the running backend
npm test                                                 # Vitest unit + component tests (jsdom, fake backend), ~20 s
npm run test:coverage                                    # same + coverage report → coverage/index.html
npm run e2e                                              # Playwright browser journeys against the real app (Postgres must run; reuses `.\dev`)
npx tsc --noEmit && npm run lint && npm test && npm run build   # frontend verification
```

Mailpit (local email inbox): `"%LOCALAPPDATA%\Programs\mailpit\mailpit.exe"` → http://localhost:8025.
Platform console (dev): http://localhost:3000/platform, `softzenith` / `local-platform-admin` (`application-dev.yml`).
Dev sign-in phones (mock organisation in the `demo` tenant, `devdata/DevDataSeeder`): 9000000001 Anuj (Super Admin, Rohtak),
9000000002 Naman (Branch Manager, Rohini), 9000000003 Natasha (Counsellor, Rohtak), 9000000004 Front Desk (Receptionist),
9000000005 Deepak (Branch Manager, Bahadurgarh), 9000000006 Indu (Counsellor, Rohini), 9000000007 Priya (Counsellor, Bahadurgarh).
Dev builds sign in with the phone alone (dev login); every demo staff member's **sign-in code is `482917`** (phone + code login).
Start/stop everything locally: `.\dev`, `.\dev stop [-All]`, `.\dev status` (`dev.cmd` → `scripts/dev.ps1`).

Local Postgres (Windows dev machine) is a portable install with no service. Start it after a reboot:
`"%LOCALAPPDATA%\Programs\pgsql\bin\pg_ctl" -D "%LOCALAPPDATA%\crm-pgdata" -l "%LOCALAPPDATA%\crm-pgdata\server.log" start`
DB `crm`, user `crm_app`/`crm_app`, superuser `postgres`/`postgres`. Port 8080 is taken by another app, so the API uses 8081.

---

## Repository layout

```
backend/                     Spring Boot API
  src/main/resources/db/migration/   Flyway migrations (V1 tenancy+identity, V2 event_publication, ...)
  src/main/java/com/softzenith/crm/
    shared/       OPEN module: base entities, TenantContext + Hibernate resolver, errors, PhoneNumbers, OpenAPI
    tenancy/      Tenant
    identity/     roles/permissions, branches, staff users, security (JWT → staff → tenant), /api/v1/me
    onboarding/   TenantBlueprint → preview (trial run, rolled back) / goLive (tenant + settings + roles + branches + staff, one tx)
    platform/     SoftZenith platform admins (username + password, own JWT + filter chain), audit log, /api/platform
    devdata/      dev-profile seed data (neutral "Demo Visas" tenant, slug demo)
    lead/         Lead, LeadActivity, LeadService (single intake for every source), events, staff + public controllers
    notification/ Notifier (email/WhatsApp + notification_log), MessageTemplates, LeadNotifications (event listeners)
  src/main/resources/templates/notifications/   Mustache message templates shared by all tenants
frontend/                    Next.js
  lib/api-schema.d.ts   generated types (do not edit)   lib/api.ts  typed client + auth headers
  components/AppShell   nav + signed-in user (useMe, can)
  app/login  app/enquiry/[tenant]  app/(staff)/leads  app/(staff)/leads/[id]  app/(staff)/admin/{staff,branches}
  app/platform (SoftZenith console: login, businesses, tenants/new wizard; lib/platform.ts = its own client + session)
docs/onboarding.md          runbook: onboarding a business (docs/onboarding/westernworld.blueprint.json = starter file)
docs/go-live.md             ordered plan to staging + live (AimDesk, softzenithlabs.com subdomains), who does what
docs/flows.md               architecture, flows, permissions, debugging guide (generated by docs/generate_flows.py)
CLAUDE.md  AGENTS.md  HANDOFF.md  README.md
```

---

## Architectural rules

- **Tenant isolation is non-negotiable.** Every tenant-scoped entity extends `TenantScopedEntity`
  (`@TenantId`). Hibernate stamps `tenant_id` on insert and filters every query.
- **`TenantContext` must be set before the transaction opens.** Web requests: `StaffContextFilter` sets it.
  Public intake/jobs: `TenantContext.run(tenantId, ...)`. Cross-tenant platform work (onboarding, sign-in
  lookup): `TenantContext.callAsSystem(...)`. With no context, queries see nothing and inserts fail. Keep it that way.
- **Every tenant table:** `tenant_id` FK, `unique (id, tenant_id)`, composite FKs `(x_id, tenant_id)` for
  references to other tenant rows, audit columns (`created_at/by`, `updated_at/by`), `version`.
- **Fixed schema + one JSONB `custom_fields` column** per main entity for tenant-specific fields.
  Promote a custom field to a real column when every tenant needs it.
- **Permissions, never role names, in code.** `@PreAuthorize("hasAuthority('LEAD_ASSIGN')")`. Roles are
  per-tenant and editable; new permissions go in `Permission` with defaults in `DefaultRoles`.
- **Module boundaries:** a module owns its tables. Others use its public top-level types or react to its
  events (`LeadCreated`, `LeadAssigned`, ...). No cross-module repository access.
- **Phones:** `PhoneNumbers.toE164(raw, tenant.getDefaultRegion())` before storing or comparing.
- **Errors:** throw `NotFoundException` / `ConflictException` / `InvalidInputException` → RFC 9457 ProblemDetail.
- **Logging:** SLF4J; every line carries `[requestId|tenantId|userId]` (MDC, `shared/logging`). Log business events at INFO
  with ids, never raw personal data: wrap phones/emails in `Mask`. Error bodies carry `requestId`; grep `logs/crm.log` for it.
- **Never drop an enquiry.** Every source goes through `LeadService.intake(NewLead)`; a repeat enquiry from a person
  with an open lead is recorded on that lead (`REPEAT_ENQUIRY` activity), not duplicated.
- **Tenant differences are data.** `TenantSettings` (JSON in `tenants.settings`): lead-number prefix, enquiry-form
  options, sender name, welcome on/off. Add new per-tenant knobs there with safe defaults, not in code branches.
- **Who gets notified is decided by permissions**, e.g. new-lead alerts go to staff whose role has `LEAD_NEW_ALERT`.
- **Notifications:** listen to module events, never block the request; every attempt goes to `notification_log`.
  Message wording lives in `templates/notifications/*.mustache` (first line `Subject:` for email).
- **Frontend:** client components + SWR (`useSWR(key, () => unwrap(api.GET(...)))`); types only from the generated
  schema; permission checks in the UI are hints (`can(me, ...)`); the backend enforces them. Menu items appear only for modules that exist (no placeholder counts).
- **OpenAPI schema names must be unique**: DTO records with the same simple name in two controllers collide.
- No Lombok; records for DTOs; short Javadoc only where the "why" isn't obvious.

---

## Key flows (summary; full diagrams in `docs/flows.md`, read it before changing any of these)

- **Enquiry → lead**: public `POST /api/v1/public/tenants/{slug}/enquiries` → validation → tenant by slug → honeypot →
  `TenantContext` set → `LeadService.intake` (normalise phone, open-lead duplicate check → `REPEAT_ENQUIRY`, else
  per-tenant number, lead `NEW`, `CREATED` activity, publish `LeadCreated`) → commit → 201 with reference.
  Staff walk-in/phone leads (`POST /api/v1/leads`) use the same `intake`.
- **Notifications**: after commit, `@Async @TransactionalEventListener` in `LeadNotifications` sets tenant context from
  the event → welcome email + WhatsApp (demo) per tenant settings → alert email to staff with `LEAD_NEW_ALERT` →
  every attempt in `notification_log`. `LeadAssigned` → email to assignee. Student updates (assign/status/reopen, repeat-enquiry
  ack) follow `studentUpdates`; a repeat enquiry bumps the lead to the top and alerts its counsellor (or `LEAD_NEW_ALERT`
  staff). Unfinished events re-sent on restart.
- **Sign-in**: `POST /api/v1/auth/phone-code` (prod: phone + the code the tenant Admin set; 5 wrong codes lock the phone
  15 min, 20 failures per address per 10 min) → token listing only the memberships whose code matched (INVITED → ACTIVE)
  → `/api/v1/me/memberships` → pick tenant (`X-Tenant-ID`). Setting a new code ends sessions opened with the old one.
  Dev: `/api/v1/dev/login` (phone only). Supabase tokens (later: email, AUTH2) link INVITED rows by verified phone.
- **Every staff request**: JWT check → `StaffContextFilter` (active staff of the tenant, permissions from DB, set
  `TenantContext`) → `@PreAuthorize(permission)` → service/entity rules → tenant-filtered queries → context cleared.
- **Assign**: `PUT /leads/{id}/assignment` (`LEAD_ASSIGN`: Admin, Receptionist) → assignee must be enabled + assignable
  role → not CLOSED → `ASSIGNED` + activity + `LeadAssigned`. Optimistic lock → 409 on concurrent edit.
- **Status**: `POST /leads/{id}/status` (`LEAD_CHANGE_STATUS`: Admin, Counsellor) → NEW/CONTACTED/CLOSED only (ASSIGNED via assign),
  CLOSED needs reason, leaving CLOSED needs `LEAD_REOPEN`.
- **Dashboard**: `GET /leads/stats` (`REPORTS_VIEW`) → grouped counts of leads created in the period.
- **Staff onboarding**: `POST /users` (`USER_MANAGE`) → phone unique per tenant, role/branch in tenant → INVITED.
- **Tenant onboarding** (runbook `docs/onboarding.md`): platform admin signs in at `/platform` (`POST /api/platform/auth/login`,
  bcrypt, lock after 5 failures, 10 attempts/10 min per address) → wizard builds a `TenantBlueprint` → `POST
  /api/platform/tenants/preview` (validates, then a full trial run rolled back) → `POST /api/platform/tenants` (one
  system-context transaction: tenant + settings + default roles + branches + INVITED staff; nobody messaged) → audit row.
  The platform chain (`@Order(0)`, `/api/platform/**`) accepts only platform tokens; staff tokens never pass, and
  platform tokens open no tenant data. Tenants are never seeded outside the dev profile.

## Documentation

`docs/flows.md` is the team's map: architecture (apps, modules, request path, tenancy, data model), every flow, the
permission matrix, and a debugging guide (request-id tracing, error codes, runbook, code map, local run, settings, SQL).
Shareable page: https://claude.ai/artifact/KCuBRN6GoiU8LApZzPhkDs (private until the owner shares it).

When you change a user-visible flow, a permission default, or a status rule, update its section in
`docs/generate_flows.py` and regenerate: `python docs/generate_flows.py . <out.html>` (rewrites `docs/flows.md`).

---

## Verification loop

```bash
cd backend && ./mvnw verify
cd frontend && npx tsc --noEmit && npm run lint && npm test && npm run build
```

Must be green before a task is called done (for UI flow changes also `npm run e2e`). Never lower the coverage gate in
`backend/pom.xml`; raise it when coverage goes up. For API changes, also boot with the `dev` profile and hit
the endpoint. **Never** make a test pass by weakening an assertion, skipping it, or disabling a check.
If you cannot get it green, say so honestly in `HANDOFF.md`.

## Testing expectations

Four layers; when debugging, start at the narrowest one that fails:

| Layer | Where | What it proves |
|---|---|---|
| Unit | `*Test.java` next to the class (`LeadTest`, `AppUserTest`, `JwtClaimsTest`...); frontend `*.test.ts(x)` next to the file | One class's rules, no Spring/DB. Fastest place for a breakpoint. |
| Functional (per endpoint) | `*EndpointTests.java` (one `@Nested` class per endpoint); frontend page `page.test.tsx` with the fake backend in `test/` | Each endpoint/page: success, 401, 403, 400, 404, 409, per role. |
| Phase journey | `backend/.../journeys/Phase<N>*JourneyTests.java` | A PRD phase end to end across modules, written as numbered steps. |
| Browser | `frontend/e2e/*.spec.ts` (Playwright, dev seed data) | The real UI + API together, per role. |

A new endpoint gets functional tests; a new rule gets a unit test; a new PRD phase gets a `Phase<N>...JourneyTests`.

- Integration tests use `@IntegrationTest` (Spring Boot + MockMvc + embedded Postgres, one shared context).
  The DB is shared across tests, so create fresh tenants/phones via `TestData.slug()` / `TestData.phone()`.
- Every endpoint: happy path + auth failure (no token → 401) + permission failure (→ 403).
- Build test data with the `Fixtures` bean (`tenant()`, `staff(tenant, role)` → `.token()`).
- Emails: GreenMail on port 3025 (set in `@IntegrationTest`); async notifications: wait with Awaitility.
- Domain rules (status transitions, dedupe) get focused tests.
- Tenant isolation gets a test whenever a new tenant-scoped table appears.
- No test calls a live third-party API; mock at the boundary (WireMock, GreenMail).
- Constraint-violation ERROR lines in test logs are expected from negative tests.

---

## Product decisions (settled — implement, do not relitigate)

- Backend is Java + Spring Boot (the HLD's Next.js server actions were replaced). Frontend Next.js later.
- Default roles: **Admin, Branch Manager, Counsellor, Receptionist**, customisable per tenant (rename,
  add roles, regrant permissions, `data_scope` ALL/BRANCH/OWN, `assignable`).
- Lead visibility (owner, 26 Sep 2026; role `data_scope`, V7): **Admin and Receptionist** see every lead, a **Branch
  Manager** the leads of their branch (plus any assigned to them), a **Counsellor** only the leads assigned to them. A lead a
  counsellor adds stays unassigned (reception/admin assign it). All staff create/edit leads they can see.
- A lead ends either **converted** into a Student (generic Contact; follow-ups, documents and applications live there;
  roadmap S1) or **closed** as lost with a reason. Assignment to a counsellor does not end a lead.
- Staff sign in with **mobile number + a personal sign-in code** (owner, 8 Oct 2026; no SMS gateway, no DLT). **Only an
  Admin** (`USER_MANAGE`, never for a stronger role) sets or replaces codes; the first Admin's code is set at onboarding.
  A code belongs to one membership (tenant), so one tenant's Admin can never open another tenant's account for the same
  phone. Only a bcrypt hash is stored. Email sign-in via Supabase comes later (AUTH2); Supabase phone OTP is not used.
- Western World's production tenant slug is **`westernworldvisaservices`** (owner, 8 Oct 2026).
- Phase 1 access: **Admin and Receptionist** assign leads to counsellors
  (`LEAD_ASSIGN`); **Admin and Counsellor** change status (`LEAD_CHANGE_STATUS`, owner 26 Sep 2026); **only Admin** reopens a
  closed lead and sees the lead dashboard (`REPORTS_VIEW`).
- Changing a default role's permissions = update `DefaultRoles` (new tenants) **and** a Flyway migration that grants it
  to existing tenants' seeded (`is_system`) roles (see V3/V4).
- Lead statuses (PRD): `NEW`, `CONTACTED`, `ASSIGNED`, `CLOSED`. Closing needs a reason; Admin can reopen. A reopened
  lead goes back to the unassigned queue (owner, 30 Sep 2026).
- **Phone number is the identity** for staff login and students. Lead/student uniqueness is
  `(tenant_id, phone, email)` with a missing email counted as a value.
- Leads arrive from many sources (website form, Meta lead ads, WhatsApp, walk-in, phone, referral) via
  source adapters into one intake pipeline; the source is always retained.
- Welcome email + WhatsApp go out **immediately** on a new lead (owner, 25 Sep 2026; overrides the PRD's 30-min
  assumption). New-lead alert emails go to Admin + Receptionist (via `LEAD_NEW_ALERT`); assignee gets an email.
- WhatsApp is **demo only** (logged, status DEMO) until a WhatsApp Business API account exists.
- Staff records: name, phone (sign-in id), email, role, branch, employee code, designation, joined-on, status.
- Dev login (`crm.auth.dev-login.enabled`, `dev` profile only) lets the UI run without Supabase. Never in production.
- Out of scope (PRD): partner/agent portal, commissions, B2B payments, full accounting/ERP, staff mobile app.
- Package `com.softzenith.crm`. Rename when a product name exists.

---

## Commit and PR conventions

- Conventional commits: `feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`, with the task id:
  `feat: lead domain and history (T3, PRD Phase 1)`.
- End commit messages with the attribution line required by the environment.
- PR/commit description: what changed, what was assumed, what was not verified.

---

## HANDOFF.md template

Overwrite the "Session" part at the end of every task/session; keep the task table current.

```markdown
# Handoff — <date>

## Phase 1 task status
<table: task id, description, status (done / in progress / next / todo)>

## Session: <task id> — <one line>
### What I built
- <bullets with file paths>
### Assumptions I made
- <every ambiguity resolved, and how>
### What I could NOT verify
- <needs credentials, live providers, human judgement>
### Verification status
- ./mvnw test: <pass/fail + counts>; app boot/manual checks: <...>
### Git status
- <uncommitted / committed on <branch> at <sha>>

## Next action
- <one line, specific enough to start immediately>

## Open questions for the owner
- <genuine blockers only>
```

---

## Things that need a human, not an agent

Flag these in `HANDOFF.md` and move on:
- Creating or paying for cloud projects (Supabase, Meta, WhatsApp, SMS gateways) and entering credentials.
- India DLT registration for SMS OTP / templates; WhatsApp Business verification.
- Client-confirmation items from the PRD (alert recipients, welcome wording/timing, Day-1 sources, form fields).
- Final wording of any message sent to students.
- Anything touching production data or data-protection posture.
