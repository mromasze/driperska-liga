import { useEffect, useMemo, useState } from 'react';
import { useHighlights } from '../../api/hooks/highlights';

export function HeroVideoBackground() {
  const highlights = useHighlights();
  const [failed, setFailed] = useState<Set<string>>(() => new Set());
  const [index, setIndex] = useState(0);
  const [reducedMotion, setReducedMotion] = useState(false);
  const videos = useMemo(
    () => (highlights.data ?? []).filter((video) => !failed.has(video.id)),
    [failed, highlights.data],
  );

  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const update = () => setReducedMotion(media.matches);
    update();
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, []);

  useEffect(() => setIndex(0), [highlights.data]);

  const current = videos[index % Math.max(videos.length, 1)];

  return (
    <div className="pointer-events-none absolute inset-0" aria-hidden="true">
      {!reducedMotion && current && (
        <video
          key={current.id}
          className="h-full w-full scale-[1.02] object-cover opacity-[0.24]"
          autoPlay
          muted
          playsInline
          preload="metadata"
          loop={videos.length === 1}
          onEnded={() => setIndex((value) => (value + 1) % videos.length)}
          onError={() => {
            setFailed((value) => new Set(value).add(current.id));
            setIndex(0);
          }}
        >
          <source src={current.url} type={current.id.endsWith('.webm') ? 'video/webm' : 'video/mp4'} />
        </video>
      )}
      <div className="absolute inset-0 bg-gradient-to-r from-[color:var(--bg-1)] via-[color:var(--bg-1)]/85 to-[color:var(--bg-1)]/45" />
      <div className="absolute inset-0 bg-gradient-to-t from-[color:var(--bg-1)]/85 via-transparent to-[color:var(--bg)]/35" />
    </div>
  );
}
