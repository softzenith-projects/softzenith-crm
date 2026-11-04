# SoftZenith CRM

Multi-tenant CRM platform. Each customer business is a tenant; the first is **Western World Visa Services**
(study-abroad / visa consultancy), onboarded through the platform onboarding flow. Tenant differences are data
(settings, roles, branches), never code branches, so more businesses can be onboarded without code changes.

| App | Folder | Stack | Local port |
|---|---|---|---|
| REST API | `backend/` | Java 21, Spring Boot 4, PostgreSQL, Flyway, Spring Modulith | 8081 |
| Staff UI | `frontend/` | Next.js 16, Tailwind v4, shadcn/ui, SWR, typed client generated from the OpenAPI spec | 3000 |

Tenant websites are **not** in this repository. A tenant's own public site (e.g. Western World's, in
[softzenith-projects/westernworld-website](https://github.com/softzenith-projects/westernworld-website)) lives in its own repo and talks
to the CRM only through the public enquiry API (`/api/v1/public/tenants/{slug}/...`). Tenants without a site use the
CRM's hosted enquiry page at `/enquiry/<slug>`.

More docs: [CLAUDE.md](CLAUDE.md) (architecture rules, for developers and AI agents) ·
[HANDOFF.md](HANDOFF.md) (current status and next task) · [docs/flows.md](docs/flows.md) (flows, permissions, debugging) ·
[docs/onboarding.md](docs/onboarding.md) (onboarding a new business).

---

## Quick start: run everything with `.\dev` (Windows)

`.\dev` starts Postgres, Mailpit, the backend and both web apps in one go, each in its own window so logs stay visible.

**One-time prerequisites**

- **JDK 21** and **Node.js 20+** (with npm) on your `PATH`. Maven is not needed; the project ships `mvnw`.
- **PostgreSQL 16+** (developed on 18) with a database `crm` owned by `crm_app` / `crm_app`:
  ```sql
  create role crm_app login password 'crm_app';
  create database crm owner crm_app;
  ```
- **Mailpit** (local mail catcher, https://mailpit.axllent.org), the inbox at http://localhost:8025.
- Install the UI dependencies once:
  ```powershell
  cd frontend; npm install; cd ..
  ```

> Optional: to have `.\dev` also start a tenant website you have cloned next to this repo, copy
> `.dev-sites.example.json` to `.dev-sites.json` (not committed) and list it there, e.g.
> `[{ "Name": "Western World site", "Dir": "../westernworld-website", "Port": 3001 }]`.

> `scripts/dev.ps1` looks for Postgres in `%LOCALAPPDATA%\Programs\pgsql` (data in `%LOCALAPPDATA%\crm-pgdata`) and Mailpit
> in `%LOCALAPPDATA%\Programs\mailpit\mailpit.exe`. If yours are installed elsewhere, edit the paths at the top of
> [scripts/dev.ps1](scripts/dev.ps1), or start Postgres and Mailpit yourself and follow the manual steps below.

**Daily use** (from the repository root, in PowerShell)

```powershell
.\dev              # (re)start everything and wait until each service is up
.\dev status       # what is running
.\dev stop         # stop the backend, the staff UI and any tenant websites
.\dev stop -All    # also stop Postgres and Mailpit
```

Once it reports everything as running:

| What | URL |
|---|---|
| Staff UI (sign in here) | http://localhost:3000 |
| Public enquiry form (demo tenant) | http://localhost:3000/enquiry/demo |
| API docs (Swagger) | http://localhost:8081/swagger-ui.html |
| Email inbox (Mailpit) | http://localhost:8025 |
| A tenant website listed in `.dev-sites.json` | its port, e.g. http://localhost:3001 |
| Platform console (SoftZenith admins) | http://localhost:3000/platform — `softzenith` / `local-platform-admin` (dev only) |

The first backend start takes a minute or two (dependencies download, Flyway builds the schema, demo data is seeded).

## Run manually (any OS)

Use this on macOS/Linux, or if you prefer separate terminals. Start PostgreSQL (with the `crm` database above) and
Mailpit first (`mailpit`; SMTP on 1025). Then, one terminal each:

```sh
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # API on :8081
cd frontend && npm install && npm run dev                                # staff UI on :3000
```

The `dev` profile seeds a neutral demo tenant ("Demo Visas", slug `demo`) and enables phone-only sign-in.

## Signing in (development)

There is no OTP in dev: enter just the phone number at http://localhost:3000/login.

| Phone | Person | Role | Branch |
|---|---|---|---|
| 9000000001 | Anuj | Admin (Super Admin) | Rohtak |
| 9000000002 | Naman | Branch Manager | Rohini |
| 9000000003 | Natasha | Counsellor | Rohtak |
| 9000000004 | Front Desk | Receptionist | — |
| 9000000005 | Deepak | Branch Manager | Bahadurgarh |
| 9000000006 | Indu | Counsellor | Rohini |
| 9000000007 | Priya | Counsellor | Bahadurgarh |

Try it: submit the public enquiry form, then watch the welcome and alert emails arrive in Mailpit and the new lead
appear for the Receptionist and Admin.

## What works today (PRD Phase 1)

- Public enquiry form per tenant → creates a lead (a repeat enquiry attaches to the open lead, nothing is dropped).
- On a new lead: welcome **email** and welcome **WhatsApp (demo: logged, not sent)** to the enquirer, plus an alert
  email to staff whose role has the new-lead-alert permission (Admin and Receptionist by default).
- Staff sign in by phone, see the leads their role allows, and add walk-in / phone leads. Admin and Receptionist assign
  leads to counsellors (who get an email); Admin and Counsellors change status; only Admin reopens closed leads.
- Admin dashboard: leads per period by status, source and counsellor.
- Admin manages staff (with basic employee records) and branches.
- SoftZenith platform console to onboard new businesses without code changes.

Roadmap and the current task: [HANDOFF.md](HANDOFF.md).

## Working on the project

**Repository layout**

```
backend/     Spring Boot API (src/main/java/com/softzenith/crm/<module>; Flyway migrations in src/main/resources/db/migration)
frontend/    Staff UI (Next.js App Router); lib/api-schema.d.ts is generated, do not edit
docs/        Flows, onboarding runbook, doc generator
scripts/     dev.ps1 (behind .\dev)
```

**Rules of the road** (details in [CLAUDE.md](CLAUDE.md))

- Tenant isolation is non-negotiable: every tenant-scoped entity extends `TenantScopedEntity`.
- Check permissions, never role names (`@PreAuthorize("hasAuthority('LEAD_ASSIGN')")`).
- Schema changes only through Flyway migrations (`ddl-auto=validate`).
- Modules own their tables; talk to other modules through public types or events.
- After changing an API, regenerate the frontend types with the backend running: `cd frontend && npm run gen:api`.

**Git workflow**

- `main` is the release branch; `develop` is where continuous development lands. Never push straight to `main`.
- Branch from `develop` as `feat/<slug>` or `fix/<slug>`, use conventional commits
  (`feat: lead domain and history (T3, PRD Phase 1)`), and open a PR into `develop`.
- Never commit secrets. New settings go in the relevant `.env.example` with an empty value.

## Tests and verification

Run both before opening a PR:

```sh
cd backend && ./mvnw verify                                              # embedded Postgres + GreenMail, no Docker; enforces the coverage gate (~2 min)
cd frontend && npx tsc --noEmit && npm run lint && npm test && npm run build
```

Other useful commands:

```sh
./mvnw test -Dtest=LeadFlowTests          # one backend test class
npm run test:coverage                     # frontend coverage report
npm run e2e                               # Playwright browser journeys (Postgres must be running; `.\dev` is reused)
```

## Configuration (environment variables)

Nothing is required for local development; defaults point at the local Postgres and Mailpit.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | local `crm` | Postgres |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | `localhost:1025` (Mailpit) | SMTP for real email |
| `MAIL_FROM` | `no-reply@softzenith.local` | From address; display name is the tenant's |
| `FRONTEND_URL` | `http://localhost:3000` | Links in emails |
| `SUPABASE_URL` | placeholder | Supabase project for real phone-OTP sign-in |
| `PORT` | 8081 | API port |
| `CRM_PLATFORM_JWT_SECRET`, `CRM_PLATFORM_ADMIN_USERNAME`, `CRM_PLATFORM_ADMIN_PASSWORD` | dev values in `application-dev.yml` | Platform console; required with the prod profile |
| frontend `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY` | empty = dev login | see `frontend/.env.example` |

Full lists: `backend/.env.example`, `frontend/.env.example`. A tenant website's settings are in its own repo.

## Authentication

Production: staff sign in with **phone OTP through Supabase Auth**; the API only verifies the token. Tenant, role and
permissions come from our own tables (an admin adds staff by phone; the first sign-in links them).
Development (`dev` profile): `POST /api/v1/dev/login {phone}` issues a token without OTP. Never enable it in production.

## Onboarding another business

No code changes: a SoftZenith platform admin signs in at `/platform` and uses the wizard (tenant, settings, roles,
branches, invited staff). Runbook: [docs/onboarding.md](docs/onboarding.md). Its enquiry form is live at
`/enquiry/<slug>` straight away.

## Troubleshooting

- **Port already in use**: 8081 (API), 3000 (staff UI), and any tenant website port in `.dev-sites.json`. `.\dev` only stops processes from this repo and
  warns about anything else holding a port; stop that program or change the port.
- **Backend cannot connect to the database**: check Postgres is running on 5432 and the `crm` database / `crm_app` role exist.
- **No emails in Mailpit**: check Mailpit is running (`.\dev status`). The app keeps working without it, the sends are logged as failed.
- **Something failed**: every error response carries a `requestId`; search `backend/logs/crm.log` for it. More in
  [docs/flows.md](docs/flows.md) (debugging guide).
