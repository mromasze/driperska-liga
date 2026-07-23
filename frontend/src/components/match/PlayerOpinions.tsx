import { useEffect, useState } from 'react';
import { useMatchFeedbackSummary } from '../../api/hooks/feedback';
import type { FeedbackComment, PlayerFeedbackSummary } from '../../api/types';
import { cn } from '../../lib/cn';
import { roleLabel } from '../../lib/format';

/** Peer-feedback highlights for a match: the most-praised and most-criticised players, each with a
 *  rotating slider of anonymous comments about their game. Only shows for signed-in users. */
export function PlayerOpinions({ matchId }: { matchId: string }) {
  const summary = useMatchFeedbackSummary(matchId);
  const data = summary.data;
  if (!data || data.players.length === 0) return null;

  const withUp = data.players.filter((p) => p.upvotes > 0);
  const withDown = data.players.filter((p) => p.downvotes > 0);
  const topPos = withUp.length ? withUp.reduce((a, b) => (b.upvotes > a.upvotes ? b : a)) : null;
  const topNeg = withDown.length ? withDown.reduce((a, b) => (b.downvotes > a.downvotes ? b : a)) : null;
  if (!topPos && !topNeg) return null;

  return (
    <section className="glass grid-tex p-5 sm:p-7">
      <div className="mb-4">
        <div className="kicker text-gold">Opinie graczy</div>
        <h2 className="mt-1 font-display text-2xl">Co mówili o tym meczu</h2>
        <p className="mt-1 text-sm text-text-lo">
          {data.responses} {plural(data.responses, 'ocena', 'oceny', 'ocen')} od uczestników · komentarze anonimowe.
        </p>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        {topPos && <OpinionCard player={topPos} tone="POSITIVE" />}
        {topNeg && <OpinionCard player={topNeg} tone="NEGATIVE" />}
      </div>
    </section>
  );
}

function OpinionCard({ player, tone }: { player: PlayerFeedbackSummary; tone: 'POSITIVE' | 'NEGATIVE' }) {
  const positive = tone === 'POSITIVE';
  const accent = positive ? 'var(--win)' : 'var(--loss)';
  return (
    <div className="overflow-hidden rounded-xl border border-line bg-[color:var(--bg-1)]/80">
      <div
        className="flex items-center justify-between px-4 py-3"
        style={{ background: `color-mix(in srgb, ${accent} 12%, transparent)` }}
      >
        <div className="min-w-0">
          <div className="kicker" style={{ color: accent }}>
            {positive ? '👍 Najwięcej plusów' : '👎 Najwięcej minusów'}
          </div>
          <div className="truncate font-display text-lg text-text-hi">{player.nickname}</div>
        </div>
        <div className="flex shrink-0 items-center gap-2 text-sm font-bold">
          <span className="text-win">+{player.upvotes}</span>
          <span className="text-loss">−{player.downvotes}</span>
          {player.role && <span className="kicker">{roleLabel(player.role)}</span>}
        </div>
      </div>
      <div className="p-4">
        <CommentSlider comments={player.comments} />
      </div>
    </div>
  );
}

function CommentSlider({ comments }: { comments: FeedbackComment[] }) {
  const [index, setIndex] = useState(0);
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    if (comments.length <= 1) return;
    let inner: number;
    const id = window.setInterval(() => {
      setVisible(false);
      inner = window.setTimeout(() => {
        setIndex((v) => (v + 1) % comments.length);
        setVisible(true);
      }, 320);
    }, 4200);
    return () => { window.clearInterval(id); window.clearTimeout(inner); };
  }, [comments.length]);

  if (comments.length === 0) {
    return <p className="text-sm italic text-text-lo">Bez komentarza — tylko głosy.</p>;
  }
  const c = comments[index % comments.length];
  return (
    <div>
      <blockquote
        className={cn('min-h-14 text-sm leading-relaxed transition-opacity duration-300',
          visible ? 'opacity-100' : 'opacity-0')}
      >
        <span className={c.tone === 'POSITIVE' ? 'text-win' : 'text-loss'}>
          {c.tone === 'POSITIVE' ? '➕' : '➖'}
        </span>{' '}
        <span className="text-text">„{c.note}”</span>
      </blockquote>
      {comments.length > 1 && (
        <div className="mt-3 flex gap-1.5">
          {comments.map((_, i) => (
            <span key={i} className={cn('h-1.5 w-1.5 rounded-full', i === index ? 'bg-gold' : 'bg-bg-2')} />
          ))}
        </div>
      )}
    </div>
  );
}

function plural(n: number, one: string, few: string, many: string): string {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (n === 1) return one;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return few;
  return many;
}
