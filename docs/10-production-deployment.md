# Produkcyjne wdrożenie driperska.pl

Ten wariant jest przygotowany dla serwera `37.59.114.253`, na którym działają też inne
aplikacje. Kontener Driperskiej nasłuchuje wyłącznie na
`127.0.0.1:18080`; publiczne porty 80/443 pozostają pod kontrolą hostowego nginx, który
wybiera aplikację po domenie.

## 1. DNS i Cloudflare

W strefie `driperska.pl` dodaj:

| Typ | Nazwa | Wartość | Proxy |
|---|---|---|---|
| A | `@` | `37.59.114.253` | Proxied (pomarańczowa chmura) |
| CNAME | `www` | `driperska.pl` | Proxied |

Cloudflare zaleca proxy dla rekordów A/AAAA/CNAME obsługujących WWW:
[Proxy status](https://developers.cloudflare.com/dns/proxy-status/).

W **SSL/TLS → Origin Server** utwórz Origin Certificate dla
`driperska.pl` i `*.driperska.pl` (format PEM dla nginx). Zapisz certyfikat i klucz od
razu — prywatnego klucza nie da się później ponownie wyświetlić. Instrukcja:
[Cloudflare Origin CA](https://developers.cloudflare.com/ssl/origin-configuration/origin-ca/).

Po zainstalowaniu certyfikatu ustaw **SSL/TLS encryption mode: Full (strict)**. Ten tryb
sprawdza ważność certyfikatu origin i zgodność nazwy hosta:
[Full (strict)](https://developers.cloudflare.com/ssl/origin-configuration/ssl-modes/full-strict/).
Nie używaj trybu Flexible.

## 2. Przygotowanie Ubuntu

Zaloguj się na serwer i sprawdź, czy są Docker Engine, plugin Compose i nginx:

```bash
docker --version
docker compose version
nginx -v
```

Jeśli brakuje Dockera, użyj oficjalnej instrukcji dla Ubuntu. Plugin Compose instalowany
z repozytorium Dockera aktualizuje się razem z systemem:
[Docker Compose plugin](https://docs.docker.com/compose/install/linux/).

Utwórz odseparowany katalog aplikacji:

```bash
sudo install -d -o "$USER" -g "$USER" /opt/driperska
sudo install -d -m 700 /etc/nginx/ssl/driperska.pl
```

Inne aplikacje mogą dalej używać własnych portów lub vhostów nginx. Ważne tylko, aby
`18080` nie był już zajęty na loopback:

```bash
ss -ltn | grep ':18080' || true
```

## 3. Sekrety i konfiguracja

W `/opt/driperska/.env` umieść konfigurację na podstawie `.env.example`:

```dotenv
APP_VERSION=0.2.3
APP_PUBLIC_URL=https://driperska.pl
WEB_PORT=18080
DB_PASSWORD=<losowe: openssl rand -base64 36>
JWT_SECRET=<losowe: openssl rand -base64 48>
APP_ADMIN_USERNAME=admin
APP_ADMIN_EMAIL=admin@driperska.pl
APP_ADMIN_PASSWORD=<długie losowe hasło>
DDRAGON_SYNC_ON_STARTUP=true
```

Ustaw prawa:

```bash
chmod 600 /opt/driperska/.env
```

Nie kopiuj `.env` do repozytorium i nie umieszczaj sekretów w workflow.

## 4. Certyfikat i host nginx

Wklej Cloudflare Origin Certificate do:

- `/etc/nginx/ssl/driperska.pl/origin.pem`
- `/etc/nginx/ssl/driperska.pl/origin.key`

Następnie:

```bash
sudo chmod 600 /etc/nginx/ssl/driperska.pl/origin.key
sudo cp /opt/driperska/deploy/nginx/driperska.pl.conf /etc/nginx/sites-available/driperska.pl
sudo ln -s /etc/nginx/sites-available/driperska.pl /etc/nginx/sites-enabled/driperska.pl
sudo nginx -t
sudo systemctl reload nginx
```

Jeżeli dystrybucja nie używa `sites-available`, dołącz plik z `nginx.conf` przez
`include`. Konfiguracja ma wyłączone buforowanie i godzinny timeout, żeby strumień
losowania docierał natychmiast.

Firewall powinien wystawiać tylko używane usługi, typowo SSH, 80 i 443. Portu 18080 nie
otwieraj — Compose wiąże go z `127.0.0.1`.

## 5. Pierwsze wdrożenie ręczne

Po skopiowaniu repozytorium do `/opt/driperska`:

```bash
cd /opt/driperska
chmod +x deploy/scripts/*.sh
./deploy/scripts/deploy.sh
```

Skrypt:

1. tworzy skompresowany `pg_dump`,
2. buduje obrazy,
3. uruchamia kontenery bez usuwania wolumenów,
4. Flyway wykonuje wyłącznie brakujące migracje,
5. czeka, aż frontend i backend będą healthy.

Nigdy nie używa `docker compose down -v`. Dane PostgreSQL są w wolumenie `pgdata`,
a zdjęcia w `media`.

## 6. Automatyczny deploy z GitHub Actions

Workflow `.github/workflows/deploy.yml` czeka na udane zakończenie workflow CI dla `main`,
następnie kopiuje dokładnie sprawdzony commit przez SSH i wywołuje ten sam skrypt deployu. Utwórz środowisko GitHub
**production** (warto włączyć required reviewer) i dodaj sekrety:

| Secret | Przykład |
|---|---|
| `DEPLOY_HOST` | `37.59.114.253` |
| `DEPLOY_USER` | osobny użytkownik deploy |
| `DEPLOY_PATH` | `/opt/driperska` |
| `DEPLOY_SSH_KEY` | prywatny klucz Ed25519 |
| `DEPLOY_KNOWN_HOSTS` | pełny wynik `ssh-keyscan -H 37.59.114.253` zweryfikowany z fingerprintem serwera |

Sekrety środowiska są udostępniane dopiero jobom wskazującym dane environment:
[GitHub environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)
oraz [GitHub Actions secrets](https://docs.github.com/en/actions/reference/security/secrets).

Udany CI po pushu do `main` uruchamia deploy. Można go też wywołać ręcznie przez **Actions → Deploy
production → Run workflow**. Plik `.env` i katalog `backups` są wyłączone z rsync.

## 7. Migracje i aktualizacje bez utraty danych

Produkcja używa `ddl-auto=validate`; Hibernate nie zmienia schematu. Zmiany bazy dodawaj
wyłącznie jako następny plik:

```text
backend/src/main/resources/db/migration/V2__opis_zmiany.sql
backend/src/main/resources/db/migration/V3__kolejna_zmiana.sql
```

Zasadą dla działającego systemu jest **expand → deploy → contract**:

1. dodaj nowe nullable kolumny/tabele,
2. wdroż kod, który potrafi obsłużyć stary i nowy stan,
3. uzupełnij dane osobną migracją,
4. dopiero w późniejszej wersji zaostrzaj constrainty lub usuwaj stare pola.

Nie edytuj migracji, która została już wykonana na serwerze — Flyway sprawdza checksumy.

Backup ręczny:

```bash
cd /opt/driperska
./deploy/scripts/backup.sh
```

Kopie są w `/opt/driperska/backups`; skrypt zachowuje ostatnie 30. Ten katalog trzeba
dodatkowo replikować poza serwer (np. storage obiektowy), bo wolumen i lokalny backup nie
chronią przed awarią całej maszyny.

## 8. Rollback

Jeżeli nowa wersja kodu nie wstaje:

1. zachowaj logi: `docker compose logs --tail=200 backend web`,
2. wgraj poprzedni tag/commit,
3. uruchom ponownie `./deploy/scripts/deploy.sh`.

Migracje powinny być kompatybilne wstecz, więc zwykle nie cofa się bazy. Przy konieczności
pełnego restore najpierw zatrzymaj ruch, zrób jeszcze jeden backup i dopiero potem odtwórz
wybraną kopię. Restore zastępuje aktualne dane i dlatego nie jest częścią automatycznego
workflow.

## 9. Kontrola po wdrożeniu

```bash
cd /opt/driperska
docker compose ps
docker compose logs --tail=100 backend
curl -I http://127.0.0.1:18080/
curl -I https://driperska.pl/
```

Sprawdź ręcznie:

- logowanie admina i gracza,
- utworzenie gracza oraz kopiowanie wiadomości,
- start meczu z dziesięcioma kontami,
- głosy w dwóch przeglądarkach,
- upload zdjęcia,
- patch notes v0.1 na stronie głównej.

Gdy wszystko działa, zmień bootstrapowe hasło admina w `.env` na nowe losowe. Obecny
initializer synchronizuje je przy starcie, więc `.env` jest źródłem prawdy.