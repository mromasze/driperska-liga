import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useLogout } from '../api/hooks/auth';
import { LogoHex } from '../components/brand/Logo';
import { useAuthStore } from '../store/auth';

export function PlayerLayout() {
  const account = useAuthStore((s) => s.account);
  const logout = useLogout();
  const navigate = useNavigate();

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-40 border-b border-line bg-[color:var(--bg)]/80 backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-content items-center justify-between px-4 sm:px-6">
          <Link to="/panel" className="flex items-center gap-2.5">
            <LogoHex size={36} className="shrink-0" />
            <span className="font-display font-bold text-text-hi">STREFA GRACZA</span>
          </Link>
          <nav className="flex items-center gap-2 text-sm">
            <NavLink to="/panel" className="rounded-md px-3 py-2 text-text-hi">Losowanie i profil</NavLink>
            <Link to="/" className="rounded-md px-3 py-2 text-text-lo hover:text-text">Liga</Link>
            <button
              onClick={() => logout.mutate(undefined, { onSettled: () => navigate('/login') })}
              className="rounded-md border border-line px-3 py-2 text-text-lo hover:text-loss"
            >
              Wyloguj {account?.username}
            </button>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-content px-4 py-8 sm:px-6"><Outlet /></main>
    </div>
  );
}