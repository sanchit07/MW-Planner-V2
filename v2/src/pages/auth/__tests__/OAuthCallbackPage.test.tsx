import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import OAuthCallbackPage from "../OAuthCallbackPage";

const mockNavigate = vi.fn();
const mockCallbackApi = vi.fn();
const mockGetProfile = vi.fn();
const mockDispatch = vi.fn();
const mockShowSuccess = vi.fn();

let searchParamsCode: string | null = "auth-code";
let searchParamsState: string | null = "auth-state";
let mutationError: unknown = null;

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useSearchParams: () => [
      {
        get: (key: string) =>
          key === "code"
            ? searchParamsCode
            : key === "state"
              ? searchParamsState
              : null,
      },
    ],
  };
});

const setTokenAction = vi.fn((data: unknown) => ({
  type: "auth/setToken",
  payload: data,
}));

const setHasPlannerAccessAction = vi.fn((data) => ({
  type: "auth/setHasPlannerAccess",
  payload: data,
}));

const logoutAction = vi.fn(() => ({ type: "auth/logout" }));

vi.mock("@services/auth/authSlice", () => ({
  get logout() {
    return logoutAction;
  },
  get setToken() {
    return setTokenAction;
  },
  get setHasPlannerAccess() {
    return setHasPlannerAccessAction;
  },
  useCallbackMutation: () => [mockCallbackApi, { error: mutationError }],
}));

const setUserProfileAction = vi.fn((data: unknown) => ({
  type: "user/setProfile",
  payload: data,
}));

vi.mock("@services/user/userSlice", () => ({
  get setUserProfile() {
    return setUserProfileAction;
  },
  updateUserProfile: vi.fn((data: unknown) => ({
    type: "user/updateUserProfile",
    payload: data,
  })),
  setFetchingCompanies: vi.fn((data: unknown) => ({
    type: "user/setFetchingCompanies",
    payload: data,
  })),
  useLazyGetProfileQuery: () => [mockGetProfile],
}));

// Prevents a real (and timing-dependent) network call — fetchTenantCompaniesPage
// is otherwise called unmocked and its resolution speed under test can race
// against waitFor's timeout.
vi.mock("@services/account/accountApi", () => ({
  fetchTenantCompaniesPage: vi.fn().mockResolvedValue({ items: [], total: 0 }),
  mapTenantCompanyToMembership: vi.fn(),
}));

vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({ showSuccess: mockShowSuccess }),
}));

