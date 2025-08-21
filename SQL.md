-- PIHS COMPLETE DATABASE SCHEMA (VERSION 2.6 - SYNTAX CORRECTED)
-- This version fixes a syntax error (missing comma) in the insights table definition.
-- It also retains all previous fixes for constraints and partition management.

-- -----------------------------------------------------------------------------
-- Section 1: Required PostgreSQL Extensions
-- -----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS postgis;

-- -----------------------------------------------------------------------------
-- Section 2: Helper Functions & Core Data Tables
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
NEW.updated_at = now();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE IF NOT EXISTS public.user_profiles (
user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
full_name TEXT NULL,
timezone TEXT NULL DEFAULT 'UTC',
created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
updated_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own profile." ON public.user_profiles FOR ALL USING (auth.uid() = user_id);
CREATE TRIGGER on_user_profiles_updated BEFORE UPDATE ON public.user_profiles FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

CREATE TABLE IF NOT EXISTS public.metric_sources (
id BIGSERIAL PRIMARY KEY,
user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
source_identifier TEXT NOT NULL,
source_name TEXT NOT NULL,
is_active BOOLEAN DEFAULT true NOT NULL,
last_synced_at TIMESTAMPTZ NULL,
created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
updated_at TIMESTAMPTZ DEFAULT now() NOT NULL,
UNIQUE (user_id, source_identifier)
);
ALTER TABLE public.metric_sources ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own metric sources." ON public.metric_sources FOR ALL USING (auth.uid() = user_id);
CREATE TRIGGER on_metric_sources_updated BEFORE UPDATE ON public.metric_sources FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

CREATE TABLE IF NOT EXISTS public.metric_definitions (
id BIGSERIAL PRIMARY KEY,
metric_name TEXT NOT NULL UNIQUE,
category TEXT NOT NULL,
default_unit TEXT NULL
);
ALTER TABLE public.metric_definitions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Authenticated users can read metric definitions." ON public.metric_definitions FOR SELECT USING (auth.role() = 'authenticated');

CREATE TABLE IF NOT EXISTS public.data_points (
user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
metric_source_id BIGINT NOT NULL REFERENCES public.metric_sources(id) ON DELETE CASCADE,
metric_id BIGINT NOT NULL REFERENCES public.metric_definitions(id) ON DELETE CASCADE,
"timestamp" TIMESTAMPTZ NOT NULL,
value_numeric DOUBLE PRECISION NULL,
value_text TEXT NULL,
value_json JSONB NULL,
value_geography GEOGRAPHY(Point, 4326) NULL,
-- FIX: Expanded UNIQUE constraint to include metric_source_id.
-- This prevents data collision and allows multiple sources to log the same metric at the exact same time.
UNIQUE(user_id, metric_source_id, metric_id, "timestamp")
) PARTITION BY RANGE ("timestamp");

ALTER TABLE public.data_points ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own data points." ON public.data_points FOR ALL USING (auth.uid() = user_id);
CREATE INDEX IF NOT EXISTS idx_datapoints_user_metric_id_timestamp ON public.data_points(user_id, metric_id, "timestamp" DESC);
CREATE INDEX IF NOT EXISTS idx_datapoints_value_json_gin ON public.data_points USING GIN (value_json);
CREATE INDEX IF NOT EXISTS idx_datapoints_timestamp_brin ON public.data_points USING BRIN ("timestamp");
CREATE INDEX IF NOT EXISTS idx_datapoints_value_geography_gist ON public.data_points USING GIST (value_geography);

CREATE TABLE IF NOT EXISTS public.events (
id BIGSERIAL PRIMARY KEY,
user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
event_name TEXT NOT NULL,
start_timestamp TIMESTAMPTZ NOT NULL,
end_timestamp TIMESTAMPTZ NULL,
description TEXT NULL,
properties JSONB NULL,
embedding vector(1536) NULL,
created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
ALTER TABLE public.events ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own events." ON public.events FOR ALL USING (auth.uid() = user_id);
CREATE INDEX IF NOT EXISTS idx_events_user_start_timestamp ON public.events(user_id, start_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_events_embedding_hnsw ON public.events USING hnsw (embedding vector_l2_ops);

-- -----------------------------------------------------------------------------
-- Section 3: Structured Logging Tables (Supplements, App Usage)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.supplement_definitions (
id BIGSERIAL PRIMARY KEY,
supplement_name TEXT NOT NULL UNIQUE,
category TEXT NULL, -- e.g., 'Vitamin', 'Nootropic', 'Mineral'
description TEXT NULL
);
ALTER TABLE public.supplement_definitions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Authenticated users can read supplement definitions." ON public.supplement_definitions FOR SELECT USING (auth.role() = 'authenticated');

CREATE TYPE public.supplement_unit AS ENUM ('mg', 'mcg', 'g', 'iu', 'tsp', 'tbsp', 'pill');

CREATE TABLE IF NOT EXISTS public.supplement_logs (
id BIGSERIAL PRIMARY KEY,
user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
"timestamp" TIMESTAMPTZ NOT NULL,
supplement_id BIGINT NOT NULL REFERENCES public.supplement_definitions(id),
amount DOUBLE PRECISION NOT NULL,
unit supplement_unit NOT NULL,
notes TEXT NULL
);
ALTER TABLE public.supplement_logs ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own supplement logs." ON public.supplement_logs FOR ALL USING (auth.uid() = user_id);
CREATE INDEX idx_supplement_logs_user_timestamp ON public.supplement_logs(user_id, "timestamp" DESC);

CREATE TABLE IF NOT EXISTS public.app_usage_logs (
id BIGSERIAL PRIMARY KEY,
user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
start_timestamp TIMESTAMPTZ NOT NULL,
duration_seconds INT NOT NULL,
app_name TEXT NOT NULL,
app_category TEXT NULL, -- e.g., 'Social Media', 'Productivity', 'Entertainment'
UNIQUE (user_id, app_name, start_timestamp)
);
ALTER TABLE public.app_usage_logs ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own app usage logs." ON public.app_usage_logs FOR ALL USING (auth.uid() = user_id);
CREATE INDEX idx_app_usage_logs_user_timestamp ON public.app_usage_logs(user_id, start_timestamp DESC);


-- -----------------------------------------------------------------------------
-- Section 4: Automated & Manual Partition Management
-- -----------------------------------------------------------------------------

-- This function runs daily to create partitions for the current and next month.
CREATE OR REPLACE FUNCTION public.manage_partitions()
RETURNS void AS $$
DECLARE
current_month_start timestamptz := date_trunc('month', now());
next_month_start timestamptz := current_month_start + interval '1 month';
current_partition_name text := 'data_points_' || to_char(current_month_start, 'YYYY_MM');
next_partition_name text := 'data_points_' || to_char(next_month_start, 'YYYY_MM');
BEGIN
-- Create partition for current month if it doesn't exist
IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = current_partition_name) THEN
EXECUTE format('CREATE TABLE %I PARTITION OF public.data_points FOR VALUES FROM (%L) TO (%L)',
current_partition_name, current_month_start, next_month_start);
RAISE NOTICE 'Created partition: %', current_partition_name;
END IF;

    -- Create partition for next month if it doesn't exist
    IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = next_partition_name) THEN
        EXECUTE format('CREATE TABLE %I PARTITION OF public.data_points FOR VALUES FROM (%L) TO (%L)',
                       next_partition_name, next_month_start, next_month_start + interval '1 month');
        RAISE NOTICE 'Created partition: %', next_partition_name;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- FIX: Added a new function to backfill partitions for historical data imports.
-- Call this function manually before running a large, historical data import.
CREATE OR REPLACE FUNCTION public.backfill_partitions(start_date TIMESTAMPTZ, end_date TIMESTAMPTZ)
RETURNS void AS $$
DECLARE
loop_month_start timestamptz;
BEGIN
loop_month_start := date_trunc('month', start_date);
WHILE loop_month_start < date_trunc('month', end_date) LOOP
DECLARE
partition_name TEXT := 'data_points_' || to_char(loop_month_start, 'YYYY_MM');
partition_end_date TIMESTAMPTZ := loop_month_start + interval '1 month';
BEGIN
IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = partition_name) THEN
EXECUTE format('CREATE TABLE %I PARTITION OF public.data_points FOR VALUES FROM (%L) TO (%L)',
partition_name, loop_month_start, partition_end_date);
RAISE NOTICE 'Created historical partition: %', partition_name;
END IF;
END;
loop_month_start := loop_month_start + interval '1 month';
END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Schedule the routine partition management job.
SELECT cron.schedule(
'daily-partition-management',
'0 1 * * *', -- 1:00 AM every day
$$ SELECT public.manage_partitions(); $$
);

