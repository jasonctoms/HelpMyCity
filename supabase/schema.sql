-- HelpMyCity on Supabase: schema, policies and storage.
--
-- Run this first in a new project's SQL editor, then `seed.sql`, then
-- `schedule.sql`. Put the project URL and publishable key into
-- `local.properties` as `supabase.url` and `supabase.publishableKey`.
--
-- Column names here are the contract `SupabaseRows.kt` encodes to; enum-valued
-- columns hold the domain type's `storageKey`, never its Kotlin constant name.

-- ---------------------------------------------------------------------------
-- Reference data. Optional: a deployment that keeps departments and
-- neighborhoods in its CityProfile can leave both tables empty.
-- ---------------------------------------------------------------------------

-- These are not optional, even though nothing reads them back yet: issues carry
-- a foreign key to `departments`, so an issue pushed with a department id that
-- is missing here is rejected outright. Keep this table in step with the
-- deployment's `CityProfile.departments` -- `seed.sql` does that for Oceanside.
create table if not exists departments (
    id                 text primary key,
    name               text not null,
    contact_email      text,
    contact_phone      text,
    handles_categories text[] not null default '{}'
);

create table if not exists neighborhoods (
    id               text primary key,
    name             text not null,
    council_district text,
    boundary_geojson text
);

-- ---------------------------------------------------------------------------
-- Issues.
--
-- Timestamps are client-authored epoch millis, because the app is offline-first:
-- a report filed in a parking garage is written locally and keeps the time it
-- was actually filed. `updated_at_millis` doubles as the row version and as the
-- cursor for incremental sync, which is why it is indexed.
--
-- Caveat worth knowing before this carries a real city: that cursor is in the
-- *client's* clock domain. A device whose clock runs fast can write a row that
-- another device's next incremental pull skips. Fine for a demo; a production
-- deployment should add a server-stamped `synced_at timestamptz default now()`
-- and page on that instead.
-- ---------------------------------------------------------------------------

create table if not exists issues (
    id                       text primary key,
    title                    text not null,
    description              text not null,
    requested_action         text not null,
    category                 text not null,
    status                   text not null,
    priority                 text not null,
    location                 jsonb not null,
    department_id            text references departments (id) on delete set null,
    reporter                 jsonb,
    submitted_by_user_id     uuid references auth.users (id) on delete set null,
    review_state             text not null default 'pending_review',
    reviewed_by_user_id      text,
    reviewed_by_display_name text,
    reviewed_at_millis       bigint,
    rejection_reason         text,
    last_edit                jsonb,
    notes_source             text not null default '',
    external_reference       jsonb,
    support_count            integer not null default 0,
    created_at_millis        bigint not null,
    updated_at_millis        bigint not null,

    -- Mirrors IssueReview's own invariant: a manager cannot reject without
    -- saying why, because the submitter is shown the reason. The Kotlin type
    -- refuses to construct the pair, so a row breaking it could not be read back.
    constraint rejected_issues_carry_a_reason check (
        review_state <> 'rejected' or coalesce(btrim(rejection_reason), '') <> ''
    )
);

create index if not exists issues_updated_at_millis_idx on issues (updated_at_millis);
create index if not exists issues_review_state_idx on issues (review_state);
create index if not exists issues_submitted_by_idx on issues (submitted_by_user_id);

-- Append-only audit trail.
create table if not exists issue_status_changes (
    id                       text primary key,
    issue_id                 text not null references issues (id) on delete cascade,
    from_status              text,
    to_status                text not null,
    note                     text,
    changed_by_user_id       text,
    changed_by_display_name  text,
    changed_at_millis        bigint not null
);

create index if not exists issue_status_changes_issue_id_idx on issue_status_changes (issue_id);

-- ---------------------------------------------------------------------------
-- Who may do what.
--
-- The publishable key is public, so every rule that matters lives here. Roles come
-- from `app_metadata`, which only the service key can write -- never
-- `user_metadata`, which the signed-in user can write themselves.
--
-- Grant a manager or admin from a trusted context, e.g. the SQL editor:
--   update auth.users
--      set raw_app_meta_data = raw_app_meta_data || '{"role":"admin"}'::jsonb
--    where email = 'you@example.com';
-- ---------------------------------------------------------------------------

-- `set search_path = ''` keeps these resolvable only through qualified names,
-- which is what stops a shadowing schema from redefining what a role means.
create or replace function claimed_role() returns text
language sql stable set search_path = '' as $$
    select coalesce(auth.jwt() -> 'app_metadata' ->> 'role', 'resident')
$$;

