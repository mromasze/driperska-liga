import { useEffect, useState } from 'react';
import { useSetPickOrder, useSetTeamReady, useVoteCaptain } from '../../api/hooks/drawLobby';
import { Avatar } from '../../components/ui/Avatar';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { cn } from '../../lib/cn';
import { roleLabel } from '../../lib/format';
import type { DraftSetup, DraftSetupSide, DrawLobby, LobbyPlayer, Side } from '../../api/types';

/**
 * What a team settles before the first ban: who captains it, in what order they pick, and whether
 * they are ready.
 *
 * The flow is deliberately not blocking. A team that ignores this screen entirely still gets a draft —
 * the backend shuffles the order and hands the captaincy to whoever ends up first. What this adds is
 * the option to do it on purpose: vote someone in, let them arrange the picks, and tell the other side
 * you are done. The moment both teams say ready, the draft starts by itself.
 */
export function DraftSetupPanel({ lobby, setup, myPlayerId }: {
  lobby: DrawLobby;
  setup: DraftSetup;
  myPlayerId: string;
}) {
  const mine = lobby.blue.some((p) => p.playerId === myPlayerId) ? 'BLUE' : 'RED';
  const mySide: Side = mine;
  const myTeam = mySide === 'BLUE' ? lobby.blue : lobby.red;
  const enemyTeam = mySide === 'BLUE' ? lobby.red : lobby.blue;
  const mySetup = mySide === 'BLUE' ? setup.blue : setup.red;
  const enemySetup = mySide === 'BLUE' ? setup.red : setup.blue;
  const iAmCaptain = mySetup.captain === myPlayerId;

  return (
    <div className="space-y-5">
      <div>
        <div className="kicker text-gold">Przed draftem</div>
        <h2 className="mt-1 font-display text-3xl">Kapitan i kolejność picków</h2>
        <p className="mt-2 max-w-2xl text-sm text-text-lo">
          Wybierzcie kapitana — {setup.votesToDecide} głosy z pięciu wystarczą. Kapitan banuje za
          drużynę i ustawia kolejność wybierania postaci, a potem zgłasza gotowość. Draft zaczyna się
          sam, gdy obie drużyny będą gotowe. Jak nic nie ustalicie, kolejność będzie losowa.
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <MyTeamCard
          matchId={lobby.matchId}
          side={mySide}
          team={myTeam}
          setup={mySetup}
          myPlayerId={myPlayerId}
          iAmCaptain={iAmCaptain}
          votesToDecide={setup.votesToDecide}
        />
        <EnemyCard side={mySide === 'BLUE' ? 'RED' : 'BLUE'} team={enemyTeam} setup={enemySetup} />
      </div>
    </div>
  );
}

