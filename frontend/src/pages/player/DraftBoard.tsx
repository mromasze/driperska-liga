import { useEffect, useMemo, useState } from 'react';
import { useChampions } from '../../api/hooks/champions';
import {
  useDraftBan, useDraftPick, useRequestSwap, useRespondSwap,
} from '../../api/hooks/drawLobby';
import type { Champion, DrawLobby, LobbyPlayer, Side } from '../../api/types';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { ChampionIcon } from '../../components/champion/ChampionIcon';
import { roleLabel } from '../../lib/format';

const STEP_SECONDS = 30;

/** Seconds remaining until an ISO deadline, ticking every second (0 when past/absent). */
export function useCountdown(deadline: string | null): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!deadline) return;
    const id = window.setInterval(() => setNow(Date.now()), 500);
    return () => window.clearInterval(id);
  }, [deadline]);
  if (!deadline) return 0;
  return Math.max(0, Math.ceil((new Date(deadline).getTime() - now) / 1000));
}

export function DraftBoard({ lobby, myPlayerId }: { lobby: DrawLobby; myPlayerId: string }) {
  const draft = lobby.draft;
  const champions = useChampions();
  const ban = useDraftBan(lobby.matchId);
  const pick = useDraftPick(lobby.matchId);
  const requestSwap = useRequestSwap(lobby.matchId);
  const respondSwap = useRespondSwap(lobby.matchId);
  const [search, setSearch] = useState('');
  const [swapMenu, setSwapMenu] = useState<string | null>(null);
  const remaining = useCountdown(draft?.deadline ?? null);

  const champById = useMemo(() => {
    const map = new Map<number, Champion>();
    (champions.data ?? []).forEach((c) => map.set(c.id, c));
    return map;
  }, [champions.data]);

  if (!draft) return null;

  const all = [...lobby.blue, ...lobby.red];
  const me = all.find((p) => p.playerId === myPlayerId);
  const mySide = me?.side ?? null;
  const isDone = lobby.status === 'DRAFTED' || draft.status === 'DONE';

  const unavailable = new Set<number>([
    ...draft.blueBans, ...draft.redBans,
    ...all.map((p) => p.championId).filter((id): id is number => id != null),
  ]);

  const captainId = draft.currentSide === 'BLUE' ? draft.blueCaptain : draft.redCaptain;
  const myTurnBan = !isDone && draft.currentType === 'BAN'
    && draft.currentSide === mySide && myPlayerId === captainId;
  const myTurnPick = !isDone && draft.currentType === 'PICK'
    && draft.currentSide === mySide && me != null && me.championId == null;
  const myTurn = myTurnBan || myTurnPick;

  const filtered = (champions.data ?? [])
    .filter((c) => !unavailable.has(c.id))
    .filter((c) => c.name.toLowerCase().includes(search.trim().toLowerCase()));

  const onPickChampion = (id: number) => {
    if (myTurnBan) ban.mutate(id);
    else if (myTurnPick) pick.mutate(id);
    setSearch('');
  };

  const phaseLabel = isDone ? 'Draft zakończony'
    : draft.currentType === 'BAN' ? 'Faza banów' : 'Faza wyborów';
  const sideName = draft.currentSide === 'BLUE' ? 'Niebiescy' : draft.currentSide === 'RED' ? 'Czerwoni' : '';

  return (
    <section className="draw-stage glass grid-tex overflow-hidden p-5 sm:p-8">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <div className="kicker text-gold">Draft turniejowy</div>
          <h2 className="mt-1 font-display text-3xl">{phaseLabel}</h2>
          {!isDone && (
            <p className="mt-1 text-sm text-text-lo">
              Teraz: <strong style={{ color: draft.currentSide === 'BLUE' ? 'var(--blue)' : 'var(--red)' }}>
                {sideName}</strong> — {draft.currentType === 'BAN' ? 'ban (kapitan)' : 'wybór postaci'}
            </p>
          )}
        </div>
        {!isDone && (
          <div className="text-right">
            <div className={`num font-display text-4xl font-bold ${remaining <= 5 ? 'text-loss' : 'text-text-hi'}`}>
              0:{String(remaining).padStart(2, '0')}
            </div>
            <div className="mt-1 h-1.5 w-28 overflow-hidden rounded-full bg-bg-2">
              <div className="h-full rounded-full bg-gradient-to-r from-cyan to-gold transition-all duration-500"
                style={{ width: `${Math.min(100, (remaining / STEP_SECONDS) * 100)}%` }} />
            </div>
          </div>
        )}
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <DraftTeam title="Niebiescy" side="BLUE" color="var(--blue)" players={lobby.blue}
          bans={draft.blueBans} champById={champById} draft={draft} myPlayerId={myPlayerId}
          isDone={isDone} mySide={mySide} swapMenu={swapMenu} setSwapMenu={setSwapMenu}
          onSwap={(target, type) => { requestSwap.mutate({ targetPlayerId: target, type }); setSwapMenu(null); }} />
        <DraftTeam title="Czerwoni" side="RED" color="var(--red)" players={lobby.red}
          bans={draft.redBans} champById={champById} draft={draft} myPlayerId={myPlayerId}
          isDone={isDone} mySide={mySide} swapMenu={swapMenu} setSwapMenu={setSwapMenu}
          onSwap={(target, type) => { requestSwap.mutate({ targetPlayerId: target, type }); setSwapMenu(null); }} />
      </div>

      {/* Pending swap requests that concern me */}
      {isDone && draft.swaps.filter((s) => s.toPlayerId === myPlayerId || s.fromPlayerId === myPlayerId).length > 0 && (
        <div className="mt-6 space-y-2">
          {draft.swaps.filter((s) => s.toPlayerId === myPlayerId || s.fromPlayerId === myPlayerId).map((s) => {
            const other = all.find((p) => p.playerId === (s.fromPlayerId === myPlayerId ? s.toPlayerId : s.fromPlayerId));
            const kind = s.type === 'POSITION' ? 'pozycją' : 'postacią';
            const incoming = s.toPlayerId === myPlayerId;
            return (
              <div key={s.id} className="flex items-center justify-between gap-3 rounded-lg border border-gold/40 bg-[color:var(--bg)]/60 p-3">
                <span className="text-sm text-text">
                  {incoming ? <><strong>{other?.nickname}</strong> chce zamienić się {kind} z Tobą</>
                    : <>Czekasz aż <strong>{other?.nickname}</strong> zaakceptuje zamianę {kind}</>}
                </span>
                <div className="flex gap-2">
                  {incoming && (
                    <Button variant="gold" onClick={() => respondSwap.mutate({ swapId: s.id, accept: true })}>Akceptuj</Button>
                  )}
                  <Button variant="danger" onClick={() => respondSwap.mutate({ swapId: s.id, accept: false })}>
                    {incoming ? 'Odrzuć' : 'Anuluj'}
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {isDone ? (
        <div className="mt-6 rounded-lg border border-[color:var(--win)]/40 bg-[color:var(--win)]/10 p-5 text-center">
          <p className="font-display text-lg text-win">Draft zakończony — stwórzcie lobby w grze i zaczynajcie!</p>
          <p className="mt-2 text-sm text-text-lo">
            Możesz jeszcze zamienić się pozycją lub postacią z kolegą z drużyny (klik strzałki przy graczu).
            Admin uruchomi mecz, gdy będziecie gotowi.
          </p>
        </div>
      ) : myTurn ? (
        <div className="mt-6 rounded-lg border border-gold/40 bg-[color:var(--bg)]/60 p-4">
          <div className="mb-3 flex items-center justify-between gap-3">
            <strong className="text-gold">{myTurnBan ? 'Twój ban' : 'Wybierz swoją postać'}</strong>
            <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Szukaj postaci…"
              className="w-48 rounded-md border border-line bg-[color:var(--bg-1)] px-3 py-1.5 text-sm" />
          </div>
          <div className="grid max-h-72 grid-cols-6 gap-2 overflow-y-auto sm:grid-cols-10">
            {filtered.map((c) => (
              <button key={c.id} type="button" title={c.name}
                disabled={ban.isPending || pick.isPending}
                onClick={() => onPickChampion(c.id)}
                className="rounded-md ring-1 ring-transparent transition hover:ring-gold">
                <ChampionIcon iconUrl={c.iconUrl} name={c.name} size={44} />
              </button>
            ))}
          </div>
        </div>
      ) : (
        <div className="mt-6 rounded-lg border border-line bg-[color:var(--bg)]/40 p-4 text-center text-sm text-text-lo">
          Czekaj na swoją kolej…
        </div>
      )}
    </section>
  );
}

function DraftTeam({ title, side, color, players, bans, champById, draft, myPlayerId, isDone, mySide, swapMenu, setSwapMenu, onSwap }: {
  title: string; side: Side; color: string; players: LobbyPlayer[]; bans: number[];
  champById: Map<number, Champion>; draft: NonNullable<DrawLobby['draft']>; myPlayerId: string;
  isDone: boolean; mySide: Side | null;
  swapMenu: string | null; setSwapMenu: (id: string | null) => void;
  onSwap: (targetPlayerId: string, type: 'POSITION' | 'CHAMPION') => void;
}) {
  const active = draft.currentSide === side && draft.status !== 'DONE';
  return (
    <div className={`overflow-hidden rounded-xl border bg-[color:var(--bg-1)]/80 ${active ? 'border-gold' : 'border-line'}`}>
      <div className="flex items-center justify-between px-4 py-3 font-display font-semibold"
        style={{ color, background: `color-mix(in srgb, ${color} 12%, transparent)` }}>
        <span>{title}</span>
        {active && <Badge tone="win">tura</Badge>}
      </div>

      {/* Ban strip */}
      <div className="flex items-center gap-2 border-b border-line px-4 py-2">
        <span className="kicker text-text-lo">Bany</span>
        {Array.from({ length: 5 }).map((_, i) => {
          const champ = bans[i] != null ? champById.get(bans[i]) : undefined;
          return bans[i] != null
            ? <ChampionIcon key={i} iconUrl={champ?.iconUrl} name={champ?.name} size={28} className="opacity-60 grayscale" />
            : <span key={i} className="h-7 w-7 rounded-md border border-dashed border-line" />;
        })}
      </div>

      <div className="divide-y divide-line">
        {players.map((player) => {
          const champ = player.championId != null ? champById.get(player.championId) : undefined;
          const canSwap = isDone && mySide === side && player.playerId !== myPlayerId;
          return (
            <div key={player.playerId} className="relative flex items-center gap-3 px-4 py-3">
              <ChampionIcon iconUrl={champ?.iconUrl} name={champ?.name} size={40} />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5 font-medium text-text-hi">
                  <span className="truncate">{player.nickname}</span>
                  {player.captain && <span title="Kapitan (robi bany)" className="text-gold">★</span>}
                  {player.playerId === myPlayerId && <span className="kicker text-cyan">(Ty)</span>}
                </div>
                <div className="text-xs text-text-lo">{roleLabel(player.role)}{champ ? ` · ${champ.name}` : ''}</div>
              </div>
              {canSwap && (
                <button type="button" onClick={() => setSwapMenu(swapMenu === player.playerId ? null : player.playerId)}
                  title="Zamień się" className="rounded-md border border-line px-2 py-1 text-text-lo hover:text-text-hi">⇄</button>
              )}
              {canSwap && swapMenu === player.playerId && (
                <div className="absolute right-3 top-12 z-10 w-44 rounded-lg border border-line bg-[color:var(--bg-1)] p-1 shadow-lg">
                  <button type="button" onClick={() => onSwap(player.playerId, 'POSITION')}
                    className="block w-full rounded px-3 py-2 text-left text-sm hover:bg-[var(--glass-strong)]">Zamień pozycję</button>
                  <button type="button" onClick={() => onSwap(player.playerId, 'CHAMPION')}
                    className="block w-full rounded px-3 py-2 text-left text-sm hover:bg-[var(--glass-strong)]">Zamień postać</button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
