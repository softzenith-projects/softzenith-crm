-- Staff sign-in with phone + a personal code set by the tenant's Admin (AUTH1, owner 8 Oct 2026): no SMS gateway.
-- Only a bcrypt hash is stored; a forgotten code is reset, never read back. The code belongs to the membership (this
-- row, one tenant), so one tenant's Admin can never open another tenant's account for the same phone.
alter table app_users
    add column signin_code_hash   text,
    add column signin_code_set_at timestamptz,
    add constraint app_users_signin_code_ck check ((signin_code_hash is null) = (signin_code_set_at is null));

-- Code sign-in looks a phone up across tenants (system context).
create index app_users_phone_idx on app_users (phone_e164);
