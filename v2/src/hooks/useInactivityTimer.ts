import { useActiveCompany } from "@hooks/useActiveCompany";
import { logout, useLazyLogoutQuery } from "@services/auth/authSlice";
import { useAppDispatch, useAppSelector } from "@store";
import { useCallback, useEffect, useRef, useState } from "react";

// Warn at 29 minutes idle, auto-logout at 30 — matches both normal and
// impersonation sessions (impersonation state is just Redux/localStorage
// state cleared by the same logout() reducer used here).
const WARNING_AFTER_MS = 29 * 60 * 1000;
const LOGOUT_AFTER_MS = 30 * 60 * 1000;
const COUNTDOWN_SECONDS = Math.round(
  (LOGOUT_AFTER_MS - WARNING_AFTER_MS) / 1000,
);

const ACTIVITY_EVENTS = [
  "mousemove",
  "mousedown",
  "keydown",
  "scroll",
  "touchstart",
] as const;

export interface UseInactivityTimerResult {
  isWarningOpen: boolean;
  remainingSeconds: number;
  handleStaySignedIn: () => void;
  handleSignOutNow: () => void;
  isImpersonating: boolean;
  impersonatedCompanyName: string;
}

export function useInactivityTimer(): UseInactivityTimerResult {
  const dispatch = useAppDispatch();
  const [userLogout] = useLazyLogoutQuery();
  const isAuthenticated = useAppSelector((s) => s.auth.isAuthenticated);
  const refreshToken = useAppSelector((s) => s.auth.refreshToken);
  const user = useAppSelector((s) => s.profile.profile);
  const { companyName: impersonatedCompanyName } = useActiveCompany();
  const isImpersonating = Boolean(
    user?.activeCompanyId && user.activeCompanyId !== user?.current_company?.id,
  );

  const [isWarningOpen, setIsWarningOpen] = useState(false);
  const [remainingSeconds, setRemainingSeconds] = useState(COUNTDOWN_SECONDS);

  const isWarningOpenRef = useRef(false);
  const refreshTokenRef = useRef(refreshToken);
  refreshTokenRef.current = refreshToken;

  const warningTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const logoutTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const countdownIntervalRef = useRef<ReturnType<typeof setInterval> | null>(
    null,
  );

  const clearTimers = useCallback(() => {
    if (warningTimeoutRef.current) clearTimeout(warningTimeoutRef.current);
    if (logoutTimeoutRef.current) clearTimeout(logoutTimeoutRef.current);
    if (countdownIntervalRef.current)
      clearInterval(countdownIntervalRef.current);
  }, []);

  const performLogout = useCallback(async () => {
    clearTimers();
    try {
      await userLogout({ refresh_token: refreshTokenRef.current || "" });
    } catch {
      // Ignore — log the user out locally regardless of the API result;
      // after this much idle time the session may already be dead.
    } finally {
      document.cookie.split(";").forEach((cookie) => {
        const cookieName = cookie.split("=")[0].trim();
        document.cookie = `${cookieName}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/`;
      });
      isWarningOpenRef.current = false;
      setIsWarningOpen(false);
      dispatch(logout());
    }
  }, [clearTimers, userLogout, dispatch]);

  const showWarning = useCallback(() => {
    isWarningOpenRef.current = true;
    setIsWarningOpen(true);
    setRemainingSeconds(COUNTDOWN_SECONDS);
    countdownIntervalRef.current = setInterval(() => {
      setRemainingSeconds((prev) => Math.max(0, prev - 1));
    }, 1000);
    logoutTimeoutRef.current = setTimeout(() => {
      performLogout();
    }, LOGOUT_AFTER_MS - WARNING_AFTER_MS);
  }, [performLogout]);

  const resetTimer = useCallback(() => {
    clearTimers();
    isWarningOpenRef.current = false;
    setIsWarningOpen(false);
    warningTimeoutRef.current = setTimeout(showWarning, WARNING_AFTER_MS);
  }, [clearTimers, showWarning]);

  const handleStaySignedIn = useCallback(() => {
    resetTimer();
  }, [resetTimer]);

  const handleSignOutNow = useCallback(() => {
    performLogout();
  }, [performLogout]);

  useEffect(() => {
    if (!isAuthenticated) {
      clearTimers();
      isWarningOpenRef.current = false;
      setIsWarningOpen(false);
      return;
    }

    resetTimer();

    // Once the warning is showing, passive activity shouldn't silently
    // dismiss it — only an explicit "Stay signed in" click should.
    const handleActivity = () => {
      if (isWarningOpenRef.current) return;
      resetTimer();
    };

    ACTIVITY_EVENTS.forEach((event) =>
      window.addEventListener(event, handleActivity, { passive: true }),
    );

    return () => {
      clearTimers();
      ACTIVITY_EVENTS.forEach((event) =>
        window.removeEventListener(event, handleActivity),
      );
    };
    // resetTimer/clearTimers are stable (refreshToken is read via a ref, not
    // a dep) — only isAuthenticated should re-run this setup.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated]);

  return {
    isWarningOpen,
    remainingSeconds,
    handleStaySignedIn,
    handleSignOutNow,
    isImpersonating,
    impersonatedCompanyName,
  };
}
