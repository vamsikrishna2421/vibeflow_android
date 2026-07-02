-- VibeFlow — managed Smart Formatting tier: schema + metering.
--
-- Run this once in your Supabase project (Dashboard ▸ SQL Editor ▸ paste ▸ Run).
-- It is idempotent — safe to re-run after edits.
--
-- Model: identity is Supabase Auth (Google sign-in -> rows in auth.users). Each user
-- gets a public.profiles row tracking entitlement (is_pro) and free-trial usage. The
-- client can only READ its own profile (to show "X polishes left"); all metering writes
-- happen server-side from the `polish` Edge Function using the service role, so the
-- count can't be tampered with from the device.

-- ---------------------------------------------------------------------------
-- profiles: one row per user
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
    id                 uuid primary key references auth.users (id) on delete cascade,
    is_pro             boolean   not null default false,   -- entitlement (test-toggle now; billing later)
    free_used          int       not null default 0,       -- free-trial polishes consumed
    free_limit         int       not null default 50,       -- free-trial allowance
    total_polishes     int       not null default 0,
    prompt_tokens      bigint    not null default 0,
    completion_tokens  bigint    not null default 0,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);

alter table public.profiles enable row level security;

-- Free-tier buckets: a lifetime "welcome" allowance (free_used vs config welcome_limit), then a
-- recurring weekly allowance (week_used vs config weekly_limit, rolling every 7 days).
alter table public.profiles add column if not exists week_used  int not null default 0;
alter table public.profiles add column if not exists week_start timestamptz;

-- Daily request rate-limit (anti-abuse / spend cap): per-user requests/day, resets at UTC
-- midnight. Applies even to Pro (whose quota is otherwise unlimited). Limit = app_status.daily_limit.
alter table public.profiles add column if not exists day_used  int  not null default 0;
alter table public.profiles add column if not exists day_start date;

-- Users may READ only their own profile. No client writes at all (metering is server-side).
drop policy if exists "read own profile" on public.profiles;
create policy "read own profile" on public.profiles
    for select using (auth.uid() = id);

-- ---------------------------------------------------------------------------
-- Auto-create a profile when a new auth user signs up.
-- ---------------------------------------------------------------------------
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    insert into public.profiles (id) values (new.id) on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- Metering RPCs — SECURITY DEFINER, callable ONLY by the service role (Edge Function).
-- ---------------------------------------------------------------------------

-- Atomically reserve one polish. Free users draw first from a lifetime "welcome" bucket
-- (free_used vs welcome_limit), then a recurring weekly bucket (week_used vs weekly_limit,
-- rolling every 7 days). A per-device pool (device_usage) mirrors both buckets so many
-- accounts on one install can't farm free polishes. Pro = unlimited. Limits come from the
-- app_status config row. Returns allowed + remaining (of the active bucket; -1 = pro) +
-- is_pro + which bucket was used ('welcome'|'weekly'|'pro') so a refund can undo the right one.
drop function if exists public.reserve_polish(uuid);
drop function if exists public.reserve_polish(uuid, text);
create or replace function public.reserve_polish(p_user uuid, p_device text default null)
returns table(allowed boolean, remaining int, is_pro boolean, bucket text)
language plpgsql
security definer set search_path = public
as $$
-- OUT param `is_pro` collides with profiles.is_pro on bare refs — prefer the column.
#variable_conflict use_column
declare
    v_is_pro     boolean;
    v_free_used  int;
    v_week_used  int;
    v_week_start timestamptz;
    v_day_used   int;
    v_day_start  date;
    c_welcome    int;
    c_weekly     int;
    c_devcap     int;
    c_daily      int;
    d_free       int;
    d_week_used  int;
    d_week_start timestamptz;
    wk interval := interval '7 days';
    weekly boolean;
