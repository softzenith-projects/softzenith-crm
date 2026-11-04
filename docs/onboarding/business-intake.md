# SoftCRM: business setup form

Send this to a business before onboarding it. Everything they answer is entered as data in the SoftZenith console;
nothing about a business is written in code. The client-facing part ends at "Thank you"; the appendix is for us.

Published as a page: see the link in `HANDOFF.md` (keep the two in step).

**How it comes back:** send the business `business-intake.html` (opens in any browser). They fill it in and click
**Download setup file**, which saves `<slug>.blueprint.json`, then email it with their logo. In the console:
**Onboard a business → Load file** → that file. Then add the first Admin's sign-in code, check, and go live. The file's
`intake` part holds the answers the console has no field for yet (contact, brand, alerts, technical); the backend
ignores it. Downloads do not work inside the claude.ai page, so there the client uses **Copy answers** and you save the
part after "SETUP FILE" as the `.json` file.

---

**From SoftZenith Softwares**

Thank you for choosing SoftCRM. Please fill in this form so we can set up your CRM. It takes about 20 minutes.
Fields marked **\*** are needed before we can go live; the rest can follow later.

## 1. Your business

| | Your answer |
|---|---|
| Business name, as customers should see it **\*** | |
| Main contact person: name, mobile, email **\*** | |
| Your website address (if any) | |
| Country and time zone (default: India, IST) | |
| A short name for your web address, e.g. `westernworld` (letters, numbers and dashes; **cannot be changed later**) | |

## 2. Your brand

How your CRM and your enquiry form will look to your team and to your students.

| | Your answer |
|---|---|
| Logo: a high-quality file (SVG or PNG with a transparent background). If you have one, also a square version (the icon alone) **\*** | Attach |
| Brand colours: your main colour and, if any, a second colour (a colour code like `#1E3A8A`, or a link to your website where we can pick them) | |
| A one-line tagline (shown on your enquiry form), e.g. "Your study-abroad partner since 2010" | |
| Name your messages are sent from, e.g. "Western World Visa Services" | |
| Email address students can reply to | |

**Your CRM's address.** Choose one:

- [ ] Our standard address (fastest; nothing needed from you)
- [ ] Your own name with us, e.g. `yourname.softzenithlabs.com`
- [ ] Your own domain, e.g. `crm.yourbusiness.com` (we will send you one DNS record to add)

## 3. Your enquiry form

| | Your answer |
|---|---|
| Services you offer (the choices on the form), e.g. Study Visa, Visitor Visa, IELTS Coaching **\*** | |
| Countries students can choose, e.g. Canada, UK, Australia (leave empty for free text) | |
| 2–4 letters to start your lead numbers, e.g. `WWV` gives WWV-000001 | |

## 4. Branches

Skip this if you have one office.

| Branch name | City |
|---|---|
| | |
| | |

## 5. Your team

Each person signs in with their **mobile number**, so please give the number they actually use (with WhatsApp if
possible). You need **at least one Admin**.

**Roles** (tick the ones you will use):

- [ ] **Admin**: sees and does everything, adds staff
- [ ] **Receptionist / front desk**: sees every enquiry and hands them to the right person
- [ ] **Branch Manager**: sees their branch's enquiries
- [ ] **Counsellor**: sees only the enquiries handed to them

Who should be able to receive enquiries (have them assigned)? ______________________

Who should be able to change an enquiry's status (Contacted, Closed, and so on)? ______________________

| Full name **\*** | Mobile **\*** | Email | Role **\*** | Branch | Designation | Employee code |
|---|---|---|---|---|---|---|
| | | | | | | |
| | | | | | | |

A spreadsheet with these columns is fine for a long list.

## 6. Messages and alerts

| | Yes / No |
|---|---|
| Send each new enquirer a welcome email | |
| Send each new enquirer a welcome WhatsApp (once WhatsApp is connected) | |
| Send students updates as their case moves | |
| Should every Admin and Receptionist get a new-enquiry alert? If not, who should? | |
| Would you like a Google Sheet with a copy of every enquiry? If yes, the Google account(s) to share it with | |

We will show you the wording of every message before anything is sent to your students.

## 7. Website and technical contacts

| | Your answer |
|---|---|
| Who built or manages your website (name, phone) | |
| Where your website is hosted, and who has the domain / DNS login | |
| Who provides your business email (Google Workspace, Microsoft 365, other) | |
| Your privacy policy page (if any) | |
| Facebook Page, Instagram and WhatsApp Business number (for WhatsApp messages and ad enquiries later) | |
| Do you have existing enquiries in Excel or another CRM you want brought in? | |

**Thank you!** Email us the setup file (the form's **Download setup file** button), your logo and any spreadsheet, and we
will set up a trial version for you to check before going live.

---

## Appendix (internal, not for the client): where each answer goes

| Answer | Goes to | Status |
|---|---|---|
| Business name, short name, country/time zone | Console → Business (`business.name`, `slug`, `defaultRegion`, `timezone`) | Supported |
| Services, countries, lead prefix | Console → Enquiry form (`settings.serviceInterests`, `countries`, `leadNumberPrefix`) | Supported |
| Sender name, welcome email/WhatsApp, student updates | `settings.senderName`, `welcomeEmail`, `welcomeWhatsApp`, `studentUpdates` | Supported (WhatsApp is demo only) |
| Branches | Console → Branches (`branches[]`: name, city) | Supported |
| Staff | Console → Staff (`staff[]`); the first Admin needs a sign-in code | Supported |
| Roles used | Features: Admin only → `LEADS_CORE`; any team → add `TEAM_AND_STUDENTS` | Supported |
| Who receives leads / changes status | Role `assignable` flag and permissions | **Not in the blueprint**: database only for now |
| Who gets alerts | Admin + Receptionist with an email (default) | Changing it: not supported |
| Google Sheet | `settings.leadSheet` via `deploy/lead-sheet.sh` (`docs/lead-sheet.md`) | Supported (by us, not the console) |
| Logo, colours, tagline | Tenant branding settings | **Not built**: task A2 |
| CRM address (own name / own domain) | Subdomain or custom domain per business | **Not built**; standard address only |
| Reply-to email | — | **Not built** (sender is the platform's `MAIL_FROM`) |
| Message wording | Shared Mustache templates | Per-business wording: not supported |
| Website, DNS, email provider, Meta | `docs/go-live.md` (domain switch, email, WhatsApp) | Manual steps |
| Existing enquiries to import | — | **No import yet**; plan per business |
