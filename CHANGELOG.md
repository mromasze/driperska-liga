# Changelog

Wszystkie istotne zmiany Driperskiej Ligi są opisywane tutaj oraz w
`frontend/src/content/releases.ts`, który zasila patch notes na stronie głównej.

## v0.4.3 — 2026-07-28

- **Poprawiono wybór MVP przez PR v2.** Historia pozycji nie może już przejąć 100% oceny po 20 próbkach. Jej udział rośnie maksymalnie do 50%, a co najmniej połowa PR zawsze wynika z bezpośredniego porównania graczy tej samej roli w bieżącym meczu. Widoczny punkt odniesienia pokazuje teraz połączenie średniej z meczu i mediany historii.
- **Aktywność ma znaczenie w klasyfikacji.** Do stabilizowanej średniej dochodzi +0,10 punktu rankingowego za każdy rozegrany mecz, maksymalnie +2,00 po 20 meczach. Dobra średnia pozostaje najważniejsza, ale dalsza gra realnie poprawia pozycję i ogranicza opłacalność „zamrożenia” wyniku.
- **Najlepsze KDA w meczu daje +1 LP.** Przy identycznym najlepszym wyniku bonus jest współdzielony.
- **Perfect KDA daje +1 LP** za mecz bez śmierci, o ile gracz ma przynajmniej jedno zabójstwo lub asystę; wynik 0/0/0 nie kwalifikuje się.
- Oba bonusy KDA łączą się ze sobą oraz z MVP albo ACE. Samo MVP i ACE nadal nie stackują się dla tej samej osoby.
- Rozpiska meczu, historia gracza i scoreboard pokazują nowe wyróżnienia, a tabela rozdziela bazowy wynik od bonusu aktywności.
- Wszystkie historyczne sezony zostaną automatycznie i jednokrotnie przeliczone do nowych zasad po wdrożeniu.
- Patch notes na Discordzie wymagają teraz jawnie ustawionego, dedykowanego `DISCORD_PATCH_CHANNEL_ID` i nie wpadają już awaryjnie na kanał ogłoszeń.
- Dźwięk draftu odblokowuje się po pierwszej interakcji w całej aplikacji; aktywny gracz dostaje pewne powiadomienie o swojej turze oraz jednorazowe przypomnienie po przekroczeniu 10 sekund.

## v0.4.2 — 2026-07-28

- Naprawiono błąd 500 w widokach gracza korzystających z meczów zapisanych w jego puli (m.in. aktywne lobby i mecze do oceny). PostgreSQL odrzucał wygenerowane zapytanie `SELECT DISTINCT` z sortowaniem po `coalesce(started_at, created_at)`, ponieważ wyrażenie sortujące nie występowało w liście `SELECT`.
- Zapytania `findForPlayerAndStatus` i `findForPlayerAndStatuses` filtrują teraz pulę przez JPQL `MEMBER OF`, bez zbędnego `JOIN + DISTINCT`; kolejność po faktycznej dacie startu pozostaje bez zmian.
- Dodano test regresyjny wykonujący oba warianty zapytania i sprawdzający kolejność wyników.
- Naprawiono wywracanie deploya 0.4.1 podczas jednorazowego przeliczania rankingu. Statystyki sezonu są teraz usuwane natychmiastowym bulk delete przed odbudową, więc Hibernate nie próbuje wstawić nowych rekordów przed usunięciem starych i nie narusza `ux_player_season`. Ponowne uruchomienie po nieudanym deployu jest bezpieczne.

## v0.4.1 — 2026-07-28

