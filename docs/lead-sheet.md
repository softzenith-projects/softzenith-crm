# Lead sheet: every enquiry also lands in a Google Sheet

A stopgap until the business uses the AimDesk staff app every day: each enquiry (new or repeat) that AimDesk saves is
also added as a row to a Google Sheet the business can open, filter and download as Excel. AimDesk stays the source of
truth; the sheet is a copy.

```
Website form ──► AimDesk saves the lead ──► AimDesk posts it to the sheet's script ──► new row in the sheet
```

- **Columns:** Received (India time) · Lead number · Enquiry (New / Repeat #2…) · Name · Mobile · Email · Service ·
  Country · Branch · Source · Form / page · Message · Ref.
- **Ref** identifies the enquiry. Keep the column (hide it if you like): it stops a retry from adding the same row twice.
- Only enquiries **after** the sheet is connected are copied.
- If Google is briefly unreachable AimDesk retries 3 times; after that the row is sent again when the server restarts.
- Code: `backend/.../leadsheet/` (listener + client), setting `leadSheet` in the business's settings. Script:
  `deploy/google-sheet/lead-sheet.gs`. Connect / disconnect: `deploy/lead-sheet.sh`.

## Set it up (owner, about 15 minutes)

Use the Google account that should own the sheet (Western World: `namankaushiktalks@gmail.com`).

### 1. Create the sheet

1. Open [sheets.google.com](https://sheets.google.com) signed in as that account → **Blank spreadsheet**.
2. Name it, e.g. `Western World – Enquiries (AimDesk)`. Leave it empty: the header row appears with the first enquiry.

### 2. Add the script

1. In the sheet: **Extensions → Apps Script**.
2. Delete everything in the editor, paste the whole of `deploy/google-sheet/lead-sheet.gs`, click **Save** (disk icon).
   Name the project `AimDesk lead sheet` if asked.

### 3. Publish the script as a web app

1. **Deploy → New deployment** → click the gear next to "Select type" → **Web app**.
2. Description `AimDesk`, **Execute as: Me**, **Who has access: Anyone** → **Deploy**.
3. Google asks for permission: **Authorize access** → pick the account → "Google hasn't verified this app" →
   **Advanced** → **Go to AimDesk lead sheet (unsafe)** → **Allow**. (It is your own script; it only writes to this
   sheet.)
4. Copy the **Web app URL** (it ends in `/exec`).

"Anyone" lets AimDesk's server post to it without a Google login. Every post must carry the secret from step 4, or the
script refuses it.

### 4. Connect the business in AimDesk

On the server (PowerShell on your laptop; put your URL in place of `PASTE-URL`):

```powershell
ssh -i C:\Users\naman\.ssh\softzenith-staging.pem ubuntu@54.253.207.84 "/opt/aimdesk/deploy/lead-sheet.sh westernworldvisaservices 'PASTE-URL'"
```

It prints a **SECRET**. The secret shows only on your screen.

### 5. Give the secret to the script

Apps Script → **Project Settings** (gear, left) → **Script properties** → **Add script property** → Property `SECRET`,
Value = the secret from step 4 → **Save script properties**.

Do this straight after step 4: until the script knows the secret it refuses AimDesk's rows (they are sent again later).

### 6. Test

Fill a form on the website. Within a few seconds the sheet gets a bold header row and your enquiry below it.

### 7. Share (later)

**Share** → add the business's email → **Viewer** (or Editor). Do not use "Anyone with the link": the sheet holds
students' phone numbers. To download: **File → Download → Microsoft Excel (.xlsx)**.

## Change or stop

| Want to | Do |
|---|---|
| New secret (e.g. it leaked) | Step 4 again with the same URL, then step 5 with the new secret |
| A new sheet | Steps 1–5 with the new sheet |
| Stop copying | `deploy/lead-sheet.sh westernworldvisaservices off` on the server |
| Changed the script code | Deploy → **Manage deployments** → edit (pencil) → Version **New version** → Deploy (the URL stays) |

## If rows do not appear

| What you see | Cause | Fix |
|---|---|---|
| Nothing, and the API log says "wrong secret" | Secret missing or mistyped in Script properties | Step 5 (no spaces) |
| API log says "HTTP 401" / a Google sign-in page | Web app access is not "Anyone" | Manage deployments → Who has access: Anyone |
| Nothing at all in the API log | Business not connected, or the server runs older code | Step 4; ask Claude to deploy the latest code |

API log: `cd /opt/aimdesk/deploy && docker compose logs --tail 200 api | grep -i sheet`.
