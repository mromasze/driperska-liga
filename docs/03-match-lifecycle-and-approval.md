# 03 — Cykl życia meczu, losowanie drużyn i akceptacja

## 3.1 Maszyna stanów meczu

```
                    ┌─────────┐
                    │  DRAFT  │  mecz utworzony, wybrana pula graczy
                    └────┬────┘
                         │ POST /draw            (losowanie składów)
                         ▼
                 ┌──────────────┐
                 │ TEAMS_DRAWN  │  propozycja drużyn (można re-rollować)
                 └──────┬───────┘
              re-roll ↺ │ POST /draw/confirm     (admin/editor zatwierdza składy)
                        ▼
                    ┌────────┐
                    │  LIVE  │  gra trwa
                    └───┬────┘
                        │ POST /results          (wpisanie statystyk)
                        ▼
             ┌────────────────────┐
             │ RESULTS_SUBMITTED  │  czeka na akceptację (kolejka admina)
             └─────┬─────────┬────┘
     POST /approve │         │ POST /reject (powód)
       (ADMIN,     │         ▼
     podpisany     │    ┌──────────┐  PATCH /results → ponowny submit
     checkbox)     │    │ REJECTED │──────────────────────────────────┐
                   ▼    └──────────┘                                   │
              ┌──────────┐                                            │
              │ APPROVED │  punkty naliczone, mecz publiczny          │
              └──────────┘                                            │
                                                                      ▼
   Z DRAFT / TEAMS_DRAWN / LIVE / RESULTS_SUBMITTED / REJECTED ──► CANCELLED
```

### Tablica dozwolonych przejść (jawna, w `MatchStatus`)

```java
enum MatchStatus {
    DRAFT, TEAMS_DRAWN, LIVE, RESULTS_SUBMITTED, APPROVED, REJECTED, CANCELLED;

    private static final Map<MatchStatus, Set<MatchStatus>> ALLOWED = Map.of(
        DRAFT,             EnumSet.of(TEAMS_DRAWN, CANCELLED),
        TEAMS_DRAWN,       EnumSet.of(TEAMS_DRAWN, LIVE, CANCELLED),   // TEAMS_DRAWN→self = re-roll
        LIVE,              EnumSet.of(RESULTS_SUBMITTED, CANCELLED),
        RESULTS_SUBMITTED, EnumSet.of(APPROVED, REJECTED, CANCELLED),
        REJECTED,          EnumSet.of(RESULTS_SUBMITTED, CANCELLED),
        APPROVED,          EnumSet.noneOf(MatchStatus.class),          // stan końcowy
        CANCELLED,         EnumSet.noneOf(MatchStatus.class)
    );

    public boolean canTransitionTo(MatchStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
}
```

Domenowy guard w `MatchService` woła `canTransitionTo` i rzuca `InvalidTransitionException`
(→ HTTP 409 Conflict) przy niedozwolonym ruchu. Każde przejście zapisuje `MatchEvent`.

> **Ważne:** `APPROVED` jest stanem końcowym. Korekta zaakceptowanego meczu = osobna operacja
> administracyjna (`POST /matches/{id}/reopen`, tylko ADMIN, z audytem i **przeliczeniem punktów**),
> celowo cięższa, bo zmienia już naliczone punkty w rankingu. Domyślnie niedostępna w UI editora.

## 3.2 Rozpoczęcie meczu i losowanie drużyn

### Krok 1 — utworzenie meczu (`DRAFT`)
Admin/editor wybiera **pulę graczy** (zwykle 10) z listy aktywnych, ustawia `drawMode` i sezon.
Mecz powstaje w stanie `DRAFT` bez przypisanych stron.

### Krok 2 — losowanie (`POST /matches/{id}/draw`)
`DrawService` generuje propozycję podziału na BLUE/RED. Zwraca ją bez zapisu jako finalny —
mecz przechodzi w `TEAMS_DRAWN`, propozycję można **re-rollować** dowolnie wiele razy aż do
zatwierdzenia.

**Tryby losowania:**

- **`PURE_RANDOM`** — losowa permutacja puli, pierwsza piątka → BLUE, druga → RED.
- **`BALANCED`** — podział minimalizujący różnicę sumarycznego MMR drużyn, z respektowaniem ról
  (patrz algorytm niżej). Domyślny.
- **`MANUAL`** — admin sam wskazuje strony (np. z góry ustalone drużyny).

#### Algorytm `BALANCED` (balans MMR + role)
Dla puli 10 graczy z rolami i MMR:

1. Jeśli włączony wymóg ról (1× każda rola na drużynę): przypisz każdemu graczowi rolę
   (preferowana główna, fallback poboczna), pogrupuj po rolach.
2. Dla każdej z 5 ról mamy 2 graczy → 2^5 = 32 możliwych przypisań „który do BLUE".
   Przeszukaj wszystkie (trywialne), wybierz podział minimalizujący `|ΣMMR_BLUE − ΣMMR_RED|`.
