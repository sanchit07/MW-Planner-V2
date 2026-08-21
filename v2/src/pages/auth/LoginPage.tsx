// src/pages/auth/LoginPage.tsx
import { CONFIG } from "@config/index";
import { useAnnounce } from "@hooks/useAnnounce";
import { useTranslate } from "@tolgee/react";
import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { Spinner } from "../../components/ui/Spinner";
import { useAppSelector } from "../../store";
import movingWallsLogo from "/img/MW-logo-trans_1754045676555.png";

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAppSelector((state) => state.auth);
  const [isRedirecting, setIsRedirecting] = useState(false);
  const { showError } = useAnnounce();
  const { t: tCommon } = useTranslate(["common"]);

  useEffect(() => {
    const initiateOAuth = async () => {
      if (isAuthenticated) {
        // Users must always land on the dashboard after login — never the
        // page they were on before logout / session expiry.
        navigate("/dashboard", { replace: true });
        return;
      }

      if (isRedirecting) return;
      setIsRedirecting(true);

      try {
        // Build the authorize URL with callback as query param
        const callbackUrl = encodeURIComponent(
          `${window.location.origin}/auth/oauth/callback`,
          // `${CONFIG.BACKEND_URL}/api/v1/auth/oauth/callback`,
        );
        const authorizeUrl = `${CONFIG.BACKEND_URL}/api/v1/auth/oauth/authorize?redirect_uri=${callbackUrl}`;
        // Navigate directly to authorize API - browser will handle 303 redirect automatically
        window.location.href = authorizeUrl;
        setIsRedirecting(false);
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } catch (error: any) {
        console.log(error);
        showError(error.message);
      }
    };

    initiateOAuth();
  }, [isAuthenticated, navigate, isRedirecting, showError]);

  return (
    <div className="bg-white min-h-screen flex flex-col items-center justify-center">
      <img
        src={movingWallsLogo}
        alt={tCommon("auth.movingWalls")}
        className="h-20 w-20 mb-8"
      />
      <Spinner />
      <p className="mt-4 text-mw-neutral-500">
        {tCommon("auth.redirectingToLogin")}
      </p>
    </div>
  );
};

export default LoginPage;
