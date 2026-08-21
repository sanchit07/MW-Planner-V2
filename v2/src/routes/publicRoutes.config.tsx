import NoAccessPage from "@pages/auth/NoAccessPage";
import { lazy } from "react";
import { Navigate, RouteObject } from "react-router-dom";

const LoginPage = lazy(() => import("../pages/auth/LoginPage"));
const OAuthCallbackPage = lazy(() => import("../pages/auth/OAuthCallbackPage"));
const ProfileLoadErrorPage = lazy(
  () => import("../pages/auth/ProfileLoadErrorPage"),
);
const PublicInventoryMapViewPage = lazy(
  () => import("../pages/campaigns/inventory/PublicInventoryMapViewPage"),
);
const PublicMediaPlanPage = lazy(
  () => import("../pages/campaigns/media-plan/PublicMediaPlanPage"),
);

export const publicRoutes: RouteObject[] = [
  { path: "/", element: <Navigate to="/login" replace /> },
  { path: "/login", element: <LoginPage /> },
  { path: "/auth/oauth/callback", element: <OAuthCallbackPage /> },
  { path: "/auth/profile-error", element: <ProfileLoadErrorPage /> },
  { path: "/no-access", element: <NoAccessPage /> },
  {
    path: "/public/inventory-map/view/:campaignId",
    element: <PublicInventoryMapViewPage />,
  },
  {
    path: "/public/media-plan/view/:campaignId",
    element: <PublicMediaPlanPage />,
  },
];
export const getPublicRoutes = () => {
  return publicRoutes;
};
