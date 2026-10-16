# Single source for the team flow documentation.
# Writes docs/flows.md (Mermaid; renders on GitHub/VS Code) and a standalone HTML page (shareable).
# Usage (from repo root): python docs/generate_flows.py . <output.html>
# When a flow changes in code, update its section here and regenerate both outputs.
import html
import sys
REPO = sys.argv[1]
OUT_HTML = sys.argv[2]

INTRO = (
    "How the CRM is built and how it behaves: the architecture (apps, modules, request path, tenancy, data), who can do "
    "what, exactly what happens step by step when someone submits an enquiry, signs in, assigns a lead or changes its "
    "status, and how to debug any of it from a request id down to the line of code. Every flow is the same for every "
    "tenant; a tenant only changes data (settings, roles, branches), never the flow."
)

LEGEND = [
    ("Rounded box", "where a flow starts or ends"),
    ("Rectangle", "a step the system performs"),
    ("Diamond", "a decision or check"),
    ("Dotted arrow", "happens later, in the background, after the database commit"),
    ("Circle", "an actor (a person) in use-case diagrams"),
    ("Stadium", "a use case (something an actor can do)"),
    ("Sequence diagram", "messages between parts of the system, top to bottom in time"),
]

# Default role permissions (identity/DefaultRoles.java + V3/V4/V5/V7 migrations). Editable per tenant.
# A flag is 1/0 (tick / dot) or a text value shown as is (data scope).
PERMISSIONS = [
    ("LEAD_VIEW", "View and search leads", [1, 1, 1, 1]),
    ("LEAD_CREATE", "Add walk-in / phone leads", [1, 1, 1, 1]),
    ("LEAD_EDIT", "Edit lead contact details", [1, 1, 1, 1]),
    ("LEAD_ASSIGN", "Assign a lead to a counsellor", [1, 0, 0, 1]),
    ("LEAD_CHANGE_STATUS", "Change status, close a lead", [1, 0, 1, 0]),
    ("LEAD_REOPEN", "Reopen a closed lead", [1, 0, 0, 0]),
    ("LEAD_NEW_ALERT", "Receive new-lead alert emails", [1, 0, 0, 1]),
    ("REPORTS_VIEW", "See the lead dashboard", [1, 0, 0, 0]),
    ("USER_VIEW", "See staff list", [1, 1, 1, 1]),
    ("USER_MANAGE", "Add, edit, disable staff", [1, 0, 0, 0]),
    ("BRANCH_MANAGE", "Add and deactivate branches", [1, 0, 0, 0]),
    ("(role flag) assignable", "Can be assigned leads", [0, 1, 1, 0]),
    ("(role setting) data scope", "Which leads the role sees: ALL, its BRANCH, or OWN (assigned to them)",
     ["ALL", "BRANCH", "OWN", "ALL"]),
    ("LEAD_CONVERT (planned, S1)", "Convert a lead into a student (proposed)", [1, 1, 1, 0]),
]
ROLES = ["Admin", "Branch Manager", "Counsellor", "Receptionist"]

SECTIONS = []


def section(group, sid, title, text, code, diagram="", kind="flowchart", tables=(), snippets=()):
    """tables: [(caption, headers, rows)]; snippets: [(caption, text)]. Text in `backticks` renders as code."""
    SECTIONS.append(dict(group=group, id=sid, title=title, text=text, code=code, diagram=diagram.strip("\n"), kind=kind,
                         tables=list(tables), snippets=list(snippets)))


# ---------------------------------------------------------------- Architecture
section("Architecture", "architecture", "System at a glance",
        "Three web apps and one API. Staff use the CRM app; the public uses the CRM's own enquiry page or a tenant's "
        "website (Western World runs on its own domain). Browsers never call the API directly: each Next.js server "
        "forwards `/api` calls to it, and the Western World site forwards only the two public enquiry endpoints. The API "
        "is one Spring Boot application split into modules, over one PostgreSQL schema where every tenant row carries "
        "`tenant_id`. Staff sign in with mobile + a sign-in code checked by the API itself. Supabase is optional and only "
        "for sign-in (email, planned AUTH2); it holds no CRM data, and the API reaches the database through its own "
        "connection, never through Supabase's data API.",
        "frontend/next.config.ts · westernworld-website repo: next.config.ts · backend/src/main/java/com/softzenith/crm/*",
        """
flowchart LR
  V(["Visitor / student"]) --> WW["Western World website :3001<br/>own domain, Next.js<br/>forwards ONLY public enquiry calls"]
  V --> EQ["CRM enquiry page<br/>/enquiry/slug"]
  S(["Staff"]) --> UI["CRM staff app :3000<br/>Next.js, forwards /api/*"]
  EQ --- UI
  WW -->|"POST .../public/tenants/slug/enquiries"| API
  UI -->|"/api/* with Bearer token + X-Tenant-ID"| API
  subgraph API["Spring Boot API :8081 (one app, Modulith modules)"]
    direction TB
    FL["Filters: request id + access log,<br/>security, staff and tenant"] --> MO["Modules: lead · identity · notification<br/>tenancy · onboarding"]
  end
  MO --> DB[("PostgreSQL :5432<br/>one schema, tenant_id on every row,<br/>event_publication outbox")]
  MO -->|SMTP| MAIL["Email<br/>Mailpit :8025 locally"]
  MO -.->|"demo only for now"| WA["WhatsApp provider"]
  MO --> LOG["logs/crm.log<br/>JSON in production"]
  S -.->|"email sign-in, planned"| SUPA["Supabase Auth (optional)<br/>sign-in only, holds no CRM data"]
  API -.->|"checks token signature"| SUPA
  API -.->|"checks captcha when configured"| CF["Cloudflare Turnstile"]
""")

section("Architecture", "arch-modules", "Backend modules and how they talk",
        "Each folder under `com.softzenith.crm` is a module that owns its tables. A module may call another module's "
        "public classes (solid arrows) but never its repositories; `ModularityTests` fails the build if that rule is "
        "broken. The lead module never calls notification: it publishes events, which are stored with the lead in the "
        "same transaction and delivered after commit (dotted arrow). `shared` is open to every module.",
        "backend/src/main/java/com/softzenith/crm/*/package-info.java · src/test/.../ModularityTests.java",
        """
flowchart LR
  lead["lead<br/>Lead, LeadService: intake, status,<br/>assignment, data scope, stats"]
  notification["notification<br/>LeadNotifications: who gets what<br/>Notifier: send, cap, log"]
  identity["identity<br/>staff, roles, permissions, branches<br/>security: token to staff to tenant"]
  tenancy["tenancy<br/>Tenant, TenantSettings,<br/>TenantCounters (lead numbers)"]
  onboarding["onboarding<br/>new tenant + default roles"]
  devdata["devdata<br/>demo data, dev profile only"]
  shared["shared (open to all)<br/>TenantContext, base entities, errors,<br/>logging, phone numbers, text cleaning"]
  lead -->|"StaffLookup, CurrentStaff"| identity
  lead -->|"counters, settings"| tenancy
  lead -.->|"LeadCreated, LeadAssigned,<br/>LeadStatusChanged, LeadRepeatEnquiry"| notification
  notification -->|"who holds LEAD_NEW_ALERT"| identity
  notification -->|"sender name, toggles"| tenancy
  identity --> tenancy
  onboarding --> tenancy
  onboarding --> identity
  devdata --> onboarding
""")

section("Architecture", "arch-pipeline", "The path of one API request",
        "The same fixed order for every call. The first filter gives the request an id, which appears on every log line "
        "and in every error body, and follows the work into the background notification thread. Anything that goes "
        "wrong becomes an RFC 9457 problem response in one place, `GlobalExceptionHandler`.",
        "shared/logging/RequestLoggingFilter · identity/security/SecurityConfig · identity/security/StaffContextFilter · shared/web/GlobalExceptionHandler",
        """
flowchart TD
  A(["HTTP request arrives"]) --> F1["RequestLoggingFilter<br/>request id into log context,<br/>X-Request-Id response header"]
  F1 --> F2{"Spring Security:<br/>public URL?"}
  F2 -- "yes: /public, /dev, docs" --> D
  F2 -- no --> F3{"Bearer token valid?<br/>signature, expiry, issuer"}
  F3 -- no --> E401["401, reason logged"]
  F3 -- yes --> F4["StaffContextFilter<br/>token to active staff of X-Tenant-ID tenant;<br/>sets TenantContext, tenant + user in logs"]
  F4 -- "not staff there" --> E403a["403, or 400 if no tenant chosen"]
  F4 --> D["Controller"]
  D --> P{"@PreAuthorize:<br/>role has the permission?"}
  P -- no --> E403["403, logged"]
  P -- yes --> SV["Service, one transaction:<br/>business rules, data scope"]
  SV --> RP["Repository and Hibernate:<br/>tenant_id added to every query"]
  RP --> DB[("PostgreSQL")]
  SV -- "exception" --> GE["GlobalExceptionHandler<br/>problem JSON + requestId;<br/>4xx one line, 5xx stack trace"]
  SV --> EV["publishEvent: saved in<br/>event_publication, same transaction"]
  EV --> C["Commit"]
  C --> R(["Response + one access-log line:<br/>method, path, status, ms"])
  C -.->|"after commit, background thread,<br/>same request id"| NT[["Notification listeners"]]
""")

section("Architecture", "arch-tenancy", "How tenants are kept apart",
        "Tenant isolation does not rely on developers remembering a where-clause. Whoever handles the work puts the "
        "tenant in `TenantContext` (a per-thread value); Hibernate reads it and stamps or filters `tenant_id` on every "
        "entity that extends `TenantScopedEntity`. If nothing set it, queries return nothing and inserts fail.",
        "shared/tenant/TenantContext · shared/tenant/HibernateTenancyConfig · shared/persistence/TenantScopedEntity",
        """
flowchart LR
  subgraph WHO["Who sets the tenant"]
    direction TB
    W1["Staff request:<br/>StaffContextFilter, from X-Tenant-ID"]
    W2["Public enquiry:<br/>TenantContext.call, tenant found by slug"]
    W3["Background notification:<br/>TenantContext.run, tenant from the event"]
    W4["Onboarding, sign-in lookup:<br/>TenantContext.callAsSystem, sees all tenants"]
  end
  WHO --> R["Hibernate tenant resolver<br/>reads TenantContext"]
  R --> Q["Insert: tenant_id stamped<br/>Select / update: where tenant_id = current"]
  R --> N["Nothing set: queries see nothing,<br/>inserts fail. Safe by default"]
  Q --> DBC["Database repeats it: composite keys<br/>(x_id, tenant_id) on every reference"]
""")

