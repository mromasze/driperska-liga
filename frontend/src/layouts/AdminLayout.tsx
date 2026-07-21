import { NavLink, Outlet, Link, useNavigate } from 'react-router-dom';
import { cn } from '../lib/cn';
import { useAuthStore } from '../store/auth';
import { useLogout } from '../api/hooks/auth';
import { useMatches } from '../api/hooks/matches';

const NAV = [
  { to: '/admin', label: 'Pulpit', end: true },
  { to: '/admin/matches/new', label: 'Nowy mecz', end: false },
  { to: '/admin/approvals', label: 'Akceptacje', end: false },
  { to: '/admin/players', label: 'Gracze', end: false },
];

export function AdminLayout() {
  const account = useAuthStore((s) => s.account);
  const logout = useLogout();
  const navigate = useNavigate();
  const pending = useMatches({ status: 'RESULTS_SUBMITTED', size: 1 });
  const pendingCount = pending.data?.totalElements ?? 0;

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-60 shrink-0 flex-col border-r border-line bg-[color:var(--bg-1)]/60 p-4 md:flex">
        <Link to="/" className="mb-8 flex items-center gap-2.5 px-2">
          <span className="grid h-9 w-9 place-items-center rounded-md bg-gradient-to-b from-gold-soft to-gold text-[#1a1205]">
            <span className="font-display text-lg font-bold">D</span>
          </span>
          <span className="font-display font-bold text-text-hi">Panel</span>
        </Link>
        <nav className="flex flex-1 flex-col gap-1">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex items-center justify-between rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  isActive ? 'bg-[var(--glass-strong)] text-text-hi' : 'text-text-lo hover:text-text',
                )
              }
            >
              <span>{item.label}</span>
              {item.to === '/admin/approvals' && pendingCount > 0 && (
                <span className="num rounded-full bg-pending px-2 text-xs font-bold text-[#1a1205]">
                  {pendingCount}
                </span>
              )}
            </NavLink>
          ))}
        </nav>
        <div className="mt-4 border-t border-line pt-4">
          <div className="px-2 text-sm text-text-hi">{account?.username}</div>
          <div className="px-2 text-xs text-text-lo">{account?.role}</div>
          <button
            onClick={() => logout.mutate(undefined, { onSettled: () => navigate('/admin/login') })}
            className="mt-2 w-full rounded-md px-2 py-1.5 text-left text-sm text-text-lo hover:text-loss"
          >
            Wyloguj
          </button>
        </div>
      </aside>

      <div className="flex-1">
        {/* Mobile top bar */}
        <div className="flex items-center justify-between border-b border-line p-3 md:hidden">
          <span className="font-display font-bold">Panel</span>
          <div className="flex gap-2 overflow-x-auto">
            {NAV.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className="whitespace-nowrap px-2 py-1 text-sm text-text-lo [&.active]:text-text-hi">
                {item.label}
              </NavLink>
            ))}
          </div>
        </div>
        <main className="mx-auto max-w-5xl p-4 sm:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
