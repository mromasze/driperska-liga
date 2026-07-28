import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  useAdminSettings, useAnnouncePatchNotes, useUpdateAdminSettings,
} from '../../api/hooks/settings';
import { useRuntimeConfig } from '../../api/hooks/config';
import { RELEASES } from '../../content/releases';
import { SettingsForm } from '../../components/admin/SettingsForm';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { CardSkeleton, ErrorState } from '../../components/ui/States';

export function AdminSettingsPage() {
  const settings = useAdminSettings();
  const update = useUpdateAdminSettings();
  const announce = useAnnouncePatchNotes();
  const [version, setVersion] = useState(RELEASES[0]?.version ?? '');
  const [patchMsg, setPatchMsg] = useState<string | null>(null);

  if (settings.isLoading) return <CardSkeleton lines={5} />;
  if (settings.isError || !settings.data) return <ErrorState error={settings.error} />;

  const riotEnabled = settings.data.riotEnabled;
  const toggleRiot = () => update.mutate({ riotEnabled: !riotEnabled });

  const release = RELEASES.find((r) => r.version === version) ?? RELEASES[0];
  const sendPatch = () => {
    if (!release) return;
    if (!window.confirm(`Wysłać patch notes ${release.version} na Discord (z pingiem @everyone)?`)) return;
    setPatchMsg(null);
    announce.mutate(
      { version: release.version, title: release.title, date: release.date, changes: release.changes },
      {
        onSuccess: (r) => setPatchMsg(r.sent ? '✓ Wysłano na Discord.' : '⚠ ' + r.message),
        onError: (e) => setPatchMsg('⚠ ' + (e as Error).message),
      },
    );
  };

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

      <RuntimeConfigSection />

      <section className="glass grid-tex max-w-2xl p-6">
        <h2 className="font-display text-xl">Patch notes na Discord</h2>
        <p className="mt-1 text-sm text-text-lo">
          Wygeneruj obrazek z informacjami o patchu i wyślij go na kanał patch notes (z pingiem @everyone).
          Wybierz wersję z listy zmian i kliknij „Wyślij”.
        </p>
        <div className="mt-4 flex flex-wrap items-end gap-3">
          <label className="min-w-52 flex-1">
            <span className="kicker">Wersja</span>
            <select className="form-control mt-1" value={version} onChange={(e) => setVersion(e.target.value)}>
              {RELEASES.map((r) => (
                <option key={r.version} value={r.version}>{r.version} — {r.title}</option>
              ))}
            </select>
          </label>
          <Button variant="gold" disabled={announce.isPending || !release} onClick={sendPatch}>
            {announce.isPending ? 'Wysyłanie…' : '📤 Wyślij na Discord'}
          </Button>
        </div>
        {release && (
          <div className="mt-4 rounded-lg border border-line bg-[color:var(--bg-1)]/60 p-4">
            <div className="text-sm font-semibold text-text-hi">{release.version} — {release.title}</div>
            <ul className="mt-2 list-disc space-y-1 pl-5 text-xs text-text-lo">
              {release.changes.map((c, i) => <li key={i}>{c}</li>)}
            </ul>
          </div>
        )}
        {patchMsg && <p className="mt-3 text-sm text-text">{patchMsg}</p>}
      </section>
    </div>
  );
}

/**
 * The whole `.env` surface, editable at runtime. Each group collapses so the page stays scannable;
 * a group that has been changed away from the deployed defaults says so on its header, because the
 * one thing worth spotting at a glance is "this box no longer matches the file on disk".
 */
function RuntimeConfigSection() {
  const config = useRuntimeConfig();
  const [open, setOpen] = useState<string | null>(null);

  if (config.isLoading) return <CardSkeleton lines={4} />;
  if (config.isError) {
    // EDITOR accounts get a 403 here — the runtime config is admin-only, and that is not an error
    // worth shouting about on a page they can otherwise use.
    return (
      <section className="glass grid-tex p-6">
        <h2 className="font-display text-xl">Konfiguracja (.env)</h2>
        <p className="mt-1 text-sm text-text-lo">
          Podgląd i edycja konfiguracji są dostępne tylko dla konta administratora.
        </p>
      </section>
    );
  }
  if (!config.data) return null;

  return (
    <section className="glass grid-tex p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="font-display text-xl">Konfiguracja (.env)</h2>
          <p className="mt-1 max-w-2xl text-sm text-text-lo">
            Klucze API, modele, kanały Discorda i czasy — zmieniane w locie, bez restartu backendu.
            Zapisana wartość nadpisuje plik <code>.env</code> i przeżywa restart; „przywróć z .env”
            kasuje nadpisanie. Ustawienia AI mają{' '}
            <Link to="/admin/ai" className="text-gold underline-offset-2 hover:underline">własną zakładkę</Link>.
          </p>
        </div>
      </div>

      <div className="mt-5 space-y-2">
        {config.data.groups.map((group) => {
          const overrides = group.settings.filter((s) => s.overridden).length;
          const expanded = open === group.name;
          return (
            <div key={group.name} className="rounded-lg border border-line">
              <button
                type="button"
                onClick={() => setOpen(expanded ? null : group.name)}
                aria-expanded={expanded}
                className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left"
              >
                <span className="flex flex-wrap items-center gap-2">
                  <span className="font-display text-base text-text-hi">{group.name}</span>
                  <span className="text-xs text-text-lo">{group.settings.length} ustawień</span>
                  {overrides > 0 && <Badge tone="info">{overrides} z panelu</Badge>}
                </span>
                <span className="text-text-lo">{expanded ? '▴' : '▾'}</span>
              </button>
              {expanded && (
                <div className="border-t border-line p-4">
                  <SettingsForm settings={group.settings} />
                </div>
              )}
            </div>
          );
        })}
      </div>
    </section>
  );
}
