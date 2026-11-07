#!/bin/sh
# Runs once, when the postgres image creates a new, empty database (docker-entrypoint-initdb.d).
#
# Prod (deploy/.env has DB_MIGRATION_USER): three roles instead of one superuser for everything.
#   POSTGRES_USER    superuser, reachable only from inside this container (`docker compose exec db psql`)
#   CRM_OWNER_USER   owns the database and schema; Flyway runs the migrations as this role
#   CRM_APP_USER     what the API uses for every request: read and write rows, nothing else
# Staging (no DB_MIGRATION_USER): nothing to do, the app login is the superuser as before.
#
# No `exit` and no `set -u`: the entrypoint sources this file instead of running it if the execute bit is lost.
set -e

if [ -n "${CRM_OWNER_USER:-}" ] && [ "${CRM_OWNER_USER}" != "${POSTGRES_USER}" ]; then
	if [ "${CRM_APP_USER}" = "${POSTGRES_USER}" ] || [ "${CRM_APP_USER}" = "${CRM_OWNER_USER}" ]; then
		echo "10-roles.sh: DB_USER must differ from DB_SUPERUSER and DB_MIGRATION_USER" >&2
		false
	fi
	if [ -z "${CRM_OWNER_PASSWORD:-}" ] || [ -z "${CRM_APP_PASSWORD:-}" ]; then
		echo "10-roles.sh: DB_MIGRATION_PASSWORD and DB_PASSWORD must be set" >&2
		false
	fi

	psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
		-v owner="$CRM_OWNER_USER" -v owner_pw="$CRM_OWNER_PASSWORD" \
		-v app="$CRM_APP_USER" -v app_pw="$CRM_APP_PASSWORD" -v db="$POSTGRES_DB" <<-'SQL'
		create role :"owner" login nosuperuser nocreatedb nocreaterole noreplication password :'owner_pw';
		create role :"app" login nosuperuser nocreatedb nocreaterole noreplication password :'app_pw';

		alter database :"db" owner to :"owner";
		revoke all on database :"db" from public;
		grant connect, temporary on database :"db" to :"owner", :"app";

		-- public belongs to pg_database_owner (now the owner role): only it creates tables. The app only reads and
		-- writes rows in the tables the migrations create.
		revoke create on schema public from public;
		grant usage on schema public to :"app";
		alter default privileges for role :"owner" in schema public
			grant select, insert, update, delete on tables to :"app";
		alter default privileges for role :"owner" in schema public
			grant usage, select on sequences to :"app";
	SQL

	# The superuser may not log in over the network, even with its password (pg_hba is first match).
	sed -i "1i host all ${POSTGRES_USER} all reject" "$PGDATA/pg_hba.conf"
	echo "10-roles.sh: roles ${CRM_OWNER_USER} (migrations) and ${CRM_APP_USER} (app) created"
else
	echo "10-roles.sh: single-role database (no DB_MIGRATION_USER), nothing to do"
fi
