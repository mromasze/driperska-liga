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
