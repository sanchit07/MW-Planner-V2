import "@testing-library/jest-dom";
import { configureStore } from "@reduxjs/toolkit";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CampaignDetails from "../CampaignDetails";

// Mock useTranslate
vi.mock("@tolgee/react", () => ({
  useTranslate: (_namespace?: string) => {
    const translations: Record<string, string> = {
      "viewCampaign.tabTitles.goal": "Goal",
      "viewCampaign.tabTitles.inventory": "Inventory",
      "viewCampaign.tabTitles.performance": "Performance",
      "viewCampaign.tabTitles.costBreakdown": "Cost Breakdown",
      "viewCampaign.tabTitles.comments": "Comments",
      "targeting.title": "Targeting",
      "create_campaign.steps.campaign_details": "Campaign Details",
      "viewCampaign.budget": "Budget",
      "viewCampaign.campaignStartDate": "Campaign Start Date",
      "viewCampaign.campaignEndDate": "Campaign End Date",
      "viewCampaign.campaignKeyStakeholders": "Campaign Key Stakeholders",
      "create_campaign.form.agency": "Agency",
      "viewCampaign.planner": "Planner",
      "create_campaign.form.brand": "Brand",
      "viewCampaign.brandCategory": "Brand Category",
      "viewCampaign.MEDIA_OWNER": "Media Owner",
      "viewCampaign.SIZE": "Size",
      "viewCampaign.INVENTORY_TYPE": "Inventory Type",
      "viewCampaign.COUNTRY": "Country",
      "viewCampaign.STATE": "State",
      "viewCampaign.CITY": "City",
      "viewCampaign.VENUE_TYPE": "Venue Type",
      "agency_form.country": "Country",
    };
    return {
      t: (key: string) => translations[key] || key,
    };
  },
  useTolgee: () => ({ getLanguage: () => "en" }),
}));

vi.mock("@utils/currency", () => ({
  formatCurrencyWithLocale: (amount: number | undefined, currency: string) => {
    if (amount === undefined || amount === null) return "--";
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: currency || "USD",
    }).format(amount);
  },
}));

// Mock campaignSlice hooks
const mockGetViewData = vi.fn();
const mockGetSplitCostData = vi.fn();

// Create variables to control the return values
let mockViewDataReturn: {
  data: unknown;
  isLoading: boolean;
  isError: boolean;
} = {
  data: undefined,
  isLoading: false,
  isError: false,
};

let mockSplitCostReturn: {
  data: unknown;
  isLoading: boolean;
  isError: boolean;
} = {
  data: undefined,
  isLoading: false,
  isError: false,
};

vi.mock("@services/campaign/campaignSlice", () => ({
  useLazyViewCampaignQuery: () => [mockGetViewData, mockViewDataReturn],
  useLazySplitCostCampaignQuery: () => [
    mockGetSplitCostData,
    mockSplitCostReturn,
  ],
}));

// Mock child components
vi.mock("../CampaignDetailsTargetingTabInfo", () => ({
  default: () => <div data-testid="targeting-tab">Targeting Tab</div>,
}));

vi.mock("../CampaignGoalView", () => ({
  default: () => <div data-testid="goal-view">Goal View</div>,
}));

vi.mock("../CommentsTab", () => ({
  default: () => <div data-testid="comments-tab">Comments Tab</div>,
}));

vi.mock("../CostBreakdown", () => ({
  default: () => <div data-testid="cost-breakdown">Cost Breakdown</div>,
}));

vi.mock("../InventoryOverview", () => ({
  default: () => <div data-testid="inventory-overview">Inventory Overview</div>,
}));

vi.mock("../PerformanceMetrics", () => ({
  default: () => (
    <div data-testid="performance-metrics">Performance Metrics</div>
  ),
}));

// Mock Loading
vi.mock("@components/ui/Spinner", () => ({
  Loading: ({ overlay }: { overlay: boolean }) => (
    <div data-testid="loading">{overlay && "Loading..."}</div>
  ),
}));

// Mock Dropdown
vi.mock("@components/ui/Dropdown", () => ({
  Dropdown: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="dropdown">{children}</div>
  ),
  DropdownTrigger: ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  ),
  DropdownContent: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="dropdown-content">{children}</div>
  ),
  DropdownItem: ({
    children,
    onClick,
  }: {
    children: React.ReactNode;
    onClick?: () => void;
  }) => <button onClick={onClick}>{children}</button>,
}));

