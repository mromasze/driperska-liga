# Changelog

Wszystkie istotne zmiany Driperskiej Ligi są opisywane tutaj oraz w
`frontend/src/content/releases.ts`, który zasila patch notes na stronie głównej.

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