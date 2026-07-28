import { useState } from 'react';
import {
  useAiModels, useRuntimeConfig, useTestAiModel, type AiTestResult,
} from '../../api/hooks/config';
import { useOllamaHealth } from '../../api/hooks/diagnostics';
import { SettingsForm } from '../../components/admin/SettingsForm';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { CardSkeleton, ErrorState, Spinner } from '../../components/ui/States';
import { RuntimeConfigGroups } from './adminConfigGroups';

/**
 * AI panel: swap the model that reads end-game screenshots without redeploying.
 *
 * Testing a candidate is deliberately separate from saving it — the check runs against whatever
 * model name is typed in, so a model can be proven to answer before it becomes the one the OCR
 * pipeline depends on.
 */
export function AdminAiPage() {
  const config = useRuntimeConfig();
  const models = useAiModels();
  const test = useTestAiModel();
  const health = useOllamaHealth();
  const [candidate, setCandidate] = useState('');
  const [result, setResult] = useState<AiTestResult | null>(null);

  if (config.isLoading) return <CardSkeleton lines={6} />;
  if (config.isError || !config.data) return <ErrorState error={config.error} />;

  const group = config.data.groups.find((g) => g.name === RuntimeConfigGroups.AI);
  const activeModel = models.data?.activeModel
    ?? group?.settings.find((s) => s.key === 'ollama.vision-model')?.value
    ?? null;
  const available = models.data?.models ?? [];
  const modelToTest = candidate.trim() || activeModel || '';

  const runTest = () => {
    setResult(null);
    test.mutate({ model: modelToTest }, {
      onSuccess: setResult,
      onError: (error) => setResult({
        ok: false, model: modelToTest, elapsedMillis: 0, reply: null,
        message: (error as Error).message,
      }),
    });
  };

  return (
    <div className="space-y-8">
      <header>
        <div className="kicker text-gold">Panel administratora</div>
        <h1 className="mt-1 font-display text-4xl">AI</h1>
        <p className="mt-2 max-w-2xl text-sm text-text-lo">
          Model wizyjny czyta screenshoty z końca gry i wypełnia wyniki meczu. Klucz, adres i model
          zmieniasz tutaj — zapis działa od razu, bez restartu backendu.
        </p>
      </header>

      <section className="glass grid-tex p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h2 className="font-display text-xl">Aktywny model</h2>
            <div className="mt-1 flex flex-wrap items-center gap-2">
              <code className="num text-lg text-gold">{activeModel ?? 'nie ustawiono'}</code>
              {health.data && (
                <Badge tone={health.data.ok ? 'win' : health.data.configured ? 'loss' : 'pending'}>
                  {health.data.ok ? 'połączono' : health.data.configured ? 'błąd' : 'brak konfiguracji'}
                </Badge>
              )}
            </div>
            {health.data && <p className="mt-1 text-xs text-text-lo">{health.data.message}</p>}
          </div>
          <Button variant="ghost" onClick={() => { void models.refetch(); void health.refetch(); }}
            disabled={models.isFetching}>
            {models.isFetching ? 'Odświeżanie…' : '↻ Odśwież listę modeli'}
          </Button>
        </div>

        {models.data && !models.data.ok && (
          <p className="mt-3 rounded-md border border-[color:var(--pending)]/40 bg-[color:var(--pending)]/10 p-2.5 text-sm text-pending">
            Nie udało się pobrać listy modeli z konta Ollama: {models.data.message}. Nadal możesz
            wpisać nazwę modelu ręcznie.
          </p>
        )}
      </section>

      <section className="glass grid-tex p-6">
        <h2 className="font-display text-xl">Przetestuj model</h2>
        <p className="mt-1 text-sm text-text-lo">
          Wysyła jedno krótkie zapytanie i mierzy czas odpowiedzi. Nic nie zapisuje — sprawdź model
          tutaj, a dopiero potem ustaw go jako aktywny poniżej.
        </p>
        <div className="mt-4 flex flex-wrap items-end gap-3">
          <label className="min-w-64 flex-1">
            <span className="kicker">Model do sprawdzenia</span>
            {available.length > 0 ? (
              <select className="form-control mt-1" value={candidate}
                onChange={(e) => setCandidate(e.target.value)}>
                <option value="">Aktywny ({activeModel ?? 'brak'})</option>
                {available.map((model) => <option key={model} value={model}>{model}</option>)}
              </select>
            ) : (
              <input className="form-control mt-1" value={candidate} placeholder={activeModel ?? 'nazwa modelu'}
                onChange={(e) => setCandidate(e.target.value)} />
            )}
          </label>
          <Button variant="gold" onClick={runTest} disabled={test.isPending || !modelToTest}>
            {test.isPending ? 'Testowanie…' : '⚡ Testuj'}
          </Button>
          {test.isPending && <Spinner />}
        </div>

        {result && (
          <div className={`mt-4 rounded-lg border p-4 ${result.ok
            ? 'border-[color:var(--win)]/40 bg-[color:var(--win)]/10'
            : 'border-[color:var(--loss)]/40 bg-[color:var(--loss)]/10'}`}>
            <div className="flex flex-wrap items-center gap-2">
              <span className={result.ok ? 'font-semibold text-win' : 'font-semibold text-loss'}>
                {result.ok ? '✓ Model odpowiedział' : '✗ Model nie odpowiedział'}
              </span>
              <code className="num text-xs text-text-lo">{result.model}</code>
              {result.elapsedMillis > 0 && (
                <span className="num text-xs text-text-lo">{result.elapsedMillis} ms</span>
              )}
            </div>
            <p className="mt-1 text-sm text-text">{result.message}</p>
            {result.reply && (
              <pre className="mt-2 overflow-x-auto rounded bg-[color:var(--bg)]/60 p-2 text-xs text-text-lo">
                {result.reply}
              </pre>
            )}
          </div>
        )}
      </section>

      <section className="glass grid-tex p-6">
        <h2 className="font-display text-xl">Konfiguracja AI</h2>
        <p className="mt-1 mb-4 text-sm text-text-lo">
          Odpowiada zmiennym <code>OLLAMA_*</code> z <code>.env</code>. Zapisane wartości nadpisują
          plik i przeżywają restart.
        </p>
        {group
          ? <SettingsForm settings={group.settings} extraOptions={{ 'ollama.vision-model': available }} />
          : <p className="text-sm text-text-lo">Brak ustawień AI w tej wersji backendu.</p>}
      </section>
    </div>
  );
}
