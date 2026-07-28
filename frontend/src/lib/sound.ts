/**
 * Draft audio: a looping music bed plus short cues for the moments that matter (your turn, lock-in,
 * ban, the last seconds ticking away, phase changes).
 *
 * Two sources, in order of preference:
 *  1. Real audio files under `public/sounds/` — see `MUSIC_SOURCES` / `CUE_SOURCES` for the names.
 *     Drop the League soundtrack in as `public/sounds/draft-music.mp3` and it is picked up with no
 *     code change.
 *  2. If a file is absent (404 / unsupported), short WebAudio tones are synthesised instead, so the
 *     draft always has audible feedback even on a fresh checkout with no assets committed.
 *
 * Browsers refuse to start audio before a user gesture, so nothing plays until `unlock()` has been
 * called from a real interaction; `SoundEngine.armOnFirstGesture()` wires that up once.
 */

const VOLUME_KEY = 'driperska-draft-volume';
const MUTED_KEY = 'driperska-draft-muted';

/** Tried in order; the first one that loads wins. */
const MUSIC_SOURCES = ['/sounds/draft-music.mp3', '/sounds/draft-music.ogg'];

export type SoundCue =
  | 'draftStart'
  | 'yourTurn'
  | 'lockIn'
  | 'ban'
  | 'tick'
  | 'draftDone'
  | 'error';

const CUE_SOURCES: Record<SoundCue, string[]> = {
  draftStart: ['/sounds/draft-start.mp3'],
  yourTurn: ['/sounds/your-turn.mp3'],
  lockIn: ['/sounds/lock-in.mp3'],
  ban: ['/sounds/ban.mp3'],
  tick: ['/sounds/tick.mp3'],
  draftDone: ['/sounds/draft-done.mp3'],
  error: ['/sounds/error.mp3'],
};

/** Synthesised stand-ins: [frequency Hz, seconds, waveform, gain]. */
const CUE_TONES: Record<SoundCue, [number, number, OscillatorType, number][]> = {
  draftStart: [[220, 0.18, 'sawtooth', 0.5], [330, 0.22, 'sawtooth', 0.45], [440, 0.3, 'triangle', 0.4]],
  yourTurn: [[880, 0.12, 'triangle', 0.6], [1175, 0.16, 'triangle', 0.5]],
  lockIn: [[523, 0.09, 'square', 0.35], [784, 0.14, 'triangle', 0.45]],
  ban: [[196, 0.16, 'sawtooth', 0.4], [147, 0.22, 'sawtooth', 0.35]],
  tick: [[1320, 0.05, 'square', 0.25]],
  draftDone: [[523, 0.14, 'triangle', 0.5], [659, 0.14, 'triangle', 0.5], [880, 0.34, 'triangle', 0.45]],
  error: [[160, 0.28, 'square', 0.35]],
};

function readVolume(): number {
  const raw = Number(localStorage.getItem(VOLUME_KEY));
  return Number.isFinite(raw) && raw >= 0 && raw <= 1 ? raw : 0.35;
}

class SoundEngine {
  private volume = readVolume();
  private muted = localStorage.getItem(MUTED_KEY) === '1';
  private unlocked = false;
  private music: HTMLAudioElement | null = null;
  private musicWanted = false;
  private context: AudioContext | null = null;
  private cues = new Map<SoundCue, HTMLAudioElement | null>();
  private listeners = new Set<() => void>();

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private emit() {
    this.listeners.forEach((l) => l());
  }

  getVolume(): number { return this.volume; }
  isMuted(): boolean { return this.muted; }
  isUnlocked(): boolean { return this.unlocked; }
  /** Effective gain applied to everything: 0 while muted. */
  private gain(): number { return this.muted ? 0 : this.volume; }

  setVolume(value: number) {
    this.volume = Math.min(1, Math.max(0, value));
    localStorage.setItem(VOLUME_KEY, String(this.volume));
    // Raising the slider from zero is an unmute in spirit; honour that.
    if (this.volume > 0 && this.muted) this.setMuted(false);
    if (this.music) this.music.volume = this.gain() * 0.6;
    this.emit();
  }

