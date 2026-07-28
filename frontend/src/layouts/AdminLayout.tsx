import { useState } from 'react';
import { NavLink, Outlet, Link, useNavigate, useLocation } from 'react-router-dom';
import { cn } from '../lib/cn';
import { useAuthStore } from '../store/auth';
import { useLogout } from '../api/hooks/auth';
import { useMatches } from '../api/hooks/matches';

interface NavLeaf { to: string; label: string; end?: boolean; badge?: 'approvals'; }
interface NavGroup { group: string; items: NavLeaf[]; }
type NavEntry = NavLeaf | NavGroup;

const isGroup = (e: NavEntry): e is NavGroup => 'group' in e;

const NAV: NavEntry[] = [
  { to: '/admin', label: 'Pulpit', end: true },
  {
    group: 'Mecze',
    items: [
      { to: '/admin/matches', label: 'Lista meczów', end: true },
      { to: '/admin/matches/new', label: 'Nowy mecz' },
      { to: '/admin/schedule', label: 'Plan meczów' },
      { to: '/admin/approvals', label: 'Akceptacje', badge: 'approvals' },
    ],
  },
  { to: '/admin/draft-test', label: 'Test draftu' },
  { to: '/admin/highlights', label: 'Zagrywki' },
  { to: '/admin/players', label: 'Gracze' },
  { to: '/admin/diagnostics', label: 'Diagnostyka' },
  { to: '/admin/ai', label: 'AI' },
  { to: '/admin/settings', label: 'Ustawienia' },
];

const LEAVES: NavLeaf[] = NAV.flatMap((e) => (isGroup(e) ? e.items : [e]));

export function AdminLayout() {
  const account = useAuthStore((s) => s.account);
  const logout = useLogout();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const pending = useMatches({ status: 'RESULTS_SUBMITTED', size: 1 });
  const pendingCount = pending.data?.totalElements ?? 0;

  const matches = (item: NavLeaf) =>
    item.end ? location.pathname === item.to : location.pathname.startsWith(item.to);

  const activeItem = [...LEAVES]
    .sort((a, b) => b.to.length - a.to.length)
    .find(matches);
  const signOut = () => logout.mutate(undefined, { onSettled: () => navigate('/admin/login') });

  // A group opens itself when you are inside it, and stays wherever you last put it after that.
  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>({});
  const groupIsOpen = (entry: NavGroup) =>
    openGroups[entry.group] ?? entry.items.some(matches);
  const toggleGroup = (name: string, open: boolean) =>
    setOpenGroups((current) => ({ ...current, [name]: !open }));

  const leaf = (item: NavLeaf, onClick?: () => void) => (
    <NavLink
      key={item.to}
      to={item.to}
      end={item.end}
      onClick={onClick}
      className={({ isActive }) =>
        cn(
          'flex items-center justify-between rounded-md px-3 py-2 text-sm font-medium transition-colors',
          isActive ? 'bg-[var(--glass-strong)] text-text-hi' : 'text-text-lo hover:text-text',
        )
      }
    >
      <span>{item.label}</span>
      {item.badge === 'approvals' && pendingCount > 0 && (
        <span className="num rounded-full bg-pending px-2 text-xs font-bold text-[#1a1205]">{pendingCount}</span>
      )}
    </NavLink>
  );

  const nav = (onClick?: () => void) => (
    <>
      {NAV.map((entry) => {
        if (!isGroup(entry)) return leaf(entry, onClick);
        const open = groupIsOpen(entry);
        // Badges inside a collapsed group would be invisible, so the header carries the count.
        const hiddenBadge = !open && entry.items.some((i) => i.badge === 'approvals') && pendingCount > 0;
        return (
          <div key={entry.group} className="mt-1 first:mt-0">
            <button
              type="button"
              onClick={() => toggleGroup(entry.group, open)}
              aria-expanded={open}
              className={cn(
                'flex w-full items-center justify-between rounded-md px-3 py-2 text-sm font-medium transition-colors',
                open ? 'text-text-hi' : 'text-text-lo hover:text-text',
              )}
            >
              <span>{entry.group}</span>
              <span className="flex items-center gap-2">
                {hiddenBadge && (
                  <span className="num rounded-full bg-pending px-2 text-xs font-bold text-[#1a1205]">
                    {pendingCount}
                  </span>
                )}
                <span className={cn('text-xs text-text-lo transition-transform', open && 'rotate-180')}>▾</span>
              </span>
            </button>
            {open && (
              <div className="flex flex-col gap-0.5 border-l border-line pl-1.5">
                {entry.items.map((item) => leaf(item, onClick))}
              </div>
            )}
          </div>
        );
      })}
    </>
  );

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-60 shrink-0 flex-col border-r border-line bg-[color:var(--bg-1)]/60 p-4 md:flex">
        <Link to="/" className="mb-8 flex items-center gap-2.5 px-2">
          <span className="grid h-9 w-9 place-items-center rounded-md bg-gradient-to-b from-gold-soft to-gold text-[#1a1205]">
            <span className="font-display text-lg font-bold">D</span>
          </span>
          <span className="font-display font-bold text-text-hi">Panel</span>
        </Link>
        <nav className="flex flex-1 flex-col gap-1">{nav()}</nav>
        <div className="mt-4 border-t border-line pt-4">
          <div className="px-2 text-sm text-text-hi">{account?.username}</div>
          <div className="px-2 text-xs text-text-lo">{account?.role}</div>
          <button
            onClick={signOut}
            className="mt-2 w-full rounded-md px-2 py-1.5 text-left text-sm text-text-lo hover:text-loss"
          >
            Wyloguj
          </button>
        </div>
      </aside>

      <div className="min-w-0 flex-1">
        <header className="sticky top-0 z-30 flex items-center justify-between border-b border-line bg-[color:var(--bg-1)]/95 px-4 py-3 backdrop-blur md:hidden">
          <Link to="/" className="flex items-center gap-2">
            <span className="grid h-8 w-8 place-items-center rounded-md bg-gradient-to-b from-gold-soft to-gold text-[#1a1205]">
              <span className="font-display text-base font-bold">D</span>
            </span>
            <span className="font-display font-bold text-text-hi">{activeItem?.label ?? 'Panel'}</span>
          </Link>
          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-expanded={menuOpen}
            aria-label="Menu"
            className="relative inline-flex h-10 w-10 items-center justify-center rounded-md border border-line text-text-hi"
          >
            {menuOpen ? '✕' : '☰'}
            {!menuOpen && pendingCount > 0 && (
              <span className="absolute -right-1 -top-1 h-2.5 w-2.5 rounded-full bg-pending" />
            )}
          </button>
        </header>

        {menuOpen && (
          <div className="border-b border-line bg-[color:var(--bg-1)]/95 backdrop-blur md:hidden">
            <nav className="flex flex-col p-2">
              {nav(() => setMenuOpen(false))}
              <div className="mt-2 flex items-center justify-between border-t border-line px-3 pt-3">
                <div className="min-w-0">
                  <div className="truncate text-sm text-text-hi">{account?.username}</div>
                  <div className="text-xs text-text-lo">{account?.role}</div>
                </div>
                <button
                  onClick={() => { setMenuOpen(false); signOut(); }}
                  className="rounded-md px-2 py-1.5 text-sm text-text-lo hover:text-loss"
                >
                  Wyloguj
                </button>
              </div>
            </nav>
          </div>
        )}

        <main className="mx-auto max-w-5xl p-4 sm:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
