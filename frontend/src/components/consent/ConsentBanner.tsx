import { useEffect, useState } from 'react';
import {
  adsAllowed,
  personalizedAdsAllowed,
  publishConsentMode,
  useConsentStore,
  type ConsentDecision,
} from '../../store/consent';
import { adsenseLoaderPresent, CMP_MODE } from '../../lib/ads';
import { Button } from '../ui/Button';
import { LogoHex } from '../brand/Logo';

/** Google's own description of what its ad partners do with the data. */
const GOOGLE_PARTNER_URL = 'https://policies.google.com/technologies/partner-sites';

/**
 * Consent panel for advertising.
 *
 * Deliberately not a dark pattern: "Tylko niezbędne" is styled exactly as prominently as
 * "Zaakceptuj", refusal is one click from the first screen, and the panel never blocks the page
 * behind a modal scrim — the league's own content stays readable and usable either way. That is
 * both the decent thing and what the GDPR expects of a freely given choice.
 *
 * Only advertising is covered, because advertising is the only third party the site has. There is no
 * analytics, no tag manager and no social embed, so there is nothing else to ask about, and padding
 * the panel with purposes we don't use would be theatre.
 */
export function ConsentBanner() {
  const decision = useConsentStore((s) => s.decision);
  const reopened = useConsentStore((s) => s.reopened);
  const decide = useConsentStore((s) => s.decide);
  const close = useConsentStore((s) => s.close);
  const [details, setDetails] = useState(false);

  // Google's CMP owns the dialog on that path. Two consent panels on one page is a terrible
  // experience and grounds for rejecting the site, so this one stands down entirely.
  const open = CMP_MODE === 'own' && (decision === null || reopened);

  // Mirror a restored decision into Consent Mode on load, so the declared state matches the stored
  // one even when the user never interacts this visit.
  useEffect(() => {
    if (decision) publishConsentMode(decision);
  }, [decision]);

  useEffect(() => {
    if (!open) setDetails(false);
  }, [open]);

  if (!open) return null;

  const choose = (value: ConsentDecision) => {
    const downgrade = adsAllowed(decision) && !personalizedAdsAllowed(value) && decision !== value;
    decide(value);
    publishConsentMode(value);
    // A script that has run cannot be unrun: once adsbygoogle.js is in the document it keeps its
    // cookies and its personalisation for the rest of the page's life. Withdrawing or narrowing
    // consent is therefore only honest after a reload, so do exactly that rather than leaving a
    // panel that claims one thing while the page does another.
    if (downgrade && adsenseLoaderPresent()) window.location.reload();
  };

  return (
    <div
      className="fixed inset-x-0 bottom-0 z-[90] p-3 sm:p-4"
      role="dialog"
      aria-modal="false"
      aria-labelledby="consent-title"
    >
      <div className="panel mx-auto max-w-content p-5 shadow-pop sm:p-6">
        <div className="flex items-start gap-4">
          <LogoHex size={40} className="hidden shrink-0 sm:block" />
          <div className="min-w-0 flex-1">
            <div className="kicker text-gold">Prywatność</div>
            <h2 id="consent-title" className="mt-1 font-display text-xl">
              Zgoda na reklamy
            </h2>
            <p className="mt-2 text-sm text-text">
              Driperska Liga utrzymuje się z reklam Google AdSense. Do ich wyświetlania Google używa
              plików cookie — do pomiaru i ograniczania powtarzalności, a przy zgodzie na
              personalizację również do dopasowania treści reklam.{' '}
              <a
                href={GOOGLE_PARTNER_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="text-gold hover:underline"
              >
                Jak Google wykorzystuje te dane →
              </a>
            </p>
            <p className="mt-2 text-xs text-text-lo">
              Bez zgody skrypt reklamowy nie jest w ogóle pobierany. Ranking, mecze, draft i panel
              gracza działają identycznie w każdym wariancie — wybór nie ogranicza dostępu do ligi.
            </p>

            {details && (
              <dl className="mt-4 space-y-3 border-t border-line pt-4 text-sm">
                <div>
                  <dt className="font-semibold text-text-hi">Reklamy niespersonalizowane</dt>
                  <dd className="text-text-lo">
                    Reklamy dobierane bez profilu — na podstawie treści strony i przybliżonej
                    lokalizacji. Google nadal zapisuje cookie, żeby liczyć wyświetlenia i wykrywać
                    nadużycia. To wariant „Tylko niezbędne”.
                  </dd>
                </div>
                <div>
                  <dt className="font-semibold text-text-hi">Reklamy spersonalizowane</dt>
                  <dd className="text-text-lo">
                    Google i jego partnerzy mogą korzystać z Twojej historii przeglądania, żeby
                    dopasować reklamy. To wariant „Zaakceptuj wszystko”.
                  </dd>
                </div>
                <div>
                  <dt className="font-semibold text-text-hi">Czego nie robimy</dt>
                  <dd className="text-text-lo">
                    Strona nie ma analityki, menedżera tagów ani wtyczek społecznościowych. Poza
                    AdSense żaden zewnętrzny podmiot nie dostaje niczego. Zgodę możesz zmienić w
                    każdej chwili linkiem „Prywatność” w stopce.
                  </dd>
                </div>
              </dl>
            )}
          </div>
        </div>

        <div className="mt-5 flex flex-wrap items-center gap-2">
          {/* Accept and refuse carry equal visual weight on purpose. */}
          <Button variant="gold" onClick={() => choose('accepted')}>
            Zaakceptuj wszystko
          </Button>
          <Button variant="gold" onClick={() => choose('basic')}>
            Tylko niezbędne
          </Button>
          <Button variant="ghost" onClick={() => choose('refused')}>
            Bez reklam
          </Button>
          <button
            type="button"
            onClick={() => setDetails((value) => !value)}
            className="ml-auto text-xs text-text-lo underline decoration-dotted underline-offset-4 hover:text-text"
          >
            {details ? 'Ukryj szczegóły' : 'Szczegóły'}
          </button>
          {decision !== null && (
            <button
              type="button"
              onClick={close}
              className="text-xs text-text-lo underline decoration-dotted underline-offset-4 hover:text-text"
            >
              Zamknij
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * Footer entry point for the consent decision. Renders nothing under Google's CMP, which brings its
 * own way back into the dialog — there the footer carries the privacy policy link instead.
 */
export function ConsentSettingsButton() {
  const reopen = useConsentStore((s) => s.reopen);
  if (CMP_MODE !== 'own') return null;
  return (
    <button type="button" onClick={reopen} className="text-gold hover:underline">
      Ustawienia reklam
    </button>
  );
}