section("Architecture", "arch-data", "Data model",
        "Tables as the Flyway migrations V1 to V7 create them (V7 only changes the seeded roles' data scope; the planned "
        "Student table is described under 'Converting a lead to a student') (`ddl-auto=validate`: the app refuses to start if an entity "
        "and the schema disagree). Tables owned by a tenant also carry `tenant_id`, audit columns (`created_at/by`, "
        "`updated_at/by`) and `version` for optimistic locking. One open lead per person: unique on "
        "(tenant, phone, email) while the lead is not CLOSED.",
        "backend/src/main/resources/db/migration/V1..V7 · */*.java entities",
        """
erDiagram
  TENANTS ||--o{ BRANCHES : has
  TENANTS ||--o{ ROLES : has
  ROLES ||--o{ ROLE_PERMISSIONS : grants
  ROLES ||--o{ APP_USERS : "role of"
  BRANCHES |o--o{ APP_USERS : "works at"
  TENANTS ||--o{ TENANT_COUNTERS : numbers
  TENANTS ||--o{ LEADS : owns
  BRANCHES |o--o{ LEADS : "enquired at"
  APP_USERS |o--o{ LEADS : "assigned to"
  LEADS ||--o{ LEAD_ACTIVITIES : history
  LEADS ||--o{ NOTIFICATION_LOG : messages
  TENANTS {
    uuid id PK
    text slug
    text status
    text default_region
    jsonb settings
  }
  ROLES {
    uuid id PK
    text code
    text name
    text data_scope
    boolean assignable
    boolean is_system
  }
  ROLE_PERMISSIONS {
    uuid role_id FK
    text permission
  }
  APP_USERS {
    uuid id PK
    text phone_e164
    text auth_subject
    text full_name
    text status
    uuid role_id FK
    uuid branch_id FK
  }
  LEADS {
    uuid id PK
    text lead_number
    text phone_e164
    text email
    text status
    text source_type
    uuid assigned_to FK
    uuid branch_id FK
    int enquiry_count
    timestamptz last_enquiry_at
    jsonb custom_fields
  }
  LEAD_ACTIVITIES {
    uuid id PK
    uuid lead_id FK
    text type
    text from_value
    text to_value
    text note
  }
  NOTIFICATION_LOG {
    uuid id PK
    uuid lead_id FK
    uuid event_id
    text channel
    text kind
    text recipient
    text status
    text error
  }
  EVENT_PUBLICATION {
    uuid id PK
    text event_type
    text listener_id
    timestamptz publication_date
    timestamptz completion_date
  }
""", kind="er")

section("Architecture", "arch-sequence", "One website enquiry, end to end",
        "What happens between a visitor pressing Submit on the Western World site and the emails going out. The visitor "
        "gets the thank-you as soon as the database commits; messages follow on a background thread carrying the same "
        "request id, so the whole story can be read back from the log.",
        "westernworld-website repo: components/EnquiryForm.tsx, lib/enquiry.ts · lead/web/PublicEnquiryController · lead/LeadService.intake · notification/*",
        """
sequenceDiagram
  autonumber
  actor V as Visitor
  participant W as Website server
  participant A as CRM API
  participant D as PostgreSQL
  participant N as Background thread
  participant M as Email and WhatsApp
  V->>W: Submit the Keep in touch form
  W->>A: POST /api/v1/public/tenants/westernworld/enquiries
  A->>A: request id, validation, honeypot, rate limit, captcha
  A->>D: lock this person, look for an open lead
  alt new person
    A->>D: insert lead, CREATED activity, LeadCreated event
  else open lead exists
    A->>D: REPEAT_ENQUIRY activity, count + 1, LeadRepeatEnquiry event
  end
  A->>D: commit
  A-->>W: 201 with the thank-you message
  W-->>V: thank-you page
  A-)N: deliver the event after commit
  N->>M: welcome or acknowledgement, staff alerts
  N->>D: one notification_log row per message, event completed
""", kind="sequence")

section("Architecture", "arch-frontends", "The two front-end apps",
        "Both are Next.js 16 apps with client-side pages. The staff app builds its menu from the signed-in user's "
        "permissions and calls the API through a client whose types are generated from the backend's OpenAPI spec "
        "(`npm run gen:api`). Tenant websites live in their own repositories (Western World: `softzenith-projects/westernworld-website`): "
        "content pages, one enquiry form component used everywhere, redirects that keep every old URL working, and the "
        "public enquiry API as their only link to the CRM.",
        "frontend/app/* · frontend/components/AppShell.tsx · frontend/lib/api.ts · westernworld-website repo: app/*, content/*",
        """
flowchart LR
  subgraph STAFF["frontend/ : CRM staff app"]
    direction TB
    LG["app/login<br/>mobile + sign-in code, dev login"] --> SH["components/AppShell<br/>GET /me, menu from permissions"]
    SH --> P1["app/(staff)/dashboard"]
    SH --> P2["app/(staff)/leads<br/>and leads/[id]"]
    SH --> P3["app/(staff)/admin<br/>staff, branches"]
    P2 --> CL["lib/api.ts<br/>typed client, adds token<br/>and X-Tenant-ID"]
    EQ["app/enquiry/[tenant]<br/>public form"]
  end
  subgraph SITE["westernworld-website repo : tenant website"]
    direction TB
    CT["content/*.json, copy.ts"] --> PG["app/** : 235 pages<br/>countries, universities, courses, cities"]
    PG --> EF["components/EnquiryForm<br/>every form on the site"]
    EF --> LE["lib/enquiry.ts<br/>POST, then /thank-you"]
    RD["next.config.ts<br/>old .php URLs: 308 redirects"]
  end
  CL -->|"/api/*"| BE["CRM API"]
  EQ -->|"public endpoints"| BE
  LE -->|"public endpoints only"| BE
""")

section("Architecture", "tenant-onboarding", "Onboarding a new business (tenant)",
        "The golden rule: a new business needs data, not code. Onboarding creates the tenant and its default roles in one "
        "transaction; everything that differs between businesses is stored as tenant settings. Settings and tenant creation "
        "have no admin screen yet (planned); today they are done by the seeder or directly.",
        "onboarding/TenantOnboardingService.java · tenancy/TenantSettings.java · identity/DefaultRoles.java",
        """
flowchart LR
  A(["New business"]) --> B["Create tenant<br/>slug, name, phone region, timezone"]
  B --> C["Create 4 default roles<br/>with their permissions"]
  C --> D["Tenant settings, stored as data:<br/>lead number prefix, form options,<br/>email sender name, welcome on/off"]
  D --> E["Admin adds branches<br/>and staff"]
  E --> F(["Enquiry form live at<br/>/enquiry/slug"])
""")

# ---------------------------------------------------------------- Use cases
section("Use cases", "uc-capture", "Use cases: capturing leads",
        "Leads come in from the public form or are entered by staff. Both go through the same intake, so duplicate "
        "detection, numbering and notifications are identical.",
        "lead/web/PublicEnquiryController.java · lead/web/LeadController.java · lead/LeadService.intake",
        """
flowchart LR
  visitor(("Visitor /<br/>student"))
  staff(("Any staff<br/>member"))
  subgraph CRM["CRM: lead capture"]
    direction TB
    u1(["Open enquiry form"])
    u2(["Submit enquiry"])
    u3(["Add walk-in / phone /<br/>referral lead"])
    u4(["Detect repeat enquiry<br/>and attach to open lead"])
    u5(["Receive welcome email"])
    u6(["Receive welcome WhatsApp<br/>(demo)"])
  end
  visitor --- u1 & u2 & u5 & u6
  staff --- u3
  u2 -.->|"includes"| u4
  u3 -.->|"includes"| u4
""", kind="usecase")

section("Use cases", "uc-handling", "Use cases: working leads",
        "What a role sees follows its data scope: Admin and Receptionist ALL, Branch Manager BRANCH (own branch + assigned "
        "to them), Counsellor OWN (assigned to them); other leads answer 404. A lead a counsellor adds stays unassigned "
        "until reception or an admin assigns it. Receptionists and admins assign; admins and counsellors change status; only "
        "admins reopen and see the dashboard. Every action is written to the lead's history, and the student is told "
        "(email + WhatsApp) when the lead is assigned or its status changes. Planned (S1, not built yet): the counsellor "
        "converts a qualified lead into a Student, and the follow-up work moves there.",
        "lead/web/LeadController.java · lead/LeadService.java · notification/LeadNotifications.java",
        """
flowchart LR
  recep(("Receptionist"))
  couns(("Counsellor /<br/>Branch Manager"))
  onlyc(("Counsellor<br/>(status only)"))
  admin(("Admin"))
  subgraph CRM["CRM: lead handling"]
    direction TB
    h1(["View and search leads"])
    h2(["See lead history<br/>and notifications"])
    h3(["Assign lead to<br/>counsellor"])
    h4(["Change status"])
    h5(["Close lead"])
    h6(["Give close reason"])
    h7(["Reopen closed lead"])
    h8(["View lead dashboard"])
    h9(["Receive new-lead alert"])
    h10(["Receive 'assigned to you'<br/>email"])
    h11(["Convert lead to student<br/>(planned, S1)"])
  end
  recep --- h1 & h2 & h3 & h9
  couns --- h1 & h2 & h10 & h11
  onlyc --- h4 & h5
  admin --- h1 & h2 & h3 & h4 & h5 & h7 & h8 & h9 & h11
  h5 -.->|"includes"| h6
""", kind="usecase")

section("Use cases", "uc-admin", "Use cases: administration",
        "Admins onboard staff by mobile number and keep basic employee records. The staff member activates their own "
        "account by signing in with that number.",
        "identity/web/StaffController.java · identity/web/OrganisationController.java · identity/StaffDirectory.java",
        """
flowchart LR
  admin(("Admin"))
  member(("New staff<br/>member"))
  subgraph CRM["CRM: administration"]
    direction TB
    a1(["Add staff member<br/>(INVITED)"])
    a2(["Edit staff record<br/>role, branch, employee code"])
    a3(["Disable / enable staff"])
    a4(["Add / deactivate branch"])
    a5(["View roles and permissions"])
    a6(["Sign in with mobile"])
    a7(["Link invite to identity<br/>INVITED to ACTIVE"])
  end
  admin --- a1 & a2 & a3 & a4 & a5
  member --- a6
  a6 -.->|"first time, includes"| a7
""", kind="usecase")

