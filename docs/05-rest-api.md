# 05 — REST API

## 5.1 Zasady projektowe

- **Wersjonowanie:** prefiks `/api/v1`.
- **Zasoby, nie czasowniki** — akcje cyklu życia meczu jako pod-zasoby (`/draw`, `/results`,
  `/approve`), bo to przejścia stanu, nie CRUD.
- **DTO ≠ encje** — osobne rekordy request/response (Java `record`), mapowane MapStructem.
  Nigdy nie serializujemy encji JPA (lazy loading, wycieki pól).
- **Błędy: RFC 7807** `application/problem+json` — jeden spójny kształt.
- **Paginacja:** `?page=&size=&sort=` → koperta `PageResponse<T>` (`content`, `page`, `size`,
  `totalElements`, `totalPages`).
- **Auth:** Bearer JWT (access ~15 min + refresh ~7 dni). Publiczne GET-y bez tokenu.
- **Idempotencja:** operacje mutujące stan (approve) bezpieczne przy powtórzeniu (409, nie dubel).
- **Czas:** ISO-8601 UTC (`Instant`). **Pieniądze/liczby:** bez formatowania po stronie API.
- **Dokumentacja:** springdoc → `/swagger-ui.html` + `/v3/api-docs` (źródło typów dla frontu).

## 5.2 Kształt odpowiedzi i błędów

Sukces — bezpośrednio zasób lub `PageResponse`:
```json
{ "content": [ /* ... */ ], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
```

Błąd (Problem Details):
```json
{
  "type": "https://driperska.liga/errors/validation",
  "title": "Walidacja nie powiodła się",
  "status": 422,
  "detail": "Mecz musi mieć dokładnie 10 uczestników (5 BLUE + 5 RED).",
  "instance": "/api/v1/matches/1f2.../results",
  "errors": [
    { "field": "participants", "message": "oczekiwano 10, otrzymano 9" }
  ],
  "traceId": "abc123"
}
```

Kody: `400` (zły request), `401` (brak/nieważny token), `403` (brak roli), `404` (nie ma zasobu),
`409` (niedozwolone przejście stanu / konflikt wersji), `422` (walidacja semantyczna), `500`.

## 5.3 Mapa endpointów

### Auth — `/api/v1/auth`
| Metoda | Ścieżka | Rola | Opis |
|--------|---------|------|------|
| POST | `/login` | public | `{username,password}` → `{accessToken, refreshToken, account}` |
| POST | `/refresh` | public | odświeżenie access tokenu |
| POST | `/logout` | auth | unieważnienie refresh tokenu |
| GET | `/me` | auth | bieżące konto + rola |

### Accounts — `/api/v1/accounts` (ADMIN)
| Metoda | Ścieżka | Opis |
|--------|---------|------|
| GET | `/` | lista kont (paginacja) |
| POST | `/` | utwórz konto `{username,email,password,role}` |
| PATCH | `/{id}` | zmiana roli / enabled / reset hasła |
| DELETE | `/{id}` | dezaktywacja (soft) |

### Players — `/api/v1/players`
| Metoda | Ścieżka | Rola | Opis |
|--------|---------|------|------|
| GET | `/` | public | lista graczy (filtr: `active`, `role`, `search`) |
| GET | `/{id}` | public | profil + zagregowane statystyki (sezon lub all-time) |
| GET | `/{id}/matches` | public | historia meczów gracza (paginacja) |
| GET | `/{id}/stats?season=` | public | agregaty: LP, W/L, avg PR, top championy, MMR |
| POST | `/` | ADMIN/EDITOR | dodaj gracza |
| PATCH | `/{id}` | ADMIN/EDITOR | edycja danych gracza |
| POST | `/{id}/avatar` | ADMIN/EDITOR | upload zdjęcia (multipart, patrz 5.5) |
| DELETE | `/{id}` | ADMIN | soft-delete |

### Champions — `/api/v1/champions`
| Metoda | Ścieżka | Rola | Opis |
|--------|---------|------|------|
| GET | `/` | public | lista championów (id, nazwa, ikona) |
| GET | `/{key}` | public | szczegóły + URL-e obrazów |
| POST | `/sync` | ADMIN | synchronizacja z Data Dragon (patch opcjonalny) |

### Seasons — `/api/v1/seasons`
| Metoda | Ścieżka | Rola | Opis |
|--------|---------|------|------|
| GET | `/` | public | lista sezonów |
| GET | `/current` | public | aktywny sezon |
| GET | `/{id}/ranking` | public | tabela ligowa sezonu |
| POST | `/` | ADMIN | nowy sezon |
| PATCH | `/{id}` | ADMIN | aktywacja/archiwizacja, override `scoringConfig` |

### Ranking — `/api/v1/ranking`
| Metoda | Ścieżka | Rola | Opis |
|--------|---------|------|------|
| GET | `/?season=` | public | leaderboard (LP, W-L, PR, MVP, MMR opcj.) |
| POST | `/recalculate?season=` | ADMIN | pełne przeliczenie z historii |

