import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { MemoryRouter, useParams } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import ViewMediaPlanPage from "../ViewMediaPlanPage";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useParams: vi.fn(),
  };
});

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const mockShowSuccess = vi.fn();
const mockShowError = vi.fn();
vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({
    showSuccess: mockShowSuccess,
    showError: mockShowError,
  }),
}));

const mockUseMediaPlanData = vi.fn();
vi.mock("../useMediaPlanData", () => ({
  useMediaPlanData: (campaignId: string | undefined) =>
    mockUseMediaPlanData(campaignId),
}));

vi.mock("@services/public-access/publicAccessSlice", () => ({
  useGeneratePublicTokenMutation: () => [
    vi.fn().mockReturnValue({
      unwrap: () =>
        Promise.resolve({ success: true, data: { publicToken: "token" } }),
    }),
    {},
  ],
  useLazyGetPublicInventoriesQuery: () => [
    vi.fn().mockReturnValue({
      unwrap: () => Promise.resolve({ success: true, data: { content: [] } }),
    }),
  ],
}));

vi.mock("@components/PageHeader", () => ({
  default: ({
    title,
    leftAction,
  }: {
    title: string;
    leftAction?: React.ReactNode;
  }) => (
    <div data-testid="page-header">
      <span data-testid="page-header-title">{title}</span>
      <div data-testid="page-header-left">{leftAction}</div>
    </div>
  ),
}));

vi.mock("../MediaPlanHeader", () => ({
  default: ({
    onViewTypeChange,
    onDownload,
    onShare,
  }: {
    onViewTypeChange: (v: string) => void;
    onDownload: () => void;
    onShare: () => void;
  }) => (
    <div data-testid="media-plan-header">
      <button type="button" onClick={() => onViewTypeChange("presentation")}>
        Presentation
      </button>
      <button type="button" onClick={() => onViewTypeChange("analytics")}>
        Analytics
      </button>
      <button type="button" onClick={onDownload}>
        Download
      </button>
      <button type="button" onClick={onShare}>
        Share
      </button>
    </div>
  ),
}));

vi.mock("../MediaPlanTitleSlide", () => ({
  default: () => <div data-testid="media-plan-title-slide">TitleSlide</div>,
}));
vi.mock("../MediaPlanPerformanceMetrics", () => ({
  default: () => <div data-testid="media-plan-performance">Performance</div>,
}));
vi.mock("../MediaPlanInventoryMix", () => ({
  default: () => <div data-testid="media-plan-inventory-mix">InventoryMix</div>,
}));
vi.mock("../MediaPlanAudienceTrends", () => ({
  default: () => (
    <div data-testid="media-plan-audience-trends">AudienceTrends</div>
  ),
}));
vi.mock("../MediaPlanGeographicPlan", () => ({
  default: () => (
    <div data-testid="media-plan-geographic-plan">GeographicPlan</div>
  ),
}));
vi.mock("../MediaPlanAudienceMap", () => ({
  default: () => <div data-testid="media-plan-audience-map">AudienceMap</div>,
}));
vi.mock("../MediaPlanTargeting", () => ({
  default: () => <div data-testid="media-plan-targeting">Targeting</div>,
}));
vi.mock("../MediaPlanGoalsKpis", () => ({
  default: () => <div data-testid="media-plan-goals-kpis">GoalsKpis</div>,
}));
vi.mock("../MediaPlanInventorySnapshots", () => ({
  default: () => (
    <div data-testid="media-plan-inventory-snapshots">Snapshots</div>
  ),
}));
vi.mock("../MediaPlanWhyThisPlanWorks", () => ({
  default: () => <div data-testid="media-plan-why-plan">WhyPlan</div>,
}));
vi.mock("@services/campaign/campaignSlice", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  useGetCampaignQuery: () => ({ data: undefined }),
}));
vi.mock("@utils/geofencing-pois", () => ({
  extractGeofencingPOIs: () => [],
}));
vi.mock("../../inventory/plan-summary/useReachCurve", () => ({
  useReachCurve: () => ({
    status: "idle",
    overallReach: [],
    labels: [],
    inventoryCount: 0,
    refetch: () => {},
  }),
}));
vi.mock("../ShareModalDrawer", () => ({
  default: () => null,
}));
vi.mock("../AnalyticsView", () => ({
  default: () => <div data-testid="analytics-view">Analytics</div>,
}));
vi.mock("../../components/CampaignActionsDropdownContent", () => ({
  CampaignActionsDropdownContent: () => null,
}));

vi.mock("@components/ui/Mapbox", () => ({
  default: () => <div data-testid="mapbox">Map</div>,
}));

