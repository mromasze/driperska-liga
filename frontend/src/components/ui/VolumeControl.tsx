import { sound } from '../../lib/sound';
import { useSoundSettings } from '../../lib/useSound';
import { cn } from '../../lib/cn';

/**
 * Speaker toggle plus volume slider for the draft. Clicking either one is a user gesture, which is
 * also what unlocks audio in the browser — so the control doubles as the "turn sound on" affordance.
 */
export function VolumeControl({ className }: { className?: string }) {
  const { volume, muted, unlocked } = useSoundSettings();
  const level = muted ? 0 : volume;

  return (
    <div className={cn('flex items-center gap-2', className)}>
      <button
        type="button"
        onClick={() => { sound.unlock(); sound.setMuted(!muted); }}
        title={muted ? 'Włącz dźwięk' : 'Wycisz'}
        aria-label={muted ? 'Włącz dźwięk' : 'Wycisz'}
        className="grid h-8 w-8 place-items-center rounded-md border border-line text-text-lo transition hover:text-text-hi"
      >
        {muted || level === 0 ? '🔇' : level < 0.5 ? '🔉' : '🔊'}
      </button>
      <input
        type="range"
        min={0}
        max={100}
        value={Math.round(level * 100)}
        aria-label="Głośność"
        onChange={(e) => { sound.unlock(); sound.setVolume(Number(e.target.value) / 100); }}
        className="h-1.5 w-24 cursor-pointer appearance-none rounded-full bg-bg-2 accent-[var(--gold)] sm:w-32"
      />
      <span className="num w-8 text-right text-xs text-text-lo">{Math.round(level * 100)}</span>
      {!unlocked && (
        <span className="hidden text-[11px] text-text-lo sm:inline">kliknij, by włączyć</span>
      )}
    </div>
  );
}
