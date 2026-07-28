import { useMemo, useState } from 'react';
import { useChampions } from '../../api/hooks/champions';
import { DraftBoard } from '../player/DraftBoard';
import { simRoster, useDraftSimulation, type BotSpeed } from '../../lib/draftSim';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { CardSkeleton, EmptyState, ErrorState } from '../../components/ui/States';
import { VolumeControl } from '../../components/ui/VolumeControl';
import { roleLabel } from '../../lib/format';
import { sound } from '../../lib/sound';

const BOT_SPEEDS: { value: BotSpeed; label: string }[] = [
  { value: 'fast', label: 'Szybkie (lock po ~¼ czasu)' },
  { value: 'normal', label: 'Normalne (lock w połowie)' },
  { value: 'slow', label: 'Wolne (lock tuż przed czasem)' },
];

/**
 * A full draft, played out against bots, on the real board.
 *
 * Same component, same audio cues, same clock as the live tournament draft — only the transport is
 * different: instead of the API and SSE, a local simulation drives the state (see `lib/draftSim`).
 * Nothing here touches a match or the database.
 *
 * The board opens full-screen, exactly as it does in the player panel, so what an admin sees here is
 * pixel-for-pixel what the ten players see. The simulation controls ride above it in a floating dock
 * rather than sitting beside the board, which would have changed the layout being tested.
 */
