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
    version: 'v0.2.3',
    date: '2026-07-22',
    title: 'Deploy, zagrywki i zarządzanie meczami',
    changes: [
      'Automatyczny deploy produkcyjny po udanym CI na gałęzi main.',
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