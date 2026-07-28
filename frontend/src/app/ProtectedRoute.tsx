import { useEffect, useRef, useState } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import type { AccountRole } from '../api/types';
import { useAuthStore } from '../store/auth';
import { restoreSession } from '../lib/session';
import { LoadingState } from '../components/ui/States';

export function ProtectedRoute({ allowedRoles }: { allowedRoles?: AccountRole[] }) {
  const location = useLocation();
  const accessToken = useAuthStore((s) => s.accessToken);
  const account = useAuthStore((s) => s.account);
  const remember = useAuthStore((s) => s.remember);
  const credentials = useAuthStore((s) => s.credentials);

  // With "remember me" on there is a window — right after a backend restart wiped the tokens — where
  // credentials exist but the session does not. Restore it here instead of bouncing the user to the
  // login screen they explicitly opted out of.
  const canRestore = Boolean(remember && credentials) && (!accessToken || !account);
  const [restoring, setRestoring] = useState(canRestore);
  const attempted = useRef(false);

  useEffect(() => {
    if (!canRestore || attempted.current) return;
    attempted.current = true;
    setRestoring(true);
    void restoreSession().finally(() => setRestoring(false));
  }, [canRestore]);

  if (restoring) return <LoadingState label="Przywracanie sesji…" />;

  if (!accessToken || !account) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (allowedRoles && !allowedRoles.includes(account.role)) {
    return <Navigate to={account.role === 'PLAYER' ? '/panel' : '/admin'} replace />;
  }
  return <Outlet />;
}
