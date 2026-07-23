# Changelog

Wszystkie istotne zmiany Driperskiej Ligi są opisywane tutaj oraz w
`frontend/src/content/releases.ts`, który zasila patch notes na stronie głównej.

## v0.3.1 — 2026-07-23

- Zagrywki w tle strony głównej przenikają się płynnie (fade in/out ~0,9 s): klip zaczyna się od zera, a ~1 s przed końcem wygasza się przed przełączeniem na kolejny. Respektuje „prefers-reduced-motion”.
- Nowa sekcja „Opinie graczy” na stronie meczu (widoczna dla zalogowanych — endpoint `GET /api/v1/matches/{id}/feedback-summary`): karta gracza z największą liczbą plusów oraz karta z największą liczbą minusów, każda z automatycznie przewijanym sliderem anonimowych komentarzy o jego grze (pozytywne na zielono, negatywne na czerwono). Backend agreguje `match_feedback` (liczby +/− oraz notatki przypięte do wyróżnionego/ocenionego gracza).

## v0.3.0 — 2026-07-23

- Nowość — **draft turniejowy** rozgrywany po zaakceptowaniu składu (gdy wsparcie Riot API jest wyłączone). Kolejność banów i picków jest kanoniczna dla LoL: bany B‑R‑B‑R‑B‑R, picki B‑R‑R‑B‑B‑R, bany R‑B‑R‑B, picki R‑B‑B‑R (5 banów i 5 pików na drużynę). Bany wykonuje losowo wyznaczony kapitan drużyny; każdy gracz sam blokuje swoją postać w swojej turze; postać zbanowana lub wybrana jest niedostępna dla obu drużyn.
- Każdy krok draftu ma licznik 30 s (konfigurowalny `DRAFT_STEP_SECONDS`) z odliczaniem na żywo; po upływie czasu przypisywana jest losowa dostępna postać. Egzekwuje to `DraftScheduler`.
- Po zakończeniu draftu gracze mogą w obrębie drużyny zamienić się **pozycją** lub **postacią** — jedna osoba wysyła prośbę (strzałka przy graczu), druga akceptuje. Admin może **zresetować** cały draft z panelu kontroli meczu.
- Nowy, trwały przełącznik **„Wsparcie Riot API”** w panelu (`/admin/settings`, tabela `app_setting`). Wyłączony (domyślnie) → `DrawService.confirm` startuje draft wewnętrzny zamiast tworzyć lobby Riot; włączony → zachowanie jak dotychczas. Naprawia to pokazywanie niedziałającego kodu Riot, gdy nie mamy dostępu do Tournament API.
- Poprawka głosowania: strumień SSE wysyła teraz aktualny stan lobby natychmiast po połączeniu, więc odświeżenie strony lub zalogowanie w trakcie głosowania od razu pokazuje składy i przyciski głosowania. `/draw-lobby/active` zwraca `null` zamiast pustej odpowiedzi (React Query nie akceptuje `undefined`).
- Timer głosowania nad składem wydłużony 30 → 60 s (`DRAW_AUTO_CONFIRM_SECONDS`), a `voteDeadline` jest wystawiany do klienta, który pokazuje odliczanie każdemu uczestnikowi.
- Nowe statusy meczu `DRAFTING` i `DRAFTED`; ręczne rozpoczęcie meczu działa też po draftcie. Migracja `V5` dodaje tabele `app_setting` i `match_draft` (stan draftu trzymany jako JSON).
- Draft: stała kolejność miejsc w drużynie (losowo obsadzane) — od góry TOP, JUNGLE, MID, ADC, SUPPORT; kapitanem (i banującym) jest gracz na miejscu TOP, a picki lecą po kolei z góry na dół do konkretnego gracza (DTO `DraftView.currentPlayerId`, front podświetla jego wiersz). Zamiany pozycji/postaci po draftcie realnie przestawiają skład.
- OCR: dodano atlas referencyjny championów wysyłany do modelu wizyjnego + dziennik analizy; obrazy są skalowane przed wysyłką, żeby nie przekraczać limitu requestu Ollamy (fix HTTP 400 „body too large”), a odpowiedź modelu jest parsowana tolerancyjnie (zdejmowanie ```json```).
- Patch notes na Discord: nowy przycisk w panelu (Ustawienia) → `POST /api/v1/admin/patch-notes/announce`. Backend renderuje obrazek (Java2D, styl karty wyniku) z wybranej wersji z changelogu i wysyła na kanał `DISCORD_PATCH_CHANNEL_ID` (fallback do kanału ogłoszeń) z pingiem @everyone.
- Panel gracza: osobna zakładka „Ocena” na ankiety pomeczowe, z licznikiem meczów do oceny (jak powiadomienie). Po `RESULTS_SUBMITTED` mecz w panelu gracza pokazuje się jako zakończony (oczekiwanie na zamknięcie przez admina), bez draftu/lobby.
- Panel admina: nawigacja pogrupowana w sekcje — „Mecze” zbiera listę meczów, nowy mecz, plan meczów i akceptacje. Zatwierdzone mecze natychmiast znikają z kolejki akceptacji (element chowany, gdy status ≠ `RESULTS_SUBMITTED`).
- Wyłączone cache’owanie: nginx zwraca `Cache-Control: no-cache` dla powłoki SPA (hashowane `/assets` dalej cache’owane na rok), a React Query odświeża dane na wejściu/fokusie (`staleTime 0`). Koniec z Ctrl+F5.