### Matches — `/api/v1/matches` (rdzeń cyklu życia)
| Metoda | Ścieżka | Rola | Stan → Stan | Opis |
|--------|---------|------|-------------|------|
| GET | `/` | public* | — | lista (filtr `status`, `season`); niezaakceptowane tylko dla auth |
| GET | `/{id}` | public* | — | szczegóły + scoreboard; pełne dane wg roli |
| GET | `/{id}/events` | ADMIN/EDITOR | — | oś czasu audytu |
| POST | `/` | ADMIN/EDITOR | → DRAFT | utwórz mecz z pulą graczy `{seasonId, drawMode, playerIds[]}` |
| POST | `/{id}/draw` | ADMIN/EDITOR | DRAFT/TEAMS_DRAWN → TEAMS_DRAWN | losuj / re-roll (zwraca propozycję + szansa) |
| POST | `/{id}/draw/confirm` | ADMIN/EDITOR | TEAMS_DRAWN → LIVE | zatwierdź składy, „gra rusza" |
| POST | `/{id}/results` | ADMIN/EDITOR | LIVE/REJECTED → RESULTS_SUBMITTED | wpisz statystyki |
| PATCH | `/{id}/results` | ADMIN/EDITOR | RESULTS_SUBMITTED/REJECTED | popraw statystyki przed akceptacją |
| POST | `/{id}/approve` | **ADMIN** | RESULTS_SUBMITTED → APPROVED | podpisany checkbox → nalicz punkty |
| POST | `/{id}/reject` | **ADMIN** | RESULTS_SUBMITTED → REJECTED | odeślij do edycji z powodem |
| POST | `/{id}/cancel` | ADMIN | * → CANCELLED | anuluj mecz |
| POST | `/{id}/reopen` | ADMIN | APPROVED → RESULTS_SUBMITTED | korekta zaakceptowanego (przelicza punkty) |

\* publiczne GET-y pokazują tylko mecze `APPROVED`; stany robocze widoczne po zalogowaniu.

### Konfiguracja runtime — `/api/v1/admin/config` (ADMIN)
Podgląd i edycja konfiguracji `.env` bez restartu. Nadpisania są trzymane w `app_setting` i odtwarzane
na beanach `@ConfigurationProperties` przy starcie (`RuntimeConfigService`), więc reszta aplikacji
czyta swoją konfigurację jak dotąd i nie wie o tej tabeli.

| Metoda | Ścieżka | Opis |
|--------|---------|------|
| GET | `/` | wszystkie ustawienia w grupach; sekrety wyłącznie zamaskowane (`abc…7890`) |
| PUT | `/` | `{values: {klucz: wartość}}` — pominięty klucz zostaje bez zmian, `null` kasuje nadpisanie |
| POST | `/reset` | `{keys: [...]}` — przywróć wartości, z którymi wystartował proces |

Ustawienia konsumowane raz przy starcie (`JWT_SECRET`, `MEDIA_DIR`, Data Dragon, konto bootstrap) są
zwracane z `editable: false` i odrzucane przy zapisie — zmienia się je w `.env` i restartuje backend.

### AI — `/api/v1/admin/ai` (ADMIN)
| Metoda | Ścieżka | Opis |
|--------|---------|------|
| GET | `/models` | modele dostępne na koncie Ollama (aktywny zawsze na liście) |
| POST | `/test` | `{model?, prompt?}` — jedno krótkie zapytanie; mierzy czas, nic nie zapisuje |

## 5.4 Przykładowe kontrakty (kluczowe)

**Losowanie — odpowiedź `POST /matches/{id}/draw`:**
```json
{
  "matchId": "…",
  "drawMode": "BALANCED",
  "blue": [ { "playerId":"…","nickname":"Faker","role":"MID","mmr":1180 }, … ],
  "red":  [ … ],
  "balance": { "blueMmrAvg": 1104, "redMmrAvg": 1097, "predictedBlueWinPct": 51.0 }
}
```

**Wpisanie wyników — `POST /matches/{id}/results`:**
```json
{
  "winningSide": "BLUE",
  "durationSeconds": 1980,
  "patch": "14.13",
  "participants": [
    { "playerId":"…","side":"BLUE","role":"ADC","championId":51,
      "kills":10,"deaths":2,"assists":8,"cs":250,"gold":15000,
      "damageToChampions":30000,"visionScore":20,"largestMultiKill":3 }
    /* …10 pozycji… */
  ]
}
```

**Akceptacja — `POST /matches/{id}/approve`:**
```json
{ "signatureConfirmed": true, "signatureName": "Magda K." }
```
→ `422`, gdy `signatureConfirmed=false`; `409`, gdy mecz nie jest w `RESULTS_SUBMITTED`.

## 5.5 Upload avatarów
`POST /players/{id}/avatar`, `multipart/form-data`, pole `file`. Walidacja: typ (`image/png|jpeg|webp`),
max ~5 MB, przeskalowanie serwerowe (np. 512×512, thumbnail 96×96 — biblioteka `imgscalr`/`Thumbnailator`).
Zapis do storage (v1: wolumen `./data/avatars`, serwowany przez nginx spod `/media/avatars/`;
v2: MinIO/S3). W bazie trzymamy tylko ścieżkę/URL.

## 5.6 Bezpieczeństwo (Spring Security)
- Stateless, `SessionCreationPolicy.STATELESS`, filtr JWT przed `UsernamePasswordAuthenticationFilter`.
- `@EnableMethodSecurity` + `@PreAuthorize` na use-case'ach (`hasRole('ADMIN')`, `hasAnyRole('ADMIN','EDITOR')`).
- BCrypt (`strength 12`). Rate-limiting logowania (bucket4j) — ochrona brute-force.
- CORS zbędny w produkcji (jeden origin przez nginx); w dev whitelist `localhost:5173`.
- Nagłówki bezpieczeństwa (CSP, HSTS) na nginx.

## 5.7 Wersjonowanie kontraktu z frontendem
CI generuje `openapi.json` z springdoc; frontend odpala `openapi-typescript` → `api-types.ts`.
Rozjazd kontraktu wychwytywany na etapie builda, nie w runtime.
