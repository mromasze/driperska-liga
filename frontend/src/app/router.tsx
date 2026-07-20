import { Route, Routes } from 'react-router-dom';
import { PublicLayout } from '../layouts/PublicLayout';
import { AdminLayout } from '../layouts/AdminLayout';
import { ProtectedRoute } from './ProtectedRoute';

import { HomePage } from '../pages/HomePage';
import { RankingPage } from '../pages/RankingPage';
import { PlayersPage } from '../pages/PlayersPage';
import { PlayerProfilePage } from '../pages/PlayerProfilePage';
import { MatchPage } from '../pages/MatchPage';
import { NotFoundPage } from '../pages/NotFoundPage';

import { LoginPage } from '../pages/admin/LoginPage';
import { DashboardPage } from '../pages/admin/DashboardPage';
import { AdminPlayersPage } from '../pages/admin/AdminPlayersPage';
import { ApprovalsPage } from '../pages/admin/ApprovalsPage';
import { MatchCreatePage } from '../pages/admin/MatchCreatePage';
import { MatchControlPage } from '../pages/admin/MatchControlPage';

export function AppRoutes() {
  return (
    <Routes>
      {/* Public */}
      <Route element={<PublicLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/ranking" element={<RankingPage />} />
        <Route path="/players" element={<PlayersPage />} />
        <Route path="/players/:id" element={<PlayerProfilePage />} />
        <Route path="/matches/:id" element={<MatchPage />} />
      </Route>

      {/* Admin login (unguarded) */}
      <Route path="/admin/login" element={<LoginPage />} />

      {/* Admin (guarded) */}
      <Route element={<ProtectedRoute />}>
        <Route element={<AdminLayout />}>
          <Route path="/admin" element={<DashboardPage />} />
          <Route path="/admin/players" element={<AdminPlayersPage />} />
          <Route path="/admin/approvals" element={<ApprovalsPage />} />
          <Route path="/admin/matches/new" element={<MatchCreatePage />} />
          <Route path="/admin/matches/:id/control" element={<MatchControlPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
