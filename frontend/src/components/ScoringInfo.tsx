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
            Ocena gry bez bezpośredniego bonusu za zwycięstwo, liczona ze statystyk normalizowanych
            względem roli. PR v2 porównuje wynik z wcześniejszymi występami na tej samej pozycji.
            Historyczny percentyl jest stopniowo łączony z bezpośrednim porównaniem rywali na tej
            samej pozycji, ale historia ma maksymalnie 50% wagi — bieżący mecz zawsze stanowi co
            najmniej połowę oceny. Składniki: KDA, udział w zabójstwach (KP), CS/min,
            obrażenia/min, efektywność obrażenia/złoto i vision/min.
            Normalizacja zależy od pozycji, ale końcowe wagi są wspólne dla wszystkich:
            <strong> KDA 35%, KP 20%, CS 10%, obrażenia 25%, efektywność 5%, wizja 5%.</strong>
            Dzięki temu PR i MVP można uczciwie porównywać między rolami.
          </p>
          <p className="mt-1 text-text-lo">Przeciętnie ≈ 50, dobra gra 65–80, dominacja 80+.</p>
        </div>

        <div>
          <div className="kicker text-gold">Punkty Ligowe (LP) · ranking sezonu</div>
          <p className="mt-1">Naliczane przy akceptacji meczu przez admina:</p>
          <ul className="mt-2 space-y-1">
            <li>• Wygrana <strong className="text-win">+10</strong>, przegrana <strong>+4</strong></li>
            <li>• Próg PR: <strong>&lt;35: −2</strong>, 35–44: −1, 45–54: 0, 55–64: +1, 65–74: +2, 75+: +3</li>
            <li>• MVP meczu (najwyższy PR) <strong>+3</strong></li>
            <li>• ACE przegranych: najwyższy PR po przegranej stronie, wymagane PR ≥60, <strong>+2</strong></li>
            <li>• Jeżeli przegrany jest jednocześnie MVP i ACE, widzi oba tytuły, ale dostaje tylko bonus MVP</li>
            <li>• Najlepsze KDA w meczu <strong>+1</strong>; przy remisie bonus dostaje każdy z najlepszym wynikiem</li>
            <li>• Perfect KDA (0 śmierci i co najmniej kill lub asysta) <strong>+1</strong></li>
            <li>• Bonusy KDA łączą się ze sobą oraz z MVP/ACE; penta i quadra są osiągnięciami bez LP</li>
          </ul>
          <p className="mt-2 text-text-lo">
            Przykład: przeciętna wygrana = 10 LP. Dobry ACE z PR 70 i najlepszym KDA = 9 LP,
            a perfect KDA będące zarazem najlepszym KDA daje dodatkowo łącznie +2 LP.
          </p>
        </div>

        <div>
          <div className="kicker text-gold">MMR · balans losowania</div>
          <p className="mt-1">
            Ukryty rating siły (start 1000, czyste Elo bez mnożnika PR), aktualizowany po każdym zaakceptowanym
            meczu. Używany <strong>tylko</strong> do wyrównanego losowania drużyn (tryb Balanced) —
            nie wpływa na ranking ligowy.
          </p>
        </div>

        <div>
          <div className="kicker">Kolejność w tabeli sezonu</div>
          <p className="mt-1 text-text-lo">
            Główny wynik to skorygowana średnia punktów na mecz
            <strong> (suma LP + 5 × średnia ligi) ÷ (mecze + 5)</strong> oraz bonus aktywności:
            <strong> +0,10 za każdy mecz</strong>, maksymalnie +2,00 za 20 meczów.
            Do pełnej klasyfikacji potrzeba 5 meczów; wcześniej wynik jest oznaczony jako prowizoryczny.
            Dzięki temu dobra średnia nadal jest najważniejsza, ale dalsza gra realnie poprawia pozycję.
          </p>
        </div>
      </div>
    </details>
  );
}
