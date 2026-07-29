import { useState } from 'react';
import { NavLink, Outlet, Link, useLocation } from 'react-router-dom';
import { cn } from '../lib/cn';
import { LogoHex } from '../components/brand/Logo';
import { useAuthStore } from '../store/auth';
import { APP_VERSION } from '../version';

const NAV = [
  { to: '/', label: 'Start', end: true },
  { to: '/ranking', label: 'Ranking', end: false },
  { to: '/matches', label: 'Mecze', end: false },
  { to: '/players', label: 'Gracze', end: false },
  { to: '/patch-notes', label: 'Patch notes', end: false },
];

export function PublicLayout() {
  const account = useAuthStore((s) => s.account);
  const panelUrl = account ? (account.role === 'PLAYER' ? '/panel' : '/admin') : '/login';
  const panelLabel = account?.role === 'PLAYER' ? 'Strefa gracza' : account ? 'Panel' : 'Zaloguj się';
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b border-line bg-[color:var(--bg)]/70 backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-content items-center justify-between px-4 sm:px-6">
          <Link to="/" className="group flex min-w-0 items-center gap-2.5">
            {/* The hex carries its own cyan bloom, so no shadow-glow-gold here. */}
            <LogoHex size={38} className="shrink-0" />
            <span className="truncate font-display text-base font-bold tracking-wide text-text-hi sm:text-lg">
              DRIPERSKA <span className="text-gradient-gold">LIGA</span>
            </span>
          </Link>

          {/* Desktop nav */}
          <nav className="hidden items-center gap-1 md:flex">
            {NAV.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end}
                className={({ isActive }) => cn('rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  isActive ? 'text-text-hi' : 'text-text-lo hover:text-text')}>
                {({ isActive }) => <span className="relative">{item.label}
                  {isActive && <span className="absolute -bottom-2 left-0 h-0.5 w-full rounded-full bg-gold" />}
                </span>}
              </NavLink>
            ))}
            <Link to={panelUrl} className="ml-2 rounded-md border border-line bg-[var(--glass)] px-3 py-2 text-sm font-medium text-text hover:text-text-hi">
              {panelLabel}
            </Link>
          </nav>

          {/* Mobile hamburger */}
          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-expanded={menuOpen}
            aria-label="Menu"
            className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-line text-text-hi md:hidden"
          >
            {menuOpen ? '✕' : '☰'}
          </button>
        </div>

        {menuOpen && (
          <nav className="border-t border-line bg-[color:var(--bg)]/95 px-4 py-2 backdrop-blur-xl md:hidden">
            {NAV.map((item) => {
              const active = item.end ? location.pathname === item.to : location.pathname.startsWith(item.to);
              return (
                <NavLink key={item.to} to={item.to} end={item.end} onClick={() => setMenuOpen(false)}
                  className={cn('block rounded-md px-3 py-2.5 text-sm font-medium',
                    active ? 'bg-[var(--glass-strong)] text-text-hi' : 'text-text-lo')}>
                  {item.label}
                </NavLink>
              );
            })}
            <Link to={panelUrl} onClick={() => setMenuOpen(false)}
              className="mt-1 block rounded-md border border-line bg-[var(--glass)] px-3 py-2.5 text-sm font-medium text-text">
              {panelLabel}
            </Link>
          </nav>
        )}
      </header>
      <main className="mx-auto w-full max-w-content flex-1 px-4 py-8 sm:px-6 sm:py-10"><Outlet /></main>
      <footer className="border-t border-line py-8 text-center text-xs text-text-lo">
        <div className="mx-auto max-w-content space-y-1 px-4">
          <LogoHex size={30} className="mx-auto mb-3 opacity-70" />
          <div>Driperska Liga {APP_VERSION} · inhouse League of Legends</div>
          <div>
            Stworzone przez{' '}
            <a href="https://mromasze.github.io/" target="_blank" rel="noopener noreferrer"
              className="text-gold hover:underline">mromasze</a>
          </div>
        </div>
      </footer>
    </div>
  );
}
