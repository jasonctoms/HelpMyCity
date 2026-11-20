-- Runs the demo reset once a day.
--
-- Run this last, after `schema.sql` and `seed.sql`, and after the `reset-demo`
-- function is deployed and its DEMO_RESET_SECRET is set. Replace both
-- placeholders below before running it.
--
-- The call does not send an Authorization header, so the function has to have
-- **Verify JWT turned off** (see step 6d of README.md). Verifying a JWT here would
-- not protect anything anyway, since every visitor has the publishable key and
-- it is a valid one. The x-reset-secret header below is what actually authorizes
-- this.

create extension if not exists pg_cron;
-- pg_net is what lets a scheduled job make an HTTP call in the first place.
create extension if not exists pg_net;

-- Running this file again should re-point the job instead of failing, and
-- cron.unschedule throws if the job does not exist.
select cron.unschedule('reset-demo-daily')
where exists (select 1 from cron.job where jobname = 'reset-demo-daily');

-- 09:00 UTC, which is early morning in Oceanside, so the demo resets before
-- anyone is likely to be looking at it and not in the middle of the day.
select cron.schedule(
    'reset-demo-daily',
    '0 9 * * *',
    $$
    select net.http_post(
        url := 'https://YOUR-PROJECT-REF.supabase.co/functions/v1/reset-demo',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            -- This has to match the DEMO_RESET_SECRET set on the function.
            'x-reset-secret', 'YOUR-RESET-SECRET'
        ),
        body := jsonb_build_object('scheduledAt', now()),
        timeout_milliseconds := 60000
    );
    $$
);

-- Handy afterwards:
--   select * from cron.job;
--   select * from cron.job_run_details where jobname = 'reset-demo-daily'
--     order by start_time desc limit 10;