// Mock Button
vi.mock("@components/ui/Button", () => ({
  Button: ({
    children,
    onClick,
  }: {
    children: React.ReactNode;
    onClick?: () => void;
  }) => <button onClick={onClick}>{children}</button>,
}));

// Mock Tabs
vi.mock("@components/ui/Tabs", () => ({
  Tabs: ({
    children,
    value,
  }: {
    children: React.ReactNode;
    value: string;
    onValueChange?: (value: string) => void;
  }) => (
    <div data-testid="tabs" data-value={value}>
      {children}
    </div>
  ),
  TabsList: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="tabs-list">{children}</div>
  ),
  TabsTrigger: ({
    children,
    value,
    onClick,
  }: {
    children: React.ReactNode;
    value: string;
    onClick?: () => void;
  }) => (
    <button data-testid={`tab-trigger-${value}`} onClick={onClick}>
      {children}
    </button>
  ),
  TabsContent: ({
    children,
    value,
  }: {
    children: React.ReactNode;
    value: string;
  }) => <div data-testid={`tab-content-${value}`}>{children}</div>,
}));

const createMockStore = () => {
  return configureStore({
    reducer: {
      // Add minimal reducers to satisfy Redux requirements
      campaign: (state = {}) => state,
      campaignApi: (state = null) => state,
    },
  });
};

const TestWrapper = ({ children }: { children: React.ReactNode }) => {
  const store = createMockStore();
  return <Provider store={store}>{children}</Provider>;
};

