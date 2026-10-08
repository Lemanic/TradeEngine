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
                                                   ilości wpisane ręcznie)
        → domain.port.portfolio.PriceProvider      (CoinMarketCap, USD + PLN)
        → domain.port.portfolio.PortfolioSnapshotRepository  (JPA; one hour, one snapshot)

GET /api/portfolio/summary | /history
    → application.service.portfolio.PortfolioQueryService  →  PortfolioMetrics (rounding, sampling)
```

`config.HoldingsSourceRegistry` builds the sources from configuration: EVM wallet entries are
grouped by label, so `Rabby` on ethereum + arbitrum is one source, while every xpub becomes a
source of its own. **Etykieta decyduje o podziale w `summary.sources[]` i w donucie**, więc dobiera
się ją pod to, co ma być widoczne osobno, a nie pod to, gdzie leżą klucze:

- `Trezor` to adres EVM na bsc **i** xpub Bitcoina — jedna etykieta, jeden kawałek donuta,
  a `HoldingsSource.networks()` zawęża awarię do właściwej połowy.
- `Hyperliquid` to **ten sam adres co `Rabby`**, ale osobna etykieta, bo ten kapitał ma być
  w donucie osobnym kawałkiem. Dlatego korzysta z `PORTFOLIO_RABBY_ADDRESS`, a nazywa się inaczej.

Zmiana etykiety zeruje historię carry-forward dla tego źródła: zapisane pozycje mają starą
nazwę w kolumnie `source`, więc do pierwszego udanego snapshotu nie ma z czego przenosić. Samo
się naprawia po jednym cyklu.

**Dodając źródło, uprzedź repozytorium dashboardu — ale powód jest inny, niż tu wcześniej stało.**
Pierwsza wersja tej notatki twierdziła, że paleta donuta działa tylko dla trzech źródeł i że
czwarte wymaga zmiany wartości kolorów. **Oba zdania są nieprawdziwe** i warto wiedzieć dlaczego,
bo ta sama pomyłka zmyliła najpierw tamtą stronę, a potem mnie.

Wiążące jest **uporządkowanie serii, nie dobór kolorów**. Walidator kontrastu inaczej traktuje
wykresy, w których stykają się tylko sąsiedzi (słupki, linie), a inaczej te, w których zetknąć się
może każda para. Donut wpadał do drugiej kategorii **tylko dlatego, że front sortował wycinki
malejąco po wartości** — przy takim sortowaniu dowolne dwa źródła mogą stać się sąsiadami.
Zmierzone przez nich, w obu motywach:

| Jak używana jest paleta | Ile źródeł przechodzi |
| --- | --- |
| kolejność po wartości (każda para możliwa) | **4**, i tylko dwa zestawy z ośmiu kolorów zdają; przy **5** nie zdaje żaden z 56 podzbiorów |
| kolejność stała (po nazwie, sąsiedztwo niezmienne) | **6** — obecna paleta zdaje wszystkie kontrole |

Czyli przy pięciu źródłach przemalowanie hexów **nie pomoże**: trzeba albo zmniejszyć liczbę serii
(zwinąć w „Inne"), albo rozbić na panele, albo ustalić kolejność wycinków na stałe. To zmiana
w tym, jak czyta się wykres, więc **decyzja użytkownika**, nie nasza ani ich.

Stan na dziś: **pięć źródeł** — `Hyperliquid`, `TAO staking`, `Trezor`, `TRB staking`, `Rabby`.
Przy sortowaniu wycinków po wartości to zakres, w którym **żaden dobór kolorów z udokumentowanej
ósemki nie zdaje kontroli w obu motywach** (56 sprawdzonych podzbiorów). Donut działa i każdy
wycinek ma etykietę, ale próg kontrastu jest przekroczony dla którejś pary sąsiednich.

Rozwiązanie **nie leży po stronie backendu ani palety**: trzeba albo zwinąć najmniejsze źródła
w „Inne", albo rozbić na panele, albo ustalić kolejność wycinków na stałe (wtedy obecna paleta
zdaje dla sześciu). To decyzja użytkownika o tym, jak czyta się jego wykres — czeka na nią.

Szóste źródło dodawaj świadomie: przy stałej kolejności zmieści się bez zmian, przy sortowaniu
po wartości pogłębi problem.

### Degradation rules

Fail softly, but never invent numbers:

| Co się psuje | Zachowanie |
| --- | --- |
| Źródło rzuca wyjątkiem | Ilości przeniesione wstecz (reguła niżej), pozycje `stale=true`, snapshot `complete=false` |
| Brak ceny dla `cmcId` | Ostatnia znana cena z bazy, pozycja `stale=true`, `complete` bez zmian |
| Brak ceny i brak historii | Pozycja pominięta, `complete=false` — zero kłamałoby o wartości portfela |
| Ceny niedostępne (401/429/sieć) | Nic nie zapisane, tylko `ERROR` w logu |
| Brak skonfigurowanego źródła | Nic nie zapisane, `WARN` w logu |

#### Przenoszenie ilości, gdy źródło padnie

Cel: brak dziury w sumie i brak uskoku na wykresie, przy jednoczesnym braku kłamstwa. Serwis
idzie snapshotami **wstecz** i zatrzymuje się na pierwszej rozstrzygającej odpowiedzi:

| Co widzi w snapshocie | Wniosek |
| --- | --- |
| są pozycje tego źródła | przenosi te ilości, wycenia **bieżącymi** cenami, `stale=true` |
| brak pozycji, ale snapshot `complete` | w tym snapshocie **wszystkie** źródła odpowiedziały, więc brak pozycji to samo źródło mówiące „już tego nie mam" — przeniesienie starszej ilości wskrzesiłoby coś sprzedanego, więc nie przenosi nic |
| brak pozycji, snapshot `complete=false` | nierozstrzygające, bo źródło mogło wtedy też padać — szuka dalej wstecz |

Okno przeszukiwania ogranicza `portfolio.carry-forward-max-age` (domyślnie 7 dni). Starsza ilość
nie jest używana: mogła w tym czasie zostać sprzedana, a zła liczba jest gorsza niż uczciwa dziura.

Rozróżnienie „opróżnione" od „nieosiągalne" jest tu **istotą**, a nie detalem. Pierwsza wersja
tego mechanizmu czytała tylko najnowszy snapshot i gubiła pozycję na zawsze, gdy w nim jej
zabrakło; naiwna poprawka „szukaj aż znajdziesz" wskrzeszałaby sprzedane aktywa. Oba zachowania
są zablokowane testami (`doesNotResurrectAPositionThatTheSourceReportedAsEmptied`,
`looksPastAnInconclusiveSnapshotInsteadOfLosingThePosition`).

Zmierzone na żywo przez zepsucie RPC Ethereum: suma spadła z 5 321,60 na 5 321,38 USD, czyli
o 0,004 % wynikające z ruchu cen — bez tego mechanizmu spadłaby o 317,51 USD.

`taken_at` jest zawsze obcięte do pełnej godziny UTC i ma `UNIQUE` — powtórny snapshot w tej samej
godzinie **zastępuje** poprzedni. W adapterze JPA `deleteByTakenAt` + `flush()` przed zapisem jest
konieczne: bez flusha Hibernate wykonałby INSERT przed DELETE i wywalił się na unikalności.

### Configuration

Prefix `portfolio`, klasa `config.PortfolioProperties`. Repo jest **publiczne**, więc adresy i klucz
czytane są wyłącznie z zewnątrz, z pustą wartością domyślną — brak zmiennej to ostrzeżenie w logu,
nigdy błąd startu.

| Zmienna | Rola |
| --- | --- |
| `CMC_API_KEY` | Klucz CoinMarketCap. Bez niego snapshot loguje błąd i nic nie zapisuje. |
| `PORTFOLIO_RABBY_ADDRESS` | Adres EVM dla etykiety `Rabby` (ethereum, arbitrum). |
| `PORTFOLIO_TREZOR_EVM_ADDRESS` | Adres EVM dla etykiety `Trezor` (bsc). |
| `PORTFOLIO_TREZOR_BTC_XPUB` | Rozszerzony klucz publiczny BTC dla `Trezor` (zpub, BIP84). |
| `PORTFOLIO_TREZOR_BCH_XPUB` | Klucz konta BCH (xpub, BIP44 — **inna ścieżka niż BTC**). Dziś nieaktywny, hosty Blockbooka zwracają 403. |
| `PORTFOLIO_TREZOR_BCH_AMOUNT` | Ilość BCH wpisana ręcznie, póki odczyt z xpuba jest niemożliwy. Wyzeruj, włączając xpub. **Czytana tylko przy pierwszym starcie** — potem rządzi baza, patrz [Ilości wpisane ręcznie](#ilości-wpisane-ręcznie). |
| `PORTFOLIO_TAO_STAKING_AMOUNT` | Ilość TAO w stakingu. **Nagrody narastają ciągle — odświeżaj**, ale przez `PUT`, nie przez tę zmienną. |
| `PORTFOLIO_TRB_STAKING_AMOUNT` | Ilość TRB w stakingu. Jak wyżej. |
| `COINBASE_KEY_NAME` | Nazwa klucza CDP: `organizations/{org}/apiKeys/{id}`. Sama w sobie nie jest sekretem. |
| `COINBASE_PRIVATE_KEY` | Prywatny klucz EC (SEC1 PEM, `\n` dopuszczalne). **To jest sekret — klucz tylko do odczytu.** |

Hyperliquid korzysta z `PORTFOLIO_RABBY_ADDRESS` — nie ma własnej zmiennej, choć w podziale na
źródła występuje pod własną etykietą `Hyperliquid`.
| `DB_USER`, `DB_PASSWORD` | Jak w pozostałej części aplikacji. |

`portfolio.carry-forward-max-age` (domyślnie `7d`) ogranicza, jak stare ilości wolno przenieść
po awarii źródła — patrz [Przenoszenie ilości](#przenoszenie-ilości-gdy-źródło-padnie).

Wartości podaje się **poza repo**, w `config/application-dev.yml` (albo `-prod`), który jest
w `.gitignore`. Plik z katalogu `./config/` ma wyższy priorytet niż ten z `src/main/resources`:

```yaml
# config/application-prod.yml — nigdy nie wchodzi do gita
DB_USER: "..."
DB_PASSWORD: "..."
CMC_API_KEY: "..."
PORTFOLIO_RABBY_ADDRESS: "0x..."
PORTFOLIO_TREZOR_EVM_ADDRESS: "0x..."
```

Nie nadpisuj tam `portfolio.evm.wallets` — listy YAML się nie scalają, tylko podmieniają, więc
zniknęłyby pozostałe portfele. Definiuj tylko nazwy, których szukają placeholdery.

`portfolio.enabled` włącza **wyłącznie scheduler** (`config.PortfolioSchedulerConfig` wnosi
`@EnableScheduling`). Ręczny `POST /api/portfolio/snapshots` działa niezależnie od flagi;
w testach flaga jest wyłączona.

Sieci i whitelista tokenów siedzą w `application-{profile}.yml`. Kontrakty i `decimals`
zweryfikowane on-chain przez `symbol()`/`decimals()`, id przez CoinMarketCap:

| Sieć | Aktywa | cmcId |
| --- | --- | --- |
| ethereum | ETH natywne, NPC `0x8eD97a63…08F6`, WOO `0x4691937a…5D4B` (oba 18 decimals) | 1027, 27960, 7501 |
| arbitrum | ETH natywne | 1027 |
| bsc | BNB natywne, ASTER `0x000Ae314…556A` (18 decimals) | 1839, 36341 |
| bitcoin | BTC z xpuba przez Blockbook | 1 |
| hyperliquid | spot, kapitał na perpach i staking; osobne źródło `Hyperliquid` | 3408, 34332, 32196, 1027, 1, 34466 |
| coinbase | salda giełdowe przez CDP, whitelista z `min-amount` na pyłek | 3408, 3773, 13502, 1, 1027 |
| bittensor, ethereum (staking) | `TAO staking`, `TRB staking` — ilości wpisywane ręcznie | 22974, 4944 |

Dodanie tokenu EVM to **jedna linia YAML-a, bez kodu i bez redeploya schematu**. Procedura,
której trzymaj się za każdym razem — adresu, `decimals` ani id nie zgaduj:

1. Znajdź adres kontraktu w dwóch niezależnych źródłach (eksplorator + lista platform w CMC).
2. Potwierdź on-chain na docelowym RPC: `symbol()` = `0x95d89b41`, `decimals()` = `0x313ce567`.
3. Sprawdź `balanceOf` (`0x70a08231` + adres dopełniony do 32 bajtów) na swoim adresie — jeśli
   wychodzi zero, to albo nie ten kontrakt, albo nie ta sieć.
4. Weź `cmc-id` z CoinMarketCapa i dopisz wpis do `tokens` danej sieci.

Przykład przejścia tej procedury dla ASTER-a na bsc: `symbol()` zwróciło `ASTER`,
`decimals()` → 18, `balanceOf` → 354,56577328, CMC id 36341, platforma w CMC zgodna z BscScanem.

#### Ilości wpisane ręcznie

`portfolio.manual.holdings` to wpisy, których nie da się odczytać automatycznie. **Z konfiguracji
pochodzi wyłącznie ilość** — wycena idzie normalną ścieżką przez CoinMarketCap, więc wartość
i przeliczenie na PLN są spójne z resztą. Pozycja **nie** jest `stale` tylko dlatego, że jest
ręczna (tak stanowi kontrakt), a źródło nie ma czego wywołać, więc nigdy nie zawodzi i nie psuje
`complete`.

Ilość jest daną o majątku, więc w śledzonym YAML-u stoi placeholder. Zero znaczy „nic tu teraz nie
trzymam" — wpis zostaje zadeklarowany i wraca, gdy ilość urośnie. `source` decyduje, do którego
kawałka portfela pozycja dołącza; `key` to stabilny identyfikator, pod którym adresuje ją
`PUT /api/portfolio/manual-holdings/{key}`.

**Konfiguracja jest rejestrem tego, JAKIE pozycje istnieją; aktualną ILOŚĆ trzyma baza**
(tabela `manual_holding`). Wartość z YAML-a to tylko ziarno: `PortfolioConfig` wstawia ją przy
pierwszym starcie (`@Bean(initMethod = "seedMissing")`) i od tej chwili zmienia ją wyłącznie `PUT`
— **edycja YAML-a nie robi już nic**, co jest zaskakujące i warte zapamiętania. Bez tego podziału
`PUT` nie przetrwałby restartu, a kontraktowe `updatedAt` nie miałoby skąd się wziąć.

Zasianie siedzi w `initMethod`, nie w `ApplicationRunner`, celowo: runner biegnie **po** otwarciu
portu, więc pierwsze zapytanie zdążyło zobaczyć `updatedAt: null`, czego kontrakt nie dopuszcza.

`ManualHoldingsSource` nie dostaje ilości w konstruktorze, tylko funkcję odczytu
(`ManualHoldingService::amountFor`), wołaną przy każdym snapshocie. Dlatego zmiana ilości działa
bez restartu i bez przebudowy `HoldingsSourceRegistry` — ale też dlatego widać ją **dopiero
w następnym snapshocie**, nie natychmiast w `summary`.

Dziś tak wchodzą trzy pozycje: **BCH** (tymczasowo, bo hosty Blockbooka oddają 403) oraz
**staking TAO i TRB**, których nie czyta żadne nasze API.

**Jedyna realna słabość: liczba jest prawdziwa tylko do momentu, w którym stan się zmieni.** Przy
BCH to znaczy „dopóki nie ruszysz monet". Przy stakingu jest gorzej — **nagrody narastają ciągle**,
więc wpis starzeje się sam, zawsze zaniżając portfel, i nic tego nie sygnalizuje: ani `stale`, ani
`complete=false`. Odczyt z łańcucha lub API jest zawsze lepszy tam, gdzie jest możliwy.

**Uwaga na podwójne liczenie.** Pozycje agregują się po `(source, network, asset, cmcId)`, więc
ręczny wpis BCH i źródło xpub pod tą samą etykietą zsumowałyby się po cichu.
`HoldingsSourceRegistry` ostrzega o tym przy starcie (`TWO SOURCES COVER '…'`), ale ostrzeżenie nie
zastępuje decyzji: włączając odczyt z xpuba, wyzeruj `PORTFOLIO_TREZOR_BCH_AMOUNT`.

#### Coinbase

`portfolio.coinbase` czyta salda przez Advanced Trade API (`GET /api/v3/brokerage/accounts`).
Uwierzytelnienie to **klucz CDP**: nazwa (`organizations/{org}/apiKeys/{id}`) plus prywatny klucz
EC w PEM — dwie osobne wartości, obie z zewnątrz. Klucz powinien mieć uprawnienia **tylko do
odczytu**; portfel nie potrzebuje niczego więcej.

Token buduje `CoinbaseJwt` per zapytanie i trzy rzeczy łatwo w nim zrobić źle, bo każda kończy się
nagim `401` bez wskazówki:

- **algorytm musi być ES256** (ECDSA P-256); klucze Ed25519 nie są akceptowane;
- **`uri` to `{METHOD} {HOST}{PATH}`** — host bez schematu, ścieżka bez query stringa — a token
  wiąże się z jednym takim zapytaniem i żyje 120 sekund;
- **JCA podpisuje w DER, a JWS wymaga surowego `R||S`** po 32 bajty. Bez tej konwersji token jest
  poprawnie zbudowany i nigdy się nie weryfikuje, a błąd w dopełnianiu ujawnia się **losowo** —
  tylko gdy `r` albo `s` zaczyna się od bajtu ≥ 0x80. Dlatego konwersja ma test round-trip na obu
  skrajach, a nie przykładowy wektor.

Coinbase daje klucz jako SEC1 PEM (`BEGIN EC PRIVATE KEY`), którego `KeyFactory` nie czyta — chce
PKCS#8. Zamiast dodawać bibliotekę, 32-bajtowy skalar jest wyciągany z DER-a w `CoinbaseJwt`.

**Whitelista walut jest tu ważniejsza niż na łańcuchu.** Konto giełdowe zbiera pyłek z opłat
i konwersji: w tym portfelu leżą `7E-7` BTC i `3.6E-9` ETH, razem kilka centów. Dlatego wpis
whitelisty ma opcjonalne `min-amount`, które odcina pyłek bez wyrzucania waluty z konfiguracji.

#### Hyperliquid

`portfolio.hyperliquid` czyta kapitał z publicznego API `POST /info` (bez klucza, adres w ciele).
Trzy osobne zapytania, bo żadne nie pokrywa całości: `clearinghouseState` (kapitał na perpach),
`spotClearinghouseState` (salda spot), `delegatorSummary` (staking). Adres to ten sam
`PORTFOLIO_RABBY_ADDRESS` — **żadnej nowej zmiennej środowiskowej**.

Wszystkie trzy kubełki lądują na **jednej sieci** `hyperliquid`, rozdzielone po assecie. Tego
`network` nie rozbijaj na trzy: decyzja użytkownika, a klucz agregacji to
`(source, network, asset, cmcId)`, więc podział po sieciach nic nie wnosi, gdy assety i tak są różne.

Trzy rzeczy, których nie widać z kodu:

- **Tokeny owinięte przez Unit wyceniamy tym, czym są**, a Hyperliquid sam to podaje w
  `fullName`: `UETH` to „Unit Ethereum" (ETH, 1027), `UBTC` to „Unit Bitcoin" (BTC, 1),
  `HPENGU` to „Pudgy Penguins" (PENGU, 34466). Dodając token na whitelistę, sprawdź `fullName`
  w `spotMetaAndAssetCtxs` — nie zgaduj po tickerze.
- **`accountValue` to nie saldo tokena**, a kapitał własny w USD po niezrealizowanym PnL. Jest
  raportowany jako ilość assetu `USD` wycenianego ceną USDC (~1), więc wychodzi osobnym wierszem
  wartym dokładnie tyle dolarów, zamiast zlać się ze spotowym USDC. Ta pozycja jest najbardziej
  zmienna w portfelu — konto jest na dźwigni (zaobserwowane 40× na BTC) i przy likwidacji może
  pójść do zera.
- **Whitelista spot jest obowiązkowa.** Hyperliquid zwraca salda także dla tokenów nigdy nie
  tykanych (zerowe UBTC, USDE, USDT0, USDH). Zera są pomijane, a wszystko poza whitelistą
  ignorowane — więc po nowym zakupie spot trzeba dopisać token, inaczej po cichu zniknie
  z portfela.

Czego świadomie nie czytamy: depozytów w vaultach (`userVaultEquities`) i subkont
(`subAccounts`) — sprawdzone dla tego portfela, zwracają `[]` i `null`. Gdy się pojawią, będzie
trzeba dołożyć czwarty kubełek.

Pułapka przy pracy z API, która kosztowała mnie błędną wycenę: w `spotMetaAndAssetCtxs`
**`universe` i `ctxs` nie są wyrównane pozycyjnie** (330 vs 1003 wpisów). Łącz po `ctx.coin`
równym `universe[].name`, nigdy przez `zip`. Nasz adapter tego endpointu nie używa — ceny bierze
CoinMarketCap — ale notuję, bo to naturalny odruch przy diagnostyce.

#### Łańcuchy UTXO (xpub przez Blockbook)

`portfolio.blockbook` czyta salda z rozszerzonego klucza publicznego przez publiczny Blockbook
Trezora (`GET /api/v2/xpub/{xpub}?details=basic`, saldo w satoshi). Blockbook sam wyprowadza
adresy i stosuje gap limit, więc `zpub` (BIP84) wystarcza — po stronie aplikacji nie ma żadnej
derywacji kluczy. Dodanie BCH to wpis sieci `bitcoin-cash` z `base-url: https://bch1.trezor.io`,
`cmc-id: 1831`, `decimals: 8` plus wpis portfela z tą samą etykietą `Trezor`.

