# Driperska Liga

Aplikacja do prowadzenia amatorskiej ligi League of Legends: publiczna strona z wynikami i
rankingiem, profile graczy z pełnymi statystykami, panel administracyjny z losowaniem drużyn oraz
dwustopniową akceptacją wyników.

- **Backend:** Java 21, Spring Boot 3.4, REST API (JWT), JPA, PostgreSQL. Katalog [`backend/`](backend/).
- **Frontend:** React 18 + TypeScript + Vite, TanStack Query, Tailwind. Katalog [`frontend/`](frontend/).
- **Plan i architektura:** [`docs/`](docs/README.md) — obszerny plan implementacji, model domenowy,
  system punktów, REST API, design wizualny, integracja z championami, Docker, roadmapa.

Pracujemy na branchu `driperska-next`.

## Szybki start (Docker)

```bash
cp .env.example .env          # ustaw JWT_SECRET i hasła
docker compose up --build
```

Strona publiczna: `http://localhost:8080` (port zmienisz przez `WEB_PORT`).
Backend jest dostępny tylko przez nginx pod `/api`. Domyślny admin: `admin` / `changeit123`
(zmień hasło po pierwszym logowaniu).

### Tryb developerski

```bash
# baza + backend + frontend, z odsłoniętymi portami do debugowania
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build
```

lub lokalnie:

```bash
# backend (profil dev = baza H2 w pamięci, zero konfiguracji)
cd backend && JWT_SECRET=dev-secret-32-bytes-minimum-please-1234 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# frontend (Vite dev server na :5173, proxy do backendu)
cd frontend && npm install && npm run dev
```

- Swagger UI: `http://localhost:8080/swagger-ui.html` (przez backend na porcie 8080/8081 zależnie od trybu).
- Health: `/actuator/health`.

## Funkcje

- Logowanie (JWT), role **ADMIN** i **EDITOR**.
- Zarządzanie graczami + zdjęcia profilowe (skalowane serwerowo).
- Synchronizacja championów z Riot **Data Dragon** (bez klucza API).
- Cykl życia meczu: utworzenie → **losowanie zbalansowanych drużyn** (balans MMR) → potwierdzenie →
  wpisanie wyników → **akceptacja z podpisem** (nawet gdy wpisał admin, wymagane osobne
  zatwierdzenie checkboxem) albo odesłanie do edycji. Pełny audyt zdarzeń.
- **System punktów:** Performance Rating (0–100) per mecz, Punkty Ligowe (ranking sezonu) oraz
  ukryte MMR (Elo) do balansowania losowania. Szczegóły: [docs/04](docs/04-points-and-ranking.md).
- Publiczny ranking, profile graczy, scoreboardy meczów.

## Stan implementacji

Zaimplementowany jest kompletny backend (wszystkie feature'y z planu: auth, konta, gracze,
championy, sezony, mecze z pełnym cyklem życia, silnik punktów i ranking) oraz frontend (design
system + strony publiczne i panel). Weryfikacja: testy jednostkowe backendu, pełny smoke-test
cyklu życia meczu, build frontendu (lint + typecheck + vite build).

**Świadome uproszczenia względem docelowego planu (do domknięcia w kolejnych iteracjach):**

- Schemat bazy tworzy Hibernate (`ddl-auto=update`); migracje **Flyway** z [docs/02](docs/02-domain-and-database.md)
  to następny krok hardeningu (Faza 0/7 roadmapy).
- Odświeżanie tokenu trzyma refresh token po stronie klienta; docelowo httpOnly cookie.
- Typy API frontu są pisane ręcznie; docelowo generowane z OpenAPI.
