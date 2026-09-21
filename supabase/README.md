# Setting up the HelpMyCity demo backend

Everything the hosted demo at helpmycity.dev needs, in the order it has to
happen. All of it is done in the Supabase dashboard in a browser — no CLI, no
Docker, no local Deno. Budget about twenty minutes, most of it waiting for the
project to provision.

> **This documents one deployment: the public demo.** Supabase is the
> repository's worked backend example, not a requirement — the app talks to four
> small interfaces and any provider can implement them
> ([GETTING_STARTED.md § Choose a backend](../GETTING_STARTED.md#5-choose-a-backend)).
> Read the steps below as a sample rather than as general instructions. A real
> city wants steps 1–3 and 8; the rest — published sign-ins, a seeded dataset,
> and a function that wipes everything nightly — exist to keep a public demo
> honest and would be wrong in a deployment holding residents' reports.

```
schema.sql      tables, row-level security, the photo bucket
seed.sql        Oceanside's reference data + demo issues, and reset_demo_data()
schedule.sql    the daily cron job that calls the reset function
functions/
  reset-demo/   empties the photo bucket, then calls reset_demo_data()
```

| Step | A real deployment |
| --- | --- |
| 1–3 create the project, keys and schema | yes — this is the backend |
| 4 load the demo dataset | no. Its `departments` and `neighborhoods` inserts are worth keeping in step with your `CityProfile`; the example issues and `reset_demo_data()` are not |
| 5 three shared accounts | no. Create real accounts, and grant roles the same way — through `app_metadata` |
| 6–7 the reset function and its cron job | no. Never deploy anything that clears the issue table on a schedule |
| 8 run the app | yes |

---

## 1. Create the project

1. Go to [supabase.com/dashboard](https://supabase.com/dashboard) and choose
   **New project**.
2. Name it whatever you like, pick a region near your users (`West US` for
   Oceanside), and set a database password. **Save that password somewhere** —
   the dashboard shows it once, and you need it for direct database access.
   The app itself never uses it.
3. Wait for provisioning to finish before continuing.

## 2. Get the URL and key — the two values the app needs

In the dashboard: **Project Settings → API Keys** (URL is on **Project
Settings → Data API**).

| What you need | Where | Looks like |
|---|---|---|
| `supabase.url` | Project Settings → Data API → **Project URL** | `https://abcdefghijklmnopqrst.supabase.co` |
| `supabase.publishableKey` | Project Settings → API Keys → **Publishable key** | `sb_publishable_...` |

Take the **publishable** key (`sb_publishable_...`), not the legacy `anon` JWT —
Supabase is retiring the legacy keys at the end of 2026, and a publishable key
can be revoked on its own without invalidating everyone's session.

**Never take the `secret` / `service_role` key.** That one bypasses every
security policy in `schema.sql`. It belongs only in the Edge Function, where
Supabase injects it automatically (step 6).

Put both into `local.properties` at the repo root — it is gitignored, so they
stay out of the repo:

```properties
supabase.url=https://YOUR-PROJECT-REF.supabase.co
supabase.publishableKey=sb_publishable_YOUR_KEY
```

The build reads these through `cityConfig/build.gradle.kts`. Precedence is
environment variable → `-P` flag → `local.properties` → blank, so CI sets
`SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` as secrets and overrides the file.

## 3. Create the tables and policies

**SQL Editor → New query**, paste all of [`schema.sql`](./schema.sql), **Run**.

This creates `issues`, `issue_status_changes`, `issue_photos`, `departments`
and `neighborhoods`, turns on row-level security for all five, and creates the
public `issue-photos` bucket, limited to images of 20 MB or less.

Check: **Table Editor** should list the five tables, each marked *RLS enabled*.

A project set up before photos synced can take the photo section on its own:
paste everything from the `Photos.` banner to the end of `schema.sql`. It
replaces the old storage policies rather than adding beside them.

## 4. Load the demo data

**SQL Editor → New query**, paste all of [`seed.sql`](./seed.sql), **Run**.

This inserts Oceanside's five departments and five neighborhoods, defines
`reset_demo_data()`, and calls it once to create the five example issues.

Check: `select count(*) from issues;` returns **5**. One of them
(`demo-issue-crosswalk`) is `pending_review` on purpose, so the manager's review
queue has something in it.

> The departments are not decorative. `issues.department_id` is a foreign key to
> `departments`, so if those rows are missing, every report a visitor files with
> a department attached is rejected.

## 5. Create the three demo accounts

**Authentication → Users → Add user → Create new user.** Do this three times,
ticking **Auto Confirm User** each time so nobody has to click an email link:

| Email | Password |
|---|---|
| `admin@helpmycity.dev` | *(your choice)* |
| `manager@helpmycity.dev` | *(your choice)* |
| `resident@helpmycity.dev` | *(your choice)* |

Then give two of them their roles. **SQL Editor**, run:

```sql
update auth.users
   set raw_app_meta_data = raw_app_meta_data || '{"role":"admin"}'::jsonb
 where email = 'admin@helpmycity.dev';

update auth.users
   set raw_app_meta_data = raw_app_meta_data || '{"role":"manager","scope":{"citywide":true}}'::jsonb
 where email = 'manager@helpmycity.dev';
```

The resident needs nothing — no claim means resident.

This has to be `raw_app_meta_data`, not `raw_user_meta_data`. Users can write
their own `user_metadata` through the API, so a role kept there would let anyone
make themselves an admin.

Finally, put the three passwords you chose into `ConfiguredCityProfile.demoLogins`
so the sign-in screen advertises them. They are published to every visitor by
design, so pick throwaway ones.

**Also turn on anonymous sign-in** — the app's "continue as guest" button uses
it: **Authentication → Sign In / Providers → Anonymous sign-ins → enable**.

## 6. Deploy the reset function

All in the browser — no CLI, no Docker, no local Deno.

### 6a. Generate the shared secret

This is what stops anyone who finds the function URL from wiping the demo on
demand, so make it long and random. **SQL Editor**, run:

```sql
select encode(gen_random_bytes(32), 'hex');
```

Copy the result somewhere — you need it twice, in 6c and in step 7, and the two
must match exactly.

### 6b. Create the function

1. **Edge Functions** in the left sidebar → **Deploy a new function** → **Via
   Editor**.
2. Name it exactly **`reset-demo`**. The name becomes the URL, and
   `schedule.sql` expects that one.
3. Delete the scaffold code in the editor, and paste the entire contents of
   [`functions/reset-demo/index.ts`](./functions/reset-demo/index.ts) from this
   repo in its place. It is a single self-contained file.
4. **Deploy function** at the bottom. Give it 10–30 seconds.

> The repo file is the canonical copy. The dashboard editor has no versioning or
> rollback, so if you ever tweak the function in the browser, paste it back into
> `functions/reset-demo/index.ts` and commit — otherwise the next person to
> deploy from the repo silently reverts your change.

### 6c. Add the secret

**Edge Functions → Secrets** (the Edge Function Secrets Management page). Add:

| Key | Value |
|---|---|
| `DEMO_RESET_SECRET` | the hex string from 6a |

**Save.** The name cannot start with `SUPABASE_` — that prefix is reserved.

You do *not* add a service-role key here. Supabase injects
`SUPABASE_SERVICE_ROLE_KEY` and `SUPABASE_URL` into every function
automatically, and those are what the function uses to bypass row-level
security for the wipe.

### 6d. Turn off Verify JWT

On the function's page, open its settings and **disable "Verify JWT"** (some
dashboards label it *Enforce JWT verification*). It defaults to on.

This sounds wrong and is not. JWT verification here would check only that the
caller holds *some* valid project token — and the publishable key is public, so
every visitor to the site holds one. It gates nothing, while guaranteeing that
the scheduled call fails with a 401 before your code ever runs. The
`x-reset-secret` shared secret is the real gate, and unlike the publishable key
it is not published anywhere.

### 6e. Test it

On the function's page, click **Test**:

- Method **POST**
- Add a header: `x-reset-secret` = your hex string
- **Send Request**

Expect **200** and a body like `{"ok":true,"photosRemoved":0,"resetAt":"..."}`.

Now remove the header and send again. It must come back **403**. If it returns
401 instead, Verify JWT is still on — go back to 6d.

Confirm it actually did something: in the **SQL Editor**,
`select count(*) from issues;` should still be **5**, with fresh
`created_at_millis` values.

## 7. Schedule it daily

Copy [`schedule.sql`](./schedule.sql) into the **SQL Editor**, replace the two
placeholders, and **Run**:

- `YOUR-PROJECT-REF` → your project ref (the subdomain of your project URL)
- `YOUR-RESET-SECRET` → the same hex string from 6a

It enables `pg_cron` and `pg_net` and schedules `reset-demo-daily` at 09:00 UTC
(early morning in Oceanside, so the demo resets before anyone is looking at it).

The job sends no `Authorization` header, which is why step 6d matters: with
Verify JWT still on, every nightly run would fail with a 401.

Check:

```sql
select jobname, schedule, active from cron.job;

select status, return_message, start_time
  from cron.job_run_details
 where jobname = 'reset-demo-daily'
 order by start_time desc limit 5;
```

Do not wait until tomorrow to find out whether it works. The **Test** button in
6e bypasses `pg_net` entirely, so it proves the function but not the plumbing
around it. Fire exactly what cron will fire, from the **SQL Editor**:

```sql
select net.http_post(
    url := 'https://YOUR-PROJECT-REF.supabase.co/functions/v1/reset-demo',
    headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'x-reset-secret', 'YOUR-RESET-SECRET'
    ),
    body := jsonb_build_object('manual', true)
);
```

That returns a request id, not a result — `pg_net` is asynchronous. Read the
response a second or two later:

```sql
select status_code, content
  from net._http_response
 order by created desc
 limit 1;
```

**200** with `{"ok":true,...}` means the whole chain works. **403** means the
secret does not match; **401** means Verify JWT is still on.

## 8. Run the app

```bash
./gradlew :webApp:jsBrowserDevelopmentRun
```

Sign in as the manager, approve the pending crosswalk report, and confirm it
appears on the public map. Then file a report as a guest and confirm it lands in
the review queue rather than on the map.

---

## Changing the demo data

Edit `reset_demo_data()` in [`seed.sql`](./seed.sql) and re-run that file in the
**SQL Editor**. The Edge Function carries no copy of the dataset, so it never
needs redeploying for a data change.

## What the reset does and does not touch

Clears every issue, every status change and every stored photo — including
reports visitors filed, which is the point of resetting a public demo daily.

Leaves accounts, departments and neighborhoods alone. If an admin deletes a
department through the app, re-run `seed.sql` to put it back.
