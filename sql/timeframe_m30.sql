-- Adds M30 to the timeframe CHECK constraints on PostgreSQL (prod).
--
-- Run once, BEFORE deploying a build whose application-prod.yml enables M30:
--
--   psql -h localhost -U <user> -d postgres -f sql/timeframe_m30.sql
--
-- On the Windows prod machine psql.exe lives in C:\Program Files\PostgreSQL\{version}\bin.
--
-- Why: Hibernate wrote each timeframe CHECK from the enum values that existed when the table was
-- created. ddl-auto: validate does not verify CHECK constraints, so without this script the app
-- starts clean and the first M30 webhook fails on INSERT (400, real cause only in the log).
-- Dev (H2, ddl-auto: create) needs nothing.
--
-- The list below is the full Timeframe enum, so this script also covers H8 if timeframe_h8.sql
-- was never run. It is idempotent - running it twice is harmless.
--
-- Check the result afterwards:
--
--   SELECT conrelid::regclass, conname, pg_get_constraintdef(oid)
--   FROM pg_constraint WHERE contype = 'c' AND conname LIKE '%timeframe_check';

BEGIN;

ALTER TABLE divergence_signal DROP CONSTRAINT IF EXISTS divergence_signal_timeframe_check;
ALTER TABLE divergence_signal ADD CONSTRAINT divergence_signal_timeframe_check
    CHECK (timeframe IN ('M1', 'M3', 'M5', 'M15', 'M30', 'H1', 'H2', 'H4', 'H8', 'H12', 'D1', 'W1'));

ALTER TABLE fvg_zone DROP CONSTRAINT IF EXISTS fvg_zone_timeframe_check;
ALTER TABLE fvg_zone ADD CONSTRAINT fvg_zone_timeframe_check
    CHECK (timeframe IN ('M1', 'M3', 'M5', 'M15', 'M30', 'H1', 'H2', 'H4', 'H8', 'H12', 'D1', 'W1'));

ALTER TABLE market_bias DROP CONSTRAINT IF EXISTS market_bias_timeframe_check;
ALTER TABLE market_bias ADD CONSTRAINT market_bias_timeframe_check
    CHECK (timeframe IN ('M1', 'M3', 'M5', 'M15', 'M30', 'H1', 'H2', 'H4', 'H8', 'H12', 'D1', 'W1'));

ALTER TABLE swing_point DROP CONSTRAINT IF EXISTS swing_point_timeframe_check;
ALTER TABLE swing_point ADD CONSTRAINT swing_point_timeframe_check
    CHECK (timeframe IN ('M1', 'M3', 'M5', 'M15', 'M30', 'H1', 'H2', 'H4', 'H8', 'H12', 'D1', 'W1'));

COMMIT;
