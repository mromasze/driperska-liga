import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  useConfirmDraw,
  useDrawTeams,
  useMatch,
  useSubmitResults,
} from '../../api/hooks/matches';
import { useChampions } from '../../api/hooks/champions';
import type { DrawResult } from '../../api/types';
import { DrawBoard } from '../../components/match/DrawBoard';
import { ResultsForm } from '../../components/match/ResultsForm';
import { Badge } from '../../components/ui/Badge';
import { Card, CardBody } from '../../components/ui/Card';
import { LoadingState, ErrorState, EmptyState } from '../../components/ui/States';

export function MatchControlPage() {
  const { id } = useParams<{ id: string }>();
  const matchId = id ?? '';

  const match = useMatch(id);
  const champions = useChampions();
  const draw = useDrawTeams(matchId);
  const confirmDraw = useConfirmDraw(matchId);
  const submitResults = useSubmitResults(matchId);

  const [drawResult, setDrawResult] = useState<DrawResult | null>(null);

  if (match.isLoading) return <LoadingState />;
  if (match.isError) return <ErrorState error={match.error} />;
  if (!match.data) return <EmptyState title="Nie znaleziono meczu" />;

  const m = match.data;
  const inDrawPhase = m.status === 'DRAFT' || m.status === 'TEAMS_DRAWN';
  const inResultsPhase =
    m.status === 'LIVE' || m.status === 'RESULTS_SUBMITTED' || m.status === 'REJECTED';

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/admin" className="text-xs text-text-lo hover:text-text-hi">
            ← Panel
          </Link>
          <h1 className="text-2xl">Kontrola meczu</h1>
          <p className="num text-sm text-text-lo">{matchId.slice(0, 8)}</p>
        </div>
        <Badge tone="info">{m.status}</Badge>
      </div>

      {inDrawPhase && (
        <DrawBoard
          draw={drawResult}
          isDrawing={draw.isPending}
          isConfirming={confirmDraw.isPending}
          onReroll={() => draw.mutate(undefined, { onSuccess: (data) => setDrawResult(data) })}
          onConfirm={() => confirmDraw.mutate()}
        />
      )}

      {inResultsPhase &&
        (champions.isLoading ? (
          <LoadingState label="Ładowanie championów…" />
        ) : (
          <ResultsForm
            participants={m.participants}
            champions={champions.data ?? []}
            isSubmitting={submitResults.isPending}
            onSubmit={(payload) => submitResults.mutate(payload)}
          />
        ))}

      {m.status === 'APPROVED' && (
        <Card>
          <CardBody className="text-sm text-text-lo">
            Mecz został zaakceptowany — punkty naliczone. Korekta wymaga operacji „reopen" (ADMIN).
          </CardBody>
        </Card>
      )}

      {(draw.isError || confirmDraw.isError || submitResults.isError) && (
        <ErrorState
          error={draw.error ?? confirmDraw.error ?? submitResults.error}
          title="Operacja nie powiodła się"
        />
      )}
    </div>
  );
}
