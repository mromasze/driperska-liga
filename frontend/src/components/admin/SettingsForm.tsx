import { useMemo, useState } from 'react';
import {
  useResetRuntimeConfig, useUpdateRuntimeConfig, type SettingView,
} from '../../api/hooks/config';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { cn } from '../../lib/cn';

/** Pending edits: key → new value, or `null` meaning "drop the override, go back to .env". */
type Draft = Record<string, string | null>;

/**
 * Editor for one group of runtime settings.
 *
 * The rule that shapes everything here: an untouched field is never sent. That is what lets a secret
 * be edited alongside its neighbours — the browser only ever holds a masked preview of it, so
 * "leave it alone" has to mean "omit the key", not "send back what I was shown".
 */
export function SettingsForm({ settings, extraOptions, onSaved, compact = false }: {
  settings: SettingView[];
  /** Per-key option lists that beat whatever the backend suggested (used for the AI model picker). */
  extraOptions?: Record<string, string[]>;
  onSaved?: () => void;
  compact?: boolean;
}) {
  const update = useUpdateRuntimeConfig();
  const reset = useResetRuntimeConfig();
  const [draft, setDraft] = useState<Draft>({});
  const [message, setMessage] = useState<string | null>(null);

  const dirtyKeys = useMemo(() => Object.keys(draft), [draft]);
  const editable = settings.filter((s) => s.editable);
  const readOnly = settings.filter((s) => !s.editable);

  const set = (key: string, value: string | null) =>
    setDraft((current) => ({ ...current, [key]: value }));

  const discard = (key: string) =>
    setDraft(({ [key]: _dropped, ...rest }) => rest);

  const save = () => {
    if (dirtyKeys.length === 0) return;
    setMessage(null);
    update.mutate(draft, {
      onSuccess: () => {
        setDraft({});
        setMessage('✓ Zapisano — zmiany działają od razu.');
        onSaved?.();
      },
      onError: (error) => setMessage('⚠ ' + (error as Error).message),
    });
  };

  const restoreDefault = (key: string) => {
    setMessage(null);
    discard(key);
    reset.mutate([key], {
      onSuccess: () => setMessage('✓ Przywrócono wartość z .env.'),
      onError: (error) => setMessage('⚠ ' + (error as Error).message),
    });
  };

  const pending = update.isPending || reset.isPending;

  return (
    <div className="space-y-4">
      <div className={cn('grid gap-4', !compact && 'sm:grid-cols-2')}>
        {editable.map((setting) => (
          <Field
            key={setting.key}
            setting={setting}
            options={extraOptions?.[setting.key] ?? setting.options}
            draft={draft}
            onChange={set}
            onDiscard={discard}
            onRestoreDefault={restoreDefault}
            disabled={pending}
          />
        ))}
      </div>

      {readOnly.length > 0 && (
        <div className="rounded-lg border border-line bg-[color:var(--bg)]/40 p-4">
          <div className="kicker text-text-lo">Tylko do odczytu</div>
          <dl className="mt-2 grid gap-x-6 gap-y-2 sm:grid-cols-2">
            {readOnly.map((setting) => (
              <div key={setting.key} className="flex items-baseline justify-between gap-3 text-sm">
                <dt className="text-text-lo" title={setting.description}>{setting.label}</dt>
                <dd className="num truncate text-text-hi" title={setting.value ?? ''}>
                  {setting.value ?? <span className="text-text-lo">nie ustawiono</span>}
                </dd>
              </div>
            ))}
          </dl>
          <p className="mt-3 text-xs text-text-lo">
            Te wartości są wczytywane raz, przy starcie backendu. Zmień je w <code>.env</code> i zrestartuj kontener.
          </p>
        </div>
      )}

      {editable.length > 0 && (
        <div className="flex flex-wrap items-center gap-3 border-t border-line pt-4">
          <Button variant="gold" onClick={save} disabled={pending || dirtyKeys.length === 0}>
            {update.isPending ? 'Zapisywanie…' : `Zapisz${dirtyKeys.length > 0 ? ` (${dirtyKeys.length})` : ''}`}
          </Button>
          {dirtyKeys.length > 0 && (
            <Button variant="ghost" onClick={() => { setDraft({}); setMessage(null); }} disabled={pending}>
              Odrzuć zmiany
            </Button>
          )}
          {message && <span className="text-sm text-text">{message}</span>}
        </div>
      )}
    </div>
  );
}

