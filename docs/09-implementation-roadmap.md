# 09 — Roadmapa implementacji

Praca na branchu `driperska-next`, PR-y do `main`. Każda faza kończy się **działającą** wersją
(`docker compose up`) i **zielonym CI**. Fazy są przyrostowe — po każdej jest co pokazać.

Legenda: 🔴 blokujące dla dalszych faz · 🟡 ważne · 🟢 usprawnienie.

---

## Faza 0 — Fundamenty (szkielet, Docker, CI)
**Cel:** puste, ale w pełni uruchamialne środowisko.

- 🔴 Reorganizacja repo do monorepo (`backend/`, `frontend/`, `docker-compose*.yml`, `.env.example`).
- 🔴 `pom.xml`: Java 21, dependencje (web, security, data-jpa, validation, flyway, postgres, mapstruct, springdoc, jjwt, testcontainers).
- 🔴 Usunięcie stubów Thymeleaf i `static/1sezon/*`; szkielet pakietów package-by-feature.
- 🔴 PostgreSQL + Flyway `V1` (pusta migracja bazowa); `application.yml` z profilami `dev/docker`.
- 🔴 Dockerfile backend + frontend, `docker-compose.yml`, nginx reverse proxy, healthchecki.
- 🔴 Frontend: bootstrap Vite+React+TS, Tailwind + tokeny motywu, router, layout, strona „Hello".
- 🟡 CI (`ci.yml`): build+test backend, build+lint frontend, build obrazów.
- 🟢 ArchUnit — reguły warstw (na razie szkielet).

**Kryteria akceptacji:** `docker compose up` wstaje (db+backend+frontend), `/actuator/health` = UP,
frontend serwuje stronę, CI zielone.

---

## Faza 1 — Tożsamość, konta, gracze, championy
**Cel:** logowanie i dane słownikowe.

- 🔴 Encje + migracje: `Account`, `Player`, `Champion`, `Season`.
- 🔴 Spring Security: JWT (access+refresh), `SecurityConfig`, BCrypt, `/auth/login|refresh|me|logout`.
- 🔴 Seed pierwszego admina (Flyway repeatable / bootstrap z env).
- 🔴 CRUD `players` + upload avatarów (skalowanie obrazów, storage na wolumenie `/media`).
- 🔴 `ChampionSyncService` + `POST /champions/sync` + bootstrap na starcie (Data Dragon).
- 🟡 CRUD `accounts` (ADMIN), CRUD `seasons` (aktywny sezon).
- 🟡 Frontend: ekran logowania, guard tras `/admin/*`, panel „Gracze" (lista + dodawanie + avatar), panel „Konta".
- 🟡 Global exception handler (RFC 7807), `PageResponse`, OpenAPI + Swagger UI.

**Kryteria akceptacji:** admin loguje się, dodaje gracza ze zdjęciem, synchronizuje championów;
`GET /champions` zwraca listę z ikonami; publiczna lista graczy działa.

---

## Faza 2 — Rdzeń meczu: tworzenie i losowanie drużyn
**Cel:** rozpoczęcie meczu i losowanie składów.

- 🔴 Encje + migracje: `Match`, `MatchParticipant`, `MatchEvent`; enum `MatchStatus` + tablica przejść.
- 🔴 `MatchService`: `POST /matches` (DRAFT z pulą graczy), guard przejść + zapis `MatchEvent`.
- 🔴 `DrawService`: tryby `PURE_RANDOM` / `BALANCED` (balans MMR + role) / `MANUAL`;
  `POST /draw` (re-roll), `POST /draw/confirm` (→ LIVE, tworzy uczestników).
- 🟡 Frontend: kreator meczu (`/admin/matches/new`), `DrawBoard` (losuj/re-roll/potwierdź, pokaz szansy).
- 🟢 Testy jednostkowe algorytmu balansu (minimalizacja różnicy MMR, poprawność ról).

**Kryteria akceptacji:** admin tworzy mecz z 10 graczy, losuje zbalansowane drużyny, re-rolluje,
zatwierdza → mecz `LIVE`, oś czasu (`/events`) zawiera CREATED/TEAMS_DRAWN/DRAW_CONFIRMED.

---

## Faza 3 — Wyniki i dwustopniowa akceptacja
**Cel:** wpisywanie statystyk i zasada „dwóch par oczu" + podpis.

- 🔴 Encja + migracja `MatchApproval`.
- 🔴 `POST /matches/{id}/results` (ADMIN+EDITOR) + walidacja (10 uczestników, 5+5, statystyki ≥ 0,
  championy istnieją, zwycięska strona) → `RESULTS_SUBMITTED`, `MatchApproval(PENDING)`.
- 🔴 `PATCH /results` (edycja przed akceptacją).
- 🔴 `POST /approve` (ADMIN) — wymóg `signatureConfirmed=true` + `signatureName`; idempotencja; → APPROVED.
- 🔴 `POST /reject` (ADMIN, z powodem) → REJECTED → ponowny submit.
- 🟡 Publikacja `MatchApprovedEvent` (na razie tylko log; punkty w Fazie 4).
- 🟡 Frontend: formularz wyników (scoreboard do wpisania, ChampionPicker + statystyki),
  kolejka akceptacji (`/admin/approvals`) z `SignOffPanel` (checkbox blokujący przycisk Zatwierdź).

