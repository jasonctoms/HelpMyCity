-- Oceanside's demo data, and the function that puts it back.
--
-- Run after `schema.sql`. Running it again is safe: it replaces the demo rows
-- rather than adding to them.
--
-- This file is the one source of the demo dataset. The `reset-demo` Edge
-- Function does not carry its own copy -- it empties the photo bucket and then
-- calls `public.reset_demo_data()` defined here, so editing an example issue
-- means editing this file and nothing else.

-- ---------------------------------------------------------------------------
-- Reference data, mirroring ConfiguredCityProfile.
--
-- The app seeds its own local copy from the CityProfile and never reads these
-- back, but issues carry a foreign key to `departments`, so a department that
-- is missing here makes every push naming it fail. Ids must match the profile.
-- ---------------------------------------------------------------------------

insert into departments (id, name, contact_email, handles_categories) values
    ('dept-public-works',    'Public Works',        'publicworks@oceansideca.org',
     '{road_surface,sidewalk,drainage,trash_dumping}'),
    ('dept-traffic-calming', 'Traffic Calming',     null,
     '{traffic_safety,signage,ada_access}'),
    ('dept-water-utilities', 'Water Utilities',     null,
     '{water_utilities,drainage}'),
    ('dept-parks',           'Parks & Recreation',  null,
     '{park_maintenance,graffiti}'),
    ('dept-street-lighting', 'Street Lighting',     null,
     '{street_lighting}')
on conflict (id) do update set
    name = excluded.name,
    contact_email = excluded.contact_email,
    handles_categories = excluded.handles_categories;

insert into neighborhoods (id, name, council_district) values
    ('nbhd-libby-lake',     'Libby Lake',     'District 1'),
    ('nbhd-crown-heights',  'Crown Heights',  'District 2'),
    ('nbhd-eastside',       'Eastside',       'District 2'),
    ('nbhd-mesa-margarita', 'Mesa Margarita', 'District 3'),
    ('nbhd-fire-mountain',  'Fire Mountain',  'District 4')
on conflict (id) do update set
    name = excluded.name,
    council_district = excluded.council_district;

-- ---------------------------------------------------------------------------
-- The demo issues, and the daily reset.
--
-- Ids are fixed and prefixed `demo-` so the reset can tell seeded rows from
-- anything a visitor filed -- though it clears both, since a public demo that
-- kept yesterday's submissions would fill up with whatever the internet typed.
--
-- Ages are relative to the reset, not absolute, so "open for 9 days" stays true
-- tomorrow. `location` is jsonb in the shape IssueLocation serializes to:
-- camelCase keys, and anything left at its default is simply absent.
-- ---------------------------------------------------------------------------

create or replace function public.reset_demo_data()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    now_ms bigint := (extract(epoch from now()) * 1000)::bigint;
    day_ms bigint := 86400000;
