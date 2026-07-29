import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import {
  ADSENSE_CLIENT,
  ADSENSE_SCRIPT_ID,
  AD_SLOTS,
  CMP_MODE,
  type AdSlotName,
} from '../../lib/ads';
import { adsAllowed, personalizedAdsAllowed, useConsentStore } from '../../store/consent';
import { cn } from '../../lib/cn';

/** The queue AdSense drains, which also carries the non-personalised flag as a property. */
interface AdsByGoogleQueue extends Array<unknown> {
  requestNonPersonalizedAds?: number;
}

declare global {
  interface Window {
    adsbygoogle?: AdsByGoogleQueue;
  }
}

/**
 * Injected on first use rather than sitting in index.html. Two reasons: the admin panel then never
 * loads Google's script at all, and — more importantly — nothing is fetched before the visitor has
 * actually consented.
 */
function ensureLoaderScript() {
  if (document.getElementById(ADSENSE_SCRIPT_ID)) return;
  const script = document.createElement('script');
  script.id = ADSENSE_SCRIPT_ID;
  script.async = true;
  script.crossOrigin = 'anonymous';
  script.src = `https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${ADSENSE_CLIENT}`;
  document.head.appendChild(script);
}

/**
 * One advertising unit, dressed to match the surrounding page.
 *
 * Kept deliberately quiet: a small muted label so it is never mistaken for league content, the
 * site's own hairline border, a height cap so a tall creative can't shove the page around, and no
 * placement above the fold or anywhere inside the live draft.
 *
 * Nothing here runs until the consent panel has an answer — see `store/consent`.
 */
export function AdSlot({
  name,
  className,
  maxHeight = 280,
}: {
  name: AdSlotName;
  className?: string;
  /** Cap in px. Stops a large creative from pushing content down the page. */
  maxHeight?: number;
}) {
  const slot = AD_SLOTS[name];
  const decision = useConsentStore((s) => s.decision);
  const reopen = useConsentStore((s) => s.reopen);
  const googleCmp = CMP_MODE === 'google';
  // Under Google's CMP the loader carries the consent dialog, so it must run before an answer
  // exists and Google — not this component — decides what kind of ad is served.
  const allowed = googleCmp || adsAllowed(decision);
  const personalized = googleCmp || personalizedAdsAllowed(decision);
  const pushed = useRef(false);

  useEffect(() => {
    if (!slot || !allowed || pushed.current) return;
    if (!personalized) {
      // Page-wide, and it has to be set before the loader evaluates — hence ahead of the injection.
      const queue = (window.adsbygoogle = window.adsbygoogle ?? []);
      queue.requestNonPersonalizedAds = 1;
    }
    ensureLoaderScript();
    pushed.current = true;
    try {
      (window.adsbygoogle = window.adsbygoogle ?? []).push({});
    } catch {
      // AdSense throws if this <ins> was already filled — happens on React's double mount in dev.
      // Nothing to recover: the unit either rendered or it didn't.
    }
  }, [slot, allowed, personalized]);

  // No answer yet, or ads refused: render nothing, request nothing.
  if (!allowed) return null;

  if (!slot) {
    if (!import.meta.env.DEV) return null;
    return (
      <div
        className={cn(
          'grid place-items-center rounded-md border border-dashed border-line p-4 text-center',
          className,
        )}
        style={{ minHeight: 90 }}
      >
        <div>
          <div className="kicker">Reklama</div>
          <p className="mt-1 text-xs text-text-lo">
            Slot <code>{name}</code> bez identyfikatora — uzupełnij <code>AD_SLOTS</code> w{' '}
            <code>lib/ads.ts</code>. W produkcji to miejsce jest puste.
          </p>
        </div>
      </div>
    );
  }

  return (
    <aside className={cn('overflow-hidden', className)} aria-label="Reklama">
      <div className="mb-1.5 flex items-baseline justify-between gap-3">
        <div className="kicker text-[0.6rem] opacity-70">
          Reklama{!personalized && ' · niespersonalizowana'}
        </div>
        {/* Wherever an ad appears, so does the way to understand or change it. The player panel has
            no footer, so without this both would be unreachable from behind the login. Under
            Google's CMP the panel is Google's to reopen, so this points at the policy instead. */}
        {googleCmp ? (
          <Link
            to="/privacy"
            className="text-[0.6rem] text-text-lo underline decoration-dotted underline-offset-2 hover:text-text"
          >
            Prywatność
          </Link>
        ) : (
          <button
            type="button"
            onClick={reopen}
            className="text-[0.6rem] text-text-lo underline decoration-dotted underline-offset-2 hover:text-text"
          >
            Ustawienia
          </button>
        )}
      </div>
      <ins
        className="adsbygoogle block"
        style={{ display: 'block', maxHeight }}
        data-ad-client={ADSENSE_CLIENT}
        data-ad-slot={slot}
        data-ad-format="auto"
        data-full-width-responsive="true"
      />
    </aside>
  );
}
