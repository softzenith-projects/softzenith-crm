# Onboarding a business

How a new business (tenant) goes live on the CRM, with no code change and no database work. Western World Visa Services
is the first; every later business follows the same steps.

## Before you start

Collect from the business:

| What | Example | Notes |
|---|---|---|
| Business name | Western World Visa Services | Shown on the enquiry form and in emails |
| Web address (slug) | `westernworld` | Their form lives at `/enquiry/<slug>`; cannot be changed later |
| Lead number prefix | `WWV` | Leads are numbered `WWV-000001`, `WWV-000002`, ... |
| Services they offer | Study Visa, IELTS Coaching, ... | The choices on the enquiry form |
| Countries | Canada, UK, Australia, ... | Empty list = free text |
| Branches | Rohtak (Rohtak), Hisar (Hisar) | Optional for a single office |
| Staff | Name, mobile, email, role, branch | At least one **Admin**; the mobile is how each person signs in |

Roles: **Admin** (everything), **Branch Manager** (their branch's leads), **Counsellor** (leads assigned to them),
**Receptionist** (all leads, assigns them). Admins and Receptionists with an email get new-enquiry alerts.

A staff list in a spreadsheet can be pasted straight in (columns: Name, Mobile, Email, Role, Branch, Designation,
Employee code).

## Steps

1. Sign in to the SoftZenith console at **`/platform`** with the platform admin username and password
   (locally: `softzenith` / `local-platform-admin`; in production, the account created from `CRM_PLATFORM_ADMIN_*`).
   The console is not linked from the staff app, and tenant staff cannot use it.
2. **Businesses → Onboard a business.** For Western World, click **Load file** and choose
   `docs/onboarding/westernworld.blueprint.json`: it fills in the name, address, prefix, services and countries (from
   their website; confirm the service list with them). Then add the branches and staff.
3. Work through the steps. **Save file** at any time keeps a copy of the setup; the form also keeps a draft in the
   browser.
4. **Review & go live** checks everything with the server (a full trial run that is then undone). Fix anything it lists;
   **Go to …** jumps to the right step. Warnings are allowed but worth reading.
5. **Go live.** The business, its settings, the four default roles, the branches and the staff invitations are created
   in one step: all of it or nothing. Nobody is messaged.
6. After going live:
   - Their enquiry form works at `/enquiry/<slug>`. Send a test enquiry and check it arrives.
   - **Western World's website** (own repo, `softzenith-projects/westernworld-website`): set `NEXT_PUBLIC_CRM_TENANT=westernworldvisaservices` (the default; it must
     equal the slug) so its forms send enquiries to this business.
   - Staff sign in at **`/login`** with their mobile number and the sign-in code their Admin set. Their first sign-in
     activates them (status Invited → Active).
   - Their Admin adds or changes staff and branches under **Staff** and **Branches**.

Every sign-in and every onboarding is recorded in the `platform_audit` table.

## What "live" needs in production (not code)

Locally, staff sign in with the dev login (phone only). For real use the CRM must be deployed, and these need an owner:

- Hosting for the API and the UI, and a production PostgreSQL database.
- Supabase phone sign-in: a Supabase project, an SMS provider and India DLT registration (`SUPABASE_URL`,
  `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`).
- An email provider and sender domain (`MAIL_*`, `MAIL_FROM`).
- `CRM_PLATFORM_JWT_SECRET` (32+ random characters) and, for the first start only, `CRM_PLATFORM_ADMIN_USERNAME` /
  `CRM_PLATFORM_ADMIN_PASSWORD` (12+ characters). Remove the password from the environment after the first start.

## Not in the console yet

Changing a business after it is live (its settings, suspending it), role names and permissions per business, logo and
colours, and message wording per business. Until then: settings via the database, everything else via the business's
own Admin screens.