begin
    -- Status changes and photos cascade from the issue.
    delete from public.issues;

    insert into public.issues (
        id, title, description, requested_action,
        category, status, priority,
        location, department_id, notes_source, resolution,
        review_state, reviewed_by_display_name, reviewed_at_millis, rejection_reason,
        support_count, created_at_millis, updated_at_millis
    ) values
    (
        'demo-issue-sidewalk',
        'Missing sidewalk along park frontage',
        'No continuous sidewalk on the park frontage, so people walk in the traffic lane to reach the community center.',
        'Install a continuous sidewalk or a striped walking lane.',
        'sidewalk', 'open', 'high',
        '{"description":"Libby Lake Park frontage","point":{"latitude":33.2385,"longitude":-117.3245},"neighborhood":"nbhd-libby-lake"}',
        'dept-public-works', 'Walk audit', null,
        'approved', 'Sample data', now_ms - 9 * day_ms, null,
        7, now_ms - 9 * day_ms, now_ms - 9 * day_ms
    ),
    (
        'demo-issue-lighting',
        'Street lights out for three blocks',
        'Street lights have been out between Montecito and Redondo for weeks.',
        'Repair or replace the failed street lights.',
        'street_lighting', 'in_progress', 'high',
        '{"description":"N. River Rd (Montecito -> Redondo)","point":{"latitude":33.2421,"longitude":-117.3312},"neighborhood":"nbhd-libby-lake"}',
        'dept-street-lighting', 'Resident request', null,
        'approved', 'Sample data', now_ms - 6 * day_ms, null,
        12, now_ms - 6 * day_ms, now_ms - 2 * day_ms
    ),
    (
        'demo-issue-drainage',
        'Standing water after every rain',
        'The corner floods and stays flooded for days; the storm drain looks blocked.',
        'Clear the storm drain and check the grade.',
        'drainage', 'in_progress', 'medium',
        '{"description":"Calle Solimar & Calle Los Santos","point":{"latitude":33.2298,"longitude":-117.3187},"neighborhood":"nbhd-mesa-margarita"}',
        'dept-water-utilities', 'Resident request; ownership unclear', null,
        'approved', 'Sample data', now_ms - 4 * day_ms, null,
        3, now_ms - 4 * day_ms, now_ms - 1 * day_ms
    ),
    (
        -- Left unreviewed on purpose: the manager's review queue should have
        -- something in it the moment someone signs in as a manager.
        'demo-issue-crosswalk',
        'Crosswalk faded at school crossing',
        'Crosswalk paint is nearly gone where kids cross to the school.',
        'Repaint the crosswalk and add advance warning signage.',
        'traffic_safety', 'in_review', 'high',
        '{"description":"Douglas Dr at the elementary school crossing","point":{"latitude":33.2456,"longitude":-117.3402},"neighborhood":"nbhd-crown-heights"}',
        'dept-traffic-calming', 'Walk audit', null,
        'pending_review', null, null, null,
        1, now_ms - 2 * 3600000, now_ms - 2 * 3600000
    ),
    (
        'demo-issue-dumping',
        'Illegal dumping behind the ballfields',
        'Mattresses and construction debris dumped along the fence line.',
        'Schedule a pickup and consider a camera or better lighting.',
        'trash_dumping', 'complete', 'low',
        '{"description":"Behind the ballfields, north fence line","point":{"latitude":33.2371,"longitude":-117.3268},"neighborhood":"nbhd-libby-lake"}',
        'dept-public-works', 'Resident request',
        'Debris hauled away and a motion-activated light installed on the fence line.',
        'approved', 'Sample data', now_ms - 21 * day_ms, null,
        5, now_ms - 21 * day_ms, now_ms - 14 * day_ms
    ),
    (
        -- Turned down, so the rejected page has something in it for a manager.
        'demo-issue-rejected',
        'Neighbor''s hedge blocks the view',
        'The hedge next door has grown over six feet and blocks my view of the street.',
        'Make them trim it.',
        'other', 'rejected', 'low',
        '{"description":"Private yard off Mesa Dr","neighborhood":"nbhd-mesa-margarita"}',
        null, 'Resident request', null,
        'rejected', 'Sample data', now_ms - 3 * day_ms,
        'A hedge on private property is between neighbors; the city only acts when it blocks a sidewalk or a sight line at a corner.',
        0, now_ms - 5 * day_ms, now_ms - 3 * day_ms
    );

    -- History, so the detail screen's timeline is not empty. The unreviewed
    -- sample deliberately has none: nothing has happened to it yet.
    insert into public.issue_status_changes (
        id, issue_id, from_status, to_status, note,
        changed_by_display_name, changed_at_millis
    ) values
    ('demo-change-sidewalk', 'demo-issue-sidewalk', 'in_review', 'open',
     'Imported from the Libby Lake walk audit.', 'Sample data', now_ms - 9 * day_ms),
    ('demo-change-lighting', 'demo-issue-lighting', 'in_review', 'in_progress',
     'Imported from the Libby Lake walk audit.', 'Sample data', now_ms - 2 * day_ms),
    ('demo-change-drainage', 'demo-issue-drainage', 'in_review', 'in_progress',
     'Imported from the Libby Lake walk audit.', 'Sample data', now_ms - 1 * day_ms),
    ('demo-change-dumping', 'demo-issue-dumping', 'in_review', 'complete',
     'Debris hauled away and a motion-activated light installed on the fence line.', 'Sample data', now_ms - 14 * day_ms),
    ('demo-change-rejected', 'demo-issue-rejected', 'in_review', 'rejected',
     'A hedge on private property is between neighbors; the city only acts when it blocks a sidewalk or a sight line at a corner.',
     'Sample data', now_ms - 3 * day_ms);
end;
$$;

-- `security definer` so the scheduled reset runs with the owner's rights rather
-- than the caller's, which is what lets it delete rows the RLS policies would
-- otherwise protect. Nobody but the owner and the reset function should be able
-- to call it.
revoke execute on function public.reset_demo_data() from public, anon, authenticated;

select public.reset_demo_data();
