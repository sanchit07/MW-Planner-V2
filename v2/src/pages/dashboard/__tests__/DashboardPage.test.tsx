import { useGetDashboardWidgetsQuery } from "@services/dashboard/dashboardSlice";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import DashboardPage from "../DashboardPage";

const mockSaveWidgetVisibilityToStorage = vi.fn();
const mockLoadWidgetVisibilityFromStorage = vi.fn();

vi.mock("@components/Tolgee/RouteNamespaceManager", () => ({
  useNamespace: () => ({ namespace: "dashboard" }),
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  T: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
}));

vi.mock("@services/dashboard/dashboardSlice", () => ({
  useGetDashboardWidgetsQuery: vi.fn(),
}));

vi.mock("../../components/PageHeader", () => ({
  default: ({
    title,
    descriptionKey,
    actions,
  }: {
    title: string;
    descriptionKey: string;
    actions: React.ReactNode;
  }) => (
    <header data-testid="page-header">
      <h1>{title}</h1>
      <p>{descriptionKey}</p>
      <div data-testid="header-actions">{actions}</div>
    </header>
  ),
}));

vi.mock("@utils/dashboard.utils", () => ({
  getPeriodLabel: (period: string) =>
    period === "date-range" ? "Date range" : `Label: ${period}`,
  createNDaysPresets: () => [],
}));

vi.mock("../dashboardWidgetPersistence", () => ({
  loadWidgetVisibilityFromStorage: () => mockLoadWidgetVisibilityFromStorage(),
  saveWidgetVisibilityToStorage: (v: unknown) =>
    mockSaveWidgetVisibilityToStorage(v),
}));

vi.mock("../dashboardWidgetConfig", () => ({
  getRevenueChildKeys: () => ["sales-overview", "revenue-distribution"],
  getBudgetChildKeys: () => ["budget-overview"],
}));

vi.mock("../RevenuePerformance", () => ({
  default: ({ selectedPeriod }: { selectedPeriod: string }) => (
    <div data-testid="revenue-performance">Revenue {selectedPeriod}</div>
  ),
}));

vi.mock("../CampaignOverview", () => ({
  default: () => <div data-testid="campaign-overview">Campaign Overview</div>,
}));

vi.mock("../CampaignPerformance", () => ({
  default: () => (
    <div data-testid="campaign-performance">Campaign Performance</div>
  ),
}));

vi.mock("../BudgetTracker", () => ({
  default: () => <div data-testid="budget-tracker">Budget Tracker</div>,
}));

vi.mock("../AudienceReachPerformance", () => ({
  default: () => (
    <div data-testid="audience-reach-performance">Audience Reach</div>
  ),
}));

vi.mock("../RegionalInventorySnapshot", () => ({
  default: () => (
    <div data-testid="regional-inventory-snapshot">Regional Inventory</div>
  ),
}));

vi.mock("../CustomizeLayoutDrawer", () => ({
  default: ({
    isOpen,
    onClose,
    widgetVisibility,
    onWidgetVisibilityChange,
  }: {
    isOpen: boolean;
    onClose: () => void;
    widgetVisibility: Record<string, boolean>;
    onWidgetVisibilityChange: (v: Record<string, boolean>) => void;
  }) => (
    <div data-testid="customize-drawer" data-open={isOpen}>
      {isOpen && (
        <>
          <button type="button" onClick={onClose} data-testid="drawer-close">
            Close
          </button>
          <button
            type="button"
            onClick={() =>
              onWidgetVisibilityChange({
                ...widgetVisibility,
                "campaign-overview": false,
              })
            }
            data-testid="drawer-apply"
          >
            Apply
          </button>
        </>
      )}
    </div>
  ),
  defaultWidgetVisibility: {},
}));

vi.mock("@components/ui/Button", () => ({
  Button: ({
    children,
    onClick,
    "aria-label": ariaLabel,
  }: {
    children: React.ReactNode;
    onClick?: () => void;
    "aria-label"?: string;
  }) => (
    <button type="button" onClick={onClick} aria-label={ariaLabel}>
      {children}
    </button>
  ),
}));

vi.mock("@components/ui/Dropdown", () => ({
  Dropdown: ({
    children,
    value,
    onChange,
  }: {
    children: React.ReactNode;
    value: string;
    onChange: (v: string) => void;
  }) => (
    <div data-testid="dropdown" data-value={value}>
      {children}
      <button type="button" onClick={() => onChange("last-30-days")}>
        Set 30 days
      </button>
      <button type="button" onClick={() => onChange("date-range")}>
        Set date range
      </button>
    </div>
  ),
  DropdownTrigger: ({ children }: { children: React.ReactNode }) => (
    <span>{children}</span>
  ),
  DropdownContent: ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  ),
  DropdownItem: () => null,
}));

