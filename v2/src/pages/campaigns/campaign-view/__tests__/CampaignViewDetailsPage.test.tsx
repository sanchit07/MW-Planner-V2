import { configureStore } from "@reduxjs/toolkit";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { BrowserRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CampaignViewDetailsPage from "../CampaignViewDetailsPage";

// Mock useParams
const mockParams = { campaignId: "test-campaign-id" };
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useParams: () => mockParams,
    useNavigate: () => vi.fn(),
  };
});

// Mock useTranslate - return key as value so tests can query by key; add common labels for tab/error text
vi.mock("@tolgee/react", () => ({
  useTranslate: (_namespace?: string) => {
    const translations: Record<string, string> = {
      ID: "CID",
      CID: "CID",
      "viewCampaign.ID": "CID",
      "viewCampaign.tabs.campaignDetails": "Campaign Details",
      "viewCampaign.tabs.campaignHistory": "Campaign History",
      "viewCampaign.errorLoadingData": "Error loading campaign data.",
      "viewCampaign.defaultCampaignName": "Campaign 1",
    };
    return {
      t: (key: string) => translations[key] ?? key,
    };
  },
}));

// Mock campaignSlice hooks
let mockViewData: unknown = undefined;
let mockIsLoading = false;
let mockIsError = false;

vi.mock("@services/campaign/campaignSlice", () => ({
  useLazyViewCampaignQuery: () => {
    const trigger = vi.fn().mockReturnValue({
      unwrap: async () => {
        return mockViewData;
      },
    });
    return [
      trigger,
      {
        get data() {
          return mockViewData;
        },
        get isLoading() {
          return mockIsLoading;
        },
        get isError() {
          return mockIsError;
        },
      },
    ];
  },
  useSubmitForReviewMutation: () => [
    vi.fn().mockReturnValue({
      unwrap: async () => ({
        success: true,
        data: {},
      }),
    }),
  ],
}));

// Mock CampaignDetails
vi.mock("../CampaignDetails", () => ({
  default: ({ campaignId }: { campaignId: string }) => (
    <div data-testid="campaign-details">Campaign Details: {campaignId}</div>
  ),
}));

// Mock CampaignHistory
vi.mock("../CampaignHistory", () => ({
  default: ({ campaignId }: { campaignId: string }) => (
    <div data-testid="campaign-history">Campaign History: {campaignId}</div>
  ),
}));

// Mock PageHeader
vi.mock("@components/PageHeader", () => ({
  default: ({
    title,
    description,
    actions,
    leftAction,
  }: {
    title: string;
    description: React.ReactNode;
    actions: React.ReactNode;
    leftAction: React.ReactNode;
  }) => (
    <div data-testid="page-header">
      <div>{title}</div>
      <div>{description}</div>
      <div>{actions}</div>
      <div>{leftAction}</div>
    </div>
  ),
}));

// Mock Loading
vi.mock("@components/ui/Spinner", () => ({
  Loading: ({ overlay }: { overlay: boolean }) => (
    <div data-testid="loading">{overlay && "Loading..."}</div>
  ),
}));

// Mock StatusBadge
vi.mock("@components/ui/StatusBadge", () => ({
  StatusBadge: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="status-badge">{children}</div>
  ),
}));

// Mock Badge
vi.mock("@components/ui/Badge", () => ({
  Badge: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="badge">{children}</div>
  ),
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

// Mock Dropdown
vi.mock("@components/ui/Dropdown", () => ({
  Dropdown: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="dropdown">{children}</div>
  ),
  DropdownTrigger: ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  ),
}));

// Mock CampaignActionsDropdownContent
vi.mock("../../components/CampaignActionsDropdownContent", () => ({
  CampaignActionsDropdownContent: () => (
    <div data-testid="campaign-actions-dropdown">Actions</div>
  ),
}));

