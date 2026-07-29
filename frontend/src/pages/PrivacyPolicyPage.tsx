import type { ReactNode } from 'react';

/**
 * Privacy policy.
 *
 * Written against what the code actually does, not from a template: every recipient listed below
 * corresponds to a real integration in this repository (Riot API, Discord bot, the Ollama vision
 * model behind the screenshot reader, Cloudflare Turnstile, Google Fonts, AdSense). Two items are
 * disclosed because they are genuine risks to the user rather than because a template asked for
 * them — the "remember me" credential storage and the password sent over Discord DM.
 *
 * If an integration is added or removed, this page is part of the change.
 */

const UPDATED = '29 lipca 2026';
const CONTACT = 'm.romaszewski@hotmail.com';

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section id={id} className="scroll-mt-24">
      <h2 className="font-display text-xl text-text-hi sm:text-2xl">{title}</h2>
      <div className="mt-3 space-y-3 text-sm leading-relaxed text-text">{children}</div>
    </section>
  );
}

function Table({ head, rows }: { head: string[]; rows: ReactNode[][] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[34rem] text-left text-sm">
        <thead>
          <tr className="kicker">
            {head.map((cell) => <th key={cell} className="px-2 py-2 align-bottom">{cell}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={index} className="border-t border-line align-top">
              {row.map((cell, cellIndex) => (
                <td key={cellIndex} className="px-2 py-2">{cell}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function PrivacyPolicyPage() {
  return (
    <div className="space-y-10">
      <header>
        <div className="kicker text-gold">Dokumenty</div>
        <h1 className="mt-1 font-display text-4xl">Polityka prywatności</h1>
        <p className="mt-3 max-w-2xl text-sm text-text-lo">
          Ta strona opisuje, jakie dane zbiera Driperska Liga, po co, komu je przekazuje i co możesz
          z tym zrobić. Ostatnia aktualizacja: {UPDATED}.
        </p>
      </header>

      <div className="glass p-5 sm:p-8">
        <div className="space-y-10">
          <Section id="administrator" title="1. Kto odpowiada za Twoje dane">
            <p>
              Administratorem danych osobowych jest osoba prywatna prowadząca serwis Driperska Liga
              pod adresem <span className="num">driperska.pl</span>, posługująca się pseudonimem{' '}
              <strong className="text-text-hi">mromasze</strong>. Serwis jest amatorską,
              niekomercyjną ligą League of Legends prowadzoną dla zamkniętej grupy znajomych.
            </p>
            <p className="text-text-lo">
              Pełne dane identyfikacyjne administratora (imię i nazwisko) udostępniamy na żądanie
              skierowane na adres kontaktowy poniżej — w szczególności osobom korzystającym z praw
              opisanych w punkcie 8 oraz organom nadzorczym.
            </p>
            <p>
              Kontakt we wszystkich sprawach dotyczących danych osobowych, w tym w celu skorzystania
              z praw opisanych w punkcie 8:{' '}
              <a href={`mailto:${CONTACT}`} className="text-gold hover:underline">{CONTACT}</a>.
            </p>
          </Section>

          <Section id="dane" title="2. Jakie dane zbieramy">
            <p>
              Konta zakłada administrator ligi — nie ma tu samodzielnej rejestracji. Część danych
              podajesz sam w panelu gracza i są one opcjonalne.
            </p>
            <Table
              head={['Kategoria', 'Konkretne dane', 'Skąd']}
              rows={[
                [
                  'Konto',
                  'Login, adres e-mail, skrót hasła (hasło nie jest przechowywane jawnie w bazie), rola, data utworzenia i ostatniego logowania.',
                  'Zakłada administrator',
                ],
                [
                  'Profil gracza',
                  'Pseudonim (nick), opcjonalnie imię i nazwisko, Riot ID oraz identyfikatory Riot (PUUID, summoner ID), nazwa i identyfikator konta Discord, preferowane pozycje, ulubieni bohaterowie, opis „o mnie”, zdjęcie profilowe, link do OP.GG.',
                  'Administrator i Ty w panelu gracza',
                ],
                [
                  'Dane z meczów',
                  'Wyniki, statystyki (zabójstwa, śmierci, asysty, CS, złoto, obrażenia, wizja), wybrani bohaterowie, oceny wyniku (PR), punkty ligowe (LP), wyróżnienia MVP/ACE, ranking i MMR.',
                  'Wpisywane przez administratora lub pobierane z API Riot',
                ],
                [
                  'Oceny od innych graczy',
                  'Głosy „w górę / w dół” oraz krótkie komentarze wystawiane po meczu przez pozostałych uczestników.',
                  'Inni gracze',
                ],
                [
                  'Obecność',
                  'Odpowiedzi „będę / nie będę / może” na zaplanowane mecze, także klikane przez przyciski na Discordzie.',
                  'Ty',
                ],
                [
                  'Pliki',
                  'Zdjęcie profilowe, klipy z zagrywek, powtórki meczów (.rofl), zrzuty ekranu z podsumowania gry.',
                  'Ty lub administrator',
                ],
                [
                  'Dane techniczne',
                  'Adres IP oraz standardowe informacje o przeglądarce w logach serwera i usług pośredniczących, identyfikator sesji.',
                  'Automatycznie',
                ],
              ]}
            />
            <p className="text-text-lo">
              Nie zbieramy danych o zdrowiu, przekonaniach, pochodzeniu ani innych danych szczególnej
              kategorii. Nie prowadzimy profilowania wpływającego na Twoją sytuację prawną. Nie
              sprzedajemy danych nikomu.
            </p>
          </Section>

          <Section id="cele" title="3. Po co i na jakiej podstawie">
            <Table
              head={['Cel', 'Podstawa prawna (RODO)']}
              rows={[
                [
                  'Prowadzenie konta, logowanie, udział w meczach, losowanie składów, draft i ranking.',
                  'Art. 6 ust. 1 lit. b — wykonanie umowy o korzystanie z serwisu.',
                ],
                [
                  'Publiczna prezentacja wyników, rankingu i profili graczy — to istota ligi.',
                  'Art. 6 ust. 1 lit. b oraz lit. f — uzasadniony interes w prowadzeniu tabeli rozgrywek.',
                ],
                [
                  'Bezpieczeństwo: ochrona logowania przed botami, logi serwera, wykrywanie nadużyć.',
                  'Art. 6 ust. 1 lit. f — uzasadniony interes.',
                ],
                [
                  'Powiadomienia na Discordzie o meczach i wynikach.',
                  'Art. 6 ust. 1 lit. b — obsługa udziału w rozgrywkach.',
                ],
                [
                  'Wyświetlanie reklam i korzystanie z plików cookie w tym celu.',
                  'Art. 6 ust. 1 lit. a — Twoja zgoda, którą możesz wycofać w każdej chwili.',
                ],
                [
                  'Opcjonalne pola profilu (imię i nazwisko, opis, zdjęcie, OP.GG).',
                  'Art. 6 ust. 1 lit. a — zgoda wyrażona przez samo ich wypełnienie; możesz je usunąć.',
                ],
              ]}
            />
          </Section>

          <Section id="odbiorcy" title="4. Komu przekazujemy dane">
            <p>
              Serwis korzysta z usług zewnętrznych. Poniżej pełna lista podmiotów, które mogą
              otrzymać Twoje dane, oraz zakres tego przekazania.
            </p>
            <Table
              head={['Podmiot', 'Co dostaje', 'Po co']}
              rows={[
                [
                  <>Google Ireland Ltd. / Google LLC <span className="text-text-lo">(AdSense)</span></>,
                  'Adres IP, identyfikatory plików cookie, informacje o przeglądarce i odwiedzanych podstronach.',
                  'Wyświetlanie reklam. Wyłącznie po wyrażeniu zgody w panelu zgód.',
                ],
                [
                  <>Google <span className="text-text-lo">(Fonts)</span></>,
                  'Adres IP i informacje o przeglądarce, przy pobieraniu krojów pisma.',
                  'Wygląd strony. Fonty są pobierane z serwerów Google przy każdym wejściu.',
                ],
                [
                  'Riot Games, Inc.',
                  'Riot ID i identyfikatory Riot, dane rozegranych meczów, kody turniejowe.',
                  'Tworzenie lobby turniejowego i pobieranie wyników meczów.',
                ],
                [
                  'Discord Netherlands B.V. / Discord Inc.',
                  'Nazwa i identyfikator konta Discord, treść wysyłanych wiadomości, kliknięcia przycisków obecności.',
                  'Wysyłanie danych dostępowych, ogłoszeń o meczach, kart wyników i zbieranie potwierdzeń obecności.',
                ],
                [
                  'Cloudflare, Inc.',
                  'Adres IP, informacje o przeglądarce, dane weryfikacji Turnstile.',
                  'Ochrona logowania przed botami oraz obsługa ruchu do serwera.',
                ],
                [
                  <>Ollama, Inc. <span className="text-text-lo">(model wizyjny)</span></>,
                  'Zrzuty ekranu z podsumowania gry — zawierają pseudonimy graczy i statystyki.',
                  'Automatyczny odczyt wyników ze screenshotów. Dotyczy tylko sytuacji, gdy administrator użyje tej funkcji.',
                ],
              ]}
            />
            <p className="rounded-md border border-[color:var(--pending)]/40 bg-[color:var(--pending)]/10 p-3 text-pending">
              Część z tych podmiotów przetwarza dane w Stanach Zjednoczonych i innych krajach poza
              Europejskim Obszarem Gospodarczym. Przekazanie odbywa się na podstawie decyzji Komisji
              Europejskiej o odpowiednim stopniu ochrony (EU–US Data Privacy Framework) lub
              standardowych klauzul umownych stosowanych przez te podmioty.
            </p>
          </Section>

          <Section id="cookies" title="5. Pliki cookie i dane w przeglądarce">
            <p>
              Sama liga nie używa plików cookie do śledzenia. Do działania i do zapamiętania Twoich
              wyborów wykorzystuje pamięć lokalną przeglądarki (localStorage), która nie jest
              wysyłana na serwer:
            </p>
            <ul className="ml-4 list-disc space-y-1.5 text-text">
              <li>
                <strong className="text-text-hi">sesja</strong> — tokeny logowania, żeby nie trzeba
                było logować się przy każdym wejściu;
              </li>
              <li>
                <strong className="text-text-hi">zgoda na reklamy</strong> — Twój wybór z panelu
                zgód, żeby nie pytać ponownie;
              </li>
              <li>
                <strong className="text-text-hi">ustawienia</strong> — głośność dźwięków draftu.
              </li>
            </ul>
            <p>
              <strong className="text-text-hi">Pliki cookie reklamowe</strong> ustawia Google po
              wyrażeniu zgody. Służą do wyświetlania reklam, ograniczania powtarzalności i wykrywania
              nadużyć, a przy zgodzie na personalizację również do dopasowania treści reklam. Ich
              opis prowadzi Google:{' '}
              <a
                href="https://policies.google.com/technologies/partner-sites"
                target="_blank"
                rel="noopener noreferrer"
                className="text-gold hover:underline"
              >
                jak Google wykorzystuje dane z witryn partnerskich
              </a>
              . Zgodę możesz zmienić lub wycofać w każdej chwili linkiem w stopce strony.
            </p>
            <p className="rounded-md border border-[color:var(--red)]/40 bg-[color:var(--red-bg)] p-3">
              <strong className="text-text-hi">Ważne, jeśli zaznaczysz „Zapamiętaj mnie”.</strong>{' '}
              Sesje w tym serwisie wygasają przy każdym restarcie serwera, dlatego ta opcja zapisuje
              Twój login i hasło w pamięci lokalnej przeglądarki, aby odtworzyć sesję automatycznie.
              Oznacza to, że hasło jest odczytywalne dla każdego, kto ma dostęp do Twojego profilu
              przeglądarki lub urządzenia. Opcja jest domyślnie wyłączona, włącza się tylko przez
              Twoje zaznaczenie i jest usuwana po wylogowaniu. Nie używaj jej na komputerze
              współdzielonym.
            </p>
            <p className="rounded-md border border-[color:var(--pending)]/40 bg-[color:var(--pending)]/10 p-3 text-pending">
              Przy zakładaniu konta oraz przy resecie hasła bot wysyła Ci login i wygenerowane hasło
              w wiadomości prywatnej na Discordzie. Hasło jest tam widoczne jawnie i pozostaje w
              historii tej rozmowy, dopóki jej nie usuniesz. Zalecamy usunięcie wiadomości po
              pierwszym zalogowaniu.
            </p>
          </Section>

          <Section id="przechowywanie" title="6. Jak długo przechowujemy dane">
            <ul className="ml-4 list-disc space-y-1.5">
              <li>
                Dane konta i profilu — przez czas istnienia konta w lidze. Po usunięciu konta
                usuwamy je wraz z plikami, które wgrałeś.
              </li>
              <li>
                Wyniki i statystyki meczów — pozostają jako element historii rozgrywek. Po usunięciu
                konta pseudonim może zostać zanonimizowany, ale sam wynik meczu, w którym brałeś
                udział, zostaje, ponieważ dotyczy również pozostałych dziewięciu graczy.
              </li>
              <li>
                Logi techniczne — przez czas potrzebny do diagnostyki i bezpieczeństwa, po czym są
                nadpisywane.
              </li>
              <li>
                Kopie zapasowe bazy danych tworzone są przy wdrożeniach. Usunięcie danych obejmuje
                również kolejne kopie; starsze kopie wygasają wraz z rotacją.
              </li>
            </ul>
          </Section>

          <Section id="bezpieczenstwo" title="7. Bezpieczeństwo">
            <p>
              Połączenie ze stroną jest szyfrowane (HTTPS). Hasła w bazie są przechowywane wyłącznie
              jako skróty kryptograficzne. Dostęp do panelu administracyjnego mają tylko konta z rolą
              administratora lub edytora. Formularz logowania jest chroniony weryfikacją Cloudflare
              Turnstile.
            </p>
            <p className="text-text-lo">
              Serwis jest projektem amatorskim, prowadzonym przez jedną osobę. Nie zakładaj poziomu
              zabezpieczeń usługi komercyjnej — nie używaj tutaj hasła, którego używasz gdziekolwiek
              indziej.
            </p>
          </Section>

          <Section id="prawa" title="8. Twoje prawa">
            <p>W odniesieniu do swoich danych masz prawo do:</p>
            <ul className="ml-4 list-disc space-y-1.5">
              <li>dostępu do danych i otrzymania ich kopii;</li>
              <li>sprostowania danych nieprawidłowych lub nieaktualnych;</li>
              <li>usunięcia danych („prawo do bycia zapomnianym”);</li>
              <li>ograniczenia przetwarzania;</li>
              <li>przenoszenia danych w powszechnie używanym formacie;</li>
              <li>
                sprzeciwu wobec przetwarzania opartego na uzasadnionym interesie;
              </li>
              <li>
                wycofania zgody w każdej chwili — dotyczy reklam oraz opcjonalnych pól profilu.
                Wycofanie nie wpływa na zgodność z prawem przetwarzania sprzed wycofania.
              </li>
            </ul>
            <p>
              Żądanie wystarczy wysłać na{' '}
              <a href={`mailto:${CONTACT}`} className="text-gold hover:underline">{CONTACT}</a>.
              Odpowiadamy najpóźniej w ciągu miesiąca.
            </p>
            <p>
              Jeśli uznasz, że przetwarzamy Twoje dane niezgodnie z prawem, możesz złożyć skargę do
              Prezesa Urzędu Ochrony Danych Osobowych, ul. Stawki 2, 00-193 Warszawa.
            </p>
          </Section>

          <Section id="dzieci" title="9. Wiek użytkowników">
            <p>
              Serwis nie jest przeznaczony dla dzieci poniżej 16 lat i nie zbieramy świadomie ich
              danych. Konta zakłada administrator wyłącznie dla znanych sobie osób. Jeśli okaże się,
              że konto należy do osoby poniżej 16 lat bez zgody opiekuna, zostanie usunięte —
              zgłoszenia przyjmujemy pod adresem kontaktowym powyżej.
            </p>
          </Section>

          <Section id="zmiany" title="10. Zmiany polityki">
            <p>
              Jeśli dodamy lub usuniemy integrację, która wpływa na przetwarzanie danych, ta strona
              zostanie zaktualizowana wraz z datą u góry. Istotne zmiany ogłaszamy na Discordzie
              ligi.
            </p>
          </Section>
        </div>
      </div>
    </div>
  );
}
