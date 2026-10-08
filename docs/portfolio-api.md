# Kontrakt API portfela

Spisany na podstawie `src/features/portfolio/types.ts` i obecnego zachowania widoku.
Backend implementuje `/summary` i `/history` zgodnie z tym dokumentem, a **frontend jest
już na nich podłączony**: transport i mapowanie w `src/api/portfolio.ts`, hooki w
`src/features/portfolio/data.ts`, typy odpowiedzi generowane z OpenAPI do
`src/api/schema.d.ts`. Dane testowe z `fixtures.ts` zostają, ale włącza je wyłącznie
jawna flaga — `?state=` albo `VITE_MOCK=1`, nigdy fallback po błędzie.

Sekcja [Ręczne pozycje](#ręczne-pozycje) jest już zaimplementowana po stronie backendu
(`GET` i `PUT /api/portfolio/manual-holdings`); UI jej jeszcze nie pokazuje.

Ustalenia i ich konsekwencje dla kodu zbiera sekcja [Decyzje](#decyzje).

## Konwencje ogólne

| Zasada        | Szczegóły                                                                                                                      |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Czas          | ISO-8601 w **UTC**, z sufiksem `Z`, dokładność do sekund: `2026-10-04T07:00:00Z`. Strefę lokalną dobiera UI.                   |
| Kwoty         | Liczby JSON (nie stringi) **w wybranej walucie**, bez separatorów i symbolu, **zaokrąglone do 2 miejsc**.                      |
| Procenty      | Liczby JSON **w punktach procentowych**, zaokrąglone do **4 miejsc**: `42.1683` znaczy 42,1683%. Dotyczy wszystkich pól `pct`. |
| Ceny          | Jak kwoty od `1.00` w górę, ale **poniżej jedności 6 cyfr znaczących**: `0.0220914`, nie `0.02`.                               |
| Ilości        | `amount` w **pełnej precyzji** — bez zaokrąglania. Zaokrągla dopiero UI przy wyświetlaniu.                                     |
| Waluta        | `USD` albo `PLN`. Backend zwraca wartości już przeliczone — UI nie przelicza kursów.                                           |
| Kodowanie     | `application/json; charset=utf-8`                                                                                              |
| Brak wartości | Jawne `null`. Nie pomijamy kluczy i nie zastępujemy ich zerem — `0` znaczy „zero”, `null` znaczy „nie wiadomo”.                |

Różnice groszowe między sumą `positions[].value` a `total` są **dopuszczalne** —
to skutek zaokrąglania do 2 miejsc. UI nie sumuje pozycji i nie porównuje wyniku z `total`.

Z tego samego powodu **`amount × price` nie musi dać `value`** — cena drogiego aktywa
jest obcięta do dwóch miejsc, a `value` policzone z pełnej. Wartość pozycji bierzemy
zawsze z `value`, nigdy z mnożenia.

### Parametry wspólne

| Parametr   | Wartości     | Wymagany | Domyślnie |
| ---------- | ------------ | -------- | --------- |
| `currency` | `USD`, `PLN` | nie      | `USD`     |

### Dostęp

Uwierzytelnianie jest **poza aplikacją** — backend nie ma własnej warstwy logowania.
Na produkcji **Cloudflare Access** chroni ścieżki `/portfolio*` i `/api/portfolio*`.
Webhooki TradingView muszą zostać otwarte, więc polityka obejmuje wyłącznie te dwa
prefiksy, nigdy całej domeny. Lokalnie API działa bez auth.

Backend **nie wysyła nagłówków CORS** — UI jedzie przez proxy Vite, czyli z tego samego
originu. Dlatego w kodzie frontu nie ma i nie może być absolutnego `http://localhost:8081`.

---

## GET /api/portfolio/summary

Stan portfela z ostatniego snapshotu.

```
GET /api/portfolio/summary?currency=USD
```

### 200 OK

```json
{
  "takenAt": "2026-10-04T07:00:00Z",
  "currency": "USD",
  "total": 47899.88,
  "complete": true,
  "change": {
    "h24": { "abs": 349.55, "pct": 0.7351 },
    "d7": { "abs": -972.53, "pct": -1.9899 },
    "d30": { "abs": 1195.55, "pct": 2.5598 }
  },
  "ath": {
    "value": 52213.54,
    "at": "2026-05-07T05:00:00Z"
  },
  "sources": [
    { "name": "Rabby", "value": 20198.58, "pct": 42.1683 },
    { "name": "Trezor", "value": 15300.06, "pct": 31.9418 },
    { "name": "TAO staking", "value": 10000.8, "pct": 20.8785 },
    { "name": "TRB staking", "value": 2400.44, "pct": 5.0114 }
  ],
  "positions": [
    {
      "source": "Rabby",
      "network": "ethereum",
      "asset": "ETH",
      "amount": 2.41,
      "price": 3735.0,
      "value": 9001.35,
      "pct": 18.792,
      "stale": false
    },
    {
      "source": "Rabby",
      "network": "arbitrum",
      "asset": "ETH",
      "amount": 2.0875,
      "price": 3735.0,
      "value": 7796.81,
      "pct": 16.2773,
      "stale": false
    },
    {
      "source": "Rabby",
      "network": "arbitrum",
      "asset": "USDC",
      "amount": 3400.42,
      "price": 1.0,
      "value": 3400.42,
      "pct": 7.099,
      "stale": false
    },
    {
      "source": "Trezor",
      "network": "bitcoin-cash",
      "asset": "BCH",
      "amount": 21.5,
      "price": 511.63,
      "value": 11000.05,
      "pct": 22.9647,
      "stale": false
    },
    {
      "source": "Trezor",
      "network": "bsc",
      "asset": "BNB",
      "amount": 7.15,
      "price": 601.4,
      "value": 4300.01,
      "pct": 8.9771,
      "stale": false
    },
    {
      "source": "TAO staking",
      "network": "bittensor",
      "asset": "TAO",
      "amount": 27.78,
      "price": 360.0,
      "value": 10000.8,
      "pct": 20.8785,
      "stale": false
    },
    {
      "source": "TRB staking",
      "network": "ethereum",
      "asset": "TRB",
      "amount": 48.25,
      "price": 49.75,
      "value": 2400.44,
      "pct": 5.0114,
      "stale": true
    }
  ]
}
```

### Pola

| Pole                  | Typ                | Jednostka         | `null`? | Opis                                                                                           |
| --------------------- | ------------------ | ----------------- | ------- | ---------------------------------------------------------------------------------------------- |
| `takenAt`             | string             | ISO-8601 UTC      | nie     | Moment wykonania snapshotu. Snapshoty powstają o pełnych godzinach.                            |
| `currency`            | `"USD"` \| `"PLN"` | —                 | nie     | Waluta wszystkich kwot w odpowiedzi. Echo parametru zapytania.                                 |
| `total`               | number             | waluta            | nie     | Łączna wartość portfela. Suma `positions[].value` może różnić się o grosze.                    |
| `complete`            | boolean            | —                 | nie     | `false`, gdy snapshot nie objął wszystkich źródeł. UI pokazuje wtedy znacznik „dane niepełne”. |
| `change.h24`          | object \| null     | —                 | **tak** | Zmiana wobec stanu sprzed 24 h. `null`, gdy historia jest krótsza niż 24 h.                    |
| `change.d7`           | object \| null     | —                 | **tak** | Jw. dla 7 dni.                                                                                 |
| `change.d30`          | object \| null     | —                 | **tak** | Jw. dla 30 dni.                                                                                |
| `change.*.abs`        | number             | waluta            | nie     | Zmiana kwotowa. Ujemna przy spadku.                                                            |
| `change.*.pct`        | number             | punkty procentowe | nie     | Zmiana względna, liczona **w walucie zapytania**. Ujemna przy spadku.                          |
| `ath`                 | object \| null     | —                 | **tak** | Historyczne maksimum **w walucie zapytania**. `null`, gdy brak danych historycznych.           |
| `ath.value`           | number             | waluta            | nie     | Wartość szczytu. Nie powinna być mniejsza niż `total`.                                         |
| `ath.at`              | string             | ISO-8601 UTC      | nie     | Moment szczytu. Dla USD i PLN może wypaść w **różnych dniach**.                                |
| `sources[]`           | array              | —                 | nie     | Podział na źródła. Może być pustą tablicą. Kolejność dowolna — UI sortuje sam.                 |
| `sources[].name`      | string             | —                 | nie     | Nazwa źródła. **Stabilny identyfikator** — UI przypisuje kolor na stałe do nazwy.              |
| `sources[].value`     | number             | waluta            | nie     | Suma wartości pozycji tego źródła.                                                             |
| `sources[].pct`       | number             | punkty procentowe | nie     | Udział w portfelu. Suma po wszystkich źródłach ≈ `100`.                                        |
| `positions[]`         | array              | —                 | nie     | Pozycje. Może być pustą tablicą. Kolejność dowolna — UI sortuje sam. Bez stronicowania.        |
| `positions[].source`  | string             | —                 | nie     | Musi odpowiadać jednemu z `sources[].name`.                                                    |
| `positions[].network` | string             | —                 | nie     | Sieć, np. `ethereum`, `arbitrum`, `bsc`, `bitcoin-cash`. Wyświetlana dosłownie.                |
| `positions[].asset`   | string             | —                 | nie     | Symbol aktywa, np. `ETH`. **Dowolna długość** — UI pokazuje go w całości, bez skracania.       |
| `positions[].amount`  | number             | jednostki aktywa  | nie     | Ilość w pełnej precyzji. UI pokazuje do 4 miejsc po przecinku.                                 |
| `positions[].price`   | number             | waluta            | nie     | Cena jednostkowa.                                                                              |
| `positions[].value`   | number             | waluta            | nie     | `amount × price`.                                                                              |
| `positions[].pct`     | number             | punkty procentowe | nie     | Udział w portfelu.                                                                             |
| `positions[].stale`   | boolean            | —                 | nie     | Pozycja nie została odświeżona w tym snapshocie — patrz [Decyzje, pkt 4](#decyzje).            |

### 204 No Content

Brak jakiegokolwiek snapshotu. Bez ciała odpowiedzi. UI pokazuje wtedy ekran
„Pierwszy snapshot pojawi się o pełnej godzinie”.

### Przykład w PLN

Backend trzyma w każdym snapshocie **oba totale** (`total_usd` i `total_pln`),
więc zmiany i ATH dla PLN liczą się z historii PLN, a nie przez przeliczenie
wyniku z USD.

```json
{
  "takenAt": "2026-10-04T07:00:00Z",
  "currency": "PLN",
  "total": 177229.55,
  "complete": true,
  "change": {
    "h24": { "abs": 1768.83, "pct": 1.0081 },
    "d7": { "abs": -5553.26, "pct": -3.0382 },
    "d30": { "abs": 6291.7, "pct": 3.6807 }
  },
  "ath": { "value": 196139.18, "at": "2026-06-18T13:00:00Z" },
  "sources": [{ "name": "Rabby", "value": 74734.76, "pct": 42.1683 }]
}
```

Co się zmienia między walutami, a co nie:

| Pole                               | Zależy od waluty? | Dlaczego                                                                                                                                                                                       |
| ---------------------------------- | ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `sources[].pct`, `positions[].pct` | **nie**           | Udziały w obrębie jednego snapshotu — ten sam kurs dzieli się przez siebie.                                                                                                                    |
| `change.*.pct`                     | **tak**           | Porównanie dwóch snapshotów. Jeśli kurs USD/PLN się ruszył, procenty się rozjeżdżają: tu +0,7351% w USD wobec +1,0081% w PLN.                                                                  |
| `ath`                              | **tak**           | Szczyt w PLN wypadł 18 czerwca, a w USD 7 maja. `196 139,18 zł` to więcej niż ATH z USD przeliczone bieżącym kursem (`52 213,54 × 3,70 = 193 190,10`) — szczytu nie da się policzyć konwersją. |

---

## GET /api/portfolio/history

Szereg czasowy wartości portfela.

```
GET /api/portfolio/history?range=30D&currency=USD
```

### Parametry

| Parametr   | Wartości                               | Wymagany              |
| ---------- | -------------------------------------- | --------------------- |
| `range`    | `24H`, `7D`, `30D`, `90D`, `1Y`, `ALL` | tak                   |
| `currency` | `USD`, `PLN`                           | nie (domyślnie `USD`) |

### 200 OK

```json
{
  "range": "24H",
  "interval": "1h",
  "currency": "USD",
  "points": [
    { "t": "2026-10-03T08:00:00Z", "value": 47518.4, "complete": true },
    { "t": "2026-10-03T09:00:00Z", "value": 47544.12, "complete": true },
    { "t": "2026-10-03T10:00:00Z", "value": 47501.9, "complete": true },
    { "t": "2026-10-04T06:00:00Z", "value": 47862.31, "complete": true },
    { "t": "2026-10-04T07:00:00Z", "value": 47899.88, "complete": true }
  ]
}
```

### Pola

| Pole                | Typ                                                          | Jednostka    | `null`? | Opis                                                                    |
| ------------------- | ------------------------------------------------------------ | ------------ | ------- | ----------------------------------------------------------------------- |
| `range`             | `"24H"` \| `"7D"` \| `"30D"` \| `"90D"` \| `"1Y"` \| `"ALL"` | —            | nie     | Echo parametru zapytania.                                               |
| `interval`          | `"1h"` \| `"4h"` \| `"1d"`                                   | —            | nie     | Rozdzielczość próbkowania.                                              |
| `currency`          | `"USD"` \| `"PLN"`                                           | —            | nie     | Waluta wartości punktów.                                                |
| `points[]`          | array                                                        | —            | nie     | **Posortowane rosnąco po `t`.** Może być pustą tablicą.                 |
| `points[].t`        | string                                                       | ISO-8601 UTC | nie     | Czas **snapshotu**, z którego pochodzi punkt — nie początek przedziału. |
| `points[].value`    | number                                                       | waluta       | nie     | Wartość portfela w tym snapshocie.                                      |
| `points[].complete` | boolean                                                      | —            | nie     | `false`, gdy ten snapshot nie objął wszystkich źródeł.                  |

### Rozdzielczość

| `range` | `interval` | Przedział | Orientacyjna liczba punktów           |
| ------- | ---------- | --------- | ------------------------------------- |
| `24H`   | `1h`       | 1 godzina | do 24                                 |
| `7D`    | `1h`       | 1 godzina | do 168                                |
| `30D`   | `4h`       | 4 godziny | do 180                                |
| `90D`   | `1d`       | 1 doba    | do 90                                 |
| `1Y`    | `1d`       | 1 doba    | do 365                                |
| `ALL`   | `1d`       | 1 doba    | tyle, ile dni od pierwszego snapshotu |

Zasady próbkowania:

- punktem przedziału jest **ostatni snapshot w tym przedziale**,
- **puste przedziały są pomijane** — nie wypełniamy ich zerami ani interpolacją,
  więc liczba punktów bywa mniejsza niż maksymalna,
- `ALL` liczy się od pierwszego snapshotu w bazie.

Liczba punktów jest **orientacyjna w obie strony**. Przedziały są wyrównane do granic
UTC, więc okno dotyka przedziału brzegowego i przy rozdzielczości `4h` oraz `1d` wynik
bywa o jeden większy od tabelki (`30D` → 181, `90D` → 91). Obciąć go nie można, bo
ostatni punkt musi pozostać tym samym snapshotem co `summary`.

UI nie zakłada stałej liczby punktów ani stałego odstępu między nimi. Zakłada
natomiast **porządek chronologiczny** — na pierwszym i ostatnim punkcie liczy
zmianę w nagłówku dla zakresów `90D`, `1Y` i `ALL`.

### Brak danych

Gdy nie ma żadnego snapshotu w zakresie, zwracamy **200** z pustą tablicą:

```json
{ "range": "30D", "interval": "4h", "currency": "USD", "points": [] }
```

Nie `204` — inaczej niż `summary`. Tablica punktów jest naturalnie „pusta”,
a podsumowania po prostu nie ma.

---

## Ręczne pozycje

Pozycje, których backend nie potrafi odczytać z łańcucha ani z giełdy (staking,
zimny portfel, cokolwiek trzymanego poza zasięgiem integracji). Ilość podaje
człowiek.

**Lista ręcznych pozycji pochodzi z konfiguracji backendu** — `key`, `source`,
`asset`, `network` oraz identyfikator CMC do wyceny. API pozwala zmienić
**wyłącznie ilość**; dodanie i usunięcie pozycji to zmiana konfiguracji.

Sama ilość mieszka w bazie, nie w konfiguracji: wartość z YAML-a jest tylko
**ziarnem**, wpisywanym przy pierwszym starcie. Potem zmienia ją wyłącznie `PUT`,
a edycja YAML-a nie robi już nic. Inaczej `PUT` nie przetrwałby restartu.

Zmiana ilości jest widoczna **w najbliższym snapshocie**, nie natychmiast —
`summary` i `history` zmienią się dopiero po kolejnym pełnym kwadransie zbierania.

Na razie endpointy obsługuje się curlem. Formularz w UI dojdzie później.

### GET /api/portfolio/manual-holdings

```json
[
  {
    "key": "tao-staking",
    "source": "TAO staking",
    "asset": "TAO",
    "network": "bittensor",
    "amount": 27.78,
    "updatedAt": "2026-10-03T19:42:11Z"
  },
  {
    "key": "trb-staking",
    "source": "TRB staking",
    "asset": "TRB",
    "network": "ethereum",
    "amount": 48.25,
    "updatedAt": "2026-09-28T08:15:03Z"
  }
]
```

| Pole        | Typ    | Jednostka        | `null`? | Opis                                                                  |
| ----------- | ------ | ---------------- | ------- | --------------------------------------------------------------------- |
| `key`       | string | —                | nie     | Stabilny identyfikator z konfiguracji. Używany w ścieżce `PUT`.       |
| `source`    | string | —                | nie     | Nazwa źródła — ta sama, która pojawia się w `summary.sources[].name`. |
| `asset`     | string | —                | nie     | Symbol aktywa.                                                        |
| `network`   | string | —                | nie     | Sieć.                                                                 |
| `amount`    | number | jednostki aktywa | nie     | Aktualna ilość, pełna precyzja.                                       |
| `updatedAt` | string | ISO-8601 UTC     | nie     | Kiedy ostatnio zmieniono ilość — nie kiedy ją wyceniono.              |

Odpowiedź nie zawiera kwot ani cen. To rejestr tego, co człowiek wpisał;
wycena dzieje się przy budowaniu snapshotu.

### PUT /api/portfolio/manual-holdings/{key}

```bash
curl -X PUT http://localhost:8081/api/portfolio/manual-holdings/tao-staking \
  -H 'Content-Type: application/json' \
  -d '{"amount": 29.4}'
```

Ciało żądania:

```json
{ "amount": 29.4 }
```

**200 OK** — zaktualizowany obiekt, w tym samym kształcie co element listy:

```json
{
  "key": "tao-staking",
  "source": "TAO staking",
  "asset": "TAO",
  "network": "bittensor",
  "amount": 29.4,
  "updatedAt": "2026-10-04T07:31:52Z"
}
```

| Kod               | Kiedy                                                                |
| ----------------- | -------------------------------------------------------------------- |
| `200 OK`          | Ilość zapisana.                                                      |
| `400 Bad Request` | `amount` ujemne, nieliczbowe, brakujące albo `NaN` / nieskończoność. |
| `404 Not Found`   | `key` nieznany — nie ma takiej pozycji w konfiguracji.               |

`amount: 0` jest **poprawne** i znaczy „nic tu nie trzymam”. Pozycja zostaje
w konfiguracji i wróci, gdy ilość znowu urośnie.

---

## Spójność między endpointami

Przy tej samej walucie i tym samym snapshocie:

- `history.points.at(-1).t` === `summary.takenAt`
- `history.points.at(-1).value` === `summary.total`
- `history.points.at(-1).complete` === `summary.complete`

UI pokazuje łączną wartość w nagłówku i na końcu wykresu jednocześnie — rozjazd
tych liczb byłby widoczny gołym okiem. Dane testowe gwarantują tę równość i jest
na nią test (`fixtures.test.ts`).

## Błędy

| Kod               | Kiedy                                                                                    | Ciało                                  |
| ----------------- | ---------------------------------------------------------------------------------------- | -------------------------------------- |
| `400 Bad Request` | Nieznana wartość `range` albo `currency`, brak wymaganego `range`, niepoprawne `amount`. | Dowolne — UI nie parsuje treści błędu. |
| `404 Not Found`   | Nieznany `key` ręcznej pozycji.                                                          | Jw.                                    |
| `5xx`             | Awaria backendu, niedostępne źródło wyceny, błąd bazy.                                   | Jw.                                    |

UI traktuje każdą odpowiedź inną niż `2xx` tak samo: pokazuje komunikat błędu
z przyciskiem „Spróbuj ponownie”.

> **Nigdy nie podstawiamy danych demo po błędzie API.** Ani backend (pustym
> szkieletem odpowiedzi), ani frontend (fallbackiem na fixtures). Użytkownik
> podejmuje decyzje finansowe — ciche podsunięcie fikcyjnych liczb jest gorsze
> niż komunikat o awarii.

---

## Podział odpowiedzialności

### Liczy backend

| Wartość                                                    | Gdzie w API                             |
| ---------------------------------------------------------- | --------------------------------------- |
| Łączna wartość portfela                                    | `summary.total`                         |
| Udziały źródeł i pozycji                                   | `sources[].pct`, `positions[].pct`      |
| Zmiana 24 h / 7 d / 30 d (kwota i procent)                 | `summary.change.h24` / `.d7` / `.d30`   |
| Historyczne maksimum i jego data, osobno dla każdej waluty | `summary.ath`                           |
| Kompletność snapshotu                                      | `summary.complete`, `points[].complete` |
| Nieaktualność wyceny pozycji                               | `positions[].stale`                     |
| Przeliczenie walutowe                                      | wszystkie kwoty, zależnie od `currency` |
| Agregacja pozycji do źródeł                                | `sources[]`                             |
| Próbkowanie historii do rozdzielczości                     | `points[]`, `interval`                  |

### Liczy UI

| Wartość                                                                              | Gdzie w kodzie                                             |
| ------------------------------------------------------------------------------------ | ---------------------------------------------------------- |
| Zmiana w nagłówku dla `90D`, `1Y`, `ALL` — z pierwszego i ostatniego punktu historii | `resolveHeroChange` w `components/portfolio-dashboard.tsx` |
| Spadek od ATH w procentach (`total / ath.value - 1`)                                 | `components/kpi-tiles.tsx`                                 |
| Łączenie najmniejszych źródeł w „Inne” (top 5 + reszta)                              | `aggregate.ts`                                             |
| Sortowanie źródeł (malejąco) i pozycji (po klikniętej kolumnie)                      | `aggregate.ts`                                             |
| Przypisanie koloru do nazwy źródła                                                   | `palette.ts`                                               |
| Zaokrąglanie osi Y do okrągłych wartości                                             | `chart-scale.ts`                                           |
| Całe formatowanie: `Intl` `pl-PL`, maskowanie w trybie prywatnym                     | `format.ts`, `use-money.ts`                                |

Backend **nie** zwraca niczego dla „Inne” ani gotowych stringów do wyświetlenia.
UI **nie** liczy udziałów, zmian 24 h/7 d/30 d ani ATH.

### Co dziś pochodzi z fixtures, a należy do backendu

Widok czyta te wartości z pól `summary` i nic w komponentach nie zmieniło się po
podłączeniu API — zmieniło się tylko źródło. `fixtures.ts` zostaje jako tryb demo
(`?state=`, `VITE_MOCK=1`) i nadal liczy je po swojemu:

| Wartość                     | Jak powstaje dziś w `fixtures.ts`                                                 |
| --------------------------- | --------------------------------------------------------------------------------- |
| `ath.value`, `ath.at`       | maksimum bazowej serii godzinowej (`buildAth`)                                    |
| `change.h24`, `.d7`, `.d30` | różnica wobec wartości sprzed 24 h / 7 d / 30 d w tej samej serii (`buildChange`) |
| `sources[]` wraz z `pct`    | agregacja pozycji po nazwie źródła (`buildSources`)                               |
| `positions[].pct`           | `value / total` (`buildPositions`)                                                |
| `total`                     | suma `positions[].value`                                                          |
| przeliczenie na PLN         | stały kurs `PLN_RATE = 3.7`                                                       |

---

## Decyzje

Ustalenia zamykające wcześniejsze pytania otwarte.

1. **`pct` zostaje w punktach procentowych.** `42.0` znaczy 42%. Widok formatuje
   przez `Intl.NumberFormat({ style: 'percent' })`, które samo mnoży przez 100,
   więc **mapowanie dzieli wszystkie `pct` przez 100**. Konwersja siedzi w jednym
   miejscu — `toSummary` w `src/api/portfolio.ts`, na granicy API i typów domenowych —
   i jest pilnowana testem. Komponenty widzą wyłącznie frakcje.
2. **Bez `fxRate`.** Kurs nie trafia do odpowiedzi. Skutek uboczny: użytkownik
   patrzący na PLN nie odróżni zmiany wartości portfela od ruchu na USD/PLN.
   Do rozważenia, gdy PLN zacznie być główną walutą widoku.
3. **`complete: false` bez listy źródeł, które zawiodły.** Odpowiedź mówi tylko,
   że snapshot jest niepełny. Które pozycje ucierpiały, UI rozpozna po
   `positions[].stale` — to wystarcza, żeby oznaczyć je w tabeli.
4. **`stale` liczy backend.** `true` znaczy: **pozycja nie została odświeżona
   w tym snapshocie** — ilość przeniesiono z poprzedniego snapshotu, bo źródło
   nie odpowiedziało, albo cena pochodzi z ostatniej znanej wartości.
   Ręczna pozycja **nie jest `stale` tylko dlatego, że jest ręczna**: dopóki jej
   ilość i cena wchodzą do snapshotu normalnie, ma `stale: false`.
5. **ATH osobno dla każdej waluty.** Backend trzyma w każdym snapshocie
   `total_usd` i `total_pln`, a szczyt liczy z odpowiedniego szeregu. Szczyt
   w PLN może wypaść w innym dniu niż w USD i **nie jest** konwersją szczytu
   z USD. Tak samo `change.*.pct` — liczone w walucie zapytania.
6. **`interval` zostaje `1h` / `4h` / `1d`.** Tak jak w `types.ts`; nazewnictwo
   H1 / H4 / D1 zostaje wewnętrzną sprawą backendu i nie wychodzi na JSON.
7. **Precyzja: kwoty do 2 miejsc, `pct` do 4, `amount` w pełnej precyzji.**
   Różnice groszowe między sumą `positions[].value` a `total` są dopuszczalne.
8. **Uwierzytelnianie poza aplikacją** — Cloudflare Access na `/portfolio*`
   i `/api/portfolio*`. Webhooki TradingView zostają otwarte, więc polityka
   nigdy nie obejmuje całej domeny. Lokalnie bez auth.
9. **„X min temu” liczone z zegara przeglądarki.** Backend nie zwraca swojego
   czasu. Ujemny wiek snapshotu (zegar przeglądarki spóźniony względem serwera)
   pokazujemy jako „przed chwilą”, nigdy jako „za 5 min”.
10. **Bez stronicowania `positions[]`.** Portfel mieści się w jednej odpowiedzi.

### Co kontrakt wymusił na UI

Cztery zmiany, wszystkie wdrożone razem z podłączeniem hooków.

| Co                                       | Gdzie                                  | Dlaczego                                                                                                    |
| ---------------------------------------- | -------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `pct` dzielone przez 100 przy mapowaniu  | `api/portfolio.ts`, `toSummary`        | Decyzja 1. Bez tego `42.1683` wyświetliłoby się jako „4216,8%”.                                             |
| `ath` jako `{ value, at } \| null`       | `types.ts`, `components/kpi-tiles.tsx` | Na świeżej bazie `ath` jest `null`. Kafelek mówi wtedy „brak danych”, zamiast wywalać render TypeError-em.  |
| `204` → snapshot bez czasu i bez pozycji | `api/portfolio.ts`, `noSnapshot`       | Brak ciała znaczy brak pomiaru, więc `takenAt` jest `string \| null`, a nagłówek pokazuje „brak snapshotu”. |
| Ujemny wiek snapshotu → „przed chwilą”   | `format.ts`, `formatRelative`          | Decyzja 9. Spóźniony zegar przeglądarki nie może dać „za 5 min”.                                            |

Doszło do tego formatowanie świadome rzędu wielkości (`formatPrice`, `formatAmount`):
cena poniżej jedności dostaje 6 cyfr znaczących, bo dwa miejsca po przecinku zrobiłyby
z `0.0220914` kwotę `0,02`, a ilość poniżej jedności — tyle samo, bo `amount` ma do
18 miejsc i przy czterech ułamek tokena pokazałby się jako `0`.

### Gwarancje kontraktu, na których stoi UI

Backend potwierdził je testami; każda z nich jest założeniem w kodzie frontu.

| Gwarancja                                                               | Co się na niej opiera                                                         |
| ----------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `(source, network, asset)` jest unikalne w obrębie snapshotu            | klucz Reacta w `positions-table.tsx` i `positions-cards.tsx`                  |
| `change` jest **zawsze** obiektem, nullowalne są tylko `h24`/`d7`/`d30` | `summary.change.h24` bez dodatkowego zawężenia na samym `change`              |
| `ath.value` nigdy nie jest mniejsze od `total`                          | spadek od ATH jest opisany jako wartość ≤ 0                                   |
| `points` są posortowane rosnąco po `t`                                  | zmiana w hero dla `90D`, `1Y`, `ALL` liczona z pierwszego i ostatniego punktu |

Nieoczekiwany kształt odpowiedzi **nie** jest tu zakładem: `api/portfolio.ts` sprawdza
go przed mapowaniem i zamienia na zwykły stan błędu. Bez tego literówka w kontrakcie
albo strona HTML z proxy wywaliłaby się dopiero w trakcie renderu, czyli białym
ekranem — aplikacja nie ma error boundary.