Dwie rzeczy, których nie widać z kodu:

- **`User-Agent` jest obowiązkowy.** Bez nagłówka serwer Trezora oddaje `403` ze stroną
  Cloudflare, nie JSON-a. Dlatego `portfolio.blockbook.user-agent` jest konfigurowalny.
- **Xpub jedzie w ścieżce URL-a**, a komunikaty `RestClient` cytują URL. `BlockbookClient`
  dlatego nigdy nie przepuszcza wiadomości wyjątku źródłowego — składa własną, z nazwą sieci
  i kodem HTTP. Xpub ujawnia całe saldo i historię portfela, więc nie może trafić do logu.
  Jest na to test (`hasMessageNotContaining`).

Pojedynczy host to pojedynczy punkt awarii (`btc2.trezor.io` odpowiadał `503` w trakcie
implementacji), ale nie ma tu failoveru świadomie: awaria Blockbooka to zwykła awaria źródła,
więc ilość BTC przenosi się z poprzedniego snapshotu i pozycja dostaje `stale=true`.

**BCH jest skonfigurowany, ale uśpiony i obecnie niedostępny.** Dwie niezależne przeszkody:

- Brakuje `PORTFOLIO_TREZOR_BCH_XPUB`. BCH ma inną ścieżkę derywacji niż BTC, więc to **osobny
  klucz z Trezor Suite** — nie ten sam `zpub`. Bez zmiennej wpis jest pomijany z ostrzeżeniem
  `Wallet 'Trezor' has no xpub configured`, czyli nic się nie psuje.