function Field({ setting, options, draft, onChange, onDiscard, onRestoreDefault, disabled }: {
  setting: SettingView;
  options: string[];
  draft: Draft;
  onChange: (key: string, value: string | null) => void;
  onDiscard: (key: string) => void;
  onRestoreDefault: (key: string) => void;
  disabled: boolean;
}) {
  const touched = setting.key in draft;
  const drafted = draft[setting.key];
  // A secret is the one field whose current value we cannot put in the input, so it stays empty
  // until the admin types a replacement.
  const current = setting.secret ? (touched ? (drafted ?? '') : '') : (touched ? (drafted ?? '') : setting.value ?? '');

  return (
    <label className={cn('flex flex-col rounded-lg border p-3 transition-colors',
      touched ? 'border-gold bg-[color:var(--gold)]/5' : 'border-line bg-[color:var(--bg)]/40')}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-medium text-text-hi">{setting.label}</span>
        <div className="flex items-center gap-1.5">
          {setting.overridden && <Badge tone="info">z panelu</Badge>}
          {touched && <Badge tone="gold">zmienione</Badge>}
        </div>
      </div>
      <code className="mt-0.5 text-[11px] text-text-lo">{setting.envName}</code>

      <div className="mt-2">
        {setting.type === 'BOOLEAN' ? (
          <Toggle
            value={(touched ? drafted : setting.value) === 'true'}
            disabled={disabled}
            onChange={(next) => onChange(setting.key, String(next))}
          />
        ) : setting.type === 'CHOICE' && options.length > 0 ? (
          <select
            className="form-control"
            value={options.includes(current) ? current : '__custom__'}
            disabled={disabled}
            onChange={(e) => onChange(setting.key, e.target.value === '__custom__' ? '' : e.target.value)}
          >
            {options.map((option) => <option key={option} value={option}>{option}</option>)}
            <option value="__custom__">Własna wartość…</option>
          </select>
        ) : null}

        {(setting.type === 'STRING' || setting.type === 'SECRET' || setting.type === 'INTEGER'
          || (setting.type === 'CHOICE' && (options.length === 0 || !options.includes(current)))) && (
          <input
            className={cn('form-control', setting.type === 'CHOICE' && options.length > 0 && 'mt-2')}
            type={setting.type === 'INTEGER' ? 'number' : setting.secret ? 'password' : 'text'}
            autoComplete={setting.secret ? 'new-password' : 'off'}
            value={current}
            disabled={disabled}
            placeholder={setting.secret
              ? (setting.set ? `ustawiono (${setting.value}) — wpisz, aby zmienić` : 'nie ustawiono')
              : 'nie ustawiono'}
            onChange={(e) => onChange(setting.key, e.target.value)}
          />
        )}
      </div>

      <p className="mt-1.5 text-xs text-text-lo">{setting.description}</p>
      {setting.restartNote && (
        <p className="mt-1 text-xs text-pending">⚠ {setting.restartNote}</p>
      )}

      <div className="mt-2 flex flex-wrap gap-3 text-[11px]">
        {touched && (
          <button type="button" className="text-text-lo underline-offset-2 hover:underline"
            onClick={() => onDiscard(setting.key)}>
            cofnij edycję
          </button>
        )}
        {setting.secret && setting.set && (
          <button type="button" className="text-loss underline-offset-2 hover:underline"
            onClick={() => onChange(setting.key, '')}>
            wyczyść wartość
          </button>
        )}
        {setting.overridden && (
          <button type="button" className="text-cyan underline-offset-2 hover:underline"
            onClick={() => onRestoreDefault(setting.key)}>
            przywróć z .env{setting.defaultValue ? ` (${setting.defaultValue})` : ''}
          </button>
        )}
      </div>
    </label>
  );
}

function Toggle({ value, disabled, onChange }: {
  value: boolean; disabled: boolean; onChange: (next: boolean) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onChange(!value)}
      disabled={disabled}
      aria-pressed={value}
      className={cn('relative h-7 w-12 shrink-0 rounded-full transition-colors disabled:opacity-50',
        value ? 'bg-win' : 'bg-bg-2')}
    >
      <span className={cn('absolute top-1 h-5 w-5 rounded-full bg-white transition-transform',
        value ? 'translate-x-6' : 'translate-x-1')} />
    </button>
  );
}
