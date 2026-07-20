import { NavLink, Outlet, useNavigate, Link } from 'react-router-dom';
import { useAuthStore } from '../store/auth';
import { useLogout } from '../api/hooks/auth';
import { Button } from '../components/ui/Button';
import { cn } from '../lib/cn';

const NAV = [
  { to: '/admin', label: 'Dashboard', end: true },
  { to: '/admin/approvals', label: 'Akceptacje', end: false },
  { to: '/admin/matches/new', label: 'Nowy mecz', end: false },
  { to: '/admin/players', label: 'Gracze', end: false },
];

export function AdminLayout() {
  const account = useAuthStore((s) => s.account);
  const logout = useLogout();
  const navigate = useNavigate();

  function handleLogout() {
    logout.mutate(undefined, {
      onSettled: () => navigate('/admin/login', { replace: true }),
    });
  }

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-60 shrink-0 flex-col border-r border-line bg-bg-1 md:flex">
        <div className="border-b border-line px-5 py-4">
          <Link to="/" className="font-display text-lg text-text-hi">
            Driperska Liga
          </Link>
          <div className="text-xs text-text-lo">Panel administracyjny</div>
        </div>
        <nav className="flex-1 space-y-1 p-3">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'block rounded-sm px-3 py-2 text-sm font-medium transition',
                  isActive ? 'bg-bg-2 text-gold' : 'text-text hover:bg-bg-2 hover:text-text-hi',
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-line p-3">
          <div className="mb-2 px-2 text-xs text-text-lo">
            {account ? (
              <>
                <div className="text-text-hi">{account.username}</div>
                <div>{account.role}</div>
              </>
            ) : (
              'Niezalogowany'
            )}
          </div>
          <Button variant="ghost" size="sm" className="w-full" onClick={handleLogout}>
            Wyloguj
          </Button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-line px-4 py-3 md:hidden">
          <span className="font-display text-text-hi">Panel</span>
          <Button variant="ghost" size="sm" onClick={handleLogout}>
            Wyloguj
          </Button>
        </header>
        {/* Mobile nav */}
        <nav className="flex gap-1 overflow-x-auto border-b border-line px-3 py-2 md:hidden">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'whitespace-nowrap rounded-sm px-3 py-1.5 text-sm transition',
                  isActive ? 'bg-bg-2 text-gold' : 'text-text',
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
