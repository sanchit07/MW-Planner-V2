import { logout } from "@services/auth/authSlice";
import { AlertTriangle } from "lucide-react";
import React from "react";
import { useNavigate } from "react-router-dom";

import { useAppDispatch } from "../../store";

const ProfileLoadErrorPage: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();

  const clearAuthAndCookies = () => {
    document.cookie.split(";").forEach((cookie) => {
      const cookieName = cookie.split("=")[0].trim();
      document.cookie =
        cookieName + "=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/";
    });
    dispatch(logout());
  };

  const handleBackToLogin = () => {
    clearAuthAndCookies();
    navigate("/login", { replace: true });
  };

  const handleCloseApplication = () => {
    clearAuthAndCookies();
    window.close();
  };

  return (
    <div
      className="flex min-h-screen flex-col items-center justify-center gap-6 bg-slate-50 p-6"
      role="alert"
      aria-live="assertive"
    >
      <AlertTriangle className="h-14 w-14 text-amber-500" aria-hidden />
      <h1 className="text-xl font-semibold text-slate-800">
        Could not load your profile
      </h1>
      <p className="max-w-md text-center text-slate-600">
        We could not load your user profile. This may be due to a temporary
        issue or a session problem. You can go back to the login screen to try
        again or close the application.
      </p>
      <div className="flex flex-col gap-3 sm:flex-row">
        <button
          type="button"
          onClick={handleBackToLogin}
          className="rounded-md bg-slate-800 px-5 py-2.5 text-sm font-medium text-white hover:bg-slate-700 focus:outline-none focus:ring-2 focus:ring-slate-500 focus:ring-offset-2"
        >
          Back to login
        </button>
        <button
          type="button"
          onClick={handleCloseApplication}
          className="rounded-md border border-slate-300 bg-white px-5 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-slate-500 focus:ring-offset-2"
        >
          Close application
        </button>
      </div>
    </div>
  );
};

export default ProfileLoadErrorPage;
