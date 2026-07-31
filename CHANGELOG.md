# Changelog

Wszystkie istotne zmiany Driperskiej Ligi są opisywane tutaj oraz w
`frontend/src/content/releases.ts`, który zasila patch notes na stronie głównej.

## v0.5.1 — 2026-07-30

- **Potwierdzanie obecności znika, kiedy termin minie.** `GET /planned-matches` zwraca tylko mecze przed terminem, więc panel gracza nie trzyma już w nieskończoność karty „Potwierdź obecność” dla meczu, który się odbył albo się nie odbył wczoraj.
- **Głos po terminie jest odrzucany po stronie serwera, nie tylko schowany w interfejsie.** Wiadomość z przyciskami „Będę / Nie będę / Może” zostaje na kanale Discorda na zawsze, więc bez tej blokady kliknięcie w miesięczne ogłoszenie nadal zapisywałoby obecność na mecz, który dawno się skończył. Teraz w odpowiedzi przychodzi „Termin tego meczu już minął — potwierdzanie obecności jest zamknięte”, tą samą ścieżką i przez stronę, i przez bota.
- **Panel admina zachowuje historię.** Minione terminy nie przepadają — `includePast=true` (tylko dla ADMIN/EDITOR) pokazuje je w „Planowaniu meczów” w osobnej, przygaszonej sekcji „Termin minął”, bez możliwości głosowania, z „Anuluj” do sprzątnięcia listy. Gracze tego parametru nie dostaną, nawet jeśli go dopiszą do adresu.
- Panel gracza powtarza to samo odcięcie po stronie przeglądarki, żeby mecz, którego godzina wybije przy otwartej stronie, przestał pokazywać przyciski, które serwer i tak by już odrzucił.
- **„Skąd LP w tym meczu” zjeżdża pod opinie.** Na stronie meczu kolejność to teraz wynik → komentarze graczy → rozliczenie LP. Po co ludzie wchodzą na mecz, to przeczytać, co się o nim mówi; tabela z przelicznikiem punktów jest materiałem referencyjnym, do którego się schodzi, gdy jakaś liczba wygląda podejrzanie.
- **Karta meczu w „Ostatnich wynikach” pokazuje cały pojedynek, nie tylko wynik.** W każdym rzędzie jest K/D/A obu graczy, a między nimi napisana linia (TOP / JUNGLE / MID / ADC / SUPP) — dwa nicki w rzędzie to ci, którzy faktycznie grali przeciwko sobie, więc rząd czyta się jako starcie, a nie jako dwie osobne listy. Statystyki obu stron są wyrównane do środka karty (czerwony rząd jest odbity), żeby liczby stały obok siebie.
- Przy MVP meczu jest korona, a przy ACE przegranej drużyny tarcza; stopka karty wymienia obu z ich Performance Rating, bo na ciasne rzędy z nickami PR już się nie mieści. ACE pokazuje się tylko wtedy, gdy ktoś z przegranych faktycznie przebił próg PR 60 — inaczej stopka ma samego MVP.
- **Blokady w formularzu wyników działają teraz całymi kolumnami.** Kłódka w nagłówku blokuje dane pole u wszystkich dziesięciu graczy jednym kliknięciem — wszystkie postacie, wszystkie pozycje albo wszystkie wartości jednej statystyki. Gdy model dobrze odczytał postacie, ale konsekwentnie myli vision score, dziesięć osobnych kliknięć było robotą na nic. Kłódka nagłówka obejmuje obie drużyny, więc działa tak samo z tabeli niebieskich i czerwonych; kłódki pojedynczych pól z 0.4.6 zostają bez zmian.
- **Prompt AI dostaje wprost korelację „nick na stronie ↔ nick w LoL”.** Scoreboard pokazuje nazwę z Riot ID, a baza kluczuje po nicku z serwisu, i te dwa ciągi bywają zupełnie niepodobne (`Driper` grający jako `xXSmurfik99Xx`). Do tej pory nazwa z Riot ID trafiała do promptu tylko wtedy, gdy różniła się od nicku, i nigdzie nie było napisane, czym te dwa pola są dla siebie — model musiał się tego domyślić. Teraz reguła stoi w system prompcie (osobna sekcja „TWO NAMES PER PERSON” z polskim przykładem różnicy), roster zawsze podaje oba jako parę i oznacza „in-game name unknown (no Riot ID on file)” u graczy bez Riot ID, a model ma zwracać nick z serwisu — bo tylko po nim backend potrafi przypisać wiersz do właściwej osoby. Zapasowe dopasowanie po nazwie z gry i tak zostaje w kodzie.
- **Strona główna ma tło, a nie pustą czerń.** Cztery warstwy z samych gradientów CSS: dryfujące plamy koloru (cyan i fiolet nad hero, ciepła na środku, niebieska nisko po lewej), trójkątna krata nawiązująca do heksu z logo, dwie ledwie widoczne smugi światła i złota poświata przy dolnej krawędzi okna. Warstwa jest `fixed` za treścią, więc strona przewija się *przez* tło, a szklane karty rozmywają je swoim `backdrop-filter` za darmo.
- Zero obrazków, canvasu i zapytań na zewnątrz — same gradienty, więc surowy CSP zostaje nietknięty, nie ma czego dociągać i animacja (jeden powolny `transform` na warstwie kolorów) idzie po GPU. `prefers-reduced-motion` ją wyłącza, a krata jest wymaskowana tak, żeby gasła w dół i nie wchodziła pod tabele. Zmiana dotyczy tylko strony głównej — pozostałe podstrony wyglądają jak dotąd.

