# Driperska Liga v0.5.0

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

## Najważniejsze funkcje

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
- Strona główna pokazuje patch notes oraz przygaszone klipy najlepszych zagrań z trwałego magazynu mediów.
- Panel admina udostępnia listę wszystkich meczów, ich edycję i ponowne wysyłanie kart wyników na Discord.
- Zaplanowanie meczu wysyła na Discord ogłoszenie z @everyone (kanał `DISCORD_ANNOUNCE_CHANNEL_ID`)
  oraz kartę głosowania z przyciskami „Będę / Nie będę / Może” (kanał `DISCORD_VOTE_CHANNEL_ID`).
  Kliknięcia kont Discord połączonych z graczem liczą się jako RSVP w systemie (gateway JDA,
  tylko odbiór interakcji — wysyłka wiadomości pozostaje po REST).
- Punktacja LP na stronie meczu jest zwijana: widać sumę LP gracza, a „Szczegóły” rozwijają
  składniki LP z przelicznikiem oraz tabelę „jak powstało PR” (metryka, wartość, średnia meczu,
  norma, waga roli, punkty PR).
- Listy meczów pokazują i sortują po dacie faktycznego startu gry (`startedAt`) — edycja
  lub ponowna akceptacja wyniku nie przesuwa meczu w listach.
- Admin może nadać graczowi uprawnienie **moderatora** (zakładka „Gracze”). Moderator dostaje w swojej
  strefie zakładkę „Wnioski”: wprowadza rozegrany mecz (skład, strony, role, data) i jego statystyki —
  ręcznie albo ze zrzutów ekranu przez AI. Wniosek trafia do kolejki akceptacji admina, jest edytowalny
  do momentu zatwierdzenia i nie uruchamia losowania, draftu, lobby Riot ani ogłoszeń na Discordzie.
  Bot informuje o nowym wniosku na kanale `DISCORD_MODERATION_CHANNEL_ID` (puste = kanał ogłoszeń).

Tournament API wymaga produkcyjnego klucza z przyznanym dostępem do produktu Tournament.
Callback `RIOT_CALLBACK_URL` musi być publicznym adresem HTTPS. Sekretów nie zapisuj
w repozytorium; podawaj je wyłącznie przez plik `.env` na serwerze.

## Weryfikacja

```bash
cd backend && ./mvnw verify
cd ../frontend && npm run lint && npm run typecheck && npm run build
docker compose config -q
```

Testy integracyjne (klasy `*IT`) trzeba uruchomić osobno — projekt nie ma wtyczki failsafe,
a domyślne wzorce surefire ich nie obejmują:

```bash
cd backend && ./mvnw '-Dtest=*IT' test
```

Swagger UI: `/swagger-ui.html`, health: `/actuator/health`.