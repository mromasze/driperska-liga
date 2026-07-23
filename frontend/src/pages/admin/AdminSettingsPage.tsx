import { useAdminSettings, useUpdateAdminSettings } from '../../api/hooks/settings';
import { ErrorState, LoadingState } from '../../components/ui/States';

export function AdminSettingsPage() {
  const settings = useAdminSettings();
  const update = useUpdateAdminSettings();

  if (settings.isLoading) return <LoadingState />;
  if (settings.isError || !settings.data) return <ErrorState error={settings.error} />;

  const riotEnabled = settings.data.riotEnabled;
  const toggleRiot = () => update.mutate({ riotEnabled: !riotEnabled });

  return (
    <div className="space-y-8">
      <header>
        <div className="kicker text-gold">Panel administratora</div>
        <h1 className="mt-1 font-display text-4xl">Ustawienia</h1>
      </header>

      <section className="glass grid-tex max-w-2xl p-6">
        <div className="flex items-start justify-between gap-6">
          <div>
            <h2 className="font-display text-xl">Wsparcie Riot API</h2>
            <p className="mt-1 text-sm text-text-lo">
              Gdy <strong>włączone</strong>, zaakceptowanie składu tworzy lobby turniejowe Riot i pokazuje
              graczom kod do dołączenia. Gdy <strong>wyłączone</strong>, po zaakceptowaniu składu startuje
              wewnętrzny draft (bany i wybór postaci), bez kodu Riot.
            </p>
            <p className="mt-2 text-xs text-text-lo">
              Nie mamy jeszcze produkcyjnego dostępu do Tournament API, więc zalecane jest{' '}
              <strong>wyłączone</strong> — kody Riot i tak nie zadziałają w kliencie.
            </p>
          </div>
          <button
            type="button"
            onClick={toggleRiot}
            disabled={update.isPending}
            aria-pressed={riotEnabled}
            className={`relative h-8 w-14 shrink-0 rounded-full transition-colors ${
              riotEnabled ? 'bg-win' : 'bg-bg-2'
            }`}
          >
            <span
              className={`absolute top-1 h-6 w-6 rounded-full bg-white transition-transform ${
                riotEnabled ? 'translate-x-7' : 'translate-x-1'
              }`}
            />
          </button>
        </div>
        <div className="mt-4 flex items-center gap-3 border-t border-line pt-4 text-sm">
          <span className="text-text-lo">Aktualny tryb:</span>
          {riotEnabled ? (
            <span className="font-semibold text-win">Riot API — lobby turniejowe</span>
          ) : (
            <span className="font-semibold text-cyan">Draft wewnętrzny (bez Riot)</span>
          )}
          {update.isPending && <span className="text-text-lo">Zapisywanie…</span>}
        </div>
      </section>
    </div>
  );
}
