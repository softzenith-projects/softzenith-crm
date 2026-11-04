# Running AimDesk on a server

One server runs everything with Docker Compose: PostgreSQL, the API, the staff app, Mailpit (staging test inbox) and
Caddy, which serves HTTPS for the three addresses in `deploy/.env`. The step-by-step plan for the first staging server
is `docs/staging-deploy.md`.

## First time (Ubuntu with Docker installed)

```bash
sudo mkdir -p /opt/aimdesk && sudo chown "$USER" /opt/aimdesk
git clone https://github.com/softzenith-projects/softzenith-crm.git /opt/aimdesk
cd /opt/aimdesk
deploy/setup-env.sh            # creates deploy/.env with random secrets; check the addresses in it
deploy/deploy.sh develop       # builds and starts everything (first build: 10-20 min on 2 GB)
```

Then `https://<CRM_API_DOMAIN>/actuator/health` shows `{"status":"UP"}`.

After signing in to the console (`https://<CRM_DOMAIN>/platform`) once, delete the two `CRM_PLATFORM_ADMIN_*` lines
from `deploy/.env` and run `cd deploy && docker compose up -d api`.

## Everyday

| Task | Command (in `/opt/aimdesk`) |
|---|---|
| Deploy the latest code | `deploy/deploy.sh` (same branch) or `deploy/deploy.sh develop` |
| What is running | `cd deploy && docker compose ps` |
| API log (last 200 lines, follow) | `cd deploy && docker compose logs -f --tail 200 api` |
| Restart one part | `cd deploy && docker compose restart api` |
| Back up now | `deploy/backup.sh` |
| Database shell | `cd deploy && docker compose exec db psql -U crm_app -d crm` |

## Nightly backup

```bash
echo '30 21 * * * ubuntu /opt/aimdesk/deploy/backup.sh >> /opt/aimdesk/deploy/backups/backup.log 2>&1' \
  | sudo tee /etc/cron.d/aimdesk-backup
```

21:30 UTC is 03:00 in India. Dumps stay on the server for 7 days. Before real data, copy them off the server as well.

## Restore a backup

```bash
cd /opt/aimdesk/deploy
docker compose stop api web
docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" crm && createdb -U "$POSTGRES_USER" crm'
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d crm' < backups/crm-YYYYMMDD-HHMM.dump
docker compose start api web
```
