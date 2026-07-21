import { useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  useMatch,
  useDrawTeams,
  useConfirmDraw,
  useSubmitResults,
  useEditResults,
  useStartMatch,
  useStartMatchManual,
  useMatchDrawState,
  useRiotLobbyStatus,
  useImportRiotResults,
  useReplaceMatchPlayer,
  useReopenMatch,
  useUploadReplay,
  useShareMatchToDiscord,
} from '../../api/hooks/matches';
import { DrawBoard } from '../../components/match/DrawBoard';
import { usePlayers } from '../../api/hooks/players';
import { ResultsForm } from '../../components/match/ResultsForm';
import { Scoreboard } from '../../components/match/Scoreboard';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { LoadingState, ErrorState, EmptyState } from '../../components/ui/States';
import { roleLabel } from '../../lib/format';
import type { DrawResult } from '../../api/types';

export function MatchControlPage() {
  const { id = '' } = useParams<{ id: string }>();
  const match = useMatch(id);
  const draw = useDrawTeams(id);
  const start = useStartMatch(id);
  const startManual = useStartMatchManual(id);
  const drawState = useMatchDrawState(id, match.data?.status === 'TEAMS_DRAWN');
  const riotLobby = useRiotLobbyStatus(id, match.data?.status === 'LOBBY_READY');
  const importRiot = useImportRiotResults(id);
  const replacePlayer = useReplaceMatchPlayer(id);
  const players = usePlayers({ active: true });
  const confirm = useConfirmDraw(id);
  const submit = useSubmitResults(id);
  const edit = useEditResults(id);
  const reopen = useReopenMatch(id);
  const uploadReplay = useUploadReplay(id);
  const shareDiscord = useShareMatchToDiscord(id);
  const replayInput = useRef<HTMLInputElement>(null);
  const [removedPlayerId, setRemovedPlayerId] = useState('');
  const [addedPlayerId, setAddedPlayerId] = useState('');
  const [drawResult, setDrawResult] = useState<DrawResult | null>(null);
  const [editingResults, setEditingResults] = useState(false);
  const [shareMsg, setShareMsg] = useState<string | null>(null);

  if (match.isLoading) return <LoadingState />;
  if (match.isError) return <ErrorState error={match.error} />;
  if (!match.data) return <EmptyState title="Nie znaleziono meczu" />;

  const m = match.data;
  const runDraw = () => draw.mutate(undefined, { onSuccess: (d) => setDrawResult(d) });
  const currentPlayerIds = new Set(m.participants.map((participant) => participant.playerId));
  const availablePlayers = (players.data?.content ?? []).filter((player) =>
    !currentPlayerIds.has(player.id) && player.accountProvisioned && player.riotId);

  const doShare = () => {
    if (!window.confirm('Wygenerować obrazek wyników i wysłać go na kanał Discord?')) return;
    setShareMsg(null);
    shareDiscord.mutate(undefined, {
      onSuccess: (r) => setShareMsg(r.sent ? '✓ Wysłano na Discord.' : '⚠ ' + r.message),
      onError: (e) => setShareMsg('⚠ ' + (e as Error).message),
    });
  };

  const matchTools = (
    <section className="panel flex flex-wrap items-center gap-3 p-4">
      <Button variant="gold" size="sm" disabled={shareDiscord.isPending} onClick={doShare}>
        {shareDiscord.isPending ? 'Wysyłanie…' : '📤 Udostępnij wynik na Discord'}
      </Button>
      <input ref={replayInput} type="file" accept=".rofl" className="hidden"
        onChange={(e) => { const f = e.target.files?.[0]; if (f) uploadReplay.mutate(f); e.currentTarget.value = ''; }} />
      <Button variant="ghost" size="sm" disabled={uploadReplay.isPending} onClick={() => replayInput.current?.click()}>
        {uploadReplay.isPending ? 'Wgrywanie…' : m.replayUrl ? 'Podmień powtórkę (.rofl)' : 'Wgraj powtórkę (.rofl)'}
      </Button>
      {m.replayUrl && <a className="text-sm text-gold hover:underline" href={m.replayUrl}>⬇ Pobierz powtórkę</a>}
      {shareMsg && <span className="text-sm text-text-lo">{shareMsg}</span>}
      {uploadReplay.isError && <span className="text-sm text-loss">{uploadReplay.error.message}</span>}
    </section>
  );

  return (
    <div className="space-y-6">
      <div>
        <Link to="/admin" className="text-sm text-text-lo hover:text-text">
          ← Pulpit
        </Link>
        <div className="mt-1 flex items-center gap-3">
          <h1 className="font-display text-3xl">Kontrola meczu</h1>
          <Badge tone="info">{m.status}</Badge>
        </div>
      </div>

      {(m.status === 'TEAMS_DRAWN' || m.status === 'LOBBY_READY') && (
        <section className="panel p-4">
          <div className="mb-3">
            <div className="kicker">Zmiana składu</div>
            <p className="text-xs text-text-lo">W gotowym lobby zostanie wystawiony nowy kod Riot.</p>
          </div>
          <div className="flex flex-wrap items-end gap-3">
            <label className="min-w-52 flex-1"><span className="kicker">Usuń gracza</span>
              <select className="form-control mt-1" value={removedPlayerId}
                onChange={(event) => setRemovedPlayerId(event.target.value)}>
                <option value="">Wybierz…</option>
                {m.participants.map((participant) => (
                  <option key={participant.playerId} value={participant.playerId}>{participant.nickname}</option>
                ))}
              </select>
            </label>
            <label className="min-w-52 flex-1"><span className="kicker">Dodaj gracza</span>
              <select className="form-control mt-1" value={addedPlayerId}
                onChange={(event) => setAddedPlayerId(event.target.value)}>
                <option value="">Wybierz…</option>
                {availablePlayers.map((player) => (
                  <option key={player.id} value={player.id}>{player.nickname} · {player.riotId}</option>
                ))}
              </select>
            </label>
            <Button variant="ghost" disabled={!removedPlayerId || !addedPlayerId || replacePlayer.isPending}
              onClick={() => replacePlayer.mutate({
                removedPlayerId, addedPlayerId,
              }, { onSuccess: () => { setRemovedPlayerId(''); setAddedPlayerId(''); } })}>
              {replacePlayer.isPending ? 'Zmiana…' : 'Zmień gracza'}
            </Button>
          </div>
          {replacePlayer.isError && <p className="mt-2 text-sm text-loss">{replacePlayer.error.message}</p>}
        </section>
      )}

      {/* DRAFT / TEAMS_DRAWN → drawing */}
      {(m.status === 'DRAFT' || m.status === 'TEAMS_DRAWN') && (
        <>
          {drawResult ? (
            <DrawBoard
              draw={drawResult}
              drawing={draw.isPending}
              confirming={confirm.isPending}
              onReroll={runDraw}
              onConfirm={() => confirm.mutate()}
            />
          ) : m.status === 'TEAMS_DRAWN' ? (
            <div className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-2">
                {(['BLUE', 'RED'] as const).map((side) => (
                  <div key={side} className="glass p-4">
                    <div
                      className="mb-2 font-display font-semibold"
                      style={{ color: side === 'BLUE' ? 'var(--blue)' : 'var(--red)' }}
                    >
                      {side === 'BLUE' ? 'Niebiescy' : 'Czerwoni'}
                    </div>
                    {m.participants
                      .filter((p) => p.side === side)
                      .map((p) => (
                        <div key={p.playerId} className="flex justify-between py-1 text-sm">
                          <span className="text-text-hi">{p.nickname}</span>
                          <span className="kicker">{roleLabel(p.role)}</span>
                        </div>
                      ))}
                  </div>
                ))}
              </div>
              {drawState.data && (
                <div className="rounded-lg border border-line bg-[color:var(--bg)]/60 p-4">
                  <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                    <span className="kicker">Głosowanie na żywo · runda {drawState.data.round}</span>
                    <div className="flex gap-2">
                      <Badge tone="win">{drawState.data.accepts} za</Badge>
                      <Badge tone="loss">{drawState.data.rejects} przeciw</Badge>
                      <Badge>{drawState.data.requiredAccepts} wymaganych</Badge>
                    </div>
                  </div>
                  <div className="mb-2 h-2 overflow-hidden rounded-full bg-bg-2">
                    <div className="h-full rounded-full bg-gradient-to-r from-cyan to-gold transition-all duration-500"
                      style={{ width: `${Math.min(100, drawState.data.accepts / drawState.data.requiredAccepts * 100)}%` }} />
                  </div>
                  <div className="flex flex-wrap gap-2 text-xs">
                    {[...drawState.data.blue, ...drawState.data.red].map((p) => {
                      const accepted = drawState.data!.acceptedPlayerIds.includes(p.playerId);
                      const rejected = drawState.data!.rejectedPlayerIds.includes(p.playerId);
                      return (
                        <span key={p.playerId}
                          className={`rounded px-2 py-1 ${accepted ? 'bg-[color:var(--win)]/15 text-win' : rejected ? 'bg-[color:var(--loss)]/15 text-loss' : 'bg-bg-2 text-text-lo'}`}>
                          {accepted ? '✓' : rejected ? '✗' : '·'} {p.nickname}
                        </span>
                      );
                    })}
                  </div>
                </div>
              )}
              <div className="flex flex-wrap gap-3">
                <Button variant="ghost" onClick={runDraw} disabled={draw.isPending}>
                  🎲 Losuj ponownie
                </Button>
                <Button variant="gold" onClick={() => confirm.mutate()} disabled={confirm.isPending}>
                  Zatwierdź składy i utwórz lobby Riot
                </Button>
                <Button variant="ghost" onClick={() => startManual.mutate()} disabled={startManual.isPending}>
                  {startManual.isPending ? 'Uruchamianie…' : 'Rozpocznij ręcznie (bez Riot)'}
                </Button>
              </div>
              {(confirm.isError || startManual.isError) && (
                <p className="text-sm text-loss">{(confirm.error ?? startManual.error)?.message}</p>
              )}
            </div>
          ) : (
            <div className="glass p-8 text-center">
              <p className="mb-4 text-text-lo">Pula gotowa. Wylosuj drużyny, aby rozpocząć.</p>
              <Button variant="gold" onClick={runDraw} disabled={draw.isPending}>
                {draw.isPending ? 'Losowanie…' : '🎲 Losuj drużyny'}
              </Button>
            </div>
          )}
        </>
      )}

      {m.status === 'LOBBY_READY' && (
        <section className="panel space-y-4 p-5">
          <div>
            <div className="kicker text-gold">Lobby Riot gotowe</div>
            <h2 className="font-display text-2xl">Poczekaj, aż wszyscy dołączą</h2>
          </div>
          <div className="rounded-lg border border-line bg-bg-1 p-4">
            <div className="kicker">Kod turniejowy</div>
            <div className="my-2 select-all break-all font-mono text-lg text-text-hi">{m.riot.tournamentCode}</div>
            <Button size="sm" variant="ghost"
              onClick={() => navigator.clipboard.writeText(m.riot.tournamentCode ?? '')}>Kopiuj kod</Button>
          </div>
          {riotLobby.data && (
            <div>
              <div className="mb-2 flex items-center justify-between">
                <span className="font-semibold text-text-hi">W lobby: {riotLobby.data.joinedCount}/{riotLobby.data.expectedCount}</span>
                {riotLobby.data.gameStarted && <Badge tone="win">klient wykrył start</Badge>}
              </div>
              <div className="grid gap-2 sm:grid-cols-2">
                {riotLobby.data.members.map((member) => (
                  <div key={member.playerId} className="flex items-center justify-between rounded border border-line px-3 py-2 text-sm">
                    <span>{member.nickname}</span>
                    <Badge tone={member.joined ? 'win' : 'pending'}>{member.joined ? 'w lobby' : 'oczekuje'}</Badge>
                  </div>
                ))}
              </div>
            </div>
          )}
          <div className="flex flex-wrap gap-3">
            <Button variant="ghost" disabled={riotLobby.isFetching} onClick={() => riotLobby.refetch()}>
              {riotLobby.isFetching ? 'Sprawdzanie…' : 'Odśwież lobby'}
            </Button>
            <Button variant="gold" disabled={start.isPending} onClick={() => start.mutate()}>
              {start.isPending ? 'Uruchamianie…' : 'Wszyscy są — uruchom mecz'}
            </Button>
          </div>
          {(riotLobby.isError || start.isError) && (
            <p className="text-sm text-loss">{(riotLobby.error ?? start.error)?.message}</p>
          )}
        </section>
      )}

      {/* LIVE / REJECTED → enter (or fix) results */}
      {(m.status === 'LIVE' || m.status === 'REJECTED') && (
        <>
          {m.status === 'LIVE' && m.riot.tournamentCode && (
            <div className="rounded-lg border border-[color:var(--cyan)]/40 bg-[color:var(--cyan)]/10 p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="font-semibold text-text-hi">Automatyczne statystyki Riot</div>
                  <p className="text-xs text-text-lo">
                    Callback pobierze wynik automatycznie. Jeśli po około 5 minutach nic się nie pojawi, użyj pobrania ręcznego.
                  </p>
                </div>
                <Button variant="ghost" disabled={importRiot.isPending} onClick={() => importRiot.mutate()}>
                  {importRiot.isPending ? 'Pobieranie…' : 'Pobierz dane z Riot ręcznie'}
                </Button>
              </div>
              {(m.riot.importError || importRiot.isError) && (
                <p className="mt-2 text-sm text-loss">{importRiot.error?.message ?? m.riot.importError}</p>
              )}
            </div>
          )}
          {m.status === 'LIVE' && !m.riot.tournamentCode && (
            <div className="rounded-lg border border-line bg-bg-1 p-4 text-sm text-text-lo">
              Mecz ręczny — wpisz statystyki poniżej i zapisz, aby przejść do akceptacji.
            </div>
          )}
          {m.status === 'REJECTED' && m.approval?.rejectionReason && (
            <div className="rounded-lg border border-[color:var(--loss)]/40 bg-[color:var(--loss)]/10 p-4 text-sm">
              <span className="font-semibold text-loss">Odesłano do edycji:</span>{' '}
              {m.approval.rejectionReason}
            </div>
          )}
          <ResultsForm
            match={m}
            submitting={submit.isPending || edit.isPending}
            onSubmit={(req) => (m.status === 'REJECTED' ? edit.mutate(req) : submit.mutate(req))}
          />
        </>
      )}

      {/* RESULTS_SUBMITTED → awaiting sign-off */}
      {m.status === 'RESULTS_SUBMITTED' && (
        <>
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-[color:var(--pending)]/40 bg-[color:var(--pending)]/10 p-4">
            <span className="text-sm text-text-hi">Wyniki czekają na akceptację admina.</span>
            <div className="flex gap-2">
              <Button variant="ghost" size="sm" onClick={() => setEditingResults((v) => !v)}>
                {editingResults ? 'Zwiń edycję' : 'Edytuj wynik'}
              </Button>
              <Link to="/admin/approvals">
                <Button variant="gold" size="sm">Przejdź do akceptacji</Button>
              </Link>
            </div>
          </div>
          {editingResults ? (
            <ResultsForm
              match={m}
              submitting={edit.isPending}
              onSubmit={(req) => edit.mutate(req, { onSuccess: () => setEditingResults(false) })}
            />
          ) : (
            <Scoreboard match={m} />
          )}
          {matchTools}
        </>
      )}

      {/* APPROVED / CANCELLED */}
      {(m.status === 'APPROVED' || m.status === 'CANCELLED') && (
        <>
          {m.status === 'APPROVED' && (
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-line p-4">
              <span className="text-sm text-text-lo">Mecz zatwierdzony i policzony do rankingu.</span>
              <Button variant="ghost" size="sm" disabled={reopen.isPending} onClick={() => reopen.mutate()}>
                {reopen.isPending ? 'Otwieranie…' : 'Edytuj wynik (ponów akceptację)'}
              </Button>
            </div>
          )}
          <Scoreboard match={m} />
          {m.status === 'APPROVED' && matchTools}
        </>
      )}
    </div>
  );
}