- **Nowy PR v2.** Ocena występu korzysta z percentyli wcześniejszych graczy na tej samej pozycji (kroczące 60 próbek / 30 meczów). Przez pierwsze 20 próbek roli historia jest płynnie łączona z porównaniem bezpośrednich rywali w meczu. Wagi KDA pozostały bez zmian.
- Gold share zastąpiony efektywnością `damage / gold`: ranking premiuje wykorzystanie zasobów, nie samo przejmowanie złota. Pozostałe składniki to KDA, KP, CS/min, damage/min i vision/min.
- **Nowe LP:** wygrana +10, przegrana +4; występ jest liczony progami PR (`<35: -2`, `35–44: -1`, `45–54: 0`, `55–64: +1`, `65–74: +2`, `75+: +3`).
- MVP daje +3. ACE daje +2 najlepszemu przegranemu dopiero od PR 60. MVP i ACE mogą być współdzielone przy remisie; jeśli ta sama osoba jest MVP i ACE, oba tytuły są widoczne, ale bonusy nie stackują się.
- Penta, quadra i flawless pozostają osiągnięciami, ale nie przyznają LP i nie faworyzują już konkretnych ról w tabeli.
- **Ranking sezonu nie jest już sortowany po sumie LP.** Główny wynik to skorygowana średnia `(suma LP + 5 × średnia ligi) / (mecze + 5)`. Do klasyfikacji potrzeba 5 meczów; wcześniej wynik jest prowizoryczny. Suma LP zostaje jako licznik aktywności.
- MMR używa czystego Elo bez mnożnika PR — dobry gracz przegranej drużyny nie traci już więcej MMR od słabego.
- ACE jest trwale zapisywane na meczu i agregowane na profilu oraz w tabeli. Migracja `V6` dodaje `is_ace` i `ace_count`.
- Przy pierwszym uruchomieniu 0.4.1 wszystkie sezony są automatycznie i jednokrotnie przeliczane chronologicznie do PR/LP v2.
- `Season.scoringConfigJson` jest teraz faktycznie odczytywany; niepoprawna konfiguracja bezpiecznie wraca do domyślnych zasad v2.
- Zaktualizowano stronę główną, rozpiski punktów meczu, profile, tabelę rankingu i dokumentację zasad.

## v0.4.0 — 2026-07-28

