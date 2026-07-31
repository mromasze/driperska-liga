/**
 * The podium palette — gold, silver, bronze — in one place.
 *
 * Three surfaces dress the top three now (the ranking table, the leader card and a player's own
 * profile), and they have to agree: a second place recognised by a silver frame on one page and a
 * grey one on another just looks broken. {@link RankMedal} reads from here too, so the medal in a
 * table row and the frame around it are cut from the same colour.
 */
export interface PodiumLook {
  /** Ordinal label in Polish, e.g. "2. miejsce". */
  label: string;
  /** Flat accent for text, borders and glows. */
  color: string;
  /** Metallic fill for the medal itself. */
  gradient: string;
  /** Readable foreground on top of {@link gradient}. */
  ink: string;
  glow: string;
}

export const PODIUM: Record<number, PodiumLook> = {
  1: {
    label: '1. miejsce',
    color: '#f2c14e',
    gradient: 'linear-gradient(135deg,#ffe9a8,#f2c14e 55%,#b6851f)',
    ink: '#2a1c02',
    glow: 'rgba(242,193,78,0.55)',
  },
  2: {
    label: '2. miejsce',
    color: '#c3ccdb',
    gradient: 'linear-gradient(135deg,#eef2f8,#c3ccdb 55%,#8b96ab)',
    ink: '#20262f',
    glow: 'rgba(195,204,219,0.4)',
  },
  3: {
    label: '3. miejsce',
    color: '#cd7f4b',
    gradient: 'linear-gradient(135deg,#f0c39a,#cd7f4b 55%,#9a5a2f)',
    ink: '#2a1608',
    glow: 'rgba(205,127,75,0.4)',
  },
};

/** The look for a rank, or null for everyone outside the top three. */
export function podiumOf(rank: number | null | undefined): PodiumLook | null {
  return rank == null ? null : PODIUM[rank] ?? null;
}

/**
 * Frame for a podium surface: a coloured hairline with a matching bloom and a tinted wash. Returned
 * as inline styles because the three colours are data, not three sets of utility classes.
 */
export function podiumFrame(rank: number | null | undefined): React.CSSProperties | undefined {
  const look = podiumOf(rank);
  if (!look) return undefined;
  return {
    borderColor: `color-mix(in srgb, ${look.color} 55%, transparent)`,
    boxShadow: `0 0 0 1px color-mix(in srgb, ${look.color} 22%, transparent), 0 18px 50px -22px ${look.glow}`,
    background: `linear-gradient(180deg, color-mix(in srgb, ${look.color} 8%, transparent), transparent 60%)`,
  };
}
