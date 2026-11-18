-- Identity foundation for Messenger-Pro.
-- Review before applying to any hosted project. Secrets are never stored in SQL.

create extension if not exists pgcrypto;

create table if not exists public.accounts (
  id text primary key,
  phone_e164 text not null unique,
  state text not null default 'active',
  display_name text,
  created_at bigint not null,
  updated_at bigint not null default 0,
  constraint accounts_phone_e164_format check (phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
  constraint accounts_display_name_length check (display_name is null or char_length(display_name) between 1 and 80)
);

create table if not exists public.otp_requests (
  id text primary key,
  phone_e164 text not null,
  verifier text not null,
  salt text not null,
  expires_at bigint not null,
  created_at bigint not null,
  attempts integer not null default 0,
  consumed_at bigint,
  failed_at bigint
);

create table if not exists public.provider_otp_requests (
  id text primary key,
  phone_e164 text not null,
  verification_id text not null,
  expires_at bigint not null,
  created_at bigint not null,
  attempts integer not null default 0,
  consumed_at bigint,
  failed_at bigint
);

create table if not exists public.sessions (
  id text primary key,
  account_id text not null references public.accounts(id) on delete cascade,
  refresh_hash text not null unique,
  expires_at bigint not null,
  created_at bigint not null,
  device_label text,
  revoked_at bigint,
  constraint sessions_device_label_length check (device_label is null or char_length(device_label) between 1 and 64)
);

create table if not exists public.consumed_refresh_tokens (
  refresh_hash text primary key,
  account_id text not null,
  consumed_at bigint not null
);

create table if not exists public.rate_limit_events (
  id bigserial primary key,
  scope text not null,
  subject_hash text not null,
  occurred_at bigint not null
);

create table if not exists public.sms_delivery_events (
  id text primary key,
  otp_request_id text not null,
  provider_message_id text not null,
  state text not null,
  occurred_at bigint not null,
  failure_reason text
);

create table if not exists public.audit_events (
  id bigserial primary key,
  action text not null,
  subject_hash text not null,
  correlation_hash text not null,
  outcome text not null,
  occurred_at bigint not null
);

create index if not exists otp_requests_phone_idx on public.otp_requests (phone_e164);
create index if not exists provider_otp_requests_phone_idx on public.provider_otp_requests (phone_e164);
create index if not exists sessions_account_idx on public.sessions (account_id);
create index if not exists rate_limit_events_scope_subject_idx on public.rate_limit_events (scope, subject_hash, occurred_at);
create index if not exists sms_delivery_events_request_idx on public.sms_delivery_events (otp_request_id, occurred_at);

-- Deny by default. Access is only through server-side code holding a service-role secret;
-- no client-facing RLS policy is granted in this migration.
alter table public.accounts enable row level security;
alter table public.otp_requests enable row level security;
alter table public.provider_otp_requests enable row level security;
alter table public.sessions enable row level security;
alter table public.consumed_refresh_tokens enable row level security;
alter table public.rate_limit_events enable row level security;
alter table public.sms_delivery_events enable row level security;
alter table public.audit_events enable row level security;

revoke all on public.accounts, public.otp_requests, public.provider_otp_requests,
  public.sessions, public.consumed_refresh_tokens, public.rate_limit_events,
  public.sms_delivery_events, public.audit_events from anon, authenticated;

grant select, insert, update, delete on public.accounts, public.otp_requests,
  public.provider_otp_requests, public.sessions, public.consumed_refresh_tokens,
  public.rate_limit_events, public.sms_delivery_events, public.audit_events to service_role;
grant usage, select on sequence public.rate_limit_events_id_seq to service_role;
grant usage, select on sequence public.audit_events_id_seq to service_role;

-- Atomic account upsert. The generated id is preserved on duplicate verification.
create or replace function public.mp_upsert_account(p_phone text, p_now bigint)
returns public.accounts
language plpgsql
security definer
set search_path = public
as $$
declare
  result public.accounts;
begin
  insert into public.accounts (id, phone_e164, state, created_at, updated_at)
  values (encode(gen_random_bytes(16), 'hex'), p_phone, 'active', p_now, p_now)
  on conflict (phone_e164) do update set updated_at = excluded.updated_at
  returning * into result;
  return result;
end;
$$;

revoke all on function public.mp_upsert_account(text, bigint) from public, anon, authenticated;
grant execute on function public.mp_upsert_account(text, bigint) to service_role;
