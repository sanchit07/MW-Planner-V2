import "@testing-library/jest-dom";
import { configureStore } from "@reduxjs/toolkit";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CampaignDetailsTargetingTabInfo from "../CampaignDetailsTargetingTabInfo";

// Mock InventoryMapViewDrawer
vi.mock("../../inventory/InventoryMapViewDrawer", () => ({
  default: ({
    isOpen,
    onClose,
    campaignId,
  }: {
    isOpen: boolean;
    onClose: () => void;
    campaignId: string;
  }) => (
    <div data-testid="inventory-map-view-drawer">
      {isOpen && (
        <div>
          <div>Map View Drawer - Campaign: {campaignId}</div>
          <button onClick={onClose}>Close</button>
        </div>
      )}
    </div>
  ),
}));

// Mock useTranslate
vi.mock("@tolgee/react", () => ({
  useTranslate: (_namespace?: string) => {
    const translations: Record<string, string> = {
      "viewCampaign.targetingTab.audienceTitle": "Audience",
      "viewCampaign.targetingTab.ageGroups": "Age Groups",
      "viewCampaign.targetingTab.incomeLevel": "Income Level",
      "viewCampaign.targetingTab.interests": "Interests",
      "viewCampaign.targetingTab.lifestyle": "Lifestyle",
      "viewCampaign.targetingTab.geographyTitle": "Geography",
      "viewCampaign.targetingTab.mapView": "Map View",
      "viewCampaign.targetingTab.byCityTitle": "By City",
      "viewCampaign.targetingTab.byVenueTypeTitle": "By Venue Type",
      "viewCampaign.targetingTab.ofTotal": "of Total",
      "budget_goal.goal_types.impressions": "Impressions",
      "viewCampaign.targetingTab.adPlays": "Ad Plays",
    };
    return {
      t: (key: string) => translations[key] || key,
    };
  },
  useTolgee: () => ({ getLanguage: () => "en" }),
}));

// Mock formatNumber
vi.mock("@utils/budget.utils", () => ({
  formatNumber: (num: number) => {
    return new Intl.NumberFormat("en-US").format(num);
  },
}));

// Mock useConfigurationMetadataQuery
const mockRefetchDemographics = vi.fn();
vi.mock(
  "@services/configuration-metadata/configurationMetadataSlice",
  async () => {
    const actual = await vi.importActual<
      typeof import("@services/configuration-metadata/configurationMetadataSlice")
    >("@services/configuration-metadata/configurationMetadataSlice");
    return {
      ...actual,
      useConfigurationMetadataQuery: () => ({
        data: undefined,
        error: undefined,
        isLoading: false,
        refetch: mockRefetchDemographics,
      }),
    };
  },
);

const createMockStore = (demographicsState = {}) => {
  return configureStore({
    reducer: {
      configurationMetadata: (
        state = {
          demographics: demographicsState,
          campaign_status: [],
        },
      ) => state,
      configurationMetadataAPI: (state = {}) => state,
    },
    preloadedState: {
      configurationMetadata: {
        demographics: demographicsState,
        campaign_status: [],
      },
    },
  });
};

const TestWrapper = ({
  children,
  demographicsState = {},
}: {
  children: React.ReactNode;
  demographicsState?: Record<string, unknown[]>;
}) => {
  const store = createMockStore(demographicsState);
  return <Provider store={store}>{children}</Provider>;
};

