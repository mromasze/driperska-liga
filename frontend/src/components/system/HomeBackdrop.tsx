/**
 * Decorative backdrop for the landing page.
 *
 * The page used to be a flat black field: the hero carries its own clip reel, and everything below it
 * sat on the bare `body` colour, so scrolling felt like falling into an empty void. This adds depth
 * without adding weight — four fixed layers of pure CSS gradients (colour blooms, a hex lattice that
 * echoes the league's mark, two faint light beams and a horizon glow). No images, no canvas, no
 * external requests, so the strict CSP stays untouched and there is nothing to download.
 *
 * It is `position: fixed` with a negative z-index: it paints above the body background and below all
 * content, stays put while the page scrolls (so it reads as a room rather than wallpaper), and the
 * glass cards blur it through `backdrop-filter` for free. `aria-hidden` + `pointer-events: none` keep
 * it out of the accessibility tree and out of the way of clicks. Motion is disabled under
 * `prefers-reduced-motion` — see index.css.
 */
export function HomeBackdrop() {
  return (
    <div className="home-backdrop" aria-hidden="true">
      <span className="home-lattice" />
      <span className="home-beams" />
      <span className="home-horizon" />
    </div>
  );
}
