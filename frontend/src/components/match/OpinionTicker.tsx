import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useRecentOpinions } from '../../api/hooks/feedback';
import { Avatar } from '../ui/Avatar';
import { formatDate } from '../../lib/format';
import type { PublicOpinion } from '../../api/types';

/** Six seconds felt like waiting; a quote is one line and reads in two. */
const ROTATE_MS = 3200;

/**
 * Rotating reel of what players wrote about each other after recent matches.
 *
 * The landing page had nothing on it that moved by itself, which is exactly what makes a league page
 * feel abandoned between games. These quotes change every few seconds, they are the league's own
 * voice, and each one links to the match it came from and the player it is about.
 *
 * Only praise is published here — see MatchFeedbackService.recentPraise for why. Authors stay
 * anonymous, as they are everywhere else in the app.
 */
export function OpinionTicker() {
  const opinions = useRecentOpinions();
  const list = opinions.data ?? [];
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(false);

  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const update = () => setReducedMotion(media.matches);
    update();
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, []);

  // Refetching can shrink the list under a stale index.
  useEffect(() => setIndex((current) => (current < list.length ? current : 0)), [list.length]);

  // `index` is a dependency on purpose: stepping through by hand restarts the countdown, so a quote
  // you just clicked to does not vanish half a second later.
  useEffect(() => {
    if (paused || reducedMotion || list.length < 2) return;
    const timer = window.setInterval(
      () => setIndex((current) => (current + 1) % list.length),
      ROTATE_MS,
    );
    return () => window.clearInterval(timer);
  }, [paused, reducedMotion, list.length, index]);

  const step = (delta: number) =>
    setIndex((current) => (current + delta + list.length) % list.length);

  // Nothing to say yet is not worth an empty box on the landing page.
  if (opinions.isLoading || list.length === 0) return null;

  const current = list[Math.min(index, list.length - 1)];

  return (
    <section
      className="glass grid-tex relative overflow-hidden p-5 sm:p-6"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
    >
      <div className="mb-3 flex flex-wrap items-end justify-between gap-2">
        <div>
          <div className="kicker text-gold">Głosy z ligi</div>
          <h2 className="mt-1 font-display text-2xl">Co mówili o sobie po meczach</h2>
        </div>
        <span className="text-xs text-text-lo">anonimowo, od uczestników meczu</span>
      </div>

      {/* Reduced motion gets the three latest at once instead of a carousel that moves on its own. */}
      {reducedMotion ? (
        <div className="space-y-3">
          {list.slice(0, 3).map((opinion) => (
            <Quote key={`${opinion.matchId}-${opinion.aboutPlayerId}-${opinion.createdAt}`} opinion={opinion} />
          ))}
        </div>
      ) : (
        <>
          {/* `key` restarts the entry animation on every change. */}
          <div key={`${current.matchId}-${current.aboutPlayerId}-${index}`} className="animate-rise">
            <Quote opinion={current} />
          </div>
          {list.length > 1 && (
            <div className="mt-4 flex items-center gap-2">
              <StepButton label="‹" title="Poprzednia opinia" onClick={() => step(-1)} />
              {list.map((opinion, position) => (
                <button
                  key={`${opinion.matchId}-${opinion.aboutPlayerId}-${position}`}
                  type="button"
                  aria-label={`Opinia ${position + 1} z ${list.length}`}
                  aria-current={position === index}
                  onClick={() => setIndex(position)}
                  className="h-1.5 rounded-full transition-all"
                  style={{
                    width: position === index ? '1.6rem' : '0.5rem',
                    background: position === index ? 'var(--gold)' : 'var(--line-strong)',
                  }}
                />
              ))}
              <StepButton label="›" title="Następna opinia" onClick={() => step(1)} />
              <span className="num ml-auto text-[11px] text-text-lo">
                {paused ? 'zatrzymane · ' : ''}{index + 1}/{list.length}
              </span>
            </div>
          )}
        </>
      )}
    </section>
  );
}

/** Step one quote back or forward by hand. */
function StepButton({ label, title, onClick }: { label: string; title: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={title}
      aria-label={title}
      className="grid h-6 w-6 shrink-0 place-items-center rounded-full border border-line text-sm leading-none text-text-lo transition-colors hover:border-line-strong hover:text-text-hi"
    >
      {label}
    </button>
  );
}

function Quote({ opinion }: { opinion: PublicOpinion }) {
  return (
    <figure className="flex gap-4">
      <span aria-hidden className="font-display text-4xl leading-none text-gold/40">„</span>
      <div className="min-w-0 flex-1">
        <blockquote className="text-text-hi">{opinion.note}</blockquote>
        <figcaption className="mt-3 flex flex-wrap items-center gap-2 text-xs text-text-lo">
          <span>o graczu</span>
          <Link
            to={`/players/${opinion.aboutPlayerId}`}
            className="flex items-center gap-1.5 font-medium text-text hover:text-gold"
          >
            <Avatar src={opinion.aboutAvatarUrl} name={opinion.aboutNickname} size={22} />
            {opinion.aboutNickname}
          </Link>
          <span>·</span>
          <Link to={`/matches/${opinion.matchId}`} className="hover:text-gold">
            mecz z {formatDate(opinion.playedAt)}
          </Link>
        </figcaption>
      </div>
    </figure>
  );
}
