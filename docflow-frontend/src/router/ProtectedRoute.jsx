import { Navigate } from "react-router-dom";

import { useAppSelector } from "../hooks/useAppSelector";
import { ROUTES } from "./routeConstants";

function getHomeRoute(role) {
  switch (role) {
    case "ADMIN":
      return ROUTES.DASHBOARD;

    case "INDEXER":
      return ROUTES.INDEXER;

    case "PROCESSOR":
      return ROUTES.UPLOAD;

    default:
      return ROUTES.LOGIN;
  }
}

function ProtectedRoute({
  children,
  roles = [],
}) {
  const {
    isAuthenticated,
    user,
  } = useAppSelector(
    (state) => state.auth
  );

  /**
   * User is not logged in
   */
  if (!isAuthenticated) {
    return (
      <Navigate
        to={ROUTES.LOGIN}
        replace
      />
    );
  }

  /**
   * User doesn't have permission
   */
  if (
    roles.length > 0 &&
    !roles.includes(user?.role)
  ) {
    return (
      <Navigate
        to={getHomeRoute(user?.role)}
        replace
      />
    );
  }

  return children;
}

export default ProtectedRoute;