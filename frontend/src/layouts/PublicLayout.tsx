import { NavLink, Outlet, Link } from 'react-router-dom';
import { cn } from '../lib/cn';
import { useAuthStore } from '../store/auth';

const NAV = [
  { to: '/', label: 'Start', end: true },
  { to: '/ranking', label: 'Ranking', end: false },
  { to: '/players', label: 'Gracze', end: false },
];

export function PublicLayout() {
  const authed = useAuthStore((s) => Boolean(s.accessToken));

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b border-line bg-[color:var(--bg)]/70 backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-content items-center justify-between px-4 sm:px-6">
          <Link to="/" className="group flex items-center gap-2.5">
            <span className="grid h-9 w-9 place-items-center rounded-md bg-gradient-to-b from-gold-soft to-gold text-[#1a1205] shadow-glow-gold">
              <span className="font-display text-lg font-bold">D</span>
            </span>
            <span className="font-display text-lg font-bold tracking-wide text-text-hi">
              DRIPERSKA <span className="text-gradient-gold">LIGA</span>
            </span>
          </Link>

          <nav className="flex items-center gap-1">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  cn(
                    'rounded-md px-3 py-2 text-sm font-medium transition-colors',
                    isActive ? 'text-text-hi' : 'text-text-lo hover:text-text',
                  )
                }
              >
                {({ isActive }) => (
                  <span className="relative">
                    {item.label}
                    {isActive && (
                      <span className="absolute -bottom-2 left-0 h-0.5 w-full rounded-full bg-gold" />
                    )}
                  </span>
                )}
              </NavLink>
            ))}
            <Link
              to={authed ? '/admin' : '/admin/login'}
              className="ml-2 rounded-md border border-line bg-[var(--glass)] px-3 py-2 text-sm font-medium text-text hover:text-text-hi"
            >
              Panel
            </Link>
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-content flex-1 px-4 py-8 sm:px-6 sm:py-10">
        <Outlet />
      </main>

      <footer className="border-t border-line py-8 text-center text-xs text-text-lo">
        <div className="mx-auto max-w-content px-4">
          Driperska Liga · inhouse League of Legends · zbudowane dla społeczności
        </div>
      </footer>
    </div>
  );
}
