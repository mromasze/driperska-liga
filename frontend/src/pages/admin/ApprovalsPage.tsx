import { useState } from 'react';
import { useMatches, useMatch, useApproveMatch, useRejectMatch, useShareMatchToDiscord } from '../../api/hooks/matches';
import { useAuthStore } from '../../store/auth';
import { Scoreboard } from '../../components/match/Scoreboard';
import { SignOffPanel } from '../../components/admin/SignOffPanel';
import { Button } from '../../components/ui/Button';
import { CardSkeleton, EmptyState, ErrorState, SectionSkeleton } from '../../components/ui/States';
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

      {pending.isError ? (
        <ErrorState error={pending.error} />
      ) : pending.isLoading ? (
        <SectionSkeleton rows={2} />
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
  const share = useShareMatchToDiscord(matchId);
  const account = useAuthStore((s) => s.account);
  const [shareMsg, setShareMsg] = useState<string | null>(null);

  if (match.isError) return <ErrorState error={match.error} />;
  if (match.isLoading || !match.data) return <CardSkeleton lines={6} />;

  const m = match.data;
  // Once approved/rejected the match leaves the queue — hide it immediately even if the list
  // query hasn't refetched yet, so approved matches don't linger in Approvals.
  if (m.status !== 'RESULTS_SUBMITTED') return null;
  const submittedAt = m.approval?.submittedAt;

  const doShare = () => {
    if (!window.confirm('Wygenerować obrazek wyników i wysłać go na kanał Discord?')) return;
    setShareMsg(null);
    share.mutate(undefined, {
      onSuccess: (r) => setShareMsg(r.sent ? '✓ Wysłano na Discord.' : '⚠ ' + r.message),
      onError: (e) => setShareMsg('⚠ ' + (e as Error).message),
    });
  };

  return (
    <div className="space-y-4 rounded-lg border border-line p-4 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone="pending">Oczekuje na akceptację</Badge>
          {/* Who typed this in matters now that moderators submit matches too. */}
          {m.approval?.submittedByName && (
            <span className="text-xs text-text-lo">
              wprowadził: <span className="text-text-hi">{m.approval.submittedByName}</span>
            </span>
          )}
        </div>
        {submittedAt && <span className="num text-xs text-text-lo">wpisano: {formatDateTime(submittedAt)}</span>}
      </div>

      <Scoreboard match={m} />

      <div className="flex flex-wrap items-center gap-3">
        <Button variant="ghost" size="sm" disabled={share.isPending} onClick={doShare}>
          {share.isPending ? 'Wysyłanie…' : '📤 Udostępnij wynik na Discord'}
        </Button>
        {shareMsg && <span className="text-sm text-text-lo">{shareMsg}</span>}
      </div>

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