create or replace function is_reviewer() returns boolean
language sql stable set search_path = '' as $$
    select public.claimed_role() in ('manager', 'admin')
$$;

alter table issues enable row level security;
alter table issue_status_changes enable row level security;
alter table departments enable row level security;
alter table neighborhoods enable row level security;

-- Reference data is public, and editable only from a trusted context.
create policy "reference data is public"
    on departments for select using (true);
create policy "neighborhoods are public"
    on neighborhoods for select using (true);

-- An approved issue is the public map. Everything else is visible to the
-- person who filed it and to reviewers -- including a rejection and its reason,
-- which is the whole point of recording one.
create policy "read approved, own, or all as reviewer"
    on issues for select using (
        review_state = 'approved'
        or submitted_by_user_id = auth.uid()
        or is_reviewer()
    );

-- Anyone may file, including an anonymous session. A new report is pending and
-- unreviewed whatever the client sends: triage is not the submitter's to claim.
create policy "anyone may file a report"
    on issues for insert with check (
        (submitted_by_user_id is null or submitted_by_user_id = auth.uid())
        and review_state = 'pending_review'
        and reviewed_by_user_id is null
        and reviewed_at_millis is null
        and rejection_reason is null
    );

-- Only reviewers change a *reviewed* report.
create policy "reviewers may update"
    on issues for update using (is_reviewer()) with check (is_reviewer());

-- A submitter may revise their own report until a manager has looked at it,
-- and may never touch the triage fields -- so revising it cannot become
-- approving it.
--
-- This policy is also what makes the client's outbox replayable. The sync
-- engine re-pushes anything it has not seen acknowledged, and a push is an
-- upsert: the second attempt is an UPDATE, not an INSERT. Without this, a
-- resident whose acknowledgement was lost on a bad connection would have their
-- own report rejected forever afterwards.
create policy "submitters may revise their own pending report"
    on issues for update
    using (submitted_by_user_id = auth.uid() and review_state = 'pending_review')
    with check (
        submitted_by_user_id = auth.uid()
        and review_state = 'pending_review'
        and reviewed_by_user_id is null
        and reviewed_at_millis is null
        and rejection_reason is null
    );

create policy "admins may delete"
    on issues for delete using (claimed_role() = 'admin');

-- History follows the issue it belongs to, and is never rewritten.
create policy "read history of readable issues"
    on issue_status_changes for select using (
        exists (
            select 1 from issues i
            where i.id = issue_id
              and (i.review_state = 'approved' or i.submitted_by_user_id = auth.uid() or is_reviewer())
        )
    );

-- Insert only: no update or delete policy exists here, deliberately. An audit
-- trail that can be rewritten is not one. The client pushes these with
-- `ignoreDuplicates`, so re-sending an entry is a no-op rather than an update.
create policy "reviewers may append history"
    on issue_status_changes for insert with check (is_reviewer());

-- ---------------------------------------------------------------------------
-- Stars. One row per (issue, user), which is what holds a user to one star per
-- issue. `issues.support_count` is the tally of these rows, kept by the
-- triggers below; nothing a client sends can set it.
--
-- Like the photo section, this can be re-run on its own against a project that
-- predates it.
-- ---------------------------------------------------------------------------

create table if not exists issue_supports (
    issue_id          text not null references issues (id) on delete cascade,
    user_id           uuid not null default auth.uid() references auth.users (id) on delete cascade,
    created_at_millis bigint not null,
    primary key (issue_id, user_id)
);

create index if not exists issue_supports_user_id_idx on issue_supports (user_id);

alter table issue_supports enable row level security;

drop policy if exists "read own stars" on issue_supports;
create policy "read own stars"
    on issue_supports for select using (user_id = auth.uid());

-- Insert only. The client pushes with `ignoreDuplicates`, so a second star from
-- the same user is a no-op rather than an update.
drop policy if exists "star readable issues" on issue_supports;
create policy "star readable issues"
    on issue_supports for insert with check (
        user_id = auth.uid()
        and exists (
            select 1 from issues i
            where i.id = issue_id
              and (i.review_state = 'approved' or i.submitted_by_user_id = auth.uid() or is_reviewer())
        )
    );

-- Security definer, because the person starring usually may not update the
-- issue. Moving `updated_at_millis` is what lets other devices' incremental
-- pulls see the new count.
create or replace function count_issue_support() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    update public.issues
       set support_count = support_count + 1,
           updated_at_millis = greatest(updated_at_millis + 1, (extract(epoch from now()) * 1000)::bigint)
     where id = new.issue_id;
    return new;
end
$$;

