# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

**The Maven wrapper is NOT in git** — neither on `master` nor on feature branches, despite what
this file claimed for a long time. `mvnw` exists in local working trees only, so a fresh clone has
to build with a system `mvn` (3.9+, JDK 25). Commands below use `./mvnw` because it is there
locally; substitute `mvn` where it is not.

- Build: `./mvnw clean package`
- Run all tests: `./mvnw test`
- Run a single test: `./mvnw test -Dtest=TradeengineApplicationTests#contextLoads`
- Run the Spring Boot app (dev profile, H2 in-memory DB, port 8081): `./mvnw spring-boot:run`
- Run the standalone backtest (no Spring): `./mvnw exec:java -Dexec.mainClass=pl.tradeengine.backtest.BacktestApplication` — or launch `BacktestApplication.main` from the IDE.

Java target is **25** (set in `pom.xml`, despite the README badge saying 21). Spring Boot 3.5.7. Lombok is enabled via the annotation processor.

### Required environment variables

The dev profile is active by default (`application.yml`) and still reads these env vars at startup — without them the context fails to load:

- `DB_USER`, `DB_PASSWORD` — for the datasource. In dev these can be `sa` / empty (see `.run/TradeengineApplication.run.xml`).
- `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` — referenced in dev YAML too, but the Telegram publisher bean is `@Profile("prod")` only; in dev `LoggingAlertPublisher` is wired instead.

Activate prod against PostgreSQL (`localhost:5432/postgres`) with `-Dspring.profiles.active=prod`.

