# 01 — Architektura i stack technologiczny

## 1.1 Wybory technologiczne i uzasadnienie

### Backend

| Warstwa | Wybór | Uzasadnienie |
|---------|-------|--------------|
| Język | **Java 21 (LTS)** | Zmiana z Java 23 (non-LTS). LTS = stabilne wsparcie, przewidywalne obrazy Docker (`eclipse-temurin:21-jre`). Wszystkie potrzebne feature'y (records, pattern matching, virtual threads) są w 21. |
| Framework | **Spring Boot 3.4** | Standard rynkowy, dojrzały ekosystem, świetne wsparcie testów. |
| Web | **spring-boot-starter-web** (REST) | Rezygnujemy z Thymeleaf/SSR na rzecz czystego REST API + osobny SPA. |
| Bezpieczeństwo | **spring-boot-starter-security** + **JWT** (jjwt) | Stateless, pasuje do SPA, role-based access. |
| Persystencja | **spring-boot-starter-data-jpa** + Hibernate | Model relacyjny (mecze, gracze, statystyki) mapuje się naturalnie. |
| Baza | **PostgreSQL 16** | Zamiast SQLite: typy `enum`, `jsonb`, indeksy, współbieżność, natywny w Dockerze. |
| Migracje | **Flyway** | Wersjonowany, powtarzalny schemat; brak `ddl-auto=update` na produkcji. |
| Walidacja | **spring-boot-starter-validation** (Jakarta) | Deklaratywna walidacja DTO + walidatory własne. |
| Mapowanie DTO | **MapStruct** | Kompilowane mappery encja↔DTO, zero refleksji, testowalne. |
| Dokumentacja API | **springdoc-openapi** | Generuje OpenAPI 3 + Swagger UI → z tego generujemy typy dla frontu. |
| Klient HTTP | **RestClient** (Spring 6.1) | Do pobierania Data Dragon (championy). |
| Testy | JUnit 5, Mockito, **Testcontainers**, RestAssured/MockMvc, **ArchUnit** | Pełna piramida testów + egzekwowanie reguł architektury. |

> **Virtual threads:** włączamy `spring.threads.virtual.enabled=true` — tania obsługa I/O
> (wywołania Data Dragon, zapytania do bazy) bez rozbudowanego reactive stacku.

### Frontend

| Warstwa | Wybór | Uzasadnienie |
|---------|-------|--------------|
| Framework | **React 18 + TypeScript** | Bogaty ekosystem, komponentowość, łatwe budowanie profili/scoreboardów. |
| Bundler | **Vite** | Szybki dev-server, prosty build produkcyjny do statyków. |
| Routing | **React Router** | Standard dla SPA. |
| Dane serwerowe | **TanStack Query** | Cache, invalidacja, retry, stany loading/error out-of-the-box. |
| Klient API | **openapi-typescript** + typed fetch | Typy generowane z OpenAPI backendu → koniec rozjazdu kontraktu. |
| Stan lokalny | **Zustand** (auth/UI) | Lekki, bez boilerplate'u Reduxa. |
| Style | **Tailwind CSS** + tokeny CSS (motyw) | Spójny design system, szybkie prototypowanie, tryb dark-first. |
| Prymitywy UI | **Radix UI** | Dostępne dialogi/dropdowny/tabsy (WAI-ARIA za darmo). |
| Wykresy | **Recharts** | Wykresy statystyk na profilu gracza. |
| Formularze | **React Hook Form** + Zod | Wydajne formularze wpisywania statystyk + walidacja po stronie klienta. |