vi.mock("@store", () => ({
  useAppDispatch: () => mockDispatch,
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

function renderOAuthCallbackPage() {
  return render(
    <MemoryRouter>
      <OAuthCallbackPage />
    </MemoryRouter>,
  );
}

describe("OAuthCallbackPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    searchParamsCode = "auth-code";
    searchParamsState = "auth-state";
    mutationError = null;
    mockCallbackApi.mockReturnValue({
      unwrap: vi.fn().mockResolvedValue({
        success: true,
        data: {
          access_token:
            "eyJhbGciOiJSUzI1NiIsImtpZCI6ImRlZmF1bHQta2V5LTIwMjQiLCJ0eXAiOiJKV1QifQ.eyJ1c2VyX2lkIjoiNzU1M2RhOTktNjExNC00MzNkLTg5OTktZjM1YjYxNzMwMmVkIiwiZW1haWwiOiJqZWtpQGpla2kuY29tIiwiaXNfZ2xvYmFsX2FkbWluIjpmYWxzZSwiY29tcGFueV9pZHMiOlsiNTU1ODU0ZWItOGM5Yi00NmM4LTg3NmUtYTRhNWE2MzE0NWY0Il0sInByaW1hcnlfY29tcGFueV9pZCI6IjU1NTg1NGViLThjOWItNDZjOC04NzZlLWE0YTVhNjMxNDVmNCIsInByaW1hcnlfY29tcGFueV9zZWF0X2lkIjoyNTAwMDA4LCJzdWJzY3JpcHRpb25zIjpbImU5YWU0MjBiLWU2MzgtNDViNi1hNWY0LTdhOWIzNzQ1YzAxMyIsImNjMjRhZjJlLTllNjktNDRlYS05NDQwLTgzODc5MGNkOGMwYSIsImFlYTFmZmFlLTAzOWItNDJhZS05OGMzLWQyOGVmOTQzNjMyOCIsIjM0MzBmMjM4LTY4MTctNDdmNi1iMzcxLTRiMjhkMWJiMWEzNCIsIjUxYjlhMDQ0LTM1NjUtNGIxZi05Yzg4LTFhMDRmNjMzZDljYiIsImM1MjljZTcxLTgwOTEtNDUyYi04NWZlLTY0Mjc3NDllY2E1NCIsImZjNDM4N2QwLWQzNTktNGQxMi05NDYwLTZlY2FhZTAwMGJhYyIsImQ3ODExYzc3LTk3NTEtNGQxZS04NTRjLTAwMDVlNWM1NGIyOCIsIjk1YmFkOGQ0LTRkNDMtNGQxOC04NjYxLWJkOTczMDg2YTY1YiJdLCJwZXJtaXNzaW9ucyI6eyI1NTU4NTRlYi04YzliLTQ2YzgtODc2ZS1hNGE1YTYzMTQ1ZjQiOlsic2lnbmFsczoqOioiLCJ2ZXJpZmljYXRpb246KjoqIiwiaW1zOio6KiIsImluZmx1ZW5jZToqOioiLCJtZWFzdXJlOio6KiIsInN0dWRpbzoqOioiLCJhY3RpdmF0ZToqOioiLCJwbGFubmVyOio6KiIsImlhbToqOioiXX0sImlzcyI6Imh0dHBzOi8vYXV0aC1hcGktc3RnLm1vdmluZ3dhbGxzLmNvbSIsInN1YiI6Impla2kiLCJhdWQiOlsidXJuOmlhbS1mcm9udGVuZCIsInVybjpwcm9kdWN0Ok1BUktFVFBMQUNFIiwidXJuOnByb2R1Y3Q6UExBTk5FUiIsInVybjpwcm9kdWN0OlNJR05BTFMiLCJ1cm46cHJvZHVjdDpTVFVESU8iLCJ1cm46cHJvZHVjdDpWRVJJRklDQVRJT04iLCJ1cm46cHJvZHVjdDpNRUFTVVJFIiwidXJuOnByb2R1Y3Q6SU5GTFVFTkNFIiwidXJuOnByb2R1Y3Q6QUNUSVZBVEUiLCJ1cm46cHJvZHVjdDpJTVMiXSwiZXhwIjoxNzc0NDkzMzE5LCJuYmYiOjE3NzQ0MDY5MTksImlhdCI6MTc3NDQwNjkxOX0.Wh2nZSXPwKOalojhiIr7HHrQuvrZSVhAjMuN6MQepDPpcAr_xCVpc88ys-4hhvnPFx0b6xixdWknKjJwCsN7ayNCEx6xA2YLhQoNMHDaBZ1XCDs3r-8K-kptX08a3BU4y5Pb0So99Pwa8hkd70AK646HT-afKH_irdteFj3mqmD6pZt8rcKsq3YwPq_JrNO2XE81GbzUiIWMjRhn_yWtMTnJX0eXL1mMdXUDnOjXxStilLyT1umOE9SmWLXIdOCmkezOHxYBHSW5wWa7FW2L5KP7PKYiCVZeptfsm1L-olkaiLnU5gZ5BkIaxxISlDBeXRMnuscUrREcSoOXQdPN8g",
          refresh_token: "refresh",
          expires_in: new Date(Date.now() + 3600000).toISOString(),
        },
      }),
    });
    mockGetProfile.mockReturnValue({
      unwrap: vi.fn().mockResolvedValue({ id: "user-1", name: "User" }),
    });
    Object.defineProperty(document, "cookie", {
      writable: true,
      value: "",
      configurable: true,
    });
  });

  describe("rendering", () => {
    it("renders loading state by default", () => {
      renderOAuthCallbackPage();
      expect(screen.getByRole("status")).toBeInTheDocument();
      expect(screen.getByText("auth.authenticating")).toBeInTheDocument();
    });

    it("renders error message when mutation has error", () => {
      mutationError = new Error("Auth failed");
      renderOAuthCallbackPage();
      expect(screen.getByText("auth.authenticationFailed")).toBeInTheDocument();
    });
  });

  describe("when code and state are present", () => {
    it("calls callback API with code, state, and redirect_uri", async () => {
      renderOAuthCallbackPage();
      await waitFor(() => {
        expect(mockCallbackApi).toHaveBeenCalledWith({
          code: "auth-code",
          state: "auth-state",
          redirect_uri: expect.stringContaining("/auth/oauth/callback"),
        });
      });
    });

    it("dispatches setToken with response data on success", async () => {
      renderOAuthCallbackPage();
      await waitFor(() => {
        expect(setTokenAction).toHaveBeenCalledWith(
          expect.objectContaining({
            access_token:
              "eyJhbGciOiJSUzI1NiIsImtpZCI6ImRlZmF1bHQta2V5LTIwMjQiLCJ0eXAiOiJKV1QifQ.eyJ1c2VyX2lkIjoiNzU1M2RhOTktNjExNC00MzNkLTg5OTktZjM1YjYxNzMwMmVkIiwiZW1haWwiOiJqZWtpQGpla2kuY29tIiwiaXNfZ2xvYmFsX2FkbWluIjpmYWxzZSwiY29tcGFueV9pZHMiOlsiNTU1ODU0ZWItOGM5Yi00NmM4LTg3NmUtYTRhNWE2MzE0NWY0Il0sInByaW1hcnlfY29tcGFueV9pZCI6IjU1NTg1NGViLThjOWItNDZjOC04NzZlLWE0YTVhNjMxNDVmNCIsInByaW1hcnlfY29tcGFueV9zZWF0X2lkIjoyNTAwMDA4LCJzdWJzY3JpcHRpb25zIjpbImU5YWU0MjBiLWU2MzgtNDViNi1hNWY0LTdhOWIzNzQ1YzAxMyIsImNjMjRhZjJlLTllNjktNDRlYS05NDQwLTgzODc5MGNkOGMwYSIsImFlYTFmZmFlLTAzOWItNDJhZS05OGMzLWQyOGVmOTQzNjMyOCIsIjM0MzBmMjM4LTY4MTctNDdmNi1iMzcxLTRiMjhkMWJiMWEzNCIsIjUxYjlhMDQ0LTM1NjUtNGIxZi05Yzg4LTFhMDRmNjMzZDljYiIsImM1MjljZTcxLTgwOTEtNDUyYi04NWZlLTY0Mjc3NDllY2E1NCIsImZjNDM4N2QwLWQzNTktNGQxMi05NDYwLTZlY2FhZTAwMGJhYyIsImQ3ODExYzc3LTk3NTEtNGQxZS04NTRjLTAwMDVlNWM1NGIyOCIsIjk1YmFkOGQ0LTRkNDMtNGQxOC04NjYxLWJkOTczMDg2YTY1YiJdLCJwZXJtaXNzaW9ucyI6eyI1NTU4NTRlYi04YzliLTQ2YzgtODc2ZS1hNGE1YTYzMTQ1ZjQiOlsic2lnbmFsczoqOioiLCJ2ZXJpZmljYXRpb246KjoqIiwiaW1zOio6KiIsImluZmx1ZW5jZToqOioiLCJtZWFzdXJlOio6KiIsInN0dWRpbzoqOioiLCJhY3RpdmF0ZToqOioiLCJwbGFubmVyOio6KiIsImlhbToqOioiXX0sImlzcyI6Imh0dHBzOi8vYXV0aC1hcGktc3RnLm1vdmluZ3dhbGxzLmNvbSIsInN1YiI6Impla2kiLCJhdWQiOlsidXJuOmlhbS1mcm9udGVuZCIsInVybjpwcm9kdWN0Ok1BUktFVFBMQUNFIiwidXJuOnByb2R1Y3Q6UExBTk5FUiIsInVybjpwcm9kdWN0OlNJR05BTFMiLCJ1cm46cHJvZHVjdDpTVFVESU8iLCJ1cm46cHJvZHVjdDpWRVJJRklDQVRJT04iLCJ1cm46cHJvZHVjdDpNRUFTVVJFIiwidXJuOnByb2R1Y3Q6SU5GTFVFTkNFIiwidXJuOnByb2R1Y3Q6QUNUSVZBVEUiLCJ1cm46cHJvZHVjdDpJTVMiXSwiZXhwIjoxNzc0NDkzMzE5LCJuYmYiOjE3NzQ0MDY5MTksImlhdCI6MTc3NDQwNjkxOX0.Wh2nZSXPwKOalojhiIr7HHrQuvrZSVhAjMuN6MQepDPpcAr_xCVpc88ys-4hhvnPFx0b6xixdWknKjJwCsN7ayNCEx6xA2YLhQoNMHDaBZ1XCDs3r-8K-kptX08a3BU4y5Pb0So99Pwa8hkd70AK646HT-afKH_irdteFj3mqmD6pZt8rcKsq3YwPq_JrNO2XE81GbzUiIWMjRhn_yWtMTnJX0eXL1mMdXUDnOjXxStilLyT1umOE9SmWLXIdOCmkezOHxYBHSW5wWa7FW2L5KP7PKYiCVZeptfsm1L-olkaiLnU5gZ5BkIaxxISlDBeXRMnuscUrREcSoOXQdPN8g",
            refresh_token: "refresh",
          }),
        );
      });
      expect(mockDispatch).toHaveBeenCalled();
    });

    it("fetches user profile and dispatches setUserProfile on success", async () => {
      renderOAuthCallbackPage();
      await waitFor(() => {
        expect(mockGetProfile).toHaveBeenCalled();
      });
      await waitFor(() => {
        expect(setUserProfileAction).toHaveBeenCalledWith(
          expect.objectContaining({ id: "user-1", name: "User" }),
        );
      });
      expect(mockDispatch).toHaveBeenCalled();
    });

    it("shows success message and navigates to dashboard on success", async () => {
      renderOAuthCallbackPage();
      await waitFor(() => {
        expect(mockShowSuccess).toHaveBeenCalledWith(
          "auth.authenticationSuccessful",
        );
      });
      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith("/dashboard", {
          replace: true,
        });
      });
    });

    it("on callback API failure dispatches logout and navigates to login", async () => {
      mockCallbackApi.mockReturnValue({
        unwrap: vi.fn().mockRejectedValue(new Error("Token exchange failed")),
      });
      renderOAuthCallbackPage();
      await waitFor(() => {
        expect(logoutAction).toHaveBeenCalled();
      });
      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith("/login", { replace: true });
      });
    });

    it("on profile fetch failure dispatches logout", async () => {
      mockGetProfile.mockReturnValue({
        unwrap: vi.fn().mockRejectedValue(new Error("Profile failed")),
      });
      renderOAuthCallbackPage();
      await waitFor(() => {
        expect(logoutAction).toHaveBeenCalled();
      });
    });
  });

  describe("when code or state is missing", () => {
    it("dispatches logout and navigates to login when code is missing", async () => {
      searchParamsCode = null;
      renderOAuthCallbackPage();
      await waitFor(() => {
        expect(logoutAction).toHaveBeenCalled();
      });
      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith("/login", { replace: true });
      });
    });

    it("dispatches logout and navigates to login when state is missing", async () => {
      searchParamsState = null;
      renderOAuthCallbackPage();
      await waitFor(() => {
        expect(logoutAction).toHaveBeenCalled();
      });
      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith("/login", { replace: true });
      });
    });
  });
});
