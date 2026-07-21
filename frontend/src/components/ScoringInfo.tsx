/**
 * Human explanation of how points are calculated. Shown in the admin results form and on the
 * public home page so scoring is transparent. Mirrors docs/04-points-and-ranking.md.
 */
export function ScoringInfo({ defaultOpen = false }: { defaultOpen?: boolean }) {
  return (
    <details className="glass grid-tex p-4 sm:p-5" open={defaultOpen}>
      <summary className="cursor-pointer select-none font-display text-lg text-text-hi">
        Jak liczone są punkty? <span className="text-sm text-text-lo">(kliknij, aby rozwinąć)</span>
      </summary>

      <div className="mt-4 space-y-5 text-sm leading-6 text-text">
        <p className="text-text-lo">
          Drużyny są losowane co mecz, więc sam wynik W/L byłby niesprawiedliwy. Dlatego liczą się
          trzy warstwy: <strong className="text-text-hi">Performance Rating</strong> (jak zagrałeś),{' '}
          <strong className="text-text-hi">Punkty Ligowe</strong> (ranking sezonu) i{' '}
          <strong className="text-text-hi">MMR</strong> (siła do losowania).
        </p>

        <div>
          <div className="kicker text-gold">Performance Rating (PR) · 0–100</div>
          <p className="mt-1">
            Ocena Twojej gry w meczu, <strong>niezależna od wyniku</strong> — liczona ze statystyk z
            wagami zależnymi od roli (support nie jest karany za niskie CS, ADC za niski vision itd.)
            i porównywana do średniej tej roli w meczu. Składniki: KDA, udział w zabójstwach (KP),
            CS/min, obrażenia do bohaterów, udział w złocie i vision score.
          </p>
          <p className="mt-1 text-text-lo">Przeciętnie ≈ 50, dobra gra 65–80, dominacja 80+.</p>
        </div>

        <div>
          <div className="kicker text-gold">Punkty Ligowe (LP) · ranking sezonu</div>
          <p className="mt-1">Naliczane przy akceptacji meczu przez admina, nigdy ujemne:</p>
          <ul className="mt-2 space-y-1">
            <li>• Wygrana <strong className="text-win">+10</strong>, przegrana <strong>+2</strong> (nagroda za udział)</li>
            <li>• Jakość gry: <strong>+ round(PR / 10)</strong> → 0–10 punktów</li>
            <li>• MVP meczu (najwyższy PR) <strong>+5</strong></li>
            <li>• ACE przegranych (najlepszy PR po stronie, która przegrała) <strong>+3</strong></li>
            <li>• Pentakill <strong>+5</strong>, Quadrakill <strong>+2</strong>, Flawless (0 śmierci) <strong>+2</strong></li>
          </ul>
          <p className="mt-2 text-text-lo">
            Przykład: dominujące zwycięstwo z MVP i pentą ≈ 30 LP; blada przegrana ≈ 4 LP.
          </p>
        </div>

        <div>
          <div className="kicker text-gold">MMR · balans losowania</div>
          <p className="mt-1">
            Ukryty rating siły (start 1000, system Elo), aktualizowany po każdym zaakceptowanym
            meczu. Używany <strong>tylko</strong> do wyrównanego losowania drużyn (tryb Balanced) —
            nie wpływa na ranking ligowy.
          </p>
        </div>

        <div>
          <div className="kicker">Kolejność w tabeli sezonu</div>
          <p className="mt-1 text-text-lo">
            Suma LP → win rate → średni PR → liczba MVP → mniej rozegranych gier.
          </p>
        </div>
      </div>
    </details>
  );
}
