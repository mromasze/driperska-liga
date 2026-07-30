import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { API_BASE } from '../../api/client';
import { LogoHex } from '../brand/Logo';

/** Below this the splash reads as a flash of noise rather than a load. */
const MIN_MS = 600;
/**
 * Hard ceiling. Highlight clips can be hundreds of megabytes and the league is played on home
 * connections, so no preload is ever allowed to hold the door shut. When this fires the app opens
 * with whatever finished; the hero video simply buffers later, as it did before.
 */
const MAX_MS = 6_000;
const FADE_MS = 550;
/** Metadata only, and only for the clips the hero actually reaches for first. */
const CLIP_PRELOAD_COUNT = 2;
const CLIP_TIMEOUT_MS = 2_500;

const STAGES = [
  { key: 'fonts', label: 'Ładowanie krojów pisma…' },
  { key: 'config', label: 'Łączenie z serwerem ligi…' },
  { key: 'highlights', label: 'Pobieranie listy zagrywek…' },
  { key: 'clips', label: 'Buforowanie klipów z Riftu…' },
] as const;

/**
 * Warms the browser cache for a handful of clips without downloading them. `preload="metadata"`
 * fetches only the container header, which is what makes the hero start instantly instead of
 * stalling on first paint. Errors resolve rather than reject — a dead clip is the hero's problem to
 * skip, not a reason to hold the splash.
 */
function preloadClipMetadata(urls: string[]): Promise<void> {
  if (urls.length === 0) return Promise.resolve();
  const each = urls.map(
    (url) =>
      new Promise<void>((resolve) => {
        const video = document.createElement('video');
        video.preload = 'metadata';
        video.muted = true;
        video.addEventListener('loadedmetadata', () => resolve(), { once: true });
        video.addEventListener('error', () => resolve(), { once: true });
        video.src = url;
      }),
  );
  return Promise.race([
    Promise.all(each).then(() => undefined),
    new Promise<void>((resolve) => { window.setTimeout(resolve, CLIP_TIMEOUT_MS); }),
  ]);
}

/**
 * Full-screen loading curtain shown once per page load.
 *
 * Children mount immediately underneath, so React Query, the router and the session restore all run
 * while the curtain is up — that's the point, the wait buys warm data instead of just spending time.
 */
export function SplashGate({ children }: { children: ReactNode }) {
  const [doneKeys, setDoneKeys] = useState<ReadonlySet<string>>(() => new Set());
  const [leaving, setLeaving] = useState(false);
  const [gone, setGone] = useState(false);
  const startedAt = useRef(performance.now());

  const done = doneKeys.size;
  const ready = done >= STAGES.length;
  const progress = Math.round((done / STAGES.length) * 100);
  const stage = STAGES[Math.min(done, STAGES.length - 1)];

  useEffect(() => {
    let cancelled = false;
    const mark = (key: string) =>
      setDoneKeys((prev) => (cancelled || prev.has(key) ? prev : new Set(prev).add(key)));

    void (document.fonts?.ready ?? Promise.resolve()).then(() => mark('fonts'));

    void (async () => {
      try {
        await fetch(`${API_BASE}/config`);
      } catch {
        // ServerGate owns the outage experience. The splash must not become a second one.
      }
      mark('config');

      let urls: string[] = [];
      try {
        const response = await fetch(`${API_BASE}/highlights`);
        if (response.ok) {
          const clips: { url?: string }[] = await response.json();
          urls = clips.map((clip) => clip.url).filter((url): url is string => Boolean(url));
        }
      } catch {
        // No clips is an ordinary state, not a failure worth reporting on a loading screen.
      }
      mark('highlights');

      await preloadClipMetadata(urls.slice(0, CLIP_PRELOAD_COUNT));
      mark('clips');
    })();

    const ceiling = window.setTimeout(() => { if (!cancelled) setLeaving(true); }, MAX_MS);
    return () => {
      cancelled = true;
      window.clearTimeout(ceiling);
    };
  }, []);

  // Hold the curtain for MIN_MS even on a warm cache, so it fades rather than blinks.
  useEffect(() => {
    if (!ready || leaving) return;
    const wait = Math.max(0, MIN_MS - (performance.now() - startedAt.current));
    const timer = window.setTimeout(() => setLeaving(true), wait);
    return () => window.clearTimeout(timer);
  }, [ready, leaving]);

  useEffect(() => {
    if (!leaving) return;
    const timer = window.setTimeout(() => setGone(true), FADE_MS);
    return () => window.clearTimeout(timer);
  }, [leaving]);

  return (
    <>
      {children}
      {!gone && (
        <div
          className="splash-veil fixed inset-0 z-[100] grid place-items-center px-6"
          style={{ opacity: leaving ? 0 : 1, transition: `opacity ${FADE_MS}ms ease-in-out` }}
          role="status"
          aria-live="polite"
          aria-label="Ładowanie Driperskiej Ligi"
        >
          <div className="grid-tex pointer-events-none absolute inset-0 opacity-60" />
          <div className="relative flex w-full max-w-sm flex-col items-center text-center">
            <div className="splash-mark">
              <LogoHex size={104} />
            </div>
            <h1 className="mt-6 font-display text-2xl font-bold tracking-wide text-text-hi sm:text-3xl">
              DRIPERSKA <span className="text-gradient-gold">LIGA</span>
            </h1>
            <div className="kicker mt-2">Inhouse League of Legends</div>

            <div className="mt-8 h-1 w-full overflow-hidden rounded-full bg-[color:var(--line)]">
              <div
                className="splash-bar h-full rounded-full"
                style={{ width: `${Math.max(progress, 8)}%` }}
              />
            </div>
            <div className="mt-3 flex w-full items-center justify-between text-xs text-text-lo">
              <span>{leaving ? 'Gotowe' : stage.label}</span>
              <span className="num tabnum">{leaving ? 100 : progress}%</span>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