const minimalMediaPlanData = {
  mediaPlan: {
    headerInfo: { id: "camp-1", name: "Test Campaign", status: "DRAFT" },
    brandDetails: {},
    audienceDemographics: null,
    schedules: [],
    performanceMetrics: null,
    geographicTargeting: null,
  },
  costSplitByState: [],
  costSplitByInventoryType: [],
  costSplitByCity: [],
  costSplitByVenueType: [],
  forecastData: null,
  selectedInventory: { locations: [], summaryStatistics: null },
  headerInfo: { id: "camp-1", name: "Test Campaign", status: "DRAFT" },
  performanceMetrics: null,
  geographicTargeting: { cities: [], venueTypes: [] },
  priceSummary: null,
  isLoading: false,
  isError: false,
  refetch: vi.fn(),
};

function renderViewMediaPlanPage(campaignId = "camp-1") {
  vi.mocked(useParams).mockReturnValue({ campaignId });
  return render(
    <MemoryRouter>
      <ViewMediaPlanPage />
    </MemoryRouter>,
  );
}

describe("ViewMediaPlanPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseMediaPlanData.mockReturnValue(null);
  });

  describe("loading state", () => {
    it("shows loading message when useMediaPlanData returns null", () => {
      mockUseMediaPlanData.mockReturnValue(null);
      renderViewMediaPlanPage("camp-1");
      expect(screen.getByText("media_plan.errors.loading")).toBeInTheDocument();
    });

    it("shows loading message when useMediaPlanData returns isLoading true", () => {
      mockUseMediaPlanData.mockReturnValue({
        ...minimalMediaPlanData,
        isLoading: true,
      });
      renderViewMediaPlanPage("camp-1");
      expect(screen.getByText("media_plan.errors.loading")).toBeInTheDocument();
    });
  });

  describe("error state", () => {
    it("shows error message when useMediaPlanData returns isError true", () => {
      mockUseMediaPlanData.mockReturnValue({
        ...minimalMediaPlanData,
        isError: true,
      });
      renderViewMediaPlanPage("camp-1");
      expect(
        screen.getByText("media_plan.errors.error_loading"),
      ).toBeInTheDocument();
    });
  });

  describe("success state", () => {
    beforeEach(() => {
      mockUseMediaPlanData.mockReturnValue(minimalMediaPlanData);
    });

    it("renders page with id campaigns-page", () => {
      renderViewMediaPlanPage("camp-1");
      expect(document.getElementById("campaigns-page")).toBeInTheDocument();
    });

    it("renders PageHeader with campaign name from headerInfo", () => {
      renderViewMediaPlanPage("camp-1");
      expect(screen.getByTestId("page-header")).toBeInTheDocument();
      expect(screen.getByTestId("page-header-title")).toHaveTextContent(
        "Test Campaign",
      );
    });

    it("renders presentation view by default with title slide and sections", () => {
      renderViewMediaPlanPage("camp-1");
      expect(
        document.getElementById("media-plan-presentation-view"),
      ).toBeInTheDocument();
      expect(screen.getByTestId("media-plan-title-slide")).toBeInTheDocument();
      expect(screen.getByTestId("media-plan-performance")).toBeInTheDocument();
      expect(
        document.getElementById("media-plan-audience-map-section"),
      ).toBeInTheDocument();
    });

    it("back button navigates to /campaigns when clicked", async () => {
      const user = userEvent.setup();
      renderViewMediaPlanPage("camp-1");
      const leftAction = screen.getByTestId("page-header-left");
      const backTarget = leftAction.querySelector("div");
      expect(backTarget).toBeInTheDocument();
      await user.click(backTarget!);
      expect(mockNavigate).toHaveBeenCalledWith("/campaigns");
    });

    it("opens share modal when Share is clicked", async () => {
      const user = userEvent.setup();
      renderViewMediaPlanPage("camp-1");
      await user.click(screen.getByRole("button", { name: /share/i }));
      expect(mockNavigate).not.toHaveBeenCalled();
    });

    it("switches to analytics view when Analytics is clicked", async () => {
      const user = userEvent.setup();
      renderViewMediaPlanPage("camp-1");
      await user.click(screen.getByRole("button", { name: /analytics/i }));
      expect(screen.getByTestId("analytics-view")).toBeInTheDocument();
      expect(
        document.getElementById("media-plan-analytics-view"),
      ).toBeInTheDocument();
    });

    it("uses campaignId from useParams for useMediaPlanData", () => {
      renderViewMediaPlanPage("camp-123");
      expect(mockUseMediaPlanData).toHaveBeenCalledWith("camp-123");
    });
  });
});
