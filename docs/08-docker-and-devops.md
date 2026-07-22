# 08 — Docker i DevOps

## 8.1 Topologia kontenerów

```
                        ┌──────────────────────────────┐
        :80 / :443 ───► │  nginx (reverse proxy + SPA)  │
                        │   /            → statyki React │
                        │   /api         → backend:8080  │
                        │   /media       → wolumen plików│
                        └───────┬───────────────┬────────┘
                                │               │
                       ┌────────▼──────┐   ┌────▼─────────┐
                       │   backend     │   │  (statyki    │
                       │ Spring Boot   │   │   wbudowane) │
                       │   :8080       │   └──────────────┘
                       └───────┬───────┘
                               │
                       ┌───────▼───────┐
                       │  postgres:16  │  (wolumen: pgdata)
                       └───────────────┘

  Wolumeny: pgdata (baza), media (avatary + opcjonalny cache championów)
```

**Jeden origin** (nginx) eliminuje CORS i upraszcza CSP/cookies. Backend nie jest wystawiony
publicznie — tylko przez proxy.

## 8.2 Dockerfile — backend (multi-stage)

```dockerfile
# --- build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# --- runtime ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd -r -u 1001 app && mkdir -p /app/data/media && chown -R app /app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

## 8.3 Dockerfile — frontend (build → nginx)

```dockerfile
# --- build ---
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build            # → /app/dist

# --- runtime ---
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

`nginx.conf` (skrót): SPA fallback `try_files $uri /index.html`; `location /api/ { proxy_pass http://backend:8080; }`;
`location /media/ { alias /media/; }`; nagłówki bezpieczeństwa (CSP, HSTS, X-Content-Type-Options),
gzip/brotli, cache statyków z hashem.

## 8.4 docker-compose.yml (produkcyjny szkielet)

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: driperska
      POSTGRES_USER: driperska
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes: [ pgdata:/var/lib/postgresql/data ]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U driperska"]
      interval: 10s; timeout: 5s; retries: 5

  backend:
    build: ./backend
    depends_on:
      db: { condition: service_healthy }
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/driperska
      SPRING_DATASOURCE_USERNAME: driperska
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
    volumes: [ media:/app/data/media ]
    healthcheck:
      test: ["CMD","curl","-f","http://localhost:8080/actuator/health"]
      interval: 15s; timeout: 5s; retries: 5

  web:
    build: ./frontend
    depends_on: [ backend ]
    ports: [ "80:80" ]
    volumes: [ media:/media:ro ]

volumes:
  pgdata:
  media:
```

`docker-compose.dev.yml` (override): mapuje port bazy na host, montuje kod, włącza
`spring-boot-devtools` i Vite dev server (`5173`) z proxy do backendu — hot reload obu warstw.

## 8.5 Konfiguracja i sekrety
- `.env` (gitignore) — `DB_PASSWORD`, `JWT_SECRET`, ewentualne `DISCORD_WEBHOOK_URL`.
- `.env.example` w repo z pustymi/przykładowymi wartościami.
- Profile Spring: `dev` (lokalnie), `docker` (compose), `prod` (docelowy serwer).
- **Flyway** uruchamia migracje na starcie backendu (`validate` na prod — brak `ddl-auto`).
- Backend startuje dopiero po `service_healthy` bazy.

## 8.6 Repozytorium — układ monorepo

```
driperska-liga/
├── backend/            # Spring Boot (pom.xml, src/…, Dockerfile)
├── frontend/           # React+Vite (package.json, src/…, Dockerfile, nginx.conf)
├── docs/               # ten plan
├── docker-compose.yml
├── docker-compose.dev.yml
├── .env.example
└── .github/workflows/  # CI
```

> **Migracja z obecnego layoutu:** dzisiejsze `pom.xml`/`src` przenosimy do `backend/`. Stare
> assety `static/1sezon/*` i szablony Thymeleaf usuwamy (SSR znika na rzecz SPA). `error404.gif`
> — do kosza lub przeniesienia do frontendu, jeśli chcecie zachować easter egg 404.

## 8.7 CI/CD (GitHub Actions)
- **`ci.yml`** (na PR do `main` / push `driperska-next`):
  1. Backend: `mvn verify` (unit + Testcontainers integracyjne) + ArchUnit.
  2. Wygeneruj `openapi.json`, zwaliduj zgodność typów frontu (`openapi-typescript` diff).
  3. Frontend: `npm ci`, `lint`, `typecheck`, `vitest`, `build`.
  4. Zbuduj oba obrazy Docker (bez pushu) — sanity check Dockerfile'i.
- **`release.yml`** (tag / merge do `main`): build + push obrazów do rejestru (GHCR),
  opcjonalny deploy (SSH + `docker compose pull && up -d`) na serwer docelowy.

## 8.8 Obserwowalność i utrzymanie
- **Actuator**: `/actuator/health` (liveness/readiness), `/actuator/metrics`, `/actuator/info`.
- Logi JSON na prod (łatwe do zbierania); poziomy per pakiet.
- **Backup bazy:** cron `pg_dump` wolumenu `pgdata` (skrypt + wolumen na backupy).
- **Backup mediów:** archiwizacja wolumenu `media` (avatary) razem z bazą.
- Zależności: Dependabot / Renovate na `pom.xml` i `package.json`.
