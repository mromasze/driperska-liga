import { useForm, useFieldArray } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import type { Champion, MatchParticipant, SubmitResultsRequest } from '../../api/types';
import { Card, CardBody, CardHeader, CardTitle } from '../ui/Card';
import { Button } from '../ui/Button';
import { roleLabel } from '../../lib/format';
import { cn } from '../../lib/cn';

/**
 * Results-entry form skeleton (docs/06 §6.7): React Hook Form + Zod with the
 * same client-side rules as the backend — 10 participants (5 BLUE + 5 RED) and
 * non-negative stats — validated before submit.
 */
const participantSchema = z.object({
  playerId: z.string(),
  nickname: z.string(),
  side: z.enum(['BLUE', 'RED']),
  role: z.enum(['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT']),
  championId: z.number({ invalid_type_error: 'Wybierz' }).int().positive('Wybierz championa'),
  kills: z.number({ invalid_type_error: 'liczba' }).int().min(0),
  deaths: z.number({ invalid_type_error: 'liczba' }).int().min(0),
  assists: z.number({ invalid_type_error: 'liczba' }).int().min(0),
  cs: z.number({ invalid_type_error: 'liczba' }).int().min(0),
  gold: z.number({ invalid_type_error: 'liczba' }).int().min(0),
  damageToChampions: z.number({ invalid_type_error: 'liczba' }).int().min(0),
  visionScore: z.number({ invalid_type_error: 'liczba' }).int().min(0),
  largestMultiKill: z.number({ invalid_type_error: 'liczba' }).int().min(0).max(5),
});

const resultsSchema = z
  .object({
    winningSide: z.enum(['BLUE', 'RED']),
    durationSeconds: z.number({ invalid_type_error: 'liczba' }).int().positive('Podaj czas gry'),
    patch: z.string().min(1, 'Podaj patch'),
    participants: z.array(participantSchema).length(10, 'Wymaganych jest 10 uczestników'),
  })
  .refine(
    (data) =>
      data.participants.filter((p) => p.side === 'BLUE').length === 5 &&
      data.participants.filter((p) => p.side === 'RED').length === 5,
    { message: 'Wymagane 5 BLUE + 5 RED', path: ['participants'] },
  );

export type ResultsFormValues = z.infer<typeof resultsSchema>;

export interface ResultsFormProps {
  participants: MatchParticipant[];
  champions: Champion[];
  onSubmit: (payload: SubmitResultsRequest) => void;
  isSubmitting?: boolean;
}

function toDefaults(participants: MatchParticipant[]): ResultsFormValues {
  return {
    winningSide: 'BLUE',
    durationSeconds: 1800,
    patch: '',
    participants: participants.map((p) => ({
      playerId: p.playerId,
      nickname: p.nickname,
      side: p.side,
      role: p.role,
      championId: p.championId || 0,
      kills: p.kills || 0,
      deaths: p.deaths || 0,
      assists: p.assists || 0,
      cs: p.cs || 0,
      gold: p.gold || 0,
      damageToChampions: p.damageToChampions || 0,
      visionScore: p.visionScore || 0,
      largestMultiKill: p.largestMultiKill || 0,
    })),
  };
}

const STAT_FIELDS = [
  { key: 'kills', label: 'K' },
  { key: 'deaths', label: 'D' },
  { key: 'assists', label: 'A' },
  { key: 'cs', label: 'CS' },
  { key: 'gold', label: 'Gold' },
  { key: 'damageToChampions', label: 'Dmg' },
  { key: 'visionScore', label: 'Vis' },
  { key: 'largestMultiKill', label: 'MK' },
] as const;

