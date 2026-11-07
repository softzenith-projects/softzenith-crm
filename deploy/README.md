# Running SoftCRM on a server

One server per environment runs everything with Docker Compose: PostgreSQL, the API, the staff app and Caddy, which
serves HTTPS for the addresses in `deploy/.env`. `DEPLOY_ENV` in that file says which environment the server is:

| | Staging | Prod |
|---|---|---|
| Server | AWS EC2, Sydney, `/opt/aimdesk` (`docs/staging-deploy.md`) | Oracle Cloud A1, Mumbai, `/opt/softcrm` (`docs/prod-deploy-oci.md`) |
| Data | test data only | all businesses' real data |
| Branch | `develop` (or a feature branch to try it) | `main` only (`deploy.sh` refuses others) |
| Email | test inbox (Mailpit) at `MAIL_DOMAIN` | real SMTP (Google Workspace, later SES) |
| Database logins | one (the app login is the superuser) | three: superuser (only inside the database container), migration owner, app login that only reads and writes rows (`db-init/10-roles.sh`) |
| Caddy | `caddy/staging.Caddyfile` (app, API, test inbox) | `caddy/prod.Caddyfile` (app, API) |
| Backups | on the server, 7 days | on the server, 7 days, and S3 for 35 days |

## First time (Ubuntu with Docker installed)

```bash
sudo mkdir -p /opt/softcrm && sudo chown "$USER" /opt/softcrm          # staging: /opt/aimdesk
git clone https://github.com/softzenith-projects/softzenith-crm.git /opt/softcrm
cd /opt/softcrm
deploy/setup-env.sh prod       # or staging: creates deploy/.env with random secrets, asks for the rest
deploy/deploy.sh main          # staging: deploy/deploy.sh develop. First build: 10-20 min
```

`setup-env.sh` asks for the email login and the S3 bucket (prod). A value left empty can be set later without it
showing on screen: `deploy/env-set.sh MAIL_PASSWORD`, then `cd deploy && docker compose up -d api`.

Then `https://<CRM_API_DOMAIN>/actuator/health` shows `{"status":"UP"}`.

After signing in to the console (`https://<CRM_DOMAIN>/platform`) once, delete the two `CRM_PLATFORM_ADMIN_*` lines
from `deploy/.env` and run `cd deploy && docker compose up -d api`.

A `deploy/.env` made before `DEPLOY_ENV` existed (the first staging server) gets `DEPLOY_ENV=staging` and
`COMPOSE_PROFILES=staging` added by `deploy.sh` on its next run.

## Everyday

| Task | Command (in `/opt/softcrm`, staging `/opt/aimdesk`) |
|---|---|
| Deploy the latest code | `deploy/deploy.sh` (same branch); prod: `deploy/deploy.sh main` |
| What is running | `cd deploy && docker compose ps` |
| API log (last 200 lines, follow) | `cd deploy && docker compose logs -f --tail 200 api` |
| Restart one part | `cd deploy && docker compose restart api` |
| Change one setting or secret | `deploy/env-set.sh NAME`, then `cd deploy && docker compose up -d api` |
| Back up now | `deploy/backup.sh` |
| Prove the newest backup restores | `deploy/restore-check.sh` (scratch database; the real one is not touched) |
| Connect a business's Google Sheet | `deploy/lead-sheet.sh <slug> <web-app-url>` (`off` to stop); see `docs/lead-sheet.md` |
| Database shell (superuser) | `cd deploy && docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d crm'` |

## Nightly backup

```bash
echo '30 21 * * * ubuntu /opt/softcrm/deploy/backup.sh >> /opt/softcrm/deploy/backups/backup.log 2>&1' \
  | sudo tee /etc/cron.d/softcrm-backup
```

Cron uses the server's time zone (`timedatectl`): 21:30 UTC is 03:00 in India. Each dump is checked
(`pg_restore --list`) and kept on the server for 7 days. On prod it is also uploaded to
`s3://<S3_BACKUP_BUCKET>/prod/` with the upload-only key in `~/.aws` (profile `S3_BACKUP_PROFILE`): that key cannot
list, read or delete, so a broken-into server cannot wipe the copies. The bucket keeps each backup 35 days
(versioned; overwritten copies 7 more days).

## Restore a backup

A backup from S3 is downloaded with your own AWS login (the server's key cannot read), then copied to the server:

```bash
aws s3 ls s3://<bucket>/prod/ --region ap-southeast-2                      # on your computer
aws s3 cp s3://<bucket>/prod/crm-YYYYMMDD-HHMM.dump . --region ap-southeast-2
scp -i ~/.ssh/softcrm-prod crm-YYYYMMDD-HHMM.dump ubuntu@<server>:/opt/softcrm/deploy/backups/
```

First prove it restores (`deploy/restore-check.sh backups/crm-YYYYMMDD-HHMM.dump`), then replace the real database:

```bash
cd /opt/softcrm/deploy
docker compose stop api web
docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" crm && createdb -U "$POSTGRES_USER" -O "${CRM_OWNER_USER:-$POSTGRES_USER}" crm'
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d crm --exit-on-error' < backups/crm-YYYYMMDD-HHMM.dump
# Prod only: the new database lets every login connect again; limit it to the two app roles as at first start.
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d crm -c "revoke all on database crm from public; grant connect, temporary on database crm to $CRM_OWNER_USER, $CRM_APP_USER"'
docker compose start api web
```