## v0.2.8 — 2026-07-22

- Panel gracza podzielony na zakładki: „Dashboard” (losowanie/gra, potwierdzanie obecności na nadchodzące mecze i ankieta oceny po meczu) oraz „Profil i ustawienia” (edycja profilu, ulubieni bohaterowie, zdjęcie oraz zmiana hasła) — mniej przewijania i wyraźny podział na to, co „na dziś”, i ustawienia konta.
- Na publicznym profilu gracza pokazują się jego ulubieni bohaterowie (do 5, ustawiani w panelu gracza).
- Naprawiona zmiana zdjęcia profilowego: dało się je ustawić tylko raz, a kolejne wgranie nie było widoczne, bo plik zapisywał się zawsze pod tą samą nazwą (`<id>.png`) i przeglądarka serwowała starą wersję z cache. Teraz stary plik jest usuwany, a nowy zapisywany pod unikalną nazwą — URL zmienia się przy każdym wgraniu, więc zdjęcie odświeża się natychmiast.
- Naprawione wgrywanie „Zagrywek” (klipów) i powtórek — kończyło się błędem „Access Denied” w logach. Duży upload multipart Tomcat obsługuje przez re-dispatch ASYNC, a filtr JWT (jako `OncePerRequestFilter`) pomija dyspozycje async, więc Spring Security ponownie autoryzował żądanie bez tokenu i odrzucał je. Autoryzacja jest teraz wykonywana tylko na pierwotnej dyspozycji `REQUEST`; dyspozycje `ASYNC`/`FORWARD`/`ERROR` są przepuszczane.
- Panel admina: lista wszystkich meczów jest teraz zwijana — każdy mecz to rozwijany element (dropdown) z podsumowaniem w nagłówku i akcjami (udostępnij na Discord, otwórz/edytuj) po rozwinięciu; lista jest zwarta i czytelna, szczególnie na telefonie.
- Poprawione wyświetlanie panelu admina na telefonach: pasek nawigacji zastąpiony chowanym menu (hamburger) z pełną listą zakładek, licznikiem oczekujących akceptacji i wylogowaniem; nagłówek pokazuje nazwę bieżącej sekcji i jest przyklejony u góry.
- Podczas głosowania nad składem każdy widzi teraz, kto jak zagłosował — lista wszystkich graczy w lobby z ich decyzją (Gramy / Losuj ponownie / czeka), aktualizowana na żywo.
- Naprawione wgrywanie „Zagrywek” i powtórek kończące się błędem 413/502 (request nie docierał do backendu). Limit rozmiaru w v0.2.7 podniesiono tylko w wewnętrznym nginx kontenera; zewnętrzny nginx hosta (`deploy/nginx/driperska.pl.conf`) nadal miał `client_max_body_size 6m` i odrzucał duże pliki jako pierwszy. Dodano dedykowaną lokację `/api/` z limitem 512 MB i `proxy_request_buffering off`. UWAGA: konfigurację nginx hosta trzeba wgrać na serwer ręcznie i przeładować nginx (`nginx -t && systemctl reload nginx`).
- Naprawiony build Dockera w CI (padał ~11 min na `apt-get install gosu` — „Connection failed” z archiwum Ubuntu). `gosu` jest teraz kopiowane jako statyczny plik z oficjalnego obrazu `tianon/gosu` (Docker Hub), a instalacja fontów ma ponawianie (`Acquire::Retries=5`).
- Naprawione „nie ładują się dostępy Cloudflare i AI” — produkcyjny `deploy/docker-compose.prod.yml` nie przekazywał do kontenera zmiennych `TURNSTILE_SITE_KEY`, `TURNSTILE_SECRET`, `OLLAMA_BASE_URL`, `OLLAMA_API_KEY`, `OLLAMA_VISION_MODEL` ani `DISCORD_ANNOUNCE_CHANNEL_ID`, więc wartości z `.env` na serwerze nie docierały do aplikacji. Dodano je; `start_period` backendu podniesiono do 90 s (start zajmuje ~32 s).
- Wydłużona sesja logowania (hotfix — koniec wylogowywania po kilku minutach). Access token JWT ważny domyślnie 720 min (12 h) zamiast 15 min, refresh token 30 dni zamiast 7. Oba parametry są teraz konfigurowalne z `.env` przez `JWT_ACCESS_TOKEN_MINUTES` i `JWT_REFRESH_TOKEN_DAYS` (przekazywane w obu plikach compose).

