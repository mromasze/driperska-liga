import { Navigate, Outlet, useLocation } from 'react-router-dom';
import type { AccountRole } from '../api/types';
import { useAuthStore } from '../store/auth';

export interface ProtectedRouteProps {
  /** Optional role requirement; when set, other roles get bounced to /admin. */
  requiredRole?: AccountRole;
}

/** Guards /admin/*: unauthenticated users are redirected to the login page. */
export function ProtectedRoute({ requiredRole }: ProtectedRouteProps) {
  const location = useLocation();
  const accessToken = useAuthStore((s) => s.accessToken);
  const account = useAuthStore((s) => s.account);

  if (!accessToken) {
    return <Navigate to="/admin/login" replace state={{ from: location.pathname }} />;
  }

  if (requiredRole && account && account.role !== requiredRole) {
    return <Navigate to="/admin" replace />;
  }

  return <Outlet />;
}
