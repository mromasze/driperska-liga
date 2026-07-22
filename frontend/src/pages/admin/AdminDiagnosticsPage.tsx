import { useCheckService, type DiagService } from '../../api/hooks/diagnostics';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';

const SERVICES: { key: DiagService; title: string; desc: string }[] = [
  { key: 'ollama', title: 'AI (Ollama)', desc: 'Odczyt statystyk ze screenshotów meczu.' },
  { key: 'discord', title: 'Discord', desc: 'Bot, wysyłka DM i obrazków wyników.' },
  { key: 'riot', title: 'Riot API', desc: 'Klucz API (Tournament API to osobny dostęp).' },
];

function DiagnosticCard({ service, title, desc }: { service: DiagService; title: string; desc: string }) {
  const check = useCheckService();
  const health = check.data;
  return (
    <div className="glass p-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="font-display text-lg text-text-hi">{title}</h2>
          <p className="mt-1 text-xs text-text-lo">{desc}</p>
        </div>
        <Button variant="ghost" size="sm" disabled={check.isPending} onClick={() => check.mutate(service)}>
          {check.isPending ? 'Sprawdzam…' : 'Sprawdź'}
        </Button>
      </div>
      {check.isError && <p className="mt-3 text-sm text-loss">⚠ {(check.error as Error).message}</p>}
      {health && (
        <div className="mt-3 flex items-center gap-2">
          <Badge tone={health.ok ? 'win' : 'loss'}>{health.ok ? 'OK' : 'Błąd'}</Badge>
          {!health.configured && <Badge tone="pending">nieskonfigurowane</Badge>}
          <span className="text-sm text-text">{health.message}</span>
        </div>
      )}
    </div>
  );
}

export function AdminDiagnosticsPage() {
  return (
    <div className="space-y-6">
      <div>
        <div className="kicker text-gold">Administracja</div>
        <h1 className="font-display text-3xl">Diagnostyka</h1>
        <p className="mt-2 text-sm text-text-lo">
          Sprawdź połączenie z usługami zewnętrznymi. Testy są lekkie i nie zmieniają żadnych danych.
        </p>
      </div>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {SERVICES.map((s) => <DiagnosticCard key={s.key} service={s.key} title={s.title} desc={s.desc} />)}
      </div>
    </div>
  );
}