## v0.5.0 — 2026-07-30

- **Moderatorzy.** Wybrani gracze mogą wprowadzać rozegrane mecze do ligi. Wniosek trafia do tej samej kolejki akceptacji, z której admin zatwierdza wyniki wpisane przez siebie — nic nie wchodzi do rankingu bez jego podpisu. Do momentu zatwierdzenia moderator może poprawiać swój wniosek dowolnie wiele razy.
- **Moderator to flaga na koncie gracza, a nie czwarta rola.** Rola `EDITOR` już istniała i technicznie robi więcej, ale wejście na nią jest degradacją: taka osoba przestaje być graczem, traci draft, profil i ocenianie meczów, a panel dostaje w `/admin` — czyli nie „u siebie”. Nowa kolumna `account.moderator` (migracja `V8`) dokłada uprawnienie do konta `PLAYER` i zostawia wszystko inne bez zmian.
- **Uprawnienie jest sprawdzane w bazie przy każdym żądaniu, nie w tokenie.** Token dostępu żyje 12 godzin, więc `ROLE_MODERATOR` w JWT oznaczałoby, że odebranie uprawnień działa dopiero po wygaśnięciu sesji. `MatchSubmissionService.requireModerator` czyta konto, więc nadanie i odebranie działa natychmiast — sprawdzone: ten sam token po odebraniu flagi dostaje 403.
- **Wpis moderatora nie dotyka pipeline'u na żywo.** Mecz z przeszłości nie ma czego losować, nikt nie głosuje nad składem, nie powstaje lobby Riot i nie idzie żadne ogłoszenie na Discord. `POST /moderation/matches` tworzy mecz od razu w `LIVE` ze składem wpisanym ręcznie (strony i role z tego samego kreatora, którego używa admin) i z datą faktycznego rozegrania, bo to ona ustala kolejność na listach meczów. Data w przyszłości jest odrzucana, z 10-minutową tolerancją na rozjazd zegara.
- **Statystyki wpisuje ten sam formularz co u admina**, razem z odczytem ze zrzutów ekranu przez AI i z kłódkami chroniącymi poprawione pola przed nadpisaniem. Endpoint OCR jest osobny (`/moderation/matches/{id}/results/ocr`) tylko dlatego, że dokłada sprawdzenie własności wniosku — sama analiza jest wspólna, bez duplikatu kodu.
- **Zakres jest wąski i sprawdzony punkt po punkcie.** Moderator widzi i edytuje wyłącznie własne wnioski (`match_game.created_by`); cudzy wniosek, endpointy meczowe admina, konta, ustawienia i akceptacja własnego meczu odpowiadają 403. Zatwierdzony mecz jest dla autora zamrożony — odblokować może go tylko admin przez „ponowne otwarcie”.
- **Skład można zmienić tylko przed wysłaniem wyników albo po odesłaniu do poprawy** (409 w pozostałych przypadkach). Podmiana składu usuwa statystyki wpisane dla poprzednich uczestników — inaczej wniosek w kolejce mógłby po cichu zmienić uczestników pod już podpisanymi liczbami. Panel ostrzega o tym przed zapisem; datę rozegrania można poprawiać zawsze.
- **Odesłanie do poprawy wraca do autora z powodem.** Lista wniosków pokazuje status wprost: szkic bez statystyk, oczekuje na akceptację, odesłany do poprawy (z uzasadnieniem admina) albo zatwierdzony. Wniosek, którego moderator nie chce już dosyłać, można wycofać.
- **Powiadomienie na Discord przy wejściu do kolejki.** Nowy `DISCORD_MODERATION_CHANNEL_ID` (puste = kanał ogłoszeń) dostaje informację, kto wprowadził mecz i jaki jest wynik, bez `@everyone` — to zadanie dla jednej osoby, nie ogłoszenie dla serwera. Wysyłka jest best-effort: brak tokenu albo awaria Discorda nie wywala wniosku, na który ktoś poświęcił dziesięć minut, a nieudane wysłanie ląduje w logu. Kolejne edycje tego samego wniosku już nie powiadamiają, żeby poprawki nie zaśmiecały kanału.
- **Kolejka akceptacji mówi teraz, kto wpisał mecz.** `ApprovalResponse` dokłada `submittedByName`, więc przy dwóch parach oczu widać nie tylko „oczekuje”, ale i autora — przy wpisach od kilku osób bez tego nie ma odpowiedzialności za dane.
- **Nadawanie uprawnienia jest w zakładce „Gracze”**, przy koncie logowania: przycisk „Nadaj/Odbierz moderatora” i odznaka na liście. Flaga wymaga istniejącego konta, bo siedzi na koncie, nie na profilu — bez konta panel przypomina o tym komunikatem. Publiczna lista graczy nigdy nie zwraca tej flagi; widzi ją admin i sam zainteresowany, więc strona nie ogłasza, które konta są uprzywilejowane.
- Weryfikacja: `MatchSubmissionServiceIT` (10 testów) plus pełny przebieg przez API na uruchomionym backendzie — wniosek → statystyki → edycja w kolejce → odesłanie → poprawa składu → ponowne wysłanie → zatwierdzenie → mecz publiczny i policzony w rankingu, wraz ze wszystkimi odmowami wymienionymi wyżej. Uwaga na przyszłość: `./mvnw verify` **nie** uruchamia klas `*IT` (projekt nie ma wtyczki failsafe, a domyślne wzorce surefire ich nie łapią) — uruchamia się je jawnie, np. `./mvnw '-Dtest=*IT' test`.

