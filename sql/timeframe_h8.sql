-- Adds H8 to the timeframe CHECK constraints on PostgreSQL (prod).
--
-- Hibernate derives those constraints from the Timeframe enum when it generates DDL, so every
-- table holding a timeframe got an inline, unnamed CHECK listing the values that existed at
-- creation time. PostgreSQL names such a constraint <table>_<column>_check.
--
-- ddl-auto: validate does NOT verify CHECK constraints, so without this script the application
-- starts clean and the first H8 alert fails on INSERT instead — a 400 from the webhook, with the
-- real cause only in the log. Run this once, before deploying a build that knows H8:
--
--   psql -h localhost -U <user> -d postgres -f sql/timeframe_h8.sql
--
-- Dev (H2, ddl-auto: create) rebuilds the constraints from the entities and needs nothing here.
--
-- Only divergence_signal is required for H8 divergence alerts; the other three are included
-- because every one of them is reachable from a webhook whose payload carries a timeframe
-- (fvg_zone from /webhooks/fvg, market_bias and swing_point from /webhooks/momentum).
--
-- If a constraint carries a different name on this machine, find it with:
--
--   SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
--   WHERE conrelid = 'divergence_signal'::regclass AND contype = 'c';

ALTER TABLE divergence_signal DROP CONSTRAINT IF EXISTS divergence_signal_timeframe_check;
ALTER TABLE divergence_signal ADD CONSTRAINT divergence_signal_timeframe_check
    CHECK (timeframe IN ('M1', 'M3', 'M5', 'M15', 'H1', 'H2', 'H4', 'H8', 'H12', 'D1', 'W1'));

ALTER TABLE fvg_zone DROP CONSTRAINT IF EXISTS fvg_zone_timeframe_check;
ALTER TABLE fvg_zone ADD CONSTRAINT fvg_zone_timeframe_check
    CHECK (timeframe IN ('M1', 'M3', 'M5', 'M15', 'H1', 'H2', 'H4', 'H8', 'H12', 'D1', 'W1'));

ALTER TABLE market_bias DROP CONSTRAINT IF EXISTS market_bias_timeframe_check;
ALTER TABLE market_bias ADD CONSTRAINT market_bias_timeframe_check
    CHECK (timeframe IN ('M1', 'M3', 'M5', 'M15', 'H1', 'H2', 'H4', 'H8', 'H12', 'D1', 'W1'));

ALTER TABLE swing_point DROP CONSTRAINT IF EXISTS swing_point_timeframe_check;
ALTER TABLE swing_point ADD CONSTRAINT swing_point_timeframe_check
    CHECK (timeframe IN ('M1', 'M3', 'M5', 'M15', 'H1', 'H2', 'H4', 'H8', 'H12', 'D1', 'W1'));
