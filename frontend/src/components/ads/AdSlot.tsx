import { useEffect, useRef } from 'react';
import { ADSENSE_CLIENT, AD_SLOTS, type AdSlotName } from '../../lib/ads';
import { cn } from '../../lib/cn';

declare global {
  interface Window {
    adsbygoogle?: unknown[];
  }
}

const SCRIPT_ID = 'adsense-loader';

/**
 * Injected on first use rather than sitting in index.html, so the admin panel never loads Google's
 * script at all — no ads there, and no third-party request from the pages used to run the league.
 */
function ensureLoaderScript() {
  if (document.getElementById(SCRIPT_ID)) return;
  const script = document.createElement('script');
  script.id = SCRIPT_ID;
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
  const pushed = useRef(false);

  useEffect(() => {
    if (!slot || pushed.current) return;
    ensureLoaderScript();
    pushed.current = true;
    try {
      (window.adsbygoogle = window.adsbygoogle ?? []).push({});
    } catch {
      // AdSense throws if this <ins> was already filled — happens on React's double mount in dev.
      // Nothing to recover: the unit either rendered or it didn't.
    }
  }, [slot]);

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
      <div className="kicker mb-1.5 text-[0.6rem] opacity-70">Reklama</div>
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
