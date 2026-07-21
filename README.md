# Driperska Liga v0.2

Aplikacja do prowadzenia amatorskiej ligi League of Legends: ranking i wyniki,
profile graczy, panel administracyjny oraz głosowane losowanie drużyn w czasie rzeczywistym.

- **Backend:** Java 21, Spring Boot, JWT, JPA, Flyway i PostgreSQL.
- **Frontend:** React + TypeScript + Vite, TanStack Query i Tailwind.
- **Uruchomienie:** Docker Compose; publiczny ruch przechodzi przez nginx.
- **Changelog:** [CHANGELOG.md](CHANGELOG.md).
- **Pełne wdrożenie driperska.pl:** [docs/10-production-deployment.md](docs/10-production-deployment.md).

## Szybki start

```bash
cp .env.example .env
# ustaw hasła oraz sekrety RIOT_API_KEY i DISCORD_BOT_TOKEN/DISCORD_GUILD_ID
docker compose up --build
```

Domyślny port z przykładowego pliku to `127.0.0.1:18080`. Tryb developerski:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build
```

Lokalnie bez Postgresa:

```bash
cd backend
JWT_SECRET=dev-secret-32-bytes-minimum-please-1234 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

cd ../frontend
npm install
npm run dev
```

## Najważniejsze funkcje v0.2

- Admin tworzy gracza z obowiązkową nazwą Discord. Bot wysyła login i losowe hasło
  na DM; panel pozwala też skopiować wiadomość i wygenerować oraz wysłać nowe hasło.
- Utworzenie meczu od razu rozpoczyna rundę losowania dla dokładnie 10 graczy.
- Składy i strony są transmitowane przez SSE. 6 głosów „za” tworzy lobby Riot;
  5 „przeciw” automatycznie losuje kolejną rundę.
- Gracze widzą przypisaną stronę i kod turniejowy, a admin kontroluje obecność
  w lobby i ręcznie oznacza rozpoczęcie meczu.
- Callback Tournament API pobiera dane meczu przez Match-v5 i wysyła wynik do kolejki
  akceptacji. Admin ma ręczny fallback pobrania po kodzie turniejowym.
- Przed rozpoczęciem można wymienić gracza; gotowe lobby otrzymuje wtedy nowy kod.
- Gracz edytuje role, ulubionych championów, Riot ID, bio, zdjęcie i OP.GG.
- Bazowa migracja v0.2 zawiera cały schemat dla pustej bazy. Deploy robi backup i nie usuwa
  wolumenów PostgreSQL ani mediów.
- Strona główna pokazuje patch notes z `frontend/src/content/releases.ts`.

Tournament API wymaga produkcyjnego klucza z przyznanym dostępem do produktu Tournament.
Callback `RIOT_CALLBACK_URL` musi być publicznym adresem HTTPS. Sekretów nie zapisuj
w repozytorium; podawaj je wyłącznie przez plik `.env` na serwerze.

## Weryfikacja

```bash
cd backend && ./mvnw verify
cd ../frontend && npm run lint && npm run typecheck && npm run build
docker compose config -q
```

Swagger UI: `/swagger-ui.html`, health: `/actuator/health`.