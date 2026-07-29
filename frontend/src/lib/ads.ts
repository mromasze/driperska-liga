/**
 * AdSense wiring.
 *
 * The publisher id is fixed for driperska.pl and is also what `public/ads.txt` declares. Slot ids
 * come from ad units created in the AdSense dashboard, and until they exist every slot here is null.
 *
 * A slot with no id renders nothing at all in production. That is deliberate: an empty bordered box
 * or a "space reserved for advertising" placeholder is precisely what makes a small site read as
 * spam, which is the one thing this must not do. In dev the slot draws a dashed outline instead, so
 * the layout can be judged before the units exist.
 *
 * To go live: create three display units in AdSense, then paste each `data-ad-slot` value below.
 * Nothing else needs changing — the loader script and the CSP allowances are already in place.
 */
export const ADSENSE_CLIENT = 'ca-pub-4170130757231322';

/**
 * Which consent management platform is in charge.
 *
 * 'google' — Google's own "Privacy & messaging" (Funding Choices), configured in the AdSense
 *   console. It is on Google's certified list and mints a valid IAB TCF string, which is the only
 *   way to actually satisfy the EU user consent policy for EEA/UK traffic. Google *delivers its
 *   message through adsbygoogle.js*, so on this path the script has to load before consent exists —
 *   gating it ourselves would mean the consent dialog never appears. Google then decides whether a
 *   personalised, non-personalised or no ad is served. Our own banner stays hidden, because two
 *   consent panels on one page is both a terrible experience and grounds for rejection.
 *
 * 'own' — the in-repo panel in components/consent. Looks better and genuinely enforces the choice
 *   (no script at all without consent, requestNonPersonalizedAds when personalisation is refused),
 *   but it is NOT certified and cannot produce a TC string, so Google may limit or withhold ads in
 *   the EEA/UK. Kept because it is a working fallback and makes the trade-off inspectable.
 *
 * Flipping this constant is the whole switch — nothing else needs touching.
 */
export type CmpMode = 'google' | 'own';
export const CMP_MODE: CmpMode = 'google';

export type AdSlotName =
  /** Home page, between the results grid and the ranking table. */
  | 'homeFeed'
  /** Home page, above the patch-notes card at the bottom. */
  | 'homeFooter'
  /** Player panel, below the dashboard. Never on the draft tab. */
  | 'panelDashboard';

export const AD_SLOTS: Record<AdSlotName, string | null> = {
  homeFeed: null,
  homeFooter: null,
  panelDashboard: null,
};

/** True once at least one unit is configured — used to skip loading the script entirely. */
export const ADS_CONFIGURED = Object.values(AD_SLOTS).some(Boolean);

export const ADSENSE_SCRIPT_ID = 'adsense-loader';

/**
 * Whether Google's script is already in the document.
 *
 * Consent withdrawal needs this: a script that has run cannot be unrun, so downgrading consent
 * mid-visit only takes real effect after a reload.
 */
export function adsenseLoaderPresent(): boolean {
  return document.getElementById(ADSENSE_SCRIPT_ID) !== null;
}
