import RouteNamespaceManager from "@components/Tolgee/RouteNamespaceManager";
import ViewMediaPlanPage from "@pages/campaigns/media-plan/ViewMediaPlanPage";
import CampaignPriceManagement from "@pages/campaigns/price-management/CampaignPriceManagement";
import React, { Suspense, lazy } from "react";
import { Route, Routes } from "react-router-dom";

import ProtectedRoute from "./ProtectedRoute";
import { publicRoutes } from "./publicRoutes.config";
import { Layout } from "../layout/Layout";

// import { useSelector } from "react-redux";
// import { RootState } from "../store";

// Lazy load page components (public routes are in publicRoutes.config)
const DashboardPage = lazy(() => import("../pages/dashboard/DashboardPage"));
const SettingsPage = lazy(() => import("../pages/settings/SettingsPage"));
const CampaignsPage = lazy(() => import("../pages/campaigns/CampaignsPage"));
const CreateCampaignPage = lazy(
  () => import("../pages/campaigns/CreateCampaignPage"),
);
const CampaignViewDetailsPage = lazy(
  () => import("../pages/campaigns/campaign-view/CampaignViewDetailsPage"),
);
const CreativesPage = lazy(() => import("../pages/creatives/CreativesPage"));
const ReservationsPage = lazy(
  () => import("../pages/reservations/ReservationsPage"),
);
const StatementsPage = lazy(() => import("../pages/statements/StatementsPage"));
const InventoriesPage = lazy(
  () => import("../pages/inventories/InventoriesPage"),
);
const ProposalThemePage = lazy(
  () => import("../pages/proposal-theme/ProposalThemePage"),
);
const PoisPage = lazy(() => import("../pages/pois/PoisPage"));
const TagsPage = lazy(() => import("../pages/tags/TagsPage"));
const ProfilePage = lazy(() => import("../pages/profile/ProfilePage"));
const SignalsPage = lazy(() => import("../pages/signals/SignalsPage"));
const ApprovalsPage = lazy(() => import("../pages/approvals/ApprovalsPage"));
const ExecutionPlanPage = lazy(
  () => import("../pages/campaigns/execution-plan/ExecutionPlanPage"),
);
const ExecutionWorkspacePage = lazy(
  () => import("../pages/campaigns/execution-plan/ExecutionWorkspacePage"),
);
export const AppRoutes: React.FC = () => {
  // const user = useSelector((state: RootState) => state.auth.user);

  return (
    <RouteNamespaceManager>
      <Suspense fallback={<div className="p-4">Loading...</div>}>
        <Routes>
          {/* Public Routes (outside Layout, no authentication required) */}
          {publicRoutes.map((route) => (
            <Route
              key={route.path as string}
              path={route.path as string}
              element={route.element}
            />
          ))}
          <Route element={<Layout />}>
            <Route element={<ProtectedRoute />}>
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/settings" element={<SettingsPage />} />
              <Route path="/campaigns" element={<CampaignsPage />} />
              <Route
                path="/campaigns/create"
                element={<CreateCampaignPage />}
              />
              <Route
                path="/campaigns/edit/:campaignId"
                element={<CreateCampaignPage />}
              />
              <Route
                path="/campaigns/view/:campaignId"
                element={<CampaignViewDetailsPage />}
              />
              <Route
                path="/campaigns/media-plan/:campaignId"
                element={<ViewMediaPlanPage />}
              />
              <Route
                path="/campaigns/price-management/:campaignId"
                element={<CampaignPriceManagement />}
              />
              <Route
                path="/campaigns/execution-plan/:campaignId"
                element={<ExecutionPlanPage />}
              />
              <Route
                path="/campaigns/execution-workspace/:campaignId"
                element={<ExecutionWorkspacePage />}
              />
              <Route path="/approvals" element={<ApprovalsPage />} />
              <Route path="/creatives" element={<CreativesPage />} />
              <Route path="/reservations" element={<ReservationsPage />} />
              <Route path="/statements" element={<StatementsPage />} />
              <Route path="/inventories" element={<InventoriesPage />} />
              <Route path="/proposal-theme" element={<ProposalThemePage />} />
              <Route path="/pois" element={<PoisPage />} />
              <Route path="/tags" element={<TagsPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/signals" element={<SignalsPage />} />
            </Route>
          </Route>
          <Route path="*" element={<div className="p-4">Not Found</div>} />
        </Routes>
      </Suspense>
    </RouteNamespaceManager>
  );
};

export default AppRoutes;
