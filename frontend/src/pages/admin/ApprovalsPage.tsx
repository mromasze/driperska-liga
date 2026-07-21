import { useMatches, useMatch, useApproveMatch, useRejectMatch } from '../../api/hooks/matches';
import { useAuthStore } from '../../store/auth';
import { Scoreboard } from '../../components/match/Scoreboard';
import { SignOffPanel } from '../../components/admin/SignOffPanel';
import { LoadingState, EmptyState } from '../../components/ui/States';
import { Badge } from '../../components/ui/Badge';
import { formatDateTime } from '../../lib/format';

export function ApprovalsPage() {
  const pending = useMatches({ status: 'RESULTS_SUBMITTED', size: 50 });
  const list = pending.data?.content ?? [];

  return (
    <div className="space-y-6">
      <div>
        <div className="kicker text-gold">Dwie pary oczu</div>
        <h1 className="font-display text-3xl">Kolejka akceptacji</h1>
        <p className="mt-1 text-sm text-text-lo">
          Zatwierdź wyniki podpisanym potwierdzeniem albo odeślij do edycji.
        </p>
      </div>

      {pending.isLoading ? (
        <LoadingState />
      ) : list.length === 0 ? (
        <EmptyState title="Brak wyników do akceptacji" description="Wszystko zatwierdzone. 🎉" />
      ) : (
        <div className="space-y-8">
          {list.map((m) => (
            <ApprovalItem key={m.id} matchId={m.id} />
          ))}
        </div>
      )}
    </div>
  );
}

function ApprovalItem({ matchId }: { matchId: string }) {
  const match = useMatch(matchId);
  const approve = useApproveMatch(matchId);
  const reject = useRejectMatch(matchId);
  const account = useAuthStore((s) => s.account);

  if (match.isLoading || !match.data) return <LoadingState />;

  const m = match.data;
  const submittedAt = m.approval?.submittedAt;

  return (
    <div className="space-y-4 rounded-lg border border-line p-4 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Badge tone="pending">Oczekuje na akceptację</Badge>
        {submittedAt && <span className="num text-xs text-text-lo">wpisano: {formatDateTime(submittedAt)}</span>}
      </div>

      <Scoreboard match={m} />

      <SignOffPanel
        defaultSignature={account?.username ?? ''}
        approving={approve.isPending}
        rejecting={reject.isPending}
        onApprove={(signatureName) =>
          approve.mutate({ signatureConfirmed: true, signatureName })
        }
        onReject={(reason) => reject.mutate({ reason })}
      />
    </div>
  );
}
