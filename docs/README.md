# Driperska Liga — plan implementacji v2 (`driperska-next`)

> **Status:** plan zrealizowany w pierwszej iteracji — backend (`backend/`) i frontend (`frontend/`)
> są zaimplementowane i uruchamialne w Dockerze. Ten dokument pozostaje źródłem prawdy o projekcie
> i architekturze; sekcja „Stan implementacji" w [głównym README](../README.md) opisuje świadome
> uproszczenia względem docelowego planu.

> Kompletny plan przeprojektowania aplikacji do zarządzania amatorską ligą League of Legends:
> publiczna strona z wynikami i rankingiem, profile graczy z pełnymi statystykami, panel
> administracyjny z losowaniem drużyn i dwustopniową akceptacją wyników, integracja z championami
> z LoL, REST API w Javie oraz uruchomienie całości w Dockerze.

## Cel biznesowy

Aplikacja obsługuje wewnętrzną („inhouse") ligę LoL, w której **drużyny są losowane przed każdym
meczem** (skład miesza się z gry na grę). Chcemy:

1. **Publiczny frontend** — atrakcyjna wizualnie strona: wyniki meczów, ranking graczy sezonu,
   profil każdego gracza z pełnymi statystykami z LoL (KDA, CS, championy, historia meczów).
2. **Panel administracyjny** — logowanie, zarządzanie kontami, dodawanie graczy i ich zdjęć
   profilowych, wprowadzanie statystyk.
3. **Cykl życia meczu** — rozpoczęcie meczu → losowanie drużyn z panelu → zatwierdzenie składów →
   gra rusza → wpisywanie wyników (przez admina i nie-admina) → **akceptacja wyników** (nawet gdy
   wpisał je admin, admin musi jeszcze raz zatwierdzić przez podpisany checkbox albo odesłać do
   edycji).
4. **System punktów** — sprawiedliwy przy losowanych drużynach, nagradzający zwycięstwa i grę
   indywidualną, plus ukryty rating do balansowania losowania.
5. **Docker** — całość (baza, backend, frontend) uruchamiana jednym `docker compose up`.

## Stan wyjściowy repozytorium

- Spring Boot 3.4.1, Java 23, Maven.
- Wszystkie klasy Java to puste stuby (4 linie), szablony Thymeleaf puste.
- Stara konfiguracja: SQLite + Thymeleaf (server-side rendering) + stub `DiscordBot`.
- Assety ze „starego sezonu" w `static/1sezon/*` (do usunięcia / archiwizacji).

**Wniosek:** to praktycznie greenfield — przepisujemy od zera, zachowując tylko domenę problemu
i pomysł na Discord (opcjonalna integracja powiadomień).

## Mapa dokumentów

| Dok | Zakres |
|-----|--------|
| [01 — Architektura i stack](01-architecture-and-stack.md) | Decyzje technologiczne, warstwy backendu, wzorce, struktura katalogów |
| [02 — Model domenowy i baza](02-domain-and-database.md) | Encje, schemat PostgreSQL, migracje Flyway |
| [03 — Cykl życia meczu i akceptacja](03-match-lifecycle-and-approval.md) | Maszyna stanów, losowanie drużyn, zasada dwóch par oczu, audyt |
| [04 — System punktów i ranking](04-points-and-ranking.md) | Performance Rating, punkty ligowe (LP), MMR/Elo, tie-breakery |
| [05 — REST API](05-rest-api.md) | Zasoby, endpointy, DTO, błędy (RFC 7807), bezpieczeństwo, wersjonowanie |
| [06 — Frontend i design wizualny](06-frontend-and-visual-design.md) | React+TS, design system, paleta „Hextech", komponenty, dostępność |
| [07 — Integracja z championami LoL](07-lol-champions-integration.md) | Data Dragon, synchronizacja, cache, obrazy |
| [08 — Docker i DevOps](08-docker-and-devops.md) | Dockerfile'e, compose, nginx, profile, CI, storage plików |
| [09 — Roadmapa implementacji](09-implementation-roadmap.md) | Fazy, zadania, kryteria akceptacji, kolejność prac |

## Kluczowe decyzje w skrócie

- **Backend:** Java 21 LTS (zmiana z 23), Spring Boot 3.4, Spring Web + Security (JWT) + Data JPA +
  Validation, Flyway, MapStruct, PostgreSQL. Architektura **package-by-feature**, warstwy
  `api / application / domain / infra`.
- **Frontend:** oddzielny SPA — **React + TypeScript + Vite**, TanStack Query, klient API generowany
  z OpenAPI (pełna typizacja), Tailwind + tokeny motywu, Radix UI dla dostępności.
- **Baza:** PostgreSQL (zamiast SQLite) — poprawne typy, migracje, współbieżność, gotowość na Docker.
- **Style plików:** zdjęcia graczy na wolumenie (v1), z furtką na MinIO/S3 (v2).
- **Championy:** statyczne dane z Riot **Data Dragon** (bez klucza API), synchronizowane do bazy.
- **Punkty:** system hybrydowy — **Punkty Ligowe (LP)** widoczne w rankingu + **Performance Rating
  (PR 0–100)** per mecz + ukryte **MMR (Elo)** do balansowania losowania drużyn.
- **Deployment:** nginx jako reverse proxy (jeden origin: `/` → frontend, `/api` → backend), brak
  problemów z CORS, `docker compose` spina bazę + backend + frontend.

## Zasady prowadzenia projektu

- Pracujemy na branchu `driperska-next`, PR-y scalane do `main` po review.
- Konwencja commitów: Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`…).
- Każda faza kończy się działającą, uruchamialną wersją (`docker compose up`) i zielonym CI.
- Testy: unit (JUnit5 + Mockito), integracyjne (Testcontainers + Postgres), architektura (ArchUnit).