# ---------------------------------------------------------------- Flows
section("Flows", "flow-journey", "The customer journey: enquiry, lead, student",
        "The big picture. A Lead is an enquiry that is not yet qualified; it is assigned to a counsellor, who contacts "
        "and qualifies it. A lead ends in one of two ways: it is converted into a Student when the person decides to go "
        "ahead, or it is closed as lost, with a reason. Assignment does not end a lead. From conversion on, all the real "
        "work (follow-ups, tasks, remarks, documents, applications, the visa case) belongs to the Student. Student is the "
        "Western World name for a generic Contact, so other businesses can call it Client or Customer. Dashed boxes are "
        "planned: Student and conversion (S1), then remarks and tasks (C1 to C3), documents (DOC1, DOC2), and "
        "applications and visa (PRD phase 4).",
        "lead/LeadService.java (today) · roadmap S1, C1 to C3, DOC1 and DOC2 in HANDOFF.md",
        """
flowchart TD
  E(["Enquiry: website form, walk-in,<br/>phone, referral, later Meta / WhatsApp"]) --> L["Lead: NEW"]
  L --> A["Assigned to a counsellor: ASSIGNED<br/>(Admin or Receptionist)"]
  A --> C["Counsellor calls and qualifies: CONTACTED<br/>follow-ups happen on the lead"]
  C --> D{"Is the person<br/>going ahead?"}
  D -- "yes" --> S["Convert: a Student is created<br/>lead becomes CONVERTED"]
  D -- "no" --> X["Close as lost, with a reason: CLOSED"]
  X -.->|"Admin reopens"| C
  S --> W["Student: follow-ups, tasks, remarks<br/>documents, applications, visa case"]
  R(["The same person enquires again"]) -.->|"open lead: added to it"| C
  R -.->|"already a student: added to the student"| W
  classDef planned stroke-dasharray: 6 4
  class S,W planned
""")

section("Flows", "flow-enquiry", "Submitting an enquiry",
        "Everything up to \"Commit\" happens in one database transaction on the request thread. The lead and the "
        "\"notify about it\" event are saved together, or not at all. Notifications run afterwards (next diagram), so the "
        "visitor never waits for email.",
        "frontend/app/enquiry/[tenant]/page.tsx · lead/web/PublicEnquiryController.submit · lead/LeadService.intake · tenancy/TenantCounters.next",
        """
flowchart TD
  A(["Visitor clicks Submit"]) --> B["Browser sends POST<br/>/api/v1/public/tenants/slug/enquiries"]
  B --> C["Next.js proxy forwards to Spring Boot<br/>security: public URL, no token"]
  C --> D{"Fields valid?<br/>name, phone, email format"}
  D -- no --> D1["400 with field errors,<br/>shown under the form"]
  D -- yes --> E{"Tenant exists<br/>and is active?"}
  E -- no --> E1["404"]
  E -- yes --> F{"Hidden honeypot<br/>field filled in?"}
  F -- "yes (bot)" --> F1["Answer like success,<br/>save nothing"]
  F -- no --> RL{"Within rate limits?<br/>per IP, per phone, per tenant"}
  RL -- no --> RL1["429 + Retry-After"]
  RL -- yes --> CP{"Captcha OK?<br/>(Turnstile, when configured)"}
  CP -- no --> CP1["403: complete the verification"]
  CP -- yes --> G["Set tenant context,<br/>open transaction"]
  G --> H["Normalise phone to +91...<br/>check branch belongs to tenant<br/>strip control characters"]
  H --> HL["Lock this person (tenant + phone)<br/>so a double submit waits its turn"]
  HL --> I{"Open lead already exists<br/>for same phone + email?"}
  I -- yes --> I1["Add REPEAT_ENQUIRY to the existing lead,<br/>bump last-enquiry time and count<br/>(lead moves to the top of the list)"]
  I1 --> I2["Publish LeadRepeatEnquiry"]
  I -- no --> J["Next number from tenant counter<br/>e.g. WWV-000003"]
  J --> K["Save lead, status NEW<br/>+ CREATED activity"]
  K --> L["Publish LeadCreated<br/>stored in event_publication"]
  L --> M["Commit"]
  I2 --> M
  M --> N(["201: the same thank-you for new and repeat<br/>(no lead number: the form must not reveal<br/>who already has an enquiry)"])
  L -.->|"after commit, background"| Q[["Notifications flow"]]
  I2 -.->|"after commit, background"| Q2[["Student-update flow"]]
""")

section("Flows", "flow-walkin", "Staff adding a walk-in or phone lead",
        "Same intake as the public form. The only differences: the tenant comes from the signed-in user, the source is chosen "
        "by staff, and created_by records who entered it.",
        "frontend/app/(staff)/leads/page.tsx (AddLead) · lead/web/LeadController.create",
        """
flowchart TD
  A(["Staff clicks + Add lead"]) --> B["Fills name, mobile, source<br/>WALK_IN / PHONE / REFERRAL / OTHER"]
  B --> C["POST /api/v1/leads"]
  C --> D["Staff request checks<br/>see 'Every staff request'"]
  D --> E{"Has LEAD_CREATE?"}
  E -- no --> E1["403"]
  E -- yes --> F[["Same intake as the enquiry flow:<br/>duplicate check, number, activity, event"]]
  F --> G(["201: lead appears in the list"])
  F -.->|"background"| H[["Notifications flow"]]
""")

section("Flows", "flow-notify", "Notifications after a new lead",
        "Runs on a background thread once the lead is committed. One failed recipient never stops the others. Every attempt, "
        "whatever the result, is written to notification_log and shown on the lead page. If the app crashes halfway, the "
        "event is re-sent on the next start; messages already delivered for that event are skipped. The enquirer's welcome "
        "only repeats service/country values from the tenant's form options, and at most enquirer-daily-cap messages of one "
        "kind go to one address per day (the rest are logged SKIPPED). Completed events are deleted from event_publication.",
        "notification/LeadNotifications.java · notification/Notifier.java · resources/templates/notifications/*.mustache",
        """
flowchart TD
  A(["LeadCreated committed"]) --> B["Background thread:<br/>set tenant context from the event"]
  B --> C["Load tenant settings<br/>and build message data"]
  C --> D{"Welcome email on<br/>and enquirer gave email?"}
  D -- yes --> D1["Send welcome email<br/>from template via SMTP"]
  D -- no --> E
  D1 --> E{"Welcome WhatsApp on?"}
  E -- yes --> E1["Send welcome WhatsApp<br/>demo sender: logged only"]
  E -- no --> F
  E1 --> F["Find staff whose role has<br/>LEAD_NEW_ALERT<br/>default: Admin, Receptionist"]
  F --> G["Send new-lead alert email<br/>to each one with an email"]
  G --> H["Each attempt saved to notification_log:<br/>SENT / FAILED / DEMO / SKIPPED"]
  H --> I(["Event marked complete"])
  I -.-> J["Lead page shows them<br/>refreshes every 5 s"]
""")

section("Flows", "flow-student-updates", "Keeping the student informed, and repeat enquiries",
        "Messages to the enquirer after the welcome, all following the tenant setting notifications.studentUpdates "
        "(default on). Staff wording is never forwarded: the close reason stays internal. A repeat enquiry alerts the "
        "counsellor who owns the lead, or the new-lead alert staff when nobody does. (Feed @mentions of the student "
        "arrive with the Phase 3 feed.)",
        "notification/LeadNotifications.java · resources/templates/notifications/lead-status-*, repeat-enquiry-*",
        """
flowchart TD
  A1(["LeadAssigned"]) --> S1["Student: 'X is now your counsellor'"]
  A2(["LeadStatusChanged"]) --> T{"New status"}
  T -- CONTACTED --> S2["Student: 'your enquiry is in progress'"]
  T -- CLOSED --> S3["Student: 'your enquiry has been closed'<br/>(no internal reason)"]
  T -- "NEW, from CLOSED" --> S4["Student: 'your enquiry has been reopened'"]
  T -- "NEW, otherwise" --> S0["Nothing sent"]
  A3(["LeadRepeatEnquiry"]) --> R{"Lead assigned?"}
  R -- yes --> R1["Alert email to that counsellor"]
  R -- no --> R2["Alert email to LEAD_NEW_ALERT staff"]
  R1 & R2 --> S5["Student: 'we have added your message<br/>to your enquiry'"]
  S1 & S2 & S3 & S4 & S5 --> W{"studentUpdates on?"}
  W -- yes --> X["Email (if the lead has one) + WhatsApp,<br/>each attempt in notification_log"]
  W -- no --> Y["Nothing sent to the student"]
""")

section("Flows", "flow-signin", "Signing in",
        "Production: mobile number + the personal sign-in code the business's Admin set (no SMS). Only a bcrypt hash of "
        "the code is stored. A code belongs to one membership, so it opens only the businesses whose own code it matches, "
        "never every business the phone works for. 5 wrong codes lock that phone for 15 minutes; 20 failures from one "
        "address in 10 minutes pause that address. Setting a new code ends sessions opened with the old one. Development "
        "also has a dev login (phone only), which refuses to start with the prod profile. Supabase tokens (email sign-in, "
        "planned AUTH2) are accepted when SUPABASE_URL is set; they link open invites by verified phone.",
        "frontend/app/login/page.tsx · identity/security/CodeSignInController · identity/StaffSignIn · "
        "identity/security/StaffTokenDecoders · identity/StaffDirectory.memberships",
        """
flowchart TD
  A(["Staff enters mobile number<br/>and sign-in code"]) --> B["POST /api/v1/auth/phone-code"]
  B --> L{"Phone or address<br/>locked out?"}
  L -- yes --> L1["429 + Retry-After"]
  L -- no --> C["Find this phone's staff rows with a code,<br/>in every business (system context)"]
  C --> D{"Code matches<br/>any row's hash?"}
  D -- no --> D1["401 'Wrong mobile number or sign-in code'<br/>(same answer for an unknown phone)"]
  D -- yes --> E["Matched rows: INVITED to ACTIVE<br/>token lists only those rows (12 h)"]
  E --> F["GET /api/v1/me/memberships"]
  F --> G{"Token issuer known, signature,<br/>expiry, audience OK?"}
  G -- no --> G1["401"]
  G -- yes --> H["Rows from the token, still active,<br/>code not changed since"]
  H --> K{"How many businesses?"}
  K -- "0" --> K0(["Not registered as staff"])
  K -- "1" --> K1(["Remember business, open Leads"])
  K -- "2 or more" --> K2(["Staff picks the organisation"])
""",
        tables=[("Who sets codes", ["When", "Who", "How"], [
            ["Onboarding", "SoftZenith platform admin", "Wizard, Staff step: a code per person (at least the first Admin); never kept in the draft or the saved file"],
            ["After go-live", "The business's Admin (USER_MANAGE)", "Staff page, Set / Change sign-in code; shown once to hand over; never for a role stronger than their own"],
            ["Forgotten code", "The Admin", "Set a new one (codes cannot be read back); the old code and its sessions stop working"],
        ])])