-- -----------------------------------------------------------------------------
-- Section 5: Asynchronous Insight Engine
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.insights (
id BIGSERIAL PRIMARY KEY,
user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
insight_type TEXT NOT NULL,
title TEXT NOT NULL,
summary TEXT NOT NULL,
generated_at TIMESTAMPTZ DEFAULT now() NOT NULL,
result_data JSONB NOT NULL,
-- SYNTAX FIX: Added the required comma before the UNIQUE constraint.
UNIQUE(user_id, insight_type, generated_at)
);
ALTER TABLE public.insights ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can read their own insights." ON public.insights FOR SELECT USING (auth.uid() = user_id);

CREATE TYPE public.job_status AS ENUM ('pending', 'in_progress', 'completed', 'failed');
CREATE TABLE IF NOT EXISTS public.analysis_jobs (
id BIGSERIAL PRIMARY KEY,
user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
status job_status DEFAULT 'pending' NOT NULL,
created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
updated_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
ALTER TABLE public.analysis_jobs ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Service workers can manage jobs." ON public.analysis_jobs FOR ALL USING (auth.role() = 'service_role');
CREATE POLICY "Users can see their own jobs." ON public.analysis_jobs FOR SELECT USING (auth.uid() = user_id);
CREATE TRIGGER on_analysis_jobs_updated BEFORE UPDATE ON public.analysis_jobs FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

-- Add this index definition to your schema
CREATE UNIQUE INDEX idx_unique_pending_job_per_user
ON public.analysis_jobs (user_id)
WHERE (status = 'pending');

CREATE OR REPLACE FUNCTION public.queue_all_user_analyses()
RETURNS TEXT AS $$
DECLARE
user_rec RECORD;
users_queued INT := 0;
BEGIN
FOR user_rec IN SELECT id FROM auth.users LOOP
-- Insert a job for each user, doing nothing if a pending job already exists.
-- The function can now rely on the index
INSERT INTO public.analysis_jobs (user_id, status)
VALUES (user_rec.id, 'pending')
ON CONFLICT (user_id) WHERE (status = 'pending') -- This now correctly targets the partial index
DO NOTHING;
users_queued := users_queued + 1;
END LOOP;
RETURN 'Queued analysis jobs for ' || users_queued || ' users.';
END;
$$ LANGUAGE plpgsql;

SELECT cron.schedule(
'weekly-insight-job-queueing',
'0 3 * * 0', -- 3:00 AM on Sunday
$$ SELECT public.queue_all_user_analyses(); $$
);

-- -----------------------------------------------------------------------------
-- Section 6: Example On-Demand Function for Real-Time UI
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION get_metric_summary_for_period(
user_id_input UUID,
metric_name_input TEXT,
start_date TIMESTAMPTZ,
end_date TIMESTAMPTZ
)
RETURNS TABLE(avg_val DOUBLE PRECISION, min_val DOUBLE PRECISION, max_val DOUBLE PRECISION, total_count BIGINT) AS $$
BEGIN
RETURN QUERY
SELECT
avg(dp.value_numeric),
min(dp.value_numeric),
max(dp.value_numeric),
count(dp.value_numeric)
FROM public.data_points dp
JOIN public.metric_definitions md ON dp.metric_id = md.id
WHERE dp.user_id = user_id_input
AND md.metric_name = metric_name_input
AND dp."timestamp" BETWEEN start_date AND end_date;
END;
$$ LANGUAGE plpgsql;