function MyTeamCard({ matchId, side, team, setup, myPlayerId, iAmCaptain, votesToDecide }: {
  matchId: string;
  side: Side;
  team: LobbyPlayer[];
  setup: DraftSetupSide;
  myPlayerId: string;
  iAmCaptain: boolean;
  votesToDecide: number;
}) {
  const accent = side === 'BLUE' ? 'var(--blue)' : 'var(--red)';
  const vote = useVoteCaptain(matchId);
  const setOrder = useSetPickOrder(matchId);
  const ready = useSetTeamReady(matchId);
  const byId = new Map(team.map((p) => [p.playerId, p]));
  const nameOf = (playerId: string) => byId.get(playerId)?.nickname ?? '—';

  // Local working copy of the order so the captain can shuffle rows around before saving. Re-seeded
  // whenever the server's version changes (a save, another captain action, an admin reset) — compared
  // as a joined key, because the array itself is a new object on every render.
  const serverOrder = setup.order.length > 0 ? setup.order : team.map((p) => p.playerId);
  const serverOrderKey = serverOrder.join(',');
  const [order, setOrderDraft] = useState<string[]>(serverOrder);
  useEffect(() => {
    setOrderDraft(serverOrderKey ? serverOrderKey.split(',') : []);
  }, [serverOrderKey]);

  const move = (index: number, delta: number) => {
    const next = [...order];
    const target = index + delta;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target], next[index]];
    setOrderDraft(next);
  };

  const dirty = order.join(',') !== serverOrderKey;

  return (
    <section
      className="glass overflow-hidden"
      style={{ borderColor: `color-mix(in srgb, ${accent} 40%, transparent)` }}
    >
      <header className="flex items-center justify-between gap-2 px-4 py-3"
        style={{ background: `color-mix(in srgb, ${accent} 12%, transparent)` }}>
        <span className="font-display font-semibold" style={{ color: accent }}>
          Twoja drużyna
        </span>
        {setup.ready
          ? <Badge tone="win">gotowi</Badge>
          : <Badge tone="pending">{setup.votesCast}/{setup.squadSize} głosów</Badge>}
      </header>

      {/* Step one: the vote. Closed as soon as it resolves. */}
      {!setup.captain ? (
        <div className="space-y-2 p-4">
          <p className="text-sm text-text-lo">
            Kto ma być kapitanem? {votesToDecide} głosy kończą sprawę. Możesz zagłosować na siebie.
          </p>
          {setup.votes.map((candidate) => (
            <button
              key={candidate.playerId}
              type="button"
              disabled={vote.isPending}
              onClick={() => vote.mutate(candidate.playerId)}
              // Who voted for whom is never sent back — only the tally — so no button is "yours".
              className={cn(
                'flex w-full items-center gap-3 rounded-lg border border-line p-2 text-left transition-colors',
                'hover:border-line-strong hover:bg-[var(--glass)]',
              )}
            >
              <Avatar src={byId.get(candidate.playerId)?.avatarUrl} name={nameOf(candidate.playerId)} size={30} />
              <span className="min-w-0 flex-1 truncate text-sm text-text-hi">
                {nameOf(candidate.playerId)}
                {candidate.playerId === myPlayerId && <span className="ml-1 text-xs text-gold">(Ty)</span>}
              </span>
              <span className="num text-sm text-text-lo">{candidate.votes}</span>
            </button>
          ))}
          {vote.isError && <p className="text-xs text-loss">{(vote.error as Error).message}</p>}
        </div>
      ) : (
        <div className="space-y-3 p-4">
          <div className="flex items-center gap-2 text-sm">
            <span className="kicker">Kapitan</span>
            <Avatar src={byId.get(setup.captain)?.avatarUrl} name={nameOf(setup.captain)} size={26} />
            <span className="font-medium text-text-hi">{nameOf(setup.captain)}</span>
            {iAmCaptain && <Badge tone="gold">to Ty</Badge>}
          </div>

          <div>
            <div className="kicker mb-2">Kolejność wybierania postaci</div>
            <ol className="space-y-1.5">
              {order.map((playerId, index) => (
                <li key={playerId} className="flex items-center gap-2 rounded-md border border-line px-2 py-1.5">
                  <span className="num w-5 text-center text-sm text-text-lo">{index + 1}</span>
                  <Avatar src={byId.get(playerId)?.avatarUrl} name={nameOf(playerId)} size={24} />
                  <span className="min-w-0 flex-1 truncate text-sm text-text-hi">{nameOf(playerId)}</span>
                  <span className="kicker hidden sm:inline">
                    {byId.get(playerId) ? roleLabel(byId.get(playerId)!.role) : ''}
                  </span>
                  {iAmCaptain && !setup.ready && (
                    <span className="flex shrink-0 gap-1">
                      <OrderButton label="▲" disabled={index === 0} onClick={() => move(index, -1)} />
                      <OrderButton label="▼" disabled={index === order.length - 1} onClick={() => move(index, 1)} />
                    </span>
                  )}
                </li>
              ))}
            </ol>
            {!iAmCaptain && (
              <p className="mt-2 text-xs text-text-lo">
                Kolejność ustawia kapitan. {setup.order.length === 0 && 'Na razie jest wstępna — bez zapisu byłaby losowa.'}
              </p>
            )}
          </div>

          {iAmCaptain && (
            <div className="flex flex-wrap items-center gap-2">
              <Button size="sm" variant="ghost" disabled={!dirty || setOrder.isPending || setup.ready}
                onClick={() => setOrder.mutate(order)}>
                {setOrder.isPending ? 'Zapisywanie…' : 'Zapisz kolejność'}
              </Button>
              <Button size="sm" variant="ghost" disabled={setOrder.isPending || setup.ready}
                onClick={() => setOrder.mutate([])}>
                Losowa kolejność
              </Button>
              <Button size="sm" variant={setup.ready ? 'ghost' : 'gold'} disabled={ready.isPending}
                onClick={() => ready.mutate(!setup.ready)}>
                {ready.isPending ? '…' : setup.ready ? 'Cofnij gotowość' : '✓ Jesteśmy gotowi'}
              </Button>
              {dirty && !setup.ready && (
                <span className="text-xs text-pending">Masz niezapisane zmiany kolejności.</span>
              )}
            </div>
          )}
          {(setOrder.isError || ready.isError) && (
            <p className="text-xs text-loss">{((setOrder.error ?? ready.error) as Error).message}</p>
          )}
        </div>
      )}
    </section>
  );
}

/** The other side, read-only: who leads them and whether they are done. */
function EnemyCard({ side, team, setup }: { side: Side; team: LobbyPlayer[]; setup: DraftSetupSide }) {
  const accent = side === 'BLUE' ? 'var(--blue)' : 'var(--red)';
  const captainName = team.find((p) => p.playerId === setup.captain)?.nickname;
  return (
    <section className="glass overflow-hidden"
      style={{ borderColor: `color-mix(in srgb, ${accent} 40%, transparent)` }}>
      <header className="flex items-center justify-between gap-2 px-4 py-3"
        style={{ background: `color-mix(in srgb, ${accent} 12%, transparent)` }}>
        <span className="font-display font-semibold" style={{ color: accent }}>Przeciwnicy</span>
        {setup.ready
          ? <Badge tone="win">gotowi</Badge>
          : <Badge tone="pending">{setup.captain ? 'ustalają kolejność' : 'wybierają kapitana'}</Badge>}
      </header>
      <div className="space-y-2 p-4 text-sm">
        <div className="flex items-center gap-2">
          <span className="kicker">Kapitan</span>
          <span className="text-text-hi">{captainName ?? '—'}</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="kicker">Głosy</span>
          <span className="num text-text">{setup.votesCast}/{setup.squadSize}</span>
        </div>
        {/* Their pick order is their business; only the fact that it is settled is shared. */}
        <p className="text-xs text-text-lo">
          {setup.ready
            ? 'Czekają na Was — draft ruszy, gdy zgłosicie gotowość.'
            : 'Jeszcze się nie zgłosili jako gotowi.'}
        </p>
      </div>
    </section>
  );
}

function OrderButton({ label, disabled, onClick }: {
  label: string; disabled: boolean; onClick: () => void;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      aria-label={label === '▲' ? 'Wyżej' : 'Niżej'}
      className="grid h-6 w-6 place-items-center rounded border border-line text-[10px] text-text-lo hover:text-text-hi disabled:opacity-30"
    >
      {label}
    </button>
  );
}
