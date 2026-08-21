import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import LoginPage from "../LoginPage";

const mockNavigate = vi.fn();
let locationState: { from?: { pathname: string } } | undefined = {
  from: { pathname: "/campaigns" },
};

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useLocation: () => ({
      pathname: "/login",
      state: locationState,
      key: "default",
      search: "",
      hash: "",
    }),
  };
});

vi.mock("@config/index", () => ({
  CONFIG: { BACKEND_URL: "https://api.test.com" },
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const mockShowError = vi.fn();
vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({ showError: mockShowError }),
}));

const mockIsAuthenticated = vi.fn();
vi.mock("@store", () => ({
  useAppSelector: (
    selector: (state: { auth: { isAuthenticated: boolean } }) => boolean,
  ) => selector({ auth: { isAuthenticated: mockIsAuthenticated() } }),
}));

const originalLocation = window.location;
let hrefValue = "";

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  );
}

describe("LoginPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    hrefValue = "";
    locationState = { from: { pathname: "/campaigns" } };
    mockIsAuthenticated.mockReturnValue(false);
    Object.defineProperty(window, "location", {
      configurable: true,
      value: {
        ...originalLocation,
        origin: "https://app.test.com",
        get href() {
          return hrefValue;
        },
        set href(v: string) {
          hrefValue = v;
        },
      },
    });
  });

  afterEach(() => {
    Object.defineProperty(window, "location", {
      configurable: true,
      value: originalLocation,
    });
  });

  describe("rendering", () => {
    it("renders Moving Walls logo with correct alt text", () => {
      renderLoginPage();
      const img = screen.getByRole("img", { name: /auth\.movingWalls/i });
      expect(img).toBeInTheDocument();
      expect(img).toHaveAttribute("src", expect.stringContaining("MW-logo"));
    });

    it("renders spinner", () => {
      renderLoginPage();
      expect(screen.getByRole("status")).toBeInTheDocument();
    });

    it("renders redirecting message", () => {
      renderLoginPage();
      expect(screen.getByText("auth.redirectingToLogin")).toBeInTheDocument();
    });

    it("renders root container with expected layout classes", () => {
      const { container } = renderLoginPage();
      const root = container.querySelector(".min-h-screen.flex.flex-col");
      expect(root).toBeInTheDocument();
    });
  });

  describe("when user is already authenticated", () => {
    it("navigates to /dashboard even when location state from is present", async () => {
      mockIsAuthenticated.mockReturnValue(true);
      renderLoginPage();
      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith("/dashboard", {
          replace: true,
        });
      });
    });

    it("navigates to /dashboard when no location state from", async () => {
      locationState = undefined;
      mockIsAuthenticated.mockReturnValue(true);
      renderLoginPage();
      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith("/dashboard", {
          replace: true,
        });
      });
    });
  });

  describe("when user is not authenticated", () => {
    it("redirects to authorize URL with encoded callback", async () => {
      renderLoginPage();
      await waitFor(
        () => {
          expect(hrefValue).toBe(
            "https://api.test.com/api/v1/auth/oauth/authorize?redirect_uri=https%3A%2F%2Fapp.test.com%2Fauth%2Foauth%2Fcallback",
          );
        },
        { timeout: 1000 },
      );
    });

    it("does not persist a post-login redirect path", async () => {
      renderLoginPage();
      await waitFor(() => {
        expect(hrefValue).toContain("/auth/oauth/authorize");
      });
      expect(sessionStorage.getItem("post_login_redirect")).toBeNull();
    });
  });
});
