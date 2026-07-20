import { useState } from 'react';
import { Card, CardBody, CardHeader, CardTitle } from '../ui/Card';
import { Button } from '../ui/Button';

export interface SignOffPanelProps {
  /** Prefilled signature (logged-in admin's nick). */
  defaultSignatureName?: string;
  onApprove: (signatureName: string) => void;
  onReject: (reason: string) => void;
  isApproving?: boolean;
  isRejecting?: boolean;
}

/**
 * Approval decision section (docs/03 §3.5, docs/06 §6.6). The "Zatwierdź"
 * button stays disabled until the confirmation checkbox is ticked — a
 * deliberate friction + audit signature.
 */
export function SignOffPanel({
  defaultSignatureName = '',
  onApprove,
  onReject,
  isApproving,
  isRejecting,
}: SignOffPanelProps) {
  const [confirmed, setConfirmed] = useState(false);
  const [signature, setSignature] = useState(defaultSignatureName);
  const [reason, setReason] = useState('');

  const canApprove = confirmed && signature.trim().length > 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Decyzja</CardTitle>
      </CardHeader>
      <CardBody className="space-y-4">
        <label className="flex cursor-pointer items-start gap-3">
          <input
            type="checkbox"
            checked={confirmed}
            onChange={(e) => setConfirmed(e.target.checked)}
            className="mt-0.5 h-5 w-5 shrink-0 accent-[var(--gold)]"
          />
          <span className="text-sm text-text">
            Potwierdzam poprawność wyników tego meczu.
          </span>
        </label>

        <div>
          <label htmlFor="signature" className="mb-1 block text-xs uppercase tracking-wide text-text-lo">
            Podpis
          </label>
          <input
            id="signature"
            type="text"
            value={signature}
            onChange={(e) => setSignature(e.target.value)}
            placeholder="Imię / nick zatwierdzającego"
            className="w-full rounded-sm border border-line bg-bg-0 px-3 py-2 text-sm text-text-hi outline-none focus:border-[var(--gold)]"
          />
        </div>

        <div className="flex flex-wrap gap-2">
          <Button
            variant="gold"
            disabled={!canApprove || isApproving}
            onClick={() => onApprove(signature.trim())}
          >
            Zatwierdź
          </Button>
        </div>

        <hr className="border-line" />

        <div>
          <label htmlFor="reason" className="mb-1 block text-xs uppercase tracking-wide text-text-lo">
            Odeślij do edycji — powód
          </label>
          <textarea
            id="reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={2}
            placeholder="Np. „Złe CS u gracza X, popraw"
            className="w-full rounded-sm border border-line bg-bg-0 px-3 py-2 text-sm text-text-hi outline-none focus:border-[var(--red)]"
          />
          <Button
            variant="danger"
            className="mt-2"
            disabled={reason.trim().length === 0 || isRejecting}
            onClick={() => onReject(reason.trim())}
          >
            Odeślij do edycji
          </Button>
        </div>
      </CardBody>
    </Card>
  );
}
