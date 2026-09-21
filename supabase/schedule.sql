-- Runs the demo reset once a day.
--
-- Apply last, after `schema.sql` and `seed.sql`, and after the `reset-demo`
-- function is deployed and its DEMO_RESET_SECRET is set. Replace both
-- placeholders below before running.
--
-- The call carries no Authorization header, so the function must have **Verify
-- JWT turned off** -- see step 6d of README.md. Verifying a JWT here would gate
-- nothing anyway, since the publishable key every visitor holds is a valid one;
-- the x-reset-secret header below is what actually authorizes this.

create extension if not exists pg_cron;
-- pg_net is what lets a scheduled job make an HTTP call at all.
create extension if not exists pg_net;

-- Re-running this file should re-point the job rather than fail, and
-- cron.unschedule throws if the job is absent.
select cron.unschedule('reset-demo-daily')
where exists (select 1 from cron.job where jobname = 'reset-demo-daily');

-- 09:00 UTC, which is early morning in Oceanside -- the demo resets before
-- anyone is likely to be looking at it, not in the middle of the day.
select cron.schedule(
    'reset-demo-daily',
    '0 9 * * *',
    $$
    select net.http_post(
        url := 'https://YOUR-PROJECT-REF.supabase.co/functions/v1/reset-demo',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            -- Must equal the DEMO_RESET_SECRET set on the function.
            'x-reset-secret', 'YOUR-RESET-SECRET'
        ),
        body := jsonb_build_object('scheduledAt', now()),
        timeout_milliseconds := 60000
    );
    $$
);

-- Useful afterwards:
--   select * from cron.job;
--   select * from cron.job_run_details where jobname = 'reset-demo-daily'
--     order by start_time desc limit 10;
