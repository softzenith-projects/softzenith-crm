# Handoff — 2026-10-16 (GO0 plan: Western World live on staging today; earlier AUTH1, TRUST1)

Project rules, stack, conventions and settled decisions: **`CLAUDE.md`**. This file is the current state.

## Phase 1 task status

| # | Task | Status |
|---|---|---|
| T0 | Repo + Spring Boot skeleton, springdoc, ProblemDetail errors, Modulith boundary test | done |
| T1 | Flyway V1 (tenancy + identity), V2 (Modulith `event_publication`), onboarding, dev seed | done |
| T2 | Security + tenancy: Supabase JWT, `StaffContextFilter`, `@TenantId`, method security, `/api/v1/me` | done |
| T3 | Lead domain: `leads`, `lead_activities`, per-tenant lead numbers (`tenant_counters`), status rules, filters | done |
| T4 | Staff lead API: list/get/create/update, status, assignment, activities | done |
| T2b | Staff admin: onboard staff by phone with employee record, enable/disable, branches, roles list | done (roles are read-only in the API) |
| T5 | Public enquiry per tenant (`/api/v1/public/tenants/{slug}/...`), dedupe, honeypot | done (**no rate limit / captcha yet**) |
| T6 | Notifications: welcome email + demo WhatsApp, new-lead alert emails (`LEAD_NEW_ALERT`), assignment email, `notification_log` | done (immediate; no delay/JobRunr) |
| UI | Next.js basic UI: login (dev or Supabase OTP), leads list/detail/assign/status, add walk-in, staff admin, branches, public form | done |
| D1 | Receptionist can assign leads (`LEAD_ASSIGN` default + V4 migration); Admin lead dashboard (`REPORTS_VIEW`, `GET /api/v1/leads/stats`, `/dashboard`) | done |
| T7 | Meta Lead Ads (FB/IG) + WhatsApp inbound webhook adapters; real WhatsApp sender | todo (needs Meta/WhatsApp accounts) |
| T8 | Hardening: ~~Bucket4j rate limit + captcha on public form~~ (done in SEC1), Postgres RLS, role editing API/UI, tenant-settings API/UI, platform-admin tenant onboarding API | todo (rest) |
| SEC1 | Adversarial / security review fixes | done |
| SCOPE1 | Default data scopes (Counsellor OWN, Branch Manager BRANCH) + owner's mock organisation and mock leads in the dev seed; `dev.cmd` start/stop script | done |
| G1 | Feature gates: `FeatureGate` (subscription-plan entitlement, orthogonal to permissions) + `FeatureGateService`, wired into Phase 1/2 endpoints | done (no admin API to change a tenant's gates yet — deferred to T8) |
| REV1 | Code-review fixes: rate limits vs captcha order, one-open-lead check on edit/reopen | done |
| REOPEN1 | Reopened lead goes back to the unassigned queue (owner, 30 Sep) + `UNASSIGNED` activity; `LeadTest` unit tests | done |
| TEST1–6 | Test coverage programme (JaCoCo gate, unit gap-fill, per-endpoint functional tests, per-phase journey tests, frontend Vitest + Playwright) | done (owner approved 30 Sep) |
| WW1 | Western World is the first tenant (owner, 30 Sep); EaseMyViz removed everywhere (future B2B business of Western World); dev seed → neutral "Demo Visas" (`demo`) | done |
| PA1 | SoftZenith platform console: admin login (username+password), own security chain, audit; onboarding wizard (preview → go live) | done (v1: create only) |
| PA2 | Console: edit a live business (settings, feature gates, suspend), change admin password, more admins, TOTP | todo |
| AUTH1 | Staff sign-in: mobile + personal sign-in code set by the tenant Admin (owner, 8 Oct; no SMS); V9; codes at onboarding | done (this session) |
| TRUST1 | Visitor address behind a tenant website on another host (Vercel): shared-secret `X-Crm-Client-Ip` | done (this session; website `proxy.ts` uncommitted in its repo) |
| GH1 | Publish to GitHub (`codewithnmn/softzenith-crm`), `main` + `develop` branches, README rewrite for new developers | done (README first-time-setup section pending, see session) |

## Roadmap (owner-approved 25 Sep 2026, from the role-dashboard mockups)

Owner decisions: **Tailwind + shadcn/ui** replaces Pico; slice order **A → B → C → S → DOC**; documents in
**Supabase Storage** (local disk in dev, behind a `DocumentStorage` interface); tenant setup via a
**`tenants/<slug>.yml` import + admin settings UI** (DB stays the source of truth). Mockups are a direction, not a
spec: menu items appear only when their module exists (no fake counts).

| # | Task | Status |
|---|---|---|
| A1 | Tailwind v4 + shadcn/ui; new AppShell (dark sidebar built from permissions, top bar, user menu); port all existing pages off Pico | done |
| R1 | Repeat enquiries: lead moves to top (`last_enquiry_at`, `enquiry_count`), staff alert, student acknowledgement | done |
| N1 | Counsellor gets `LEAD_CHANGE_STATUS`; student email + WhatsApp on assign / status change / reopen | done |
| W1 | Western World website, all old content + URLs, every form → CRM public intake (tenant `westernworld` since 30 Sep) | done; **moved to `codewithnmn/westernworld-website`** (MOVE1) |
| W2 | Western World website redesign, announcement bar, News & blogs | done in `westernworld-website` (`develop`); status there |
| MOVE1 | Website moved out of this repo; the public intake API is the only contract; `.\dev` starts tenant sites from `.dev-sites.json` | done (branch `chore/extract-westernworld-website`) |
| L1 | Backend logging: request id, tenant/user on every line, access log, error logging, masked PII, rolling files, JSON in prod | done |
| A2 | Public enquiry page (layout done in A1); tenant branding in `TenantSettings` (logo, colour, tagline); hero copy from settings | next |
| A3 | Front desk workspace (Receptionist): quick enquiry capture, unassigned queue, one-click assign, scoped counts endpoint | todo |
| A4 | Tenant config file: `tenants/<slug>.yml` (branches, role names/scopes, form options, branding, templates) imported by onboarding/dev seed | todo |
| B1 | Enforce `data_scope` on leads (ALL / BRANCH / OWN); branch switcher for ALL-scope users; lead branch set at intake; scope tests | backend enforcement + tests done (SEC1); branch switcher, scoped stats, assignment within scope todo |
| B2 | Role dashboards on real scoped stats: Super Admin overview, Branch command centre, My counselling workspace, Front desk | todo |
| B3 | Branch Manager assigns within own branch; permission to hide academic/financial fields from Receptionist | todo |
| C1 | Remarks with @mentions (incl. mentioning the student → email/WhatsApp, owner 26 Sep); "Chatter & activity" panel (All / Remarks / System) merging `lead_activities` + remarks | todo |
| C2 | In-app notifications (bell) + mention email; `notification` module listens to `RemarkPosted` | todo |
| C3 | Tasks: create from a remark or record, assignee/due/priority, **depends-on** (blocked until prerequisite done), My tasks | todo |
| S1 | Student Master (PRD Phase 2): convert lead → student keeping history; student profile page with configurable journey stages | **next** (owner approved 26 Sep; propose the breakdown first) |
| S2 | Student portal: phone-OTP sign-in as a student identity, portal home (journey, actions, team), student-visible messages | todo |
| DOC1 | Document checklist per service (tenant config); `DocumentStorage` (local / Supabase Storage) | todo |
| DOC2 | Upload by student or counsellor, verify/reject by staff, events into the feed | todo |
| AUTH2 | Email sign-in via Supabase (email OTP / magic link; SES as its SMTP); link invites by verified email | todo (needs Supabase project + SES) |
| DEPLOY1 | Docker images (API, staff app), `docker-compose.yml` (Postgres, Caddy HTTPS), **staging + live env files** (staging: Mailpit as SMTP behind a password, Turnstile test keys), nightly `pg_dump` to S3 + tested restore. Plan: `docs/go-live.md` | staging done (16 Oct): `deploy/` (compose: Postgres 18, API, staff app as Next standalone, Mailpit, Caddy), `backend/Dockerfile`, `frontend/Dockerfile`; copy of backups off the server todo |
| DEPLOY2 | Staging (Lightsail 2 GB) then live (Lightsail 4 GB), Mumbai; `aimdesk[-staging][-api].softzenithlabs.com`; smoke check list in `docs/go-live.md` | todo (needs AWS account + DNS) |
| MAIL1 | Amazon SES: domain verification (SPF/DKIM/DMARC), production access | todo (needs AWS + DNS) |
| GO0 | Western World live on the **staging** server today (owner, 16 Oct): website + form → staging, Google Workspace SMTP, WhatsApp (Meta Cloud API templates) to student + Admin, Meta lead ads webhook. Beginner runbook: `docs/go-live.md` Part A. Code still needed: C0 commit, C1 packaging, C5 privacy page + consent line, C2 real WhatsApp sender, C3 Admin WhatsApp alert, C4 Meta lead ads webhook | plan written; code todo |
| GO1 | Rehearse on staging (`westernworld-test`), then onboard Western World on live (slug `westernworldvisaservices`), real branches + staff + codes, website pointed at `aimdesk-api`, end-to-end check | todo |
| BRAND1 | Product name **AimDesk** (owner, 9 Oct) in the UI (title, login, shell); Java package rename not planned | todo |
| later | Applications, offers, visa cases, IELTS/PTE, payments, reports (PRD Phases 4–5) | todo |

## Session (latest): GO0 — go-live runbook for today (owner, 16 Oct)

### What I did
- Rewrote `docs/go-live.md`: Part A = step-by-step for today (staging takes Western World's real leads), Mermaid
  diagrams (system, lead sequence, step dependencies), cost, troubleshooting; Part B = the longer staging/live plan,
  adjusted (staging holds real data until live exists, then data moves once).

### Findings worth knowing
- `westernworldvisaservices.com` DNS is **not** at Namecheap: nameservers `dns81/82.datanethostingsolutions.com`;
  A and MX both point at the old server `159.89.161.124` (MX = the bare domain), so moving A without first moving MX
  to `mail.` would break their email. Runbook step 8.3.
- `softzenithlabs.com`: Hostinger DNS, Google Workspace mail, no SPF yet. Owner wrote "softzenith.com" (NS1 DNS,
  not ours): owner confirmed softzenithlabs.com.
- Website has no privacy page (Meta needs one). WhatsApp templates are free text today; Meta needs approved
  templates for business-initiated messages (C2 maps kinds → template + params). Staff alert is email only (C3).
- Spring mail has no SSL/STARTTLS property set; Gmail needs 465 + SSL (C1 adds it).

### Decisions (owner, 16 Oct, later the same day)
- Website deployed to Vercel (Hobby) from `softzenith-projects/westernworld-website` `main`: https://westernworld-website.vercel.app (testing).
- Hosting (final for staging): Oracle had no free capacity in Mumbai, so **AWS EC2 free plan** (US$100 credits until 16 Apr 2027, project locked to Sydney): `t3.small` Ubuntu 24.04 x86, 2 GB + 2 GB swap, Elastic IP `54.253.207.84`, Docker 29 + Compose v5 + git installed, key `C:/Users/naman/.ssh/softzenith-staging.pem` (user `ubuntu`). DNS: `crm-staging`, `crm-staging-api`, `crm-staging-mail`.softzenithlabs.com point to that IP. `docs/staging-deploy.md` rewritten for this.
- First milestone plan: `docs/staging-deploy.md` (page https://claude.ai/artifact/3cfmzGG6jwtWYwMKeQcMhU): website form → staging DB → Admin sees it. Staging sign-in = existing mobile + code; Mailpit test inbox; no Turnstile/WhatsApp/Meta yet.
- Email sign-in (AUTH2) proposed as our own email one-time code over Google Workspace SMTP, no Supabase (not yet approved).
- Onboarding blueprint `docs/onboarding/westernworld.blueprint.json` still has slug `westernworld` and no staff: the wizard step changes it to `westernworldvisaservices` and adds the owner as Admin.

- DEPLOY1 (staging): `deploy/docker-compose.yml`, `Caddyfile` (API address exposes only `/api/v1/public/*` and `/actuator/health`; staff API only via the staff app), `setup-env.sh` (secrets generated on the server), `deploy.sh` (builds API then web, one at a time for 2 GB), `backup.sh` (nightly `pg_dump -Fc`, 7 days), `deploy/README.md`. `frontend/next.config.ts`: `output: standalone` only when `NEXT_OUTPUT=standalone` (Docker), so dev/e2e are unchanged. `.gitattributes` keeps scripts LF. Verified locally: frontend `tsc` only; the images are first built on the server.

### Open for the owner
- Domain confirmed: softzenithlabs.com (owner, 16 Oct). Still open: Western World email usage and old-hosting login; WhatsApp sender number; approve C0–C5.

## Session: AUTH1 + TRUST1 — phone + sign-in code; visitor address behind a proxy (owner, 8 Oct)

### What I built
- **AUTH1 (backend)**: `V9__staff_signin_codes.sql` (`app_users.signin_code_hash/_set_at`, phone index);
  `identity/SigninCodes` (6–8 digits, no repeated digit / straight run), `SigninCodeHasher` (bcrypt), `StaffSignIn`
  (phone + code across tenants in system context; only wrong codes count: 5/phone → 15 min lock, 20/address/10 min;
  unknown phone costs a dummy bcrypt), `security/CrmTokens` (HS256, issuer `crm`, 12 h, lists the proven
  `staff_user_ids`), `security/CodeSignInController` (`POST /api/v1/auth/phone-code`, 401 same message for every
  refusal), `security/StaffTokenDecoders` (one `JwtDecoder` that picks by issuer: `crm`, Supabase if `SUPABASE_URL`
  set, dev login in dev; replaces Boot's resource-server properties, removed from `application.yml`),
  `StaffDirectory.memberships(Jwt)` (code tokens open only their rows, still active, code unchanged since `iat`),
  `PUT /api/v1/users/{id}/signin-code` (`USER_MANAGE`, never for a stronger role, self allowed; response has
  `hasSigninCode`, `signinCodeSetAt`), blueprint `signinCode` per staff (validated, hashed at go-live, masked in
  `toString`, warning when no Admin has one), dev seed: every demo staff code `482917`.
- **AUTH1 (frontend)**: `lib/auth.ts` `authMode()` = `code` in production builds, `dev` otherwise, `supabase` when
  configured; `codeLogin`. Login page: sign-in code field (required in production, optional in dev). Staff page:
  Sign-in code column + Set/Change dialog (Generate, shown once with Copy). Wizard: code per staff row (Generate),
  never kept in the browser draft or the saved blueprint file (`withoutSigninCodes`). `lib/signin-code.ts` mirrors the
  backend rules. API types regenerated.
- **TRUST1**: `shared/web/TrustedProxyFilter` — with `X-Crm-Proxy-Secret` = `crm.trusted-proxy.secret`
  (`CRM_TRUSTED_PROXY_SECRET`, 32+ chars) the request's address becomes `X-Crm-Client-Ip` (rate limits, captcha);
  otherwise ignored. Website repo: `proxy.ts` strips browser copies of both headers and adds the secret
  (`CRM_PROXY_SECRET`) + visitor address (`x-real-ip` / `x-forwarded-for`); default tenant slug →
  `westernworldvisaservices`; README + `.env.example`.
- Docs: CLAUDE.md (stack, sign-in flow, decisions: codes, Admin-only, production slug; dev code), `docs/generate_flows.py`
  (Signing in rewritten + "who sets codes" table, runbook rows, settings) → `docs/flows.md`; `backend/.env.example`.
- Coverage gate raised to lines 0.98 / branches 0.89 (`backend/pom.xml`).

### Assumptions I made
- A code belongs to one membership (tenant), not to the person: otherwise one tenant's Admin could open another
  tenant's account for the same phone. The same code at two businesses opens both (the person picks).
- Lockout counters are in memory (one instance; reset on restart). Fine for one server; revisit with several.
- Signing in with a code turns INVITED → ACTIVE without linking an identity, so disable → enable returns such a user
  to INVITED until their next sign-in (existing `AppUser.enable` rule). Harmless; noted.
- Supabase phone OTP is dropped from the plan; the Supabase login-page path stays until AUTH2 replaces it with email.
- Only an Admin sets codes (owner); the first Admin's code is set in the platform wizard. A platform-side "reset the
  Admin's code" for a locked-out business is not built (PA2).

### What I could NOT verify
- Vercel's own `x-real-ip` / `x-forwarded-for` behaviour (verified locally against a header echo server: forged
  headers are replaced, the visitor address and secret arrive). Check once on a Vercel preview (GO1).
- In production the staff app's Next.js server must pass `X-Forwarded-For` from a private address so Tomcat trusts it
  (true for Docker on one host; verify in DEPLOY2).

### Verification status
- `./mvnw verify`: 250 tests, all green, coverage gate (0.98 / 0.89) met.
- Frontend: `tsc`, `lint` (0 errors, 2 old warnings), `npm test` 102 passed, `npm run build` ok, `npm run e2e` 8/8
  (incl. new `e2e/signin-code.spec.ts`). Website: `tsc`, `lint`, production build ok.
- Manual: `POST /api/v1/auth/phone-code` with 9000000001 + 482917 → token → Demo Visas membership; wrong code → 401.

### Git status
- Uncommitted on `chore/extract-westernworld-website` (CRM) and on `develop` (website). The owner's local backend on
  :8081 was restarted on the new code (V9 applied to the local database).

## Session: DOC2 — flows page refreshed, enquiry debugging, Supabase vs Postgres + RLS (owner, 8 Oct)

### What I did
- `docs/generate_flows.py`: Supabase drawn and described as **sign-in only, holds no CRM data**; runbook row "website
  said thank you but the lead is not in my list" (signed in to another tenant); `NEXT_PUBLIC_CRM_TENANT` must equal
  `tenants.slug`. Regenerated `docs/flows.md`; republished https://claude.ai/artifact/KCuBRN6GoiU8LApZzPhkDs (the live
  page still showed EaseMyViz / `sites/westernworld` from before WW1 and MOVE1).
- Website enquiry did not arrive: backend + Postgres were stopped, and the onboarded tenant's slug is
  `western-world-visa-services`, not the website's default `westernworld`. Fixed locally with a git-ignored
  `westernworld-website/.env.local` (`NEXT_PUBLIC_CRM_TENANT=western-world-visa-services`). Lead LD-000001 arrived.
- Evaluated Supabase (BaaS) vs our Postgres and RLS for the owner (answer in chat): keep Spring + Hibernate
  `@TenantId`; add Postgres RLS as defence in depth (T8); the production DB host (Supabase Postgres or other) is
  independent of that and still open.

### Findings worth knowing
- RLS is off on every table, and `crm_app` owns the tables and runs the app: RLS would need `FORCE ROW LEVEL
  SECURITY` or a separate migration owner role, or the owner silently bypasses it.

### Verification status
- Docs-only change; generator ran (34 sections). No code changed, so no test run.

### Git status
- Uncommitted on `chore/extract-westernworld-website`: `docs/generate_flows.py`, `docs/flows.md`, `HANDOFF.md`.

## Session: MOVE1 — Western World website moved to its own repo (owner, 5 Oct)

### What I did
- New repo `codewithnmn/westernworld-website` (created by the owner): the history of `sites/westernworld` extracted with
  `git subtree split` and merged in (`main` = the site as imported). The W2 redesign (announcement bar, News & blogs,
  services, success stories; never committed here) is on its `develop`, with its own README (CRM API contract,
  content guide), HANDOFF and CLAUDE. All W2 details and open questions are in that repo's `HANDOFF.md`.
- This repo: `sites/` removed (173 files). README, CLAUDE.md (new rule: this repo is the CRM only; tenant websites live
  in their own repos; keep the public intake API backward compatible), `docs/onboarding.md`, `docs/generate_flows.py`
  + regenerated `docs/flows.md`.
- `scripts/dev.ps1`: tenant sites are no longer hard-coded; `.\dev` starts those listed in the untracked
  `.dev-sites.json` (example: `.dev-sites.example.json`). The owner's local file points at `../westernworld-website` on 3001.
- Committed separately on the branch: the pending DEV1 dev-launcher fix and the GH1 branching rule in CLAUDE.md.

### Assumptions I made
- The onboarding blueprint `docs/onboarding/westernworld.blueprint.json` and the `westernworld` examples in onboarding
  code/tests stay: they are CRM configuration and data for the tenant, not website code.
- The shareable flows artifact (claude.ai) was not republished; `docs/flows.md` is regenerated.

### What I could NOT verify
- Backend and frontend suites not re-run: no backend or staff-UI code changed (only docs, the dev script and the removed folder).

### Verification status
- Website repo: fresh `npm ci`, lint, `tsc`, build (252 pages) pass; `main` and `develop` pushed.
  Its `package-lock.json` had drifted from `package.json` (npm ci failed); regenerated there.
- `.\dev status` reads `.dev-sites.json` and reports the site from `../westernworld-website`; site serving on :3001 from the new repo.
- `python -W error::SyntaxWarning docs/generate_flows.py`: 34 sections, no warnings; no `sites/westernworld` left in docs.

### Git status
- Branch `chore/extract-westernworld-website` pushed; PRs (owner request, opened as `codewithnmn`):
  #1 → `develop`, #2 → `main`. Not merged.

## Session: DEV1 — `.\dev` hung waiting for the backend (health gate)

### What I built
- `scripts/dev.ps1`: the Backend API readiness gate now polls `/actuator/health/readiness` instead of the aggregate
  `/actuator/health`. Root cause: the aggregate folds in Spring Boot's auto-configured `MailHealthIndicator`, so with
  Mailpit not listening on SMTP 1025 the endpoint returns **503**. `Test-Up` only accepts `StatusCode -lt 500`, so the
  script never saw the API as up and burned its full 240 s `Wait` (then the UIs) printing dots — the "stuck" symptom.
  The API itself was healthy throughout; `readiness` is also the semantically correct "is it serving traffic" probe.
- `scripts/dev.ps1` `Start-Infra`: Mailpit is now detected on port **1025** (the SMTP port the backend actually dials)
  rather than 8025 (inbox UI only), and the script waits up to 10 s for 1025 to open, warning if it never does.
  The "Mailpit not found" warning no longer claims the app keeps working unqualified.

### Assumptions I made
- The `liveness`/`readiness` health groups are available — confirmed empirically (both returned 200 while the
  aggregate was 503). No config change was needed to expose them.
- Losing the mail/db checks from the start-up gate is acceptable: Flyway and Hikari already fail the boot loudly if
  Postgres is wrong, and a missing Mailpit should not block a dev start.

### What I could NOT verify
- The no-Mailpit path end to end (Mailpit was installed and healthy on this machine). The 503 → readiness-200
  behaviour was reproduced directly against the running API, which is the mechanism the fix targets.

### Verification status
- Reproduced: with Mailpit down, `/actuator/health` = 503 `{"status":"DOWN"}` while `health/readiness` = 200 `UP`;
  starting Mailpit flipped the aggregate to 200 with nothing else changed.
- `.\dev` after the fix: all five services report `running` (Postgres, Mailpit, API 8081, Staff UI 3000, WW site 3001).
- Smoke test: `POST /api/v1/dev/login` (9000000001) → `GET /api/v1/me/memberships` → Demo Visas / ADMIN. 200s.
- Test suites not re-run: the change is confined to the dev launcher script, which no test covers.

### Git status
- Uncommitted on `develop`: `scripts/dev.ps1`, plus the pre-existing `CLAUDE.md` / `HANDOFF.md` edits.

## Session: GH1 — repo published to GitHub, branching model, README

### What I built
- Root `.gitignore` gained `build/` (Spring Modulith output) and safety-net patterns; the per-app `.gitignore` files already
  cover `node_modules`, `.next`, `target`, `logs`, `.env*` (except `.env.example`).
- Initial import committed; merged with GitHub's auto-created "Initial commit" (kept the local `README.md`).
  Remote `origin` = `https://codewithnmn@github.com/codewithnmn/softzenith-crm.git`. `develop` created from `main` and pushed.
- Repo-local git config pins the GitHub account (`credential.https://github.com.username=codewithnmn`, `useHttpPath`),
  so the owner's other account (`namankau`, used for another project) keeps working. Nothing global was changed.
- `README.md` rewritten: `.\dev` quick start, manual run, dev sign-in table, layout, git workflow, tests, config, troubleshooting.
- `CLAUDE.md` ground rule 4 now describes `main` / `develop` and the feature-branch flow.

### Assumptions I made
- `develop` is the integration branch (PRs target it); `main` only gets releases, or an explicit owner-requested publish.
- The repo stays public. It exposes dev-only credentials (`application-dev.yml`) and the Western World tenant name.

### What I could NOT verify
- A clean-machine run of the README steps (winget IDs, Postgres binaries link).
- Commit email: commits are authored `Naman <naman.kaushik06@gmail.com>`; if that email is not on `codewithnmn`, they will
  not link to that profile.

### Verification status
- No code changed; backend and frontend suites not re-run this session.

### Git status
- `origin/main` and `origin/develop` are both at `7654c9e` (README rewrite). Working branch: `develop`.
- Uncommitted: this `HANDOFF.md` and the `CLAUDE.md` rule change.
- Not done yet: the README "First time here? 6 steps" section (edits were blocked by a tooling error); redo it.

## Session: WW1 + PA1 — Western World first tenant; SoftZenith onboarding console

### What I built
- **WW1 rename**: EaseMyViz removed from code, tests, docs, memory; `sites/westernworld` defaults to tenant
  `westernworld`; dev seed is a neutral "Demo Visas" tenant (slug `demo`, prefix `DEMO`) with the same mock staff/leads.
  Local DB backed up (`pg_dump`, session scratchpad `crm-before-westernworld-2026-09-30.dump`) and recreated empty.
- **V8** `platform_admins` (bcrypt hash, failed attempts, lock) + `platform_audit` (append-only).
- **platform module**: `PlatformAuthService` (bcrypt via DelegatingPasswordEncoder, lock 15 min after 5 failures,
  10 attempts/10 min per address, same answer for every refusal, dummy hash for unknown users), `PlatformTokens`
  (HS256, iss/aud `crm-platform`, 4 h; decoder deliberately not a bean so the Supabase decoder stays),
  `PlatformSecurityConfig` (`@Order(0)`, `/api/platform/**`, login open, stale bearer ignored on login),
  `PlatformAdminBootstrap` (first admin from `crm.platform.bootstrap-admin.*`, never touches existing admins),
  `PlatformController` (`/auth/login`, `/me`, `GET /tenants`, `POST /tenants/preview`, `POST /tenants`).
- **onboarding**: `TenantBlueprint`, `BlueprintValidator` (all problems at once, per field; normalises phones, roles by
  code or name, branch names), `OnboardingPlan`, `TenantOnboardingService.preview` (trial run + rollback) / `goLive`
  (one system-context tx). `identity.OrganisationProvisioner` + tenant-id constructors on `Branch`/`AppUser`.
  `ProblemResponses` moved to `shared.web` (used by both security chains).
- **Frontend** `/platform` (not linked from the staff app, `noindex`): login, businesses list, onboarding wizard
  (5 steps, paste staff from a spreadsheet, save/load file, draft kept in the browser, review → go live, next steps).
  `lib/platform.ts` = separate client + session (never sends the staff token), `lib/blueprint.ts` helpers.
- **Docs**: `docs/onboarding.md` runbook, `docs/onboarding/westernworld.blueprint.json`, flows section
  "Onboarding a business", `CLAUDE.md`, `.env.example` (`CRM_PLATFORM_*`), dev creds in `application-dev.yml`.
- **Tests**: `BlueprintValidatorTest`, `PlatformAdminTest`, `PlatformTokensTest`, `PlatformOnboardingTests` (sign-in,
  lock, throttle, isolation both ways, preview changes nothing, go live → admin signs in, form works, `WWV-000001`);
  frontend `blueprint.test.ts`, `platform.test.tsx`, `OnboardingWizard.test.tsx`; e2e `platform-onboarding.spec.ts`.

### Assumptions I made
- v1 creates a business; editing a live one (settings, gates, suspend) is PA2. Default roles only (names per tenant later).
- Onboarding sends no messages (staff have no invite SMS yet; they sign in with their phone).
- Slugs `platform`, `admin`, `api`, `www`, `app`, `login` are reserved.
- Without `CRM_PLATFORM_JWT_SECRET` (non-prod) a random key is used; required with the prod profile.
- Suggested Western World services in the starter file are my reading of their website; to confirm.

### What I could NOT verify
- Production (none exists). The local dev DB now also holds one e2e test business (`e2e-<n>`) from the browser test.

### Verification status
- Backend `./mvnw verify`: 226/226, 98.9% lines / 90.9% branches, `ModularityTests` pass.
- Frontend: 87/87 Vitest; `tsc` clean; lint 0 errors (2 warnings: the 401 redirect in `lib/api.ts` and `lib/platform.ts`);
  `npm run build` OK; `npm run e2e` 6/6 (incl. onboarding a business in the browser). Starter file previews `ready`.

### Git status
- Uncommitted.

## Session: TEST1–6 — tests at every layer, and the bugs they found

### What I built
- **TEST1** JaCoCo in `backend/pom.xml`: `mvnw test` writes `target/site/jacoco/index.html`; `mvnw verify` fails below
  `coverage.lines` 0.97 / `coverage.branches` 0.88 (devdata seeder + `CrmApplication` excluded). Not on `test`, so running
  one class for debugging never trips it.
- **TEST2** backend unit tests: `LeadTest`, `AppUserTest`, `RoleAndBranchTest`, `JwtClaimsTest`, `TenantSettingsTest`,
  `PersistenceHelpersTest`, `MessageTemplatesTest` (+ `src/test/resources/templates/notifications/test-*.mustache`),
  `RequestLoggingFilterTest`, more `MaskTest`/`DevLoginTest` cases.
- **TEST3** backend functional tests, one `@Nested` class per endpoint: `StaffEndpointTests`, `LeadEndpointTests`,
  `PublicEnquiryEndpointTests`, `PublicEnquiryCaptchaTests` (own context, captcha on, no-token path only),
  `DevLoginFlowTests` (own context, real signed tokens), `ErrorHandlingTests` (+ test-only `ErrorTestController`),
  `NotificationRulesTests` (each tenant notification setting / recipient rule via the API), more `NotifierTests`
  and `LeadScopeTests` cases. `Fixtures.tenant(TenantSettings)` and `Fixtures.staffWithoutEmail` added.
- **TEST4** `journeys/Phase1LeadCaptureJourneyTests`, `journeys/Phase2TeamJourneyTests`: each PRD phase as numbered steps.
- **TEST5** frontend Vitest + Testing Library: `vitest.config.mts`, `test/setup.ts`, `test/api-mock.ts` (fake backend;
  `lib/api` captures fetch at import, so setup installs it first), `test/navigation.ts`, `test/staff.tsx` (render a page
  inside AppShell as a given role, with the backend's default permissions), `test/leads.ts`. Tests for `lib/api`,
  `lib/session`+`auth`, `components/common`, `AppShell`, `LeadForm`, and every page (login dev + Supabase OTP,
  enquiry, leads list, lead detail, staff, branches, dashboard).
- **TEST6** Playwright: `playwright.config.ts` (reuses a running `.\dev` stack or starts backend + UI), `e2e/helpers.ts`,
  `e2e/phase1-enquiry-to-counsellor.spec.ts`, `e2e/phase2-roles.spec.ts`.
- `CLAUDE.md`: commands, verification loop, the four test layers table.

### Bugs the new tests found (fixed)
- Backend `GlobalExceptionHandler`: Spring's own errors built after our override (405, 503...) had no `requestId` in the
  body. Now added after `super.handleExceptionInternal`.
- Frontend lead detail: after closing a lead, "Reopen" re-sent CLOSED (the status form kept its old state) → 409 until
  a page reload. Forms are now keyed on the lead's status/assignee. Regression test verified to fail without the fix.
- Frontend lead detail: a counsellor was offered "Reopen" on a closed lead, which always 403s. Now needs `LEAD_REOPEN`.
- Frontend `LeadForm`: the user's branch default was lost when the branch list loaded after the form mounted, so staff
  leads were saved with no branch (a Branch Manager's own walk-in could then drop out of their view).
- Frontend `Field`: labels were not linked to their inputs (no accessible names for screen readers). The label now wraps
  the control.

### New dependencies (all dev/test only)
- `org.jacoco:jacoco-maven-plugin` 0.8.13: standard Java coverage; the gate keeps coverage from silently dropping.
- `vitest`, `jsdom`, `@testing-library/{react,dom,jest-dom,user-event}`, `@vitest/coverage-v8`: the standard React
  component-test stack; Vitest compiles TSX itself, so `@vitejs/plugin-react` is not needed (it conflicts with
  shadcn's Babel 7).
- `@playwright/test` (+ its Chromium, `npx playwright install chromium`): browser tests of the real app.
- `@types/node` ^20 → ^24: Vitest 5 requires ≥22; the machine runs Node 24.

### Assumptions I made
- "Cover every piece of code" read as: every rule and branch that matters, with a gate at 97% lines / 88% branches,
  rather than literally 100% (getters, dev seed data). Remaining gaps are defensive (e.g. a staff name lookup for a
  user that no longer exists).
- E2E runs against the dev database and adds one lead per run (unique phone/name). The per-IP enquiry limit (30/h)
  caps back-to-back runs from one machine.

### What I could NOT verify
- CI: none exists yet; the commands are ready for one.
- Captcha with a real token (would call Cloudflare); only the no-token refusal is tested.

### Verification status
- Backend `./mvnw verify`: 191/191 pass, 99.2% lines / 91.2% branches, gate passes.
- Frontend `npm test`: 71/71 pass (91% lines); `tsc` clean; lint 0 errors (1 pre-existing warning in `lib/api.ts`);
  `npm run build` OK; `npm run e2e`: 5/5 pass against the running stack.

### Git status
- Uncommitted.

## Session: REOPEN1 — reopened leads return to the unassigned queue

### What I built
- `lead/Lead.changeStatus`: leaving CLOSED (to NEW or CONTACTED) clears `assignedTo/By/At`. Other transitions keep the
  counsellor (e.g. ASSIGNED → CONTACTED).
- `LeadActivity.Type.UNASSIGNED` (from = previous assignee id, note "Reopened"), written by `LeadService.changeStatus`;
  `LeadController` resolves its user ids to names like ASSIGNED. No migration needed: `type` is a plain varchar with no
  check constraint. The UI timeline renders it generically ("Unassigned").
- `LeadTest` (new, 10 unit tests on status/assignment rules); `LeadFlowTests` asserts the reopened lead is unassigned,
  hidden from the old counsellor, listed under `unassigned=true`, and has the UNASSIGNED activity.
- Docs: `CLAUDE.md` status rule, `docs/generate_flows.py` status flow + lifecycle, `docs/flows.md` regenerated.

### Coverage baseline (JaCoCo run from the command line, not in the pom yet)
- Backend: 85% lines (1293/1520), 66% branches (338/516). Frontend: no tests.
- Weakest: `DevDataSeeder` 0%, `DevLoginController` 0%, `StaffAdminService` 73% lines, `GlobalExceptionHandler` 17%
  branches, `AppUser` 29% branches, `LeadService` 69% branches, `LeadNotifications` 67% branches, `LeadFilter` 60%
  branches, `PublicEnquiryController` 50% branches.
- Re-run: `./mvnw org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.13:report`
  → `target/site/jacoco/index.html`.

### Assumptions I made
- Reopening to CONTACTED also unassigns (same rule as NEW); the student's "reopened" message never named the counsellor,
  so no template change.

### What I could NOT verify
- The shared flows page (claude.ai artifact) was not republished; `docs/flows.md` is current.

### Verification status
- `./mvnw test`: 85/85 pass.

### Git status
- Uncommitted.

## Session: REV1 — fixes from the /code-review pass

### What I built
- `lead/web/EnquiryRateLimiter`: `check` split into `checkClient(ip)` (before captcha) and `checkEnquiry(tenant, phone)`
  (after captcha). `PublicEnquiryController.submit` calls them in that order, so requests without a valid captcha can
  no longer drain a victim's per-phone bucket or the tenant-wide bucket (which would 429 real enquirers). The per-IP
  limit stays first so a flood can't make us call the Turnstile API unthrottled.
- `LeadService.requireNoOtherOpenLead`: `updateContact` (lead not CLOSED) and a reopen (CLOSED → NEW/CONTACTED) take
  the same per-person advisory lock as `intake` and refuse with a 409 naming the other open lead, instead of hitting
  `leads_open_person_uq` and returning the generic "Request conflicts with existing data".
- Tests: `EnquiryRateLimiterTest` rewritten for the split (+ per-phone case);
  `LeadFlowTests.editsAndReopensCannotCreateASecondOpenLeadForOnePerson`.

### Assumptions I made
- Review finding "client IP spoofable via X-Forwarded-For": not changed. `forward-headers-strategy: native` (Tomcat
  RemoteIpValve) trusts the header only from private-range proxies and takes the rightmost untrusted hop, so a client
  can't spoof it directly. Correct IPs in production still depend on the reverse proxy appending `X-Forwarded-For`;
  check this at deploy time.
- Review finding "reopen keeps the old assignee": not changed; it's a product decision (see open questions). ASSIGNED →
  CONTACTED/NEW already keeps the assignee by design, and clearing it would hide the lead from the counsellor (OWN scope).
- With no captcha secret configured (dev), unverified requests still count against the phone/tenant buckets, as before.

### What I could NOT verify
- Real Turnstile and a real reverse proxy in front of the API.

### Verification status
- `./mvnw test`: 75/75 pass (73 + `limitsEachPhoneWithinATenant` + the edit/reopen test).

### Git status
- Uncommitted.

## Session: G1 — feature gates (plan entitlements, orthogonal to permissions)

### What I built
- `shared/tenant/FeatureGate`: 5-value enum, one per PRD phase — `LEADS_CORE` (Phase 1), `TEAM_AND_STUDENTS`
  (Phase 2), `ENGAGEMENT`/`APPLICATIONS_DOCS`/`AUTOMATION_REPORTING` (Phases 3-5, reserved, no enforcement yet —
  nothing exists to gate).
- `shared/tenant/TenantFeatureLookup`: read-only interface in `shared` (an open module that depends on none of the
  others), implemented by `tenancy/TenantFeatureLookupImpl` — dependency inversion so `shared` can check entitlements
  without depending on `tenancy`. Mirrors how `identity/StaffLookup` is the public read API the other direction.
- `shared/tenant/FeatureGateService`: `hasFeature`/`requireFeature` against `TenantContext`'s current tenant;
  `requireFeature` throws the new `shared/web/FeatureNotEntitledException` → `GlobalExceptionHandler` maps it to a
  403 `ProblemDetail` (same infra as `NotFoundException`/`ConflictException`; distinct from a permission 403).
- `tenancy/TenantSettings.enabledFeatures` (`Set<FeatureGate>`, JSON in `tenants.settings`): compact constructor
  defaults a missing/`null` value to `{LEADS_CORE, TEAM_AND_STUDENTS}` — matches what's actually built today. This
  covers both new tenants (`Tenant`'s field initializer, `TenantOnboardingService.onboard`) and every tenant onboarded
  before this task (their stored JSON has no `enabledFeatures` key, so it deserializes as `null` and gets the same
  default) with **no Flyway migration needed** — same pattern this record already uses for its other fields.
- Wired `requireFeature` alongside the existing `@PreAuthorize` (not replacing it):
  `LEADS_CORE` — `LeadController.list/get/create`, `PublicEnquiryController.submit` (staff + public lead intake/list/detail).
  `TEAM_AND_STUDENTS` — `LeadController.stats/assign/changeStatus`, `OrganisationController.createBranch/updateBranch`,
  `StaffController.invite/update/disable/enable/resetSignIn`.
- Tests: `FeatureGateServiceTest` (unit, fake `TenantFeatureLookup`, no Mockito — matches this codebase's style),
  `FeatureGateEnforcementTests` (integration: a tenant with only `LEADS_CORE` gets 403 on stats/assign/status-change/
  staff-invite but keeps lead list/create; a tenant with only `TEAM_AND_STUDENTS` gets 403 on lead intake).
  `Fixtures.tenant(Set<FeatureGate>)` added for the restricted-tenant test; `Fixtures.tenant()` (used by every other
  test) is unchanged and still gets both default gates via `TenantSettings`'s own default, so nothing regressed.

### Assumptions I made
- Endpoint grouping follows the task brief literally: the *endpoint* is gated by the phase it belongs to, not by
  which permission the caller happens to hold. E.g. `POST /leads/{id}/status` is entirely behind `TEAM_AND_STUDENTS`
  even though Admin also uses it for plain NEW/CONTACTED/CLOSED transitions — CLAUDE.md's phase table lists "basic
  status" under Phase 1 but the task's explicit wiring list put `LEAD_CHANGE_STATUS` under `TEAM_AND_STUDENTS`
  (it only became counsellor-usable in Phase 2/N1). Flagging this in case the owner wants Admin's basic status
  changes carved out as `LEADS_CORE` separately — not done here to keep the diff minimal and match the brief.
  `LeadController.update` (`LEAD_EDIT`) and `/activities` were left ungated (not named in the brief).
- No admin API/UI to change a tenant's `enabledFeatures` yet (explicitly out of scope) — a tenant's plan can only be
  changed today by editing `tenants.settings` directly in the DB or via `Fixtures.tenant(Set<...>)` in tests. Tracked
  under T8 (tenant-settings API/UI) in the roadmap.
- Treated only `null` `enabledFeatures` (missing JSON key) as "use the default two gates"; an explicit empty set would
  be honoured as "no features" once something can actually set it — consistent with how `TenantSettings`'s other
  fields already only special-case `null`, not empty.

### What I could NOT verify
- No live tenant plan changes (no billing module exists to trigger one) — only exercised via the test fixture and
  directly via `TenantSettings`.

### Verification status
- `./mvnw test`: 73/73 pass (66 existing + `FeatureGateServiceTest` 4 + `FeatureGateEnforcementTests` 3), including
  `ModularityTests` (module boundaries) and the full existing suite unchanged.

### Git status
- Uncommitted.

## Session: SCOPE1 — who sees which leads, mock organisation

### What I built
- Owner rules (26 Sep 2026): Admin and Receptionist see every lead, Branch Manager their branch (+ assigned to them),
  Counsellor only leads assigned to them. `DefaultRoles` (new tenants) + `V7__default_data_scopes.sql` (existing
  tenants' system roles). Enforcement is the SEC1 code in `LeadService`.
- A lead a counsellor adds stays unassigned (owner); the Add-lead toast says it went to the unassigned queue instead of
  offering "Open" when the adder cannot see it (`frontend/app/(staff)/leads/page.tsx`).
- `Role.update(name, description, dataScope, assignable)` (also the start of role editing, T8).
- `devdata/DevDataSeeder` is now additive + idempotent (runs on every dev start): branches Rohtak / Bahadurgarh / Rohini
  (others deactivated); the demo tenant's Admin role labelled "Super Admin" and assignable (tenant data); staff upserted by
  phone: 9000000001 Anuj (Super Admin, Rohtak), …02 Naman (BM Rohini), …03 Natasha (Counsellor Rohtak), …04 Front Desk
  (Receptionist), …05 Deepak (BM Bahadurgarh), …06 Indu (Counsellor Rohini), …07 Priya (Counsellor Bahadurgarh);
  15 mock leads (`source_detail = 'Mock data'`, phones 9810000001–15) across the branches, assignees and statuses,
  created once through `LeadService` (so welcome/alert emails land in Mailpit).
- `dev.cmd` + `scripts/dev.ps1`: `.\dev` (stop app services, ensure Postgres + Mailpit, start API + both UIs in their
  own windows, wait until up), `.\dev stop [-All]`, `.\dev status`. Only stops processes from this repo.
- CLAUDE.md settled decisions + dev phones updated; `docs/flows.md` regenerated.
- Flow docs: new "customer journey" and "converting a lead to a student (planned, S1)" sections, CONVERTED in the lead
  lifecycle, data scope + proposed LEAD_CONVERT rows in the permission matrix (planned items are labelled as such).
  All 27 Mermaid diagrams parse (Mermaid 11). Shared page republished in place (version 3):
  https://claude.ai/artifact/KCuBRN6GoiU8LApZzPhkDs

### Assumptions I made
- One role per person (model): the owner chose "managers also counsel", so Branch Manager (already assignable) and the
  demo tenant's Admin role are assignable; a 6th mock counsellor, Priya, fills Bahadurgarh's second slot.
- Existing dev users with phones 9000000001–04 were renamed in place (their old test leads stay assigned to them).
- Lead stats (dashboard) stay tenant-wide; only Admin has `REPORTS_VIEW`.

### What I could NOT verify
- UI clicked through per role in a browser (verified through the API as each person instead).

### Verification status
- `./mvnw test`: all green (onboarding test now asserts the new per-role scopes; lead flow test checks a counsellor
  gets 404 for an unassigned lead). Frontend `tsc` + `lint` pass.
- Local DB after `.\dev`: leads visible per person (mock / total): Anuj 15/30, Front Desk 15/30, Naman 4/5 (Rohini),
  Deepak 4/4 (Bahadurgarh), Natasha 4/11 (older test leads were assigned to the old demo counsellor row), Indu 2, Priya 2.

### Git status
- Uncommitted.

## Session: DOCS — architecture + debugging guide

- `docs/generate_flows.py` extended (tables and code blocks per section) and regenerated `docs/flows.md` (31 sections):
  new **Architecture** group (system incl. Western World site, Modulith modules + events, request pipeline, tenancy,
  ER data model, website-enquiry sequence, both front ends), new **data scope** flow, counsellor status change in the
  use-case diagram, new **Debugging** group (request-id tracing + log anatomy, error codes, symptom runbook, code map,
  running locally, settings, SQL). All 25 Mermaid diagrams render (checked in Chrome with Mermaid 11).
- Shareable page updated in place: https://claude.ai/artifact/KCuBRN6GoiU8LApZzPhkDs (version 2, private).
- Regenerate after flow changes: `python docs/generate_flows.py . <out.html>`, then republish the page.

## Session: SEC1 — fixes from the adversarial / security review

### What I built
- **Public intake abuse** (`lead/web/PublicEnquiryController`): Bucket4j limits in a bounded Caffeine cache
  (`EnquiryRateLimiter`: per IP 5/min + 30/h, per tenant+phone 5/h, per tenant 1000/h, `crm.public-intake.rate-limit.*`)
  → 429 + `Retry-After` (`TooManyRequestsException`); Cloudflare Turnstile (`CaptchaVerifier`, on only when
  `TURNSTILE_SECRET` is set; widget `components/Turnstile.tsx` in both frontends when `NEXT_PUBLIC_TURNSTILE_SITE_KEY`
  is set); names limited to letters/spaces/`.'-` (`NewLead.NAME_PATTERN`, also on staff create/update); the response is
  the same for new and repeat enquiries and carries **no lead number** (field removed; both thank-you screens updated).
- **Message injection**: `shared/text/Text` strips control characters from lead text on save, and `MessageTemplates`
  cleans every template value (only `message` keeps line breaks), so a name cannot break out of an email subject. The
  welcome only repeats service/country if they are one of the tenant's form options; a `firstName` that is not
  name-like (e.g. a domain) becomes "there".
- **Enquirer bombing / duplicate sends** (`notification/Notifier`): at most `crm.notifications.enquirer-daily-cap` (5)
  messages of one kind per recipient per 24 h (the rest logged `SKIPPED`); every lead event now carries `eventId`, stored
  in `notification_log.event_id` (V6), and an already-delivered (event, channel, kind, recipient) is not re-sent on
  re-delivery. Async sends capped at 16 concurrent (`spring.task.execution.simple.concurrency-limit`).
- **Sign-in**: `JwtClaims.verifiedPhone` trusts the phone only if the token's `amr` shows an OTP/SMS sign-in (dev login
  now adds `amr`). `StaffDirectory` links open invites for that phone on every OTP sign-in (second tenant / re-invite
  now works). New `POST /api/v1/users/{id}/reset-sign-in` (USER_MANAGE) + "Reset sign-in" menu item on Staff.
- **Privilege escalation** (`StaffAdminService`): you can only grant, or manage users holding, roles whose permissions
  you have; nobody changes their own role (409) or resets their own sign-in.
- **Data scope (B1 backend)**: `LeadService.get/search` apply the role's scope (ALL; BRANCH = their branch or assigned to
  them; OWN = assigned to them); everything that loads a lead (edit, status, assign, activities, notifications)
  inherits it → 404.
- **Double submit**: `LeadRepository.lockPerson` (`pg_advisory_xact_lock` on tenant+phone) serialises intake, so
  concurrent identical submissions become one lead + repeat enquiries instead of a 409.
- **Dev login** refuses to start with the `prod` profile. **Prod config** (`application-prod.yml`): DB_* and
  SUPABASE_URL have no fallbacks; Swagger/api-docs off. **Event retention**: `spring.modulith.events.completion-mode: delete`.
- **Website** (`sites/westernworld/next.config.ts`) proxies only `/api/v1/public/tenants/:slug/{enquiry-form,enquiries}`.
- **Frontend**: the token is taken from `supabase.auth.getSession()` per request (auto-refresh, no hourly sign-out;
  `lib/supabase.ts`); CSP, X-Frame-Options, nosniff, Referrer-Policy, Permissions-Policy (+HSTS in prod) on both apps.
- `customFields` bounded (50 keys, 64-char keys, ~10 KB); `NotFoundException` cleans ids before they are logged.
- `server.forward-headers-strategy: native` so the client IP (rate limit) comes from X-Forwarded-For of trusted proxies.
- New dependencies: `com.bucket4j:bucket4j_jdk17-core` 8.20.0 (rate limits, as planned in T8) and Caffeine
  (Boot-managed; bounded bucket store). New env: `TURNSTILE_SECRET` (backend), `NEXT_PUBLIC_TURNSTILE_SITE_KEY` (both
  sites); added `backend/.env.example` and `sites/westernworld/.env.example`.
- Tests: `PublicIntakeSecurityTests`, `LeadScopeTests`, `NotifierTests`, `EnquiryRateLimiterTest`, `CaptchaVerifierTest`,
  `DevLoginTest`, `TextTest`, `FirstNameTest`, new cases in `MeControllerTests` / `StaffAdminTests`;
  `TestData.otpToken` builds Supabase-shaped OTP tokens. `@IntegrationTest` lifts the per-IP/per-tenant limits (all
  tests share 127.0.0.1); the per-phone limit stays real. `docs/flows.md` regenerated.

### Assumptions I made
- Rejecting free-text service/country at intake would drop website enquiries (the Western World form sends free text),
  so they are stored as before and only not echoed to the enquirer.
- Dropped the "last active user-manager" check I first wrote: via the API the caller always holds USER_MANAGE and cannot
  act on themselves, so "no own-role change + no managing stronger roles" already protects the last admin.
- A staff "create lead" that hits an existing open lead outside the caller's scope still records the repeat and returns
  that lead (the person is at the desk). Lead stats (`REPORTS_VIEW`) stay tenant-wide.
- Rate limits are per instance (in memory); move them to Bucket4j JDBC/Redis before running several instances.
- Event payloads still carry enquirer details until the notification completes (then deleted); slimming events to ids
  was not done.

### What I could NOT verify
- Turnstile against Cloudflare (needs a site key/secret: owner action); only mocked at the HTTP boundary.
- Supabase `amr` shape on a real project (coded for `[{"method":"otp"}]` and RFC 8176 strings), and token refresh in the
  browser with a real Supabase session.
- CSP in a real browser session (builds pass; pages not clicked through). The Google Maps iframe and Turnstile are allowed.

### Verification status
- `./mvnw test`: 66 tests, 0 failures. New build booted with the dev profile on :8082 (V6 applied to the local DB):
  enquiry → same message for new/repeat, no reference; a URL as the name → 400; rapid enquiries from one IP → 429 after 5
  a minute. Frontend `tsc`, `lint` (one existing warning), `build` pass; Western World `tsc`, `lint`, `build` pass.
- `frontend/lib/api-schema.d.ts` regenerated from the new backend.

### Git status
- Uncommitted (nothing in the repo is committed yet).

## Session: L1 — backend logging

### What I built
- `shared/logging/RequestLoggingFilter`: first filter; request id (keeps a well-formed incoming `X-Request-Id`, else
  generates one), returned in the `X-Request-Id` header; one access line per request (`crm.access`: method, path,
  status, ms; no query strings); unhandled exceptions logged with stack trace.
- MDC keys `requestId`, `tenantId`, `userId` (`LogContext`) on every line via `logging.pattern.level`
  (`[request|tenant|user]`). Tenant/user set in `StaffContextFilter` and on public intake; notification listeners set
  the tenant. The request id follows into `@Async` notification threads (`AsyncConfig` registers an SLF4J accessor with
  Micrometer context-propagation, which Spring Boot's executor already applies).
- `GlobalExceptionHandler`: every problem body has `requestId`; 4xx logged as one line; integrity violations log the
  constraint name + SQL state only; **new catch-all** → 500 "quote request id …" + ERROR with stack trace (Spring
  Security exceptions are rethrown so 401/403 still work). Filter-written errors (`ProblemResponses`) carry it too.
- Security: 401 (with the token-rejection reason, log only), 403 and "not staff of this tenant" are logged.
- Business events at INFO: lead created / repeat enquiry / updated / status change / assignment / refused reopen or
  assignment; honeypot drops; inactive tenant; staff invited/updated/enabled/disabled; branch created/updated; every
  email/WhatsApp sent or failed; listener failures (ERROR, event left for re-delivery).
- `shared/logging/Mask`: phones `+91******3210`, emails `a***@mail.com` in all logs. Demo WhatsApp no longer logs
  the message text (it is in `notification_log`).
- Config: `logs/crm.log` (`LOG_FILE`), rolling 20 MB / 30 days / 2 GB cap (gitignored). `application-prod.yml`:
  JSON (ECS) console + file, MDC keys as fields.
- New direct dependency `io.micrometer:context-propagation` (version from Boot; it was already on the runtime classpath
  via spring-modulith-observability) — needed to compile the MDC propagation.
- Tests: `LoggingTests` (request id header/echo/rejection, 500 with request id + stack trace in log, 404/403 bodies,
  tenant/user in log line, masked phone, request id in the async notification line), `MaskTest`;
  test-only `FailingTestController` at `/api/v1/public/test-only/boom` (test sources only).

### How to find a problem
- A user reports an error → the response (and error body) has `requestId` → `grep <requestId> logs/crm.log` shows the
  request, the business events, background notifications and any stack trace.
- More detail: `logging.level.com.softzenith.crm=debug` (dev already), SQL: `logging.level.org.hibernate.SQL=debug`.

### Assumptions / not verified
- Log shipping (Loki/ELK/CloudWatch) and alerting on ERROR are not set up; choose with hosting.
- The website server does not forward the visitor's own request id yet (it could send `X-Request-Id`).

### Follow-up (same day)
- Removed the old site's tawk.to live chat from `sites/westernworld` (owner): chats went to an outside account and never
  became CRM leads. WhatsApp button moved to bottom right. A tawk.to webhook → CRM intake adapter can be added later with T7.

### Verification status
- `./mvnw test`: 43/43 pass. Live dev check: enquiry via the website proxy (DEMO-000008), bad token (401), invalid
  body (400) all traced in `backend/logs/crm.log` with request ids; no full phone/email in our log lines.

## Session: R1 + N1 + W1 — repeat enquiries, student updates, Western World website

### What I built
- **R1** `V5__repeat_enquiries_counsellor_status.sql`: `leads.last_enquiry_at` + `enquiry_count` (backfilled from
  REPEAT_ENQUIRY history), index; `LeadRepository.recordRepeatEnquiry` (atomic JPQL bump that skips the version, so a
  public enquiry never fails on a concurrent staff edit); `LeadEvents.LeadRepeatEnquiry`; lead list sorted by
  `lastEnquiryAt`; `LeadResponse.lastEnquiryAt/enquiryCount`; public response says "added to your existing enquiry".
  UI: "Repeat ×N" badge, "Last enquiry" column, lead header shows the count.
- **N1** `LeadEvents.LeadStatusChanged`; `LeadNotifications` now sends: student update on assign (names the counsellor),
  CONTACTED, CLOSED (never the internal reason), reopen; repeat-enquiry alert to the assignee (or `LEAD_NEW_ALERT` staff
  when unassigned) + student acknowledgement. New `NotificationLog.Kind`s: `REPEAT_ENQUIRY_ALERT`, `REPEAT_ENQUIRY_ACK`,
  `LEAD_STATUS_UPDATE`. Templates `lead-status-*`, `repeat-enquiry-*`. Tenant setting `notifications.studentUpdates`
  (default true). Counsellor role gets `LEAD_CHANGE_STATUS` (`DefaultRoles` + V5); reopening stays `LEAD_REOPEN`.
- **W1** `sites/westernworld/` (Next.js 16 + Tailwind v4, own app): home, about, contact, visa assistance, blog, 8
  country pages, 50 university pages, IELTS General/Academy, PTE, TOEFL, UKVI, online + classroom courses, IELTS/PTE
  class hubs + 79 + 79 city pages, thank-you page (235 pages). Content generated from the old site into
  `content/*.json` (+ `content/copy.ts`); 118 images in `public/`. Old URLs redirect (308). Every form (Keep in touch,
  Call back, Request call back, Book Now, Enroll Now, trial class, newsletter) → `POST /api/v1/public/tenants/:slug/
  enquiries` with `sourceDetail` naming the form → `/thank-you` ("someone from our team will get in touch"). See its README.
- Docs: `docs/generate_flows.py` (permission matrix, status flow/state diagram, repeat enquiry, new
  "Keeping the student informed" flow) regenerated.

### Assumptions I made
- Website forms feed the **Western World** tenant (owner, 30 Sep) via `NEXT_PUBLIC_CRM_TENANT`; the site is a separate app, not
  CRM code, so the CRM stays generic.
- Student updates cover assignment too (it is how a status becomes ASSIGNED). NEW without a reopen sends nothing.
- A repeat enquiry sends the student an acknowledgement, not the full welcome again.
- Newsletter "Subscribe" opens the contact form with the email filled in (owner).
- Old-site quirks fixed, not copied: "Book Now" on the home page pointed to Study in Singapore, "Read More" on online
  courses pointed to a 404 page, visa-assistance page was titled UKVI-IELTS, favicon was an HTML page; lorem ipsum
  subtitles dropped; "TOEFL 2022" → "TOEFL". Obvious typos fixed (Graducation, Addhar, Sectin, Rohitak).
- Existing tests updated for the owner's rule change (counsellor may change status): `LeadFlowTests`,
  `TenantOnboardingTests` (now also asserts counsellors cannot reopen).

### What I could NOT verify
- Tawk.to chat on the real domain (blocked on localhost). Real WhatsApp/SMTP delivery (Mailpit + demo only).
- Final wording of the student update / repeat messages (placeholder, needs the client).
- Hosting of the website (domain, where it runs, `CRM_API_URL` in production) — not set up.
- Mermaid diagrams were regenerated but not re-validated with the mermaid parser this time.

### Verification status
- `./mvnw test`: 37/37 pass (2 new tests: repeat enquiry, counsellor status + student notifications).
- CRM frontend: tsc, lint (1 accepted warning), build OK. Website: tsc, lint clean, build OK (235 pages).
- Browser E2E (Playwright + Chrome, dev backend): 10 legacy URLs redirect correctly; Keep in touch → thank-you with
  reference DEMO-000007 → lead in CRM with source detail; admin assigns, counsellor sets CONTACTED; repeat enquiry from
  the contact page lands on the same lead; notification log has welcome, status update, repeat ack (email + WhatsApp)
  and repeat alert; CRM list shows the repeat badge; mobile layouts checked. No console errors except Tawk on localhost.
- Dev DB now contains test leads DEMO-000005..7.

### Git status
- Uncommitted on `main` (nothing committed in the repo yet).

## Session: A1 — Tailwind + shadcn/ui shell

### What I built
- Removed Pico; added Tailwind v4 (`postcss.config.mjs`) and shadcn/ui (`components.json`, style `base-nova` on Base UI,
  `components/ui/*`, `lib/utils.ts`). Theme tokens in `app/globals.css`: blue primary, navy sidebar.
- `components/AppShell.tsx`: dark sidebar whose menu is a `NAV` list filtered by permission (only existing modules),
  branch indicator, user menu (enquiry form link, sign out), top bar with lead search (→ `/leads?q=`) and "Add lead"
  (→ `/leads?new=1`), mobile slide-in menu.
- `components/common.tsx` (PageHeader, StatusBadge, Field, ErrorText, Initials, `humanize`), `components/LeadForm.tsx`
  (staff lead entry, reused by the A3 front desk).
- Pages ported: login (split screen, demo-user shortcuts in dev), dashboard (stat cards, pipeline/source bars, team
  workload; users without `REPORTS_VIEW` are redirected to `/leads`, `/` now goes to `/dashboard`), leads list (table
  card, add-lead dialog, toast), lead detail (profile header + info strip, activity timeline, actions, messages sent),
  staff (table + row menu + dialog), branches (cards), public enquiry (hero + form card).
- `lib/api.ts` `formatDate` uses medium date + short time.

### Assumptions I made
- The enquiry-page hero text is generic for now ("Tell us how we can help"); A2 moves it into `TenantSettings`.
- The `cn` npm package added by the shadcn CLI is shadcn's own (github.com/shadcn-ui/cn); kept.
- Native `<select>` (shadcn `native-select`) for forms, because the forms post via `FormData`.

### What I could NOT verify
- Supabase OTP sign-in screen (dev login only). Dark mode is not wired (light only, on purpose).

### Verification status
- Frontend: `tsc` clean, `lint` 0 errors (the accepted `window.location` warning), `next build` OK.
- Browser (Playwright + local Chrome, against the dev backend): enquiry DEMO-000005 submitted → welcome email +
  demo WhatsApp + 2 alerts shown on the lead; admin top-bar search, assign, user menu, staff edit dialog, walk-in via
  dialog + toast; receptionist lands on leads; 390 px mobile layout. No console errors.
- Backend unchanged this session (`./mvnw test` not re-run; last run 35/35).

### Git status
- Uncommitted on `main` (nothing committed in the repo yet).

## Session: team flow documentation

- `docs/flows.md` + `docs/generate_flows.py`: 3 use-case diagrams, 10 flowcharts, 2 state diagrams, permission matrix.
  All 16 Mermaid diagrams validated with the mermaid parser. Shareable page: https://claude.ai/artifact/KCuBRN6GoiU8LApZzPhkDs
  (private until the owner shares it).

## Session: D1 — receptionist assigns, admin dashboard

- `identity/Permission.REPORTS_VIEW`, `DefaultRoles` (Receptionist + `LEAD_ASSIGN`), `V4__receptionist_assigns_admin_reports.sql`.
- `lead/LeadStats`, `LeadRepository` count queries, `LeadService.stats(from, to)`, `GET /api/v1/leads/stats?from&to`.
- `frontend/app/(staff)/dashboard/page.tsx` (period: all / today / 7 / 30 days; totals, by status, source, counsellor;
  numbers link to the filtered lead list); leads list reads `?status=` / `?unassigned=true`.
- Tests: `LeadFlowTests` now 8 (receptionist assigns, counsellor/receptionist can't change status, stats + 403s). 35/35 pass.
- Frontend tsc/lint/build clean. Live check: receptionist assign → 200, receptionist stats → 403, admin stats correct.
- Assumption: dashboard counts leads **created** in the period, by their **current** status.
- Fix: enabled `spring.modulith.events.republish-outstanding-events-on-restart` (was off by default), so incomplete
  notification events are re-delivered on restart (at-least-once; a duplicate message is possible after a crash).

## Session: T3–T6 + basic UI

### What I built
- **Migration** `V3__leads_notifications_staff_records.sql`: `tenant_counters`, employee fields on `app_users`, `leads`
  (unique open lead per tenant+phone+email, `NULLS NOT DISTINCT`), `lead_activities`, `notification_log`,
  `LEAD_NEW_ALERT` granted to existing Admin/Receptionist roles.
- **tenancy**: `TenantSettings` (JSON: lead prefix, enquiry-form options, sender name, welcome toggles), `TenantCounters`.
- **identity**: `StaffLookup` (read API for other modules), `StaffAdminService`, `web/StaffController` (`/api/v1/users`),
  `web/OrganisationController` (`/api/v1/branches`, `/api/v1/roles`), dev login (`security/DevLogin`, `DevLoginController`).
- **lead**: `Lead`, `LeadActivity`, `LeadService` (intake/update/status/assign), `LeadFilter`, `LeadEvents`,
  `web/LeadController` (`/api/v1/leads/**`), `web/PublicEnquiryController`.
- **notification**: `Notifier`, `MessageTemplates` (Mustache, `templates/notifications/`), `LeadNotifications`,
  `DemoWhatsAppSender`, `web/NotificationController` (`/api/v1/leads/{id}/notifications`).
- **frontend/**: Next.js 16 app (see CLAUDE.md layout). Typed client from OpenAPI (`npm run gen:api`).
- Tests: `LeadFlowTests` (7), `StaffAdminTests` (4), plus the existing suites. `Fixtures` test helper.

### Assumptions I made
- Welcome goes out immediately (owner said "as soon as"), overriding the PRD's 30-minute demo delay.
- Assignee also gets an email (not explicitly asked; natural for "assigned to a counsellor").
- Envers dropped in favour of an explicit `lead_activities` table (simpler with `@TenantId`, and it is the seed of the Phase 3 feed).
- Public form is addressed by tenant slug; embeddable per-source keys (`lead_sources`) are deferred until needed.
- Welcome/alert wording is placeholder text in the Mustache templates until the client confirms.
- The demo tenant's enquiry-form options (services, countries) in the dev seed are demo values.

### What I could NOT verify
- Visual look of the UI in a browser (pages build and return 200; the API flow was exercised through the Next proxy).
- Real email delivery via a production SMTP provider (only Mailpit/GreenMail).
- Real Supabase OTP sign-in (no project yet; dev login used). Supabase token refresh is not handled (1 h expiry → re-login).

### Verification status
- `./mvnw test`: 34 tests, all pass.
- Frontend: `tsc` clean, `lint` 0 errors (1 accepted warning: full-page redirect on 401 in `lib/api.ts`), `next build` OK.
- Manual: dev profile + Mailpit + `next start`: enquiry → lead `DEMO-000001/2`, welcome email + demo WhatsApp, alerts to
  admin + reception, assignment email to counsellor, all visible in Mailpit.

### Git status
- Nothing committed yet (branch `main`, no remote). Commit only when the owner asks.

## Next action
- DEPLOY1 (Docker images + compose + backups), once the owner approves the hosting proposal below; owner meanwhile:
  AWS account (Mumbai), Vercel Pro, DNS access for westernworldvisaservices.com, Cloudflare Turnstile keys.
- Owner: commit AUTH1 + TRUST1 when happy (CRM branch `feat/auth-signin-codes` from `develop`; website `develop`).
- S1 Student Master after go-live: propose the breakdown (Student/Contact entity + V10 migration, `CONVERTED` lead
  status, convert endpoint + button, student list/profile with the same data scopes, lead history on the student).

## Open questions for the owner
- Decided 9 Oct (replaces 8 Oct `crm.`/`api.`): product name **AimDesk**; live `aimdesk.softzenithlabs.com` (staff app +
  `/platform`) and `aimdesk-api.softzenithlabs.com`; staging `aimdesk-staging[-api|-mail].softzenithlabs.com`. Tenant
  websites keep their own domains and have no staging copy. Ordered plan: `docs/go-live.md`. Needed: DNS access.
- Hosting (sized 8 Oct: ~10k leads/yr, ≤20 staff, ~5k documents/yr ≈ 5–10 GB/yr): proposed AWS Mumbai — one Lightsail
  4 GB server with Docker Compose (API, staff app, Postgres, HTTPS proxy), documents in a private S3 bucket (instead
  of the 25 Sep "Supabase Storage" decision: free tier is 1 GB), nightly `pg_dump` to S3, SES for email, Supabase
  free for sign-in only. Approve, then a deploy task (Docker images, compose, backups, HTTPS) goes on the roadmap.
- Production database host: Supabase Postgres (then keep its Data API off our schema) or another host? And
  approve RLS1 (Postgres RLS as a second tenant-isolation layer) as the next T8 item?
- Production for Western World to be live for real: hosting (API, UI, PostgreSQL), Supabase phone OTP (+ SMS provider,
  DLT), email provider/domain, and the platform secrets (`CRM_PLATFORM_JWT_SECRET`, first admin). Which hosting?
- Western World's service list for the enquiry form (starter file has a suggestion from their website).
- Supabase project: reuse "Softzenith" or create a new one (org "Calsquare" already has 2 active projects)? Then SMS
  gateway + India DLT registration for OTP.
- SMTP provider for real email (Resend / SES / Brevo / Gmail SMTP) and the sender domain.
- Final wording of the welcome email/WhatsApp and the alert email.
- WhatsApp Business API provider (Meta Cloud API directly, or Gupshup/Interakt) when ready.
- Western World site: where will it be hosted, and is www.westernworldvisaservices.com moving to it? Who confirms the
  university data clean-up (see `sites/westernworld/README.md`)?
- Wording of the new student messages (status update, repeat acknowledgement).
- Should Branch Managers also assign leads / see the dashboard? (Permission toggles: `LEAD_ASSIGN`, `REPORTS_VIEW`.)
- Cloudflare Turnstile (free account) for the enquiry captcha: site key → `NEXT_PUBLIC_TURNSTILE_SITE_KEY`, secret →
  `TURNSTILE_SECRET`.
- Supabase (AUTH2, email only): keep sign-ups off; phone OTP is no longer planned (AUTH1 replaced it).
- Message templates: `MessageTemplates` says "missing values render empty", but JMustache's `nullValue("")` (after
  `defaultValue("")`) makes a key missing from the model throw. A template using a key the code doesn't set would fail
  that event's notifications (retried on restart). Keep the strict behaviour (catches template typos) and fix the
  comment, or make missing keys render empty as the comment says?
- Enquiry rate limits and the per-enquirer daily cap (defaults: 5/min and 30/h per IP, 5/h per phone, 5 messages of a
  kind per day): fine for real traffic, e.g. a school or office where many people share one IP?