begin
    insert into public.profiles (id) values (p_user) on conflict (id) do nothing;
    select is_pro, free_used, week_used, week_start, day_used, day_start
      into v_is_pro, v_free_used, v_week_used, v_week_start, v_day_used, v_day_start
      from public.profiles where id = p_user;
    select coalesce(welcome_limit, 50), coalesce(weekly_limit, 20), coalesce(device_cap, 50), coalesce(daily_limit, 500)
      into c_welcome, c_weekly, c_devcap, c_daily
      from public.app_status where id = true;
    c_welcome := coalesce(c_welcome, 50);
    c_weekly  := coalesce(c_weekly, 20);
    c_devcap  := coalesce(c_devcap, 50);
    c_daily   := coalesce(c_daily, 500);

    -- DAILY RATE LIMIT (everyone, including Pro). Resets at UTC midnight. day_used is bumped
    -- only on an allowed reservation and is NOT refunded on upstream failure, so it also
    -- throttles retry-hammering. Returns bucket 'daily' on rejection (the Edge Function maps
    -- any !allowed to 402, so no function/app change is needed).
    if v_day_start is null or v_day_start <> current_date then
        v_day_used := 0;
        update public.profiles set day_used = 0, day_start = current_date where id = p_user;
    end if;
    if v_day_used >= c_daily then
        return query select false, 0, v_is_pro, 'daily'; return;
    end if;

    if v_is_pro then
        update public.profiles set total_polishes = total_polishes + 1, day_used = day_used + 1, updated_at = now() where id = p_user;
        return query select true, -1, true, 'pro'; return;
    end if;

    -- roll the account's weekly window if 7 days have elapsed
    if v_week_start is null or now() >= v_week_start + wk then
        update public.profiles set week_used = 0, week_start = now() where id = p_user;
        v_week_used := 0;
    end if;

    weekly := v_free_used >= c_welcome;   -- welcome exhausted -> weekly bucket

    if p_device is not null then
        insert into public.device_usage (device_id) values (p_device) on conflict (device_id) do nothing;
        select free_used, week_used, week_start into d_free, d_week_used, d_week_start
          from public.device_usage where device_id = p_device;
        if d_week_start is null or now() >= d_week_start + wk then
            update public.device_usage set week_used = 0, week_start = now() where device_id = p_device;
            d_week_used := 0;
        end if;
    end if;

    if not weekly then
        -- WELCOME bucket (lifetime). Device pool guards against many-accounts-one-phone farming.
        if p_device is not null and d_free >= c_devcap then
            return query select false, 0, false, 'welcome'; return;
        end if;
        update public.profiles set free_used = free_used + 1, total_polishes = total_polishes + 1, day_used = day_used + 1, updated_at = now() where id = p_user;
        if p_device is not null then
            update public.device_usage set free_used = free_used + 1, updated_at = now() where device_id = p_device;
        end if;
        return query select true, greatest(c_welcome - v_free_used - 1, 0), false, 'welcome';
    else
        -- WEEKLY bucket (recurring). Capped per-account AND per-device per week.
        if v_week_used >= c_weekly then
            return query select false, 0, false, 'weekly'; return;
        end if;
        if p_device is not null and d_week_used >= c_weekly then
            return query select false, 0, false, 'weekly'; return;
        end if;
        update public.profiles set week_used = week_used + 1, total_polishes = total_polishes + 1, day_used = day_used + 1, updated_at = now() where id = p_user;
        if p_device is not null then
            update public.device_usage set week_used = week_used + 1, updated_at = now() where device_id = p_device;
        end if;
        return query select true, greatest(c_weekly - v_week_used - 1, 0), false, 'weekly';
    end if;
end;
$$;

-- Record token usage after a successful upstream call.
create or replace function public.record_tokens(p_user uuid, p_prompt int, p_completion int)
returns void
language plpgsql
security definer set search_path = public
as $$
begin
    update public.profiles
       set prompt_tokens     = prompt_tokens + greatest(p_prompt, 0),
           completion_tokens = completion_tokens + greatest(p_completion, 0),
           updated_at        = now()
     where id = p_user;
end;
$$;

-- Give back a reserved polish if the upstream call failed — undo the SAME bucket used.
drop function if exists public.refund_polish(uuid);
drop function if exists public.refund_polish(uuid, text);
drop function if exists public.refund_polish(uuid, text, text);
create or replace function public.refund_polish(p_user uuid, p_device text default null, p_bucket text default null)
returns void
language plpgsql
security definer set search_path = public
as $$
begin
    update public.profiles set total_polishes = greatest(total_polishes - 1, 0), updated_at = now() where id = p_user;
    if p_bucket = 'welcome' then
        update public.profiles set free_used = greatest(free_used - 1, 0) where id = p_user;
        if p_device is not null then
            update public.device_usage set free_used = greatest(free_used - 1, 0) where device_id = p_device;
        end if;
    elsif p_bucket = 'weekly' then
        update public.profiles set week_used = greatest(week_used - 1, 0) where id = p_user;
        if p_device is not null then
            update public.device_usage set week_used = greatest(week_used - 1, 0) where device_id = p_device;
        end if;
    end if;
end;
$$;

