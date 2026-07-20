import type { DrawPlayer, DrawResult, Side } from '../../api/types';
import { Card, CardBody, CardHeader, CardTitle } from '../ui/Card';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';
import { roleLabel } from '../../lib/format';

export interface DrawBoardProps {
  draw: DrawResult | null;
  onReroll: () => void;
  onConfirm: () => void;
  isDrawing?: boolean;
  isConfirming?: boolean;
}

/**
 * Draw visualisation (docs/06 §6.6): two teams with MMR + predicted chance and
 * re-roll / confirm actions. Skeleton — animation ("shuffle") is a follow-up.
 */
export function DrawBoard({ draw, onReroll, onConfirm, isDrawing, isConfirming }: DrawBoardProps) {
  return (
    <Card>
      <CardHeader className="flex items-center justify-between">
        <CardTitle>Losowanie drużyn</CardTitle>
        {draw?.balance && (
          <Badge tone="info">
            {Math.round(draw.balance.predictedBlueWinPct)}% / {Math.round(100 - draw.balance.predictedBlueWinPct)}%
          </Badge>
        )}
      </CardHeader>
      <CardBody>
        {draw ? (
          <div className="grid gap-4 md:grid-cols-2">
            <DrawTeam
              side="BLUE"
              players={draw.blue}
              mmrAvg={draw.balance?.blueMmrAvg}
              winPct={draw.balance?.predictedBlueWinPct}
            />
            <DrawTeam
              side="RED"
              players={draw.red}
              mmrAvg={draw.balance?.redMmrAvg}
              winPct={
                draw.balance ? 100 - draw.balance.predictedBlueWinPct : undefined
              }
            />
          </div>
        ) : (
          <p className="py-6 text-center text-sm text-text-lo">
            Brak propozycji składów — kliknij „Losuj", aby wygenerować drużyny.
          </p>
        )}

        <div className="mt-4 flex flex-wrap gap-2">
          <Button variant="secondary" onClick={onReroll} disabled={isDrawing}>
            {draw ? 'Losuj ponownie' : 'Losuj'}
          </Button>
          <Button variant="gold" onClick={onConfirm} disabled={!draw || isConfirming}>
            Zatwierdź składy
          </Button>
        </div>
      </CardBody>
    </Card>
  );
}

function DrawTeam({
  side,
  players,
  mmrAvg,
  winPct,
}: {
  side: Side;
  players: DrawPlayer[];
  mmrAvg?: number;
  winPct?: number;
}) {
  const color = side === 'BLUE' ? 'var(--blue)' : 'var(--red)';
  const bg = side === 'BLUE' ? 'var(--blue-bg)' : 'var(--red-bg)';
  return (
    <div className="rounded-md border border-line p-3" style={{ backgroundColor: bg }}>
      <div className="mb-2 flex items-center justify-between">
        <span className="font-semibold" style={{ color }}>
          {side === 'BLUE' ? 'Niebiescy' : 'Czerwoni'}
        </span>
        <span className="num text-xs text-text-lo">
          Ø MMR {mmrAvg != null ? Math.round(mmrAvg) : '—'}
          {winPct != null && ` · ${Math.round(winPct)}%`}
        </span>
      </div>
      <ul className="space-y-1">
        {players.map((p) => (
          <li key={p.playerId} className="flex items-center justify-between text-sm">
            <span className="truncate text-text-hi">{p.nickname}</span>
            <span className="num text-xs text-text-lo">
              {roleLabel(p.role)} · {p.mmr}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
