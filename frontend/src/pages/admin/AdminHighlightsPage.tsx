import { useRef } from 'react';
import { useDeleteHighlight, useHighlights, useUploadHighlight } from '../../api/hooks/highlights';
import { Button } from '../../components/ui/Button';
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/States';
import { formatDateTime } from '../../lib/format';

function megabytes(bytes: number) {
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function AdminHighlightsPage() {
  const highlights = useHighlights();
  const upload = useUploadHighlight();
  const remove = useDeleteHighlight();
  const input = useRef<HTMLInputElement>(null);

  if (highlights.isLoading) return <LoadingState />;
  if (highlights.isError) return <ErrorState error={highlights.error} />;

  const videos = highlights.data ?? [];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div className="kicker text-gold">Strona główna</div>
          <h1 className="font-display text-3xl">Najlepsze zagrywki</h1>
          <p className="mt-2 max-w-2xl text-sm text-text-lo">
            Klipy są odtwarzane kolejno jako subtelne tło sekcji głównej. MP4 lub WebM, maksymalnie 100 MB.
          </p>
        </div>
        <input
          ref={input}
          className="hidden"
          type="file"
          accept="video/mp4,video/webm,.mp4,.webm"
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file) upload.mutate(file);
            event.currentTarget.value = '';
          }}
        />
        <Button variant="gold" disabled={upload.isPending} onClick={() => input.current?.click()}>
          {upload.isPending ? 'Wgrywanie…' : '+ Wgraj klip'}
        </Button>
      </div>

      {upload.isError && <p className="text-sm text-loss">{upload.error.message}</p>}
      {remove.isError && <p className="text-sm text-loss">{remove.error.message}</p>}

      {videos.length === 0 ? (
        <EmptyState
          title="Nie ma jeszcze klipów"
          description="Wgraj pierwszą zagrywkę — po zapisaniu od razu pojawi się w tle strony głównej."
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {videos.map((video) => (
            <article key={video.id} className="glass overflow-hidden">
              <video className="aspect-video w-full bg-black object-cover" src={video.url} controls preload="metadata" />
              <div className="flex items-center justify-between gap-3 p-4">
                <div className="min-w-0">
                  <div className="truncate font-mono text-xs text-text-hi">{video.id}</div>
                  <div className="mt-1 text-xs text-text-lo">
                    {megabytes(video.sizeBytes)} · {formatDateTime(video.uploadedAt)}
                  </div>
                </div>
                <Button
                  variant="danger"
                  size="sm"
                  disabled={remove.isPending}
                  onClick={() => {
                    if (window.confirm('Usunąć ten klip ze strony głównej?')) remove.mutate(video.id);
                  }}
                >
                  Usuń
                </Button>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