- Stan na 2026-10-05: **wszystkie hosty `bch1`–`bch5.trezor.io` oddają `403`** ze stroną
  Cloudflare „Sorry, you have been blocked", na każdej ścieżce i niezależnie od `User-Agent`
  (sprawdzone też z przeglądarkowym). Kontrola na `btc1.trezor.io/api/v2` w tym samym momencie:
  `200`. Czyli to reguła firewalla po stronie Trezora dla hostów BCH, nie problem z naszym
  zapytaniem i nie coś, co należy obchodzić.

Dopóki blokada trwa, ustawienie xpuba sprawi tylko, że źródło będzie zgłaszać awarię — czyli
carry-forward i `stale=true`, a snapshot `complete=false`. Opcje, gdy BCH będzie potrzebny:
poczekać (host wcześniej działał), wskazać `base-url` na inną instancję Blockbooka (ale xpub
ujawnia cały portfel, więc to decyzja o zaufaniu), albo postawić własną instancję.

### Database

Prod chodzi z `ddl-auto: validate`, więc tabele muszą istnieć **przed** startem:

```bash
psql -h localhost -U <user> -d postgres -f sql/portfolio_schema.sql
```

Skrypt zakłada **trzy** tabele: `portfolio_snapshot`, `portfolio_position` i `manual_holding`.
Jest zgodny z encjami co do kolumn, typów i skal (sprawdzone eksportem DDL Hibernate'a
z dialektem Postgresa). Dev (H2, `create`) buduje je sam z encji.