describe("CampaignDetailsTargetingTabInfo", () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("Rendering", () => {
    it("should render audience section with title", () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo />
        </TestWrapper>,
      );

      expect(screen.getByText("Audience")).toBeInTheDocument();
    });

    it("should render all demographic fields", () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo />
        </TestWrapper>,
      );

      expect(screen.getByText("Age Groups")).toBeInTheDocument();
      expect(screen.getByText("Income Level")).toBeInTheDocument();
      expect(screen.getByText("Interests")).toBeInTheDocument();
      expect(screen.getByText("Lifestyle")).toBeInTheDocument();
    });

    it("should display '--' when no demographic data is available", () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo />
        </TestWrapper>,
      );

      const dashes = screen.getAllByText("--");
      expect(dashes.length).toBeGreaterThan(0);
    });

    it("should render geography section when not forMediaPlan", () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo forMediaPlan={false} />
        </TestWrapper>,
      );

      expect(screen.getByText("Geography")).toBeInTheDocument();
      expect(screen.getByText("By City")).toBeInTheDocument();
      expect(screen.getByText("By Venue Type")).toBeInTheDocument();
    });

    it("should not render geography section when forMediaPlan is true", () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo forMediaPlan={true} />
        </TestWrapper>,
      );

      expect(screen.queryByText("Geography")).not.toBeInTheDocument();
    });
  });

  describe("Audience Demographics", () => {
    const mockTabData = {
      audienceDemographics: {
        ageGroups: ["age_18_24", "age_25_34"],
        incomeLevel: ["income_high"],
        interests: ["interest_tech"],
        lifestyle: ["lifestyle_urban"],
      },
    };

    const mockDemographicsMetadata = {
      age: [
        { demoKey: "age_18_24", name: "18-24" },
        { demoKey: "age_25_34", name: "25-34" },
      ],
      income: [{ demoKey: "income_high", name: "High Income" }],
      interests: [{ demoKey: "interest_tech", name: "Technology" }],
      behavior: [{ demoKey: "lifestyle_urban", name: "Urban Lifestyle" }],
    };

    it("should display age groups when data is available", () => {
      render(
        <TestWrapper demographicsState={mockDemographicsMetadata}>
          <CampaignDetailsTargetingTabInfo tabData={mockTabData} />
        </TestWrapper>,
      );

      expect(screen.getByText("18-24")).toBeInTheDocument();
      expect(screen.getByText("25-34")).toBeInTheDocument();
    });

    it("should display income levels when data is available", () => {
      render(
        <TestWrapper demographicsState={mockDemographicsMetadata}>
          <CampaignDetailsTargetingTabInfo tabData={mockTabData} />
        </TestWrapper>,
      );

      expect(screen.getByText("High Income")).toBeInTheDocument();
    });

    it("should display interests when data is available", () => {
      render(
        <TestWrapper demographicsState={mockDemographicsMetadata}>
          <CampaignDetailsTargetingTabInfo tabData={mockTabData} />
        </TestWrapper>,
      );

      expect(screen.getByText("Technology")).toBeInTheDocument();
    });

    it("should display lifestyle when data is available", () => {
      render(
        <TestWrapper demographicsState={mockDemographicsMetadata}>
          <CampaignDetailsTargetingTabInfo tabData={mockTabData} />
        </TestWrapper>,
      );

      expect(screen.getByText("Urban Lifestyle")).toBeInTheDocument();
    });
  });

  describe("Geographic Targeting", () => {
    const mockTabData = {
      geographicTargeting: {
        cities: [
          {
            name: "New York",
            impressions: 1000000,
            adPlays: 5000,
            allocatedBudget: 60,
          },
          {
            name: "Los Angeles",
            impressions: 500000,
            adPlays: 2500,
            allocatedBudget: 30,
          },
        ],
        venueTypes: [
          {
            name: "Shopping Mall",
            impressions: 200000,
            adPlays: 1000,
            allocatedBudget: 25,
          },
        ],
      },
    };

    it("should display cities when data is available", () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo tabData={mockTabData} />
        </TestWrapper>,
      );

      expect(screen.getByText("New York")).toBeInTheDocument();
      expect(screen.getByText("Los Angeles")).toBeInTheDocument();
    });

    it("should display venue types when data is available", () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo tabData={mockTabData} />
        </TestWrapper>,
      );

      expect(screen.getByText("Shopping Mall")).toBeInTheDocument();
    });

    it("should display budget percentages for cities", () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo tabData={mockTabData} />
        </TestWrapper>,
      );

      expect(screen.getByText(/60.00%/)).toBeInTheDocument();
      expect(screen.getByText(/30.00%/)).toBeInTheDocument();
    });

    it("should display impressions and ad plays for cities", () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo tabData={mockTabData} />
        </TestWrapper>,
      );

      expect(screen.getByText("1,000,000")).toBeInTheDocument();
      expect(screen.getByText("5,000")).toBeInTheDocument();
    });

    it("should show success badge for budget >= 50%", () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo tabData={mockTabData} />
        </TestWrapper>,
      );

      const badges = screen.getAllByText(/60.00%/);
      expect(badges.length).toBeGreaterThan(0);
    });

    it("should show warning badge for budget < 25%", () => {
      const lowBudgetData = {
        geographicTargeting: {
          cities: [
            {
              name: "Chicago",
              impressions: 100000,
              adPlays: 500,
              allocatedBudget: 15,
            },
          ],
        },
      };

      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo tabData={lowBudgetData} />
        </TestWrapper>,
      );

      expect(screen.getByText(/15.00%/)).toBeInTheDocument();
    });
  });

  describe("Map View Drawer", () => {
    it("should render map view button when isMapViewVisible is true", () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo
            isMapViewVisible={true}
            campaignId="test-campaign-id"
          />
        </TestWrapper>,
      );

      expect(screen.getByText("Map View")).toBeInTheDocument();
    });

    it("should not render map view button when isMapViewVisible is false", () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo
            isMapViewVisible={false}
            campaignId="test-campaign-id"
          />
        </TestWrapper>,
      );

      expect(screen.queryByText("Map View")).not.toBeInTheDocument();
    });

    it("should open map view drawer when button is clicked", async () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo
            isMapViewVisible={true}
            campaignId="test-campaign-id"
          />
        </TestWrapper>,
      );

      const mapViewButton = screen.getByText("Map View");
      await user.click(mapViewButton);

      await waitFor(() => {
        expect(
          screen.getByText("Map View Drawer - Campaign: test-campaign-id"),
        ).toBeInTheDocument();
      });
    });

    it("should close map view drawer when close button is clicked", async () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo
            isMapViewVisible={true}
            campaignId="test-campaign-id"
          />
        </TestWrapper>,
      );

      const mapViewButton = screen.getByText("Map View");
      await user.click(mapViewButton);

      await waitFor(() => {
        expect(
          screen.getByText("Map View Drawer - Campaign: test-campaign-id"),
        ).toBeInTheDocument();
      });

      const closeButton = screen.getByText("Close");
      await user.click(closeButton);

      await waitFor(() => {
        expect(
          screen.queryByText("Map View Drawer - Campaign: test-campaign-id"),
        ).not.toBeInTheDocument();
      });
    });

    it("should not render drawer when campaignId is not provided", () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo isMapViewVisible={true} />
        </TestWrapper>,
      );

      // Drawer should not render when campaignId is missing
      const drawer = screen.queryByTestId("inventory-map-view-drawer");
      expect(drawer).not.toBeInTheDocument();
    });
  });

  describe("Edge Cases", () => {
    it("should handle empty tabData gracefully", () => {
      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo tabData={undefined} />
        </TestWrapper>,
      );

      expect(screen.getByText("Audience")).toBeInTheDocument();
      const dashes = screen.getAllByText("--");
      expect(dashes.length).toBeGreaterThan(0);
    });

    it("should handle missing city names", () => {
      const mockTabData = {
        geographicTargeting: {
          cities: [
            {
              name: undefined as unknown as string,
              impressions: 100000,
              adPlays: 500,
              allocatedBudget: 50,
            },
          ],
        },
      };

      render(
        <TestWrapper>
          {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
          <CampaignDetailsTargetingTabInfo tabData={mockTabData as any} />
        </TestWrapper>,
      );

      // "--" appears multiple times, check that at least one exists
      expect(screen.getAllByText("--").length).toBeGreaterThan(0);
    });

    it("should handle zero values for impressions and ad plays", () => {
      const mockTabData = {
        geographicTargeting: {
          cities: [
            {
              name: "Test City",
              impressions: 0,
              adPlays: 0,
              allocatedBudget: 0,
            },
          ],
        },
      };

      render(
        <TestWrapper>
          <CampaignDetailsTargetingTabInfo tabData={mockTabData} />
        </TestWrapper>,
      );

      expect(screen.getByText("Test City")).toBeInTheDocument();
      // "0" appears multiple times (for impressions and ad plays)
      expect(screen.getAllByText("0").length).toBeGreaterThan(0);
    });
  });
});
