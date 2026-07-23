export interface ReleaseNote {
  version: string;
  date: string;
  title: string;
  changes: string[];
}

/**
 * Public patch notes. Add the newest release at the top; the home page renders
 * this list automatically.
 */
export const RELEASES: ReleaseNote[] = [
  {
    version: 'v0.3.1',
    date: '2026-07-23',
    title: 'Opinie graczy i płynniejsze tło',
    changes: [
      'Zagrywki w tle strony głównej płynnie się przenikają — każdy klip pojawia się i znika łagodnym fade in/out.',
      'Na stronie meczu (dla zalogowanych) sekcja „Opinie graczy”: gracz z największą liczbą plusów i ten z największą liczbą minusów, a obok każdego dynamiczny slider z anonimowymi komentarzami o jego grze (pozytywnymi i negatywnymi).',
      'Poprawiony wygląd na telefonach: górne menu strony głównej chowa się pod hamburger, wersja aplikacji pobierana jest z jednego źródła (package.json).',
      'Szybszy i stabilniejszy odczyt wyników ze screenshotów (OCR): wyłączone „myślenie” modelu i dłuższy limit czasu (koniec timeoutów).',
      'Lepsze rozpoznawanie postaci w OCR: mocniejszy model wizyjny (qwen3.5:397b), włączony atlas referencyjny championów (dopasowanie portretu do nazwy), a normalne screenshoty (~0,5 MB) wysyłane są bez kompresji — kompresja rozmazywała małe ikonki i psuła rozpoznawanie.',
      'Na stronie meczu rozpisana punktacja LP: dla każdego gracza widać z czego składają się punkty (baza za wygraną/przegraną, występ = PR ÷ przelicznik, bonusy MVP/ACE/multikill/bez śmierci) i ile wyszło razem.',
      'Draft: rusza dopiero po kliknięciu „Rozpocznij draft” w panelu admina (czas na przejście na Discorda i ustalenie pozycji). Duży, wyraźny napis „TWOJA KOLEJ”, postać najpierw się zaznacza, a przycisk „Lock in” potwierdza wybór. Admin może wstrzymać/wznowić draft (pauza zatrzymuje licznik).',
      'Po rozpoczęciu gry ekran pokazuje pełny skład „kto kim gra” (gracz + jego postać i pozycja) po obu stronach.',
      'Losowanie składów domyślnie w 100% losowe (a nie zbalansowane po MMR, które powtarzało podobne drużyny). Tryb zbalansowany nadal dostępny do wyboru.',
    ],
  },
  {
    version: 'v0.3.0',
    date: '2026-07-23',
    title: 'Draft turniejowy',
    changes: [
      'Nowość: wewnętrzny draft postaci (bany i picki) w kolejności turniejowej LoL — 5 banów i 5 wyborów na drużynę. Bany robi kapitan drużyny, każdy gracz wybiera własną postać w swojej turze.',
      'Każdy krok draftu ma 30-sekundowy licznik widoczny dla wszystkich; po jego upływie przypisywana jest losowa dostępna postać.',
      'Po draftcie gracze mogą zamieniać się w obrębie drużyny pozycją lub postacią — klik strzałki przy graczu, druga osoba akceptuje.',
      'Admin może zresetować draft w dowolnym momencie z panelu kontroli meczu.',
      'Nowy przełącznik „Wsparcie Riot API” w ustawieniach panelu: wyłączony = po zaakceptowaniu składu startuje draft wewnętrzny (bez kodu Riot); włączony = tworzy lobby turniejowe Riot jak dotychczas.',
      'Poprawka: po odświeżeniu strony lub zalogowaniu w trakcie głosowania od razu widać składy i możliwość głosowania (stan dociera natychmiast przez strumień na żywo).',
      'Poprawka: bez wsparcia Riot API nie pokazuje się już niedziałający kod dołączenia do lobby.',
      'Timer głosowania nad składem wydłużony do 60 sekund, z widocznym odliczaniem dla każdego uczestnika.',
      'W draftcie stała kolejność drużyny od góry: kapitan (TOP) → JUNGLE → MID → ADC → SUPPORT; kapitan banuje, a picki idą po kolei z góry na dół (podświetlany jest gracz, którego jest tura).',
      'Panel admina: przycisk „Patch notes na Discord” w Ustawieniach — generuje obrazek z listą zmian i wysyła na kanał patch notes z pingiem @everyone.',
      'Panel gracza: mecze do oceny przeniesione do zakładki „Ocena” z licznikiem oczekujących ocen (jak powiadomienie).',
      'Po wysłaniu wyników mecz jest w panelu gracza oznaczany jako zakończony (znika draft/lobby, zostaje informacja o oczekiwaniu na zamknięcie meczu przez admina).',
      'Panel admina: zakładki pogrupowane — sekcja „Mecze” zbiera listę meczów, nowy mecz, plan meczów i akceptacje.',
      'Naprawione: zatwierdzone mecze od razu znikają z kolejki akceptacji.',
      'Wyłączone cache’owanie strony — koniec z ręcznym Ctrl+F5, aby zobaczyć nową wersję/dane.',
    ],
  },
  {
    version: 'v0.2.8',
    date: '2026-07-22',
    title: 'Zakładki w panelu gracza i poprawka zdjęcia profilowego',
    changes: [
      'Panel gracza podzielony na zakładki: „Dashboard” (losowanie/gra, potwierdzanie obecności i ocena meczu) oraz „Profil i ustawienia” (edycja profilu, ulubieni bohaterowie, zdjęcie i zmiana hasła).',
      'Na publicznym profilu gracza widać teraz jego ulubionych bohaterów.',
      'Naprawiona zmiana zdjęcia profilowego — dało się je ustawić tylko raz; teraz stare zdjęcie jest usuwane, a nowe od razu widoczne (koniec problemu z cache przeglądarki).',
      'Naprawione wgrywanie „Zagrywek” i powtórek — kończyło się błędem „Access Denied” przy większych plikach.',
      'Panel admina: lista meczów zwijana do rozwijanych elementów (dropdown) — zwarta i czytelna.',
      'Poprawione wyświetlanie panelu admina na telefonach — chowane menu (hamburger) zamiast ściśniętego paska zakładek.',
      'Podczas głosowania nad składem każdy widzi, kto jak zagłosował (Gramy / Losuj ponownie / czeka) — na żywo.',
      'Dłuższa sesja logowania — koniec wylogowywania po kilku minutach. Token ważny domyślnie 12 godzin (a odświeżanie utrzymuje sesję do 30 dni).',
    ],
  },
  {
    version: 'v0.2.7',
    date: '2026-07-22',
    title: 'Ładniejsza karta wyniku i poprawki panelu',
    changes: [
      'Obrazek wyniku na Discord wygląda teraz jak tabela z aplikacji: ikony bohaterów, KDA, CS i kolorowe oceny PR.',
      'Naprawiona zakładka „Mecze” (nie ładowała się w nieskończoność).',
      'Pozycję gracza wpisuje się przy wyniku (grana pozycja, nie ulubiona) — edytowalna w formularzu i czytana ze screenshotów.',
      'Na podsumowaniu meczu stała kolejność: TOP, JUNGLE, MID, BOT, SUPPORT.',
      'Post z wynikiem na Discordzie zawiera link do szczegółów meczu.',
      'W panelu gracza mecz znika po zakończeniu — zostaje sama ankieta oceny z informacją, którego meczu dotyczy (data i składy).',
      'Naprawione wgrywanie klipów i powtórek na produkcji — większe limity (do 512 MB przez nginx i backend, klipy do 400 MB) i czytelny komunikat zamiast błędu przy zbyt dużym pliku.',
      'Drobne utwardzenie strumienia na żywo (SSE) — brak wyjątku w logach po wygaśnięciu połączenia.',
      'Godziny wyświetlane w formacie 24-godzinnym i w polskiej strefie czasowej (Europe/Warsaw).',
    ],
  },
  {
    version: 'v0.2.6',
    date: '2026-07-22',
    title: 'Oceny meczu, zmiana hasła, zabezpieczenie logowania',
    changes: [
      'Po meczu zamiast statystyk: opcjonalna ankieta — jeden plus i jeden minus dla wybranych graczy + notatka z uzasadnieniem.',
      'Profil gracza zwalnia się po zakończonym meczu.',
      'Zmiana hasła w ustawieniach konta gracza.',
      'Cloudflare Turnstile na formularzu logowania (ochrona przed botami).',
    ],
  },
  {
    version: 'v0.2.5',
    date: '2026-07-22',
    title: 'Planowanie meczów, diagnostyka i poprawki',
    changes: [
      'Naprawione udostępnianie wyniku na Discord (błąd reactive-streams przy wysyłce obrazka).',
      'Większy limit wgrywanych plików — koniec „request too large” przy powtórkach i screenshotach.',
      'Zakładka „Mecze”: tabela wszystkich meczów z edycją i udostępnianiem z poziomu listy.',
      'Planowanie meczów: termin + zbiorowe ogłoszenie na Discord z linkiem do potwierdzenia obecności; gracze RSVP (Będę / Może / Nie), admin widzi kto potwierdził.',
      'Wyszukiwarka bohaterów po nazwie w edycji profilu.',
      'Zakładka „Diagnostyka”: szybkie testy połączenia z AI (Ollama), Discordem i Riot API.',
    ],
  },
  {
    version: 'v0.2.4',
    date: '2026-07-22',
    title: 'Wyniki ze screenshotów (AI)',
    changes: [
      'Wczytywanie statystyk z ekranu podsumowania LoL przez model wizyjny (Ollama Cloud) — tabela wypełnia się sama.',
      'Można wgrać kilka zrzutów naraz (np. osobne zakładki KDA / obrażenia / wizja); dane łączone są po graczu.',
      'AI dopasowuje graczy po nazwie w grze / Riot ID oraz championy po nazwie — admin sprawdza i poprawia przed wysłaniem.',
      'Rozwiązanie tymczasowe, dopóki nie dostaniemy produkcyjnego dostępu do Riot Tournament API.',
    ],
  },
  {
    version: 'v0.2.3',
    date: '2026-07-22',
    title: 'Deploy, zagrywki i zarządzanie meczami',
    changes: [
      'CI wysyła na serwer gotowe obrazy Dockera i uruchamia je bez nadpisywania serwerowych .env oraz docker-compose.yml.',
      'Najlepsze zagrywki MP4/WebM odtwarzane jako subtelne tło strony głównej, z uploadem w panelu.',
      'Naprawione generowanie obrazka wyniku w Dockerze oraz czytelne błędy i logi integracji Discord.',
      'Lista wszystkich meczów z filtrowaniem, edycją i ponownym udostępnianiem wyniku na Discordzie.',
    ],
  },
  {
    version: 'v0.2.2',
    date: '2026-07-22',
    title: 'Wygoda panelu i diagnostyka Discord',
    changes: [
      'Mecz nie znika po odświeżeniu — sekcja „Mecze w przygotowaniu” na pulpicie prowadzi z powrotem do trwającego meczu.',
      'Przycisk „Anuluj mecz” w panelu kontroli meczu.',
      'Udostępnianie obrazka z wynikiem na Discord dostępne także w kolejce akceptacji.',
      'Czytelne komunikaty, gdy DM na Discord nie dojdzie (Server Members Intent, prywatność odbiorcy, numeryczny User ID).',
      'Ostrzeżenie na ekranie lobby, że kod testowy (stub) nie zadziała w kliencie bez produkcyjnego dostępu Tournament API.',
      'Plik weryfikacyjny riot.txt hostowany w korzeniu domeny (rejestracja Tournament API).',
    ],
  },
  {
    version: 'v0.2.1',
    date: '2026-07-21',
    title: 'Poprawki: Riot, zdjęcia, podsumowanie',
    changes: [
      'Naprawiona integracja Riot — poprawny klaster (americas) i tournament-stub dla kluczy deweloperskich (koniec błędów 403).',
      'Usunięte zbędne zapytanie summoner-v4 (błąd „zasób nie istnieje").',
      'Region ustawiony na EUNE.',
      'Naprawione wgrywanie zdjęć (uprawnienia woluminu w Dockerze).',
      'Edycja danych gracza z panelu admina: Riot ID, Discord, role, imię.',
      'Ręczne rozpoczęcie meczu bez Riot oraz edycja już rozegranych meczów.',
      'Auto-akceptacja składu po 30 s i podgląd głosowania na żywo dla admina.',
      'Oceny (PR/LP) widoczne na podsumowaniu zaraz po wpisaniu wyniku.',
      'Udostępnianie obrazka z wynikiem na Discord i wgrywanie powtórki .rofl.',
      'Podpowiedzi statystyk, wyjaśnienie punktacji i osobna zakładka Patch Notes.',
    ],
  },
  {
    version: 'v0.2',
    date: '2026-07-21',
    title: 'Lobby turniejowe i automatyczne wyniki',
    changes: [
      'Automatyczne tworzenie lobby Riot po zaakceptowaniu składów.',
      'Kod gry i przypisana strona widoczne w strefie każdego uczestnika.',
      'Callback oraz ręczne pobieranie statystyk do kolejki akceptacji.',
      'Wymiana gracza przed startem i ponowne wystawienie bezpiecznego kodu.',
      'Dane logowania wysyłane przez bota Discord z opcją kopiowania i ponownej wysyłki.',
    ],
  },
  {
    version: 'v0.1',
    date: '2026-07-21',
    title: 'Pierwsze losowanie',
    changes: [
      'Konta graczy z losowym hasłem i gotową wiadomością do wysłania na DM.',
      'Losowanie drużyn i stron na żywo z głosowaniem 6/10.',
      'Automatyczny re-roll po pięciu głosach przeciw.',
      'Edycja roli, ulubionych championów, zdjęcia profilowego i linku OP.GG.',
      'Bezpieczne migracje bazy i workflow aktualizacji serwera.',
    ],
  },
];