## v0.2.7 — 2026-07-22

- Obrazek wyniku udostępniany na Discord przeprojektowany tak, by odwzorować scoreboard z aplikacji: ciemne panele drużyn, ikony bohaterów, KDA (śmierci na czerwono), CS, kolorowe „pigułki” PR, korona MVP i zwycięzcy, wynik w nagłówku.
- Naprawiona zakładka „Mecze" w panelu admina — lista wszystkich meczów bez filtra statusu ładowała się w nieskończoność (brak `@EntityGraph` na `findAll` powodował błąd lazy-init przy budowaniu podsumowania).
- Pozycja zawodnika jest teraz wpisywana przy wyniku (faktycznie grana pozycja, a nie ulubiona): edytowalny wybór roli w formularzu wyników; OCR ze screenshotów również próbuje odczytać pozycję.
- Podsumowanie meczu i karta na Discord zawsze sortują graczy w kolejności: TOP, JUNGLE, MID, BOT, SUPPORT.
- Post z wynikiem na Discordzie zawiera link do strony szczegółów meczu (`/matches/{id}`).
- W panelu gracza zakończony mecz znika (aktywne są tylko stany w toku); po zatwierdzeniu zostaje sama ankieta oceny, z nagłówkiem informującym o dacie meczu i składach obu drużyn.
- Naprawione wgrywanie klipów („Zagrywki") i powtórek na produkcji: limit multipart podniesiony do 512 MB, `server.tomcat.max-swallow-size=-1` (Tomcat domyka odrzucone zbyt duże uploady zamiast resetować połączenie, co za nginx dawało 502 i stack trace), `client_max_body_size 512m` + `proxy_request_buffering off` w nginx, klip do 400 MB, oraz czytelny błąd 413 zamiast nieobsłużonego wyjątku przy przekroczeniu limitu.
- Utwardzenie strumienia SSE (`/draw-lobby/stream`): emitter domykany w `onTimeout`, więc wygaśnięcie połączenia nie powoduje `AsyncRequestTimeoutException` w logach.
- Wszystkie daty i godziny wyświetlane w formacie 24-godzinnym i wymuszonej strefie czasowej Europe/Warsaw (niezależnie od strefy przeglądarki).

## v0.2.6 — 2026-07-22

- Po zakończonym meczu gracz nie widzi już podsumowania statystyk — profil jest zwolniony, a zamiast tego pojawia się opcjonalna ankieta oceny meczu: jeden upvote i jeden downvote dla wybranych uczestników plus krótka notatka z uzasadnieniem (kto zagrał źle i dlaczego). Ocena jest edytowalna; nie można ocenić samego siebie ani dać tej samej osobie plusa i minusa.
- Zmiana hasła w ustawieniach konta gracza (weryfikacja aktualnego hasła, min. 8 znaków).
- Cloudflare Turnstile na formularzu logowania — weryfikacja tokenu po stronie serwera (`app.turnstile.*` / `TURNSTILE_SITE_KEY` + `TURNSTILE_SECRET`); klucz publiczny wystawiany przez `GET /api/v1/config`. Zaktualizowano CSP nginx o `challenges.cloudflare.com`.

## v0.2.5 — 2026-07-22

- Naprawiony błąd udostępniania wyniku na Discord (`NoClassDefFoundError: org/reactivestreams/Publisher`) — multipart bez zależności reaktywnych.
- Zwiększony limit wielkości uploadu: nginx `client_max_body_size 128m` + Spring multipart 128 MB (koniec „request too large" przy powtórkach/screenach ~10 MB).
- Zakładka „Mecze" (panel admina): tabela wszystkich meczów z filtrem statusu, edycją i udostępnianiem karty na Discord z listy.
- Planowanie meczów: termin + notatka, zbiorowe ogłoszenie na Discord (@everyone) z linkiem do potwierdzenia obecności; gracze RSVP (Będę/Może/Nie) w panelu, admin widzi listę potwierdzeń. Konfiguracja kanału: `DISCORD_ANNOUNCE_CHANNEL_ID` (fallback do kanału wyników).
- Wyszukiwarka bohaterów po nazwie w edycji profilu gracza.
- Zakładka „Diagnostyka": lekkie testy połączenia z Ollama, Discordem i Riot API (bez zmiany danych).

## v0.2.4 — 2026-07-22

Tymczasowe uzupełnianie wyników ze zrzutów ekranu (do czasu produkcyjnego dostępu do Riot Tournament API):

- Wczytywanie statystyk z ekranu podsumowania meczu LoL przez model wizyjny Ollama Cloud — formularz wyniku wypełnia się automatycznie.
- Obsługa wielu zrzutów w jednym żądaniu (np. osobne zakładki KDA / obrażenia / wizja); dane są scalane po nazwie gracza.
- Dopasowanie odczytanych graczy do składu meczu po nazwie w grze / Riot ID oraz championów po nazwie; niedopasowania są zgłaszane, a admin sprawdza i poprawia przed wysłaniem.
- Konfiguracja przez `OLLAMA_API_KEY` / `OLLAMA_BASE_URL` / `OLLAMA_VISION_MODEL` (domyślnie `https://ollama.com`).

## v0.2.3 — 2026-07-22

Automatyzacja wdrożeń, klipy i pełne zarządzanie meczami:

- CI buduje i pakuje obrazy Dockera, a deploy po udanym `main` przesyła gotową paczkę i uruchamia ją bez kompilacji na serwerze; serwerowe `.env` i `docker-compose.yml` pozostają nietknięte.
- Panel „Zagrywki” przyjmuje MP4/WebM i odtwarza klipy jako przygaszone tło strony głównej; pliki zostają w trwałym woluminie mediów.
- Naprawione generowanie kart wyników w obrazie produkcyjnym (fonty Java2D), szczegółowe komunikaty błędów Discord i logowanie wyjątków 500/503 ze stack trace.
- Nowy widok wszystkich meczów z filtrowaniem, wejściem do edycji dowolnego wyniku i ponownym udostępnianiem na Discordzie.

## v0.2.2 — 2026-07-22

Wygoda panelu admina i lepsza diagnostyka Discord:

- Mecz w toku nie znika po odświeżeniu — na pulpicie doszła sekcja „Mecze w przygotowaniu” (TEAMS_DRAWN/LOBBY_READY) z linkiem powrotnym do kontroli meczu.
- Przycisk „Anuluj mecz” w panelu kontroli (dla stanów innych niż zaakceptowany/anulowany).
- Przycisk udostępniania obrazka z wynikiem na Discord również w kolejce akceptacji.
- Dokładne komunikaty błędów wysyłki DM Discord: brak „Server Members Intent”, blokada DM po stronie odbiorcy (kod 50007), nieznany użytkownik — z podpowiedzią, by użyć numerycznego Discord User ID.
- Ostrzeżenie na ekranie lobby (gracz i admin), że kod testowy `STUB` nie jest grywalny w kliencie bez produkcyjnego dostępu Tournament API.
- Hosting pliku weryfikacyjnego `riot.txt` w korzeniu domeny na potrzeby rejestracji Tournament API.

## v0.2.1 — 2026-07-21

Poprawki błędów i uzupełnienia po v0.2:

- Riot: użycie właściwego klastra `americas` i `tournament-stub-v5` dla kluczy deweloperskich (koniec błędów 403); usunięto zbędne `summoner-v4`.
- Region docelowy ustawiony na EUNE.
- Naprawione wgrywanie avatarów (uprawnienia woluminu media w Dockerze — entrypoint chown).
- Panel admina: edycja danych gracza (Riot ID, Discord, role, imię).
- Ręczne rozpoczęcie meczu bez Riot oraz edycja już rozegranych (zatwierdzonych) meczów.
- Auto-akceptacja składu po 30 s (konfigurowalne) z limitem prób; podgląd głosowania na żywo dla admina.
- Oceny PR/LP liczone i pokazywane na podsumowaniu zaraz po wpisaniu wyniku.
- Udostępnianie obrazka wyniku na Discord i wgrywanie powtórki `.rofl` do meczu.
- Podpowiedzi statystyk w formularzu wyniku, wyjaśnienie punktacji i osobna zakładka Patch Notes.
- Tryb testowy `RIOT_MOCK` do pełnego przejścia flow bez realnego Riot.

## v0.2 — 2026-07-21

- Integracja Tournament API v5: provider, turniej, jednorazowy kod lobby i lista PUUID.
- Widok strony BLUE/RED oraz kodu lobby dla uczestników; ręczne rozpoczęcie przez admina.
- Status obecności na podstawie zdarzeń lobby oraz wymiana gracza przed startem z nowym kodem.
- Callback Riot, automatyczny import Match-v5 do kolejki akceptacji i ręczny fallback po kodzie.
- Informacja dla graczy, że wynik oczekuje na akceptację administratora.
- Obowiązkowa nazwa Discord, automatyczny DM z danymi logowania, kopiowanie i ponowna wysyłka.
- Wszystkie zmiany schematu scalone w migracji bazowej dla pustej bazy.

## v0.1 — 2026-07-21

- Konta graczy tworzone razem z profilem: nick jako login, losowe hasło i gotowa wiadomość na DM.
- Strefa gracza z edycją roli, championów, zdjęcia profilowego, bio, Riot ID i OP.GG.
- Losowanie drużyn i stron w czasie rzeczywistym.
- Głosowanie uczestników: 6 głosów „za” uruchamia mecz, 5 „przeciw” uruchamia kolejną rundę.
- Flyway z idempotentną migracją bazową, backup przed deployem i zachowanie wolumenów.
- Deployment na współdzielonym serwerze przez port loopback i host nginx.