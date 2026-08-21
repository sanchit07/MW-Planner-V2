import { configureStore } from "@reduxjs/toolkit";
import authSlice, { AuthState } from "@services/auth/authSlice";
import { render, screen, waitFor } from "@testing-library/react";
import React from "react";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { AppRoutes } from "../AppRoutes";

const MockPage = ({ testId, label }: { testId: string; label: string }) => (
  <div data-testid={testId}>{label}</div>
);

vi.mock("@components/Tolgee/RouteNamespaceManager", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock("../../layout/Layout", () => ({
  Layout: () => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { Outlet } = require("react-router-dom");
    return <Outlet />;
  },
}));

vi.mock("../ProtectedRoute", () => ({
  default: () => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { Outlet } = require("react-router-dom");
    return <Outlet />;
  },
}));

vi.mock("../../pages/dashboard/DashboardPage", () => ({
  default: () => <MockPage testId="dashboard-page" label="Dashboard" />,
}));
vi.mock("../../pages/auth/LoginPage", () => ({
  default: () => <MockPage testId="login-page" label="Login" />,
}));
vi.mock("../../pages/settings/SettingsPage", () => ({
  default: () => <MockPage testId="settings-page" label="Settings" />,
}));
vi.mock("../../pages/campaigns/CampaignsPage", () => ({
  default: () => <MockPage testId="campaigns-page" label="Campaigns" />,
}));
vi.mock("../../pages/campaigns/CreateCampaignPage", () => ({
  default: () => (
    <MockPage testId="create-campaign-page" label="Create Campaign" />
  ),
}));
vi.mock("../../pages/campaigns/campaign-view/CampaignViewDetailsPage", () => ({
  default: () => <MockPage testId="campaign-view-page" label="Campaign View" />,
}));
vi.mock("../../pages/creatives/CreativesPage", () => ({
  default: () => <MockPage testId="creatives-page" label="Creatives" />,
}));
vi.mock("../../pages/statements/StatementsPage", () => ({
  default: () => <MockPage testId="statements-page" label="Statements" />,
}));
vi.mock("../../pages/inventories/InventoriesPage", () => ({
  default: () => <MockPage testId="inventories-page" label="Inventories" />,
}));
vi.mock("../../pages/proposal-theme/ProposalThemePage", () => ({
  default: () => (
    <MockPage testId="proposal-theme-page" label="Proposal Theme" />
  ),
}));
vi.mock("../../pages/pois/PoisPage", () => ({
  default: () => <MockPage testId="pois-page" label="Pois" />,
}));
vi.mock("../../pages/tags/TagsPage", () => ({
  default: () => <MockPage testId="tags-page" label="Tags" />,
}));
vi.mock("../../pages/profile/ProfilePage", () => ({
  default: () => <MockPage testId="profile-page" label="Profile" />,
}));
vi.mock("../../pages/auth/OAuthCallbackPage", () => ({
  default: () => (
    <MockPage testId="oauth-callback-page" label="OAuth Callback" />
  ),
}));
vi.mock("../../pages/campaigns/media-plan/ViewMediaPlanPage", () => ({
  default: () => (
    <MockPage testId="view-media-plan-page" label="View Media Plan" />
  ),
}));
vi.mock(
  "../../pages/campaigns/price-management/CampaignPriceManagement",
  () => ({
    default: () => (
      <MockPage testId="price-management-page" label="Price Management" />
    ),
  }),
);
vi.mock("../../pages/campaigns/inventory/PublicInventoryMapViewPage", () => ({
  default: () => (
    <MockPage testId="public-inventory-map-page" label="Public Map View" />
  ),
}));
vi.mock("../../pages/signals/SignalsPage", () => ({
  default: () => <MockPage testId="signals-page" label="Signals" />,
}));

const createStore = (isAuthenticated: boolean) =>
  configureStore({
    reducer: { auth: authSlice },
    preloadedState: {
      auth: {
        isAuthenticated,
        token: isAuthenticated ? "token" : null,
      } as AuthState,
    },
  });

const renderWithRouter = (
  initialEntries: string[] = ["/"],
  isAuthenticated = false,
) => {
  const store = createStore(isAuthenticated);
  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={initialEntries} initialIndex={0}>
        <AppRoutes />
      </MemoryRouter>
    </Provider>,
  );
};

