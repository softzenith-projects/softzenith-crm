-- Lead import from a spreadsheet (IMPORT1, owner 3 Nov 2026): a business's Admin uploads its existing enquiries
-- (.xlsx / .csv), matches the columns to lead fields, checks, then imports. No alerts are sent for imported leads.

-- Imported leads keep where they came from: source IMPORT, the label the Admin gave (e.g. "Portal") in source_detail.
alter table leads drop constraint leads_source_type_check;
alter table leads add constraint leads_source_type_check check (source_type in
    ('WEBSITE_FORM', 'META_LEAD_AD', 'WHATSAPP', 'WALK_IN', 'PHONE', 'REFERRAL', 'OTHER', 'IMPORT'));

-- Admins of existing businesses may import; new businesses get it from the Admin template (all permissions).
insert into role_permissions (role_id, permission)
select id, 'LEAD_IMPORT' from roles where is_system and code = 'ADMIN'
on conflict do nothing;

-- The column matches a business last used, so the next file with the same headers is matched for it.
-- header_key = the spreadsheet header, lower-case with spaces and punctuation removed ("Mobile No." -> "mobileno").
create table lead_import_columns (
    id         uuid        primary key default uuidv7(),
    tenant_id  uuid        not null references tenants (id),
    header_key text        not null,
    target     text        not null,
    created_at timestamptz not null default now(),
    created_by uuid,
    updated_at timestamptz not null default now(),
    updated_by uuid,
    version    bigint      not null default 0,
    unique (tenant_id, header_key)
);