`manual_holding` doszła później niż pozostałe dwie, więc na maszynie, która wstała przed nią,
`validate` wywali start — trzeba puścić skrypt ponownie (`CREATE TABLE` dla istniejących tabel
zgłosi błąd, wtedy wykonaj samo `CREATE TABLE manual_holding` z końca pliku).

`portfolio_snapshot` trzyma **oba** totale (`total_usd`, `total_pln`), bo historia, zmiany i ATH
liczą się osobno dla każdej waluty — szczyt w PLN może wypaść innego dnia niż w USD i nie da się
go policzyć konwersją.

### API

| Endpoint | Uwagi |
| --- | --- |
| `GET /api/portfolio/summary?currency=USD\|PLN` | `204`, gdy nie ma żadnego snapshotu. `400` przy nieznanej walucie. |
| `GET /api/portfolio/history?range=24H\|7D\|30D\|90D\|1Y\|ALL&currency=…` | `200` z pustą tablicą, gdy nie ma danych — nie `204`. `400` przy braku/nieznanym `range`. |
| `POST /api/portfolio/snapshots` | Operatorski, poza kontraktem UI: zwraca snapshot jak zapisany, w obu walutach i z pełną precyzją. `503`, gdy nic nie zapisano. |
| `GET /api/portfolio/manual-holdings` | Rejestr ręcznych pozycji: bez kwot i bez cen. Kolejność jak w konfiguracji. |
| `PUT /api/portfolio/manual-holdings/{key}` | Zmienia **wyłącznie ilość**. `400` → `missing_amount`, `invalid_amount` (ujemna), `invalid_body` (tekst, `NaN`). `404` → `unknown_key`, z listą dostępnych kluczy w `message`. |

