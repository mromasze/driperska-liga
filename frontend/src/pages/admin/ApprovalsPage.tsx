import { useEffect, useMemo, useState } from 'react';
import { useApproveMatch, useMatch, useMatches, useRejectMatch } from '../../api/hooks/matches';
import { useMe } from '../../api/hooks/auth';
import { useAuthStore } from '../../store/auth';
import { Scoreboard } from '../../components/match/Scoreboard';
import { SignOffPanel } from '../../components/admin/SignOffPanel';
import { Card, CardBody, CardHeader, CardTitle } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { LoadingState, ErrorState, EmptyState } from '../../components/ui/States';
import { cn } from '../../lib/cn';
import { formatDateTime } from '../../lib/format';

export function ApprovalsPage() {
  const me = useMe();
  const account = useAuthStore((s) => s.account);
  const isAdmin = (account ?? me.data)?.role === 'ADMIN';

  const queue = useMatches({ status: 'RESULTS_SUBMITTED', size: 50 });
  const items = useMemo(() => queue.data?.content ?? [], [queue.data]);

  const [selectedId, setSelectedId] = useState<string | null>(null);
  useEffect(() => {
    if (!selectedId && items.length > 0) setSelectedId(items[0].id);
  }, [items, selectedId]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl">Kolejka akceptacji</h1>
        <p className="text-sm text-text-lo">
          Mecze w stanie RESULTS_SUBMITTED czekają na decyzję administratora.
        </p>
      </div>

      {queue.isLoading ? (
        <LoadingState />
      ) : queue.isError ? (
        <ErrorState error={queue.error} />
      ) : items.length === 0 ? (
        <EmptyState title="Pusto" description="Brak meczów oczekujących na akceptację." />
      ) : (
        <div className="grid gap-6 lg:grid-cols-[280px_1fr]">
          <Card className="h-fit">
            <CardHeader>
              <CardTitle>Do akceptacji ({items.length})</CardTitle>
            </CardHeader>
            <CardBody className="p-0">
              <ul className="divide-y divide-line">
                {items.map((m) => (
                  <li key={m.id}>
                    <button
                      type="button"
                      onClick={() => setSelectedId(m.id)}
                      className={cn(
                        'flex w-full items-center justify-between px-4 py-3 text-left text-sm transition hover:bg-bg-2',
                        selectedId === m.id && 'bg-bg-2',
                      )}
                    >
                      <span>
                        <span className="block text-text-hi">Mecz {m.id.slice(0, 8)}</span>
                        <span className="block text-xs text-text-lo">
                          {formatDateTime(m.createdAt)}
                        </span>
                      </span>
                      <Badge tone="pending">
                        {m.blueScore}:{m.redScore}
                      </Badge>
                    </button>
                  </li>
                ))}
              </ul>
            </CardBody>
          </Card>

          <div>
            {selectedId ? (
              <ApprovalDetail
                matchId={selectedId}
                isAdmin={isAdmin}
                signatureName={(account ?? me.data)?.username ?? ''}
                onResolved={() => setSelectedId(null)}
              />
            ) : (
              <EmptyState title="Wybierz mecz z listy" />
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function ApprovalDetail({
  matchId,
  isAdmin,
  signatureName,
  onResolved,
}: {
  matchId: string;
  isAdmin: boolean;
  signatureName: string;
  onResolved: () => void;
}) {
  const match = useMatch(matchId);
  const approve = useApproveMatch(matchId);
  const reject = useRejectMatch(matchId);

  if (match.isLoading) return <LoadingState />;
  if (match.isError) return <ErrorState error={match.error} />;
  if (!match.data) return <EmptyState title="Nie znaleziono meczu" />;

  return (
    <div className="space-y-4">
      <Card>
        <CardBody>
          <Scoreboard match={match.data} />
        </CardBody>
      </Card>

      {(approve.isError || reject.isError) && (
        <ErrorState error={approve.error ?? reject.error} title="Operacja nie powiodła się" />
      )}

      {isAdmin ? (
        <SignOffPanel
          defaultSignatureName={signatureName}
          isApproving={approve.isPending}
          isRejecting={reject.isPending}
          onApprove={(name) =>
            approve.mutate(
              { signatureConfirmed: true, signatureName: name },
              { onSuccess: onResolved },
            )
          }
          onReject={(reason) => reject.mutate({ reason }, { onSuccess: onResolved })}
        />
      ) : (
        <Card>
          <CardBody className="text-sm text-text-lo">
            Tylko konto z rolą ADMIN może zatwierdzać wyniki. Twoja rola (EDITOR) pozwala wpisywać i
            edytować statystyki.
          </CardBody>
        </Card>
      )}
    </div>
  );
}