// Mock Tabs
vi.mock("@components/ui/Tabs", () => ({
  Tabs: ({
    children,
    value,
  }: {
    children: React.ReactNode;
    value: string;
    onValueChange: (value: string) => void;
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
      campaign: (state = {}) => state, // Minimal reducer to satisfy Redux
    },
  });
};

const TestWrapper = ({ children }: { children: React.ReactNode }) => {
  const store = createMockStore();
  return (
    <Provider store={store}>
      <BrowserRouter>{children}</BrowserRouter>
    </Provider>
  );
};

describe("CampaignViewDetailsPage", () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    mockViewData = {
      success: true,
      data: {
        id: "test-campaign-id",
        planNumber: "PLN-0001",
        name: "Test Campaign",
        status: "DRAFT",
      },
    };
    mockIsLoading = false;
    mockIsError = false;
    mockParams.campaignId = "test-campaign-id";
  });

  describe("Rendering", () => {
    it("should render page header", () => {
      render(
        <TestWrapper>
          <CampaignViewDetailsPage />
        </TestWrapper>,
      );

      expect(screen.getByTestId("page-header")).toBeInTheDocument();
    });

    it("should display campaign name in header", () => {
      render(
        <TestWrapper>
          <CampaignViewDetailsPage />
        </TestWrapper>,
      );

      expect(screen.getByText("Test Campaign")).toBeInTheDocument();
    });

    it("should display campaign ID in header", () => {
      render(
        <TestWrapper>
          <CampaignViewDetailsPage />
        </TestWrapper>,
      );

      expect(screen.getByText(/CID: PLN-0001/)).toBeInTheDocument();
    });

    it("should render tabs", () => {
      render(
        <TestWrapper>
          <CampaignViewDetailsPage />
        </TestWrapper>,
      );

      expect(screen.getByTestId("tabs")).toBeInTheDocument();
    });

    it("should render campaign details tab", () => {
      render(
        <TestWrapper>
          <CampaignViewDetailsPage />
        </TestWrapper>,
      );

      expect(
        screen.getByTestId("tab-trigger-campaign-details"),
      ).toBeInTheDocument();
      expect(screen.getByText("Campaign Details")).toBeInTheDocument();
    });

    it("should render campaign history tab", () => {
      render(
        <TestWrapper>
          <CampaignViewDetailsPage />
        </TestWrapper>,
      );

      expect(
        screen.getByTestId("tab-trigger-campaign-history"),
      ).toBeInTheDocument();
      expect(screen.getByText("Campaign History")).toBeInTheDocument();
    });
  });

  describe("Loading State", () => {
    it("should show loading overlay when loading", () => {
      mockIsLoading = true;
      mockIsError = false;
      mockViewData = undefined;

      render(
        <TestWrapper>
          <CampaignViewDetailsPage />
        </TestWrapper>,
      );

      expect(screen.getByTestId("loading")).toBeInTheDocument();
    });
  });

  describe("Error State", () => {
    it("should show error message when data fails to load", () => {
      mockIsLoading = false;
      mockIsError = true;
      mockViewData = undefined;

      render(
        <TestWrapper>
          <CampaignViewDetailsPage />
        </TestWrapper>,
      );

      expect(
        screen.getByText("Error loading campaign data."),
      ).toBeInTheDocument();
    });
  });

  describe("Tab Switching", () => {
    it("should switch tabs when tab trigger is clicked", async () => {
      render(
        <TestWrapper>
          <CampaignViewDetailsPage />
        </TestWrapper>,
      );

      const historyTab = screen.getByTestId("tab-trigger-campaign-history");
      await user.click(historyTab);

      await waitFor(() => {
        expect(
          screen.getByTestId("tab-content-campaign-history"),
        ).toBeInTheDocument();
      });
    });
  });

  describe("Edge Cases", () => {
    it("should handle missing campaignId from URL params", () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      mockParams.campaignId = undefined as any;
      mockViewData = undefined;

      render(
        <TestWrapper>
          <CampaignViewDetailsPage />
        </TestWrapper>,
      );

      // Should not crash - component won't render campaign data if campaignId is missing
      expect(screen.queryByTestId("page-header")).not.toBeInTheDocument();
    });

    it("should handle missing campaign name", () => {
      mockViewData = {
        success: true,
        data: {
          id: "test-campaign-id",
          name: undefined,
          status: "DRAFT",
        },
      };

      render(
        <TestWrapper>
          <CampaignViewDetailsPage />
        </TestWrapper>,
      );

      expect(screen.getByText("Campaign 1")).toBeInTheDocument();
    });
  });
});