Dolna granica okna historii jest **wyłączna**, górna włączna — inaczej 24-godzinne okno
mieściłoby 25 punktów godzinowych zamiast udokumentowanych 24. Przy rozdzielczości 4h i 1d
liczba punktów nadal może przekroczyć tabelkową o jeden (`30D` → 181), bo przedziały są
wyrównane do granic UTC i okno dotyka brzegowego przedziału. Nie da się tego obciąć: ostatni
punkt musi pozostać tym samym snapshotem co `summary`. Kontrakt nazywa te liczby orientacyjnymi,
więc to zgodne ze spec — UI nie może zakładać stałej liczby punktów.

Oba endpointy odczytowe są zakotwiczone w **najnowszym zapisanym snapshocie**, nie w zegarze.
Dzięki temu ostatni punkt historii to zawsze ten sam snapshot, który opisuje `summary` — wymóg
z sekcji „Spójność między endpointami" — nawet gdy zbieranie stanęło wiele godzin temu.

Precyzja: kwoty 2 miejsca, `pct` 4 i w punktach procentowych, `amount` pełna precyzja.
**Wyjątek: `price`** — 2 miejsca powyżej 1.00, a poniżej 6 cyfr znaczących. NPC kosztuje ~$0.022,
więc dwa miejsca zrobiłyby z niego $0.02 i `value` przestałoby się zgadzać z `amount × price`;
UI-owy `price()` i tak renderuje 4 miejsca poniżej dolara.

