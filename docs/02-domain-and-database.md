# 02 — Model domenowy i baza danych

## 2.1 Diagram encji (koncepcyjny)

```
Account ──(0..1)── Player
                     │
                     │ (uczestniczy w)
                     ▼
Season ──1:N── Match ──1:N── MatchParticipant ──N:1── Champion
                 │                    │
                 │                    └── (statystyki + wyliczone: PR, LP, ΔMMR)
                 ├──1:1── MatchApproval   (podpis, kto zatwierdził)
                 └──1:N── MatchEvent      (append-only audyt cyklu życia)

PlayerSeasonStats  (materializowany agregat: LP, W/L, avg PR, MMR per gracz/sezon)
```

## 2.2 Encje

### `Account` — konto logowania
| Pole | Typ | Uwagi |
|------|-----|-------|
| id | UUID | PK |
| username | varchar unique | login |
| email | varchar unique | |
| passwordHash | varchar | BCrypt |
| role | enum `ADMIN` \| `EDITOR` | patrz role niżej |
| enabled | bool | dezaktywacja bez usuwania |
| createdAt / lastLoginAt | timestamptz | |

**Role:**
- **ADMIN** — pełne uprawnienia: zarządza kontami, graczami, sezonami, losuje drużyny, wpisuje
  wyniki **oraz akceptuje/odrzuca** wyniki.
- **EDITOR** (nie-admin z dostępem do panelu) — może rozpocząć mecz, losować drużyny, wpisywać
  wyniki, ale **nie może akceptować** — jego wyniki zawsze idą do zatwierdzenia przez admina.

### `Player` — gracz ligi
| Pole | Typ | Uwagi |
|------|-----|-------|
| id | UUID | PK |
| nickname | varchar unique | nick w lidze |
| realName | varchar null | opcjonalnie |
| riotId | varchar null | `gameName#TAG` (do wyświetlania / przyszłej integracji) |
| mainRole / secondaryRole | enum Role | TOP/JUNGLE/MID/ADC/SUPPORT |
| avatarUrl | varchar null | ścieżka do zdjęcia profilowego |
| bio | text null | |
| accountId | UUID null FK→Account | gracz może (ale nie musi) mieć konto |
| active | bool | czy aktywny w bieżącym sezonie |
| joinedAt | timestamptz | |

### `Champion` — bohater z LoL (z Data Dragon)
| Pole | Typ | Uwagi |
|------|-----|-------|
| id | int | PK = Riot `key` (np. 266 = Aatrox) |
| slug | varchar | Riot `id` (np. `Aatrox`) — do budowy URL |
| name | varchar | „Aatrox" |
| title | varchar | „the Darkin Blade" |
| tags | varchar[] | Fighter, Tank… |
| ddragonVersion | varchar | patch, z którego zsynchronizowano |
| iconUrl / splashUrl / loadingUrl | varchar | pełne URL-e CDN (patrz [dok 07](07-lol-champions-integration.md)) |

### `Season` — sezon ligi
| Pole | Typ | Uwagi |
|------|-----|-------|
| id | UUID | PK |
| name | varchar | „Sezon 2 — Lato 2026" |
| startDate / endDate | date | |
| status | enum `ACTIVE` \| `ARCHIVED` \| `UPCOMING` | tylko jeden `ACTIVE` |
| scoringConfigJson | jsonb null | override reguł punktacji dla sezonu |

### `Match` — mecz
| Pole | Typ | Uwagi |
|------|-----|-------|
| id | UUID | PK |
| seasonId | UUID FK | |
| status | enum `MatchStatus` | maszyna stanów ([dok 03](03-match-lifecycle-and-approval.md)) |
| drawMode | enum `PURE_RANDOM` \| `BALANCED` \| `MANUAL` | tryb losowania |
| winningSide | enum `BLUE` \| `RED` null | ustalane przy wpisywaniu wyników |
| durationSeconds | int null | czas gry (do statystyk per-minutę) |
| patch | varchar null | patch LoL |
| notes | text null | |
| createdBy | UUID FK→Account | |
| createdAt / startedAt / completedAt | timestamptz | |

### `MatchParticipant` — gracz w konkretnym meczu
Rdzeń statystyk. Pola **wprowadzane** przez admina/editora vs **wyliczane** przez silnik punktów.