## v0.4.10 — 2026-07-29

- **Reklamy usunięte.** AdSense wypada w całości: `AdSlot`, konfiguracja slotów, panel zgody z 0.4.8, magazyn zgody, `ads.txt` oraz wszystkie trzy miejsca reklamowe na stronie głównej i w panelu gracza. Powód jest rachunkowy, nie ideologiczny — przy zamkniętej grupie kilkunastu graczy przychód byłby groszowy, a cena stała: rozluźniony CSP, trzeci podmiot w przepływie danych, panel zgody do utrzymania i wymóg certyfikowanego CMP, którego własnym kodem nie da się spełnić.
- **CSP wrócił do stanu sprzed 0.4.7.** `script-src` to znów `'self'` plus Cloudflare Turnstile — `'unsafe-inline'` i hosty reklamowe Google zniknęły, więc skryptowa połowa ochrony przed XSS jest odzyskana. To była najdroższa część monetyzacji i jej wycofanie jest największą korzyścią z tej zmiany.
- Zostaje refaktor nagłówków do `nginx-security-headers.conf` i naprawa `/assets/` oraz `/media/`, które wcześniej gubiły **wszystkie** nagłówki bezpieczeństwa, bo deklarowały własny `add_header`. To były usprawnienia niezależne od reklam. Konfiguracja ponownie zweryfikowana `nginx -t`.
- **Polityka prywatności zostaje i jest teraz krótsza oraz mocniejsza.** Serwis nie ustawia już żadnych plików cookie — nie ma reklam, analityki ani narzędzi śledzących, i dokument mówi to wprost. Pozostali odbiorcy bez zmian: Riot Games, Discord, Cloudflare, Google Fonts oraz Ollama (`OLLAMA_BASE_URL` domyślnie `https://ollama.com`, więc zrzuty ekranu z pseudonimami trafiają poza EOG).
- Nadal opisane są dwie rzeczy będące realnym ryzykiem dla użytkownika: opcja „Zapamiętaj mnie” zapisująca login i hasło w pamięci przeglądarki oraz hasło wysyłane jawnie w wiadomości prywatnej na Discordzie.
- Bez zmian pozostaje wszystko z 0.4.7: jedno logo `Hex` na wszystkich powierzchniach, archiwum meczów pod `/matches` i ekran ładowania z buforowaniem klipów.

## v0.4.9 — 2026-07-29

