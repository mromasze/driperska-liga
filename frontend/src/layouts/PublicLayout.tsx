import { NavLink, Outlet, Link } from 'react-router-dom';
import { cn } from '../lib/cn';

const NAV = [
  { to: '/', label: 'Home', end: true },
  { to: '/ranking', label: 'Ranking', end: false },
  { to: '/players', label: 'Gracze', end: false },
];

export function PublicLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-20 border-b border-line bg-bg-0/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Link to="/" className="flex items-center gap-2">
            <span
              aria-hidden="true"
              className="inline-block h-5 w-5 rotate-45 rounded-[3px] border-2 border-[var(--gold)]"
            />
            <span className="font-display text-lg text-text-hi">Driperska Liga</span>
          </Link>

          <nav className="flex items-center gap-1">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  cn(
                    'rounded-sm px-3 py-1.5 text-sm font-medium transition',
                    isActive ? 'bg-bg-2 text-text-hi' : 'text-text hover:text-text-hi',
                  )
                }
              >
                {item.label}
              </NavLink>
            ))}
            <NavLink
              to="/admin"
              className="ml-2 rounded-sm px-3 py-1.5 text-sm text-text-lo transition hover:text-gold"
            >
              Panel
            </NavLink>
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>

      <footer className="border-t border-line py-6 text-center text-xs text-text-lo">
        Driperska Liga — inhouse League of Legends. Sezon {new Date().getFullYear()}.
      </footer>
    </div>
  );
}
