# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Maven wrapper is committed; use it instead of a system `mvn`.

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
u
## Testing

### Stack
- JUnit 5 + Mockito (already on the classpath via `spring-boot-starter-test`)
- AssertJ for fluent assertions
- Use `@ExtendWith(MockitoExtension.class)` for pure unit tests (no Spring context)
- Use `@SpringBootTest` + `@ActiveProfiles("test")` only for integration tests that need the full context

### Test profile
Create `src/test/resources/application-test.yml` with H2 datasource and stub env vars:
```yaml
spring.datasource.url: jdbc:h2:mem:testdb
DB_USER: sa
DB_PASSWORD: ""
TELEGRAM_BOT_TOKEN: test-token
TELEGRAM_CHAT_ID: "0"
```

### Conventions
- Test class name: `<ClassName>Test` in the same package under `src/test/`
- One test class per production class
- Use `InMemory*Repository` adapters (already exist in `backtest/`) as fakes — **do not mock repositories**
- Builder pattern for domain fixtures: create a `TestFixtures` utility class in `src/test/java/pl/tradeengine/`
- For `GrinderStrategyScenario` tests: instantiate directly (like `BacktestApplication` does), don't load Spring

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

### Bias vs. swing points split

In `WebhookProcessingService.handleMomentumAlert`, the set `BIAS_TIMEFRAMES = {D1, W1}` decides whether an incoming momentum alert updates the bias (via `BiasRepository`) or registers a swing point (via `SwingPointRepository`). The same split is mirrored in `BacktestRunner` (`handleWaveTrendCross`). Keep them in sync.

### FVG lifecycle is stateful in the DB

FVG status transitions (`CREATED → TOUCHED → FILLED → CONSUMED`) and the `AlertMode` (ARMED/PAUSED) are driven by `handlePriceUpdate`. Two timing constants live there: `X_OUTSIDE_CANDLES_TO_PAUSE = 4` (pause alerts after price leaves the zone) and `Y_AFTER_FILLED_TO_EXPIRE = 5` (TTL after fill before consumption). The backtest path in `BacktestRunner.updateFvgStates` re-implements similar transitions against `InMemoryFvgRepository` — but its rules differ slightly (e.g., backtest doesn't use AlertMode pausing). When changing FVG state semantics, check both.

### Webhook payloads come from TradingView Pine Script

The DTOs in `application/dto/` are tuned for specific Pine Script payloads, not generic alerts. The `Symbol`, `Timeframe`, `Direction`, `FvgKind` mappers (`fromCode` / `fromSignal` / `fromSignalType`) define the wire contract.

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

CSV historical data for backtests lives in `data/` (BTCUSDT, GOLD, USDPLN at H1/H4/D1/W1). Backtest output (Pine Script) goes to `output/`.