describe("AppRoutes", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders without throwing", async () => {
    expect(() => renderWithRouter(["/"])).not.toThrow();
    await waitFor(() => {
      expect(document.body).toBeInTheDocument();
    });
  });

  it("redirects root to login and shows login page", async () => {
    renderWithRouter(["/"], false);
    await waitFor(
      () => {
        expect(screen.getByTestId("login-page")).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
  });

  it("renders Login page at /login", async () => {
    renderWithRouter(["/login"]);
    await waitFor(() => {
      expect(screen.getByTestId("login-page")).toBeInTheDocument();
      expect(screen.getByText("Login")).toBeInTheDocument();
    });
  });

  it("renders OAuth callback page at /auth/oauth/callback", async () => {
    renderWithRouter(["/auth/oauth/callback"]);
    await waitFor(() => {
      expect(screen.getByTestId("oauth-callback-page")).toBeInTheDocument();
    });
  });

  it("renders Not Found for unknown path", async () => {
    renderWithRouter(["/unknown-path-404"]);
    await waitFor(() => {
      expect(screen.getByText("Not Found")).toBeInTheDocument();
    });
  });

  it("renders public inventory map route at /public/inventory-map/view/:campaignId", async () => {
    renderWithRouter(["/public/inventory-map/view/campaign-123"]);
    await waitFor(() => {
      expect(
        screen.getByTestId("public-inventory-map-page"),
      ).toBeInTheDocument();
      expect(screen.getByText("Public Map View")).toBeInTheDocument();
    });
  });

  it("renders Dashboard when authenticated at /dashboard", async () => {
    renderWithRouter(["/dashboard"], true);
    await waitFor(() => {
      expect(screen.getByTestId("dashboard-page")).toBeInTheDocument();
      expect(screen.getByText("Dashboard")).toBeInTheDocument();
    });
  });

  it("renders Campaigns page when authenticated at /campaigns", async () => {
    renderWithRouter(["/campaigns"], true);
    await waitFor(() => {
      expect(screen.getByTestId("campaigns-page")).toBeInTheDocument();
    });
  });

  it("renders Create Campaign page when authenticated at /campaigns/create", async () => {
    renderWithRouter(["/campaigns/create"], true);
    await waitFor(() => {
      expect(screen.getByTestId("create-campaign-page")).toBeInTheDocument();
    });
  });

  it("renders Campaign View page when authenticated at /campaigns/view/:id", async () => {
    renderWithRouter(["/campaigns/view/c1"], true);
    await waitFor(() => {
      expect(screen.getByTestId("campaign-view-page")).toBeInTheDocument();
    });
  });

  it("renders Settings page when authenticated at /settings", async () => {
    renderWithRouter(["/settings"], true);
    await waitFor(() => {
      expect(screen.getByTestId("settings-page")).toBeInTheDocument();
    });
  });

  it("renders Profile page when authenticated at /profile", async () => {
    renderWithRouter(["/profile"], true);
    await waitFor(() => {
      expect(screen.getByTestId("profile-page")).toBeInTheDocument();
    });
  });

  it("renders View Media Plan page when authenticated at /campaigns/media-plan/:campaignId", async () => {
    renderWithRouter(["/campaigns/media-plan/camp-1"], true);
    await waitFor(() => {
      expect(screen.getByTestId("view-media-plan-page")).toBeInTheDocument();
      expect(screen.getByText("View Media Plan")).toBeInTheDocument();
    });
  });

  it("renders Price Management page when authenticated at /campaigns/price-management/:campaignId", async () => {
    renderWithRouter(["/campaigns/price-management/camp-1"], true);
    await waitFor(() => {
      expect(screen.getByTestId("price-management-page")).toBeInTheDocument();
      expect(screen.getByText("Price Management")).toBeInTheDocument();
    });
  });

  it("renders Signals page when authenticated at /signals", async () => {
    renderWithRouter(["/signals"], true);
    await waitFor(() => {
      expect(screen.getByTestId("signals-page")).toBeInTheDocument();
      expect(screen.getByText("Signals")).toBeInTheDocument();
    });
  });
});
