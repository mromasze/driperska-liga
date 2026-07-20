import { useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useCreatePlayer, usePlayers, useUploadAvatar } from '../../api/hooks/players';
import type { Role } from '../../api/types';
import { Card, CardBody, CardHeader, CardTitle } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Avatar } from '../../components/ui/Avatar';
import { Badge } from '../../components/ui/Badge';
import { LoadingState, ErrorState, EmptyState } from '../../components/ui/States';
import { roleLabel } from '../../lib/format';

const ROLES: Role[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];

const schema = z.object({
  nickname: z.string().min(2, 'Min. 2 znaki'),
  realName: z.string().optional(),
  riotId: z.string().optional(),
  mainRole: z.enum(['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT']),
  secondaryRole: z.enum(['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT']).optional(),
});

type PlayerForm = z.infer<typeof schema>;

export function AdminPlayersPage() {
  const players = usePlayers();
  const createPlayer = useCreatePlayer();
  const uploadAvatar = useUploadAvatar();
  const [uploadingFor, setUploadingFor] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<PlayerForm>({
    resolver: zodResolver(schema),
    defaultValues: { mainRole: 'MID' },
  });

  const onSubmit = handleSubmit((values) => {
    createPlayer.mutate(
      {
        nickname: values.nickname,
        realName: values.realName || null,
        riotId: values.riotId || null,
        mainRole: values.mainRole,
        secondaryRole: values.secondaryRole ?? null,
      },
      { onSuccess: () => reset({ mainRole: 'MID' }) },
    );
  });

  function triggerUpload(playerId: string) {
    setUploadingFor(playerId);
    fileInputRef.current?.click();
  }

  function onFileSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file && uploadingFor) {
      uploadAvatar.mutate({ id: uploadingFor, file });
    }
    e.target.value = '';
    setUploadingFor(null);
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl">Gracze</h1>

      <input
        ref={fileInputRef}
        type="file"
        accept="image/png,image/jpeg,image/webp"
        className="hidden"
        onChange={onFileSelected}
      />

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Add player */}
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle>Dodaj gracza</CardTitle>
          </CardHeader>
          <CardBody>
            <form onSubmit={onSubmit} className="space-y-3" noValidate>
              <Field label="Nick" error={errors.nickname?.message}>
                <input {...register('nickname')} className={inputClass} />
              </Field>
              <Field label="Imię (opcjonalnie)">
                <input {...register('realName')} className={inputClass} />
              </Field>
              <Field label="Riot ID (opcjonalnie)" hint="gameName#TAG">
                <input {...register('riotId')} className={inputClass} placeholder="Faker#KR1" />
              </Field>
              <Field label="Główna rola">
                <select {...register('mainRole')} className={inputClass}>
                  {ROLES.map((r) => (
                    <option key={r} value={r}>
                      {roleLabel(r)}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Rola poboczna (opcjonalnie)">
                <select {...register('secondaryRole')} className={inputClass}>
                  <option value="">—</option>
                  {ROLES.map((r) => (
                    <option key={r} value={r}>
                      {roleLabel(r)}
                    </option>
                  ))}
                </select>
              </Field>

              {createPlayer.isError && <ErrorState error={createPlayer.error} title="Nie dodano" />}

              <Button type="submit" variant="gold" className="w-full" disabled={createPlayer.isPending}>
                {createPlayer.isPending ? 'Dodawanie…' : 'Dodaj gracza'}
              </Button>
            </form>
          </CardBody>
        </Card>

        {/* List */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Lista graczy</CardTitle>
          </CardHeader>
          <CardBody className="p-0">
            {players.isLoading ? (
              <LoadingState />
            ) : players.isError ? (
              <div className="p-4">
                <ErrorState error={players.error} />
              </div>
            ) : (players.data ?? []).length === 0 ? (
              <div className="p-4">
                <EmptyState title="Brak graczy" />
              </div>
            ) : (
              <ul className="divide-y divide-line">
                {(players.data ?? []).map((p) => (
                  <li key={p.id} className="flex items-center gap-3 px-5 py-3">
                    <Avatar src={p.avatarUrl} name={p.nickname} size={40} />
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm font-medium text-text-hi">{p.nickname}</div>
                      <div className="flex items-center gap-2 text-xs text-text-lo">
                        <Badge tone="info">{roleLabel(p.mainRole)}</Badge>
                        {!p.active && <Badge tone="neutral">nieaktywny</Badge>}
                      </div>
                    </div>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => triggerUpload(p.id)}
                      disabled={uploadAvatar.isPending}
                    >
                      Avatar
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>
      </div>
    </div>
  );
}

const inputClass =
  'w-full rounded-sm border border-line bg-bg-0 px-3 py-2 text-sm text-text-hi outline-none focus:border-[var(--gold)]';

function Field({
  label,
  hint,
  error,
  children,
}: {
  label: string;
  hint?: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label className="mb-1 block text-xs uppercase tracking-wide text-text-lo">
        {label}
        {hint && <span className="ml-1 normal-case text-text-lo/70">· {hint}</span>}
      </label>
      {children}
      {error && <p className="mt-1 text-xs text-loss">{error}</p>}
    </div>
  );
}
