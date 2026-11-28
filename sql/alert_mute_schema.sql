-- Alert mutes: (strategy, symbol, timeframe) rules switched from the dashboard at runtime.
--
-- The prod profile runs with ddl-auto: validate, so this table must exist BEFORE the jar that
-- carries AlertMuteEntity starts. Run once by hand against PostgreSQL:
--
--   psql -h localhost -U <user> -d postgres -f sql/alert_mute_schema.sql
--
-- Dev (H2, ddl-auto: create) builds it from the entity and needs nothing here.
--
-- mute_key is strategy|symbol|timeframe with '*' for "every timeframe". It is the primary key on
-- purpose: the all-timeframes rule stores NULL in `timeframe`, and UNIQUE treats NULLs as
-- distinct, so a constraint on the three columns would let the same rule in twice.

CREATE TABLE alert_mute
(
    mute_key  VARCHAR(160)             NOT NULL PRIMARY KEY,
    strategy  VARCHAR(64)              NOT NULL,
    symbol    VARCHAR(64)              NOT NULL,
    -- NULL = every timeframe of that strategy.
    timeframe VARCHAR(8),
    muted_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    -- E-mail from Cloudflare Access; NULL when the request bypassed it (localhost on prod).
    muted_by  VARCHAR(254)
);
