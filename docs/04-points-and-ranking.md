# 04 — System punktów i ranking

## 4.1 Filozofia

Drużyny są **losowane co mecz**, więc czysty win/loss byłby niesprawiedliwy w krótkiej próbie
(dostaniesz słaby skład i przegrasz mimo dobrej gry). Dlatego budujemy **system hybrydowy** z trzech
warstw, z których każda odpowiada na inne pytanie:

| Warstwa | Pytanie | Widoczność | Użycie |
|---------|---------|-----------|--------|
| **Performance Rating (PR)** 0–100 | „Jak dobrze zagrałeś w tym meczu?" | tak, per mecz | wkład do LP, MVP |
| **Punkty Ligowe (LP)** | „Jak wypadasz w sezonie?" | tak, ranking sezonu | tabela ligowa, tytuł sezonu |
| **MMR (Elo)** | „Jak mocny jesteś naprawdę?" | opcjonalnie | balansowanie losowania drużyn |

Wszystkie wagi/bonusy siedzą w `ScoringConfig` (opcjonalny pełny JSON per sezon), więc system stroimy bez
zmian w kodzie. Poniższe liczby to **rozsądny domyślny zestaw** do dostrojenia po pierwszych meczach.

---

## 4.2 Performance Rating (PR) — ocena meczu 0–100

PR v2 ocenia grę indywidualną względem **historycznych występów na tej samej roli**. Nie dostaje
bezpośredniego bonusu za zwycięstwo. Wagi zależą od roli i zachowują wysokie znaczenie KDA.
Historia jest krocząca: maksymalnie 60 próbek roli, czyli 30 wcześniejszych meczów.

### Krok 1 — metryki bazowe (per uczestnik)
Z surowych statystyk i czasu gry `min = durationSeconds / 60`:

| Metryka | Wzór |
|---------|------|
| `KDA` | `(K + A) / max(1, D)` |
| `KP` (kill participation) | `(K + A) / max(1, teamKills)` |
| `CSpm` | `CS / min` |
| `DMGpm` | `damageToChampions / min` |
| `EFF` | `damageToChampions / max(1, gold)` |
| `VSpm` | `visionScore / min` |

`teamKills` to suma zabójstw drużyny. Efektywność zastępuje gold share: system premiuje wykorzystanie
zasobów, a nie samo przejęcie dużej części złota.

### Krok 2 — historyczny percentyl roli
Każda metryka otrzymuje wynik 0–1 jako percentyl wśród wcześniejszych występów na tej samej roli.
Wartość `0.50` oznacza medianę roli, `0.80` wynik lepszy od 80% próbek.

### Krok 3 — bezpieczny start sezonu
Dopóki rola nie ma 20 próbek, percentyl historyczny jest płynnie łączony z bezpośrednim porównaniem
dwóch graczy tej roli w bieżącym meczu:

`norm = historyWeight × percentileHistory + (1-historyWeight) × comparisonInMatch`,
gdzie `historyWeight = min(1, liczbaPróbek / 20)`.

### Krok 4 — wagi ról
Profile wag (sumują się do 1.0 w każdej roli):

| Metryka | TOP | JUNGLE | MID | ADC | SUPPORT |
|---------|----:|-------:|----:|----:|--------:|
| KDA | 0.25 | 0.20 | 0.20 | 0.20 | 0.25 |
| KP | 0.15 | 0.25 | 0.20 | 0.15 | 0.30 |
| CSpm | 0.15 | 0.10 | 0.15 | 0.20 | 0.00 |
| DMGpm | 0.25 | 0.20 | 0.30 | 0.35 | 0.15 |
| EFF (damage/gold) | 0.10 | 0.10 | 0.05 | 0.05 | 0.05 |
| VSpm | 0.10 | 0.15 | 0.10 | 0.05 | 0.25 |

> DMGpm mierzy presję, a EFF wykorzystanie otrzymanego złota. Wagi KDA z v1 pozostały bez zmian.
> Wszystkie wagi są konfigurowalne w `ScoringConfig`.

### Krok 5 — złożenie
```
PR = 100 * Σ_metryka ( waga_roli(metryka) * norm(metryka) )
```
Wynik 0–100. Około 50 oznacza typowy występ dla roli, 65–74 dobry, 75+ wyróżniający.

---

## 4.3 Punkty Ligowe (LP) — ranking sezonu

To, co widać w tabeli i wyłania mistrza sezonu. Naliczane **przy akceptacji meczu**.

```
LP(gracz) = LP_bazowe(wynik) + próg_występu(PR) + nagroda_indywidualna
```

| Składnik | Wartość |
|----------|---------|
| **LP_bazowe** | wygrana: **+10**, przegrana: **+4** |
| **PR <35** | −2 |
| **PR 35–44** | −1 |
| **PR 45–54** | 0 |
| **PR 55–64** | +1 |
| **PR 65–74** | +2 |
| **PR ≥75** | +3 |
| **MVP meczu** | +3 (najwyższy PR w meczu) |
| **ACE przegranych** | +2 (najwyższy PR przegranych, wymagane PR ≥60) |