section("Flows", "flow-request", "Every staff request (the common pipeline)",
        "Every screen after sign-in goes through these checks. Role and permissions are read from the database on each "
        "request, so a role change applies immediately. The tenant filter means a query can never return another "
        "business's data, even if code forgets to check.",
        "frontend/lib/api.ts · identity/security/SecurityConfig.java · identity/security/StaffContextFilter.java · shared/tenant/*",
        """
flowchart TD
  A(["Screen needs data"]) --> B["API client adds headers:<br/>Authorization: Bearer token<br/>X-Tenant-ID"]
  B --> C["Next.js proxy to Spring Boot"]
  C --> D{"Token valid?"}
  D -- no --> D1["401: browser clears session,<br/>goes to login"]
  D -- yes --> E{"Active staff of the<br/>requested tenant?"}
  E -- no --> E1["403, or 400 if no tenant chosen<br/>and user has several"]
  E -- yes --> F["Signed-in staff built:<br/>role, permissions, branch<br/>tenant context set"]
  F --> G{"Role has this endpoint's<br/>permission? e.g. LEAD_VIEW"}
  G -- no --> G1["403: no permission"]
  G -- yes --> H["Controller, service, database<br/>every query filtered by tenant_id"]
  H --> I{"Business rules OK?"}
  I -- no --> I1["404 / 409 / 400<br/>with a readable reason"]
  I -- yes --> J(["200 JSON, tenant context cleared"])
""")

section("Flows", "flow-scope", "Which leads a user can see (data scope)",
        "Each role has a data scope. The lead list is filtered by it, and every action that loads one lead (open, edit, "
        "status, assign, history, messages) uses the same check, answering 404 so a user cannot even learn that a lead "
        "outside their scope exists. Seeded roles (owner, 26 Sep 2026; V7): Admin and Receptionist ALL, Branch Manager "
        "BRANCH, Counsellor OWN; a tenant changes them in roles.data_scope. A new enquiry is always recorded whatever the "
        "scope of the staff member entering it; a lead a counsellor adds stays unassigned until reception or an admin "
        "assigns it.",
        "lead/LeadService.scopeSpecification · lead/LeadService.inScope · identity/DataScope · identity/Role",
        """
flowchart TD
  A(["Staff opens the lead list<br/>or one lead"]) --> B{"Role's data scope"}
  B -- ALL --> C["Every lead of the tenant"]
  B -- BRANCH --> D["Leads of their branch<br/>+ leads assigned to them"]
  B -- OWN --> E["Only leads assigned to them"]
  C --> F{"Opening one lead:<br/>is it in scope?"}
  D --> F
  E --> F
  F -- no --> G["404: looks like it does not exist"]
  F -- yes --> H(["Shown; edit, status, assign,<br/>history and messages allowed<br/>as the permissions say"])
""")

section("Flows", "flow-assign", "Assigning a lead to a counsellor",
        "Admins and receptionists can assign (LEAD_ASSIGN). Only staff whose role is marked assignable appear in the list "
        "(by default Counsellor and Branch Manager; the dev demo tenant also made its Super Admin assignable, as tenant data). "
        "Reassigning works the same way and keeps the history. Assignment does not end the lead: the counsellor works it "
        "until it is converted to a student or closed as lost.",
        "frontend/app/(staff)/leads/[id]/page.tsx (Assign) · lead/LeadService.assign · lead/Lead.assignTo",
        """
flowchart TD
  A(["Admin or Receptionist<br/>opens a lead"]) --> B["Assign box lists staff whose role<br/>is assignable: Counsellor, Branch Manager"]
  B --> C["Picks a person, clicks Assign<br/>PUT /api/v1/leads/id/assignment"]
  C --> D{"Has LEAD_ASSIGN?"}
  D -- "no: e.g. Counsellor" --> D1["403"]
  D -- yes --> E{"Lead exists<br/>in this tenant?"}
  E -- no --> E1["404"]
  E -- yes --> F{"Assignee enabled and<br/>role assignable?"}
  F -- no --> F1["409: cannot be assigned leads"]
  F -- yes --> G{"Lead CLOSED?"}
  G -- yes --> G1["409: reopen it first"]
  G -- no --> H["Set assigned to / at / by<br/>status becomes ASSIGNED"]
  H --> I["ASSIGNED activity: from, to"]
  I --> J["Publish LeadAssigned"]
  J --> K{"Changed by someone else<br/>meanwhile? version check"}
  K -- yes --> K1["409: reload and retry"]
  K -- no --> L(["200: page reloads lead and history"])
  L -.->|"background"| M["Email to the counsellor:<br/>lead assigned to you"]
  L -.->|"background"| M2["Email + WhatsApp to the student:<br/>your counsellor is ..."]
""")

section("Flows", "flow-onboarding", "Onboarding a business (SoftZenith console)",
        "A SoftZenith platform admin signs in at /platform with a username and password (a separate identity from tenant "
        "staff, with its own token and filter chain) and fills in the onboarding form. The preview runs the whole setup in "
        "a transaction and rolls it back, so a ready preview will go live. Going live creates everything in one "
        "transaction. Runbook: docs/onboarding.md.",
        "frontend/components/platform/OnboardingWizard.tsx · platform/web/PlatformController · onboarding/TenantOnboardingService · onboarding/BlueprintValidator · identity/OrganisationProvisioner",
        """
flowchart TD
  A(["Platform admin opens /platform"]) --> B["POST /api/platform/auth/login"]
  B --> C{"Username + password OK?<br/>not locked, under attempt limit"}
  C -- no --> C1["401 same message for every refusal<br/>(5 failures lock 15 min; 10 tries / 10 min / address → 429)"]
  C -- yes --> D["Platform token (4 h), audit LOGIN"]
  D --> E["Wizard: business, enquiry form & messages,<br/>branches, staff (typed, pasted or loaded from file)"]
  E --> F["POST /api/platform/tenants/preview"]
  F --> G{"Blueprint valid?<br/>slug free, an Admin, phones, roles, branches"}
  G -- no --> G1["Problems per field → shown on their step"]
  G1 --> E
  G -- yes --> H["Trial run in a transaction, then rolled back"]
  H --> I["Summary: first lead number, form address, staff"]
  I --> J["POST /api/platform/tenants (Go live)"]
  J --> K["One transaction, system context: tenant + settings,<br/>4 default roles, branches, staff INVITED"]
  K --> L["Audit TENANT_ONBOARDED"]
  L --> M(["Live: /enquiry/slug takes enquiries;<br/>staff activate on first phone sign-in"])
""")

section("Flows", "flow-status", "Changing status, closing and reopening",
        "Admins and counsellors change status (LEAD_CHANGE_STATUS). ASSIGNED is never set here (use Assign). Closing means "
        "the enquiry is lost (not interested, unreachable, ...) and needs a reason; reopening a closed lead needs "
        "LEAD_REOPEN, and a reopened lead goes back to the unassigned queue (its counsellor is cleared, recorded as an "
        "UNASSIGNED activity) for reception to reassign. A lead that goes ahead is not closed but converted to a student "
        "(planned, S1). The database repeats the key rules as constraints, as a last safety net.",
        "frontend/app/(staff)/leads/[id]/page.tsx (ChangeStatus) · lead/LeadService.changeStatus · lead/Lead.changeStatus",
        """
flowchart TD
  A(["Admin or counsellor picks a new status"]) --> B["POST /api/v1/leads/id/status"]
  B --> C{"Has LEAD_CHANGE_STATUS?"}
  C -- no --> C1["403"]
  C -- yes --> D{"Lead currently CLOSED?"}
  D -- yes --> D1{"Has LEAD_REOPEN?"}
  D1 -- no --> C1
  D1 -- yes --> J{"Another open lead for<br/>the same person?"}
  J -- yes --> J1["409: names that lead"]
  J -- no --> E
  D -- no --> E{"Target status"}
  E -- "same as now" --> E0["409: already in that status"]
  E -- ASSIGNED --> E1["409: use Assign instead"]
  E -- CLOSED --> F{"Reason given?"}
  F -- no --> F1["400: reason required"]
  F -- yes --> G["Save reason and closed time"]
  E -- "NEW or CONTACTED" --> H["Clear close reason and time;<br/>if reopening, clear the counsellor"]
  G --> I["STATUS_CHANGED activity<br/>(+ UNASSIGNED on reopen)"]
  H --> I
  I --> K(["200: status updated"])
  K -.->|"background"| L[["Student-update flow:<br/>email + WhatsApp, no close reason"]]
""")

section("Flows", "state-lead", "Lead status lifecycle",
        "The statuses from the PRD and who can move a lead between them. A lead has two endings: CLOSED (lost, with a "
        "reason; an admin can reopen it) and CONVERTED (went ahead and became a student; final). CONVERTED is planned "
        "(S1, not built yet).",
        "lead/LeadStatus.java · lead/Lead.java",
        """
stateDiagram-v2
  [*] --> NEW: enquiry or staff entry
  NEW --> CONTACTED: Admin or Counsellor
  CONTACTED --> NEW: Admin or Counsellor
  NEW --> ASSIGNED: Admin or Receptionist assigns
  CONTACTED --> ASSIGNED: Admin or Receptionist assigns
  ASSIGNED --> ASSIGNED: reassign
  ASSIGNED --> CONTACTED: Admin or Counsellor
  ASSIGNED --> NEW: Admin or Counsellor
  NEW --> CLOSED: Admin or Counsellor, reason required
  CONTACTED --> CLOSED: Admin or Counsellor, reason required
  ASSIGNED --> CLOSED: Admin or Counsellor, reason required
  CLOSED --> NEW: Admin reopens (unassigned)
  CLOSED --> CONTACTED: Admin reopens (unassigned)
  ASSIGNED --> CONVERTED: counsellor converts (planned, S1)
  CONTACTED --> CONVERTED: counsellor converts (planned, S1)
  CONVERTED --> [*]: work continues on the Student
""", kind="state")

