# 06 — Frontend i design wizualny

## 6.1 Kierunek wizualny — „Hextech Arena"

Nowa tożsamość: **ciemny, premium, esportowy** — inspirowany estetyką Runeterry/Hextech, ale
własny (bez kopiowania brandingu Riot). Splash arty championów jako tło kart, złoty akcent jako
„liga/prestiż", niebieski/czerwony jako kolory drużyn (BLUE vs RED — jak w grze).

**Zasady:**
- **Dark-first.** Głębokie granatowo-czarne tło, treść „świeci" nad nim.
- **Dane to bohater.** Liczby (KDA, PR, LP) wyeksponowane, czytelne, w foncie technicznym.
- **Champion art buduje klimat**, ale nigdy nie przeszkadza w czytaniu (przyciemnienie, gradienty).
- **Złoto = osiągnięcie** (MVP, 1. miejsce, mistrz sezonu). Oszczędnie, żeby nie spowszedniało.
- **Ruch subtelny** — hover na kartach, wejścia list, licznik LP; nic rozpraszającego.

## 6.2 Tokeny motywu (CSS variables)

```css
:root {
  /* Tła — od najgłębszego do wypukłego */
  --bg-0:  #0A0E1A;   /* app background (midnight) */
  --bg-1:  #111827;   /* powierzchnia karty */
  --bg-2:  #1A2333;   /* karta wypukła / hover */
  --border: #263248;

  /* Tekst */
  --text-hi:  #F5F7FA;  /* nagłówki, liczby */
  --text:     #C7D0DE;  /* body */
  --text-lo:  #7C8AA0;  /* podpisy, meta */

  /* Marka / prestiż */
  --gold:      #C8A24B;  /* akcent ligi, MVP, 1. miejsce */
  --gold-soft: #E7C67A;

  /* Drużyny */
  --blue:  #3B82F6;  --blue-bg:  rgba(59,130,246,.12);
  --red:   #EF4444;  --red-bg:   rgba(239,68,68,.12);

  /* Semantyka */
  --win:  #34D399;   --loss: #F87171;
  --pending: #F59E0B; --info: #38BDF8;

  /* Skala PR (0–100) → kolor */
  --pr-low: #6B7280; --pr-mid: #38BDF8; --pr-high: #A78BFA; --pr-elite: #C8A24B;

  /* Promienie / cienie / spacing (skala 4px) */
  --r-sm: 8px; --r-md: 14px; --r-lg: 22px;
  --shadow-card: 0 8px 30px rgba(0,0,0,.35);
  --glow-gold: 0 0 20px rgba(200,162,75,.35);
}
```

> Tryb jasny: opcjonalny, niski priorytet — tożsamość jest ciemna. Jeśli powstanie, przez
> `:root[data-theme="light"]` z przemapowanymi tokenami; komponenty czytają tylko zmienne.

