import { Route, Routes } from 'react-router-dom';
import { PublicLayout } from '../layouts/PublicLayout';
import { AdminLayout } from '../layouts/AdminLayout';
import { PlayerLayout } from '../layouts/PlayerLayout';
import { ProtectedRoute } from './ProtectedRoute';
import { HomePage } from '../pages/HomePage';
import { RankingPage } from '../pages/RankingPage';
import { PlayersPage } from '../pages/PlayersPage';
import { PlayerProfilePage } from '../pages/PlayerProfilePage';
import { MatchPage } from '../pages/MatchPage';
import { MatchesPage } from '../pages/MatchesPage';
import { PatchNotesPage } from '../pages/PatchNotesPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { LoginPage } from '../pages/admin/LoginPage';
import { DashboardPage } from '../pages/admin/DashboardPage';
import { AdminPlayersPage } from '../pages/admin/AdminPlayersPage';
import { ApprovalsPage } from '../pages/admin/ApprovalsPage';
import { MatchCreatePage } from '../pages/admin/MatchCreatePage';
import { MatchControlPage } from '../pages/admin/MatchControlPage';
import { AdminMatchesPage } from '../pages/admin/AdminMatchesPage';
import { AdminHighlightsPage } from '../pages/admin/AdminHighlightsPage';
import { AdminSchedulePage } from '../pages/admin/AdminSchedulePage';
import { AdminDiagnosticsPage } from '../pages/admin/AdminDiagnosticsPage';
import { AdminSettingsPage } from '../pages/admin/AdminSettingsPage';
import { AdminAiPage } from '../pages/admin/AdminAiPage';
import { AdminDraftTestPage } from '../pages/admin/AdminDraftTestPage';
import { PlayerPanelPage } from '../pages/player/PlayerPanelPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/ranking" element={<RankingPage />} />
        <Route path="/players" element={<PlayersPage />} />
        <Route path="/players/:id" element={<PlayerProfilePage />} />
        <Route path="/matches" element={<MatchesPage />} />
        <Route path="/matches/:id" element={<MatchPage />} />
        <Route path="/patch-notes" element={<PatchNotesPage />} />
      </Route>

      <Route path="/login" element={<LoginPage />} />
      <Route path="/admin/login" element={<LoginPage />} />

      <Route element={<ProtectedRoute allowedRoles={['PLAYER']} />}>
        <Route element={<PlayerLayout />}>
          <Route path="/panel" element={<PlayerPanelPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute allowedRoles={['ADMIN', 'EDITOR']} />}>
        <Route element={<AdminLayout />}>
          <Route path="/admin" element={<DashboardPage />} />
          <Route path="/admin/players" element={<AdminPlayersPage />} />
          <Route path="/admin/approvals" element={<ApprovalsPage />} />
          <Route path="/admin/matches" element={<AdminMatchesPage />} />
          <Route path="/admin/matches/new" element={<MatchCreatePage />} />
          <Route path="/admin/highlights" element={<AdminHighlightsPage />} />
          <Route path="/admin/schedule" element={<AdminSchedulePage />} />
          <Route path="/admin/diagnostics" element={<AdminDiagnosticsPage />} />
          <Route path="/admin/settings" element={<AdminSettingsPage />} />
          <Route path="/admin/ai" element={<AdminAiPage />} />
          <Route path="/admin/draft-test" element={<AdminDraftTestPage />} />
          <Route path="/admin/matches/:id/control" element={<MatchControlPage />} />
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}