section("Flows", "flow-convert", "Converting a lead to a student (planned, S1)",
        "Not built yet: this is the design proposed to the owner. The counsellor working a lead converts it when the "
        "person decides to go ahead. The Student keeps a link to the lead, so the enquiry history stays visible on the "
        "student's page. Students follow the same data scope as leads: a counsellor sees their own students, a branch "
        "manager the branch's students, and an admin all of them. Still to confirm with the owner: who may convert "
        "(proposed: a new LEAD_CONVERT permission for Admin, Branch Manager and Counsellor), the student number format, "
        "and the wording of any message to the student.",
        "planned: student module · V8 migration · LeadService.convert · POST /api/v1/leads/{id}/conversion",
        """
flowchart TD
  A(["Counsellor, Branch Manager or Admin<br/>opens a lead, clicks Convert to student"]) --> B["POST /api/v1/leads/id/conversion"]
  B --> C{"Has LEAD_CONVERT?"}
  C -- no --> C1["403"]
  C -- yes --> D{"Lead within the user's<br/>data scope?"}
  D -- no --> D1["404"]
  D -- yes --> E{"Lead status"}
  E -- CLOSED --> E1["409: reopen it first"]
  E -- CONVERTED --> E2["409: already a student,<br/>link to the student"]
  E -- "NEW, no counsellor" --> E3["409: assign a counsellor first"]
  E -- "ASSIGNED or CONTACTED" --> F{"A student with this<br/>phone + email already exists?"}
  F -- "yes: returning student" --> F1["Link the lead to that student"]
  F -- no --> G["Create the Student: next student number,<br/>name, phone, email, branch, counsellor,<br/>custom fields, link to the lead"]
  F1 --> H["Lead becomes CONVERTED<br/>+ CONVERTED activity in its history"]
  G --> H
  H --> I["Publish LeadConverted"]
  I --> J(["The student's profile opens,<br/>with the lead history on it"])
  I -.->|"background"| K["Messages to the counsellor / student<br/>(wording to be confirmed by the owner)"]
""",
        tables=[("Planned Student record (generic Contact)", ["Field", "Filled from", "Why"], [
            ["student number", "a new per-tenant counter, e.g. WWV-S-000001", "human-readable reference, like lead numbers"],
            ["name, phone, email", "the lead", "the phone is the person's identity; unique per tenant with the email"],
            ["branch", "the lead's branch", "drives BRANCH scope (Branch Manager)"],
            ["counsellor", "the lead's assignee", "drives OWN scope (Counsellor); can be reassigned later"],
            ["lead", "the converted lead", "keeps the enquiry history readable"],
            ["journey stage", "tenant settings, e.g. Counselling, Applications, Visa, Enrolled", "S1's configurable journey stages"],
            ["custom fields", "the lead's custom fields", "tenant-specific data (JSONB)"],
        ])])

section("Flows", "flow-dashboard", "Lead dashboard",
        "Counts leads created in the chosen period, grouped by their current status. Every number links to the lead list "
        "with that filter applied.",
        "frontend/app/(staff)/dashboard/page.tsx · lead/LeadService.stats · lead/LeadRepository (count queries)",
        """
flowchart TD
  A(["Admin opens Dashboard,<br/>picks a period"]) --> B["GET /api/v1/leads/stats?from=..."]
  B --> C{"Has REPORTS_VIEW?"}
  C -- no --> C1["403"]
  C -- yes --> D["Four grouped counts, tenant-filtered:<br/>by status, by source, by counsellor,<br/>open and unassigned"]
  D --> E["Missing statuses and sources shown as 0,<br/>counsellor ids turned into names"]
  E --> F(["Tiles and tables,<br/>refresh every 30 s"])
  F --> G["Click a number: lead list filtered,<br/>e.g. /leads?status=NEW"]
""")

section("Flows", "flow-staff", "Onboarding a staff member",
        "The mobile number is the sign-in identity, so it must be unique within the business. Role and branch must belong "
        "to the same business; the database enforces this too.",
        "frontend/app/(staff)/admin/staff/page.tsx · identity/StaffAdminService.invite",
        """
flowchart TD
  A(["Admin: Staff, + Add staff member"]) --> B["POST /api/v1/users<br/>name, mobile, email, role, branch,<br/>employee code, designation, joined on"]
  B --> C{"Has USER_MANAGE?"}
  C -- no --> C1["403"]
  C -- yes --> C2{"Role's permissions all<br/>held by the admin?"}
  C2 -- no --> C3["403: no privilege escalation"]
  C2 -- yes --> D["Normalise mobile<br/>with the tenant's region"]
  D --> E{"Mobile already used<br/>in this tenant?"}
  E -- yes --> E1["409"]
  E -- no --> F{"Role and branch belong<br/>to this tenant?"}
  F -- no --> F1["404"]
  F -- yes --> G["Save staff member: INVITED"]
  G --> H(["They sign in with that mobile:<br/>linked and ACTIVE"])
""")

section("Flows", "state-staff", "Staff account lifecycle",
        "A disabled user is refused from their very next request. Nobody can change their own role or disable themselves, "
        "or manage a user whose role has permissions they lack.",
        "identity/UserStatus.java · identity/AppUser.java",
        """
stateDiagram-v2
  [*] --> INVITED: Admin adds staff
  INVITED --> ACTIVE: first sign-in with that mobile
  INVITED --> DISABLED: Admin disables
  ACTIVE --> DISABLED: Admin disables
  DISABLED --> INVITED: Admin enables, never signed in
  DISABLED --> ACTIVE: Admin enables, signed in before
  ACTIVE --> INVITED: Admin resets sign-in (identity recreated)
""", kind="state")


# ---------------------------------------------------------------- Debugging
section("Debugging", "dbg-trace", "Tracing a problem from a request id",
        "Every response carries an `X-Request-Id` header and every error body a `requestId`. That id is on every log "
        "line the request produced, including the background notification lines, so one search tells the whole story. "
        "Log lines read `LEVEL [request id|tenant id|user id]`; phones and emails are always masked "
        "(`+91******3210`, `a***@mail.com`).",
        "shared/logging/* · backend/logs/crm.log · application.yml (logging) · application-prod.yml (JSON)",
        """
flowchart TD
  A(["Someone reports: it failed"]) --> B["Get the request id: requestId in the error,<br/>or X-Request-Id in browser dev tools, Network tab"]
  B --> C["Search backend/logs/crm.log for it"]
  C --> D{"What do the lines show?"}
  D -- "4xx access line" --> E["The line just before names the reason:<br/>validation, permission, not found or out of scope,<br/>conflict, rate limit"]
  D -- "500 with a stack trace" --> F["A bug: the first 'at com.softzenith...' frame<br/>is the file and line to open"]
  D -- "200 or 201, then notification lines" --> G["Follow the background lines with the same id:<br/>Email ... sent, failed or skipped"]
  D -- "nothing at all" --> H["The request never reached the API:<br/>Next.js server, CRM_API_URL, backend down"]
  G --> I["Cross-check the lead page, Messages sent<br/>(notification_log)"]
""",
        snippets=[
            ("What the log lines look like (one website enquiry)", """
10:30:27.531  INFO [336c2567-51b9|01a0d8f9-...|] [tomcat-handler-1] LeadService  : Lead WWV-000008 created via WEBSITE_FORM [westernworldvisaservices.com: Keep in touch (home)] (phone +91******8827)
10:30:27.600  INFO [336c2567-51b9|01a0d8f9-...|] [tomcat-handler-1] crm.access   : POST /api/v1/public/tenants/westernworld/enquiries -> 201 in 308 ms
10:30:27.637  INFO [336c2567-51b9|01a0d8f9-...|] [task-1]           Notifier     : Email LEAD_WELCOME sent to l***@example.test (lead 01a0dc16-...)
10:30:27.638  INFO [336c2567-51b9|01a0d8f9-...|] [task-1]           Notifier     : WhatsApp LEAD_WELCOME DEMO to +91******8827 via demo-whatsapp (lead ...)
10:30:27.656  INFO [cc2228fe-9574||]             [tomcat-handler-2] SecurityConfig: 401 GET /api/v1/leads: ... Malformed token
                   ^request id  ^tenant ^user     ^thread            ^logger        ^what happened
"""),
            ("Searching the log", """
# Git Bash
grep 336c2567-51b9 backend/logs/crm.log                  # everything for one request
grep WWV-000009 backend/logs/crm.log                     # everything about one lead
grep " ERROR " backend/logs/crm.log | tail -20           # recent errors, with stack traces below them
grep "crm.access" backend/logs/crm.log | grep -- "-> 5"  # server errors

# PowerShell
Select-String -Path backend\\logs\\crm.log -Pattern "336c2567-51b9"
Get-Content backend\\logs\\crm.log -Wait -Tail 50        # follow the log live
"""),
        ])