> **Dlaczego SPA, a nie Thymeleaf?** Wymagania („ładny publiczny frontend", „REST API",
> „frontend dobrze napisany") wskazują na rozdzielenie warstw. SPA + REST daje czysty kontrakt,
> niezależny deployment, lepszy DX i możliwość rozbudowy (np. mobilny klient) w przyszłości.
> SEO publicznych wyników nie jest krytyczne (społeczność zamknięta) — jeśli kiedyś będzie, można
> dołożyć prerendering. Alternatywa (Next.js SSR) jest odnotowana, ale zwiększa złożoność Dockera.

## 1.2 Architektura backendu — package-by-feature

Pakiety organizujemy wg **funkcjonalności domenowej**, nie wg warstw technicznych. Każdy feature
ma spójne warstwy wewnątrz:

```
pl.romcio.driperska
├── DriperskaApplication.java
├── common/                      # współdzielone: błędy, security, config, audyt, paginacja
│   ├── error/                   # GlobalExceptionHandler, ProblemDetail, wyjątki domenowe
│   ├── security/                # JWT, filtry, SecurityConfig, CurrentUser
│   ├── config/                  # OpenAPI, Jackson, Cache, Async/VirtualThreads, CORS
│   ├── audit/                   # AuditLog (append-only), @Auditable
│   └── web/                     # ApiResponse, PageResponse, bazowe DTO
├── account/                     # konta logowania (ADMIN / EDITOR)
│   ├── api/  application/  domain/  infra/
├── player/                      # gracze ligi + avatary
├── champion/                    # dane championów z Data Dragon
├── season/                      # sezony ligi
├── match/                       # cykl życia meczu, losowanie, wyniki, akceptacja
│   ├── api/                     # MatchController, DrawController, ResultController, ApprovalController + DTO
│   ├── application/             # MatchService, DrawService, ApprovalService (use-case'y)
│   ├── domain/                  # Match, MatchParticipant, MatchStatus (maszyna stanów), zdarzenia
│   └── infra/                   # repozytoria JPA
└── ranking/                     # silnik punktów i rankingi
    ├── api/                     # RankingController
    ├── application/             # RankingService, RecalculationService
    ├── domain/                  # PointsEngine, RatingCalculator, MmrCalculator (czyste funkcje)
    └── infra/
```

### Warstwy wewnątrz feature'u

- **`api`** — kontrolery REST + DTO (request/response) + mappery. Zero logiki biznesowej.
  Kontroler: waliduje wejście, woła `application`, mapuje wynik, zwraca HTTP.
- **`application`** — use-case'y / serwisy aplikacyjne. Orkiestracja: transakcje, autoryzacja
  operacji, publikacja zdarzeń, wywołania innych feature'ów przez ich interfejsy aplikacyjne.
- **`domain`** — encje JPA, obiekty wartości, reguły biznesowe (maszyna stanów meczu, kalkulatory
  punktów). Kalkulatory to **czyste, bezstanowe funkcje** — trywialne do testów jednostkowych.
- **`infra`** — repozytoria Spring Data, klienci zewnętrzni (Data Dragon), storage plików.

## 1.3 Kluczowe wzorce

### Maszyna stanów meczu (bez ciężkiego frameworka)
Zamiast Spring StateMachine — jawny enum `MatchStatus` z tablicą dozwolonych przejść i domenowym
guardem. Prosto, czytelnie, testowalnie (szczegóły w [dok 03](03-match-lifecycle-and-approval.md)).

### Silnik punktów jako czysta domena
`PointsEngine`, `RatingCalculator`, `MmrCalculator` nie znają Springa ani bazy — przyjmują dane
meczu, zwracają wyniki. Reguły (wagi, bonusy) w obiekcie konfiguracyjnym `ScoringConfig`
(ładowalnym z `application.yml` / bazy), więc strojenie systemu nie wymaga zmian w kodzie.

### Zdarzenia domenowe
Po akceptacji meczu `application` publikuje `MatchApprovedEvent` (Spring `ApplicationEventPublisher`).
Nasłuchują: `RankingService` (przelicz LP/MMR), `DiscordNotifier` (opcjonalnie wyślij wynik).
Rozprzęganie = łatwiejsze testy i rozbudowa.

### Obsługa błędów — RFC 7807 (Problem Details)
Jeden `@RestControllerAdvice` mapuje wyjątki domenowe (`ResourceNotFound`, `InvalidTransition`,
`ValidationException`, `AccessDenied`) na `application/problem+json` ze spójnym kształtem
(`type`, `title`, `status`, `detail`, `errors[]`). Szczegóły w [dok 05](05-rest-api.md).

### Autoryzacja
`@PreAuthorize` na poziomie metod use-case'ów (np. `@PreAuthorize("hasRole('ADMIN')")` na akceptacji).
`CurrentUser` (custom argument resolver) wstrzykuje zalogowane konto.

## 1.4 Reguły architektoniczne (egzekwowane ArchUnit)

- `api` nie zależy od `infra` (tylko przez `application`).
- `domain` nie importuje Springa (poza adnotacjami JPA/eventów) ani `api`.
- Kalkulatory w `ranking.domain` nie mają zależności do repozytoriów.
- Kontrolery kończą się na `Controller`, serwisy na `Service`, encje w `domain`.
- Brak cykli między feature'ami — komunikacja tylko przez interfejsy `application`.

## 1.5 Konfiguracja i profile

- Format: **`application.yml`** + profile `dev`, `docker`, `prod`.
- Sekrety (JWT secret, hasło do bazy) tylko przez zmienne środowiskowe — nigdy w repo.
- `dev`: Postgres z Testcontainers lub lokalny compose, `show-sql`, seed danych demo.
- `prod`/`docker`: Flyway `validate`, pool Hikari, logowanie JSON, aktuator z metrykami.