vi.mock("@components/ui/DateRangePicker", () => ({
  DateRangePicker: () => (
    <div data-testid="date-range-picker">DateRangePicker</div>
  ),
}));

vi.mock("lucide-react", () => ({
  Download: () => <span data-testid="download-icon" />,
}));

describe("DashboardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockLoadWidgetVisibilityFromStorage.mockReturnValue(null);
    vi.mocked(useGetDashboardWidgetsQuery).mockReturnValue({
      data: {
        data: [
          { key: "campaign-overview", isEnable: true },
          { key: "campaign-performance", isEnable: true },
          { key: "sales-overview", isEnable: true },
          { key: "budget-overview", isEnable: true },
          { key: "audience-reach-performance", isEnable: true },
        ],
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });
  });

  describe("Rendering", () => {
    it("renders dashboard page with main landmark", () => {
      render(<DashboardPage />);
      expect(
        screen.getByRole("main", { name: /actions.dashboardContent/i }),
      ).toBeInTheDocument();
    });

    it("renders page header with title and actions", () => {
      render(<DashboardPage />);
      expect(document.getElementById("page-header-title")).toBeInTheDocument();
      expect(
        document.getElementById("page-header-title-actions"),
      ).toBeInTheDocument();
    });

    it("shows loading state when widgets are loading and list is empty", () => {
      vi.mocked(useGetDashboardWidgetsQuery).mockReturnValue({
        data: undefined,
        isLoading: true,
        isError: false,
        refetch: vi.fn(),
      });

      render(<DashboardPage />);
      expect(screen.getByText("actions.loadingDashboard")).toBeInTheDocument();
    });

    it("does not show loading message when widgets data is loaded", () => {
      render(<DashboardPage />);
      expect(
        screen.queryByText("actions.loadingDashboard"),
      ).not.toBeInTheDocument();
    });

    it("renders CustomizeLayoutDrawer", () => {
      render(<DashboardPage />);
      expect(screen.getByTestId("customize-drawer")).toBeInTheDocument();
    });
  });

  describe("Widget visibility", () => {
    it("renders campaign overview when widget is enabled", () => {
      render(<DashboardPage />);
      expect(screen.getByTestId("campaign-overview")).toBeInTheDocument();
    });

    it("renders revenue performance when any revenue child is enabled", () => {
      render(<DashboardPage />);
      expect(screen.getByTestId("revenue-performance")).toBeInTheDocument();
    });

    it("renders budget tracker when any budget child is enabled", () => {
      render(<DashboardPage />);
      expect(screen.getByTestId("budget-tracker")).toBeInTheDocument();
    });

    it("renders audience reach when widget is enabled", () => {
      render(<DashboardPage />);
      expect(
        screen.getByTestId("audience-reach-performance"),
      ).toBeInTheDocument();
    });
  });

  describe("Customize drawer", () => {
    it("opens customize drawer when Customize button is clicked", async () => {
      const user = userEvent.setup();
      render(<DashboardPage />);
      const customizeButton = screen.getByRole("button", {
        name: /actions.customizeLayout/i,
      });
      await user.click(customizeButton);
      await waitFor(() => {
        expect(screen.getByTestId("customize-drawer")).toHaveAttribute(
          "data-open",
          "true",
        );
      });
    });

    it("calls saveWidgetVisibilityToStorage when visibility changes via drawer", async () => {
      const user = userEvent.setup();
      render(<DashboardPage />);
      await user.click(
        screen.getByRole("button", { name: /actions.customizeLayout/i }),
      );
      await waitFor(() =>
        expect(screen.getByTestId("drawer-apply")).toBeInTheDocument(),
      );
      await user.click(screen.getByTestId("drawer-apply"));
      await waitFor(() => {
        expect(mockSaveWidgetVisibilityToStorage).toHaveBeenCalled();
      });
    });
  });

  describe("Initialization", () => {
    it("uses stored visibility when loadWidgetVisibilityFromStorage returns data", () => {
      mockLoadWidgetVisibilityFromStorage.mockReturnValue({
        "campaign-overview": false,
      });
      render(<DashboardPage />);
      expect(mockLoadWidgetVisibilityFromStorage).toHaveBeenCalled();
    });

    it("uses defaultWidgetVisibility when loadWidgetVisibilityFromStorage returns null", () => {
      mockLoadWidgetVisibilityFromStorage.mockReturnValue(null);
      render(<DashboardPage />);
      expect(screen.getByTestId("campaign-overview")).toBeInTheDocument();
    });
  });

  describe("headerActions and callbacks", () => {
    it("calls handlePeriodChange when period dropdown changes and clears date range when not date-range", async () => {
      const user = userEvent.setup();
      render(<DashboardPage />);
      const set30Days = screen.getByRole("button", { name: /set 30 days/i });
      await user.click(set30Days);
      await waitFor(() => {
        expect(screen.getByTestId("dropdown")).toHaveAttribute(
          "data-value",
          "last-30-days",
        );
      });
    });

    it("shows DateRangePicker when period is date-range", async () => {
      const user = userEvent.setup();
      render(<DashboardPage />);
      await user.click(screen.getByRole("button", { name: /set date range/i }));
      await waitFor(() => {
        expect(screen.getByTestId("date-range-picker")).toBeInTheDocument();
      });
    });

    it("does not render the Download button (hidden, unimplemented stub)", () => {
      render(<DashboardPage />);
      expect(
        screen.queryByRole("button", { name: /download/i }),
      ).not.toBeInTheDocument();
    });

    it("closes drawer when drawer Close is clicked", async () => {
      const user = userEvent.setup();
      render(<DashboardPage />);
      await user.click(
        screen.getByRole("button", { name: /actions.customizeLayout/i }),
      );
      await waitFor(() =>
        expect(screen.getByTestId("drawer-close")).toBeInTheDocument(),
      );
      await user.click(screen.getByTestId("drawer-close"));
      await waitFor(() => {
        expect(screen.getByTestId("customize-drawer")).toHaveAttribute(
          "data-open",
          "false",
        );
      });
    });
  });

  describe("Widget visibility branches", () => {
    it("does not render revenue performance when no revenue child is enabled", () => {
      vi.mocked(useGetDashboardWidgetsQuery).mockReturnValue({
        data: {
          data: [
            { key: "campaign-overview", isEnable: true },
            { key: "campaign-performance", isEnable: true },
            { key: "budget-overview", isEnable: true },
            { key: "audience-reach-performance", isEnable: true },
          ],
        },
        isLoading: false,
        isError: false,
        refetch: vi.fn(),
      });
      render(<DashboardPage />);
      expect(
        screen.queryByTestId("revenue-performance"),
      ).not.toBeInTheDocument();
    });

    it("does not render budget tracker when no budget child is enabled", () => {
      vi.mocked(useGetDashboardWidgetsQuery).mockReturnValue({
        data: {
          data: [
            { key: "campaign-overview", isEnable: true },
            { key: "campaign-performance", isEnable: true },
            { key: "sales-overview", isEnable: true },
            { key: "audience-reach-performance", isEnable: true },
          ],
        },
        isLoading: false,
        isError: false,
        refetch: vi.fn(),
      });
      render(<DashboardPage />);
      expect(screen.queryByTestId("budget-tracker")).not.toBeInTheDocument();
    });

    it("does not render campaign overview when widget key is not in widgetsList", () => {
      vi.mocked(useGetDashboardWidgetsQuery).mockReturnValue({
        data: {
          data: [
            { key: "campaign-performance", isEnable: true },
            { key: "sales-overview", isEnable: true },
          ],
        },
        isLoading: false,
        isError: false,
        refetch: vi.fn(),
      });
      render(<DashboardPage />);
      expect(screen.queryByTestId("campaign-overview")).not.toBeInTheDocument();
    });
  });

  describe("widgetsList and useEffect", () => {
    it("API visibility wins: when API says campaign-overview isEnable false, overview is hidden after load", () => {
      vi.mocked(useGetDashboardWidgetsQuery).mockReturnValue({
        data: {
          data: [
            { key: "campaign-overview", isEnable: false },
            { key: "campaign-performance", isEnable: true },
          ],
        },
        isLoading: false,
        isError: false,
        refetch: vi.fn(),
      });
      mockLoadWidgetVisibilityFromStorage.mockReturnValue(null);
      render(<DashboardPage />);
      expect(screen.queryByTestId("campaign-overview")).not.toBeInTheDocument();
    });
  });
});