- **Polityka prywatności** pod `/privacy`, linkowana ze stopki oraz z etykiety każdego bloku reklamowego (panel gracza nie ma stopki, więc bez tego byłaby nieosiągalna spod logowania). Napisana pod to, co kod faktycznie robi, a nie z szablonu — każdy wymieniony odbiorca odpowiada realnej integracji w repozytorium.
- Ujawnia pełną listę odbiorców, która jest znacznie dłuższa niż sam AdSense: **Riot Games** (Riot ID, PUUID, dane meczów), **Discord** (identyfikator konta, treść wiadomości, kliknięcia obecności), **Cloudflare** (Turnstile i ruch), **Google Fonts** (adres IP przy każdym wejściu, bo kroje pisma lecą z CDN Google) oraz **Ollama** — `OLLAMA_BASE_URL` domyślnie wskazuje `https://ollama.com`, więc zrzuty ekranu z pseudonimami graczy trafiają do zewnętrznego modelu poza EOG. Bez sprawdzenia kodu ta ostatnia pozycja nie trafiłaby do dokumentu.
- Opisuje też dwie rzeczy, które są realnym ryzykiem dla użytkownika, a nie wymogiem szablonu: opcja **„Zapamiętaj mnie” zapisuje login i hasło w pamięci lokalnej przeglądarki** (sesje wygasają przy każdym restarcie backendu, więc to jedyny sposób ich odtworzenia), oraz **bot Discord wysyła wygenerowane hasło jawnie w wiadomości prywatnej**, gdzie zostaje w historii rozmowy.
- Administrator jest wskazany pseudonimem `mromasze`, a pełne dane identyfikacyjne udostępniane na żądanie. To kompromis: RODO oczekuje tożsamości administratora, więc nie jest to podręcznikowa zgodność — dla serwisu hobbystycznego prowadzonego dla zamkniętej grupy jest to jednak obronne i nie blokuje weryfikacji w AdSense.
- **Przełączenie na certyfikowany CMP Google.** Nowa stała `CMP_MODE` w `lib/ads.ts` rozstrzyga, kto zarządza zgodą. Przy `'google'` własny banner nie renderuje się w ogóle, a loader AdSense ładuje się **przed** uzyskaniem zgody — to konieczne, bo Google dostarcza swój panel zgody właśnie przez `adsbygoogle.js`, więc bramkowanie skryptu oznaczałoby, że dialog nigdy się nie pojawi. Dwa panele zgody na jednej stronie to zła obsługa i podstawa do odrzucenia witryny.
- Przy `'google'` pomijamy też własne sygnały Consent Mode — jeden system ma być właścicielem zgody, a drugi zapisujący te same sygnały mógłby tylko zamazać obraz. Banner z 0.4.8 zostaje w repozytorium jako działający wariant `'own'`; przełączenie to zmiana jednej stałej.

## v0.4.8 — 2026-07-29

- **Panel zgody na reklamy.** Do tej pory sloty AdSense pobierałyby skrypt Google przy pierwszym wejściu, bez pytania nikogo o zgodę. Teraz nic nie leci na zewnątrz, dopóki nie ma odpowiedzi: trzy warianty — „Zaakceptuj wszystko” (reklamy spersonalizowane), „Tylko niezbędne” (reklamy niespersonalizowane) i „Bez reklam” (skrypt nie jest pobierany w ogóle).
- **Wybór jest realnie wymuszany, nie deklaratywny.** Bez zgody `AdSlot` nie renderuje `<ins>` i nie wstrzykuje `adsbygoogle.js` — zero zapytań do zewnętrznego hosta. Przy „Tylko niezbędne” ustawiane jest `requestNonPersonalizedAds = 1` **przed** wstrzyknięciem loadera, bo flaga działa na całą stronę i musi istnieć, zanim skrypt się wykona. Etykieta slotu pokazuje wtedy „Reklama · niespersonalizowana”, więc stan zgody jest widoczny na stronie, a nie tylko w `localStorage`.
- **Wycofanie zgody przeładowuje stronę.** Raz wykonanego skryptu nie da się odwykonać — `adsbygoogle.js` zachowuje swoje cookie i personalizację do końca życia strony. Zawężenie zgody w trakcie wizyty jest więc uczciwe dopiero po reloadzie, i dokładnie to się dzieje, zamiast panelu twierdzącego jedno, gdy strona robi drugie.
- Zgodę można zmienić w każdej chwili: linkiem „Prywatność i reklamy” w stopce oraz małym „Ustawienia” przy każdym bloku reklamowym. To drugie nie jest ozdobą — panel gracza nie ma stopki, więc bez tego decyzja byłaby nieosiągalna spod logowania.
- **Panel nie jest dark patternem.** „Tylko niezbędne” ma dokładnie tę samą wagę wizualną co „Zaakceptuj wszystko”, odmowa jest jednym kliknięciem z pierwszego ekranu, a panel nie zasłania strony modalnym scrimem — ranking, mecze i draft pozostają czytelne i używalne niezależnie od wyboru.
- Pytamy wyłącznie o reklamy, bo reklamy są jedynym zewnętrznym podmiotem na stronie. Nie ma analityki, menedżera tagów ani wtyczek społecznościowych, więc nie ma o co więcej pytać — dopisywanie nieużywanych celów do panelu byłoby teatrem.
- Sygnały Consent Mode v2 (`ad_storage`, `ad_user_data`, `ad_personalization`) są domyślnie ustawiane na `denied` w `main.tsx`, przed pierwszym renderem, i aktualizowane po decyzji. Dziś nic ich nie czyta — strona nie ładuje `gtag.js` — ale nic nie kosztują i prawdziwy CMP albo późniejsza instalacja gtag znajdzie stan już wyrażony w oczekiwanym formacie.
- Wersjonowanie zgody: zmiana `CONSENT_VERSION` unieważnia zapisane odpowiedzi i pyta ponownie. Ciche użycie odpowiedzi udzielonej na inne pytanie to dokładnie to, za co banery zgody są krytykowane.
- **⚠️ To nie jest certyfikowany CMP i samo w sobie nie zapewnia zgodności z polityką Google dla EEA/UK.** Certyfikacja przechodzi przez IAB Europe TCF: zarejestrowane CMP ID, podpisana lista dostawców i ciąg TC, który Google waliduje względem rejestru. Kodu w tym repozytorium nie da się zamienić w ważny ciąg TC, a wybranie „certified CMP” w konsoli AdSense tego nie zmienia. Praktyczny skutek: dla ruchu z EEA/UK Google może ograniczyć albo wstrzymać wyświetlanie reklam. Ten panel poprawnie realizuje mechanizm niespersonalizowany (`requestNonPersonalizedAds`), który jest udokumentowanym sygnałem AdSense poza TCF — i to jest granica tego, co własny kod potrafi tu zrobić. Żeby domknąć zgodność, trzeba użyć CMP z listy certyfikowanych przez Google (np. własne „Privacy & messaging” Google, Cookiebot, CookieYes, Osano, Quantcast).
- **Nadal brakuje polityki prywatności.** AdSense jej wymaga, a panel odsyła obecnie do opisu Google („Jak Google wykorzystuje te dane”). Nie wymyślałem treści prawnej — to dokument do napisania i podlinkowania.