3. Jeśli ról nie wymuszamy: losowe rozdania + lokalna optymalizacja (swap-hill-climbing lub
   pełny przegląd `C(10,5)=252` podziałów — także trywialny). Wybierz najlepszy balans.
4. Dołóż mikro-losowość: spośród podziałów w progu ε najlepszego balansu wybierz losowy — żeby
   te same składy nie wracały co mecz.

Zwracamy: przypisanie stron + ról + wyliczoną „przewidywaną szansę BLUE" (z formuły Elo,
[dok 04](04-points-and-ranking.md)) — miły akcent w UI („mecz wyrównany 52% / 48%").

### Krok 3 — zatwierdzenie składów (`POST /matches/{id}/draw/confirm`)
Admin/editor akceptuje propozycję → mecz przechodzi w `LIVE`, tworzone są encje
`MatchParticipant` (na razie bez statystyk). „Gra rusza".

## 3.3 Wpisywanie wyników

`POST /matches/{id}/results` (dostępne dla **ADMIN i EDITOR**) — dla meczu w `LIVE` lub `REJECTED`.
Body zawiera:
- `winningSide` (BLUE/RED),
- `durationSeconds`, `patch`,
- dla każdego z 10 uczestników: champion, K/D/A, CS, gold, dmg, vision, largestMultiKill.

Walidacja: dokładnie 10 uczestników (5+5), statystyki nieujemne, zwycięska strona ustawiona,
championy istnieją. Po sukcesie mecz → `RESULTS_SUBMITTED`, tworzy się `MatchApproval(PENDING)`
z `submittedBy = bieżące konto`. **Na tym etapie punkty NIE są jeszcze naliczane.**

Edycja przed akceptacją: `PATCH /matches/{id}/results` (gdy `RESULTS_SUBMITTED` lub `REJECTED`).

## 3.4 Akceptacja — zasada „dwóch par oczu" + podpis

To sedno wymagania: **nawet jeśli wyniki wpisał admin, wynik nie jest finalny, dopóki admin
świadomie go nie zatwierdzi** przez zaznaczenie podpisanego checkboxa — albo nie odeśle do edycji.

### Zatwierdzenie — `POST /matches/{id}/approve` (tylko `ADMIN`)
Body:
```json
{
  "signatureConfirmed": true,
  "signatureName": "Magda K."
}
```
Reguły:
- `signatureConfirmed` musi być `true` — inaczej HTTP 422 („Wymagane potwierdzenie podpisem").
- `signatureName` wymagane (niepuste).
- Mecz musi być w `RESULTS_SUBMITTED`.
- Operacja **idempotentna** względem już zaakceptowanego meczu (drugi POST → 409, nie podwójne
  punkty).

Skutki (w jednej transakcji):
1. `MatchApproval`: `decision=APPROVED`, `reviewedBy`, `reviewedAt`, zapis podpisu.
2. Mecz → `APPROVED`, `completedAt` ustawione.
3. `MatchEvent(APPROVED)` zapisany.
4. Publikacja `MatchApprovedEvent` → `RankingService` liczy PR/LP/MMR i odświeża
   `PlayerSeasonStats`; `DiscordNotifier` (opcjonalnie) publikuje wynik.

> **Konfigurowalny wariant „four-eyes":** flaga `approval.requireDifferentReviewer`.
> Gdy `true`, zatwierdzający musi być innym kontem niż wpisujący (twarda zasada dwóch osób).
> Gdy `false` (domyślnie, zgodnie z opisem) — ten sam admin może zatwierdzić, ale **musi** wykonać
> świadomy, osobny, podpisany krok akceptacji. Checkbox + podpis to celowa „friction" i ślad audytu.

### Odesłanie do edycji — `POST /matches/{id}/reject` (tylko `ADMIN`)
Body: `{ "reason": "Złe CS u gracza X, popraw" }`. Skutki:
- `MatchApproval`: `decision=REJECTED`, `rejectionReason`, `reviewedBy/At`.
- Mecz → `REJECTED`. Editor/admin poprawia statystyki i ponownie robi submit → znów `RESULTS_SUBMITTED`.
- `MatchEvent(REJECTED)` z powodem.

## 3.5 Kolejka akceptacji (UI panelu admina)

`GET /matches?status=RESULTS_SUBMITTED` → lista meczów czekających na decyzję. Ekran pokazuje pełny
scoreboard (jak ekran końcowy gry), kto wpisał i kiedy, oraz sekcję decyzji z:
- checkboxem „**Potwierdzam poprawność wyników**" (podpis),
- polem podpisu (prefill: nick zalogowanego admina),
- przyciskami **Zatwierdź** (aktywny dopiero po zaznaczeniu checkboxa) i **Odeślij do edycji** (z powodem).

## 3.6 Pełen ślad audytu

Każde przejście = wpis w `MatchEvent` (kto, kiedy, co, payload). `GET /matches/{id}/events` zwraca
oś czasu meczu — od utworzenia, przez losowania (z zapisanymi składami), wpisanie, aż po akceptację
z podpisem. Daje to pełną odtwarzalność i rozliczalność, spójną z wymogiem podwójnej akceptacji.
