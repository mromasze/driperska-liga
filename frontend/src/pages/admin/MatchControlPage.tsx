import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  useMatch,
  useDrawTeams,
  useConfirmDraw,
  useSubmitResults,
  useEditResults,
} from '../../api/hooks/matches';
import { DrawBoard } from '../../components/match/DrawBoard';
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
  const confirm = useConfirmDraw(id);
  const submit = useSubmitResults(id);
  const edit = useEditResults(id);
  const [drawResult, setDrawResult] = useState<DrawResult | null>(null);

  if (match.isLoading) return <LoadingState />;
  if (match.isError) return <ErrorState error={match.error} />;
  if (!match.data) return <EmptyState title="Nie znaleziono meczu" />;

  const m = match.data;
  const runDraw = () => draw.mutate(undefined, { onSuccess: (d) => setDrawResult(d) });

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
              <div className="flex gap-3">
                <Button variant="ghost" onClick={runDraw} disabled={draw.isPending}>
                  🎲 Losuj ponownie
                </Button>
                <Button variant="gold" onClick={() => confirm.mutate()} disabled={confirm.isPending}>
                  Zatwierdź składy — gra rusza
                </Button>
              </div>
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

      {/* LIVE / REJECTED → enter (or fix) results */}
      {(m.status === 'LIVE' || m.status === 'REJECTED') && (
        <>
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
          <div className="flex items-center justify-between rounded-lg border border-[color:var(--pending)]/40 bg-[color:var(--pending)]/10 p-4">
            <span className="text-sm text-text-hi">Wyniki czekają na akceptację admina.</span>
            <Link to="/admin/approvals">
              <Button variant="gold" size="sm">
                Przejdź do akceptacji
              </Button>
            </Link>
          </div>
          <Scoreboard match={m} />
        </>
      )}

      {/* APPROVED / CANCELLED */}
      {(m.status === 'APPROVED' || m.status === 'CANCELLED') && <Scoreboard match={m} />}
    </div>
  );
}