## v0.4.7 — 2026-07-29

- **Logo jest jedno.** Wariant `Hex` z nawigacji wchodzi na wszystkie powierzchnie: hero strony głównej, ekran logowania, stopkę, stronę 404 i ekran ładowania. Wcześniej strona główna i logowanie miały `Crest`, a stopka i 404 `Mono`, więc znak zmieniał się w zależności od miejsca. `Mono` zostaje tylko tam, gdzie ramka fizycznie nie działa: favicon (16 px) i znak wodny na kartach PNG rysowany w Java2D. Warianty `Crest` i `Tile` pozostają w module marki — `logo-tile.svg` jest wciąż źródłem eksportu na Discorda i ikony PWA.
- **Nowa strona „Mecze” z pełnym archiwum.** Do tej pory publicznie widoczne było wyłącznie sześć ostatnich wyników na stronie głównej; starsze mecze dawały się otworzyć tylko przez odgadnięcie adresu. `/matches` pokazuje wszystkie zatwierdzone mecze po dziewięć na stronę, z tymi samymi kartami wyników co strona główna. Nowa pozycja w nawigacji między „Ranking” a „Gracze”, a nagłówek „Ostatnie wyniki” na stronie głównej dostał link do archiwum.
- Lista nie sortuje wyników po stronie przeglądarki. Kolejność daje backend (`coalesce(started_at, created_at) desc`); sortowanie jednej strony spaginowanego zbioru lokalnie dałoby listę, która wygląda na uporządkowaną, choć granice stron mówią inaczej. Karty pojawiają się pojedynczo, w miarę jak spływają ich zapytania o szczegóły, a resztę strony przykrywa szkielet.
- **Ekran ładowania.** Pełnoekranowa kurtyna w kolorach strony: pulsujący znak `Hex` ze złotą aureolą, wordmark, pasek postępu z przesuwającym się połyskiem i nazwa aktualnego etapu. Zawartość aplikacji montuje się od razu pod kurtyną, więc React Query, router i przywracanie sesji pracują w tle — czekanie kupuje ciepłe dane, a nie samo mija.
- Kurtyna czeka na cztery etapy: kroje pisma (`document.fonts.ready`), kontakt z backendem, listę zagrywek i buforowanie dwóch pierwszych klipów. Klipy są pobierane **wyłącznie jako metadane** (`preload="metadata"`) — pojedynczy plik może mieć setki megabajtów, a liga gra na domowych łączach, więc pełne wideo nigdy nie trzyma drzwi zamkniętych.
- Ma twardy limit 6 sekund: po jego przekroczeniu strona otwiera się z tym, co się zdążyło załadować. Żaden nieudany preload nie potrafi uwięzić użytkownika. Minimalny czas 600 ms sprawia, że przy ciepłym cache kurtyna znika płynnie, a nie mruga. Animacje respektują `prefers-reduced-motion`.
- **Weryfikacja AdSense.** `frontend/public/ads.txt` deklaruje wydawcę `pub-4170130757231322` i jest wystawiony pod adresem głównym domeny (osobny `location = /ads.txt` w nginx), bo tam go szuka Google.
- **Trzy subtelne miejsca reklamowe.** Na stronie głównej między wynikami a rankingiem oraz nad patch notes; w panelu gracza pod pulpitem. Nic nad zgięciem strony — pierwszy ekran zostaje treścią ligi. Każdy blok ma drobną, wyciszoną etykietę „Reklama”, hairline strony i limit wysokości, żeby wysoka kreacja nie przepychała układu.
- **W panelu admina nie ma reklam i nie ładuje się nawet skrypt Google.** Loader jest wstrzykiwany przy pierwszym użyciu slotu, a nie w `index.html`, więc strony służące do prowadzenia ligi nie wykonują żadnego zapytania do zewnętrznego hosta. W drafcie również nie ma reklam — to część na żywo, z zegarem, i nic nie ma tam konkurować.
- Sloty bez identyfikatora jednostki reklamowej nie renderują **niczego** w produkcji. Puste obramowane pudełko albo napis „miejsce na reklamę” to dokładnie to, od czego mała strona wygląda na spam. W trybie developerskim slot rysuje przerywaną ramkę, żeby dało się ocenić układ przed założeniem jednostek.
- **Do zrobienia po stronie AdSense:** utworzyć trzy jednostki display i wpisać ich `data-ad-slot` do `AD_SLOTS` w `frontend/src/lib/ads.ts`. Do tego czasu reklamy się nie pokażą — cała reszta (loader, CSP, `ads.txt`) jest już gotowa.
- **CSP został świadomie rozluźniony i trzeba to wiedzieć.** AdSense nie działa pod `script-src 'self'`: wymaga hostów Google i — nieuchronnie — `'unsafe-inline'`, bo biblioteka `adsbygoogle` wstrzykuje inline'owe `<script>`, a Google nie publikuje nonce ani zestawu hashy do whitelisty. Kosztem jest utrata skryptowej połowy ochrony przed XSS, dla której ten nagłówek istnieje; zostaje kodowanie wyjścia. `'unsafe-eval'` **nie** został dodany — podstawowe jednostki display zwykle go nie potrzebują.
- Nagłówki bezpieczeństwa przeniesione do `frontend/nginx-security-headers.conf` i włączane przez `include`. `add_header` w nginx nie dziedziczy, gdy zagnieżdżony blok deklaruje własny, więc polityka była skopiowana w dwóch miejscach — zmiana mogła po cichu objąć tylko jedno z nich.
- Przy okazji: `/assets/` i `/media/` deklarowały własny `add_header Cache-Control` i przez to gubiły **wszystkie** nagłówki bezpieczeństwa z poziomu serwera. Dla wgrywanych przez użytkowników mediów brak `X-Content-Type-Options: nosniff` ma realne znaczenie, więc oba bloki włączają teraz snippet. Konfiguracja zweryfikowana `nginx -t` w kontenerze `nginx:1.27-alpine`.

