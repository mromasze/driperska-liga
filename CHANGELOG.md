# Changelog

Wszystkie istotne zmiany Driperskiej Ligi są opisywane tutaj oraz w
`frontend/src/content/releases.ts`, który zasila patch notes na stronie głównej.

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