- **Naprawiony błąd 500 na wszystkich listach meczów.** `GET /api/v1/matches` zwracał 500 dla każdego filtra (i bez filtra), co wywalało Pulpit, Akceptacje, listę meczów i licznik w menu. Przyczyna: dodane w v0.3.2 sortowanie `startedAt DESC NULLS LAST` przechodziło przez Criteria API (zapytania pochodne Spring Data), a Hibernate 6 odrzuca tam sterowanie kolejnością nulli — `UnsupportedOperationException: Applying Null Precedence using Criteria Queries is not yet supported`. Kolejność jest teraz wpisana w JPQL (`order by coalesce(m.startedAt, m.createdAt) desc, m.createdAt desc`) w `MatchRepository.LIST_ORDER`, a kontroler nie buduje już `Sort`. Dodany test regresyjny `MatchListingIT` przechodzi po wszystkich statusach.
- **Draft na pełny ekran.** Plansza draftu zajmuje cały widok: obie drużyny, oba pasy banów, zegar i pula postaci są widoczne jednocześnie, bez przewijania strony — przewija się tylko siatka postaci we własnej ramce. Przycisk ⤢ zwija planszę do panelu i z powrotem.
- **Draft ma własną zakładkę** w panelu gracza (obok „Dashboard”, przed „Ocena”). Pojawia się i włącza automatycznie, gdy admin wystartuje draft, i znika po jego zakończeniu. Na „Dashboard” widać wtedy informację, że mecz jest w trakcie, z przyciskiem do draftu.
- **Zaznaczona postać („hover”) jest widoczna dla wszystkich** w lobby, w obu drużynach: gracz na zegarze klika postać, a reszta widzi ją na środku ekranu („zaraz wybierze / zaraz zbanuje”) oraz w jego wierszu w składzie. Nowy endpoint `POST /api/v1/draft/{matchId}/hover` (null czyści zaznaczenie), stan rozsyłany po SSE, czyszczony przy przejściu do kolejnego kroku.
- **Po upływie czasu postać zawsze się odsłania.** Jeśli gracz zaznaczył postać, ale nie zdążył kliknąć „Lock in”, licznik blokuje właśnie ją (a nie losową); gdy nic nie zaznaczył — losową dostępną. Slot nigdy nie zostaje pusty, a `DraftView.autoResolvedSteps` pozwala oznaczyć wybory dokonane przez licznik.
- **Admin może podmienić komuś postać** w trakcie draftu i po nim (`POST /api/v1/draft/{matchId}/champion`, panel kontroli meczu) — dla przypadku „zablokowałem złą postać”. Podmiana nie przesuwa kolejki: to, kto ma turę, wynika teraz ze wskaźnika kroku w sekwencji, a nie z liczby przypisanych postaci (`DraftState.picksConsumed`). Wcześniej ręczna zmiana championa mogła przestawić kolejność picków.
- **Dźwięki w drafcie z regulacją głośności.** Osobne cue dla startu draftu, „twoja kolej”, lock-inu, bana, ostatnich 5 sekund i zakończenia, plus zapętlone tło muzyczne. Głośnik + suwak w nagłówku draftu, ustawienie zapamiętywane w przeglądarce. Pliki wrzuca się do `frontend/public/sounds/` (opis w `README.md` tego katalogu); brakujące pliki są zastępowane krótkimi tonami WebAudio, więc dźwięk działa od razu. **Muzyka z LoL-a nie była dołączona — wrzuć ją jako `frontend/public/sounds/draft-music.mp3`.**
- **Odbugowany draft po odświeżeniu.** `useDrawLobby` nie miał żadnego odpytywania — gdy strumień SSE padł (uśpiony laptop, zmiana sieci, hiccup proxy, odświeżenie tokenu), plansza zamierała do momentu ręcznego F5. Teraz REST dopytuje co 2,5 s w fazach na żywo, każda akcja draftu dociąga stan od razu po zakończeniu, a w nagłówku widać „tryb odświeżania”, gdy push nie działa. Reconnect SSE ma wykładniczy backoff — pętla po 401 nie ma już zerowego opóźnienia (potrafiła zalać backend requestami po wygaśnięciu sesji).
- Odliczanie kroku draftu i głosowania jest zakotwiczone w czasie serwera (`updatedAt` z tej samej odpowiedzi), więc telefon z przesuniętym zegarem nie pokazuje już 0:00 albo 4:59 przez cały krok.
- **„Zapamiętaj mnie” przy logowaniu.** Sesja trwa do wylogowania: po restarcie backendu (tokeny są przypisane do `bootId` procesu, więc restart je unieważnia) sesja odtwarza się sama z zapisanych danych logowania — bez wyrzucania na ekran logowania. Jawne „Wyloguj” czyści też zapamiętane dane. UWAGA: login i hasło trafiają do `localStorage` przeglądarki, więc opcja jest domyślnie wyłączona i przeznaczona na własne urządzenie.
- **Utrata połączenia = wylogowanie, z komunikatem.** Gdy backend przestaje odpowiadać, na środku ekranu pojawia się „Utracono połączenie z serwerem — zamykamy sesję”, a po ~5 s ekran przechodzi w „Przerwa techniczna” z informacją, czy sesja wróci sama (zapamiętane logowanie), czy trzeba będzie zalogować się ponownie.
- **Loadingi na całej stronie.** Nowe szkielety (`Skeleton`, `CardSkeleton`, `SectionSkeleton`, `TableSkeleton`, `TilesSkeleton`, `CardGridSkeleton`) zastąpiły spinnery blokujące całe widoki — ranking, gracze, strona główna, profil gracza, mecz, panel gracza, Pulpit, lista meczów, akceptacje, plan meczów, zagrywki, ustawienia i tworzenie meczu pokazują teraz zarys treści w miejscu, w którym się pojawi. Pulpit admina nie gaśnie już z powodu jednego wolnego zapytania — każda sekcja raportuje swój stan osobno.
- **Lista obecności przy głosowaniu.** Pod przyciskami „Będę / Może / Nie” w panelu gracza widać teraz imiennie, kto jak zagłosował (trzy kolumny z licznikami) — sam bilans nie mówił, czy są konkretne osoby.
- Pulpit admina pokazuje sekcję „Draft w toku” (statusy `DRAFTING` / `DRAFTED`), a kafelek „Mecze w toku” liczy wszystkie mecze w przygotowaniu, drafcie i grze.
- Odporność: `DrawLobbyService` nie wywala już 500 (NPE), gdy uczestnik meczu nie występuje w puli — taka rozbieżność po zmianie składu potrafiła zabić cały ekran draftu.
- Nowy test integracyjny `DraftServiceIT` pokrywa kolejność tur, podmianę postaci przez admina, hover i odsłonięcie po upływie czasu.

## v0.3.2 — 2026-07-27

