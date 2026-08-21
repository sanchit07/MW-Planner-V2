import { configureStore } from "@reduxjs/toolkit";
import sidebarSlice from "@services/sidebar-toggle/sidebar-toggle.slice";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import SidebarDefault, { Sidebar } from "../Sidebar";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        "sidebar.dashboard": "Dashboard",
        "sidebar.campaigns": "Campaigns",
      };
      return translations[key] || key;
    },
  }),
}));

const createMockStore = (isCollapsed: boolean) => {
  return configureStore({
    reducer: {
      sidebar: sidebarSlice,
    },
    preloadedState: {
      sidebar: {
        isSidebarCollapsed: isCollapsed,
      },
    },
  });
};

const TestWrapper = ({
  children,
  isCollapsed = false,
  initialEntries = ["/"],
}: {
  children: React.ReactNode;
  isCollapsed?: boolean;
  initialEntries?: string[];
}) => {
  const store = createMockStore(isCollapsed);
  return (
    <Provider store={store}>
      <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
    </Provider>
  );
};

describe("Sidebar", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders aside with id app-sidebar", () => {
      render(
        <TestWrapper>
          <Sidebar />
        </TestWrapper>,
      );
      const sidebar = screen.getByRole("complementary", { name: "" });
      expect(sidebar).toBeInTheDocument();
      expect(sidebar).toHaveAttribute("id", "app-sidebar");
    });

    it("renders nav with id sidebar-nav", () => {
      render(
        <TestWrapper>
          <Sidebar />
        </TestWrapper>,
      );
      const nav = screen.getByRole("navigation");
      expect(nav).toBeInTheDocument();
      expect(nav).toHaveAttribute("id", "sidebar-nav");
    });

    it("renders dashboard link with correct href", () => {
      render(
        <TestWrapper>
          <Sidebar />
        </TestWrapper>,
      );
      const dashboardLink = screen.getByRole("link", { name: /dashboard/i });
      expect(dashboardLink).toBeInTheDocument();
      expect(dashboardLink).toHaveAttribute("href", "/dashboard");
      expect(dashboardLink).toHaveAttribute("id", "sidebar-nav-dashboard");
    });

    it("renders campaigns link with correct href", () => {
      render(
        <TestWrapper>
          <Sidebar />
        </TestWrapper>,
      );
      const campaignsLink = screen.getByRole("link", { name: /campaigns/i });
      expect(campaignsLink).toBeInTheDocument();
      expect(campaignsLink).toHaveAttribute("href", "/campaigns");
      expect(campaignsLink).toHaveAttribute("id", "sidebar-nav-campaigns");
    });

    it("exports default component that renders the same sidebar", () => {
      render(
        <TestWrapper>
          <SidebarDefault />
        </TestWrapper>,
      );
      expect(screen.getByRole("complementary")).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: /dashboard/i }),
      ).toBeInTheDocument();
    });
  });

  describe("collapsed state", () => {
    it("applies collapsed width class when isCollapsed is true", () => {
      render(
        <TestWrapper isCollapsed={true}>
          <Sidebar />
        </TestWrapper>,
      );
      const sidebar = screen.getByRole("complementary");
      expect(sidebar).toHaveClass("w-16");
      expect(sidebar).not.toHaveClass("w-56");
    });

    it("does not render dashboard label text when collapsed", () => {
      render(
        <TestWrapper isCollapsed={true} initialEntries={["/dashboard"]}>
          <Sidebar />
        </TestWrapper>,
      );
      const sidebar = screen.getByRole("complementary");
      expect(within(sidebar).queryByText("Dashboard")).not.toBeInTheDocument();
    });

    it("does not render campaigns label text when collapsed", () => {
      render(
        <TestWrapper isCollapsed={true} initialEntries={["/campaigns"]}>
          <Sidebar />
        </TestWrapper>,
      );
      const sidebar = screen.getByRole("complementary");
      expect(within(sidebar).queryByText("Campaigns")).not.toBeInTheDocument();
    });
  });

  describe("expanded state", () => {
    it("applies expanded width class when isCollapsed is false", () => {
      render(
        <TestWrapper isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      const sidebar = screen.getByRole("complementary");
      expect(sidebar).toHaveClass("w-56");
      expect(sidebar).not.toHaveClass("w-16");
    });

    it("renders dashboard label when expanded", () => {
      render(
        <TestWrapper isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      expect(screen.getByText("Dashboard")).toBeInTheDocument();
    });

    it("renders campaigns label when expanded", () => {
      render(
        <TestWrapper isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      expect(screen.getByText("Campaigns")).toBeInTheDocument();
    });
  });

  describe("active route - dashboard", () => {
    it("applies active styles to dashboard link when route is /dashboard", () => {
      render(
        <TestWrapper initialEntries={["/dashboard"]} isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      const dashboardLink = screen.getByRole("link", { name: /dashboard/i });
      expect(dashboardLink).toHaveClass("bg-mw-primary-50");
      expect(dashboardLink).toHaveClass("text-mw-primary");
    });

    it("applies active text style to dashboard label when route is /dashboard and expanded", () => {
      render(
        <TestWrapper initialEntries={["/dashboard"]} isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      const dashboardLabel = screen.getByText("Dashboard");
      expect(dashboardLabel).toHaveClass("text-mw-primary-500");
    });

    it("does not apply active styles to campaigns link when route is /dashboard", () => {
      render(
        <TestWrapper initialEntries={["/dashboard"]} isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      const campaignsLink = screen.getByRole("link", { name: /campaigns/i });
      expect(campaignsLink).not.toHaveClass("bg-mw-primary-50");
    });
  });

  describe("active route - campaigns", () => {
    it("applies active styles to campaigns link when route is /campaigns", () => {
      render(
        <TestWrapper initialEntries={["/campaigns"]} isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      const campaignsLink = screen.getByRole("link", { name: /campaigns/i });
      expect(campaignsLink).toHaveClass("bg-mw-primary-50");
      expect(campaignsLink).toHaveClass("text-mw-primary");
    });

    it("applies active text style to campaigns label when route is /campaigns and expanded", () => {
      render(
        <TestWrapper initialEntries={["/campaigns"]} isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      const campaignsLabel = screen.getByText("Campaigns");
      expect(campaignsLabel).toHaveClass("text-mw-primary-500");
    });

    it("does not apply active styles to dashboard link when route is /campaigns", () => {
      render(
        <TestWrapper initialEntries={["/campaigns"]} isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      const dashboardLink = screen.getByRole("link", { name: /dashboard/i });
      expect(dashboardLink).not.toHaveClass("bg-mw-primary-50");
    });
  });

  describe("inactive route", () => {
    it("does not apply active styles to dashboard link when route is root", () => {
      render(
        <TestWrapper initialEntries={["/"]} isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      const dashboardLink = screen.getByRole("link", { name: /dashboard/i });
      expect(dashboardLink).not.toHaveClass("bg-mw-primary-50");
    });

    it("does not apply active styles to campaigns link when route is root", () => {
      render(
        <TestWrapper initialEntries={["/"]} isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      const campaignsLink = screen.getByRole("link", { name: /campaigns/i });
      expect(campaignsLink).not.toHaveClass("bg-mw-primary-50");
    });

    it("does not apply active styles when route is unrelated path", () => {
      render(
        <TestWrapper initialEntries={["/login"]} isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      const dashboardLink = screen.getByRole("link", { name: /dashboard/i });
      const campaignsLink = screen.getByRole("link", { name: /campaigns/i });
      expect(dashboardLink).not.toHaveClass("bg-mw-primary-50");
      expect(campaignsLink).not.toHaveClass("bg-mw-primary-50");
    });
  });

  describe("campaigns link min-w-0 branch", () => {
    it("campaigns link has min-w-0 class", () => {
      render(
        <TestWrapper>
          <Sidebar />
        </TestWrapper>,
      );
      const campaignsLink = screen.getByRole("link", { name: /campaigns/i });
      expect(campaignsLink).toHaveClass("min-w-0");
    });
  });

  describe("accessibility", () => {
    it("exposes navigation landmark", () => {
      render(
        <TestWrapper>
          <Sidebar />
        </TestWrapper>,
      );
      expect(screen.getByRole("navigation")).toBeInTheDocument();
    });

    it("dashboard link is focusable and keyboard navigable", async () => {
      const user = userEvent.setup();
      render(
        <TestWrapper>
          <Sidebar />
        </TestWrapper>,
      );
      const dashboardLink = screen.getByRole("link", { name: /dashboard/i });
      dashboardLink.focus();
      expect(dashboardLink).toHaveFocus();
      await user.tab();
      expect(screen.getByRole("link", { name: /campaigns/i })).toHaveFocus();
    });

    it("campaigns link is focusable", async () => {
      const user = userEvent.setup();
      render(
        <TestWrapper>
          <Sidebar />
        </TestWrapper>,
      );
      await user.tab();
      await user.tab();
      const campaignsLink = screen.getByRole("link", { name: /campaigns/i });
      expect(campaignsLink).toHaveFocus();
    });
  });

  describe("translation", () => {
    it("displays translated dashboard label when expanded", () => {
      render(
        <TestWrapper isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      expect(screen.getByText("Dashboard")).toBeInTheDocument();
    });

    it("displays translated campaigns label when expanded", () => {
      render(
        <TestWrapper isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      expect(screen.getByText("Campaigns")).toBeInTheDocument();
    });
  });

  describe("removed navigation items — Measure and Inventory", () => {
    it("does not render the Measure button", () => {
      render(
        <TestWrapper>
          <Sidebar />
        </TestWrapper>,
      );
      expect(
        document.getElementById("sidebar-nav-measure"),
      ).not.toBeInTheDocument();
    });

    it("does not render the Inventory external-link button", () => {
      render(
        <TestWrapper>
          <Sidebar />
        </TestWrapper>,
      );
      expect(
        document.getElementById("sidebar-nav-inventory"),
      ).not.toBeInTheDocument();
    });

    it("does not render Measure label text when sidebar is expanded", () => {
      render(
        <TestWrapper isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      const sidebar = screen.getByRole("complementary");
      // tolgee mock returns the key itself for unmapped keys, so we check "sidebar.measure"
      expect(
        within(sidebar).queryByText("sidebar.measure"),
      ).not.toBeInTheDocument();
    });

    it("does not render Inventory label text when sidebar is expanded", () => {
      render(
        <TestWrapper isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      const sidebar = screen.getByRole("complementary");
      expect(
        within(sidebar).queryByText("sidebar.inventory"),
      ).not.toBeInTheDocument();
    });

    it("nav contains no buttons (Configurations toggle is commented out)", () => {
      render(
        <TestWrapper isCollapsed={false}>
          <Sidebar />
        </TestWrapper>,
      );
      const nav = screen.getByRole("navigation");
      expect(within(nav).queryAllByRole("button")).toHaveLength(0);
    });

    it("does not render the Configurations toggle (commented out)", () => {
      render(
        <TestWrapper>
          <Sidebar />
        </TestWrapper>,
      );
      expect(
        document.getElementById("sidebar-nav-configurations"),
      ).not.toBeInTheDocument();
    });
  });
});