Dwa niezmienniki, na których opiera się dashboard, zablokowane testami: `sources` jest puste
**wtedy i tylko wtedy**, gdy puste jest `positions` (każda pozycja wnosi swoje źródło — UI
rozpoznaje po tym pusty portfel), a `pct` przy `total = 0` wychodzi `0`, nigdy `null` ani `NaN`.

`amount` z 18 miejscami po przecinku **nie przechodzi przez `double` bez straty** —
`6390.281180127170627929` wczytuje się w JavaScripcie jako `6390.28118012717`. Do wyświetlania
to bez znaczenia, ale gdyby kiedyś trzeba było pokazać dokładną ilość, liczba w JSON-ie tego nie
uniesie i dopiero wtedy warto rozważyć stringa — kontrakt dziś wymaga liczb.

`server.error.include-stacktrace: never` w `application.yml` jest **celowe**: devtools podkręca tę
wartość do `ALWAYS`, więc bez tego ciało `404` wyciekało stack trace. W paczce produkcyjnej
devtools nie ma, ale bezpieczeństwo odpowiedzi nie powinno zależeć od classpathu.

Schemat: `/v3/api-docs` (OpenAPI 3.1, zawsze włączony), Swagger UI tylko w dev pod
`/swagger-ui.html`. `OpenApiConfig` zawiera customizer, który przepisuje nullowalne referencje na
`anyOf: [ref, null]` — bez tego springdoc emituje `{"type":"null","$ref":…}` i generator
TypeScriptu produkuje `ath: null` zamiast `Ath | null`.