The portfolio module adds `CMC_API_KEY`, `PORTFOLIO_RABBY_ADDRESS` and
`PORTFOLIO_TREZOR_EVM_ADDRESS` — all optional at startup, see [Portfolio module](#portfolio-module).
Supply them outside the repository, in `config/application-{profile}.yml` (git-ignored).
## Testing

### Stack
- JUnit 5 + Mockito (already on the classpath via `spring-boot-starter-test`)
- AssertJ for fluent assertions
- Use `@ExtendWith(MockitoExtension.class)` for pure unit tests (no Spring context)
- Use `@SpringBootTest` + `@ActiveProfiles("test")` only for integration tests that need the full context

### Coverage

`jacoco-maven-plugin` runs on every `mvn test`; the report lands in
`target/site/jacoco/index.html`. **There is deliberately no `check` rule** — coverage is there to
be looked at, never to fail a build.

### Test profile

`src/test/resources/application-test.yml` exists and holds the H2 datasource plus stubs for the
env-var placeholders that the dev/prod YAML reads. Two things in it are load-bearing and easy to
delete by accident:

- **`app.strategies` must be present.** `ScenarioRegistry` reads it in `@PostConstruct` and in both
  getters, so a profile without that key fails context startup with an NPE, not with a readable
  message.
- **`LoggingAlertPublisher` is `@Profile({"dev", "test"})`.** `AlertPublisher` has no other
  implementation outside `prod` (Telegram), so without `test` on that list every
  `@SpringBootTest @ActiveProfiles("test")` dies on a missing `AlertPublisher` bean. That is why
  `contextLoads` used to run under `dev` — and therefore depended on `DB_USER` in the environment
  and on the git-ignored `config/application-dev.yml`.

### Conventions
- Test class name: `<ClassName>Test` in the same package under `src/test/`
- One test class per production class. Exception: `WebhookProcessingService` is split per entry
  point (`...HandleFvgTest`, `...HandleDivergenceTest`, `...HandleMomentumAlertTest`,
  `...HandlePriceUpdateTest`, `...EventEmissionTest`), because one class per method keeps the
  arrange blocks honest.
- Use `InMemory*Repository` adapters (already exist in `backtest/`) as fakes — **do not mock repositories**
- **`Mockito.verify` is not used anywhere in this suite.** To assert what a collaborator received,
  write a fake that records it. The ones that exist, all in `src/test/java/pl/tradeengine/`:
  `FakeAlertPublisher` (records published lists), `RecordingScenario` (records every `DomainEvent`
  it saw), `RecordingBiasRepositoryFake` (keeps `reason`, which `InMemoryBiasRepository` logs and
  drops), `StatefulFvgRepositoryFake` and `StatefulSwingPointRepositoryFake`.
- `application/service/WebhookChainHarness` assembles the real production chain without Spring:
  `WebhookProcessingService → ScenarioEngine → ScenarioRegistry → RecordingScenario` and
  `AlertDispatchService → FakeAlertPublisher`. Use it whenever the assertion is about **which**
  domain event was emitted — a mocked `ScenarioEngine` stubbed with `any()` cannot see that.
- Fixtures: static factories in `TestFixtures` (not builders, despite what this file used to say).
- For `GrinderStrategyScenario` tests: instantiate directly (like `BacktestApplication` does), don't load Spring

### Where fakes disagree with the JPA adapters

Relevant whenever a scenario test is green: the in-memory fakes and the real adapters have
**opposite** window boundaries, so a fixture sitting exactly on the edge proves nothing.

| Port | In-memory fake | JPA adapter |
| --- | --- | --- |
| `DivergenceRepository.findAllByDirectionSince` | `!isBefore(since)` — inclusive | `…DetectedAtAfter…` — **exclusive** |
| `SwingPointRepository.findRecentSwings` | `isAfter(since)` — exclusive | `detectedAt >= :since` — **inclusive** |

`StatefulSwingPointRepositoryFake` follows the adapter (inclusive) and implements
`getLastSwingDirection`, which `InMemorySwingPointRepository` returns `null` from — without it the
direction gate in `GrinderStrategyScenario` (lines 93 and 192) is always permissive and its two FVG
branches cannot be tested at all. These boundaries can only be pinned for real with a
`@DataJpaTest` on the adapters, which does not exist yet.

### What NOT to test
- DTOs and Lombok-generated code
- `TradeengineApplication` (covered by existing `contextLoads`)
- Pine Script output formatting in `backtest/`

## Architecture

This is a hexagonal/ports-and-adapters DDD application. The signal flow is:

```
TradingView webhook  →  adapter.inbound.TradingViewWebhookController
                     →  application.service.WebhookProcessingService  (maps DTO → domain event, persists via ports)
                     →  domain.scenario.ScenarioEngine                (fan-out to enabled Scenarios)
                     →  domain.scenario.* Scenario implementations    (return List<AlertToSend>)
                     →  application.service.AlertDispatchService
                     →  domain.port.AlertPublisher                    (Logging in dev, Telegram in prod)
```

Key things that are not obvious from any single file:

### Two entry points, two wirings

1. **`TradeengineApplication`** — Spring Boot. Scenarios are Spring beans (some `@Component`, some declared in `config/GrinderStrategyConfig`). `ScenarioRegistry` collects all `Scenario` beans into a map keyed by `name()` and cross-references them against the `app.strategies` list in YAML to decide which are enabled and on which timeframes. Repositories are JPA-backed (`adapter.outbound.persistence.*PersistenceAdapter` → `Jpa*Repository`).

2. **`BacktestApplication`** — a plain `main` with no Spring context. It instantiates `GrinderStrategyScenario` directly, registers them in `BacktestScenarioRegistry`, and uses `InMemory*Repository` adapters. It drives the system by replaying CSV candles via `HistoricalCandleLoader` + `CandleTimeline` and feeding them through `BacktestRunner`, which synthesizes the same `DomainEvent`s the webhook flow would produce. Output is Pine Script written under `output/`.

When changing scenario logic, make sure both paths still work. `GrinderStrategyScenario` currently has `handleFvgTouchTrigger` / `handleFvgInteraction` branches commented out in `onEvent` with a `// TURN IT OFF FOR BACKTESTING` marker — that comment is load-bearing context, not dead code.

### Strategy enablement is data-driven

Strategies are not selected by classpath presence. Each `Scenario` bean declares a stable `name()` (e.g. `"GRINDER_SWING_D1_H1"`, `"DOUBLE_DIVERGENCE_STRATEGY"`), and `application-{profile}.yml`'s `app.strategies` list decides which are `enabled` and which `timeframes` they apply to. Adding a new strategy means: implement `Scenario`, register the bean, and add a YAML entry. Renaming `name()` without updating YAML silently disables the strategy.

### The Grinder strategy uses three timeframes at once

`GrinderStrategyScenario` is parameterized by **biasTimeframe** (D1 or W1, where market bias is derived), **poiTimeframes** (HTF FVG zones to look for), and **triggerTimeframe** (LTF swing-point or FVG-touch trigger). Two beans of this class are wired in `GrinderStrategyConfig` — `GRINDER_SWING_D1_H1` and `GRINDER_POSITION_W1_H4` — so the same code drives swing- and position-trading variants. The `triggerTimeframe` in YAML must match the value passed in `GrinderStrategyConfig`, otherwise `ScenarioRegistry.getScenariosFor` will skip the scenario.

### FvgLtfConfirmationScenario — the only enabled strategy in dev

`name()` = `"FVG_LTF_CONFIRMATION"` · `id()` = `3L` · registered as `@Component` (no factory config needed)

This strategy is `enabled: true` in `application-dev.yml` across timeframes `[M1, M3, M5, M15, H1, H4]`.
It operates in two layers: a structural **HTF context** (H1/H4 FVG zone) and a **LTF confirmation trigger**.

**Signal flow:**

1. An HTF FVG on **H1 or H4** is touched (`FvgTouchedEvent`) → the zone becomes "armed". No alert is fired at this stage — only logging. The zone stays armed as long as its status is `TOUCHED` (not `FILLED`/`CONSUMED`).
2. While any HTF FVG is armed for the given symbol+direction, the strategy listens for one of two LTF triggers:
    - **Divergence trigger** — `DivergenceDetectedEvent` on any of `[M1, M3, M5, M15, H1, H4]`, but with a minimum count requirement within a rolling `DIV_LOOKBACK_CANDLES = 40` candle window:
        - `M1` → 4 divergences minimum
        - `M3` → 2 divergences minimum
        - `M5, M15, H1, H4` → 1 divergence (single is sufficient given HTF context)
    - **LTF FVG trigger** — `FvgCreatedEvent` on `[M5, M15, H1]` in the **same direction** as the armed HTF FVG
3. Either trigger fires an `AlertToSend`. Risk management fields (`sl`, `tp`) are intentionally `ZERO` / `Optional.empty()` — not in scope yet.

**HTF FVG selection rule:** when multiple armed HTF FVGs exist for the same symbol+direction, H4 is preferred over H1 (higher TF context is stronger). This is hardcoded in `findArmedHtfFvg()` via `HTF_CONTEXT_TIMEFRAMES` index sort — do not reorder that list without understanding the impact.

**Important distinctions from GrinderStrategy:**
- This scenario does **not** use `BiasRepository` or `SwingPointRepository` — only `FvgRepository` and `DivergenceRepository`.
- `BIAS_TIMEFRAMES = {D1, W1}` in `WebhookProcessingService` is **unrelated** to this strategy's timeframe sets. FVG_LTF_CONFIRMATION has its own `HTF_CONTEXT_TIMEFRAMES = {H1, H4}` and `LTF_DIVERGENCE_TIMEFRAMES = {M1…H4}`.
- This strategy has **no backtest path** — it is Spring-only and cannot be run via `BacktestApplication`.

### Bias vs. swing points split

In `WebhookProcessingService.handleMomentumAlert`, the set `BIAS_TIMEFRAMES = {D1, W1}` decides whether an incoming momentum alert updates the bias (via `BiasRepository`) or registers a swing point (via `SwingPointRepository`). The same split is mirrored in `BacktestRunner` (`handleWaveTrendCross`). Keep them in sync.

### FVG lifecycle is stateful in the DB

FVG status transitions (`CREATED → TOUCHED → FILLED → CONSUMED`) and the `AlertMode` (ARMED/PAUSED) are driven by `handlePriceUpdate`. Two timing constants live there: `X_OUTSIDE_CANDLES_TO_PAUSE = 4` (pause alerts after price leaves the zone) and `Y_AFTER_FILLED_TO_EXPIRE = 5` (TTL after fill before consumption). The backtest path in `BacktestRunner.updateFvgStates` re-implements similar transitions against `InMemoryFvgRepository` — but its rules differ slightly (e.g., backtest doesn't use AlertMode pausing). When changing FVG state semantics, check both.

### Webhook payloads come from TradingView Pine Script

The DTOs in `application/dto/` are tuned for specific Pine Script payloads, not generic alerts. The `Symbol`, `Timeframe`, `Direction`, `FvgKind` mappers (`fromCode` / `fromSignal` / `fromSignalType`) define the wire contract.

## Portfolio module

Hourly snapshots of a crypto portfolio plus a read API for the dashboard. Independent of the
trading strategies — it shares only the Spring context and the database. **`docs/portfolio-api.md`
is the source of truth for the response shapes**; this section describes the implementation.

### Flow

```
@Scheduled (cron 0 0 * * * *, UTC)   ·  or POST /api/portfolio/snapshots
    → application.service.portfolio.PortfolioSnapshotService
        → domain.port.portfolio.HoldingsSource     (EVM via JSON-RPC, xpub via Blockbook,
                                                   Hyperliquid via /info, Coinbase via CDP,
                                                   manually entered amounts)
        → domain.port.portfolio.PriceProvider      (CoinMarketCap, USD + PLN)
        → domain.port.portfolio.PortfolioSnapshotRepository  (JPA; one hour, one snapshot)

GET /api/portfolio/summary | /history
    → application.service.portfolio.PortfolioQueryService  →  PortfolioMetrics (rounding, sampling)
```

`config.HoldingsSourceRegistry` builds the sources from configuration: EVM wallet entries are
grouped by label, so `Rabby` on ethereum + arbitrum is one source, while every xpub becomes a
source of its own. **The label decides the split in `summary.sources[]` and in the donut**, so it
is chosen for what should be visible separately, not for where the keys happen to live:

- `Trezor` is an EVM address on bsc **and** a Bitcoin xpub — one label, one donut slice, while
  `HoldingsSource.networks()` narrows a failure down to the affected half.
- `Hyperliquid` is **the same address as `Rabby`**, but a separate label, because that capital is
  meant to be its own slice in the donut. Hence it uses `PORTFOLIO_RABBY_ADDRESS` under a
  different name.

Renaming a label resets the carry-forward history for that source: stored positions carry the old
name in the `source` column, so until the first successful snapshot there is nothing to carry
forward from. It heals itself after one cycle.

**When adding a source, warn the dashboard repository — but the reason is not what used to stand
here.** The first version of this note claimed the donut palette only works for three sources and
that a fourth requires changing the color values. **Both statements are false**, and it is worth
knowing why, because the same mistake misled that side first and then me.

What binds is **the ordering of the series, not the choice of colors**. The contrast validator
treats charts where only neighbours touch (bars, lines) differently from those where any pair can
touch. The donut fell into the second category **only because the front end sorted the slices by
value, descending** — with that sorting any two sources can become neighbours. Measured by them,
in both themes:

| How the palette is used | Sources that pass |
| --- | --- |
| ordered by value (every pair possible) | **4**, and only two sets out of the eight colors pass; at **5** none of the 56 subsets passes |
| fixed order (by name, neighbours invariant) | **6** — the current palette passes every check |

So past four sources, repainting the hexes **will not help**: you have to either reduce the number
of series (fold them into "Other"), or split them across panels, or fix the slice order. That
changes how the chart is read, so it is **the user's decision**, neither ours nor theirs.

As of today: **six sources** — `Rabby`, `Trezor`, `Hyperliquid`, `Coinbase`, `TAO staking`,
`TRB staking`. `Coinbase` is the one that arrived last; the five before it were everything this
note described earlier. With slices sorted by value this is past the range in which **any choice of
colors from the documented eight passes the checks in both themes** — already at five none of the
56 subsets passed, and a sixth slice only adds pairs that can touch. The donut works and every
slice is labelled, but the contrast threshold is exceeded for some pair of neighbours.

The fix lies **neither in the backend nor in the palette**: either fold the smallest sources into
"Other", or split them across panels, or fix the slice order. Fixing the order is the one option
that needs nothing else — the current palette was measured as passing every check at six sources in
a fixed order, which is exactly where the portfolio now stands. This is the user's decision about
how his own chart is read — it is waiting for him.

Observed on the dashboard on 2026-10-27, with amounts masked: six slices at 44.0 / 36.8 / 10.5 /
3.1 / 3.1 / 2.4 percent — so the two smallest are the obvious candidates for "Other" if that is the
route chosen.

A **seventh** source goes beyond everything measured: six is where the fixed-order check stops, so
adding one means asking the dashboard side to re-run the validator rather than assuming it fits.

### Degradation rules

Fail softly, but never invent numbers:

| What breaks | Behavior |
| --- | --- |
| A source throws | Amounts carried forward (rule below), positions `stale=true`, snapshot `complete=false` |
| No price for a `cmcId` | Last known price from the database, position `stale=true`, `complete` unchanged |
| No price and no history | Position skipped, `complete=false` — a zero would lie about the portfolio's value |
| Prices unavailable (401/429/network) | Nothing stored, only an `ERROR` in the log |
| No source configured | Nothing stored, a `WARN` in the log |

#### Carrying amounts forward when a source fails

The goal: no hole in the total and no step on the chart, while still not lying. The service walks
snapshots **backwards** and stops at the first conclusive answer:

| What it sees in a snapshot | Conclusion |
| --- | --- |
| positions from that source are present | carries those amounts forward, prices them at **current** prices, `stale=true` |
| no positions, but the snapshot is `complete` | in that snapshot **every** source answered, so the absence of positions is the source itself saying "I no longer hold this" — carrying an older amount forward would resurrect something sold, so it carries nothing |
| no positions, snapshot `complete=false` | inconclusive, because the source may have been failing then too — it keeps looking further back |

The search window is bounded by `portfolio.carry-forward-max-age` (7 days by default). An older
amount is not used: it could have been sold in the meantime, and a wrong number is worse than an
honest hole.

The distinction between "emptied" and "unreachable" is **the essence** here, not a detail. The
first version of this mechanism read only the newest snapshot and lost a position forever once it
was missing from it; the naive fix — "keep looking until you find something" — would resurrect
sold assets. Both behaviors are pinned by tests
(`doesNotResurrectAPositionThatTheSourceReportedAsEmptied`,
`looksPastAnInconclusiveSnapshotInsteadOfLosingThePosition`).

Measured live by breaking the Ethereum RPC: the total fell from 5,321.60 to 5,321.38 USD, i.e. by
0.004% stemming from price movement — without this mechanism it would have fallen by 317.51 USD.

`taken_at` is always truncated to the full UTC hour and is `UNIQUE` — a repeated snapshot within
the same hour **replaces** the previous one. In the JPA adapter, `deleteByTakenAt` + `flush()`
before the write is necessary: without the flush Hibernate would run the INSERT before the DELETE
and blow up on uniqueness.

### Configuration

Prefix `portfolio`, class `config.PortfolioProperties`. The repo is **public**, so the addresses
and the key are read exclusively from outside, with an empty default — a missing variable is a
warning in the log, never a startup failure.

| Variable | Role |
| --- | --- |
| `CMC_API_KEY` | CoinMarketCap key. Without it a snapshot logs an error and stores nothing. |
| `PORTFOLIO_RABBY_ADDRESS` | EVM address for the `Rabby` label (ethereum, arbitrum). |
| `PORTFOLIO_TREZOR_EVM_ADDRESS` | EVM address for the `Trezor` label (bsc). |
| `PORTFOLIO_TREZOR_BTC_XPUB` | BTC extended public key for `Trezor` (zpub, BIP84). |
| `PORTFOLIO_TREZOR_BCH_XPUB` | BCH account key (xpub, BIP44 — **a different path than BTC**). Dormant today, Blockbook hosts return 403. |
| `PORTFOLIO_TREZOR_BCH_AMOUNT` | BCH amount entered by hand, as long as reading it from the xpub is impossible. Zero it out when enabling the xpub. **Read only on the first startup** — the database rules afterwards, see [Manual amounts](#manual-amounts). |
| `PORTFOLIO_TAO_STAKING_AMOUNT` | TAO amount in staking. **It grows for two reasons — refresh it**, but through `PUT`, not through this variable. |
| `PORTFOLIO_TRB_STAKING_AMOUNT` | TRB amount in staking. As above. |
| `COINBASE_KEY_NAME` | CDP key name: `organizations/{org}/apiKeys/{id}`. Not a secret in itself. |
| `COINBASE_PRIVATE_KEY` | EC private key (SEC1 PEM, `\n` accepted). **This one is a secret — make it read-only.** |
| `DB_USER`, `DB_PASSWORD` | As in the rest of the application. |

Hyperliquid uses `PORTFOLIO_RABBY_ADDRESS` — it has no variable of its own, even though it appears
under its own `Hyperliquid` label in the source breakdown.

`portfolio.carry-forward-max-age` (`7d` by default) bounds how old an amount may be and still be
carried forward after a source fails — see
[Carrying amounts forward](#carrying-amounts-forward-when-a-source-fails).

Values are supplied **outside the repo**, in `config/application-dev.yml` (or `-prod`), which is
in `.gitignore`. A file in `./config/` takes precedence over the one in `src/main/resources`:

```yaml
# config/application-prod.yml — never goes into git
DB_USER: "..."
DB_PASSWORD: "..."
CMC_API_KEY: "..."
PORTFOLIO_RABBY_ADDRESS: "0x..."
PORTFOLIO_TREZOR_EVM_ADDRESS: "0x..."
```

Do not override `portfolio.evm.wallets` there — YAML lists do not merge, they replace, so the
remaining wallets would disappear. Define only the names the placeholders look for.

`portfolio.enabled` turns on **the scheduler only** (`config.PortfolioSchedulerConfig` contributes
`@EnableScheduling`). A manual `POST /api/portfolio/snapshots` works regardless of the flag; in
tests the flag is off.

Networks and the token whitelist live in `application-{profile}.yml`. Contracts and `decimals`
verified on-chain via `symbol()`/`decimals()`, ids via CoinMarketCap:

| Network | Assets | cmcId |
| --- | --- | --- |
| ethereum | native ETH, NPC `0x8eD97a63…08F6`, WOO `0x4691937a…5D4B` (both 18 decimals) | 1027, 27960, 7501 |
| arbitrum | native ETH | 1027 |
| bsc | native BNB, ASTER `0x000Ae314…556A` (18 decimals) | 1839, 36341 |
| bitcoin | BTC from an xpub via Blockbook | 1 |
| hyperliquid | spot, perps equity and staking; separate `Hyperliquid` source | 3408, 34332, 32196, 1027, 1, 34466 |
| coinbase | exchange balances via CDP, whitelist with `min-amount` against dust | 3408, 3773, 13502, 1, 1027 |
| bittensor, ethereum (staking) | `TAO staking`, `TRB staking` — amounts entered by hand | 22974, 4944 |

Adding an EVM token is **one line of YAML, no code and no schema redeploy**. Follow this procedure
every single time — do not guess the address, the `decimals` or the id:

1. Find the contract address in two independent sources (an explorer + the platform list in CMC).
2. Confirm it on-chain against the target RPC: `symbol()` = `0x95d89b41`, `decimals()` = `0x313ce567`.
3. Check `balanceOf` (`0x70a08231` + the address padded to 32 bytes) against your own address — if
   it comes out zero, it is either the wrong contract or the wrong network.
4. Take the `cmc-id` from CoinMarketCap and add the entry to that network's `tokens`.

This procedure applied to ASTER on bsc: `symbol()` returned `ASTER`, `decimals()` → 18,
`balanceOf` → 354.56577328, CMC id 36341, the platform in CMC matching BscScan.

#### Manual amounts

`portfolio.manual.holdings` holds the entries that cannot be read automatically. **Only the amount
comes from the configuration** — pricing goes down the normal CoinMarketCap path, so the value and
the PLN conversion stay consistent with the rest. A position is **not** `stale` merely because it
is manual (the contract says so), and the source has nothing to call, so it never fails and never
breaks `complete`.

An amount is wealth data, so the tracked YAML holds a placeholder. A zero means "I hold nothing
here right now" — the entry stays declared and comes back once the amount grows. `source` decides
which slice of the portfolio the position joins; `key` is the stable identifier under which
`PUT /api/portfolio/manual-holdings/{key}` addresses it. In production that one path sits
behind an Access application of its own — see
[Two Access applications, two sessions](#two-access-applications-two-sessions).

**The configuration is a registry of WHICH positions exist; the current AMOUNT lives in the
database** (table `manual_holding`). The YAML value is only a seed: `PortfolioConfig` inserts it on
the first startup (`@Bean(initMethod = "seedMissing")`) and from then on only `PUT` changes it —
**editing the YAML no longer does anything**, which is surprising and worth remembering. Without
that split a `PUT` would not survive a restart, and the contractual `updatedAt` would have nowhere
to come from.

The seeding sits in `initMethod` rather than in an `ApplicationRunner`, deliberately: a runner runs
**after** the port opens, so the first request managed to see `updatedAt: null`, which the contract
does not allow.

`ManualHoldingsSource` does not receive the amount in its constructor, but a read function
(`ManualHoldingService::amountFor`), called on every snapshot. That is why changing an amount works
without a restart and without rebuilding `HoldingsSourceRegistry` — but also why it shows up **only
in the next snapshot**, not immediately in `summary`.

Three positions come in this way today: **BCH** (temporarily, because Blockbook hosts return 403)
and **TAO and TRB staking**, which none of our APIs reads.

**The one real weakness: the number is true only up to the moment the state changes.** For BCH that
means "until you move the coins". For staking it is worse, because the amount grows for **two**
independent reasons: **rewards accrue continuously**, and on top of that come **top-ups — the owner
buys regularly and sends to the staking wallet**. The entry therefore ages by itself, always
understating the portfolio, and nothing signals it: neither `stale` nor `complete=false`. Reading
from the chain or an API is always better wherever it is possible.

The practical consequence when updating these amounts: **the rate of growth tells you nothing**. A
jump larger than the reward rate alone would produce — several times within a single day, even — is
an ordinary top-up, not a mistake, and there is nothing to ask about. Only these are suspicious: a
**smaller** value without explanation (unstaking?), an order-of-magnitude slip, or a comma instead
of a decimal point (the API rejects it as `invalid_body`).

**Watch out for double counting.** Positions aggregate by `(source, network, asset, cmcId)`, so a
manual BCH entry and an xpub source under the same label would silently add up.
`HoldingsSourceRegistry` warns about this at startup (`TWO SOURCES COVER '…'`), but the warning
does not replace the decision: when enabling the xpub read, zero out
`PORTFOLIO_TREZOR_BCH_AMOUNT`.

#### Coinbase

`portfolio.coinbase` reads balances through the Advanced Trade API
(`GET /api/v3/brokerage/accounts`). Authentication is a **CDP key**: the name
(`organizations/{org}/apiKeys/{id}`) plus an EC private key in PEM — two separate values, both
from outside. The key should have **read-only** permissions; the portfolio needs nothing more.

`CoinbaseJwt` builds the token per request, and three things are easy to get wrong in it, because
each one ends in a bare `401` with no hint:

- **the algorithm must be ES256** (ECDSA P-256); Ed25519 keys are not accepted;
- **`uri` is `{METHOD} {HOST}{PATH}`** — the host without a scheme, the path without the query
  string — and the token binds to that one request and lives for 120 seconds;
- **JCA signs in DER, while JWS requires a raw `R||S`** of 32 bytes each. Without that conversion
  the token is correctly built and never verifies, and a padding bug surfaces **at random** — only
  when `r` or `s` starts with a byte ≥ 0x80. That is why the conversion has a round-trip test at
  both extremes rather than one example vector.

Coinbase hands the key over as SEC1 PEM (`BEGIN EC PRIVATE KEY`), which `KeyFactory` does not read
— it wants PKCS#8. Rather than adding a library, the 32-byte scalar is extracted from the DER in
`CoinbaseJwt`.

**The currency whitelist matters more here than on-chain.** An exchange account collects dust from
fees and conversions: this portfolio holds `7E-7` BTC and `3.6E-9` ETH, a few cents in total. That
is why a whitelist entry has an optional `min-amount`, which cuts off the dust without dropping the
currency from the configuration.

#### Hyperliquid

`portfolio.hyperliquid` reads capital from the public `POST /info` API (no key, the address goes in
the body). Three separate requests, because none of them covers the whole: `clearinghouseState`
(perps equity), `spotClearinghouseState` (spot balances), `delegatorSummary` (staking). The address
is the same `PORTFOLIO_RABBY_ADDRESS` — **no new environment variable**.

All three buckets land on **one network**, `hyperliquid`, separated by asset. Do not split that
`network` into three: it is the user's decision, and the aggregation key is
`(source, network, asset, cmcId)`, so splitting by network adds nothing when the assets differ anyway.

Three things the code does not show:

- **Tokens wrapped by Unit are priced as what they actually are**, and Hyperliquid states it itself
  in `fullName`: `UETH` is "Unit Ethereum" (ETH, 1027), `UBTC` is "Unit Bitcoin" (BTC, 1),
  `HPENGU` is "Pudgy Penguins" (PENGU, 34466). When adding a token to the whitelist, check
  `fullName` in `spotMetaAndAssetCtxs` — do not guess from the ticker.
- **`accountValue` is not a token balance**, but the account equity in USD after unrealized PnL. It
  is reported as an amount of the `USD` asset priced with USDC (~1), so it comes out as a separate
  row worth exactly that many dollars instead of blending into spot USDC. This position is the most
  volatile one in the portfolio — the account is leveraged (40× observed on BTC) and a liquidation
  can take it to zero.
- **The spot whitelist is mandatory.** Hyperliquid also returns balances for tokens never touched
  (zero UBTC, USDE, USDT0, USDH). Zeros are skipped and everything outside the whitelist is
  ignored — so after a new spot purchase the token has to be added, otherwise it silently
  disappears from the portfolio.

What we deliberately do not read: vault deposits (`userVaultEquities`) and sub-accounts
(`subAccounts`) — checked for this portfolio, they return `[]` and `null`. Once they show up, a
fourth bucket will have to be added.

An API pitfall that cost me a wrong valuation: in `spotMetaAndAssetCtxs` **`universe` and `ctxs`
are not positionally aligned** (330 vs 1003 entries). Join on `ctx.coin` equal to
`universe[].name`, never by `zip`. Our adapter does not use that endpoint — prices come from
CoinMarketCap — but I am noting it, because it is the natural reflex when diagnosing.

#### UTXO chains (xpub via Blockbook)

`portfolio.blockbook` reads balances from an extended public key through Trezor's public Blockbook
(`GET /api/v2/xpub/{xpub}?details=basic`, balance in satoshi). Blockbook derives the addresses
itself and applies a gap limit, so a `zpub` (BIP84) is enough — there is no key derivation on the
application side. Adding BCH means a `bitcoin-cash` network entry with
`base-url: https://bch1.trezor.io`, `cmc-id: 1831`, `decimals: 8` plus a wallet entry under the
same `Trezor` label.

Two things the code does not show:

- **`User-Agent` is mandatory.** Without the header Trezor's server returns `403` with a Cloudflare
  page, not JSON. That is why `portfolio.blockbook.user-agent` is configurable.
- **The xpub travels in the URL path**, and `RestClient` messages quote the URL. That is why
  `BlockbookClient` never lets the source exception's message through — it composes its own, with
  the network name and the HTTP code. An xpub reveals the wallet's entire balance and history, so
  it must not reach the log. There is a test for it (`hasMessageNotContaining`).

A single host is a single point of failure (`btc2.trezor.io` was answering `503` during
implementation), but there is deliberately no failover here: a Blockbook outage is an ordinary
source failure, so the BTC amount carries forward from the previous snapshot and the position gets
`stale=true`.

**BCH is configured, but dormant and currently unreachable.** Two independent obstacles:

- `PORTFOLIO_TREZOR_BCH_XPUB` is missing. BCH has a different derivation path than BTC, so this is
  **a separate key from Trezor Suite** — not the same `zpub`. Without the variable the entry is
  skipped with a `Wallet 'Trezor' has no xpub configured` warning, so nothing breaks.
- As of 2026-10-05: **every host from `bch1` to `bch5.trezor.io` returns `403`** with a Cloudflare
  "Sorry, you have been blocked" page, on every path and regardless of `User-Agent` (checked with a
  browser one too). A control request to `btc1.trezor.io/api/v2` at the same moment: `200`. So it
  is a firewall rule on Trezor's side for the BCH hosts, not a problem with our request, and not
  something to work around.

As long as the block lasts, setting the xpub will only make the source report a failure — i.e.
carry-forward and `stale=true`, with the snapshot `complete=false`. The options once BCH is needed:
wait (the host worked before), point `base-url` at another Blockbook instance (but an xpub reveals
the whole wallet, so that is a trust decision), or run your own instance.

### Database

Prod runs with `ddl-auto: validate`, so the tables have to exist **before** startup:

```bash
psql -h localhost -U <user> -d postgres -f sql/portfolio_schema.sql
```

The script creates **three** tables: `portfolio_snapshot`, `portfolio_position` and
`manual_holding`. It agrees with the entities on columns, types and scales (verified by exporting
Hibernate's DDL with the Postgres dialect). Dev (H2, `create`) builds them from the entities itself.

`manual_holding` arrived later than the other two, so on a machine that came up before it,
`validate` will fail the startup — the script has to be run again (`CREATE TABLE` for existing
tables will raise an error, in which case run just the `CREATE TABLE manual_holding` from the end
of the file).

`portfolio_snapshot` keeps **both** totals (`total_usd`, `total_pln`), because history, changes and
the ATH are computed separately for each currency — the peak in PLN may fall on a different day
than in USD and cannot be derived by conversion.

### API

| Endpoint | Notes |
| --- | --- |
| `GET /api/portfolio/summary?currency=USD\|PLN` | `204` when there is no snapshot at all. `400` for an unknown currency. |
| `GET /api/portfolio/history?range=24H\|7D\|30D\|90D\|1Y\|ALL&currency=…` | `200` with an empty array when there is no data — not `204`. `400` for a missing/unknown `range`. |
| `POST /api/portfolio/snapshots` | Operator-facing, outside the UI contract: returns the snapshot as stored, in both currencies and at full precision. `503` when nothing was stored. |
| `GET /api/portfolio/manual-holdings` | Registry of manual positions: no values and no prices. Ordered as in the configuration. |
| `PUT /api/portfolio/manual-holdings/{key}` | Changes **the amount only**. `400` → `missing_amount`, `invalid_amount` (negative), `invalid_body` (text, `NaN`). `404` → `unknown_key`, with the list of available keys in `message`. |

The lower bound of the history window is **exclusive**, the upper one inclusive — otherwise a
24-hour window would hold 25 hourly points instead of the documented 24. At 4h and 1d resolution
the number of points can still exceed the one in the table by one (`30D` → 181), because the
buckets are aligned to UTC boundaries and the window touches the edge bucket. There is no trimming
it: the last point must remain the same snapshot as `summary`. The contract calls these numbers
approximate, so this conforms to the spec — the UI must not assume a fixed number of points.

Both read endpoints are anchored to **the newest stored snapshot**, not to the clock. That way the
last history point is always the same snapshot that `summary` describes — the requirement from the
"Consistency between endpoints" section — even when collection stopped many hours ago.

Precision: amounts 2 places, `pct` 4 and in percentage points, `amount` full precision.
**Exception: `price`** — 2 places above 1.00, and 6 significant digits below. NPC costs ~$0.022, so
two places would turn it into $0.02 and `value` would stop matching `amount × price`; the UI's
`price()` renders 4 places below a dollar anyway.

Two invariants the dashboard relies on, pinned by tests: `sources` is empty **if and only if**
`positions` is empty (every position contributes its source — that is how the UI recognizes an
empty portfolio), and `pct` at `total = 0` comes out `0`, never `null` or `NaN`.

An `amount` with 18 decimal places **does not survive a `double`** —
`6390.281180127170627929` loads in JavaScript as `6390.28118012717`. For display that is
irrelevant, but if the exact amount ever has to be shown, the number in the JSON will not carry it,
and only then is a string worth considering — the contract requires numbers today.

`server.error.include-stacktrace: never` in `application.yml` is **deliberate**: devtools turns
that value up to `ALWAYS`, so without it the `404` body leaked a stack trace. The production
package has no devtools, but response safety should not depend on the classpath.

Schema: `/v3/api-docs` (OpenAPI 3.1, always on), Swagger UI only in dev at `/swagger-ui.html`.
`OpenApiConfig` contains a customizer that rewrites nullable references into `anyOf: [ref, null]` —
without it springdoc emits `{"type":"null","$ref":…}` and the TypeScript generator produces
`ath: null` instead of `Ath | null`.

No CORS: the dashboard is served from the same origin. Authentication lives outside the application
(Cloudflare Access); TradingView webhooks have to stay open, so the policy never covers the whole
domain.

### Deployment

Two subdomains point at **the same application**, so the access policy has to account for that:

| Host | What it serves | Access |
| --- | --- | --- |
| `webhook.tradeengine.app` | `POST /webhooks/*` only | open — TradingView cannot authenticate |
| `portfolio.tradeengine.app` | the dashboard at the root and `/api/portfolio/*` | Cloudflare Access — **two applications**, see below |

**Port: prod runs on 8080**, because `application-prod.yml` does not set `server.port` and Spring
takes the default. Only dev has an explicit `8081`. So point verification commands on the machine
at `http://localhost:8080`.

**The webhook host proxies every path to the origin, not just `/webhooks/*`.** Without an edge rule,
`https://webhook.tradeengine.app/api/portfolio/summary` serves the whole portfolio publicly. That is
why a *default-deny* rule for this host sits in Cloudflare WAF:

```
(http.host eq "webhook.tradeengine.app" and not starts_with(http.request.uri.path, "/webhooks/"))
```
→ Block. It also closes `/portfolio*`, `/v3/api-docs`, `/api/signals/*` and whatever comes later.
Access on the other host does not take care of this — policies work per host.

Before the first startup on prod: the WAF rule, then `sql/portfolio_schema.sql` (**three** tables —
if the database came up before `manual_holding` was added, run just `CREATE TABLE manual_holding`),
then `config/application-prod.yml` **in the process's working directory** (`./config/` is resolved
against the cwd, not against the jar), then the dashboard build in the directory
`PORTFOLIO_UI_DIR` points at.

**The production machine runs Windows**, which invalidates a few reflexes: there is no `sudo`, no
peer auth and no `systemd`, so Postgres is entered with a password (`psql.exe` from
`C:\Program Files\PostgreSQL\{version}\bin`, usually outside `PATH`), and the working directory is
set by a .bat file, by "Start in" in Task Scheduler or by `AppDirectory` in NSSM — not by a unit's
`WorkingDirectory`.

The dashboard path **must** then come from `PORTFOLIO_UI_DIR` (e.g.
`file:C:/tradeengine/portfolio/`). The default `file:/opt/tradeengine/portfolio/` expands on
Windows to a non-existent `\opt\tradeengine\portfolio\`: startup passes, the API works, and `GET /`
returns 404 — a silent failure, so check it explicitly after a deploy.

A consequence of Access on the whole host: once the session expires, Cloudflare answers a `fetch`
with a redirect to the login page, i.e. HTML with status 200. The front end recognizes this by
`redirect: 'manual'` and `response.type === 'opaqueredirect'`, which is unambiguous because **the
API never answers 3xx** — checked for every path, including a trailing slash. Adding a redirect
under `/api` would break that detection.

#### Two Access applications, two sessions

`portfolio.tradeengine.app` is covered by **two** Access applications, not one, and each hands out
its **own `CF_Authorization` cookie with its own session duration**:

| Application | `aud` | Covers |
| --- | --- | --- |
| host-wide | `e705954a…` | `/`, `/api/portfolio/*`, `/v3/api-docs` |
| path-scoped | `68dd411d…` | `/api/portfolio/manual-holdings` — the write path |

So a live session is not a live session for everything. Measured on 2026-10-25 with one valid
host-wide cookie: `/`, `/api/portfolio/summary` and `/v3/api-docs` answered `200`, while `GET` and
`PUT` on `/api/portfolio/manual-holdings` answered `302` to the login page. Reads work, the write
does not, and nothing is wrong with the application.

**Logging in at the root does not mint the second cookie.** Access authenticates only the
application a request actually asked for, so a session for the write path comes only from opening
`https://portfolio.tradeengine.app/api/portfolio/manual-holdings` in the browser itself. The
browser then holds **two cookies of the same name**, told apart only by their `Path` attribute
(`/` versus `/api/portfolio/manual-holdings`). Copying the wrong one out of DevTools is the trap,
and it looks exactly like an expired token.

Why the second application exists is not recorded here — deliberately gating writes harder than
reads would be a sensible reason, but it could equally be a leftover. Zero Trust shows its
`Session Duration` and its creation date in the audit log; the same two numbers also settle whether
a failing write means a lapsed session or an application added after the last successful write.
**From outside those two causes are indistinguishable**, so do not guess between them.

Diagnosing this from outside, in order:

1. A `3xx` is always Access, never the API (see the paragraph above).
2. Decode the `meta=` parameter of the login redirect. **`auth_status: NONE` means there is no
   token for *that* application** — not that a token was rejected.
3. Compare `aud` in the cookie against `aud` in that `meta`. Both are genuine application AUDs, so
   a mismatch means two applications are in play, **not** that one was deleted and recreated.
   Reading it the second way costs a pointless re-login, because the root mints the other cookie.
4. `GET /api/portfolio/snapshots` answering **`405`** is the quick proof that a request crosses
   Access and reaches Spring: that path lies outside the path-scoped application, so only the
   missing GET handler stops it.

Two consequences worth keeping:

- **From outside, the write needs the service token — and that is the intended path.** The token
  `tradeengine-staking` (Zero Trust → Access controls → Service credentials) is attached to the
  path-scoped application and covers both verbs: verified 2026-10-26, `GET` and `PUT` on
  `/api/portfolio/manual-holdings` each answered `200` carrying `CF-Access-Client-Id` and
  `CF-Access-Client-Secret`. On the dev machine that pair lives in `config/cf-access.env`, kept out
  of the repo by the `/config/` rule in `.gitignore`. **A browser session is simply the wrong
  credential here**: a cookie minted at the root belongs to the host-wide application and is
  refused on this path however fresh it is — which is exactly what makes the failure look like an
  expired token and sends you re-logging in for nothing.
- **`service_token_status: false` in the login `meta` describes the request that was made, not the
  application's configuration.** It never proves that no token is attached; only the application's
  policies in Zero Trust do. (An earlier version of this section drew the wrong conclusion here.)
- **On the prod machine none of this applies**: `http://localhost:8080` skips Cloudflare entirely
  and needs no credential at all.
- **An amount can be verified without read access to `/manual-holdings`**: force a snapshot and
  read it out of `positions[]`. That is how `TAO staking` 11.77 → 15.8676 was confirmed on
  2026-10-25 while the read endpoint stayed gated.

### UI

The backend **does not carry its own UI**. The dashboard is a separate repo
(`tradeengine-dashboard`, Vite + React), built with `base: '/'` and uploaded to a directory on the
production machine. Spring serves that directory from outside the jar:

```yaml
spring.web.resources.static-locations: ${PORTFOLIO_UI_DIR:file:/opt/tradeengine/portfolio/},classpath:/static/
```

`PORTFOLIO_UI_DIR` is supplied in `config/application-prod.yml`, because the path depends on the
machine's operating system — see [Deployment](#deployment).

Releasing the front end then does not force a backend redeploy, and the origin stays single — no
CORS and no second Access policy. `static-locations` **overrides** the defaults, so
`classpath:/static/` has to be listed explicitly. `index.html` lands at the root (`GET /`), the
bundle under `/assets/`, and `config.StaticResourceConfig` splits the cache headers (the reason is
in its javadoc). **Do not add a mapping for `/`** — it would shadow the welcome page and `/` would
return a redirect instead of the dashboard; that is exactly what happened while
`PortfolioPageController` existed.

**No SPA fallback**: the dashboard is a single view with no router, so `/**` does not forward to
`index.html`. Once a router arrives, this will have to be added — it will show up as a 404 when a
subpage is refreshed.

After an API error the front end **never** substitutes demo data — mock mode is enabled only by an
explicit build flag. Quietly slipping in fictional numbers where financial decisions are made is
worse than an error message.

### Out of scope

Deliberately not implemented, so that nobody goes looking for it in the code:

| What is missing | Notes |
| --- | --- |
| A UI form for manual positions | The backend has `GET` and `PUT /api/portfolio/manual-holdings`; the dashboard does not show them. Amounts are changed with curl, see [API](#api). |
| Adding and removing manual positions through the API | Deliberate: `PUT` changes the amount only. A new position means an entry in `portfolio.manual.holdings` and a restart. |
| BCH via xpub | The infrastructure is ready: add the `bitcoin-cash` network (`base-url: https://bch1.trezor.io`, `cmc-id: 1831`, `decimals: 8`) and a wallet entry with the `Trezor` label. |
| BTC outside the xpub (e.g. a second wallet) | As above, another entry in `portfolio.blockbook.wallets`. |
| `fxRate` in the response | Contract decision 2 — the rate deliberately does not reach the JSON. |
| Authentication inside the application | Cloudflare Access, see [Deployment](#deployment). |
| Failover between Blockbook servers | An outage is an ordinary source failure: carry-forward and `stale=true`. |

### Testing

- `PortfolioMetricsTest` — rounding and history sampling, without Spring
- `PortfolioQueryServiceTest` — `change`/ATH/history on `InMemoryPortfolioSnapshotRepository`,
  including a test that `summary` agrees with the last history point across every range and currency
- `PortfolioSnapshotServiceTest` — carry-forward, price fallback, missing prices, replacing an hour
- `EvmBalanceClientTest`, `CoinMarketCapPriceProviderTest`, `BlockbookClientTest` —
  `MockRestServiceServer`; in tests use a fake xpub, never a real one
- `HoldingsSourceRegistryTest` — including a label spanning EVM and an xpub
- `PortfolioSnapshotPersistenceAdapterTest` — `@DataJpaTest`, because JPQL and snapshot replacement
  cannot be checked against a fake
- `PortfolioControllerTest` — `@WebMvcTest` with the real service over a repository fake

## Source layout

```
src/main/java/pl/tradeengine/
├── adapter/
│   ├── inbound/        — REST controllers (TradingView webhooks, FVG admin)
│   └── outbound/       — Telegram publisher + JPA persistence adapters
├── application/        — DTOs and orchestration services (WebhookProcessingService, AlertDispatchService)
├── backtest/           — Standalone backtest runner (no Spring), in-memory repos, Pine Script export
├── config/             — Spring config, StrategyProperties (@ConfigurationProperties "app"), ScenarioRegistry
└── domain/
    ├── event/          — DomainEvent hierarchy (FvgCreated/Touched/Filled, DivergenceDetected, SwingPointDetected, PriceCandle)
    ├── model/          — Value objects and enums (FvgZone, AlertToSend, Timeframe, Direction, BiasStatus, …)
    ├── port/           — Repository + AlertPublisher interfaces
    ├── scenario/       — Strategy implementations + ScenarioEngine
    └── util/           — PriceFormatter, PriceCandleUtils
```

The portfolio module lives in a `portfolio/` sub-package of each layer
(`domain/model/portfolio`, `domain/port/portfolio`, `application/service/portfolio`,
`adapter/outbound/persistence/portfolio`) plus `adapter/outbound/evm` and `adapter/outbound/cmc`.
Its hand-run PostgreSQL schema is in `sql/portfolio_schema.sql`; the dashboard it feeds lives in
a separate repository and is deployed next to the jar (see [UI](#ui)).

CSV historical data for backtests lives in `data/` (BTCUSDT, GOLD, USDPLN at H1/H4/D1/W1). Backtest output (Pine Script) goes to `output/`.
