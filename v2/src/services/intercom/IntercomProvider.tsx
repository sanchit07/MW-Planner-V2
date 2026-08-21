import Intercom, { shutdown, update } from "@intercom/messenger-js-sdk";
import { useAppSelector } from "@store";
import { useEffect } from "react";
import { useLocation } from "react-router-dom";

import { useLazyGetIntercomJwtQuery } from "./intercomApi";

interface IntercomProviderProps {
  children: React.ReactNode;
}

/**
 * Boots the Intercom Messenger for authenticated users only, using a server-signed
 * JWT (identity verification). The Messenger is shut down on logout and re-booted on
 * account switch via the `isAuthenticated` dependency.
 */
export const IntercomProvider: React.FC<IntercomProviderProps> = ({
  children,
}) => {
  const location = useLocation();
  const isAuthenticated = useAppSelector((state) => state.auth.isAuthenticated);
  const [fetchJwt] = useLazyGetIntercomJwtQuery();

  useEffect(() => {
    let active = true;

    const boot = async () => {
      if (isAuthenticated) {
        try {
          const res = await fetchJwt().unwrap();
          const data = res?.data;
          if (active && data?.token) {
            Intercom({
              app_id: data.app_id,
              intercom_user_jwt: data.token,
              alignment: "left",
            });
          }
        } catch {
          // JWT fetch failed — skip booting rather than leak an unverified session.
        }
      }
    };

    boot();

    return () => {
      active = false;
      shutdown();
    };
  }, [isAuthenticated, fetchJwt]);

  // SPA route changes — keep the Messenger in sync.
  useEffect(() => {
    update({});
  }, [location]);

  return <>{children}</>;
};

export default IntercomProvider;
