import { useAnnounce } from "@hooks/useAnnounce";
import {
  fetchTenantCompaniesPage,
  mapTenantCompanyToMembership,
  TenantCompany,
  TenantCompaniesPage,
} from "@services/account/accountApi";
import {
  logout,
  setToken,
  setHasPlannerAccess,
  useCallbackMutation,
} from "@services/auth/authSlice";
import {
  setUserProfile,
  updateUserProfile,
  setFetchingCompanies,
  useLazyGetProfileQuery,
  UserProfile,
} from "@services/user/userSlice";
import { useTranslate } from "@tolgee/react";
import storage from "@utils/storage";
import React, { useEffect, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

import { Loading } from "../../components/ui/Spinner";
import { useAppDispatch } from "../../store";

export function handleLogout(dispatch: ReturnType<typeof useAppDispatch>) {
  document.cookie.split(";").forEach(function (cookie) {
    const cookieName = cookie.split("=")[0].trim();
    document.cookie =
      cookieName + "=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/";
  });
  dispatch(logout());
}

const OAuthCallbackPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const [callbackApi, { error }] = useCallbackMutation();
  const [getProfile] = useLazyGetProfileQuery();
  const { showSuccess } = useAnnounce();
  const { t: tCommon } = useTranslate(["common"]);
  const calledOnce = useRef(false);

  useEffect(() => {
    const handleCallback = async () => {
      // Get the authorization code from URL params
      const code = searchParams.get("code");
      const state = searchParams.get("state");

      if (code && state) {
        try {
          calledOnce.current = true;

          // Call callback API to exchange code for token
          const result = await callbackApi({
            code,
            state,
            redirect_uri: `${window.location.origin}/auth/oauth/callback`,
            // redirect_uri: callbackUrl,
          }).unwrap();

          if (result.success && result.data?.access_token) {
            // Store token in Redux and localStorage
            dispatch(setToken(result.data));

            const hasPlannerAccess = checkHasPlannerAccess(
              result.data.access_token,
            );
            dispatch(setHasPlannerAccess(hasPlannerAccess));

            const expiryDate = new Date(result.data.expires_in).toUTCString();
            document.cookie = `oauth_access_token=${result.data.access_token}; expires=${expiryDate}; path=/; secure`;
            document.cookie = `oauth_refresh_token=${result.data.refresh_token}; expires=${expiryDate}; path=/; secure`;
            document.cookie = `oauth_token_expiry=${result.data.expires_in}; expires=${expiryDate}; path=/; secure`;

            // Fetch user profile, then enrich memberships from the companies API
            try {
              const userResult = await getProfile().unwrap();
              const profileData = (userResult as { data?: UserProfile }).data;

              // Only wait for the first page so login never stalls on a
              // large company list — the tenant switcher shows its own
              // loader while any remaining pages load in the background.
              // A failure here must not block login, matching the previous
              // silently-swallowed behavior of this call.
              let initialMemberships = profileData?.memberships ?? [];
              let firstPage: TenantCompaniesPage | null = null;
              try {
                firstPage = await fetchTenantCompaniesPage(500, 0);
                if (firstPage.items.length > 0) {
                  initialMemberships = firstPage.items.map(
                    mapTenantCompanyToMembership,
                  );
                }
              } catch (companiesError) {
                console.error(
                  "Failed to fetch tenant companies:",
                  companiesError,
                );
              }

              dispatch(
                setUserProfile({
                  ...userResult,
                  data: profileData
                    ? { ...profileData, memberships: initialMemberships }
                    : profileData,
                } as Parameters<typeof setUserProfile>[0]),
              );

              if (firstPage && firstPage.items.length < firstPage.total) {
                dispatch(setFetchingCompanies(true));
                void (async () => {
                  const allCompanies: TenantCompany[] = [...firstPage.items];
                  try {
                    while (allCompanies.length < firstPage.total) {
                      const page = await fetchTenantCompaniesPage(
                        500,
                        allCompanies.length,
                      );
                      if (page.items.length === 0) break;
                      allCompanies.push(...page.items);
                    }
                    dispatch(
                      updateUserProfile({
                        memberships: allCompanies.map(
                          mapTenantCompanyToMembership,
                        ),
                      }),
                    );
                  } catch (err) {
                    console.error("Failed to fetch remaining companies:", err);
                  } finally {
                    dispatch(setFetchingCompanies(false));
                  }
                })();
              }
            } catch (profileError) {
              console.error("Failed to fetch user profile:", profileError);
              handleLogout(dispatch);
              if (hasPlannerAccess) {
                navigate("/auth/profile-error", { replace: true });
              } else {
                navigate("/no-access", { replace: true });
              }
              return;
            }

            if (hasPlannerAccess) {
              showSuccess(tCommon("auth.authenticationSuccessful"));
            }

            // Always land on the dashboard after login. Clean up any stale
            // redirect keys left behind by older app versions.
            sessionStorage.removeItem("post_login_redirect");
            storage.removeItem("redirectURL");
            navigate("/dashboard", { replace: true });
          }
        } catch (err) {
          console.error("OAuth callback failed:", err);
          handleLogout(dispatch);
          // Redirect back to login/authorize flow
          navigate("/login", { replace: true });
        }
      } else {
        handleLogout(dispatch);
        // No code present, redirect to login
        navigate("/login", { replace: true });
      }
    };
    if (!calledOnce.current) {
      handleCallback();
    }
  }, [searchParams, callbackApi, getProfile, navigate]);

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <p className="text-mw-error-500">
          {tCommon("auth.authenticationFailed")}
        </p>
      </div>
    );
  }

  function checkHasPlannerAccess(token: string): boolean {
    const payload = parseJWT(token);
    const isGlobalAdmin = payload?.is_global_admin === true;

    let hasAccess = false;
    const hasPlannerPermission = (perms: unknown) =>
      Array.isArray(perms) &&
      perms.some((p: string) => p === "planner" || p.startsWith("planner:"));

    if (isGlobalAdmin) {
      return false;
    } else {
      const primaryCompanyId = payload?.primary_company_id;
      const companyPermissions = primaryCompanyId
        ? payload?.permissions?.[primaryCompanyId]
        : null;
      hasAccess = hasPlannerPermission(companyPermissions);
    }
    return hasAccess;
  }

  function parseJWT(token: string): UserProfile | null {
    try {
      const base64Url = token.split(".")[1];
      const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split("")
          .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
          .join(""),
      );
      return JSON.parse(jsonPayload);
    } catch (error) {
      console.error("Failed to parse JWT:", error);
      return null;
    }
  }

  return (
    <Loading
      className="fixed top-0 left-0 right-0 bottom-0 bg-white/80 z-50"
      text={tCommon("auth.authenticating")}
    />
  );
};

export default OAuthCallbackPage;