-- Lock the metering functions down to the service role only.
revoke all on function public.reserve_polish(uuid, text)        from public, anon, authenticated;
revoke all on function public.record_tokens(uuid, int, int)     from public, anon, authenticated;
revoke all on function public.refund_polish(uuid, text, text)   from public, anon, authenticated;
grant execute on function public.reserve_polish(uuid, text)         to service_role;
grant execute on function public.record_tokens(uuid, int, int)      to service_role;
grant execute on function public.refund_polish(uuid, text, text)    to service_role;

-- ---------------------------------------------------------------------------
-- Device seats: an account may have up to `device_limit` concurrently-active
-- devices (the per-device price tier — 1 seat, 2 seats, …). Signing in registers
-- a device; if that pushes the account over its limit, the least-recently-active
-- device is evicted and is signed out on its next polish (which checks seat
-- membership). No phone/laptop distinction — any N devices.
-- ---------------------------------------------------------------------------
alter table public.profiles add column if not exists device_limit int not null default 1;
-- legacy columns from the earlier 1-phone+1-laptop model — no longer used
alter table public.profiles drop column if exists active_mobile_device;
alter table public.profiles drop column if exists active_desktop_device;
alter table public.profiles drop column if exists mobile_claimed_at;
alter table public.profiles drop column if exists desktop_claimed_at;

create table if not exists public.devices (
    user_id    uuid not null references auth.users (id) on delete cascade,
    device_id  text not null,
    last_seen  timestamptz not null default now(),
    created_at timestamptz not null default now(),
    primary key (user_id, device_id)
);
alter table public.devices enable row level security;   -- no client policies: service-role only

-- Per-device free pool (anti-farm): welcome + weekly usage summed across ALL accounts that
-- sign in on one physical install, so throwaway accounts on the same phone share one allowance.
create table if not exists public.device_usage (
    device_id  text primary key,
    free_used  int not null default 0,
    week_used  int not null default 0,
    week_start timestamptz,
    updated_at timestamptz not null default now()
);
alter table public.device_usage enable row level security;   -- service-role only

-- Register this device, then evict anything beyond the account's seat limit
-- (keep the `device_limit` most-recently-seen). p_platform is ignored (kept for
-- signature compatibility with the Edge Functions). Called at sign-in.
create or replace function public.claim_device(p_user uuid, p_device text, p_platform text)
returns void
language plpgsql
security definer set search_path = public
as $$
declare lim int;
begin
    insert into public.profiles (id) values (p_user) on conflict (id) do nothing;
    insert into public.devices (user_id, device_id) values (p_user, p_device)
        on conflict (user_id, device_id) do update set last_seen = now();
    select device_limit into lim from public.profiles where id = p_user;
    delete from public.devices d
     where d.user_id = p_user
       and d.device_id not in (
           select device_id from public.devices
            where user_id = p_user
            order by last_seen desc
            limit greatest(coalesce(lim, 1), 1)
       );
end;
$$;

-- True if this device still holds a seat (refreshes its last_seen); false if evicted.
create or replace function public.check_device(p_user uuid, p_device text, p_platform text)
returns boolean
language plpgsql
security definer set search_path = public
as $$
begin
    update public.devices set last_seen = now() where user_id = p_user and device_id = p_device;
    return found;
end;
$$;

revoke all on function public.claim_device(uuid, text, text) from public, anon, authenticated;
revoke all on function public.check_device(uuid, text, text) from public, anon, authenticated;
grant execute on function public.claim_device(uuid, text, text) to service_role;
grant execute on function public.check_device(uuid, text, text) to service_role;

-- ---------------------------------------------------------------------------
-- Service status / maintenance broadcast + kill-switch. A single row the `polish`
-- function checks BEFORE calling OpenAI: when maintenance=true it short-circuits
-- with the message (so spend stops even if the OpenAI key is compromised, and every
-- app shows the notice + falls back to offline mode). Flip it with one UPDATE.
-- ---------------------------------------------------------------------------
create table if not exists public.app_status (
    id          boolean primary key default true,
    maintenance boolean not null default false,
    message     text not null default '',
    until       timestamptz,
    updated_at  timestamptz not null default now(),
    constraint app_status_singleton check (id)
);
-- Tunable free-tier limits (server-config — change without an app/store update).
alter table public.app_status add column if not exists welcome_limit int not null default 50;
alter table public.app_status add column if not exists weekly_limit  int not null default 20;
alter table public.app_status add column if not exists device_cap    int not null default 50;
alter table public.app_status add column if not exists daily_limit   int not null default 500;
insert into public.app_status (id) values (true) on conflict (id) do nothing;

alter table public.app_status enable row level security;
-- Anyone (even anon) may READ status to show a banner; only the service role writes.
drop policy if exists "read status" on public.app_status;
create policy "read status" on public.app_status for select using (true);