drop trigger if exists count_issue_support on issue_supports;
create trigger count_issue_support after insert on issue_supports
    for each row execute function count_issue_support();

-- A pushed issue carries whatever count its device last saw, so an API caller's
-- value is ignored: a new report starts at zero and an edit keeps the stored
-- count. The trigger above and the demo seed run as the owner and pass through.
create or replace function keep_support_count() returns trigger
language plpgsql set search_path = '' as $$
begin
    if current_user in ('anon', 'authenticated') then
        new.support_count := case when tg_op = 'INSERT' then 0 else old.support_count end;
    end if;
    return new;
end
$$;

drop trigger if exists keep_support_count on issues;
create trigger keep_support_count before insert or update on issues
    for each row execute function keep_support_count();

-- ---------------------------------------------------------------------------
-- Photos. The bytes live in a public-read bucket, keyed `<issue id>/<photo id>`
-- so a replayed upload overwrites itself instead of duplicating; `issue_photos`
-- is how other devices find them.
--
-- Public-read means an object's URL works for anyone who has it, including the
-- photo of a report still awaiting review. The ids in the path are random
-- UUIDs and the row that names them follows the issue's visibility, so the URL
-- is not discoverable -- but a deployment whose pending reports must stay
-- private should make the bucket private and hand out signed URLs instead.
--
-- Unlike every other table, `uploaded_at_millis` is stamped by the server,
-- because a photo filed offline arrives long after it was taken and would
-- otherwise land behind other devices' sync cursors. The cursor itself is still
-- the client's clock, so the caveat on `issues` applies here too.
--
-- The policies are dropped first so this section can be re-run on its own
-- against a project that predates it.
-- ---------------------------------------------------------------------------

create table if not exists issue_photos (
    id                 text primary key,
    issue_id           text not null references issues (id) on delete cascade,
    storage_path       text not null,
    caption            text,
    created_at_millis  bigint not null,
    uploaded_at_millis bigint not null default (extract(epoch from now()) * 1000)::bigint,
    uploaded_by        uuid default auth.uid() references auth.users (id) on delete set null
);

create index if not exists issue_photos_issue_id_idx on issue_photos (issue_id);
create index if not exists issue_photos_uploaded_at_millis_idx on issue_photos (uploaded_at_millis);

alter table issue_photos enable row level security;

-- Who may attach a photo to an issue: whoever filed it, or a reviewer. Runs as
-- the caller, so the issue must also be one they can read.
create or replace function may_attach_photo(target_issue_id text) returns boolean
language sql stable set search_path = '' as $$
    select exists (
        select 1 from public.issues i
        where i.id = target_issue_id
          and (i.submitted_by_user_id = auth.uid() or public.is_reviewer())
    )
$$;

drop policy if exists "read photos of readable issues" on issue_photos;
create policy "read photos of readable issues"
    on issue_photos for select using (
        exists (
            select 1 from issues i
            where i.id = issue_id
              and (i.review_state = 'approved' or i.submitted_by_user_id = auth.uid() or is_reviewer())
        )
    );

-- Insert only. The client pushes rows with `ignoreDuplicates`, so a replay is a
-- no-op and no update policy is needed.
drop policy if exists "submitters and reviewers may record photos" on issue_photos;
create policy "submitters and reviewers may record photos"
    on issue_photos for insert with check (
        may_attach_photo(issue_id)
        and uploaded_by = auth.uid()
        and storage_path = issue_id || '/' || id
    );

-- Images only, and no larger than a phone photo needs to be. Without a type
-- list, a public bucket anyone can write to is free hosting for any file.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('issue-photos', 'issue-photos', true, 20971520,
        array['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/heic'])
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "issue photos are public" on storage.objects;
create policy "issue photos are public"
    on storage.objects for select using (bucket_id = 'issue-photos');

-- The first path segment is the issue id, so an upload is checked against the
-- issue it claims to belong to.
drop policy if exists "signed-in users may attach photos" on storage.objects;
drop policy if exists "submitters and reviewers may attach photos" on storage.objects;
create policy "submitters and reviewers may attach photos"
    on storage.objects for insert to authenticated with check (
        bucket_id = 'issue-photos'
        and public.may_attach_photo((storage.foldername(name))[1])
    );

-- An upsert is an update the second time, so the outbox replay needs this --
-- but only over an object the caller uploaded.
drop policy if exists "signed-in users may replace their upload" on storage.objects;
drop policy if exists "uploaders may replace their own photo" on storage.objects;
create policy "uploaders may replace their own photo"
    on storage.objects for update to authenticated using (
        bucket_id = 'issue-photos' and owner_id = auth.uid()::text
    );
