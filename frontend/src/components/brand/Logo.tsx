import { useId, type ReactNode } from 'react';

/* ============================================================
   Driperska Liga brand marks.

   One set of letterforms ("DL"), four dressings. The geometry lives on a 256×256 grid and every
   stroke — stem, bars, and the 45° chamfers on the D's bowl — measures 28 units perpendicular, so
   the corners neither thicken nor thin when the mark is scaled to a favicon.

   Colours are the Rift Nights tokens from index.css. The gold gradient matches
   `.text-gradient-gold` exactly (#ffe4a3 → #f2c14e → #b98a2e), so a mark sitting next to the
   wordmark reads as the same metal.

   Which mark goes where:
     Hex   — every navbar (public, player, admin). The densest variant; wants ≥32px.
     Crest — standalone hero mark (login, home hero). Evolution of the old favicon.
     Tile  — highest-contrast variant. Discord avatar, PWA icon, anything on foreign backgrounds.
     Mono  — no frame, scales to 16px. Favicon, footer, watermarks.
   ============================================================ */

/** The D, with its counter as a second subpath — needs fill-rule="evenodd" to punch through. */
const D_PATH = 'M40,68 H104 L130,94 V162 L104,188 H40 Z M68,96 H92 L102,106 V150 L92,160 H68 Z';
const L_PATH = 'M150,68 H178 V160 H216 V188 H150 Z';
const HEX_PATH = 'M128,20 L221,74 L221,182 L128,236 L35,182 L35,74 Z';
const DIAMOND_PATH = 'M128,14 L242,128 L128,242 L14,128 Z';
const DIAMOND_INNER_PATH = 'M128,30 L226,128 L128,226 L30,128 Z';

export type MarkProps = {
  /** Rendered edge length in px. The mark is always square. */
  size?: number;
  className?: string;
  /**
   * Accessible name. Omit for decorative marks sitting next to the wordmark — they get
   * aria-hidden instead, so screen readers don't announce the brand twice.
   */
  title?: string;
};

/**
 * React's useId() returns colon-wrapped values (`:r0:`). Those are legal HTML ids but brittle
 * inside `url(#…)` and impossible to use in a CSS selector, so strip them.
 */
function useMarkId(prefix: string): string {
  const raw = useId();
  return `${prefix}-${raw.replace(/:/g, '')}`;
}

function MarkSvg({ size = 32, className, title, children }: MarkProps & { children: ReactNode }) {
  return (
    <svg
      viewBox="0 0 256 256"
      width={size}
      height={size}
      className={className}
      role={title ? 'img' : undefined}
      aria-hidden={title ? undefined : true}
      focusable="false"
    >
      {title ? <title>{title}</title> : null}
      {children}
    </svg>
  );
}

function GoldGradient({ id }: { id: string }) {
  return (
    <linearGradient id={id} x1="0" y1="0" x2="0.3" y2="1">
      <stop offset="0" stopColor="#ffe4a3" />
      <stop offset="0.5" stopColor="#f2c14e" />
      <stop offset="1" stopColor="#b98a2e" />
    </linearGradient>
  );
}

/** The DL itself, scaled about the canvas centre so every framed variant stays optically centred. */
function Letters({ fill, scale = 1 }: { fill: string; scale?: number }) {
  return (
    <g transform={`translate(128,128) scale(${scale}) translate(-128,-128)`}>
      <path fill={fill} fillRule="evenodd" d={D_PATH} />
      <path fill={fill} d={L_PATH} />
    </g>
  );
}

/**
 * Bare monogram on a transparent background — the only variant that survives 16px.
 * `flat` drops the gradient for a single gold, which reads cleaner at favicon sizes and is what
 * the exported favicon.svg uses.
 */
export function LogoMono({ flat = false, ...props }: MarkProps & { flat?: boolean }) {
  const gradientId = useMarkId('dl-mono');
  return (
    <MarkSvg {...props}>
      {flat ? null : (
        <defs>
          <GoldGradient id={gradientId} />
        </defs>
      )}
      <Letters fill={flat ? '#f2c14e' : `url(#${gradientId})`} />
    </MarkSvg>
  );
}

