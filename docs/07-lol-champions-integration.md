# 07 — Integracja z championami LoL (Data Dragon)

## 7.1 Źródło danych — Riot Data Dragon (ddragon)

Statyczne dane gry (championy, obrazy, czary) są dostępne **bez klucza API** przez CDN Riot
„Data Dragon". To wystarcza w 100% do naszego przypadku (statystyki wpisujemy ręcznie — nie
pobieramy prawdziwych meczów).

- **Wersje (patche):** `https://ddragon.leagueoflegends.com/api/versions.json` → tablica wersji,
  `[0]` = najnowsza (np. `"14.13.1"`).
- **Lista championów (dla wersji + języka):**
  `https://ddragon.leagueoflegends.com/cdn/{ver}/data/pl_PL/champion.json`
  (język `pl_PL` da polskie tytuły; fallback `en_US`).
- **Obrazy:**
  - Ikona kwadratowa: `https://ddragon.leagueoflegends.com/cdn/{ver}/img/champion/{slug}.png`
  - Splash (pełny): `https://ddragon.leagueoflegends.com/cdn/img/champion/splash/{slug}_0.jpg`
  - Loading: `https://ddragon.leagueoflegends.com/cdn/img/champion/loading/{slug}_0.jpg`

`{slug}` to Riot `id` (np. `Aatrox`, `MonkeyKing` dla Wukonga), `{key}` to liczbowe id (np. 266).

## 7.2 Model i synchronizacja

Encja `Champion` ([dok 02](02-domain-and-database.md)) trzyma metadane + pełne URL-e obrazów oraz
`ddragonVersion`. Synchronizacja:

- **`ChampionSyncService`** (`champion.application`):
  1. Pobierz `versions.json`, weź najnowszą (lub podaną w żądaniu).
  2. Pobierz `champion.json` dla tej wersji.
  3. Upsert każdego championa (po `key`), zbuduj i zapisz URL-e obrazów, ustaw `ddragonVersion`.
- **Wyzwalacze:**
  - Ręcznie: `POST /api/v1/champions/sync` (ADMIN), body opcjonalne `{ "version": "14.13.1" }`.
  - Automatycznie: `@Scheduled` raz na dobę — jeśli pojawił się nowy patch, resync.
  - Na starcie: jeśli tabela `champion` pusta → jednorazowy bootstrap sync (idempotentny).
- Klient HTTP: **`RestClient`** z timeoutami i retry; parsowanie Jacksonem do DTO ddragon.

## 7.3 Strategia obrazów

- **Domyślnie:** przechowujemy URL-e CDN Riot i serwujemy je bezpośrednio (ikony i splashe są
  cache'owane przez CDN, wersjonowane patchem — stabilne). Zero kosztu storage.
- **Opcjonalny proxy/cache** (`/media/champions/...` przez nginx lub endpoint backendu) — gdyby
  zależało na braku zależności od zewnętrznego CDN w runtime albo na spójnym origin/CSP. Ikony są
  małe; cache dyskowy jest tani. Do włączenia flagą, nie na start.
- Frontend: `ChampionIcon` (ikona kwadratowa, np. na scoreboardzie), `ChampionSplashCard`
  (splash jako tło hero/karty z gradientem przyciemniającym).

## 7.4 Powiązanie ze statystykami

- `MatchParticipant.championId` → FK do `Champion.id` (liczbowy `key`).
- Frontend przy wpisywaniu wyników: **ChampionPicker** — searchowalny select z ikonami, źródło
  `GET /champions` (cache w TanStack Query, dane rzadko się zmieniają).
- Walidacja: `championId` musi istnieć w tabeli `champion` (w przeciwnym razie 422).

## 7.5 Wykorzystanie w profilu gracza
- **Champion pool:** agregacja z `MatchParticipant` — najczęściej grani championi gracza, z liczbą
  gier, winrate i średnim PR na danym championie (`GET /players/{id}/stats`).
- **Tło profilu:** splash najczęściej granego („main") championa jako subtelne tło nagłówka.

## 7.6 Odporność
- Sync nie może wywrócić aplikacji — błąd CDN → log + zachowanie poprzednich danych (championy w
  bazie zostają). Nowy patch nie usuwa starych championów (tylko upsert), więc historia meczów z
  championem usuniętym z gry (rzadkie) się nie psuje.
- Język: preferuj `pl_PL`, fallback `en_US`, gdy Riot nie ma tłumaczenia pola.
- Wersje trzymamy w konfiguracji (`lol.ddragon.locale`, `lol.ddragon.baseUrl`) — łatwa podmiana/mokowanie w testach.

## 7.7 (Opcjonalnie, przyszłość) Riot Match-V5 API
Gdyby kiedyś chcieć **automatyczny import statystyk** zamiast ręcznego wpisywania: wymaga
produkcyjnego klucza API Riot + zmapowania graczy na PUUID (z `riotId`). To osobny, duży temat
(rate limity, zgodność z regulaminem Riot). Poza zakresem v2 — architektura (`riotId` na graczu,
`patch` na meczu) jest już na to przygotowana.
