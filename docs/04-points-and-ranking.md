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

Wszystkie wagi/bonusy siedzą w `ScoringConfig` (YAML/jsonb per sezon), więc system stroimy bez
zmian w kodzie. Poniższe liczby to **rozsądny domyślny zestaw** do dostrojenia po pierwszych meczach.

---

## 4.2 Performance Rating (PR) — ocena meczu 0–100

PR ocenia **grę indywidualną względem reszty meczu**, z **wagami zależnymi od roli** (support nie
jest karany za niskie CS, ADC nie jest karany za niski vision itd.). Liczony wyłącznie z wpisanych
statystyk — bez wpływu wyniku meczu (żeby „MVP przegranych" miało sens).

### Krok 1 — metryki bazowe (per uczestnik)
Z surowych statystyk i czasu gry `min = durationSeconds / 60`:

| Metryka | Wzór |
|---------|------|
| `KDA` | `(K + A) / max(1, D)` |
| `KP` (kill participation) | `(K + A) / max(1, teamKills)` |
| `CSpm` | `CS / min` |
| `DMGpm` | `damageToChampions / min` |
| `DMGshare` | `damageToChampions / max(1, teamDamage)` |
| `GOLDshare` | `gold / max(1, teamGold)` |
| `VSpm` | `visionScore / min` |

`teamKills`, `teamDamage`, `teamGold` = sumy po uczestnikach tej samej strony.

### Krok 2 — normalizacja względem meczu
Każdą metrykę normalizujemy do skali 0–1 względem **średniej dla tej samej roli w tym meczu**
(a gdy w danej roli jest tylko jeden gracz na mapie — względem średniej całego meczu):

```
norm(x) = clamp( x / (2 * avgRole(x)) , 0, 1)
```

Interpretacja: dokładnie średnia roli → 0.5; dwukrotność średniej lub więcej → 1.0. Odporne na
skrajne wartości (clamp), niezależne od patcha/długości gry (wszystko relatywne).

### Krok 3 — wagi ról
Profile wag (sumują się do 1.0 w każdej roli):

| Metryka | TOP | JUNGLE | MID | ADC | SUPPORT |
|---------|----:|-------:|----:|----:|--------:|
| KDA | 0.25 | 0.20 | 0.20 | 0.20 | 0.25 |
| KP | 0.15 | 0.25 | 0.20 | 0.15 | 0.30 |
| CSpm | 0.15 | 0.10 | 0.15 | 0.20 | 0.00 |
| DMGpm/share | 0.25 | 0.20 | 0.30 | 0.35 | 0.15 |
| GOLDshare | 0.10 | 0.10 | 0.05 | 0.05 | 0.05 |
| VSpm | 0.10 | 0.15 | 0.10 | 0.05 | 0.25 |

> Dla DMG używamy średniej z `norm(DMGpm)` i `norm(DMGshare)` — łapie i „ile bijesz", i „jaki masz
> udział w drużynie". Wagi to punkt startowy; do kalibracji w `ScoringConfig`.

### Krok 4 — złożenie
```
PR = 100 * Σ_metryka ( waga_roli(metryka) * norm(metryka) )
```
Wynik 0–100. Typowy gracz ≈ 50, wyróżniający się 65–80, dominujący 80+.

### Worked example (skrót)
ADC: 10/2/8, CS 250, dmg 30k, gold 15k, vision 20, gra 30 min; średnie roli ADC w meczu:
KDA 4.5, CSpm 7.0, DMGpm 850, DMGshare 0.26, GOLDshare 0.24, VSpm 0.9, KP 0.55.
- KDA = 18/2 = 9.0 → norm = clamp(9/(2·4.5),0,1)=1.0
- CSpm = 8.33 → norm = clamp(8.33/14,0,1)=0.595
- DMGpm = 1000 → norm=clamp(1000/1700)=0.588; DMGshare=0.30→norm=clamp(.30/.52)=0.577; śr.≈0.583
- GOLDshare = 0.25 → 0.52 ; VSpm=0.667→0.37 ; KP=0.6→0.545
- PR = 100·(0.20·1.0 + 0.15·0.545 + 0.20·0.595 + 0.35·0.583 + 0.05·0.52 + 0.05·0.37)
     = 100·(0.20+0.0818+0.119+0.204+0.026+0.0185) ≈ **64.9** → solidna gra.

---

## 4.3 Punkty Ligowe (LP) — ranking sezonu

To, co widać w tabeli i wyłania mistrza sezonu. Naliczane **przy akceptacji meczu**.

```
LP(gracz) = LP_bazowe(wynik) + LP_performance(PR) + Σ bonusy
```

| Składnik | Wartość |
|----------|---------|
| **LP_bazowe** | wygrana: **+10**, przegrana: **+2** (nagroda za udział — warto grać nawet w słabym składzie) |
| **LP_performance** | `round(PR / 10)` → **0–10** (nigdy nie karze; premiuje dobrą grę niezależnie od wyniku) |
| **MVP meczu** | +5 (najwyższy PR w meczu) |
| **ACE przegranych** | +3 (najwyższy PR po przegranej stronie — „nieśli mimo porażki") |
| **Pentakill** | +5 |
| **Quadrakill** | +2 |
| **Flawless (0 śmierci, ≥1 udział)** | +2 |

Zakres realny: dominujące zwycięstwo z MVP i pentą ≈ `10+10+5+5 = 30 LP`; blada przegrana ≈ `2+2 = 4 LP`.
System jest **niekarzący** (brak ujemnych LP) — sprzyja frekwencji, a i tak różnicuje wyraźnie.

### Tabela sezonu i tie-breakery
Sortowanie rankingu:
1. `totalLp` malejąco,
2. win rate (`wins/games`),
3. średni PR (`avgPerformanceRating`),
4. liczba MVP,
5. mniej rozegranych gier (premiuje efektywność przy remisie punktów).

> **Opcjonalny „soft cap" antygrindowy:** jeśli liczba meczów graczy jest bardzo nierówna, można
> pokazać dodatkowo ranking wg **średniego LP na mecz** (min. próg gier, np. 5), obok sumy LP.
> Konfigurowalne; domyślnie tabela wg sumy LP + kolumna „LP/mecz" informacyjnie.

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
  ΔMMR_i = K * (S_teamGracza − E_teamGracza) * modPR_i
  ```
  gdzie:
  - `K` = 32 (nowicjusze < 10 gier: K=48 dla szybszej kalibracji),
  - `modPR_i` = `0.75 + 0.5 * (PR_i / 100)` — kto wybił się w meczu, zyskuje/traci więcej
    (mnożnik ≈ 0.75–1.25). Opcjonalny; przy `scoring.mmrPrModulation=false` → `modPR_i = 1`.

MMR liczymy tylko dla meczów z pełnym, poprawnym scoreboardem (10 graczy). Zmiana zapisywana w
`MatchParticipant.mmrDelta` i agregowana do `PlayerSeasonStats.mmr` (dla ciągłości można też trzymać
globalny MMR gracza niezależny od sezonu — decyzja: **MMR globalny, przenoszony między sezonami**,
LP resetowane co sezon).

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
    /** PR 0–100 dla każdego uczestnika, w kontekście całego meczu. */
    Map<ParticipantId, Double> computePerformance(MatchStatsContext ctx, ScoringConfig cfg);
}

interface PointsEngine {
    /** LP per uczestnik: bazowe + performance + bonusy (MVP/ACE/penta...). */
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