## v0.4.6 — 2026-07-29

- **Liga ma wreszcie logo, a nie literę „D” w złotym kwadracie.** Wszystkie powierzchnie pokazywały to samo zastępcze `D` na gradientowym prostokącie. W jego miejsce wchodzi monogram **DL** narysowany na jednej siatce 256×256, ze stałą grubością kreski 28 jednostek — mierzoną prostopadle, także na fazowaniach 45° łuku litery D, więc narożniki nie puchną ani nie chudną przy skalowaniu do faviconu.
- **Cztery warianty tej samej geometrii, każdy z uzasadnionym miejscem:** `Hex` — wszystkie trzy navbary (publiczny, gracza, admina); nosi tę samą siatkę 1 px co panele hero (`.grid-tex`) i poświatę cyanu z `--glow-cyan`. `Crest` — hero na stronie głównej i ekran logowania; najbliższy dawnemu rombowi z faviconu, więc zmiana czyta się jako ewolucja, nie reset. `Tile` — pełne złoto z literami wybitymi w granacie; jedyny, który trzyma się na tle, którego nie kontrolujemy, więc to on idzie na Discorda i na ikonę PWA. `Mono` — bez ramki, czytelny w 16 px; favicon, stopka, strona 404 i znak wodny na kartach PNG.
- Kolory pochodzą wyłącznie z tokenów „Rift Nights”. Gradient złota jest identyczny z `.text-gradient-gold` (`#ffe4a3 → #f2c14e → #b98a2e`), więc znak postawiony obok wordmarku czyta się jako ten sam metal. Wnętrze `Hex` używa `var(--bg-1)`, żeby pozostało poprawne, jeśli motyw jasny zostanie kiedyś podłączony.
- Marki są komponentami React (`components/brand/Logo.tsx`) z identyfikatorami gradientów izolowanymi przez `useId()`. Bez tego kilka znaków na jednej stronie (navbar + stopka, hero + navbar) miałoby te same `id` w `<defs>`, a przeglądarka podpięłaby wypełnienia krzyżowo do pierwszego pasującego węzła.
- Favicon przeszedł ze starszego, chłodniejszego `#C8A24B` na tokenowe `#f2c14e`, więc karta w przeglądarce zgadza się z headerem. Płaskie złoto zamiast gradientu, bo w 16 px gradient tylko zamula kształt — i dzięki temu ten sam plik służy jako maska `mask-icon` dla przypiętej karty w Safari.
- **Karty PNG wysyłane na Discorda też noszą znak.** `common/image/BrandMark` przenosi geometrię wariantu `Mono` na `Path2D`, a `widthFor()` pozwala rozłożyć tekst za znakiem bez wpisywania szerokości na sztywno. Karta wyniku dostaje go w lewym górnym rogu (jak bug telewizyjny), karta patch notes przed wordmarkiem.
- Karta wyniku rysuje znak swoim własnym złotem, nie tokenowym: paleta tej karty jest celowo o odcień inna od webowej, a trzecie złoto obok korony MVP wyglądałoby jak błąd.
- **Do wgrania ręcznie:** avatar bota i ikona serwera Discord (PNG 1024×1024) oraz ikona PWA (PNG 512×512) — źródłem jest `frontend/public/logo-tile.svg`. `og:image` nie został dodany, bo Discord nie renderuje SVG w podglądzie linku, a `logo-lockup.svg` wymaga wcześniejszej konwersji wordmarku na krzywe (żywy `<text>` w Chakra Petch rozjedzie się na maszynie bez tego fontu).
- **Pola wyniku można zablokować przed nadpisaniem przez AI.** Każde wgranie screenshotu wysyła ponownie *cały* zestaw obrazów, a model odpowiada świeżym odczytem wszystkich dziesięciu wierszy — więc `applyDraft` nadpisywał całą tabelę. Wartość poprawiona ręcznie znikała w momencie dodania kolejnej zakładki, czyli dokładnie w tym przepływie, który panel sam zaleca („możesz dodać kilka naraz: KDA, obrażenia, wizja”).
- Kłódka przypina pole: scalanie OCR je pomija, a input przechodzi w tryb tylko do odczytu, więc nie da się go też ruszyć przypadkiem. Obejmuje pozycję, championa i wszystkie osiem pól statystyk każdego z dziesięciu graczy, plus zwycięską stronę i czas gry. Bez `patch` — model go nie zwraca, więc kłódka byłaby tam dekoracją.
- Przełącznik to przycisk 16 px nachodzący na prawy górny narożnik pola: przezroczysty, dopóki nie najedziesz na komórkę lub nie wejdziesz w niego tabulatorem, złoty i stale widoczny po zablokowaniu. Dziesięć trwale widocznych przycisków w wierszu przykryłoby liczby, o które w tej tabeli chodzi. Ikona jest rysowana jako SVG, nie emoji — 🔒 w rozmiarze 9 px wygląda inaczej na każdej platformie.
- Kafelek AI dostał licznik „N zablokowanych” oraz przyciski „Zablokuj wszystko” / „Odblokuj wszystko”, a komunikat po analizie dopisuje „Zachowano N zablokowanych pól.”, żeby pominięcia nie były niewidoczne.
- Licznik pominiętych pól jest liczony **poza** funkcją aktualizującą `setRows`: React wywołuje ten callback dwukrotnie w `StrictMode`, co podwajałoby wynik.
- Blokady żyją tylko tak długo, jak otwarty jest formularz — to notatnik na jedną sesję wpisywania, nie zapisywana preferencja, więc backend pozostaje bez zmian.

