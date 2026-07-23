# Skrypty testowe — draft i losowanie

Zestaw skryptów do ręcznego testowania losowania składu i **draftu turniejowego** (v0.3).
Pomysł: odpalasz w przeglądarce **jedno konto gracza + konto admina**, a te skrypty
symulują pozostałych 9 graczy (boty), żebyś mógł na żywo przeklikać cały przepływ.

## Wymagania
- `jq` i `curl` (`sudo apt install jq curl`)
- Uruchomiony backend + frontend lokalnie. Najprościej:
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build
  ```
  Aplikacja: <http://localhost:8080> · API: <http://localhost:8080/api/v1>

## Konfiguracja (zmienne środowiskowe)
Wszystkie mają sensowne domyślne wartości — nadpisz w razie potrzeby:

| Zmienna | Domyślnie | Opis |
|---|---|---|
| `BASE_URL` | auto | Adres backendu/API. Domyślnie **auto-wykrywany** lokalnie: skrypty sprawdzają `localhost:8080`, `127.0.0.1:8080`, `localhost:8081`, `127.0.0.1:8081` (endpoint `/api/v1/config`) i biorą pierwszy działający. Ustaw ręcznie, by pominąć wykrywanie. |
| `ADMIN_USER` | `admin` | Login admina |
| `ADMIN_PASS` | `changeit123` | Hasło admina (ustaw jak w Twoim `.env` / `APP_ADMIN_PASSWORD`) |
| `HUMAN_PLAYER` | `mromasze` | Nick (lub UUID) Twojego gracza — konto, którym grasz w przeglądarce. `HUMAN_PLAYER=""` = sam boty. |

### Cloudflare Turnstile a logowanie botów
Jeśli lokalny backend ma włączony Turnstile (`turnstileEnabled:true`), curl-owe logowanie botów
zostanie odrzucone (401) — bota nie da się „przeklikać" przez widget. Masz dwie opcje:

- **Bypass (zalecane):** ustaw w lokalnym `.env` backendu `TURNSTILE_BYPASS_TOKEN=local-bot-bypass`
  i zrestartuj backend. Skrypty domyślnie wysyłają dokładnie ten token, więc boty się zalogują,
  a przeglądarka dalej działa normalnie (prawdziwy widget). Wybrałeś inny token? Wyeksportuj go też
  dla skryptów: `export TURNSTILE_TOKEN=twój-token`.
- **Albo** całkiem wyłącz Turnstile lokalnie (puste `TURNSTILE_SITE_KEY`/`TURNSTILE_SECRET`).

⚠️ `TURNSTILE_BYPASS_TOKEN` zostawiaj **pusty na produkcji** (puste = bypass wyłączony). Nie jest
przekazywany w `deploy/docker-compose.prod.yml`, więc na serwer i tak nie trafi.

---

## Pierwsze uruchomienie
```bash
# 1) Utwórz 9 botów-graczy z kontami (Ty jesteś 10.). Zapisuje scripts/test-bots.json
./scripts/seed-test-players.sh

# 2) Upewnij się, że Riot API jest WYŁĄCZONE (akceptacja składu → draft, nie lobby Riot)
./scripts/toggle-riot.sh off
```
W przeglądarce: zaloguj się na swoje konto **gracza** (`mromasze`) oraz — w drugiej
karcie/incognito — na konto **admina**.

---

## Use case 1 — pełny draft (happy path)
```bash
# Stwórz mecz z Tobą + 9 botami (od razu losuje drużyny → głosowanie)
./scripts/create-match.sh

# 5 botów głosuje ZA — 6. głos oddajesz Ty w przeglądarce (panel gracza)
./scripts/bots-vote.sh accept 5

# Skład zatwierdzony → status DRAFT_READY. Rozpocznij draft z panelu admina
# (Kontrola meczu → ▶ Rozpocznij draft) albo skrótem:
./scripts/start-draft.sh

# Uruchom sterownik botów i zostaw działający:
./scripts/draft-bots.sh
```
Teraz w przeglądarce: gdy wypadnie Twoja tura, zaznacz postać i kliknij **Lock in** —
boty czekają na Ciebie i uzupełniają resztę. Jeśli jesteś kapitanem, robisz też ban swojej drużyny.

## Use case 2 — draft w pełni automatyczny (bez Ciebie)
```bash
BOT_COUNT=10 ./scripts/seed-test-players.sh     # 10 botów
./scripts/toggle-riot.sh off
HUMAN_PLAYER="" ./scripts/create-match.sh        # 10 botów (bez Ciebie)
./scripts/bots-vote.sh accept 6                  # zatwierdza skład (DRAFT_READY)
./scripts/start-draft.sh                         # admin rozpoczyna draft
./scripts/draft-bots.sh                          # boty rozegrają cały draft
```

## Use case 3 — bug głosowania (odświeżenie / logowanie w trakcie)
```bash
./scripts/create-match.sh
./scripts/bots-vote.sh accept 3                  # trochę głosów, bez zatwierdzenia
```
W przeglądarce (panel gracza): **odśwież stronę** albo wyloguj się i zaloguj ponownie —
składy i przyciski głosowania powinny pojawić się od razu, wraz z odliczaniem 60 s.

## Use case 4 — ponowne losowanie (reroll)
```bash
./scripts/create-match.sh
./scripts/bots-vote.sh reject 5                  # 5 głosów PRZECIW → nowe drużyny (nowa runda)
```

## Use case 5 — zamiany po draftcie (pozycja / postać)
Po zakończonym draftcie (status „Draft zakończony"):
```bash
./scripts/bots-accept-swaps.sh                   # zostaw działające
```
W przeglądarce kliknij strzałkę ⇄ przy koledze-bocie z Twojej drużyny → wybierz
„Zamień pozycję" lub „Zamień postać". Bot automatycznie zaakceptuje.

## Use case 6 — timer / auto-losowanie
W trakcie draftu po prostu **nie wykonuj swojego ruchu**. Po 30 s licznika backend przypisze
Ci losową dostępną postać (albo losowy ban, jeśli jesteś kapitanem) i przejdzie dalej.

## Reset między testami
```bash
./scripts/reset-matches.sh                       # anuluje wszystkie niezakończone mecze
```
Admin może też w panelu meczu użyć **„Reset draftu"**, żeby rozegrać draft od nowa bez tworzenia meczu.

---

## Spis skryptów
| Skrypt | Do czego |
|---|---|
| `seed-test-players.sh` | Tworzy boty-graczy z kontami (`test-bots.json`) |
| `toggle-riot.sh [show\|on\|off]` | Podgląd/zmiana przełącznika Riot API |
| `create-match.sh` | Admin tworzy mecz (Ty + boty) i losuje drużyny |
| `bots-vote.sh [accept\|reject] [n]` | Boty głosują nad składem |
| `draft-bots.sh` | Prowadzi draft botów (bany/picki), czeka na Twoje tury |
| `bots-accept-swaps.sh` | Boty akceptują Twoje prośby o zamianę |
| `reset-matches.sh` | Anuluje niezakończone mecze |
| `accept-squad.sh` / `decline-squad.sh` | Starsze skróty głosowania (tryb Riot) |