**Kryteria akceptacji:** editor wpisuje wyniki → mecz czeka na akceptację; admin nie może
zatwierdzić bez zaznaczenia podpisanego checkboxa; odesłanie do edycji z powodem działa; pełny
audyt w `/events`.

---

## Faza 4 — Silnik punktów i ranking
**Cel:** PR, LP, MMR, tabela sezonu.

- 🔴 Czyste kalkulatory (`ranking.domain`): `RatingCalculator` (PR), `PointsEngine` (LP),
  `MmrCalculator` (Elo) + `ScoringConfig` (YAML/jsonb per sezon).
- 🔴 `PlayerSeasonStats` + `RankingService`: nasłuch `MatchApprovedEvent` → naliczenie przyrostowe.
- 🔴 `GET /ranking` i `GET /seasons/{id}/ranking` (tie-breakery), `GET /players/{id}/stats`.
- 🔴 `POST /ranking/recalculate` (ADMIN) — pełne przeliczenie chronologiczne (dla Elo).
- 🔴 `POST /matches/{id}/reopen` (korekta zaakceptowanego meczu + przeliczenie).
- 🟡 Frontend: tabela rankingu (medale, sortowanie, filtr sezonu), PR na scoreboardzie, znaczniki MVP/ACE.
- 🟢 Testy: worked example z [dok 04](04-points-and-ranking.md), property-based (PR∈[0,100], LP≥0).

**Kryteria akceptacji:** po akceptacji meczu ranking aktualizuje się; wartości LP/PR/MMR zgodne z
przykładami; pełne przeliczenie daje spójny wynik; korekta meczu poprawnie przelicza punkty.

---

## Faza 5 — Publiczny frontend (design)
**Cel:** atrakcyjna strona publiczna.

- 🔴 Design system: komponenty `StatTile`, `PrBadge`, `ChampionIcon`, `ChampionSplashCard`,
  `Scoreboard`, `RankMedal`, `Card`, `Avatar`, `Table`.
- 🔴 Strony: Home/Wyniki (hero + karty meczów), Ranking, Lista graczy, Profil gracza
  (kafle + wykres PR + champion pool + historia), Szczegóły meczu (scoreboard).
- 🟡 Klient API generowany z OpenAPI, TanStack Query + invalidacje, stany loading/empty/error.
- 🟡 Responsywność (mobile), dostępność (kontrast, klawiatura, alt), lazy-loading obrazów.
- 🟢 Wykresy statystyk (Recharts) na profilu.

**Kryteria akceptacji:** niezalogowany użytkownik przegląda wyniki, ranking i profile graczy z
championami i statystykami; strona responsywna; Lighthouse a11y/perf w normie.

---

## Faza 6 — Dopięcie panelu admina
**Cel:** wygodny panel end-to-end.

- 🟡 Dashboard z licznikiem meczów do akceptacji i szybkimi akcjami.
- 🟡 Pełny przepływ w UI: nowy mecz → losowanie → LIVE → wyniki → akceptacja/odesłanie — spójny UX.
- 🟡 Zarządzanie sezonami (aktywacja/archiwizacja, przeliczanie), zarządzanie kontami/rolami.
- 🟢 Optimistic UI dla re-rolla i drobnych edycji.

**Kryteria akceptacji:** cały cykl życia meczu wykonalny wyłącznie z UI, bez sięgania do API ręcznie.

---

## Faza 7 — Integracje, hardening, wdrożenie
**Cel:** produkcyjna jakość.

- 🟢 `DiscordNotifier` — po akceptacji publikacja wyniku na webhook (reaktywacja pomysłu z `DiscordBot`).
- 🟡 Cache (championy, ranking) + invalidacja po akceptacji; rate-limiting logowania.
- 🟡 Testy E2E (Playwright) krytycznej ścieżki; twarde testy bezpieczeństwa autoryzacji ról.
- 🟡 `release.yml`: push obrazów do GHCR + deploy; backupy bazy i mediów (cron `pg_dump`).
- 🟢 Nagłówki bezpieczeństwa (CSP/HSTS) na nginx, logi JSON, dashboard metryk.
- 🟢 README uruchomieniowe, dokumentacja `ScoringConfig` do strojenia punktów.

**Kryteria akceptacji:** produkcyjny `docker compose` z sekretami z env, backup działa, E2E zielone,
wynik meczu ląduje na Discordzie (jeśli włączone).

---

## Kolejność zależności (skrót)

```
Faza 0 ──► Faza 1 ──► Faza 2 ──► Faza 3 ──► Faza 4 ──► Faza 5 ─┐
                                                    └► Faza 6 ─┴─► Faza 7
```

Fazy 5 i 6 (frontend) mogą częściowo iść **równolegle** do 2–4 (backend), bo kontrakt API jest
ustalony w [dok 05](05-rest-api.md), a typy generowane z OpenAPI — front może pracować na mockach
kontraktu, zanim backend danej funkcji jest gotowy.

## Sugerowana kolejność pierwszych PR-ów
1. `chore: monorepo layout + docker compose skeleton` (Faza 0)
2. `feat: postgres + flyway + health` (Faza 0)
3. `feat(frontend): vite+tailwind+theme tokens + layout` (Faza 0)
4. `feat(auth): jwt security + login + first admin seed` (Faza 1)
5. `feat(player): CRUD + avatar upload` (Faza 1)
6. `feat(champion): data dragon sync` (Faza 1)
… dalej wg faz.
