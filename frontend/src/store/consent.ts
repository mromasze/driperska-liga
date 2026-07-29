import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * Advertising consent.
 *
 * ---------------------------------------------------------------------------------------------
 * What this is, and what it is not — read before trusting it for compliance.
 *
 * Google's EU user consent policy requires a CMP from Google's *certified* list for serving ads
 * to EEA/UK users, and that certification runs through IAB Europe's TCF: a registered CMP ID, a
 * signed vendor list, and a TC string that Google validates against the register. Code written
 * in this repository cannot mint a valid TC string, so this is **not** a TCF CMP — picking
 * "certified CMP" in the AdSense console does not turn it into one. See CHANGELOG v0.4.8 for the
 * consequences and the options.
 *
 * What it does do is enforce the user's answer for real, using AdSense's own non-TCF signal:
 *
 *   no decision yet   → the AdSense script is never injected. Zero third-party requests.
 *   refused           → same. No script, no cookies, no ads.
 *   basic ads only    → script loads with `requestNonPersonalizedAds = 1`.
 *   accepted          → script loads normally.
 *
 * Consent Mode v2 signals are additionally pushed to `dataLayer`. Nothing reads them today (the
 * site loads no gtag.js and no analytics), but it costs nothing and means a real CMP or a future
 * gtag install finds the state already expressed in the format it expects.
 * ---------------------------------------------------------------------------------------------
 */

export type ConsentDecision =
  /** Personalised ads: everything granted. */
  | 'accepted'
  /** Ads allowed, but not personalised — AdSense runs in non-personalised mode. */
  | 'basic'
  /** No advertising at all; the loader script is never fetched. */
  | 'refused';

/**
 * Bump when the question itself changes (a new purpose, a new vendor). Stored decisions from an
 * older version are discarded and everyone is asked again — silently reusing an answer to a
 * different question is exactly the abuse consent banners are criticised for.
 */
export const CONSENT_VERSION = 1;

interface ConsentState {
  decision: ConsentDecision | null;
  version: number;
  decidedAt: string | null;
  /** True while the user has the panel open from the footer, even after deciding once. */
  reopened: boolean;
  decide: (decision: ConsentDecision) => void;
  reopen: () => void;
  close: () => void;
}

export const useConsentStore = create<ConsentState>()(
  persist(
    (set) => ({
      decision: null,
      version: CONSENT_VERSION,
      decidedAt: null,
      reopened: false,
      decide: (decision) =>
        set({
          decision,
          version: CONSENT_VERSION,
          decidedAt: new Date().toISOString(),
          reopened: false,
        }),
      reopen: () => set({ reopened: true }),
      close: () => set({ reopened: false }),
    }),
    {
      name: 'driperska-consent',
      // `reopened` is UI state for one visit, not part of the record of what was agreed to.
      partialize: (state) => ({
        decision: state.decision,
        version: state.version,
        decidedAt: state.decidedAt,
      }),
      // An answer given to an older version of the question is not an answer to this one.
      migrate: (persisted) => {
        const stored = persisted as Partial<ConsentState> | undefined;
        if (!stored || stored.version !== CONSENT_VERSION) {
          return { decision: null, version: CONSENT_VERSION, decidedAt: null, reopened: false };
        }
        return stored as ConsentState;
      },
      version: CONSENT_VERSION,
    },
  ),
);

/** Whether the AdSense loader may be fetched at all. */
export function adsAllowed(decision: ConsentDecision | null): boolean {
  return decision === 'accepted' || decision === 'basic';
}

/** Whether ads may be personalised. Only ever true on an explicit full acceptance. */
export function personalizedAdsAllowed(decision: ConsentDecision | null): boolean {
  return decision === 'accepted';
}

type ConsentSignal = 'granted' | 'denied';

interface ConsentModePayload {
  ad_storage: ConsentSignal;
  ad_user_data: ConsentSignal;
  ad_personalization: ConsentSignal;
}

declare global {
  interface Window {
    dataLayer?: unknown[];
  }
}

function pushConsentMode(command: 'default' | 'update', payload: ConsentModePayload) {
  window.dataLayer = window.dataLayer ?? [];
  // gtag.js replays this queue by unpacking each entry positionally as an Arguments object, not as
  // an array — the two are not interchangeable for it. An array-like with a numeric length keeps
  // that shape without needing a `function(){ dataLayer.push(arguments) }` shim just to build one.
  window.dataLayer.push({ 0: 'consent', 1: command, 2: payload, length: 3 });
}

/**
 * Denies everything before any ad code can run. Called from main.tsx ahead of the first render,
 * which is early enough because the AdSense script is only ever injected later, by a mounted slot.
 */
export function initConsentMode() {
  pushConsentMode('default', {
    ad_storage: 'denied',
    ad_user_data: 'denied',
    ad_personalization: 'denied',
  });
}

/** Mirrors a decision into Consent Mode. Enforcement lives in AdSlot; this is the declaration. */
export function publishConsentMode(decision: ConsentDecision) {
  const ads: ConsentSignal = adsAllowed(decision) ? 'granted' : 'denied';
  pushConsentMode('update', {
    ad_storage: ads,
    ad_user_data: ads,
    ad_personalization: personalizedAdsAllowed(decision) ? 'granted' : 'denied',
  });
}
