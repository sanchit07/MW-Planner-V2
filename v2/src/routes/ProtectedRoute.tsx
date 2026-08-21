import React from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";

import { useAppSelector } from "../store";
import {
  hasRouteAccess,
  ROUTE_PERMISSIONS,
  UserRole,
} from "./routePermissions";

export { hasRouteAccess, ROUTE_PERMISSIONS };
export type { UserRole };

export const ProtectedRoute: React.FC = () => {
  const { isAuthenticated } = useAppSelector((state) => state.auth);
  const hasPlannerAccess = useAppSelector(
    (state) => state.auth.hasPlannerAccess,
  );

  const location = useLocation();
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (!hasPlannerAccess) {
    if (location.pathname !== "/no-access") {
      return <Navigate to="/no-access" replace />;
    }
    return <Outlet />;
  }

  if (location.pathname === "/dashboard") {
    return <Outlet />;
  }

  // Check role-based access
  // const hasAccess = hasRouteAccess(location.pathname, user.role);

  // if (!hasAccess) {
  //   return <Navigate to="/dashboard" replace />;
  // }

  return <Outlet />;
};

export default ProtectedRoute;