| Pole | Typ | Wprowadzane/Wyliczane |
|------|-----|-----------------------|
| id | UUID | PK |
| matchId | UUID FK | |
| playerId | UUID FK | |
| side | enum `BLUE` \| `RED` | wpr. (z losowania) |
| role | enum Role | wpr. |
| championId | int FK→Champion | wpr. |
| kills / deaths / assists | int | wpr. |
| cs (minions+monsters) | int | wpr. |
| gold | int | wpr. |
| damageToChampions | int | wpr. |
| visionScore | int | wpr. |
| largestMultiKill | int (1–5) | wpr. (do bonusów penta/quadra) |
| — | | |
| kda | numeric | wyliczane: `(K+A)/max(1,D)` |
| performanceRating | numeric(5,2) | wyliczane: PR 0–100 |
| lpAwarded | int | wyliczane: punkty ligowe |
| mmrDelta | numeric | wyliczane: zmiana MMR |
| isMvp | bool | wyliczane |

> **Minimalny zestaw do wpisania** (realny do przepisania z ekranu końcowego gry): champion, rola,
> K/D/A, CS, gold, obrażenia do bohaterów, vision score, największy multikill. Reszta liczona.
> Statystyki drużynowe (team kills, team damage) wyliczamy sumując uczestników danej strony.

### `MatchApproval` — akceptacja („dwie pary oczu" + podpis)
| Pole | Typ | Uwagi |
|------|-----|-------|
| id | UUID | PK, 1:1 z Match |
| matchId | UUID FK | |
| submittedBy | UUID FK→Account | kto wpisał wyniki |
| submittedAt | timestamptz | |
| decision | enum `PENDING` \| `APPROVED` \| `REJECTED` | |
| reviewedBy | UUID FK→Account null | admin zatwierdzający |
| reviewedAt | timestamptz null | |
| signatureConfirmed | bool | checkbox „potwierdzam poprawność" |
| signatureName | varchar null | podpis (imię/nick zatwierdzającego) |
| rejectionReason | text null | powód odesłania do edycji |

### `MatchEvent` — audyt (append-only)
| Pole | Typ | Uwagi |
|------|-----|-------|
| id | UUID | PK |
| matchId | UUID FK | |
| type | enum | `CREATED`, `TEAMS_DRAWN`, `DRAW_CONFIRMED`, `RESULTS_SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED` |
| actorAccountId | UUID FK | |
| payloadJson | jsonb null | np. skład drużyn, poprzedni/nowy status |
| createdAt | timestamptz | |

### `PlayerSeasonStats` — materializowany agregat (ranking)
Utrzymywany/odświeżany po akceptacji meczu. Umożliwia szybki ranking bez agregacji na żywo.
| Pole | Typ |
|------|-----|
| playerId + seasonId | PK złożone |
| totalLp | int |
| games / wins / losses | int |
| avgPerformanceRating | numeric |
| mmr | numeric |
| mvpCount / pentaCount | int |
| updatedAt | timestamptz |

## 2.3 Enumy

- `Role`: `TOP, JUNGLE, MID, ADC, SUPPORT`
- `Side`: `BLUE, RED`
- `MatchStatus`: `DRAFT, TEAMS_DRAWN, LIVE, RESULTS_SUBMITTED, APPROVED, REJECTED, CANCELLED`
- `AccountRole`: `ADMIN, EDITOR`
- `DrawMode`: `PURE_RANDOM, BALANCED, MANUAL`
- `SeasonStatus`: `UPCOMING, ACTIVE, ARCHIVED`

Enumy trzymamy w bazie jako `varchar` (`@Enumerated(EnumType.STRING)`) — czytelne w SQL, odporne
na zmianę kolejności.

## 2.4 Migracje (Flyway)

Wersjonowane pliki w `src/main/resources/db/migration`:

```
V1__accounts.sql
V2__players.sql
V3__champions.sql
V4__seasons.sql
V5__matches_and_participants.sql
V6__match_approval_and_events.sql
V7__player_season_stats.sql
V8__indexes.sql
R__seed_dev_data.sql        # repeatable, tylko profil dev
```

**Indeksy kluczowe (`V8`):**
- `match_participant(player_id)`, `match_participant(match_id)`, `match_participant(champion_id)`
- `match(season_id, status)`, `match(status)` — listy publiczne i kolejka akceptacji
- `player_season_stats(season_id, total_lp DESC)` — ranking
- `account(username)`, `account(email)` — logowanie (unique)

## 2.5 Zasady integralności

- Uczestnik jest unikalny w meczu: `unique(match_id, player_id)`.
- Mecz „kompletny" = dokładnie 10 uczestników (5 BLUE + 5 RED) — walidacja przy submit wyników.
- Role w drużynie unikalne (opcjonalne, konfigurowalne — dla trybu bez sztywnych ról można wyłączyć).
- Statystyki nieujemne (`@PositiveOrZero`), `largestMultiKill ∈ [0,5]`.
- Usuwanie gracza z historią meczów: **soft-delete** (`active=false`) — nie kasujemy danych meczów.
- `winningSide` wymagany przy przejściu do `RESULTS_SUBMITTED`.
