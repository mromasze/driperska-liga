import { useState } from 'react';
import { usePlannedMatches, useCreatePlannedMatch, useCancelPlannedMatch } from '../../api/hooks/planned';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { LoadingState, EmptyState } from '../../components/ui/States';
import { formatDateTime } from '../../lib/format';

export function AdminSchedulePage() {
  const planned = usePlannedMatches();
  const create = useCreatePlannedMatch();
  const cancel = useCancelPlannedMatch();
  const [when, setWhen] = useState('');
  const [note, setNote] = useState('');
  const [msg, setMsg] = useState<string | null>(null);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!when) return;
    setMsg(null);
    create.mutate(
      { scheduledAt: new Date(when).toISOString(), note: note.trim() || null },
      {
        onSuccess: (r) => {
          setWhen(''); setNote('');
          setMsg(r.announced ? '✓ Zaplanowano i ogłoszono na Discordzie.' : `⚠ Zaplanowano, ale ogłoszenie nie wyszło: ${r.announceMessage}`);
        },
        onError: (e2) => setMsg('⚠ ' + (e2 as Error).message),
      },
    );
  };

  const list = planned.data ?? [];

  return (
    <div className="space-y-8">
      <div>
        <div className="kicker text-gold">Administracja</div>
        <h1 className="font-display text-3xl">Planowanie meczów</h1>
        <p className="mt-2 text-sm text-text-lo">
          Zaplanuj termin i wyślij zbiorowe powiadomienie na Discord z linkiem do potwierdzenia obecności.
          Zaplanowany mecz jest orientacyjny — nie musi się odbyć.
        </p>
      </div>

      <form onSubmit={submit} className="panel flex flex-wrap items-end gap-3 p-4">
        <label className="min-w-52"><span className="kicker">Termin</span>
          <input type="datetime-local" required value={when} onChange={(e) => setWhen(e.target.value)}
            className="form-control mt-1" />
        </label>
        <label className="min-w-64 flex-1"><span className="kicker">Notatka (opcjonalnie)</span>
          <input value={note} onChange={(e) => setNote(e.target.value)}
            placeholder="np. inhouse 5v5, zbiórka na kanale" className="form-control mt-1" />
        </label>
        <Button type="submit" variant="gold" disabled={create.isPending || !when}>
          {create.isPending ? 'Planowanie…' : 'Zaplanuj i ogłoś'}
        </Button>
      </form>
      {msg && <p className="text-sm text-text">{msg}</p>}

      <section>
        <h2 className="mb-3 font-display text-xl">Zaplanowane mecze</h2>
        {planned.isLoading ? <LoadingState /> : list.length === 0 ? (
          <EmptyState title="Brak zaplanowanych meczów" />
        ) : (
          <div className="space-y-3">
            {list.map((p) => (
              <article key={p.id} className="glass p-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <div className="font-semibold text-text-hi">{formatDateTime(p.scheduledAt)}</div>
                    {p.note && <div className="text-sm text-text-lo">{p.note}</div>}
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge tone="win">✓ {p.yes}</Badge>
                    <Badge tone="pending">? {p.maybe}</Badge>
                    <Badge tone="loss">✗ {p.no}</Badge>
                    <Button variant="ghost" size="sm" disabled={cancel.isPending}
                      onClick={() => { if (window.confirm('Anulować zaplanowany mecz?')) cancel.mutate(p.id); }}>
                      Anuluj
                    </Button>
                  </div>
                </div>
                {p.responses.length > 0 && (
                  <div className="mt-3 flex flex-wrap gap-2 text-xs">
                    {p.responses.map((r) => (
                      <span key={r.playerId}
                        className={`rounded px-2 py-1 ${r.response === 'YES' ? 'bg-[color:var(--win)]/15 text-win' : r.response === 'NO' ? 'bg-[color:var(--loss)]/15 text-loss' : 'bg-bg-2 text-text-lo'}`}>
                        {r.response === 'YES' ? '✓' : r.response === 'NO' ? '✗' : '?'} {r.nickname}
                      </span>
                    ))}
                  </div>
                )}
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