export function ResultsForm({ participants, champions, onSubmit, isSubmitting }: ResultsFormProps) {
  const {
    control,
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<ResultsFormValues>({
    resolver: zodResolver(resultsSchema),
    defaultValues: toDefaults(participants),
  });

  const { fields } = useFieldArray({ control, name: 'participants' });

  const submit = handleSubmit((values) => {
    const payload: SubmitResultsRequest = {
      winningSide: values.winningSide,
      durationSeconds: values.durationSeconds,
      patch: values.patch,
      participants: values.participants.map((p) => ({
        playerId: p.playerId,
        side: p.side,
        role: p.role,
        championId: p.championId,
        kills: p.kills,
        deaths: p.deaths,
        assists: p.assists,
        cs: p.cs,
        gold: p.gold,
        damageToChampions: p.damageToChampions,
        visionScore: p.visionScore,
        largestMultiKill: p.largestMultiKill,
      })),
    };
    onSubmit(payload);
  });

  const watched = watch('participants');

  return (
    <Card>
      <CardHeader>
        <CardTitle>Wpisz wyniki</CardTitle>
      </CardHeader>
      <CardBody>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <div className="flex flex-wrap items-end gap-4">
            <label className="text-sm">
              <span className="mb-1 block text-xs uppercase tracking-wide text-text-lo">
                Zwycięska strona
              </span>
              <select {...register('winningSide')} className={inputClass}>
                <option value="BLUE">Niebiescy</option>
                <option value="RED">Czerwoni</option>
              </select>
            </label>
            <label className="text-sm">
              <span className="mb-1 block text-xs uppercase tracking-wide text-text-lo">
                Czas gry (s)
              </span>
              <input
                type="number"
                {...register('durationSeconds', { valueAsNumber: true })}
                className={cn(inputClass, 'w-28')}
              />
            </label>
            <label className="text-sm">
              <span className="mb-1 block text-xs uppercase tracking-wide text-text-lo">Patch</span>
              <input {...register('patch')} placeholder="14.13" className={cn(inputClass, 'w-28')} />
            </label>
          </div>

          {errors.participants?.message && (
            <p className="text-sm text-loss">{errors.participants.message}</p>
          )}
          {(errors.durationSeconds || errors.patch) && (
            <p className="text-sm text-loss">
              {errors.durationSeconds?.message ?? errors.patch?.message}
            </p>
          )}

          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-sm">
              <thead>
                <tr className="border-b border-line text-xs uppercase text-text-lo">
                  <th className="px-2 py-1 text-left">Gracz</th>
                  <th className="px-2 py-1 text-left">Champion</th>
                  {STAT_FIELDS.map((f) => (
                    <th key={f.key} className="px-1 py-1 text-right">
                      {f.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {fields.map((field, index) => {
                  const side = watched?.[index]?.side ?? 'BLUE';
                  return (
                    <tr
                      key={field.id}
                      className="border-b border-line/50"
                      style={{
                        backgroundColor: side === 'BLUE' ? 'var(--blue-bg)' : 'var(--red-bg)',
                      }}
                    >
                      <td className="px-2 py-1">
                        <span className="block text-text-hi">
                          {watched?.[index]?.nickname ?? `#${index + 1}`}
                        </span>
                        <span className="text-xs text-text-lo">
                          {roleLabel(watched?.[index]?.role ?? 'MID')}
                        </span>
                      </td>
                      <td className="px-2 py-1">
                        <select
                          {...register(`participants.${index}.championId`, { valueAsNumber: true })}
                          className={cn(inputClass, 'w-36')}
                        >
                          <option value={0}>—</option>
                          {champions.map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.name}
                            </option>
                          ))}
                        </select>
                      </td>
                      {STAT_FIELDS.map((f) => (
                        <td key={f.key} className="px-1 py-1 text-right">
                          <input
                            type="number"
                            {...register(`participants.${index}.${f.key}`, {
                              valueAsNumber: true,
                            })}
                            className={cn(inputClass, 'w-16 text-right')}
                          />
                        </td>
                      ))}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <Button type="submit" variant="gold" disabled={isSubmitting}>
            {isSubmitting ? 'Zapisywanie…' : 'Zapisz wyniki'}
          </Button>
        </form>
      </CardBody>
    </Card>
  );
}

const inputClass =
  'rounded-sm border border-line bg-bg-0 px-2 py-1 text-sm text-text-hi outline-none focus:border-[var(--gold)]';