- Głosowanie RSVP przez Discord. Zaplanowanie meczu wysyła teraz dwie wiadomości: ogłoszenie z pingiem @everyone na kanał ogłoszeń oraz kartę głosowania z przyciskami „Będę / Nie będę / Może” na nowy kanał głosowań (`DISCORD_VOTE_CHANNEL_ID`, fallback do kanału ogłoszeń). Kliknięcia liczą się jako RSVP w systemie — tylko dla kont Discord połączonych z graczem (`discord_user_id`); pozostałe osoby dostają prywatną odpowiedź z odmową. Głos można zmienić klikając ponownie, potwierdzenia są efemeryczne (widzi je tylko głosujący). Backend łączy się z Discordem przez gateway (JDA, tylko odbiór interakcji — wysyłka nadal po REST); bez uprzywilejowanych intentów.
- Szczegóły punktacji LP na stronie meczu są teraz zwijane: widać tylko sumę LP każdego gracza, a kliknięcie „Szczegóły” rozwija pełną matematykę — składniki LP (baza za wygraną/przegraną, występ = PR ÷ przelicznik, bonusy MVP/ACE/penta/quadra/bez śmierci) oraz tabelę „jak powstało PR”: każda metryka (KDA, udział w zabójstwach, CS/min, obrażenia/min, udział w złocie, wizja/min) z wartością gracza, średnią meczu, normą (średnia → 0,5, 2× średnia → 1,0), wagą roli i punktami PR (norma × waga × 100).
- Naprawione daty meczów: listy (strona główna, historia gracza, panel admina, domyślne sortowanie `GET /api/v1/matches`) pokazują i sortują po dacie faktycznego startu meczu (`startedAt`), a nie po dacie utworzenia/akceptacji — edycja czy ponowna akceptacja wyniku nie przesuwa już meczu w listach. `startedAt` dodane do `MatchSummaryResponse`, `PlayerMatchEntry` i `RateableMatch`.

## v0.3.1 — 2026-07-23

- Zagrywki w tle strony głównej przenikają się płynnie (fade in/out ~0,9 s): klip zaczyna się od zera, a ~1 s przed końcem wygasza się przed przełączeniem na kolejny. Respektuje „prefers-reduced-motion”.
- Nowa sekcja „Opinie graczy” na stronie meczu (widoczna dla zalogowanych — endpoint `GET /api/v1/matches/{id}/feedback-summary`): karta gracza z największą liczbą plusów oraz karta z największą liczbą minusów, każda z automatycznie przewijanym sliderem anonimowych komentarzy o jego grze (pozytywne na zielono, negatywne na czerwono). Backend agreguje `match_feedback` (liczby +/− oraz notatki przypięte do wyróżnionego/ocenionego gracza).
- Ręczne układanie drużyn przez przeciąganie w tworzeniu meczu. Tryb „Ręcznie” pokazuje edytor składu: dwie drużyny po 5 nazwanych slotów ról (TOP/JUNGLE/MID/ADC/SUPPORT) i pulę graczy — przeciągnięcie (drag & drop, z fallbackiem klik‑podnieś/klik‑połóż na dotyku) ustawia jednocześnie stronę i rolę, drop na zajęty slot odsyła poprzednika do puli. Backend: `CreateMatchRequest.teams` + walidowany `DrawService.manualDraw` (dokładnie 5 na stronę, każda rola raz), nowy endpoint `POST /api/v1/matches/{id}/draw/manual`; dla meczów `MANUAL` ukryty jest przycisk losowego rerollu w panelu kontroli.
- Wylogowanie wszystkich użytkowników po restarcie backendu. Każdy proces generuje przy starcie `bootId` (UUID), osadzany jako claim `bid` w tokenach JWT i walidowany w filtrze uwierzytelniania oraz przy odświeżaniu tokenu — po restarcie stare tokeny są odrzucane (401), co przez istniejącą ścieżkę klienta kończy sesję. `bootId` wystawiony na publicznym `GET /api/v1/config`. UWAGA: pierwszy restart po wdrożeniu jednorazowo wyloguje wszystkich obecnie zalogowanych (ich tokeny nie mają jeszcze claimu `bid`).
- Ekran „Przerwa techniczna” przy braku połączenia z backendem. Frontend monitoruje `GET /api/v1/config`; przy niedostępności serwera (błąd sieci lub 502/503/504 zza nginx) pokazuje pełnoekranową nakładkę i automatycznie ponawia co 3 s, a po powrocie serwera z innym `bootId` (realny restart) wylogowuje. Globalny `QueryCache.onError` wykrywa przerwę natychmiast, bez czekania na cykl monitora.
- Uzupełnione wskaźniki ładowania i stany błędu na stronach, którym ich brakowało: strona główna (ostatnie wyniki, czołówka), Pulpit, Gracze, Akceptacje i Plan meczów w panelu admina.

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