section("Debugging", "dbg-status", "What each error code means",
        "Every error is an RFC 9457 problem JSON with `status`, `detail` and `requestId`. The `detail` text is written "
        "for people; the log has the technical reason.",
        "shared/web/GlobalExceptionHandler · identity/security/ProblemResponses · identity/security/SecurityConfig",
        tables=[("", ["Status", "Means", "Typical causes", "Where it is produced", "What to check"], [
            ["400 Bad Request", "The request is wrong", "Missing or invalid field (listed in `detail`); name with digits or a URL; closing a lead without a reason; bad or missing `X-Tenant-ID` for a user in several tenants", "`GlobalExceptionHandler`, `StaffContextFilter`, entity rules (`Lead.changeStatus`)", "`detail` names the field; log line `400 validation failed: ...`"],
            ["401 Unauthorized", "Not signed in", "No token, expired or forged token, unknown issuer, a code changed since sign-in", "Spring Security, `SecurityConfig` entry point", "Log `401 GET /path: <reason>`; the staff app clears the session and goes to login"],
            ["403 Forbidden", "Signed in but not allowed", "Role lacks the endpoint's permission; not active staff of that tenant; captcha failed on the public form; granting a role stronger than your own", "`@PreAuthorize`, `StaffContextFilter`, `PublicEnquiryController`, `StaffAdminService`", "Log `403 ...` or `Rejected: subject ...`; compare with the permission matrix"],
            ["404 Not Found", "Nothing to show", "Wrong id; record of another tenant; lead outside the user's data scope (BRANCH / OWN); unknown tenant slug", "`NotFoundException` from services", "Scope first: does the user's role have data_scope ALL?"],
            ["409 Conflict", "Clashes with current data", "Phone already used by another staff member; assigning a closed lead; someone else saved the record first (version check); database constraint", "`ConflictException`, optimistic lock, integrity violation", "Log shows the rule or `constraint=<name>`; reload and retry for concurrent edits"],
            ["429 Too Many Requests", "Slow down", "Public form: more than 5 a minute or 30 an hour from one IP, 5 an hour for one phone, 1000 an hour per tenant", "`EnquiryRateLimiter`", "`Retry-After` header; limits in `crm.public-intake.rate-limit.*`"],
            ["500 Internal Server Error", "A bug or an outage", "Unhandled exception, database down", "`GlobalExceptionHandler.unexpected`", "Log ERROR with the full stack trace under the same request id"],
        ])])

section("Debugging", "dbg-runbook", "Runbook: symptom, cause, fix",
        "Start from what the person sees. Most answers are in the lead page (history and Messages sent), the log for the "
        "request id, or one of the SQL queries below.",
        "",
        tables=[("", ["Symptom", "Likely cause", "How to confirm", "Fix"], [
            ["Enquiry submitted but no new lead in the list", "Same phone + email already has an open lead, so it was added to that lead as a repeat", "Log `Repeat enquiry #n on lead WWV-...`; the older lead has a Repeat badge and sits on top", "Nothing to fix: search by phone. Close the old lead to get a new one next time"],
            ["Website said thank you but the lead is not in my list", "Signed in to another tenant (the dev phones are Demo Visas staff), or the lead is outside your data scope", "`select lead_number, t.slug from leads l join tenants t on t.id = l.tenant_id order by l.created_at desc limit 5`", "Sign in as staff of that tenant (e.g. its onboarded Admin) and pick it on the organisation screen"],
            ["Website visitors get 'Too many enquiries from your network' although few enquire", "The website runs on another host (Vercel) without the proxy secret, so the CRM counts every visitor as the host's address", "Log `Ignored X-Crm-Client-Ip ... proxy secret missing or wrong`, or no such header at all", "Set the same 32+ character value as `CRM_PROXY_SECRET` (website) and `CRM_TRUSTED_PROXY_SECRET` (CRM)"],
            ["Enquiry submitted but no lead at all", "Dropped as a bot (honeypot), refused by rate limit (429) or captcha (403), wrong tenant slug", "Log `dropped: honeypot filled`, `429`, `captcha missing or invalid`; the website's `NEXT_PUBLIC_CRM_TENANT`", "Correct the slug / captcha keys; wait for the rate limit"],
            ["A lead exists but a user cannot see it", "The user's role has data scope BRANCH or OWN", "`roles.data_scope` for their role; the lead's branch and assignee", "Assign it to them, set the lead's branch, or change the role's scope"],
            ["Website form shows an error, nothing in the CRM log", "Website server cannot reach the API", "No `crm.access` line for that time; website server output", "Set `CRM_API_URL` on the website server; start the backend"],
            ["Welcome email not received", "Message FAILED, SKIPPED or never attempted", "Lead page, Messages sent: SENT (check spam / Mailpit), FAILED (error shown), SKIPPED (daily cap of 5), none (event still pending)", "Fix SMTP settings (`MAIL_*`); pending events are re-sent on restart"],
            ["WhatsApp not received", "WhatsApp is demo only", "Status DEMO in Messages sent", "Expected until the WhatsApp Business API is set up (T7)"],
            ["'Wrong mobile number or sign-in code'", "No code set for them, a typo, DISABLED, or the code was changed", "Staff page, Sign-in code column; log `Code sign-in refused for +91******1234`", "The Admin sets a new code (Staff page) and hands it over"],
            ["'Too many wrong codes for this number'", "5 wrong codes: the phone is locked for 15 minutes", "429 with Retry-After; log `phone locked for 15 min`", "Wait, or the Admin sets a new code; then sign in again"],
            ["Staff cannot sign in, 'not registered as staff'", "No staff row for that phone in an active tenant, user DISABLED, or the token does not prove an OTP sign-in", "`app_users` by `phone_e164`; log `Rejected: subject ...`", "Add or enable the staff member; Staff page, Reset sign-in if the identity changed"],
            ["A page shows 'no permission' or a menu item is missing", "The role lacks the permission", "Permission matrix; `role_permissions` for the role", "Grant the permission (DefaultRoles for new tenants + a migration for existing ones)"],
            ["'Record was modified by someone else'", "Two people edited the same lead or staff record", "409 with a version conflict in the log", "Reload and apply the change again"],
            ["Same message sent twice", "Should not happen: re-delivered events skip messages already logged for that event", "`notification_log.event_id` for the lead", "Report with the request id; check `NotifierTests`"],
            ["Backend will not start", "Postgres not running; a migration changed after it ran (checksum); entity and table disagree (`ddl-auto=validate`); dev login enabled with the prod profile", "First ERROR in the console at startup", "Start Postgres; never edit an applied migration, add a new V8...; fix the entity or migration"],
            ["Staff app shows type errors or missing fields after an API change", "Generated client types are stale", "`frontend/lib/api-schema.d.ts` lacks the new field", "Run the backend, then `npm run gen:api` in frontend"],
        ])])

section("Debugging", "dbg-codemap", "Where the code is for each feature",
        "Paths are relative to `backend/src/main/java/com/softzenith/crm/` and `frontend/`; website paths are in the "
        "`westernworld-website` repo. Tests "
        "mirror the backend packages under `backend/src/test/java/...`.",
        "",
        tables=[("", ["Feature", "Backend", "Front end", "Tests"], [
            ["Public enquiry intake", "`lead/web/PublicEnquiryController`, `EnquiryRateLimiter`, `CaptchaVerifier`, `lead/LeadService.intake`, `lead/NewLead`", "`frontend/app/enquiry/[tenant]`; website `components/EnquiryForm.tsx`, `lib/enquiry.ts`", "`PublicIntakeSecurityTests`, `LeadFlowTests`"],
            ["Leads: list, detail, edit, status, assignment", "`lead/web/LeadController`, `lead/LeadService`, `lead/Lead` (status rules), `LeadFilter`, `LeadRepository`", "`app/(staff)/leads`, `leads/[id]`, `components/LeadForm.tsx`", "`LeadFlowTests`"],
            ["Data scope (ALL / BRANCH / OWN)", "`LeadService.scopeSpecification`, `inScope`; `identity/DataScope`, `Role`", "none (the backend decides)", "`LeadScopeTests`"],
            ["Notifications", "`notification/LeadNotifications` (who gets what), `Notifier` (send, cap, idempotency, log), `MessageTemplates`, `resources/templates/notifications/*.mustache`, `DemoWhatsAppSender`", "Lead page, Messages sent", "`NotifierTests`, `LeadFlowTests`"],
            ["Sign-in and tenant choice", "`identity/security/SecurityConfig`, `StaffTokenDecoders`, `CodeSignInController`, `CrmTokens`, `JwtClaims`, `StaffContextFilter`, `DevLogin*`; `identity/StaffSignIn`, `SigninCodes`, `StaffDirectory`", "`app/login`, `lib/auth.ts`, `lib/supabase.ts`, `lib/session.ts`", "`MeControllerTests`, `DevLoginTest`"],
            ["Roles and permissions", "`identity/Permission`, `DefaultRoles`, `Role`, `RoleProvisioner`; migrations V3 to V5 and V7 (data scopes)", "`components/AppShell.tsx` (`NAV`, `can`)", "`TenantOnboardingTests`"],
            ["Staff and branches", "`identity/StaffAdminService`, `web/StaffController`, `web/OrganisationController`", "`app/(staff)/admin/staff`, `admin/branches`", "`StaffAdminTests`"],
            ["Dashboard", "`LeadService.stats`, `LeadRepository` count queries", "`app/(staff)/dashboard`", "`LeadFlowTests`"],
            ["Tenants and settings", "`tenancy/Tenant`, `TenantSettings`, `TenantCounters`; `onboarding/TenantOnboardingService`; `devdata/DevDataSeeder`", "none yet", "`TenantOnboardingTests`"],
            ["Multi-tenancy plumbing", "`shared/tenant/TenantContext`, `HibernateTenancyConfig`, `shared/persistence/TenantScopedEntity`", "none", "tenant isolation cases in each test class"],
            ["Errors and logging", "`shared/web/GlobalExceptionHandler`, `*Exception`; `shared/logging/*`; `identity/security/ProblemResponses`", "`lib/api.ts` (`errorMessage`, 401 handling)", "`LoggingTests`, `MaskTest`"],
            ["Events", "`lead/LeadEvents`; table `event_publication`; `spring.modulith.events.*` in application.yml", "none", "`LeadFlowTests` (async assertions)"],
            ["Database schema", "`resources/db/migration/V1..V7`", "none", "every integration test runs the migrations"],
            ["API types for the staff app", "springdoc at `/v3/api-docs` (dev only)", "`lib/api-schema.d.ts` (generated, do not edit)", "`tsc`"],
            ["Website content and URLs", "none", "`content/*.json`, `content/copy.ts`, `app/**`, `next.config.ts` (proxy, redirects), `app/universities-detail.php/route.ts`", "`tsc`, `lint`, `build`"],
        ])])