## 6.3 Typografia
- **Display / nagłówki:** `Marcellus` lub `Cinzel` (klasyczny, „epicki" — darmowe, Google Fonts).
- **UI / body:** `Inter`.
- **Liczby / statystyki:** `Rajdhani` lub `Chakra Petch` (techniczny, esportowy) — z `font-variant-numeric: tabular-nums` dla równych kolumn liczb.

## 6.4 Architektura frontendu

```
frontend/
├── src/
│   ├── app/                  # router, layout, providers (Query, Auth, Theme)
│   ├── api/                  # klient fetch + api-types.ts (generowane z OpenAPI) + hooki Query
│   ├── components/           # design system: Button, Card, StatTile, Badge, Avatar, Table…
│   │   ├── champion/         # ChampionIcon, ChampionSplashCard
│   │   ├── match/            # Scoreboard, TeamColumn, MatchCard, DrawBoard
│   │   ├── player/           # PlayerCard, PlayerHeader, PrChart, ChampionPool
│   │   └── ranking/          # RankingTable, RankRow, RankMedal
│   ├── features/             # widoki złożone (kolejka akceptacji, kontrola meczu)
│   ├── pages/                # trasy (patrz niżej)
│   ├── hooks/  lib/  theme/
│   └── main.tsx
├── index.html
├── vite.config.ts  tailwind.config.ts  tsconfig.json
└── Dockerfile  nginx.conf
```

## 6.5 Mapa stron

### Publiczne (bez logowania)
- **`/` Home / Wyniki** — hero z ostatnim meczem (splash MVP w tle), lista kart ostatnich meczów
  (BLUE vs RED, wynik, czas, MVP), skrót top 5 rankingu, licznik sezonu.
- **`/ranking`** — pełna tabela ligowa: miejsce (medale dla 1–3), avatar, LP, W-L, win%, avg PR,
  MVP×n, sparkline formy. Filtr sezonu. Sortowalne kolumny.
- **`/players`** — siatka kart graczy (avatar, nick, main rola, LP, rank).
- **`/players/:id` Profil gracza** — nagłówek (avatar, nick, riotId, rola, ranga, MMR),
  kafle statystyk (LP, gry, win%, KDA, avg PR, MVP), wykres PR w czasie, „champion pool"
  (najczęściej grani z winrate), historia meczów.
- **`/matches/:id` Szczegóły meczu** — scoreboard w stylu ekranu końcowego gry: dwie kolumny
  drużyn, per gracz champion + KDA + CS + dmg + PR, znaczniki MVP/ACE/Penta, „przewidywana
  szansa vs wynik".

### Panel (po zalogowaniu — `/admin`)
- **`/admin/login`** — logowanie.
- **`/admin`** Dashboard — skróty: mecze do akceptacji (badge licznika), aktywne mecze, szybkie akcje.
- **`/admin/matches/new`** Kreator meczu — wybór puli graczy, tryb losowania, sezon.
- **`/admin/matches/:id/control`** Kontrola meczu — **DrawBoard** (losuj / re-roll / potwierdź),
  potem formularz wyników (scoreboard do wpisania, per gracz champion picker + statystyki).
- **`/admin/approvals`** Kolejka akceptacji — lista `RESULTS_SUBMITTED`, wejście w mecz →
  scoreboard + sekcja decyzji (checkbox podpisu, pole podpisu, Zatwierdź / Odeślij do edycji).
- **`/admin/players`** — CRUD graczy + upload avatarów.
- **`/admin/accounts`** (ADMIN) — zarządzanie kontami i rolami.
- **`/admin/seasons`** (ADMIN) — sezony, aktywacja/archiwizacja, przeliczanie rankingu.

## 6.6 Kluczowe komponenty (esport look)

- **`StatTile`** — duża liczba (font techniczny) + etykieta + opcjonalny trend/delta.
- **`PrBadge`** — wartość PR w kolorze wg skali (`--pr-*`), na scoreboardzie i profilu.
- **`ChampionSplashCard`** — karta z przyciemnionym splashem championa w tle (gracz/mecz/hero).
- **`Scoreboard`** — dwie `TeamColumn` (BLUE/RED) z wierszami graczy; nagłówek z wynikiem i czasem.
- **`DrawBoard`** — wizualizacja losowania: dwie drużyny, MMR i szansa, przyciski re-roll/confirm z animacją „tasowania".
- **`RankMedal`** — złoto/srebro/brąz dla top 3.
- **`SignOffPanel`** — sekcja akceptacji: `Checkbox` (Radix) „Potwierdzam poprawność wyników" +
  input podpisu; przycisk „Zatwierdź" disabled dopóki checkbox nie zaznaczony.

## 6.7 Warstwa danych i uwierzytelnianie
- **TanStack Query** dla wszystkich zapytań: cache per klucz (`['ranking', seasonId]`,
  `['match', id]`), invalidacja po mutacjach (np. po `approve` → invaliduj `ranking`, `matches`, profile).
- **Auth:** access token w pamięci (Zustand), refresh w httpOnly cookie (lub secure storage);
  interceptor odświeża token na 401 i ponawia request. Trasy `/admin/*` chronione guardem roli.
- **Optimistic UI** przy re-rollu losowania i drobnych edycjach; twarda re-walidacja po odpowiedzi.
- **Formularz wyników:** React Hook Form + Zod, walidacja 10 uczestników / 5+5 / statystyki ≥ 0
  po stronie klienta (te same reguły co backend), przed wysyłką.

## 6.8 Dostępność i responsywność
- Kontrast tekstu ≥ WCAG AA na ciemnym tle (dobrane tokeny `--text-*`).
- Prymitywy Radix → focus ring, obsługa klawiatury, ARIA za darmo (dialogi, dropdowny, tabsy, checkbox).
- Kolor nigdy jedynym nośnikiem informacji (win/loss ma też ikonę/etykietę; PR ma liczbę, nie tylko barwę).
- Mobile-first: scoreboard zwija się do kart per gracz; tabela rankingu przewija się poziomo w kontenerze.
- Obrazy championów: `loading="lazy"`, `width/height` ustawione (brak layout shift), `alt` z nazwą.

## 6.9 Jakość frontendu
- TypeScript `strict`, ESLint + Prettier, brak `any` w warstwie API (typy z OpenAPI).
- Testy komponentów: Vitest + Testing Library (StatTile, Scoreboard, SignOffPanel — logika disabled).
- E2E krytycznych ścieżek: Playwright (login → utwórz mecz → losuj → wpisz wynik → akceptuj → ranking).
- Lighthouse w CI (performance/dostępność publicznych stron).