Bez CORS: dashboard jedzie z tego samego originu. Uwierzytelnianie jest poza aplikacją
(Cloudflare Access); webhooki TradingView muszą zostać otwarte, więc polityka nigdy nie obejmuje
całej domeny.

### Deployment

Dwie subdomeny wskazują na **tę samą aplikację**, więc polityka dostępu musi to uwzględniać:

| Host | Co obsługuje | Dostęp |
| --- | --- | --- |
| `webhook.tradeengine.app` | wyłącznie `POST /webhooks/*` | otwarty — TradingView nie umie się uwierzytelnić |
| `portfolio.tradeengine.app` | dashboard w korzeniu i `/api/portfolio/*` | Cloudflare Access na całym hoście |

**Port: prod chodzi na 8080**, bo `application-prod.yml` nie ustawia `server.port` i Spring bierze
domyślny. Tylko dev ma jawne `8081`. Polecenia weryfikacyjne na maszynie celuj więc
w `http://localhost:8080`.

**Host webhookowy proxuje do originu wszystkie ścieżki, nie tylko `/webhooks/*`.** Bez reguły
brzegowej `https://webhook.tradeengine.app/api/portfolio/summary` oddaje całe portfolio publicznie.
Dlatego w Cloudflare WAF stoi reguła *default-deny* dla tego hosta:

```
(http.host eq "webhook.tradeengine.app" and not starts_with(http.request.uri.path, "/webhooks/"))
```
→ Block. Zamyka też `/portfolio*`, `/v3/api-docs`, `/api/signals/*` i cokolwiek dojdzie później.
Access na drugim hoście tego nie załatwia — polityki działają per host.

Przed pierwszym startem na prodzie: reguła WAF, potem `sql/portfolio_schema.sql` (**trzy** tabele
— jeśli baza wstała przed dodaniem `manual_holding`, puść samo `CREATE TABLE manual_holding`), potem
`config/application-prod.yml` **w katalogu roboczym procesu** (`./config/` jest rozwiązywane
względem cwd, nie względem jara), potem build dashboardu w katalogu wskazanym przez
`PORTFOLIO_UI_DIR`.

**Maszyna produkcyjna chodzi na Windows**, co unieważnia kilka odruchów: nie ma `sudo`, peer auth
ani `systemd`, więc do Postgresa wchodzi się hasłem (`psql.exe` z `C:\Program Files\PostgreSQL\{wersja}\bin`,
zwykle poza `PATH`), a katalog roboczy ustawia bat-ka, „Start in" w Task Schedulerze albo
`AppDirectory` w NSSM — nie `WorkingDirectory` z unitu.

