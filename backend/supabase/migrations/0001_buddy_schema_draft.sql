-- Buddy private sync schema, intended for a dedicated Supabase project.
-- NOT DEPLOYED: execute only after an authenticated backend is provisioned.
-- All client queries must use a user-scoped JWT; NEVER embed a service-role key in the APK.
begin;
create extension if not exists pgcrypto;

create table if not exists public.buddy_families (
    id uuid primary key default gen_random_uuid(),
    display_name text not null check (length(display_name) between 1 and 80),
    created_at timestamptz not null default now()
);
create table if not exists public.buddy_memberships (
    family_id uuid not null references public.buddy_families(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null check (role in ('owner','adult','child')),
    active boolean not null default true,
    created_at timestamptz not null default now(),
    primary key(family_id,user_id)
);
create index if not exists buddy_memberships_user on public.buddy_memberships(user_id);

-- No direct grants to insert/update memberships: enrollment must use a
-- trusted backend with authenticated parent consent + short-lived pairing.
create or replace function public.buddy_role(fid uuid)
returns text language sql stable security definer set search_path=''
as $$
    select m.role from public.buddy_memberships m
    where m.family_id=fid and m.user_id=(select auth.uid()) and m.active
    limit 1
$$;
revoke all on function public.buddy_role(uuid) from public;
grant execute on function public.buddy_role(uuid) to authenticated;

create table if not exists public.buddy_events (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null references public.buddy_families(id) on delete cascade,
    created_by uuid not null references auth.users(id),
    title text not null check (length(title) between 1 and 120),
    emoji text not null default '🎉' check (length(emoji) <= 16),
    kind text not null check (kind in ('once','weekly')),
    on_date date,
    starts_on date,
    weekday smallint check (weekday between 0 and 6),
    ends_on date,
    event_time time without time zone,
    time_zone text not null default 'America/New_York',
    owner_role text not null check (owner_role in ('child','parent')),
    visibility text not null default 'child' check (visibility in ('child','parent')),
    exclusions date[] not null default '{}',
    google_event_id text,
    remote_revision bigint not null default 1,
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    check ((kind='once' and on_date is not null)
        or (kind='weekly' and starts_on is not null and weekday is not null)),
    check (ends_on is null or starts_on is null or ends_on >= starts_on)
);
create index if not exists buddy_events_family on public.buddy_events(family_id,updated_at);

create table if not exists public.buddy_routine_progress (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null references public.buddy_families(id) on delete cascade,
    child_user_id uuid not null references auth.users(id),
    local_date date not null,
    task_key text not null check(length(task_key) between 1 and 120),
    completed boolean not null default true,
    updated_at timestamptz not null default now(),
    unique(family_id,child_user_id,local_date,task_key)
);

alter table public.buddy_families enable row level security;
alter table public.buddy_memberships enable row level security;
alter table public.buddy_events enable row level security;
alter table public.buddy_routine_progress enable row level security;

-- Existing members may see only their own family's metadata.
create policy buddy_families_select on public.buddy_families for select to authenticated
using (public.buddy_role(id) is not null);

-- A member may see their own record; adults can inspect family membership.
create policy buddy_memberships_select on public.buddy_memberships for select to authenticated
using (user_id=(select auth.uid())
    or public.buddy_role(family_id) in ('owner','adult'));

-- Child accounts may see only child-visible events. Adults see all.
create policy buddy_events_select on public.buddy_events for select to authenticated
using (public.buddy_role(family_id) in ('owner','adult')
    or (public.buddy_role(family_id)='child' and visibility='child'));

-- Child accounts may create child-owned events only under their own identity.
create policy buddy_events_insert on public.buddy_events for insert to authenticated
with check (created_by=(select auth.uid()) and (
    public.buddy_role(family_id) in ('owner','adult')
    or (public.buddy_role(family_id)='child' and owner_role='child'
        and visibility='child')));

create policy buddy_events_update on public.buddy_events for update to authenticated
using (public.buddy_role(family_id) in ('owner','adult')
    or (public.buddy_role(family_id)='child'
        and owner_role='child' and visibility='child'
        and created_by=(select auth.uid())))
with check (public.buddy_role(family_id) in ('owner','adult')
    or (public.buddy_role(family_id)='child'
        and owner_role='child' and visibility='child'
        and created_by=(select auth.uid())));

create policy buddy_events_delete on public.buddy_events for delete to authenticated
using (public.buddy_role(family_id) in ('owner','adult')
    or (public.buddy_role(family_id)='child'
        and owner_role='child' and created_by=(select auth.uid())));

create policy buddy_progress_select on public.buddy_routine_progress for select to authenticated
using (public.buddy_role(family_id) in ('owner','adult')
    or (public.buddy_role(family_id)='child' and child_user_id=(select auth.uid())));
create policy buddy_progress_insert on public.buddy_routine_progress for insert to authenticated
with check (public.buddy_role(family_id) in ('owner','adult')
    or (public.buddy_role(family_id)='child' and child_user_id=(select auth.uid())));
create policy buddy_progress_update on public.buddy_routine_progress for update to authenticated
using (public.buddy_role(family_id) in ('owner','adult')
    or (public.buddy_role(family_id)='child' and child_user_id=(select auth.uid())))
with check (public.buddy_role(family_id) in ('owner','adult')
    or (public.buddy_role(family_id)='child' and child_user_id=(select auth.uid())));

-- Give only the privileges needed by RLS-approved access.
revoke all on public.buddy_families,public.buddy_memberships,
    public.buddy_events,public.buddy_routine_progress from anon;
revoke all on public.buddy_families,public.buddy_memberships,
    public.buddy_events,public.buddy_routine_progress from authenticated;
grant select on public.buddy_families, public.buddy_memberships to authenticated;
grant select,insert,update,delete on public.buddy_events to authenticated;
grant select,insert,update on public.buddy_routine_progress to authenticated;

commit;

-- BEFORE DEPLOYMENT:
-- 1. Write and test privileged enrollment endpoint for owner creation, adult invitations,
--    child-device pairing, and device revocation.
-- 2. Replace broad UPDATE grants with column-scoped access or a validated RPC so children
--    cannot alter family_id, created_by, external IDs, or server-maintained revisions.
-- 3. Implement server-side revision triggers and enforce restrictions on google_event_id,
--    updated_at and remote_revision; do not trust client values.
-- 4. Test RLS from anonymous, unrelated user, child, parent, revoked user, and forged JWT.
-- 5. Implement Google OAuth server-side, with encrypted token storage and explicit calendar scopes.
-- This is an architectural starting point, NOT a production-ready migration.
