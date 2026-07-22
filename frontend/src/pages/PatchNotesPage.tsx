import { RELEASES } from '../content/releases';

export function PatchNotesPage() {
  return (
    <div className="space-y-6">
      <div>
        <div className="kicker text-gold">Historia zmian</div>
        <h1 className="font-display text-3xl">Patch notes</h1>
        <p className="mt-1 text-sm text-text-lo">Wszystkie wydania Driperskiej Ligi.</p>
      </div>
      <div className="space-y-4">
        {RELEASES.map((release) => (
          <article key={release.version} className="glass p-5 sm:p-6">
            <div className="flex flex-wrap items-baseline gap-3">
              <span className="rounded-md bg-gold px-2.5 py-1 font-display text-sm font-bold text-[#1a1205]">{release.version}</span>
              <h2 className="font-display text-xl">{release.title}</h2>
              <time className="ml-auto text-xs text-text-lo">{release.date}</time>
            </div>
            <ul className="mt-4 grid gap-2 text-sm text-text sm:grid-cols-2">
              {release.changes.map((change) => (
                <li key={change} className="flex gap-2"><span className="text-gold">◆</span><span>{change}</span></li>
              ))}
            </ul>
          </article>
        ))}
      </div>
    </div>
  );
}
