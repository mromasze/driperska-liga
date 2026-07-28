import { useEffect, useState } from 'react';
import { sound } from './sound';

/** Subscribes a component to the shared sound engine's volume / mute / unlocked state. */
export function useSoundSettings() {
  const [state, setState] = useState(() => ({
    volume: sound.getVolume(),
    muted: sound.isMuted(),
    unlocked: sound.isUnlocked(),
  }));

  useEffect(() =>
    sound.subscribe(() =>
      setState({ volume: sound.getVolume(), muted: sound.isMuted(), unlocked: sound.isUnlocked() }),
    ),
  []);

  return state;
}