section("Debugging", "dbg-run", "Running it locally",
        "Start in this order; each needs the one before it. Everything runs without cloud accounts: dev login instead of "
        "Supabase, Mailpit instead of a real mailbox, demo WhatsApp. Shortcut from the repo root: `.\\dev` (restart all), "
        "`.\\dev stop [-All]`, `.\\dev status`. Dev sign-in phones (mock organisation): 9000000001 Anuj (Super Admin, "
        "Rohtak), 9000000002 Naman (Branch Manager, Rohini), 9000000003 Natasha (Counsellor, Rohtak), 9000000004 Front Desk "
        "(Receptionist), 9000000005 Deepak (Branch Manager, Bahadurgarh), 9000000006 Indu (Counsellor, Rohini), "
        "9000000007 Priya (Counsellor, Bahadurgarh).",
        "CLAUDE.md (Commands) · README.md",
        tables=[("", ["Service", "Port", "Start", "Check"], [
            ["PostgreSQL", "5432", "`\"%LOCALAPPDATA%\\Programs\\pgsql\\bin\\pg_ctl\" -D \"%LOCALAPPDATA%\\crm-pgdata\" -l \"%LOCALAPPDATA%\\crm-pgdata\\server.log\" start`", "DB `crm`, user `crm_app` / `crm_app`"],
            ["Mailpit", "1025 SMTP, 8025 inbox", "`\"%LOCALAPPDATA%\\Programs\\mailpit\\mailpit.exe\"`", "Every local email lands at http://localhost:8025"],
            ["Backend API", "8081", "`cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`", "http://localhost:8081/actuator/health, Swagger at `/swagger-ui.html`, log in `backend/logs/crm.log`"],
            ["CRM staff app", "3000", "`cd frontend && npm run dev` (or `npm run build` then `npx next start -p 3000`)", "http://localhost:3000/login"],
            ["Western World website (own repo)", "3001", "`cd ../westernworld-website && npm run dev -- -p 3001`, or list it in `.dev-sites.json` for `.\\dev`", "http://localhost:3001"],
            ["Backend tests", "none", "`cd backend && ./mvnw test` (embedded Postgres + GreenMail, no Docker)", "Must be green before a task is done"],
        ])],
        snippets=[("Stop everything (PowerShell)", """
# front ends and backend
Get-CimInstance Win32_Process -Filter "Name='node.exe'"  | ? { $_.CommandLine -match 'next' }            | % { Stop-Process -Id $_.ProcessId -Force }
Get-CimInstance Win32_Process -Filter "Name='java.exe'"  | ? { $_.CommandLine -match 'CrmApplication|spring-boot:run' } | % { Stop-Process -Id $_.ProcessId -Force }
# mail and database
Get-Process mailpit -ErrorAction SilentlyContinue | Stop-Process -Force
& "$env:LOCALAPPDATA\\Programs\\pgsql\\bin\\pg_ctl.exe" -D "$env:LOCALAPPDATA\\crm-pgdata" stop -m fast
""")])

section("Debugging", "dbg-config", "Settings that change behaviour",
        "Environment variables for deployment and the `application.yml` keys worth knowing when debugging. Local "
        "development needs none of the variables; with the `prod` profile the database ones and `CRM_AUTH_TOKEN_SECRET` are required.",
        "backend/.env.example · frontend/.env.example · backend/src/main/resources/application*.yml",
        tables=[("", ["Setting", "Where", "Default", "Effect"], [
            ["`DB_URL`, `DB_USER`, `DB_PASSWORD`", "backend", "local crm / crm_app", "Database connection (required in prod)"],
            ["`CRM_AUTH_TOKEN_SECRET`", "backend", "random per start", "Signs staff sessions from mobile + code sign-in (required in prod, 32+ characters)"],
            ["`SUPABASE_URL`", "backend", "empty", "Optional Supabase project whose sign-in tokens are also accepted (email sign-in, AUTH2)"],
            ["`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`", "backend", "Mailpit on localhost:1025", "Where emails go and who they come from"],
            ["`FRONTEND_URL`", "backend", "http://localhost:3000", "Links to leads inside staff emails"],
            ["`CRM_TRUSTED_PROXY_SECRET`", "backend", "empty (ignored)", "Shared with a tenant website on another host (its `CRM_PROXY_SECRET`); with it the website passes each visitor's address (`X-Crm-Client-Ip`) for the rate limits and captcha"],
            ["`TURNSTILE_SECRET`", "backend", "empty (no captcha)", "Captcha required on public enquiries when set"],
            ["`LOG_FILE`", "backend", "logs/crm.log", "Log file path (20 MB files, 30 days, 2 GB cap)"],
            ["`crm.public-intake.rate-limit.*`", "application.yml", "5/min and 30/h per IP, 5/h per phone, 1000/h per tenant", "Public form limits, answered with 429"],
            ["`crm.notifications.enquirer-daily-cap`", "application.yml", "5", "Messages of one kind to one enquirer per 24 h; the rest are logged SKIPPED"],
            ["`logging.level.com.softzenith.crm`", "application.yml", "info (debug in dev)", "More detail; add `logging.level.org.hibernate.SQL=debug` to see SQL"],
            ["`CRM_API_URL`", "both front ends", "http://localhost:8081", "Where the Next.js server forwards API calls"],
            ["`NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`", "staff app", "empty", "Supabase sign-in on the login page (unused until AUTH2)"],
            ["`CRM_PROXY_SECRET`", "website", "empty", "The CRM's `CRM_TRUSTED_PROXY_SECRET`; `proxy.ts` sends it with the visitor's address (required in production)"],
            ["`NEXT_PUBLIC_CRM_TENANT`", "website", "westernworld", "Tenant slug that receives the website's enquiries; must equal `tenants.slug` (onboarded Western World: `western-world-visa-services`)"],
            ["`NEXT_PUBLIC_TURNSTILE_SITE_KEY`", "both front ends", "empty", "Shows the captcha widget on enquiry forms"],
        ])])

section("Debugging", "dbg-sql", "Useful database queries",
        "Run in psql or any SQL client against the `crm` database. Direct SQL is not tenant-filtered, so these see every "
        "tenant: filter by `tenant_id` when it matters. Read only; change data through the app so history and events "
        "stay correct.",
        "psql: \"%LOCALAPPDATA%\\Programs\\pgsql\\bin\\psql\" -U crm_app -d crm",
        snippets=[("", """
-- A lead, its history and every message sent for it
select id, lead_number, status, enquiry_count, last_enquiry_at, assigned_to, branch_id
  from leads where lead_number = 'WWV-000009';
select created_at, type, from_value, to_value, note
  from lead_activities where lead_id = (select id from leads where lead_number = 'WWV-000009') order by created_at;
select created_at, channel, kind, recipient, status, error, event_id
  from notification_log where lead_id = (select id from leads where lead_number = 'WWV-000009') order by created_at;

-- Find leads by phone (stored as E.164)
select lead_number, full_name, status, created_at from leads where phone_e164 = '+919876543210' order by created_at;

-- Events not delivered yet (notifications pending or failing); re-sent on restart
select publication_date, event_type, listener_id, completion_attempts
  from event_publication where completion_date is null order by publication_date;

-- A staff member: status, role, scope and permissions
select u.full_name, u.phone_e164, u.status, r.name as role, r.data_scope, string_agg(rp.permission, ', ') as permissions
  from app_users u join roles r on r.id = u.role_id left join role_permissions rp on rp.role_id = r.id
 where u.phone_e164 = '+919000000003'
 group by u.full_name, u.phone_e164, u.status, r.name, r.data_scope;

-- Messages skipped or failed today
select created_at, kind, recipient, status, error from notification_log
 where status in ('FAILED', 'SKIPPED') and created_at > now() - interval '1 day' order by created_at desc;
""")])


# ---------------------------------------------------------------- Markdown
def build_md():
    out = ["# CRM flows (Phase 1)", "",
           "> Generated from one source together with the shareable page. Diagrams are Mermaid: they render on GitHub and in VS Code.",
           "", INTRO, "", "## Legend", ""]
    out += [f"- **{a}**: {b}" for a, b in LEGEND]
    out += ["", "## Contents", ""]
    group = None
    for s in SECTIONS:
        if s["group"] != group:
            group = s["group"]
            out.append(f"- **{group}**")
        out.append(f"  - [{s['title']}](#{s['id']})")
    out += ["  - [Who can do what](#permissions)", ""]
    group = None
    for s in SECTIONS:
        if s["group"] != group:
            group = s["group"]
            out += [f"## {group}", ""]
        out += [f'<a id="{s["id"]}"></a>', f"### {s['title']}", "", s["text"], ""]
        if s["diagram"]:
            out += ["```mermaid", s["diagram"], "```", ""]
        for caption, headers, rows in s["tables"]:
            if caption:
                out += [f"**{caption}**", ""]
            out += ["| " + " | ".join(headers) + " |", "|" + "---|" * len(headers)]
            out += ["| " + " | ".join(c.replace("|", "\\|") for c in r) + " |" for r in rows]
            out.append("")
        for caption, text in s["snippets"]:
            if caption:
                out += [f"**{caption}**", ""]
            out += ["```", text.strip("\n"), "```", ""]
        if s["code"]:
            out += [f"*Where in the code:* `{s['code']}`", ""]
    out += ['<a id="permissions"></a>', "## Who can do what (default roles, editable per tenant)", "",
            "| Permission | Allows | " + " | ".join(ROLES) + " |",
            "|---|---|" + "---|" * len(ROLES)]
    for p, desc, flags in PERMISSIONS:
        out.append(f"| `{p}` | {desc} | " + " | ".join(f if isinstance(f, str) else ("yes" if f else "") for f in flags) + " |")
    out += ["", "Source: `identity/DefaultRoles.java` (new tenants) and migrations V3/V4/V5/V7 (existing tenants). "
            "The dev demo tenant also made its Admin role (named Super Admin) assignable, as tenant data.", ""]
    return "\n".join(out)


# ---------------------------------------------------------------- HTML
def inline(text):
    """Escape, then turn `code` spans into <code> elements."""
    out = html.escape(text).split("`")
    return "".join(f"<code>{p}</code>" if i % 2 else p for i, p in enumerate(out))


