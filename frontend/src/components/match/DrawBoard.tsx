import type { DrawResult } from '../../api/types';
import { roleLabel } from '../../lib/format';
import { Button } from '../ui/Button';

interface DrawBoardProps {
  draw: DrawResult;
  drawing?: boolean;
  confirming?: boolean;
  onReroll: () => void;
  onConfirm: () => void;
}

export function DrawBoard({ draw, drawing, confirming, onReroll, onConfirm }: DrawBoardProps) {
  const bluePct = Math.round(draw.balance.predictedBlueWinPct);

  return (
    <div className="space-y-4">
      {/* Balance bar */}
      <div className="glass p-4">
        <div className="mb-2 flex items-center justify-between text-sm">
          <span className="num font-semibold text-blue">{bluePct}%</span>
          <span className="kicker">Przewidywana szansa</span>
          <span className="num font-semibold text-red">{100 - bluePct}%</span>
        </div>
        <div className="flex h-2 overflow-hidden rounded-full bg-bg-2">
          <div style={{ width: `${bluePct}%`, background: 'var(--blue)' }} />
          <div style={{ width: `${100 - bluePct}%`, background: 'var(--red)' }} />
        </div>
        <div className="mt-2 flex justify-between text-xs text-text-lo">
          <span className="num">śr. MMR {Math.round(draw.balance.blueMmrAvg)}</span>
          <span className="num">śr. MMR {Math.round(draw.balance.redMmrAvg)}</span>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <TeamList side="Niebiescy" color="var(--blue)" slots={draw.blue} />
        <TeamList side="Czerwoni" color="var(--red)" slots={draw.red} />
      </div>

      <div className="flex flex-wrap gap-3">
        <Button variant="ghost" onClick={onReroll} disabled={drawing}>
          {drawing ? 'Losowanie…' : '🎲 Losuj ponownie'}
        </Button>
        <Button variant="gold" onClick={onConfirm} disabled={confirming}>
          {confirming ? 'Zatwierdzanie…' : 'Zatwierdź składy — gra rusza'}
        </Button>
      </div>
    </div>
  );
}

function TeamList({
  side,
  color,
  slots,
}: {
  side: string;
  color: string;
  slots: DrawResult['blue'];
}) {
  return (
    <div className="glass overflow-hidden" style={{ borderColor: `color-mix(in srgb, ${color} 35%, transparent)` }}>
      <div className="px-4 py-2 font-display font-semibold" style={{ color, background: `color-mix(in srgb, ${color} 12%, transparent)` }}>
        {side}
      </div>
      <div className="divide-y divide-line">
        {slots.map((s) => (
          <div key={s.playerId} className="flex items-center justify-between px-4 py-2.5">
            <div>
              <div className="font-medium text-text-hi">{s.nickname}</div>
              <div className="kicker">{roleLabel(s.role)}</div>
            </div>
            <span className="num text-sm text-text-lo">{Math.round(s.mmr)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
