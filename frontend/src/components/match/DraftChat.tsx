import { useEffect, useRef, useState } from 'react';
import { useDraftChat } from '../../api/hooks/drawLobby';
import { Button } from '../ui/Button';
import { cn } from '../../lib/cn';
import type { ChatScope, DraftChatMessage, Side } from '../../api/types';

const MAX_LENGTH = 300;

/**
 * Chat for the draft: one channel for the whole lobby, one for your own team.
 *
 * Nothing is stored server-side (see DraftChatService) — this is ten people coordinating bans for a
 * few minutes. Lines arrive on the same stream that carries the lobby state, so there is no second
 * connection to babysit, and a team line is only ever sent to the five accounts on that side.
 *
 * `mySide` is null for an admin watching from the control panel: they see the shared channel, can
 * write into it, and get no team tab because they are not on a team.
 */
export function DraftChat({
  matchId,
  mySide,
  className,
}: {
  matchId: string;
  mySide: Side | null;
  className?: string;
}) {
  const { messages, send } = useDraftChat(matchId);
  const [scope, setScope] = useState<ChatScope>('ALL');
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const listRef = useRef<HTMLDivElement>(null);

  const canUseTeam = mySide != null;
  const activeScope: ChatScope = canUseTeam ? scope : 'ALL';
  const visible = messages.filter((m) => (activeScope === 'ALL' ? m.scope === 'ALL' : m.scope === 'TEAM'));

  // Stick to the newest line, the way every chat is expected to behave.
  useEffect(() => {
    const list = listRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [visible.length, activeScope]);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const value = text.trim();
    if (!value) return;
    setError(null);
    send.mutate({ scope: activeScope, text: value }, {
      onSuccess: () => setText(''),
      onError: (e) => setError((e as Error).message),
    });
  };

  return (
    <section className={cn('glass flex min-h-0 flex-col overflow-hidden', className)}>
      <div className="flex items-center gap-1 border-b border-line px-2 py-1.5">
        <ChatTab active={activeScope === 'ALL'} onClick={() => setScope('ALL')} label="Wszyscy" />
        {canUseTeam && (
          <ChatTab
            active={activeScope === 'TEAM'}
            onClick={() => setScope('TEAM')}
            label="Drużyna"
            color={mySide === 'BLUE' ? 'var(--blue)' : 'var(--red)'}
          />
        )}
        <span className="ml-auto pr-1 text-[10px] text-text-lo">bez zapisu</span>
      </div>

      <div ref={listRef} className="flex-1 space-y-1 overflow-y-auto px-3 py-2 text-sm">
        {visible.length === 0 ? (
          <p className="py-4 text-center text-xs text-text-lo">
            {activeScope === 'TEAM' ? 'Cicho w drużynie.' : 'Cicho na kanale.'}
          </p>
        ) : (
          visible.map((message) => <ChatLine key={message.id} message={message} />)
        )}
      </div>

      <form onSubmit={submit} className="flex items-center gap-2 border-t border-line p-2">
        <input
          value={text}
          maxLength={MAX_LENGTH}
          onChange={(event) => setText(event.target.value)}
          placeholder={activeScope === 'TEAM' ? 'Do drużyny…' : 'Do wszystkich…'}
          className="h-9 min-w-0 flex-1 rounded-md border border-line bg-bg-1 px-2.5 text-sm text-text-hi placeholder:text-text-lo"
        />
        <Button type="submit" size="sm" variant="gold" disabled={send.isPending || !text.trim()}>
          Wyślij
        </Button>
      </form>
      {error && <p className="px-3 pb-2 text-xs text-loss">{error}</p>}
    </section>
  );
}

function ChatTab({ active, onClick, label, color }: {
  active: boolean; onClick: () => void; label: string; color?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'rounded px-2.5 py-1 text-xs font-semibold transition-colors',
        active ? 'text-text-hi' : 'text-text-lo hover:text-text',
      )}
      style={active ? {
        background: `color-mix(in srgb, ${color ?? 'var(--gold)'} 16%, transparent)`,
        color: color ?? 'var(--gold)',
      } : undefined}
    >
      {label}
    </button>
  );
}

function ChatLine({ message }: { message: DraftChatMessage }) {
  const color = message.admin
    ? 'var(--gold)'
    : message.side === 'BLUE' ? 'var(--blue)' : message.side === 'RED' ? 'var(--red)' : 'var(--text)';
  return (
    <p className="break-words leading-snug">
      <span className="num mr-1.5 text-[10px] text-text-lo">
        {new Date(message.at).toLocaleTimeString('pl-PL', { hour: '2-digit', minute: '2-digit' })}
      </span>
      <span className="font-semibold" style={{ color }}>
        {message.admin ? `⚙ ${message.nickname}` : message.nickname}:
      </span>{' '}
      <span className="text-text">{message.text}</span>
    </p>
  );
}