MVP i ACE mogą być współdzielone przy remisie PR. Jeśli przegrany jest jednocześnie MVP i ACE,
widzi oba tytuły, ale dostaje tylko bonus MVP. Penta, quadra i flawless pozostają osiągnięciami bez LP.

### Tabela sezonu
Suma LP jest licznikiem aktywności. Kolejność wyznacza skorygowana średnia:

`rankingScore = (totalLp + 5 × leagueAveragePoints) / (games + 5)`.

Pięć wirtualnych meczów na poziomie średniej ligi stabilizuje małą próbkę. Do pełnej klasyfikacji
potrzeba 5 rozegranych meczów; wcześniej wynik jest oznaczony jako prowizoryczny i sortowany niżej.

---

## 4.4 MMR (Elo) — do balansowania losowania

Ukryty (lub półjawny) rating siły gracza, aktualizowany po każdym zaakceptowanym meczu. Służy
**wyłącznie** do wyrównanego losowania drużyn (tryb `BALANCED`), nie do rankingu ligowego.

- Start: **1000** dla nowego gracza.
- Oczekiwany wynik drużyny (średnie MMR):
  ```
  E_BLUE = 1 / (1 + 10^((mmrAvg_RED − mmrAvg_BLUE) / 400))
  ```
- Wynik `S`: 1 dla zwycięskiej strony, 0 dla przegranej.
- Zmiana dla gracza:
  ```
  ΔMMR_i = K * (S_teamGracza − E_teamGracza)
  ```
  gdzie `K` = 32, a dla nowicjuszy z mniej niż 10 gier `K` = 48.

PR nie moduluje MMR: dobry gracz przegranej drużyny nie traci więcej punktów niż słaby. Zmiana zapisywana w
`MatchParticipant.mmrDelta` i agregowana do `PlayerSeasonStats.mmr`. MMR i LP są obecnie
prowadzone w ramach sezonu.

---

## 4.5 Przeliczanie i spójność

- **Naliczanie przyrostowe:** po `MatchApprovedEvent` liczymy PR/LP/ΔMMR dla 10 uczestników i
  aktualizujemy `PlayerSeasonStats` (upsert). Szybkie, bo dotyczy jednego meczu.
- **Pełne przeliczenie:** `POST /admin/ranking/recalculate?season=` (tylko ADMIN) — czyści agregaty
  i odtwarza je z historii zaakceptowanych meczów w kolejności chronologicznej (ważne dla Elo,
  które jest sekwencyjne). Używane po zmianie `ScoringConfig` lub `reopen` meczu.
- **Determinizm:** `RatingCalculator`/`PointsEngine`/`MmrCalculator` to czyste funkcje — dają ten
  sam wynik dla tych samych danych → identyczny wynik naliczania przyrostowego i pełnego (poza
  Elo, które z natury zależy od kolejności — dlatego pełne przeliczenie idzie chronologicznie).

## 4.6 Implementacja — czyste, testowalne jądro

```java
// ranking/domain — bez Springa, bez bazy
record ParticipantStats(Role role, int k, int d, int a, int cs, int gold,
                         int damage, int vision, int largestMultiKill, Side side) {}

interface RatingCalculator {
    /** PR v2 0–100 z historycznym percentylem roli. */
    Map<ParticipantId, Double> computePerformance(MatchStatsContext ctx, ScoringConfig cfg,
                                                   PerformanceHistory history);
}

interface PointsEngine {
    /** LP per uczestnik: baza + próg PR + niestackujące MVP/ACE. */
    Map<ParticipantId, Integer> computeLeaguePoints(MatchStatsContext ctx,
                                                     Map<ParticipantId, Double> pr,
                                                     ScoringConfig cfg);
}

interface MmrCalculator {
    Map<ParticipantId, Double> computeMmrDelta(MatchStatsContext ctx,
                                               Map<ParticipantId, Double> pr,
                                               Map<PlayerId, Double> currentMmr,
                                               ScoringConfig cfg);
}
```

Testy jednostkowe: znane wejście → oczekiwane PR/LP/ΔMMR (m.in. przykład z 4.2), plus property-based
(PR zawsze ∈ [0,100], suma ΔMMR w meczu ≈ 0 przy `modPR=1`, LP ≥ 0).

## 4.7 Prezentacja w UI

- **Ranking sezonu:** miejsce, gracz, LP, W-L, win%, avg PR, (MVP×n), trend MMR opcjonalnie.
- **Profil gracza:** wykres PR w czasie, rozkład championów, najlepsze/najgorsze mecze, seria zwycięstw.
- **Scoreboard meczu:** PR obok KDA, znaczniki MVP/ACE/Penta, „przewidywana szansa" z losowania vs wynik.