def build_html():
    e = html.escape
    toc, body = [], []
    group = None
    for s in SECTIONS:
        if s["group"] != group:
            if group is not None:
                toc.append("</ul></div>")
            group = s["group"]
            toc.append(f'<div class="toc-group"><p class="eyebrow">{e(group)}</p><ul>')
            body.append(f'<h2 class="group-title" id="g-{e(group.lower().replace(" ", "-"))}">{e(group)}</h2>')
        toc.append(f'<li><a href="#{s["id"]}">{e(s["title"])}</a></li>')
        kind = {"usecase": "Use-case diagram", "state": "State diagram", "sequence": "Sequence diagram",
                "er": "Data model", "guide": "Guide"}.get(s["kind"], "Flowchart")
        parts = [f'<section class="flow" id="{s["id"]}">',
                 f'  <header><span class="kind">{kind}</span><h3>{e(s["title"])}</h3></header>',
                 f'  <p class="lede">{inline(s["text"])}</p>']
        if s["diagram"]:
            parts.append(f'  <div class="diagram"><pre class="mermaid">{e(s["diagram"])}</pre></div>')
        for caption, headers, rows in s["tables"]:
            if caption:
                parts.append(f'  <p class="caption">{e(caption)}</p>')
            head = "".join(f"<th>{e(h)}</th>" for h in headers)
            trs = "".join("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>" for r in rows)
            parts.append(f'  <div class="table-wrap"><table class="guide"><thead><tr>{head}</tr></thead><tbody>{trs}</tbody></table></div>')
        for caption, text in s["snippets"]:
            if caption:
                parts.append(f'  <p class="caption">{e(caption)}</p>')
            parts.append(f'  <pre class="snippet"><code>{e(text.strip(chr(10)))}</code></pre>')
        if s["code"]:
            parts.append(f'  <p class="code"><span>Where in the code</span> {e(s["code"])}</p>')
        parts.append("</section>")
        body.append("\n".join(parts))
    toc.append("</ul></div>")
    toc.append('<div class="toc-group"><p class="eyebrow">Reference</p><ul><li><a href="#permissions">Who can do what</a></li></ul></div>')

    rows = "".join(
        f"<tr><td><code>{e(p)}</code></td><td>{e(d)}</td>" + "".join(
            f'<td class="c">{e(f) if isinstance(f, str) else ("<span class=yes>&#10003;</span>" if f else "<span class=no>&middot;</span>")}</td>' for f in flags) + "</tr>"
        for p, d, flags in PERMISSIONS)
    legend = "".join(f"<li><strong>{e(a)}</strong> {e(b)}</li>" for a, b in LEGEND)

    return f"""<title>SoftZenith CRM Flows</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500&family=IBM+Plex+Sans:wght@400;500;600&family=IBM+Plex+Sans+Condensed:wght@600;700&display=swap">
<style>
:root {{
  --bg: #f5f7f9; --surface: #ffffff; --ink: #172030; --muted: #586274; --line: #dde3ea;
  --accent: #0d6e6a; --accent-soft: #e2f1ef; --yes: #1d7a46; --code-bg: #eef2f5;
  --sans: "IBM Plex Sans", system-ui, -apple-system, "Segoe UI", sans-serif;
  --cond: "IBM Plex Sans Condensed", "IBM Plex Sans", system-ui, sans-serif;
  --mono: "IBM Plex Mono", ui-monospace, "Cascadia Mono", Consolas, monospace;
}}
@media (prefers-color-scheme: dark) {{
  :root:not([data-theme="light"]) {{
    color-scheme: dark;
    --bg: #0f141b; --surface: #161d27; --ink: #e5e9ef; --muted: #9aa5b5; --line: #273142;
    --accent: #4fc4b7; --accent-soft: #15302f; --yes: #5fd08e; --code-bg: #1c2532;
  }}
}}
:root[data-theme="dark"] {{
  color-scheme: dark;
  --bg: #0f141b; --surface: #161d27; --ink: #e5e9ef; --muted: #9aa5b5; --line: #273142;
  --accent: #4fc4b7; --accent-soft: #15302f; --yes: #5fd08e; --code-bg: #1c2532;
}}
* {{ box-sizing: border-box; }}
body {{ background: var(--bg); color: var(--ink); font: 15px/1.6 var(--sans); padding-inline: 16px; padding-block: 0 64px; }}
a {{ color: var(--accent); text-underline-offset: 3px; }}
a:focus-visible {{ outline: 2px solid var(--accent); outline-offset: 2px; border-radius: 2px; }}
.wrap {{ max-width: 1180px; margin: 0 auto; display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 48px; }}
.masthead {{ grid-column: 1 / -1; padding-block: 48px 8px; border-bottom: 1px solid var(--line); }}
.eyebrow {{ font: 500 12px/1.2 var(--mono); letter-spacing: .08em; text-transform: uppercase; color: var(--muted); margin: 0 0 8px; }}
h1 {{ font: 700 clamp(30px, 5vw, 44px)/1.1 var(--cond); margin: 0 0 12px; text-wrap: balance; }}
.intro {{ max-width: 68ch; color: var(--muted); margin: 0 0 24px; }}
.legend {{ list-style: none; padding: 0; margin: 0 0 24px; display: flex; flex-wrap: wrap; gap: 8px 20px; font-size: 13px; color: var(--muted); }}
.legend strong {{ color: var(--ink); font-weight: 600; margin-right: 4px; }}
nav.toc {{ position: sticky; top: calc(env(safe-area-inset-top, 0px) + 24px); align-self: start; padding-top: 32px; max-height: calc(100vh - 48px); overflow-y: auto; }}
.toc-group {{ margin-bottom: 20px; }}
.toc-group ul {{ list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; }}
.toc-group a {{ display: block; padding: 4px 10px; border-left: 2px solid var(--line); color: var(--ink); text-decoration: none; font-size: 14px; }}
.toc-group a:hover {{ border-left-color: var(--accent); color: var(--accent); }}
main {{ min-width: 0; padding-top: 16px; }}
.group-title {{ font: 700 13px/1.2 var(--mono); letter-spacing: .1em; text-transform: uppercase; color: var(--accent); margin: 40px 0 0; padding-bottom: 8px; border-bottom: 2px solid var(--accent); }}
.flow {{ padding-block: 28px; border-bottom: 1px solid var(--line); scroll-margin-top: 16px; }}
.flow header {{ display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; }}
.kind {{ font: 500 11px/1 var(--mono); text-transform: uppercase; letter-spacing: .08em; color: var(--accent); background: var(--accent-soft); padding: 5px 8px; border-radius: 4px; }}
h3 {{ font: 600 24px/1.2 var(--cond); margin: 0; text-wrap: balance; }}
.lede {{ max-width: 70ch; margin: 10px 0 16px; }}
.diagram {{ background: var(--surface); border: 1px solid var(--line); border-radius: 8px; padding: 20px; overflow-x: auto; }}
.diagram pre.mermaid {{ margin: 0; display: flex; justify-content: center; min-width: min-content; background: transparent; }}
.code {{ font: 12.5px/1.5 var(--mono); color: var(--muted); margin: 12px 0 0; overflow-wrap: anywhere; }}
.code span {{ display: inline-block; color: var(--ink); font-weight: 500; margin-right: 8px; }}
.table-wrap {{ overflow-x: auto; background: var(--surface); border: 1px solid var(--line); border-radius: 8px; }}
table {{ border-collapse: collapse; width: 100%; font-size: 14px; }}
th, td {{ text-align: left; padding: 10px 14px; border-bottom: 1px solid var(--line); vertical-align: top; }}
th {{ font: 600 12px/1.3 var(--mono); text-transform: uppercase; letter-spacing: .05em; color: var(--muted); white-space: nowrap; }}
tr:last-child td {{ border-bottom: 0; }}
td code {{ font: 12.5px var(--mono); background: var(--code-bg); padding: 2px 6px; border-radius: 4px; white-space: nowrap; }}
td.c, th.c {{ text-align: center; }}
.yes {{ color: var(--yes); font-weight: 700; }}
.no {{ color: var(--line); }}
.note {{ color: var(--muted); font-size: 13px; margin-top: 12px; }}
.lede code {{ font: 12.5px var(--mono); background: var(--code-bg); padding: 1px 5px; border-radius: 4px; }}
table.guide td {{ font-size: 13.5px; }}
table.guide td code {{ white-space: normal; overflow-wrap: anywhere; }}
table.guide td:first-child {{ font-weight: 600; min-width: 150px; }}
.caption {{ font: 600 12px/1.3 var(--mono); text-transform: uppercase; letter-spacing: .06em; color: var(--muted); margin: 20px 0 8px; }}
pre.snippet {{ background: var(--surface); border: 1px solid var(--line); border-radius: 8px; padding: 14px 16px; overflow-x: auto; margin: 0; }}
pre.snippet code {{ font: 12.5px/1.55 var(--mono); color: var(--ink); white-space: pre; }}
@media (max-width: 860px) {{
  .wrap {{ grid-template-columns: minmax(0, 1fr); gap: 0; }}
  nav.toc {{ position: static; max-height: none; padding-top: 24px; display: flex; flex-wrap: wrap; gap: 0 24px; }}
  .masthead {{ padding-top: 32px; }}
}}
@media (prefers-reduced-motion: reduce) {{ html {{ scroll-behavior: auto; }} }}
@media (prefers-reduced-motion: no-preference) {{ html {{ scroll-behavior: smooth; }} }}
</style>

<div class="wrap">
  <div class="masthead">
    <p class="eyebrow">SoftZenith CRM · Phase 1 · first tenant Western World</p>
    <h1>How the CRM works: architecture, flows and debugging</h1>
    <p class="intro">{e(INTRO)}</p>
    <ul class="legend">{legend}</ul>
  </div>
  <nav class="toc" aria-label="Contents">{''.join(toc)}</nav>
  <main>
    {''.join(body)}
    <h2 class="group-title">Reference</h2>
    <section class="flow" id="permissions">
      <header><span class="kind">Matrix</span><h3>Who can do what</h3></header>
      <p class="lede">Default permissions of the four starting roles. Each business can rename roles, add its own and change these ticks without any code change; the code only ever checks permissions, never role names.</p>
      <div class="table-wrap"><table>
        <thead><tr><th>Permission</th><th>Allows</th>{''.join(f'<th class="c">{e(r)}</th>' for r in ROLES)}</tr></thead>
        <tbody>{rows}</tbody>
      </table></div>
      <p class="note">Source: identity/DefaultRoles.java for new tenants, migrations V3, V4, V5 and V7 for existing ones. The dev demo tenant also made its Admin role (named Super Admin) assignable, as tenant data.</p>
    </section>
  </main>
</div>
"""


open(f"{REPO}/docs/flows.md", "w", encoding="utf-8", newline="\n").write(build_md())
open(OUT_HTML, "w", encoding="utf-8", newline="\n").write(build_html())
print(f"{len(SECTIONS)} sections written")