/**
 * Diamond frame with a cyan hairline inside it. Deliberately close to the pre-0.4.6 favicon
 * (a rhombus inside a rhombus) so the rebrand reads as an evolution rather than a reset.
 */
export function LogoCrest(props: MarkProps) {
  const gradientId = useMarkId('dl-crest');
  const ambientId = useMarkId('dl-crest-amb');
  return (
    <MarkSvg {...props}>
      <defs>
        <GoldGradient id={gradientId} />
        <radialGradient id={ambientId} cx="0.5" cy="0.5" r="0.5">
          <stop offset="0" stopColor="#9b8cff" stopOpacity="0.22" />
          <stop offset="1" stopColor="#9b8cff" stopOpacity="0" />
        </radialGradient>
      </defs>
      {/* Violet ambient light, echoing the radial glows on the page background. */}
      <circle cx="128" cy="128" r="120" fill={`url(#${ambientId})`} />
      <path fill="none" stroke={`url(#${gradientId})`} strokeWidth="7" d={DIAMOND_PATH} />
      <path fill="none" stroke="#35e0e0" strokeOpacity="0.45" strokeWidth="1.5" d={DIAMOND_INNER_PATH} />
      <Letters fill={`url(#${gradientId})`} scale={0.58} />
    </MarkSvg>
  );
}

/**
 * Solid gold rhombus with the letters knocked out in the deepest background navy. Maximum contrast,
 * and the only variant that holds up on a background we don't control (Discord, app launchers).
 * The knockout stays hard-coded to #070912 rather than var(--bg) on purpose — this mark has to
 * survive being exported to PNG and dropped anywhere.
 */
export function LogoTile(props: MarkProps) {
  const gradientId = useMarkId('dl-tile');
  return (
    <MarkSvg {...props}>
      <defs>
        <GoldGradient id={gradientId} />
      </defs>
      <rect
        x="46"
        y="46"
        width="164"
        height="164"
        rx="26"
        fill={`url(#${gradientId})`}
        transform="rotate(45 128 128)"
      />
      <Letters fill="#070912" scale={0.58} />
    </MarkSvg>
  );
}

/**
 * Hexagonal badge carrying the same 1px tech grid as the hero panels (`.grid-tex`) and a cyan
 * bloom standing in for `--glow-cyan`. The interior uses var(--bg-1) so it stays correct if the
 * light theme is ever wired up. Grid and bloom vanish below ~28px, so keep it at navbar size.
 */
export function LogoHex(props: MarkProps) {
  const gradientId = useMarkId('dl-hex');
  const gridId = useMarkId('dl-hex-grid');
  const clipId = useMarkId('dl-hex-clip');
  const glowId = useMarkId('dl-hex-glow');
  return (
    <MarkSvg {...props}>
      <defs>
        <GoldGradient id={gradientId} />
        <pattern id={gridId} width="18" height="18" patternUnits="userSpaceOnUse">
          <path d="M18,0 V18 M0,18 H18" fill="none" stroke="#ffffff" strokeOpacity="0.06" strokeWidth="1" />
        </pattern>
        <clipPath id={clipId}>
          <path d={HEX_PATH} />
        </clipPath>
        <filter id={glowId} x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="5" />
        </filter>
      </defs>
      <g clipPath={`url(#${clipId})`}>
        <rect width="256" height="256" fill="var(--bg-1)" />
        <rect width="256" height="256" fill={`url(#${gridId})`} />
      </g>
      <path
        fill="none"
        stroke="#35e0e0"
        strokeOpacity="0.5"
        strokeWidth="4"
        filter={`url(#${glowId})`}
        d={HEX_PATH}
      />
      <path fill="none" stroke={`url(#${gradientId})`} strokeWidth="6" d={HEX_PATH} />
      {/* Cyan ticks on the top and bottom vertices. */}
      <path
        fill="none"
        stroke="#35e0e0"
        strokeWidth="3"
        strokeLinecap="square"
        d="M112,29 L128,38 L144,29 M112,227 L128,218 L144,227"
      />
      <Letters fill={`url(#${gradientId})`} scale={0.6} />
    </MarkSvg>
  );
}
