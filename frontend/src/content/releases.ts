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