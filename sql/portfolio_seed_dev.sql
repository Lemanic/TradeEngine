-- Dane testowe portfela dla lokalnego developmentu UI. NIE uruchamiaj na produkcji.
--
-- Generuje 60 dni snapshotów godzinowych, żeby dashboard miał co pokazać: niepuste
-- change.h24/d7/d30, sensowny wykres w każdym zakresie, kilka snapshotów complete=false
-- i szczyt PLN w innym dniu niż szczyt USD (kurs też falują).
--
-- Pozycje dostaje tylko najnowszy snapshot — historia czyta wyłącznie nagłówki, więc
-- pozycje dla starszych godzin byłyby martwym balastem.
--
--   psql -h localhost -U <user> -d <baza> -f sql/portfolio_seed_dev.sql

TRUNCATE portfolio_position, portfolio_snapshot RESTART IDENTITY CASCADE;

INSERT INTO portfolio_snapshot (taken_at, total_usd, total_pln, complete)
SELECT ts,
       ROUND(usd::numeric, 8),
       ROUND((usd * rate)::numeric, 8),
       -- co kilkadziesiąt godzin jeden snapshot niepełny, żeby UI miał co oznaczyć
       (EXTRACT(EPOCH FROM ts)::bigint / 3600) % 53 <> 7
FROM (
    SELECT ts,
           -- Poziom dobrany pod realny portfel (~5300 USD), żeby ostatni punkt historii
           -- nie rozjechał się ze świeżym snapshotem i wykres nie wyglądał na zepsuty.
           5300
             + 950 * SIN(EXTRACT(EPOCH FROM ts) / 86400.0 / 9)
             + 370 * SIN(EXTRACT(EPOCH FROM ts) / 3600.0 / 7)  AS usd,
           3.85 + 0.12 * SIN(EXTRACT(EPOCH FROM ts) / 86400.0 / 23) AS rate
    FROM GENERATE_SERIES(
             DATE_TRUNC('hour', NOW() AT TIME ZONE 'UTC') - INTERVAL '60 days',
             DATE_TRUNC('hour', NOW() AT TIME ZONE 'UTC'),
             INTERVAL '1 hour') AS ts
) AS series;

-- Pozycje najnowszego snapshotu, w udziałach sumujących się do 1.
WITH latest AS (
    SELECT id, total_usd, total_pln, total_pln / total_usd AS rate
    FROM portfolio_snapshot
    ORDER BY taken_at DESC
    LIMIT 1
)
INSERT INTO portfolio_position (snapshot_id, source, network, asset, cmc_id,
                                amount, price_usd, price_pln, value_usd, value_pln, stale)
SELECT l.id,
       p.source, p.network, p.asset, p.cmc_id,
       ROUND((l.total_usd * p.share / p.price_usd)::numeric, 18),
       ROUND(p.price_usd::numeric, 18),
       ROUND((p.price_usd * l.rate)::numeric, 18),
       ROUND((l.total_usd * p.share)::numeric, 8),
       ROUND((l.total_pln * p.share)::numeric, 8),
       p.stale
FROM latest l,
     -- Udziały odwzorowują realny podział na trzy źródła: Hyperliquid ~76 %, Trezor ~18 %,
     -- Rabby ~6 %. Dzięki temu donut na danych testowych wygląda jak na prawdziwych.
     (VALUES ('Hyperliquid', 'hyperliquid', 'USDC',  3408,  0.465, 1.0,      false),
             ('Hyperliquid', 'hyperliquid', 'HYPE',  32196, 0.177, 92.87,    false),
             ('Hyperliquid', 'hyperliquid', 'USD',   3408,  0.072, 1.0,      false),
             ('Hyperliquid', 'hyperliquid', 'ETH',   1027,  0.021, 2716.85,  false),
             ('Hyperliquid', 'hyperliquid', 'PENGU', 34466, 0.012, 0.0098,   false),
             ('Hyperliquid', 'hyperliquid', 'PURR',  34332, 0.012, 0.1676,   false),
             ('Trezor',      'bitcoin',     'BTC',   1,     0.129, 86000.0,  false),
             ('Trezor',      'bsc',         'ASTER', 36341, 0.049, 0.7297,   false),
             ('Trezor',      'bsc',         'BNB',   1839,  0.003, 787.43,   false),
             ('Rabby',       'ethereum',    'NPC',   27960, 0.025, 0.0211,   false),
             ('Rabby',       'ethereum',    'WOO',   7501,  0.019, 0.0134,   false),
             ('Rabby',       'ethereum',    'ETH',   1027,  0.015, 2716.85,  false),
             ('Rabby',       'arbitrum',    'ETH',   1027,  0.001, 2716.85,  true)
     ) AS p(source, network, asset, cmc_id, share, price_usd, stale);

SELECT COUNT(*) AS snapshotow, MIN(taken_at) AS od, MAX(taken_at) AS do FROM portfolio_snapshot;
SELECT COUNT(*) AS pozycji FROM portfolio_position;
