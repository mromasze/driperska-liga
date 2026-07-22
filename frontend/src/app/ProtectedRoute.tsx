import { Navigate, Outlet, useLocation } from 'react-router-dom';
import type { AccountRole } from '../api/types';
import { useAuthStore } from '../store/auth';

export function ProtectedRoute({ allowedRoles }: { allowedRoles?: AccountRole[] }) {
  const location = useLocation();
  const accessToken = useAuthStore((s) => s.accessToken);
  const account = useAuthStore((s) => s.account);

  if (!accessToken || !account) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (allowedRoles && !allowedRoles.includes(account.role)) {
    return <Navigate to={account.role === 'PLAYER' ? '/panel' : '/admin'} replace />;
  }
  return <Outlet />;
}