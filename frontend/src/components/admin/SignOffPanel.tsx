import { useState } from 'react';
import { Button } from '../ui/Button';

interface SignOffPanelProps {
  defaultSignature?: string;
  approving?: boolean;
  rejecting?: boolean;
  onApprove: (signatureName: string) => void;
  onReject: (reason: string) => void;
}

/**
 * Two-eyes sign-off: the Approve button stays disabled until the reviewer both
 * ticks the confirmation checkbox and provides a signature. Even the admin who
 * entered the results must perform this conscious, signed step.
 */
export function SignOffPanel({
  defaultSignature = '',
  approving,
  rejecting,
  onApprove,
  onReject,
}: SignOffPanelProps) {
  const [confirmed, setConfirmed] = useState(false);
  const [signature, setSignature] = useState(defaultSignature);
  const [reason, setReason] = useState('');
  const [showReject, setShowReject] = useState(false);

  const canApprove = confirmed && signature.trim().length > 0;

  return (
    <div className="panel space-y-4 p-5">
      <h3 className="font-display text-lg">Decyzja</h3>

      <label className="flex cursor-pointer items-start gap-3 rounded-md border border-line bg-bg-1 p-3">
        <input
          type="checkbox"
          checked={confirmed}
          onChange={(e) => setConfirmed(e.target.checked)}
          className="mt-0.5 h-5 w-5 accent-[var(--gold)]"
        />
        <span className="text-sm text-text">
          <span className="font-medium text-text-hi">Potwierdzam poprawność wyników.</span> Wprowadzone
          statystyki są zgodne z ekranem końcowym gry.
        </span>
      </label>

      <label className="block">
        <span className="kicker">Podpis</span>
        <input
          value={signature}
          onChange={(e) => setSignature(e.target.value)}
          placeholder="Imię lub nick zatwierdzającego"
          className="mt-1 h-10 w-full rounded-md border border-line bg-bg-1 px-3 text-text-hi placeholder:text-text-lo"
        />
      </label>

      <div className="flex flex-wrap gap-3">
        <Button
          variant="gold"
          disabled={!canApprove || approving}
          onClick={() => onApprove(signature.trim())}
        >
          {approving ? 'Zatwierdzanie…' : 'Zatwierdź wyniki'}
        </Button>
        <Button variant="ghost" onClick={() => setShowReject((v) => !v)}>
          Odeślij do edycji
        </Button>
      </div>

      {showReject && (
        <div className="space-y-2 border-t border-line pt-3">
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Powód odesłania (np. błędne CS gracza X)…"
            rows={2}
            className="w-full rounded-md border border-line bg-bg-1 p-3 text-sm text-text-hi placeholder:text-text-lo"
          />
          <Button
            variant="danger"
            disabled={reason.trim().length === 0 || rejecting}
            onClick={() => onReject(reason.trim())}
          >
            {rejecting ? 'Odsyłanie…' : 'Potwierdź odesłanie'}
          </Button>
        </div>
      )}
    </div>
  );
}