## v0.4.5 — 2026-07-28

- **Cała konfiguracja `.env` jest edytowalna w locie.** Nowy `GET/PUT /api/v1/admin/config` (ADMIN) pokazuje każde ustawienie z grupami, opisami i nazwą zmiennej środowiskowej. Zapis nadpisania trafia do `app_setting` i jest natychmiast wpisywany setterem w odpowiedni bean `@ConfigurationProperties`, więc reszta aplikacji czyta swoją konfigurację jak dotąd i nie wie o tej tabeli. Nadpisania są odtwarzane przy każdym starcie (`RuntimeConfigService.applyStoredOverrides`), więc przeżywają restart bez ruszania pliku na serwerze. `POST /api/v1/admin/config/reset` kasuje nadpisanie i przywraca wartość, z którą wystartował proces.
- **Sekrety nigdy nie wracają do przeglądarki w całości** — tylko maska (`abc…7890`) plus flaga „ustawione”. Dlatego pole sekretu startuje puste, a niedotknięte pola nie są w ogóle wysyłane przy zapisie: „zostaw bez zmian” to pominięty klucz, nie odesłanie tego, co pokazaliśmy.
- **Ustawienia konsumowane raz przy starcie są tylko do odczytu.** `JWT_SECRET` (podmiana unieważniłaby wszystkie sesje), `MEDIA_DIR`, Data Dragon i konto bootstrap są widoczne dla podglądu, ale zapis ich odrzuca z jasnym komunikatem. Token Discorda działa od razu dla wysyłki wiadomości, natomiast nasłuch głosów RSVP (websocket JDA) łączy się przy starcie — panel mówi to wprost.
- **Nowa zakładka „AI”.** Aktywny model, stan połączenia z Ollamą, lista modeli dostępnych na koncie (`GET /api/v1/admin/ai/models`) i przycisk „Testuj” (`POST /api/v1/admin/ai/test`), który wysyła jedno krótkie zapytanie do wskazanego modelu i mierzy czas — bez zapisywania czegokolwiek. Model można więc sprawdzić, zanim stanie się tym, od którego zależy odczyt screenshotów.
- `OllamaVisionClient` przestał zamrażać limit czasu w fabryce żądań przy tworzeniu beana — klient HTTP jest przebudowywany, gdy zmieni się `OLLAMA_TIMEOUT_SECONDS`. Bez tego zmiana limitu w panelu nie miałaby żadnego efektu.
- Czas kroku draftu i auto-akceptacja składu przeszły z `@Value` w konstruktorze na `DraftProperties` / `DrawProperties`, a `APP_PUBLIC_URL` na `AppCoreProperties` — wartości są czytane przy każdym użyciu, więc dają się zmieniać między meczami.
- **Nowa zakładka „Test draftu”.** Pełna symulacja draftu turniejowego: ten sam komponent planszy, te same cue dźwiękowe i ten sam zegar, co u graczy — tylko stan pochodzi z lokalnego silnika (`lib/draftSim`), a nie z API i SSE. Wybierasz miejsce w składzie (albo tryb obserwatora), czas kroku i tempo botów; możesz pauzować, pomijać krok, dokańczać draft i oddać własną turę botowi. Boty odpowiadają też na propozycje zamiany pozycji/postaci po drafcie. Żaden mecz ani gracz nie jest zapisywany.
- `DraftBoard` przyjmuje teraz opcjonalne `actions` (`DraftActions`); domyślnie są to prawdziwe endpointy. To jedyna zmiana, jakiej wymagało ponowne użycie planszy w symulacji — plansza gracza działa dokładnie jak wcześniej.
- Symulacja odtwarza reguły serwera 1:1 (kolejność `DraftState.tournamentSequence`, bany kapitana, picki spływające po kolejności drużyny, licznik blokujący zaznaczoną postać). Zweryfikowane pełnym przejściem 20 kroków: 5+5 banów, 10 obsadzonych slotów, zero duplikatów.
- Zakładka „Mecze” w panelu admina jest zwijanym menu — rozwija się sama, gdy jesteś w środku, a licznik akceptacji przechodzi na nagłówek, kiedy grupa jest zwinięta.
- **Karty ostatnich wyników pokazują składy.** Sam wynik nigdy nie mówił, *który* to był mecz — teraz każda karta ma ikony postaci i nicki obu drużyn, linia po linii, z wyróżnionym MVP.
- Migracja `V7` rozszerza `app_setting.setting_value` do `VARCHAR(2048)`, bo tabela trzyma teraz również klucze API i adresy.

## v0.4.4 — 2026-07-28

- **PR i MVP są teraz porównywalne między rolami.** Pozycja nadal określa rywala oraz historyczną bazę normalizacji, ale nie zmienia już wartości tego samego wyniku 0–1.
- Wszystkie role korzystają ze wspólnych wag ligi: **KDA 35%, KP 20%, CS/min 10%, damage/min 25%, damage/złoto 5%, vision/min 5%**. KDA pozostaje najważniejszą pojedynczą metryką.
- Usunięto podwójne uwzględnianie roli: wcześniej wynik był najpierw normalizowany względem danej pozycji, a następnie ponownie modyfikowany odmiennymi wagami TOP/JUNGLE/MID/ADC/SUPPORT. Przez to gracz z lepszym znormalizowanym KDA mógł dostać mniej punktów KDA wyłącznie z powodu pozycji.
- Dla przypadku regresyjnego ze scoreboardu nowe wagi zmieniają porównanie z około 61:60 na około **66:69** na korzyść gracza z lepszym KDA i damage.
- Po wdrożeniu wszystkie historyczne sezony zostaną automatycznie przeliczone według schematu rankingu 4.

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