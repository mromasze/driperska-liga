# Driperska Liga v0.1

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
# ustaw silne DB_PASSWORD, JWT_SECRET i APP_ADMIN_PASSWORD
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

## Najważniejsze funkcje v0.1

- Admin tworzy gracza razem z kontem. Login jest równy nickowi, hasło jest losowe,
  a UI generuje gotową wiadomość do wysłania na DM.
- Utworzenie meczu od razu rozpoczyna rundę losowania dla dokładnie 10 graczy.
- Składy i strony są transmitowane przez SSE. 6 głosów „za” uruchamia grę;
  5 „przeciw” automatycznie losuje kolejną rundę.
- Gracz edytuje role, ulubionych championów, Riot ID, bio, zdjęcie i OP.GG.
- Schemat produkcyjny zmienia wyłącznie Flyway. Deploy robi backup i nie usuwa
  wolumenów PostgreSQL ani mediów.
- Strona główna pokazuje patch notes z `frontend/src/content/releases.ts`.

## Weryfikacja

```bash
cd backend && ./mvnw verify
cd ../frontend && npm run lint && npm run typecheck && npm run build
docker compose config -q
```

Swagger UI: `/swagger-ui.html`, health: `/actuator/health`.