describe("CampaignDetails", () => {
  const user = userEvent.setup();

  const mockCampaignData = {
    success: true,
    data: {
      id: "test-campaign-id",
      name: "Test Campaign",
      currency: "USD",
      campaignDetail: {
        country: "USA",
        budget: 100000,
        startDate: "2024-01-01",
        endDate: "2024-12-31",
      },
      keyStakeholderDetail: {
        agency: "Test Agency",
        planner: "Test Planner",
        brand: "Test Brand",
        brandCategory: "Test Category",
      },
      costBreakdown: {
        totalCost: 107000,
      },
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();

    mockGetViewData.mockReturnValue({
      unwrap: async () => mockCampaignData,
    });
    mockGetSplitCostData.mockReturnValue({
      unwrap: async () => ({
        success: true,
        data: [],
      }),
    });

    // Reset return values to defaults
    mockViewDataReturn = {
      data: undefined,
      isLoading: false,
      isError: false,
    };
    mockSplitCostReturn = {
      data: undefined,
      isLoading: false,
      isError: false,
    };
  });

  describe("Rendering", () => {
    it("should render component", () => {
      mockViewDataReturn = {
        data: mockCampaignData,
        isLoading: false,
        isError: false,
      };

      render(
        <TestWrapper>
          <CampaignDetails campaignId="test-id" />
        </TestWrapper>,
      );

      expect(screen.getByText("Campaign Details")).toBeInTheDocument();
    });

    it("should display campaign details section", () => {
      mockViewDataReturn = {
        data: mockCampaignData,
        isLoading: false,
        isError: false,
      };

      render(
        <TestWrapper>
          <CampaignDetails campaignId="test-id" />
        </TestWrapper>,
      );

      expect(screen.getByText("Campaign Details")).toBeInTheDocument();
      expect(screen.getByText("USA")).toBeInTheDocument();
      expect(screen.getAllByText("$100,000.00").length).toBeGreaterThan(0);
    });

    it("should display key stakeholders section", () => {
      mockViewDataReturn = {
        data: mockCampaignData,
        isLoading: false,
        isError: false,
      };

      render(
        <TestWrapper>
          <CampaignDetails campaignId="test-id" />
        </TestWrapper>,
      );

      expect(screen.getByText("Campaign Key Stakeholders")).toBeInTheDocument();
      expect(screen.getByText("Test Agency")).toBeInTheDocument();
      expect(screen.getByText("Test Planner")).toBeInTheDocument();
    });

    it("should display cost split section", () => {
      mockViewDataReturn = {
        data: mockCampaignData,
        isLoading: false,
        isError: false,
      };

      render(
        <TestWrapper>
          <CampaignDetails campaignId="test-id" />
        </TestWrapper>,
      );

      expect(screen.getByText("viewCampaign.costSplit")).toBeInTheDocument();
    });

    it("should render all tabs", () => {
      mockViewDataReturn = {
        data: mockCampaignData,
        isLoading: false,
        isError: false,
      };

      render(
        <TestWrapper>
          <CampaignDetails campaignId="test-id" />
        </TestWrapper>,
      );

      expect(screen.getByTestId("tab-trigger-Goal")).toBeInTheDocument();
      expect(screen.getByTestId("tab-trigger-Targeting")).toBeInTheDocument();
      expect(screen.getByTestId("tab-trigger-Inventory")).toBeInTheDocument();
      expect(screen.getByTestId("tab-trigger-Performance")).toBeInTheDocument();
      expect(
        screen.getByTestId("tab-trigger-Cost Breakdown"),
      ).toBeInTheDocument();
      expect(screen.getByTestId("tab-trigger-Comments")).toBeInTheDocument();
    });
  });

  describe("Loading State", () => {
    it("should show loading overlay when loading", () => {
      mockViewDataReturn = {
        data: undefined,
        isLoading: true,
        isError: false,
      };

      render(
        <TestWrapper>
          <CampaignDetails campaignId="test-id" />
        </TestWrapper>,
      );

      expect(screen.getByTestId("loading")).toBeInTheDocument();
    });
  });

  describe("Error State", () => {
    it("should show error message when data fails to load", () => {
      mockViewDataReturn = {
        data: undefined,
        isLoading: false,
        isError: true,
      };

      render(
        <TestWrapper>
          <CampaignDetails campaignId="test-id" />
        </TestWrapper>,
      );

      expect(
        screen.getByText("viewCampaign.errorLoadingData"),
      ).toBeInTheDocument();
    });
  });

  describe("API Calls", () => {
    it("should call getViewData on mount", () => {
      render(
        <TestWrapper>
          <CampaignDetails campaignId="test-id" />
        </TestWrapper>,
      );

      expect(mockGetViewData).toHaveBeenCalledWith("test-id");
    });

    it("should call getSplitCostData on mount", () => {
      render(
        <TestWrapper>
          <CampaignDetails campaignId="test-id" />
        </TestWrapper>,
      );

      expect(mockGetSplitCostData).toHaveBeenCalled();
    });
  });

  describe("Tab Content", () => {
    it("should render goal tab content", () => {
      mockViewDataReturn = {
        data: mockCampaignData,
        isLoading: false,
        isError: false,
      };

      render(
        <TestWrapper>
          <CampaignDetails campaignId="test-id" />
        </TestWrapper>,
      );

      expect(screen.getByTestId("goal-view")).toBeInTheDocument();
    });

    it("should render targeting tab content", async () => {
      mockViewDataReturn = {
        data: mockCampaignData,
        isLoading: false,
        isError: false,
      };

      render(
        <TestWrapper>
          <CampaignDetails campaignId="test-id" />
        </TestWrapper>,
      );

      const targetingTab = screen.getByTestId("tab-trigger-Targeting");
      await user.click(targetingTab);

      await waitFor(() => {
        expect(screen.getByTestId("targeting-tab")).toBeInTheDocument();
      });
    });
  });

  describe("Edge Cases", () => {
    it("should handle missing campaignId", () => {
      render(
        <TestWrapper>
          <CampaignDetails campaignId="" />
        </TestWrapper>,
      );

      // Should not crash
      expect(screen.queryByText("Campaign Details")).not.toBeInTheDocument();
    });

    it("should handle missing campaign data", () => {
      mockViewDataReturn = {
        data: undefined,
        isLoading: false,
        isError: false,
      };

      render(
        <TestWrapper>
          <CampaignDetails campaignId="test-id" />
        </TestWrapper>,
      );

      expect(
        screen.getByText("viewCampaign.errorLoadingData"),
      ).toBeInTheDocument();
    });

    it("should handle missing keyStakeholderDetail", () => {
      const dataWithoutStakeholders = {
        ...mockCampaignData,
        data: {
          ...mockCampaignData.data,
          keyStakeholderDetail: undefined,
        },
      };

      mockViewDataReturn = {
        data: dataWithoutStakeholders,
        isLoading: false,
        isError: false,
      };

      render(
        <TestWrapper>
          <CampaignDetails campaignId="test-id" />
        </TestWrapper>,
      );

      expect(screen.getByText("Campaign Key Stakeholders")).toBeInTheDocument();
    });
  });
});