Ścieżka do dashboardu **musi** wtedy przyjść z `PORTFOLIO_UI_DIR` (np.
`file:C:/tradeengine/portfolio/`). Domyślne `file:/opt/tradeengine/portfolio/` rozwinie się na
Windows do nieistniejącego `\opt\tradeengine\portfolio\`: start przejdzie, API będzie działać,
a `GET /` odda 404 — awaria cicha, więc sprawdzaj ją jawnie po deployu.

Konsekwencja Access na całym hoście: po wygaśnięciu sesji Cloudflare odpowiada na `fetch`
przekierowaniem na stronę logowania, czyli HTML-em ze statusem 200. Front rozpoznaje to po
`redirect: 'manual'` i `response.type === 'opaqueredirect'`, co jest jednoznaczne, bo **API nigdy
nie odpowiada 3xx** — sprawdzone dla wszystkich ścieżek, również dla ukośnika końcowego. Dodanie
przekierowania pod `/api` złamałoby to rozpoznanie.

### UI

Backend **nie wozi własnego UI**. Dashboard to osobne repo (`tradeengine-dashboard`, Vite +
React), budowane z `base: '/'` i wgrywane do katalogu na maszynie produkcyjnej. Spring serwuje
ten katalog spoza jara:

```yaml
spring.web.resources.static-locations: ${PORTFOLIO_UI_DIR:file:/opt/tradeengine/portfolio/},classpath:/static/
```

`PORTFOLIO_UI_DIR` podaje się w `config/application-prod.yml`, bo ścieżka zależy od systemu
maszyny — patrz [Deployment](#deployment).

Wydanie frontu nie wymusza wtedy redeploya backendu, a origin pozostaje jeden — bez CORS-u i bez
drugiej polityki Access. `static-locations` **nadpisuje** domyślne, więc `classpath:/static/`
musi zostać wpisane jawnie. `index.html` ląduje w korzeniu (`GET /`), bundle pod `/assets/`,
a nagłówki cache rozdziela `config.StaticResourceConfig` (powód w jego javadocu).
**Nie dodawaj mapowania na `/`** — zasłoniłoby welcome page i `/` oddawałoby redirect zamiast
dashboardu; dokładnie tak było, gdy istniał `PortfolioPageController`.

**Brak fallbacku SPA**: dashboard to jeden widok bez routera, więc `/**` nie forwarduje na
`index.html`. Gdy router dojdzie, trzeba to dodać — objawi się jako 404 przy odświeżeniu
podstrony.

Po błędzie API front **nigdy** nie podstawia danych demo — tryb mock włącza wyłącznie jawna
flaga budowania. Ciche podsunięcie fikcyjnych liczb przy decyzjach finansowych jest gorsze niż
komunikat o awarii.

### Poza zakresem

Świadomie niezaimplementowane, żeby nikt nie szukał tego w kodzie:

| Czego nie ma | Uwagi |
| --- | --- |
| Formularz ręcznych pozycji w UI | Backend ma `GET` i `PUT /api/portfolio/manual-holdings`; dashboard ich nie pokazuje. Ilości zmienia się curlem, patrz [API](#api). |
| Dodawanie i usuwanie ręcznych pozycji przez API | Świadomie: `PUT` zmienia tylko ilość. Nowa pozycja to wpis w `portfolio.manual.holdings` i restart. |
| BCH przez xpub | Infrastruktura gotowa: dodaj sieć `bitcoin-cash` (`base-url: https://bch1.trezor.io`, `cmc-id: 1831`, `decimals: 8`) i wpis portfela z etykietą `Trezor`. |
| BTC poza xpubem (np. drugi portfel) | Jak wyżej, kolejny wpis w `portfolio.blockbook.wallets`. |
| `fxRate` w odpowiedzi | Decyzja 2 kontraktu — kurs świadomie nie wychodzi na JSON. |
| Uwierzytelnianie w aplikacji | Cloudflare Access, patrz [Deployment](#deployment). |
| Failover między serwerami Blockbooka | Awaria to zwykła awaria źródła: carry-forward i `stale=true`. |

### Testing

- `PortfolioMetricsTest` — zaokrąglenia i próbkowanie historii, bez Springa
- `PortfolioQueryServiceTest` — `change`/ATH/historia na `InMemoryPortfolioSnapshotRepository`,
  w tym test spójności `summary` z ostatnim punktem historii po wszystkich zakresach i walutach
- `PortfolioSnapshotServiceTest` — carry-forward, fallback ceny, brak cen, zastąpienie godziny
- `EvmBalanceClientTest`, `CoinMarketCapPriceProviderTest`, `BlockbookClientTest` —
  `MockRestServiceServer`; w testach używaj sztucznego xpuba, nigdy prawdziwego
- `HoldingsSourceRegistryTest` — m.in. etykieta rozpięta między EVM i xpubem
- `PortfolioSnapshotPersistenceAdapterTest` — `@DataJpaTest`, bo JPQL i zastępowanie snapshotu
  nie dają się sprawdzić na fake'u
- `PortfolioControllerTest` — `@WebMvcTest` z prawdziwym serwisem nad fake'em repozytorium

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
