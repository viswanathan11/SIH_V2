import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, useAuth } from './lib/auth';
import { EventsProvider } from './lib/events';
import { homeFor } from './lib/roles';

import AppLayout from './layout/AppLayout';
import LoginPage from './pages/auth/LoginPage';
import NationalDashboard from './pages/aicte/NationalDashboard';
import InstitutionsPage from './pages/aicte/InstitutionsPage';
import InstitutionDetailPage from './pages/aicte/InstitutionDetailPage';
import AnomaliesPage from './pages/aicte/AnomaliesPage';
import IssuesPage from './pages/aicte/IssuesPage';
import ReportsPage from './pages/aicte/ReportsPage';
import AuditPage from './pages/aicte/AuditPage';
import AdminPage from './pages/aicte/AdminPage';

import InstitutionDashboard from './pages/inst/InstitutionDashboard';
import UploadPage from './pages/inst/UploadPage';
import WorklistPage from './pages/inst/WorklistPage';
import StudentsPage from './pages/inst/StudentsPage';
import IntegrationPage from './pages/inst/IntegrationPage';

import VerifyPortalPage from './pages/verify/VerifyPortalPage';
import PublicVerifyPage from './pages/verify/PublicVerifyPage';
import StudentPortalPage from './pages/student/StudentPortalPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

function ProtectedRoute({ children }: { children: JSX.Element }) {
  const { user, loading } = useAuth();
  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 dark:bg-slate-950 text-xs text-slate-400">
        Authenticating session...
      </div>
    );
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function RootRedirect() {
  const { user, loading } = useAuth();
  if (loading) return null;
  return <Navigate to={homeFor(user)} replace />;
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <EventsProvider>
          <BrowserRouter>
            <Routes>
              {/* Public Routes */}
              <Route path="/login" element={<LoginPage />} />
              <Route path="/v/:jti" element={<PublicVerifyPage />} />

              {/* Protected Application Routes */}
              <Route
                path="/"
                element={
                  <ProtectedRoute>
                    <AppLayout />
                  </ProtectedRoute>
                }
              >
                <Route index element={<RootRedirect />} />

                {/* AICTE National Routes */}
                <Route path="aicte" element={<NationalDashboard />} />
                <Route path="aicte/institutions" element={<InstitutionsPage />} />
                <Route path="aicte/institutions/:id" element={<InstitutionDetailPage />} />
                <Route path="aicte/anomalies" element={<AnomaliesPage />} />
                <Route path="aicte/issues" element={<IssuesPage />} />
                <Route path="aicte/reports" element={<ReportsPage />} />
                <Route path="aicte/audit" element={<AuditPage />} />
                <Route path="aicte/admin" element={<AdminPage />} />

                {/* Institution Routes */}
                <Route path="inst" element={<InstitutionDashboard />} />
                <Route path="inst/upload" element={<UploadPage />} />
                <Route path="inst/worklist" element={<WorklistPage />} />
                <Route path="inst/students" element={<StudentsPage />} />
                <Route path="inst/integration" element={<IntegrationPage />} />

                {/* Verification & Student Routes */}
                <Route path="verify" element={<VerifyPortalPage />} />
                <Route path="me" element={<StudentPortalPage />} />
              </Route>

              {/* Catch-all */}
              <Route path="*" element={<RootRedirect />} />
            </Routes>
          </BrowserRouter>
        </EventsProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
}
