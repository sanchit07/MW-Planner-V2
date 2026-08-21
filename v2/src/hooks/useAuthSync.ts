import { CONFIG } from "@config/index";
import { LoginResponse, logout } from "@services/auth/authSlice";
import { useAppDispatch } from "@store";
import storage from "@utils/storage";
import { useEffect } from "react";

async function checkSessionAlive(): Promise<void> {
  const raw = storage.getItem("auth_token");
  if (!raw) return;

  let existing: LoginResponse;
  try {
    existing = JSON.parse(raw) as LoginResponse;
  } catch {
    return;
  }
  const refreshToken = existing.refresh_token;
  if (!refreshToken) return;

  try {
    const res = await fetch(`${CONFIG.BACKEND_URL}/api/v1/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });

    if (!res.ok) {
      storage.removeAll();
      sessionStorage.clear();
      window.location.href = "/login";
    } else {
      const data = (await res.json()) as
        | { data?: LoginResponse }
        | LoginResponse;
      const payload =
        "data" in data && data.data ? data.data : (data as LoginResponse);
      if (payload?.access_token) {
        storage.setItem(
          "auth_token",
          JSON.stringify({ ...existing, ...payload }),
        );
      }
    }
  } catch {
    // Network error — don't logout, may be temporary
  }
}

export function useAuthSync(): void {
  const dispatch = useAppDispatch();

  useEffect(() => {
    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === "auth_token" && !e.newValue) {
        dispatch(logout());
      }
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        checkSessionAlive();
      }
    };

    window.addEventListener("storage", handleStorageChange);
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      window.removeEventListener("storage", handleStorageChange);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [dispatch]);
}
