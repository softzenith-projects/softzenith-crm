# SoftCRM production on Oracle Cloud (OCI), Mumbai

**Goal:** `crm.softzenithlabs.com` is SoftCRM's production server for every business, with real data only.
`crm-staging.softzenithlabs.com` (AWS, Sydney) stays the test server, with test data only. The two never share a
database.

Written 6 Nov 2026. Page version: see the claude.ai link in `HANDOFF.md`.

> **Status 6 Nov 2026:** Claude did steps 3–7, 9 and the server half of step 12 with the OCI CLI (owner's go-ahead).
> Server `softcrm-prod`, fixed IP **137.23.36.25**, compartment `softcrm-prod`, budget US$15. Step 1 result:
> Untiered already uses 4 OCPU / 20 GB, so SoftCRM's server is **paid, ≈ US$9.50–14/month** (case 3 below).
> **7 Nov:** Claude did step 10 with the AWS CLI. The account's organization policy allows only Sydney, so the bucket
> is `softcrm-prod-backups-2213` in **ap-southeast-2** (the DPA must say backups are kept in Australia); the
> write-only key is on the server only and was tested. Still to do: step 2 (MFA) and step 8 (DNS) by the owner;
> PROD1–3 and the deploy by Claude. Details in `HANDOFF.md`.
> **7 Nov, later:** PROD1–3 written (branch `feat/prod-deploy`) and tried on the prod server; DNS and MFA done by the
> owner. Left: commit + release to `main`, then the deploy (step 12) with the owner typing the Gmail app password.

## Decisions (owner, 6 Nov 2026)

| Topic | Decision | Why |
|---|---|---|
| Prod hosting | **OCI, the existing Pay As You Go account** (the one Untiered runs in), home region **Mumbai** (`ap-mumbai-1`) | Free ARM server allowance; data stays in India; account already paid-tier |
| Separation from Untiered | Own **compartment** `softcrm-prod`, own network, own SSH key, own budget alert | One account, but nothing shared between the two projects |
| Server | `VM.Standard.A1.Flex` (ARM), **1 OCPU, 6 GB RAM, 50 GB disk**, Ubuntu 24.04 | Staging runs in 2 GB with 0.8 GB spare; 6 GB leaves room for builds and growth, and leaves half the free allowance to Untiered |
| Backups | Nightly database dump copied to **AWS S3** (outside Oracle) | If the Oracle account is ever locked, the data is still safe |
| Staging | Stays on AWS EC2 Sydney with Mailpit, test data only, branch `develop` | Already running |
| Prod code | Runs releases from branch `main` | `main` = releases (CLAUDE.md); staging tries `develop` first |
| Email (prod) | Google Workspace SMTP first; Amazon SES later is only a settings change | SES for SoftCRM is not set up yet (the Tokyo SES belongs to Untiered) |

## The picture

```
 Students (website form)        Staff (browser)                 You (testing)
          |                           |                               |
 Western World website (Vercel)       |                               |
          | enquiries                 | crm.softzenithlabs.com        | crm-staging.softzenithlabs.com
          v crm-api.softzenithlabs.com v                               v
 +--------------- OCI Mumbai, your account --------------+   +------ AWS EC2 Sydney ------+
 | compartment softcrm-prod                               |   | staging (test data only)   |
 |  Caddy (HTTPS) -> staff app + API -> PostgreSQL        |   | Caddy, app, API, Postgres, |
 | compartment of Untiered (separate, not connected)      |   | Mailpit                    |
 +--------------------------------------------------------+   +----------------------------+
          | nightly backup                 | email
          v                                v
   AWS S3 bucket (Mumbai)        Google Workspace SMTP
```

## Cost

### What Oracle gives free (whole account, shared with Untiered)

From Oracle's [Always Free resources](https://docs.oracle.com/en-us/iaas/Content/FreeTier/resourceref.htm) page, read
6 Nov 2026:

| Resource | Free per month | Note |
|---|---|---|
| ARM (A1) compute | **1,500 OCPU-hours + 9,000 GB-hours = 2 OCPUs + 12 GB RAM** in total | Was 4 OCPUs + 24 GB until 15 Jun 2026 ([InfoQ](https://www.infoq.com/news/2026/07/oracle-cloud-free-tier-limits/)). Some Pay As You Go users report support saying 4 + 24 still applies to them; Oracle has not published that, so this plan counts on **2 + 12** |
| Disk (boot + block volumes) | **200 GB** in total, 5 backups | Home region only |
| Object storage | 20 GB | Not used (backups go to AWS) |
| Outbound data | 10 TB | SoftCRM uses a tiny fraction |
| Email Delivery | 3,000 emails | Not used yet |

### What SoftCRM prod uses, and its price if it falls outside the free allowance

Prices are Oracle's last published list prices (A1: US$0.01 per OCPU-hour, US$0.0015 per GB-hour; disk US$0.0255
per GB-month + US$0.0017 per performance unit per GB-month, 10 units = "Balanced"). Oracle's price page could not be
read automatically on 6 Nov: **check them in Oracle's [cost estimator](https://www.oracle.com/cloud/costestimator.html)
before creating the server.** A month = 730 hours.

| Item | Size | Usage per month | Price if not free |
|---|---|---|---|
| CPU | 1 OCPU | 730 OCPU-hours | US$7.30 |
| Memory | 6 GB | 4,380 GB-hours | US$6.57 |
| Disk | 50 GB, Balanced | 50 GB | US$2.13 |
| Public IP (reserved) | 1 | | US$0 (Oracle lists no charge; confirm in the estimator) |
| Data out | well under 10 GB | | US$0 |
| **Total if nothing were free** | | | **about US$16/month** |

**Rule:** you pay only for what the two projects use together above the free allowance:
`paid = (Untiered + SoftCRM usage) − free allowance`, separately for CPU, memory and disk.

### The three cases (depends on Untiered's server, step 1 tells you which)

| Untiered uses | SoftCRM prod costs | Example |
|---|---|---|
| ≤ 1 OCPU, ≤ 6 GB, ≤ 150 GB disk | **US$0** | Untiered on 1 OCPU / 6 GB |
| 1.5 OCPU, 9 GB | about **US$7/month** (half the CPU + memory paid) | 0.5 OCPU = US$3.65, 3 GB = US$3.29 |
| the full 2 OCPU, 12 GB | about **US$14/month** (US$16 if the disk also goes over 200 GB) | Same as AWS Lightsail Mumbai 2 GB (US$12) |

If Untiered already uses everything, OCI costs about the same as Lightsail. OCI still wins on size (6 GB vs 2 GB)
and keeps everything in one place.

### Everything else

| Item | Cost per month | Note |
|---|---|---|
| Backups in AWS S3 (Mumbai) | **under US$0.50** | ~30 daily dumps of a few MB to tens of MB; S3 Standard is about US$0.025 per GB-month ([S3 pricing](https://aws.amazon.com/s3/pricing/)) |
| Email | US$0 extra | Existing Google Workspace account; SES later about US$0.10 per 1,000 emails |
| DNS records, HTTPS certificates | US$0 | Hostinger DNS you already have; Let's Encrypt through Caddy |
| Staging (AWS EC2 Sydney) | US$0 out of pocket until the credits run out | About US$25/month from the US$100 credits: they last until about **mid-February 2027** (the credits expire 16 Apr 2027 anyway) |

### Totals

| Period | Best case | Worst case |
|---|---|---|
| Now → mid-Feb 2027 | **about US$0.50/month** (S3 only) | **about US$16/month** |
| After the AWS credits end | + staging: about US$25/month, or about US$6/month if you stop it when not testing | same + staging |

**Before the AWS credits end:** AWS closes free-plan accounts that are not upgraded when the free plan ends. Upgrade
the AWS account to paid before then, or the staging server **and the S3 backups** go with it. Check
Billing → Credits monthly.

No one-time costs: the domain is already yours, and the code changes are Claude's work.

## Who does what

| # | Step | Who | Time | Needs |
|---|---|---|---|---|
| 1 | Check the free allowance Untiered leaves | You | 5 min | |
| 2 | MFA on your Oracle login | You | 5 min | |
| 3 | Compartment `softcrm-prod` | You | 2 min | |
| 4 | Budget alert | You | 5 min | 3 |
| 5 | Network and firewall rules | You | 10 min | 3 |
| 6 | Create the server | You | 10 min (+ retries if "out of capacity") | 5 |
| 7 | Fixed public IP | You | 5 min | 6 |
| 8 | DNS `crm` and `crm-api` | You | 5 min | 7 |
| 9 | SSH check, hand over to Claude | You | 5 min | 6 |
| 10 | S3 bucket + backup key | You | 15 min | |
| 11 | Code for prod (PROD1–PROD3) | Claude | about half a day | can start now |
| 12 | Prepare the server and deploy | Claude | about 1 hour | 8, 9, 11 |
| 13 | First console sign-in | You | 5 min | 12 |
| 14 | Move Western World to prod | You + Claude | 1–2 hours | 13 |
| 15 | Clean up staging | Claude (with your go-ahead) | 15 min | 14 |

Your part (steps 1–10) is about 1–1.5 hours in one sitting. Claude writes the code (step 11) in parallel.

---

## Step 1. Check the free allowance Untiered leaves (you, 5 min)

1. Sign in at [cloud.oracle.com](https://cloud.oracle.com). Region at the top right: **India West (Mumbai)**.
2. ☰ menu → **Governance & Administration** → **Limits, Quotas and Usage**.
3. Service: **Compute**. Scope: the availability domain (`...AP-MUMBAI-1-AD-1`).
4. Find these two rows and write down **Used** and **Available**:
   - `Cores for Standard.A1 based VM and BM Instances`
   - `Memory for Standard.A1 based VM and BM Instances (GB)`
5. Service: **Block Volume** → `Total storage for volumes (GB)` → write down **Used**. Boot disks count too.

**Tells you:** which of the three cost cases you are in. Send Claude the numbers (they are not secret).

## Step 2. MFA on your Oracle login (you, 5 min)

One login now protects Untiered and Western World's student data.

1. Profile icon (top right) → **My profile** → **Security** (or "Multi-factor authentication").
2. **Enable multi-factor authentication** → scan the QR code with an authenticator app (Oracle Mobile
   Authenticator, Google Authenticator, Microsoft Authenticator) → enter the code.
3. Save the backup codes in your password manager.

**Worked when:** signing out and in again asks for the 6-digit code.

## Step 3. Compartment `softcrm-prod` (you, 2 min)

A compartment is a folder for cloud resources. Everything for SoftCRM prod goes in it; Untiered's stays where it is.

1. ☰ → **Identity & Security** → **Compartments** → **Create compartment**.
2. Name `softcrm-prod`, description `SoftCRM production (all tenants)`, parent: the root compartment (your tenancy
   name). **Create**.

From now on, choose `softcrm-prod` in the **Compartment** box on the left of every list page before creating anything.

## Step 4. Budget alert (you, 5 min)

1. ☰ → **Billing & Cost Management** → **Budgets** → **Create budget**.
2. Name `softcrm-prod-budget`. Target: **Compartment** → `softcrm-prod`. Schedule monthly. Amount **US$1**
   (raise it to US$20 if step 1 put you in the paid cases).
3. Alert rule: **Actual spend**, 100 %, your email. Add a second rule: **Forecast spend**, 100 %.
4. **Create**.

Optional: a second budget on the **root compartment** (whole account) at the amount you expect for both projects.

## Step 5. Network and firewall rules (you, 10 min)

1. ☰ → **Networking** → **Virtual cloud networks**. Compartment `softcrm-prod`. **Start VCN Wizard** →
   **Create VCN with Internet Connectivity** → **Start VCN Wizard**.
2. Name `softcrm-prod-vcn`. Leave the address ranges as they are. **Next** → **Create**.
3. Open `softcrm-prod-vcn` → **Subnets** → `public subnet-softcrm-prod-vcn` → **Security** (or Security Lists) →
   `Default Security List for softcrm-prod-vcn` → **Security rules** / Ingress rules.
4. Edit the existing **port 22** rule: Source CIDR = your home IP followed by `/32` (find it at
   [whatismyip.com](https://www.whatismyip.com), e.g. `49.36.10.20/32`). Save.
5. **Add ingress rules** (Source CIDR `0.0.0.0/0` for each):

   | Protocol | Destination port | Why |
   |---|---|---|
   | TCP | 80 | HTTPS certificates + redirect to HTTPS |
   | TCP | 443 | The staff app and the API |
   | UDP | 443 | Faster HTTPS (HTTP/3); optional |

**Worked when:** the list shows 22 from your IP only, and 80/443 from everywhere. Do not open 5432 (database) or any
other port.

## Step 6. Create the server (you, 10 min)

1. ☰ → **Compute** → **Instances**. Compartment `softcrm-prod`. **Create instance**.
2. Name `softcrm-prod`.
3. **Image and shape** → **Edit**:
   - **Change shape** → **Virtual machine** → **Ampere** → `VM.Standard.A1.Flex` → **1 OCPU**, **6 GB** memory →
     Select shape.
   - **Change image** → **Ubuntu** → **Canonical Ubuntu 24.04** (the aarch64 build is picked for you with the ARM
     shape) → Select image.
4. **Networking**: VCN `softcrm-prod-vcn`, subnet `public subnet-softcrm-prod-vcn`, **Automatically assign public
   IPv4 address: on** (made permanent in step 7).
5. **Add SSH keys**: **Generate a key pair for me** → **Save private key** (and the public key). Move the private key
   to `C:\Users\naman\.ssh\softcrm-prod.key`. It is the only way into the server: put a copy in your password manager.
6. **Boot volume**: tick **Specify a custom boot volume size** → **50 GB**, performance **Balanced (10 VPU)**.
7. **Create**. Wait until the state is **Running** (1–3 minutes).

**If it says "Out of capacity for shape VM.Standard.A1.Flex":** Oracle has no free ARM servers in Mumbai at that
moment. Try again a few hours later (early morning India time works best), same settings. Pay As You Go accounts
usually get one within a day. Still nothing after a day: tell Claude; the fallback is AWS Lightsail Mumbai 2 GB
(US$12/month), and the rest of this plan stays the same.

## Step 7. Fixed public IP (you, 5 min)

The address the server got in step 6 changes if the server is ever re-created. DNS needs one that never changes.

1. Open the instance → **Networking** (or **Attached VNICs**) → the primary VNIC → **IP administration** /
   **IPv4 addresses**.
2. On the private IP row: ⋮ → **Edit** → **No public IP** → **Update**.
3. ⋮ → **Edit** again → **Reserved public IP** → **Create new reserved IP**, name `softcrm-prod-ip`, in compartment
   `softcrm-prod` → **Update**.
4. Write down the IP address shown.

## Step 8. DNS for `crm` and `crm-api` (you, 5 min)

Hostinger → **Domains** → `softzenithlabs.com` → **DNS / Nameservers** → **Manage DNS records**. Add:

| Type | Name | Points to | TTL |
|---|---|---|---|
| A | `crm` | the IP from step 7 | 300 |
| A | `crm-api` | the IP from step 7 | 300 |

Do not change the `crm-staging*` records.

**Worked when:** in PowerShell, `nslookup crm.softzenithlabs.com 1.1.1.1` shows the new IP (can take 5–30 minutes).

## Step 9. SSH check, hand over to Claude (you, 5 min)

In PowerShell:

```powershell
icacls "C:\Users\naman\.ssh\softcrm-prod.key" /inheritance:r /grant:r "$($env:USERNAME):(R)"
ssh -i "C:\Users\naman\.ssh\softcrm-prod.key" ubuntu@<IP from step 7> "uname -m"
```

**Worked when:** it prints `aarch64`. Tell Claude the IP; Claude does the rest on the server.

## Step 10. S3 bucket for backups (you, 15 min)

In your AWS account (the one with the staging server):

1. Region (top right): **Asia Pacific (Mumbai) ap-south-1**. If the free plan does not allow Mumbai, use Sydney and
   tell Claude: the data agreement then has to say the backups are kept in Australia.
2. **S3** → **Create bucket**: name `softcrm-prod-backups-<random 4 digits>`, **Block all public access: on**,
   **Bucket Versioning: Enable**, default encryption **SSE-S3** → Create.
3. Bucket → **Management** → **Create lifecycle rule** `expire-35-days`: apply to all objects, **Expire current
   versions after 35 days**, **Permanently delete noncurrent versions after 7 days** → Create.
4. **IAM** → **Users** → **Create user** `softcrm-prod-backup` (no console access) → **Attach policies directly** →
   **Create policy** (JSON), replacing the bucket name:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Effect": "Allow",
       "Action": "s3:PutObject",
       "Resource": "arn:aws:s3:::softcrm-prod-backups-XXXX/*"
     }]
   }
   ```

   Name it `softcrm-prod-backup-write`, attach it to the user.
5. User → **Security credentials** → **Create access key** → "Application running outside AWS" → keep the key ID and
   secret in your password manager. **Do not paste them in chat**: Claude's set-up script asks for them on the server.

The key can only add files. A hacked server cannot read or delete old backups, and versioning keeps overwritten
ones. Restores are done with your own AWS login.

## Step 11. Code for prod (Claude, about half a day; can start now)

| Task | What | Why |
|---|---|---|
| **PROD1** | `deploy/prod.env.example`; `deploy/setup-env.sh staging\|prod`; Mailpit and the mail address only on staging (Compose profile, split Caddyfile); memory settings for 6 GB (database cache, Java heap) | Today's files assume staging: Caddy and Compose refuse to start without a Mailpit address. The memory settings also keep the server above Oracle's "idle" threshold (below) |
| **PROD2** | Separate database roles: an owner role that runs migrations, and a non-superuser `crm_app` that runs the app | Audit gap 3: today the app connects as a superuser. Cheapest to fix before prod has data |
| **PROD3** | `deploy/backup.sh` uploads each nightly dump to S3 with the write-only key; a tested restore on a scratch database | Backups off the server, outside Oracle |
| PROD4 (recommended, can follow) | Postgres row-level security on every tenant table, with tests | Audit gap 4: the database itself refuses cross-tenant reads |

PRs into `develop`, tried on staging, then a release to `main` (your go-ahead) before prod runs it.

**Oracle's "idle" rule:** Oracle may reclaim an Always Free instance when, over 7 days, its CPU (95th percentile),
network **and** memory use are all below 20 %. A quiet CRM has low CPU and network, so PROD1 sizes the database cache
and Java memory to keep memory use well above 20 % of 6 GB. The page does not say whether Pay As You Go accounts are
exempt; the memory setting and the S3 backups cover it either way.

## Step 12. Prepare the server and deploy (Claude, about 1 hour)

On the server, over SSH:

1. Ubuntu updates, automatic security updates, Docker Engine + Compose, git.
2. **Oracle's own firewall inside Ubuntu:** Oracle's Ubuntu image blocks everything except SSH with its own
   `iptables` rules, even when the network rules in step 5 allow it. This is the usual reason "HTTPS does not load on
   Oracle". Claude opens 80/443 there and saves the rules so they survive a reboot:
   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p udp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```
3. `git clone` into `/opt/softcrm`, `deploy/setup-env.sh prod` (random secrets generated on the server; asks you for
   the Gmail app password and the S3 key), `deploy/deploy.sh main`.
4. Nightly backup cron (21:30 UTC = 03:00 India) and a first backup visible in S3.
5. Checks from outside: certificates for `crm` and `crm-api`; `https://crm-api.softzenithlabs.com/actuator/health` is
   `UP`; staff paths on `crm-api` answer 404; `https://crm.softzenithlabs.com/login` and `/platform` load.

All our Docker images (Java, Node, PostgreSQL, Caddy) also come in ARM versions, so nothing changes in the code
for ARM.

## Step 13. First console sign-in (you, 5 min)

1. Read the console password on the server yourself (Claude gives the one-line command; it is never shown in chat).
2. `https://crm.softzenithlabs.com/platform` → sign in as `softzenith` → save the password in your password manager.
3. Tell Claude: it removes the two `CRM_PLATFORM_ADMIN_*` lines from `deploy/.env` and restarts the API.

## Step 14. Move Western World to prod (you + Claude, 1–2 hours)

1. **Onboard on prod:** prod console → **Onboard a business** → **Load file**
   `docs/onboarding/westernworld.blueprint.json` → slug **`westernworldvisaservices`** → real Admin(s) with their real
   mobile and sign-in code → **Go live**.
2. **Real enquiries that reached staging:** if the website has been sending to `crm-staging-api` since 16 Oct, those
   enquiries are real. With your go-ahead, Claude exports only the website-form leads from staging to a `.csv`.
   Western World's Admin imports it on prod (**Import leads**), together with their own real data file.
3. **Website:** Vercel → `westernworld-website` → Settings → Environment Variables (Production):
   `CRM_API_URL=https://crm-api.softzenithlabs.com`, `CRM_PROXY_SECRET=` the prod value Claude tells you to read on
   the server, `NEXT_PUBLIC_CRM_TENANT=westernworldvisaservices` → **Redeploy**.
4. **Test:** fill the website form with your own details → the lead appears on **prod**, the alert email arrives in a
   real inbox. Close it with the reason "Test".
5. **Tell Western World's staff:** the address is now `https://crm.softzenithlabs.com`, same mobile + code.

## Step 15. Clean up staging (Claude, with your go-ahead, 15 min)

1. Remove Western World's real data from staging (its leads, staff and the business itself).
2. Re-create it as **`westernworld-test`** with your own phones and test emails, for trying new versions.
3. Staging keeps Mailpit and demo WhatsApp, so nothing from staging reaches a real person.

From then on: every new version and every new setting is tried on staging first, then released to prod.

## Every month (you, 10 min)

- OCI → **Billing & Cost Management** → **Cost analysis**, filter compartment `softcrm-prod`: expected US$0–16.
- AWS → **Billing** → **Credits**: how long staging still runs free; plan the upgrade before they end.
- S3 bucket: last night's backup is there.
- Every 3 months: Claude restores the latest backup into a scratch database to prove it works.

## If something goes wrong

| Symptom | Likely cause | Fix |
|---|---|---|
| "Out of capacity" creating the server | No free ARM capacity in Mumbai right now | Retry later (step 6); after a day, Lightsail fallback |
| `ssh` times out | Your home IP changed (port 22 allows only your IP) | Step 5.4 with the new IP |
| HTTPS does not load, ping works | Ubuntu's own firewall (step 12.2) or ports 80/443 missing in step 5 | Claude checks both |
| Certificate error | DNS not pointing at the server yet when Caddy started | Wait for `nslookup`, Claude restarts Caddy |
| Budget email | Usage above the free allowance | Cost analysis → which resource; tell Claude |
| Website form: "unknown business" | Slug mismatch, or Vercel not redeployed | Exactly `westernworldvisaservices`; redeploy |
