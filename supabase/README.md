# Setting up the HelpMyCity demo backend

This is everything the hosted demo at helpmycity.dev needs, in the order it has to happen.
All of it happens in the Supabase dashboard in a browser, so there is no CLI, no Docker, and
no local Deno. Plan on about twenty minutes, and most of that is just waiting for the project
to provision.

> **This documents one deployment, the HelpMyCity demo.** Supabase is the repo's worked
> backend example, but it is not a requirement. The app talks to four small interfaces, and
> any provider can implement them
> ([CITY_CONFIG.md § The backend](../CITY_CONFIG.md#8-the-backend)).
> So treat the steps below as a sample and not as general instructions. A city's project
> only needs steps 1–3 and 8. The rest (published sign-ins, a seeded dataset, and a function
> that wipes everything every night) is what keeps the demo honest, and it would be very
> wrong in a deployment that holds real residents' reports.

```
schema.sql      tables, row-level security, the photo bucket
seed.sql        Oceanside's reference data + demo issues, and reset_demo_data()
schedule.sql    the daily cron job that calls the reset function
functions/
  reset-demo/   empties the photo bucket, then calls reset_demo_data()
```

| Step | A city's project |
| --- | --- |
| 1–3 create the project, keys, and schema | yes, this is the backend |
| 4 load the demo dataset | no. Its `departments` and `neighborhoods` inserts are worth keeping in sync with your `CityProfile`, but the example issues and `reset_demo_data()` are not |
| 5 three shared accounts | no. Create real accounts, and grant roles the same way, through `app_metadata` |
| 6–7 the reset function and its cron job | no. Never deploy anything that clears the issue table on a schedule |
| 8 run the app | yes |

---

## 1. Create the project

1. Go to [supabase.com/dashboard](https://supabase.com/dashboard) and choose
   **New project**.
2. Name it whatever you want, pick a region close to your users (`West US` for Oceanside),
   and set a database password. **Save that password somewhere.** The dashboard only shows
   it once, and you need it for direct database access. The app itself never uses it.
3. Wait for provisioning to finish before you keep going.

## 2. Get the URL and key (the two values the app needs)

In the dashboard, go to **Project Settings → API Keys** (the URL is under **Project
Settings → Data API**).

| What you need | Where | Looks like |
|---|---|---|
| `supabase.url` | Project Settings → Data API → **Project URL** | `https://abcdefghijklmnopqrst.supabase.co` |
| `supabase.publishableKey` | Project Settings → API Keys → **Publishable key** | `sb_publishable_...` |

Use the **publishable** key (`sb_publishable_...`), not the legacy `anon` JWT. Supabase is
retiring the legacy keys at the end of 2026, and a publishable key can be revoked on its own
without invalidating everyone's session.

**Never use the `secret` / `service_role` key.** That one skips every security policy in
`schema.sql`. It only belongs in the Edge Function, where Supabase injects it for you
(step 6).

Put both values in `local.properties` at the root of the repo. It is gitignored, so they stay
out of the repo:

```properties
supabase.url=https://YOUR-PROJECT-REF.supabase.co
supabase.publishableKey=sb_publishable_YOUR_KEY
```

The build reads these through `cityConfig/build.gradle.kts`. The order it checks is
environment variable, then the `-P` flag, then `local.properties`, then blank, so CI can set
`SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` as secrets and override the file.

## 3. Create the tables and policies

Go to **SQL Editor → New query**, paste in all of [`schema.sql`](./schema.sql), and hit
**Run**.

That creates `issues`, `issue_status_changes`, `issue_supports`, `issue_photos`,
`departments`, and `neighborhoods`, turns on row-level security for all six, and creates the
public `issue-photos` bucket, which only accepts images of 20 MB or less.

To check: the **Table Editor** should list the six tables, each one marked _RLS enabled_.

If your project was set up before photos synced, you can run just the photo section by
pasting everything from the `Photos.` banner to the end of `schema.sql`. It replaces the old
storage policies instead of adding new ones next to them. In the same way, a project set up
before stars were per-user can run just the section from the `Stars.` banner up to the
`Photos.` banner.

## 4. Load the demo data

Go to **SQL Editor → New query**, paste in all of [`seed.sql`](./seed.sql), and hit **Run**.

That inserts Oceanside's five departments and five neighborhoods, defines
`reset_demo_data()`, and calls it once to create the six example issues.

To check: `select count(*) from issues;` should return **6**. One of them
(`demo-issue-crosswalk`) is `pending_review` on purpose, so the manager's review queue has
something in it, and one (`demo-issue-rejected`) is rejected, so the Rejected reports page
does too.

> The departments are not just decoration. `issues.department_id` is a foreign key to
> `departments`, so if those rows are missing, every report a visitor files with a
> department attached gets rejected.

## 5. Create the three demo accounts

Go to **Authentication → Users → Add user → Create new user**. Do this three times, and
check **Auto Confirm User** each time so nobody has to click an email link:

| Email | Password |
|---|---|
| `admin@helpmycity.dev` | _(your choice)_ |
| `manager@helpmycity.dev` | _(your choice)_ |
| `resident@helpmycity.dev` | _(your choice)_ |

Then give two of them their roles. In the **SQL Editor**, run:

```sql
update auth.users
   set raw_app_meta_data = raw_app_meta_data || '{"role":"admin"}'::jsonb
 where email = 'admin@helpmycity.dev';

update auth.users
   set raw_app_meta_data = raw_app_meta_data || '{"role":"manager","scope":{"citywide":true}}'::jsonb
 where email = 'manager@helpmycity.dev';
```

The resident does not need anything, since no claim means resident.

This _has_ to be `raw_app_meta_data` and not `raw_user_meta_data`. Users can write their own
`user_metadata` through the API, so keeping a role there would let anyone make themselves an
admin.

Last, put the three passwords you picked into `ConfiguredCityProfile.demoLogins` so the
sign-in screen shows them. They get published to every visitor on purpose, so pick throwaway
ones.

With `demoLogins` set, the sign-in screen hides both sign-up and "continue without an
account", so anonymous sign-ins can stay off. A city's project does need them on for that
button, though: **Authentication → Sign In / Providers → Anonymous sign-ins → enable**.

## 6. Deploy the reset function

This is all in the browser too, so still no CLI, no Docker, and no local Deno.

### 6a. Generate the shared secret

This secret is the thing that stops anyone who finds the function URL from wiping the demo
whenever they feel like it, so make it long and random. In the **SQL Editor**, run:

```sql
select encode(gen_random_bytes(32), 'hex');
```

Copy the result somewhere. You will need it twice (in 6c and in step 7), and the two have to
match exactly.

### 6b. Create the function

1. Click **Edge Functions** in the left sidebar, then **Deploy a new function**, then **Via
   Editor**.
2. Name it exactly **`reset-demo`**. The name becomes the URL, and `schedule.sql` expects
   that one.
3. Delete the scaffold code in the editor and paste in the entire contents of
   [`functions/reset-demo/index.ts`](./functions/reset-demo/index.ts) from this repo. It is a
   single file with no dependencies on anything else.
4. Click **Deploy function** at the bottom, and give it 10–30 seconds.

> The file in the repo is the real copy. The dashboard editor has no versioning or rollback,
> so if you ever tweak the function in the browser, paste it back into
> `functions/reset-demo/index.ts` and commit it. Otherwise the next person who deploys from
> the repo will quietly undo your change.

### 6c. Add the secret

Go to **Edge Functions → Secrets** (the Edge Function Secrets Management page) and add:

| Key | Value |
|---|---|
| `DEMO_RESET_SECRET` | the hex string from 6a |

Click **Save**. The name can't start with `SUPABASE_`, since that prefix is reserved.

You do _not_ add a service-role key here. Supabase injects `SUPABASE_SERVICE_ROLE_KEY` and
`SUPABASE_URL` into every function automatically, and those are what the function uses to get
past row-level security for the wipe.

### 6d. Turn off Verify JWT

On the function's page, open its settings and **disable "Verify JWT"** (some dashboards call
it _Enforce JWT verification_). It is on by default.

I know this sounds wrong, but it isn't. JWT verification here would only check that the
caller has _some_ valid project token, and since the publishable key is public, every visitor
to the site has one. So it does not actually protect anything, and it guarantees the
scheduled call fails with a 401 before your code even runs. The `x-reset-secret` shared secret
is the real gate, and unlike the publishable key, it is not published anywhere.

### 6e. Test it

On the function's page, click **Test**:

- Method **POST**
- Add a header: `x-reset-secret` = your hex string
- **Send Request**

You should get a **200** and a body like `{"ok":true,"photosRemoved":0,"resetAt":"..."}`.

Now take the header off and send it again. It has to come back **403**. If you get a 401
instead, Verify JWT is still on, so go back to 6d.

Make sure it actually did something. In the **SQL Editor**, `select count(*) from issues;`
should still be **6**, with fresh `created_at_millis` values.

## 7. Schedule it daily

Copy [`schedule.sql`](./schedule.sql) into the **SQL Editor**, replace the two placeholders,
and hit **Run**:

- `YOUR-PROJECT-REF` → your project ref (the subdomain of your project URL)
- `YOUR-RESET-SECRET` → the same hex string from 6a

It enables `pg_cron` and `pg_net` and schedules `reset-demo-daily` at 09:00 UTC. That is early
morning in Oceanside, so the demo resets before anyone is really looking at it.

The job does not send an `Authorization` header, which is why step 6d matters. With Verify
JWT still on, every nightly run would fail with a 401.

To check:

```sql
select jobname, schedule, active from cron.job;

select status, return_message, start_time
  from cron.job_run_details
 where jobname = 'reset-demo-daily'
 order by start_time desc limit 5;
```

Don't wait until tomorrow to find out if it works. The **Test** button in 6e skips `pg_net`
completely, so it proves the function works but not all the plumbing around it. Instead, fire
exactly what cron will fire, from the **SQL Editor**:

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

That gives you back a request id, not a result, because `pg_net` is asynchronous. Read the
response a second or two later:

```sql
select status_code, content
  from net._http_response
 order by created desc
 limit 1;
```

A **200** with `{"ok":true,...}` means the whole chain works. A **403** means the secret does
not match, and a **401** means Verify JWT is still on.

## 8. Run the app

```bash
./gradlew :webApp:jsBrowserDevelopmentRun
```

Sign in as the manager, approve the pending crosswalk report, and make sure it shows up on
the public map. Then file a report as the resident and make sure it lands in the review queue
and not on the map.

---

## Changing the demo data

Edit `reset_demo_data()` in [`seed.sql`](./seed.sql) and re-run that file in the
**SQL Editor**. The Edge Function does not keep its own copy of the dataset, so you never have
to redeploy it just to change the data.

## What the reset does and does not touch

It clears every issue, every status change, and every stored photo. That includes reports
visitors filed, which is kind of the point of resetting a public demo every day.

It leaves accounts, departments, and neighborhoods alone. If an admin deletes a department
through the app, just re-run `seed.sql` to put it back.
