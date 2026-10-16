// src/router/AppRoutes.jsx

import {
  Navigate,
  Route,
  Routes,
} from "react-router-dom";

import DashboardLayout from "../layouts/DashboardLayout";

import ProtectedRoute from "./ProtectedRoute";
import { ROUTES } from "./routeConstants";

/* Pages */
import LoginPage from "../pages/Login/LoginPage";
import DashboardPage from "../pages/Dashboard/DashboardPage";
import UploadPage from "../pages/Upload/UploadPage";
import DocumentLibraryPage from "../pages/DocumentLibrary/DocumentLibraryPage";
import IndexerPage from "../pages/Indexer/IndexerPage";
import ConversationsPage from "../pages/Conversations/ConversationsPage";
import AdministrationPage from "../pages/Administration/AdministrationPage";
import UsersPage from "../pages/Users/UsersPage";
import SettingsPage from "../pages/Settings/SettingsPage";

function AppRoutes() {
  return (
    <Routes>

      {/* Login */}

      <Route
        path={ROUTES.LOGIN}
        element={<LoginPage />}
      />

      {/* Protected */}

      <Route
        element={
          <ProtectedRoute>
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        <Route
          path={ROUTES.DASHBOARD}
          element={<DashboardPage />}
        />

        <Route
          path={ROUTES.UPLOAD}
          element={<UploadPage />}
        />

        <Route
          path={ROUTES.DOCUMENT_PROCESSING}
          element={<DocumentLibraryPage />}
        />

        <Route
          path={ROUTES.INDEXER}
          element={<IndexerPage />}
        />

        <Route
          path={ROUTES.CONVERSATIONS}
          element={<ConversationsPage />}
        />

        <Route
          path={ROUTES.ADMINISTRATION}
          element={<AdministrationPage />}
        />

        <Route
          path={ROUTES.USERS}
          element={<UsersPage />}
        />

        <Route
          path={ROUTES.SETTINGS}
          element={<SettingsPage />}
        />
      </Route>

      {/* Unknown URL */}

      <Route
        path="*"
        element={
          <Navigate
            to={ROUTES.DASHBOARD}
            replace
          />
        }
      />

    </Routes>
  );
}

export default AppRoutes;