export function AdminDraftTestPage() {
  const champions = useChampions();
  const roster = useMemo(() => simRoster(), []);
  const [mySeat, setMySeat] = useState<string | null>(roster[0].playerId);
  const [stepSeconds, setStepSeconds] = useState(30);
  const [botSpeed, setBotSpeed] = useState<BotSpeed>('normal');
  const [autoPlayMe, setAutoPlayMe] = useState(false);
  // Matches the players' default (`PlayerPanelPage` opens the draft tab full-screen too).
  const [fullscreen, setFullscreen] = useState(true);

  const sim = useDraftSimulation({
    champions: champions.data ?? [],
    stepSeconds,
    botSpeed,
    mySeat,
    autoPlayMe,
  });

  if (champions.isLoading) return <CardSkeleton lines={6} />;
  if (champions.isError) return <ErrorState error={champions.error} />;

  const pool = champions.data ?? [];
  const startSim = () => {
    // Starting is a real click, which is also what browsers require before any audio may play.
    sound.unlock();
    sim.start();
  };

  return (
    <div className="space-y-6">
      <header>
        <div className="kicker text-gold">Panel administratora</div>
        <h1 className="mt-1 font-display text-4xl">Test draftu</h1>
        <p className="mt-2 max-w-3xl text-sm text-text-lo">
          Pełna symulacja draftu turniejowego: ta sama plansza, te same dźwięki i ten sam zegar, co u
          graczy. Dziewięciu pozostałych zawodników prowadzą boty. Nic nie jest zapisywane — żaden
          mecz ani gracz nie jest ruszany.
        </p>
      </header>

      {pool.length < 25 && (
        <div className="rounded-lg border border-[color:var(--pending)]/40 bg-[color:var(--pending)]/10 p-3 text-sm text-pending">
          W bazie jest tylko {pool.length} postaci — draft potrzebuje ich 20 na bany i wybory.
          Uruchom synchronizację Data Dragon, żeby symulacja miała z czego wybierać.
        </div>
      )}

      <section className="glass grid-tex p-5">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <label>
            <span className="kicker">Grasz jako</span>
            <select
              className="form-control mt-1"
              value={mySeat ?? ''}
              onChange={(e) => setMySeat(e.target.value || null)}
            >
              <option value="">Obserwator (same boty)</option>
              {roster.map((player) => (
                <option key={player.playerId} value={player.playerId}>
                  {player.side === 'BLUE' ? '🔵' : '🔴'} {player.nickname} — {roleLabel(player.role)}
                  {player.captain ? ' ★' : ''}
                </option>
              ))}
            </select>
          </label>

          <label>
            <span className="kicker">Czas kroku (s)</span>
            <input
              className="form-control mt-1"
              type="number"
              min={5}
              max={120}
              value={stepSeconds}
              onChange={(e) => setStepSeconds(Math.min(120, Math.max(5, Number(e.target.value) || 30)))}
            />
          </label>

          <label>
            <span className="kicker">Tempo botów</span>
            <select className="form-control mt-1" value={botSpeed}
              onChange={(e) => setBotSpeed(e.target.value as BotSpeed)}>
              {BOT_SPEEDS.map((speed) => (
                <option key={speed.value} value={speed.value}>{speed.label}</option>
              ))}
            </select>
          </label>

          <div className="flex flex-col justify-end gap-2">
            <span className="kicker">Dźwięk</span>
            <VolumeControl />
          </div>
        </div>

        <div className="mt-5 flex flex-wrap items-center gap-3 border-t border-line pt-4">
          {!sim.running ? (
            <Button variant="gold" onClick={startSim}>▶ Uruchom symulację</Button>
          ) : (
            <SimControls sim={sim} onRestart={startSim} />
          )}

          <label className="ml-auto flex items-center gap-2 text-sm text-text-lo">
            <input type="checkbox" checked={autoPlayMe} disabled={!mySeat}
              onChange={(e) => setAutoPlayMe(e.target.checked)} />
            Bot gra także za mnie
          </label>

          {sim.finished && <Badge tone="win">draft zakończony</Badge>}
        </div>

        {sim.running && !mySeat && (
          <p className="mt-3 text-xs text-text-lo">
            Tryb obserwatora: widzisz dokładnie to, co gracz czekający na swoją kolej.
          </p>
        )}
        {sim.running && !fullscreen && (
          <p className="mt-3 text-xs text-text-lo">
            Widok zwinięty. Kliknij ⛶ na planszy, żeby wrócić do widoku 1:1 z panelem gracza.
          </p>
        )}
      </section>

      {sim.lobby ? (
        <>
          <DraftBoard
            lobby={sim.lobby}
            myPlayerId={mySeat ?? '—'}
            actions={sim.actions}
            streamState="live"
            fullscreen={fullscreen}
            onCollapse={() => setFullscreen((open) => !open)}
          />
          {/* The board covers the viewport when full-screen, so the sim controls float above it. */}
          {fullscreen && (
            <div className="fixed bottom-4 left-1/2 z-50 -translate-x-1/2">
              <div className="panel flex flex-wrap items-center justify-center gap-2 px-3 py-2 shadow-pop">
                <span className="kicker mr-1 hidden text-gold sm:inline">Symulacja</span>
                <SimControls sim={sim} onRestart={startSim} />
              </div>
            </div>
          )}
        </>
      ) : (
        <EmptyState
          title="Symulacja nie działa"
          description="Wybierz miejsce w składzie i kliknij „Uruchom symulację”. Kliknięcie odblokowuje też dźwięk w przeglądarce."
          action={<Button variant="gold" onClick={startSim}>▶ Uruchom symulację</Button>}
        />
      )}
    </div>
  );
}

/** The pause / skip / finish / restart / stop row, shared by the settings card and the floating dock. */
function SimControls({ sim, onRestart }: {
  sim: ReturnType<typeof useDraftSimulation>; onRestart: () => void;
}) {
  return (
    <>
      <Button variant="ghost" size="sm" onClick={sim.togglePause}>
        {sim.lobby?.draft?.paused ? '▶ Wznów' : '⏸ Pauza'}
      </Button>
      <Button variant="ghost" size="sm" onClick={sim.skipStep} disabled={sim.finished}>⏭ Pomiń krok</Button>
      <Button variant="ghost" size="sm" onClick={sim.finish} disabled={sim.finished}>⏩ Dokończ</Button>
      <Button variant="ghost" size="sm" onClick={onRestart}>↻ Od nowa</Button>
      <Button variant="danger" size="sm" onClick={sim.stop}>■ Zatrzymaj</Button>
    </>
  );
}