  setMuted(muted: boolean) {
    this.muted = muted;
    localStorage.setItem(MUTED_KEY, muted ? '1' : '0');
    if (this.music) this.music.volume = this.gain() * 0.6;
    if (muted) this.music?.pause();
    else if (this.musicWanted) void this.music?.play().catch(() => undefined);
    this.emit();
  }

  /** Enables audio on the first real user gesture anywhere in the page. */
  armOnFirstGesture() {
    if (this.unlocked) return;
    const unlock = () => {
      this.unlock();
      window.removeEventListener('pointerdown', unlock);
      window.removeEventListener('keydown', unlock);
    };
    window.addEventListener('pointerdown', unlock, { once: true });
    window.addEventListener('keydown', unlock, { once: true });
  }

  unlock() {
    if (this.unlocked) return;
    this.unlocked = true;
    try {
      this.context = new AudioContext();
      void this.context.resume();
    } catch {
      this.context = null;
    }
    if (this.musicWanted) void this.startMusic();
    this.emit();
  }

  /** Starts (or resumes) the looping music bed. No-op until audio is unlocked. */
  async startMusic() {
    this.musicWanted = true;
    if (!this.unlocked || this.muted) return;
    if (!this.music) {
      const element = await loadFirst(MUSIC_SOURCES);
      // No music file shipped: stay silent rather than looping a synth drone forever.
      if (!element) return;
      element.loop = true;
      this.music = element;
    }
    this.music.volume = this.gain() * 0.6;
    try {
      await this.music.play();
    } catch {
      // Still blocked by the browser; the next gesture will retry.
    }
  }

  stopMusic() {
    this.musicWanted = false;
    this.music?.pause();
    if (this.music) this.music.currentTime = 0;
  }

  play(cue: SoundCue) {
    if (!this.unlocked || this.gain() === 0) return;
    if (!this.cues.has(cue)) {
      this.cues.set(cue, null);
      void loadFirst(CUE_SOURCES[cue]).then((element) => this.cues.set(cue, element));
    }
    const element = this.cues.get(cue);
    if (element) {
      const clone = element.cloneNode() as HTMLAudioElement;
      clone.volume = this.gain();
      void clone.play().catch(() => undefined);
      return;
    }
    this.synth(cue);
  }

  /** WebAudio fallback: a short arpeggio so each cue is still distinguishable. */
  private synth(cue: SoundCue) {
    const ctx = this.context;
    if (!ctx) return;
    let at = ctx.currentTime;
    for (const [freq, duration, type, level] of CUE_TONES[cue]) {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = type;
      osc.frequency.value = freq;
      gain.gain.setValueAtTime(0, at);
      gain.gain.linearRampToValueAtTime(level * this.gain(), at + 0.012);
      gain.gain.exponentialRampToValueAtTime(0.0001, at + duration);
      osc.connect(gain).connect(ctx.destination);
      osc.start(at);
      osc.stop(at + duration + 0.02);
      at += duration * 0.75;
    }
  }
}

/** Resolves to a playable element, or null when none of the candidates can be loaded. */
function loadFirst(sources: string[]): Promise<HTMLAudioElement | null> {
  return sources.reduce<Promise<HTMLAudioElement | null>>(
    (chain, src) => chain.then((found) => (found ? found : tryLoad(src))),
    Promise.resolve(null),
  );
}

function tryLoad(src: string): Promise<HTMLAudioElement | null> {
  return new Promise((resolve) => {
    const audio = new Audio(src);
    audio.preload = 'auto';
    const done = (result: HTMLAudioElement | null) => {
      audio.removeEventListener('canplaythrough', ok);
      audio.removeEventListener('error', fail);
      resolve(result);
    };
    const ok = () => done(audio);
    const fail = () => done(null);
    audio.addEventListener('canplaythrough', ok, { once: true });
    audio.addEventListener('error', fail, { once: true });
    // Never leave a cue pending forever on a stalled request.
    window.setTimeout(() => done(audio.readyState >= 3 ? audio : null), 8_000);
  });
}

export const sound = new SoundEngine();
