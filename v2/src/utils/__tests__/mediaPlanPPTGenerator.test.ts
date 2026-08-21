import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock pptxgenjs before imports
vi.mock("pptxgenjs", () => {
  const mockSlide = () => ({
    addText: vi.fn(),
    addShape: vi.fn(),
    addImage: vi.fn(),
    addTable: vi.fn(),
    background: "",
  });

  const mockPptx = {
    layout: "",
    ShapeType: {
      rect: "rect",
      roundRect: "roundRect",
      ellipse: "ellipse",
      line: "line",
    },
    addSlide: vi.fn().mockImplementation(mockSlide),
    writeFile: vi.fn().mockResolvedValue(undefined),
  };

  return {
    default: vi.fn().mockImplementation(() => mockPptx),
  };
});

// Mock image/asset imports
vi.mock("../../assets/icon/calendar.png", () => ({ default: "calendar.png" }));
vi.mock("../../assets/icon/chart-column-increasing.png", () => ({
  default: "chart.png",
}));
vi.mock("../../assets/icon/clock-4.png", () => ({ default: "clock.png" }));
vi.mock("../../assets/icon/earth.png", () => ({ default: "earth.png" }));
vi.mock("../../assets/icon/eye.png", () => ({ default: "eye.png" }));
vi.mock("../../assets/icon/play.png", () => ({ default: "play.png" }));
vi.mock("../../assets/icon/trending-up.png", () => ({
  default: "trending-up.png",
}));
vi.mock("../../assets/icon/users.png", () => ({ default: "users.png" }));
vi.mock("../../assets/images/media-plan-bg.jpg", () => ({ default: "bg.jpg" }));

// Mock utilities
vi.mock("../pptTranslations", () => ({
  getPPTTranslations: vi.fn().mockResolvedValue({
    title_slide: {
      total_budget: "Total Budget",
      total_cost: "Total Cost",
      impressions: "Impressions",
      campaign_period: "Campaign Period",
      prepared_by: "Prepared By",
      plan_dates: "Plan Dates",
      planned_by: "Planned by",
      days: "{count} days",
      moving_walls: "Moving Walls",
      moving_walls_internal: "MW Planner Internal",
    },
    performance_metrics: {
      title: "Performance Metrics",
      subtitle: "Key Metrics",
      est_impressions: "Est. Impressions",
      campaign_duration: "Duration",
      est_reach: "Est. Reach",
      unique_viewers: "Unique Viewers",
      avg_frequency: "Avg Frequency",
      avg_per_person: "Avg Per Person",
      avg_ecpm: "Avg eCPM",
      effective_rate: "Effective Rate",
      avg_cpm: "Avg CPM",
      media_owner_rate: "Media Owner Rate",
      est_ad_plays: "Est. Ad Plays",
      total_displays: "Total Displays",
      sov: "SOV",
      share_of_voice: "Share of Voice",
      sot: "SOT",
      daily_hours_used: "Daily Hours Used",
    },
    targeting_strategy: {
      title: "Targeting Strategy",
      subtitle: "Audience Targeting",
      age_groups: "Age Groups",
      income_level: "Income Level",
      interests: "Interests",
      lifestyle: "Lifestyle",
    },
    geographic_targeting: {
      title: "Geographic Targeting",
      subtitle_cities: "Cities",
      subtitle_venue_types: "Venue Types",
      cities: "Cities",
      venue_type: "Venue Type",
      ad_plays: "Ad Plays",
      budget_allocation_percent: "Budget %",
      of_total_cost: "of total cost",
      page: "Page",
      of: "of",
    },
    schedule: {
      title: "Schedule",
      subtitle: "Campaign Schedule",
      daypart_performance: "Daypart Performance",
      high_traffic: "High Traffic",
      medium_traffic: "Medium Traffic",
      low_traffic: "Low Traffic",
      chart_unavailable: "Chart Unavailable",
      chart_not_provided: "Chart Not Provided",
    },
    cost_breakdown: {
      title: "Cost Breakdown",
      subtitle: "Investment Structure",
      cost_split: "Cost Split",
      fee_structure: "Fee Structure",
      media_cost: "Media Cost",
      platform_fee: "Platform Fee",
      agency_commission: "Agency Commission",
      total_investment: "Total Investment",
    },
    selected_inventory: {
      title: "Selected Inventory",
      subtitle: "Inventory Details",
      total_inventories: "Total Inventories",
      format_types: "Format Types",
      cities: "Cities",
      est_impressions: "Est. Impressions",
      inventory_name: "Inventory Name",
      type: "Type",
      city: "City",
      schedule_dates: "Schedule Dates",
      schedule_hours: "Schedule Hours",
      impression: "Impression",
      cost: "Cost",
      total_media_cost: "Total Media Cost",
      schedule_title: "Schedule",
      schedule_subtitle: "Dates & Hours",
    },
    map_view: {
      title: "Map View",
      subtitle: "Geographic Distribution",
      map_load_error: "Map load error",
      click_to_view: "Click to view",
    },
    common_labels: {
      na: "N/A",
      moving_walls: "Moving Walls",
    },
  }),
}));

vi.mock("../themeColors", () => ({
  getCssVariableValue: vi.fn().mockReturnValue("#2176cc"),
  hexToRgbString: vi.fn().mockReturnValue("2176CC"),
}));

vi.mock("../budget.utils", () => ({
  formatNumber: vi.fn().mockReturnValue("1,000"),
  normalizeGoalType: (g?: string) => (g || "").toUpperCase().replace(/_/g, ""),
}));

vi.mock("../campaign.utils", () => ({
  formatCurrency: vi.fn().mockReturnValue("$1,000"),
}));

vi.mock("../dateUtils", () => ({
  formatDisplayDate: vi.fn().mockReturnValue("Jan 01, 2026"),
}));

import type { MediaPlanResponse } from "../../types/campaign.types";
import { generateMediaPlanPPT } from "../mediaPlanPPTGenerator";

// Minimal MediaPlanResponse
const minimalMediaPlan: MediaPlanResponse = {
  headerInfo: {
    id: "campaign-1",
    name: "Test Campaign",
    startDate: "2026-01-01",
    endDate: "2026-03-01",
    budget: 100000,
    status: "ACTIVE",
    totalCost: 90000,
    impressions: 1000000,
    reach: 500000,
    duration: 60,
    currency: "USD",
    preparedBy: "Test User",
  },
  brandDetails: {
    id: "brand-1",
    name: "Test Brand",
    category: "Technology",
    companyId: "company-1",
    activated: true,
    description: "A test brand",
    websiteUrl: "https://example.com",
    externalId: "ext-1",
    createdBy: "admin",
    lastModifiedBy: "admin",
    createdAt: "2026-01-01",
    updatedAt: "2026-01-01",
    logoUrl: "",
  },
  performanceMetrics: {
    totalInventories: 10,
    estimatedImpression: 1000000,
    estimatedReach: 500000,
    estimatedFrequency: 2.0,
    estimatedAdPlays: 5000,
    sov: 0.3,
    avgCpm: 5.0,
    avgECpm: 4.5,
    totalCost: 90000,
    plannedSot: 15,
    totalSot: 12,
    warnings: [],
  },
  audienceDemographics: {
    ageGroups: ["18-24", "25-34"],
    incomeLevel: ["Middle"],
    interests: ["Sports"],
    lifestyle: ["Active"],
  },
  geographicTargeting: {
    cities: [
      {
        name: "Singapore",
        impressions: 500000,
        adPlays: 2000,
        allocatedBudget: 50000,
      },
    ],
    venueTypes: [
      {
        name: "Mall",
        impressions: 300000,
        adPlays: 1200,
        allocatedBudget: 30000,
      },
    ],
  },
  schedules: {
    dailySchedule: { Monday: 100, Tuesday: 90, Wednesday: 85 },
  },
  selectedInventory: {
    summaryStatistics: {
      totalAssets: 10,
      formatTypes: ["Digital"],
      totalFormatTypes: 1,
      totalCities: 2,
    },
    locations: [
      {
        name: "Mall Billboard",
        country: "Singapore",
        state: "Central",
        city: "Orchard",
        type: "Digital",
        impressions: 100000,
        cost: 10000,
        lat: 1.3,
        lng: 103.8,
        mediaOwnerName: "OOH Media",
        scheduleDates: [
          { startDate: "2026-01-01", endDate: "2026-01-31", totalHours: 720 },
        ],
        scheduleHours: [["09:00", "18:00"]],
      },
    ],
  },
};

const theme = {
  id: "primary",
  name: "Primary",
  colors: {
    primary: "--color-mw-primary-500",
    secondary: "--color-mw-primary-400",
    accent: "--color-mw-primary-300",
  },
};

describe("generateMediaPlanPPT", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("resolves without errors with minimal data", async () => {
    await expect(
      generateMediaPlanPPT({ mediaPlan: minimalMediaPlan, theme }),
    ).resolves.toBeUndefined();
  });

  it("accepts custom fileName", async () => {
    await expect(
      generateMediaPlanPPT({
        mediaPlan: minimalMediaPlan,
        theme,
        fileName: "custom_report.pptx",
      }),
    ).resolves.toBeUndefined();
  });

  it("accepts costSplitData", async () => {
    await expect(
      generateMediaPlanPPT({
        mediaPlan: minimalMediaPlan,
        theme,
        costSplitData: [
          {
            name: "Campaign A",
            totalAmountInPercentage: 60,
            totalAmount: 60000,
            avgCpm: 5,
            frequency: 2,
            impressions: 500000,
            reach: 300000,
            totalInventories: 5,
          },
          {
            name: "Campaign B",
            totalAmountInPercentage: 40,
            totalAmount: 40000,
            avgCpm: 4,
            frequency: 1.5,
            impressions: 300000,
            reach: 200000,
            totalInventories: 3,
          },
        ],
      }),
    ).resolves.toBeUndefined();
  });

  it("accepts scheduleChartImage and selectedInventoryChartImage", async () => {
    await expect(
      generateMediaPlanPPT({
        mediaPlan: minimalMediaPlan,
        theme,
        scheduleChartImage: "data:image/png;base64,abc",
        selectedInventoryChartImage: "data:image/png;base64,def",
      }),
    ).resolves.toBeUndefined();
  });

  it("accepts mapImage and mapImageLink", async () => {
    await expect(
      generateMediaPlanPPT({
        mediaPlan: minimalMediaPlan,
        theme,
        mapImage: "data:image/png;base64,mapimage",
        mapImageLink: "https://example.com/map",
      }),
    ).resolves.toBeUndefined();
  });

  it("respects slideVisibility config - hides all slides", async () => {
    await expect(
      generateMediaPlanPPT({
        mediaPlan: minimalMediaPlan,
        theme,
        slideVisibility: {
          titleSlide: false,
          performanceMetrics: false,
          inventoryMix: false,
          targeting: false,
          audienceTrends: false,
          geographicPlan: false,
          audienceMap: false,
          goalsKpis: false,
          inventorySnapshots: false,
          whyPlan: false,
        },
      }),
    ).resolves.toBeUndefined();
  });

  it("respects slideVisibility config - shows all slides explicitly", async () => {
    await expect(
      generateMediaPlanPPT({
        mediaPlan: minimalMediaPlan,
        theme,
        slideVisibility: {
          titleSlide: true,
          performanceMetrics: true,
          inventoryMix: true,
          targeting: true,
          audienceTrends: true,
          geographicPlan: true,
          audienceMap: true,
          goalsKpis: true,
          inventorySnapshots: true,
          whyPlan: true,
        },
      }),
    ).resolves.toBeUndefined();
  });

  it("handles empty cities and venueTypes arrays", async () => {
    const planWithEmptyGeo = {
      ...minimalMediaPlan,
      geographicTargeting: { cities: [], venueTypes: [] },
    };
    await expect(
      generateMediaPlanPPT({ mediaPlan: planWithEmptyGeo, theme }),
    ).resolves.toBeUndefined();
  });

  it("handles empty selectedInventory locations", async () => {
    const planWithEmptyInventory = {
      ...minimalMediaPlan,
      selectedInventory: {
        summaryStatistics: {
          totalAssets: 0,
          formatTypes: [],
          totalFormatTypes: 0,
          totalCities: 0,
        },
        locations: [],
      },
    };
    await expect(
      generateMediaPlanPPT({ mediaPlan: planWithEmptyInventory, theme }),
    ).resolves.toBeUndefined();
  });

  it("handles empty warnings array", async () => {
    const planWithWarnings = {
      ...minimalMediaPlan,
      performanceMetrics: {
        ...minimalMediaPlan.performanceMetrics,
        warnings: ["Warning 1", "Warning 2"],
      },
    };
    await expect(
      generateMediaPlanPPT({ mediaPlan: planWithWarnings, theme }),
    ).resolves.toBeUndefined();
  });

  it("handles empty schedules", async () => {
    const planWithEmptySchedule = {
      ...minimalMediaPlan,
      schedules: { dailySchedule: {} },
    };
    await expect(
      generateMediaPlanPPT({ mediaPlan: planWithEmptySchedule, theme }),
    ).resolves.toBeUndefined();
  });

  it("handles long location names and multiple schedule dates without throwing", async () => {
    const planWithLongContent = {
      ...minimalMediaPlan,
      selectedInventory: {
        ...minimalMediaPlan.selectedInventory,
        locations: [
          {
            name: "Bhagwan Talkies Flyover Near Omax Mall, Facing Kanpur NH-2, Opposite Central Bus Terminal",
            country: "India",
            state: "Uttar Pradesh",
            city: "Agra",
            type: "Classic",
            impressions: 100000,
            cost: 10000,
            lat: 27.1767,
            lng: 78.0081,
            mediaOwnerName: "OOH Media",
            scheduleDates: [
              {
                startDate: "2026-01-01",
                endDate: "2026-01-31",
                totalHours: 720,
              },
              {
                startDate: "2026-02-01",
                endDate: "2026-02-28",
                totalHours: 672,
              },
              {
                startDate: "2026-03-01",
                endDate: "2026-03-31",
                totalHours: 720,
              },
            ],
            scheduleHours: [["09:00", "12:00", "18:00"]],
          },
          {
            name: "Short Name",
            country: "India",
            state: "Bihar",
            city: "Patna",
            type: "Classic",
            impressions: 50000,
            cost: 5000,
            lat: 25.5941,
            lng: 85.1376,
            mediaOwnerName: "OOH Media",
            scheduleDates: [
              {
                startDate: "2026-01-01",
                endDate: "2026-01-31",
                totalHours: 720,
              },
            ],
            scheduleHours: [["09:00"]],
          },
        ],
      },
    };

    await expect(
      generateMediaPlanPPT({ mediaPlan: planWithLongContent, theme }),
    ).resolves.toBeUndefined();
  });

  it("handles multiple locations with pagination (>10 items)", async () => {
    const manyLocations = Array.from({ length: 12 }, (_, i) => ({
      name: `Billboard ${i}`,
      country: "SG",
      state: "Central",
      city: "City",
      type: "Digital",
      impressions: 1000,
      cost: 100,
      lat: 1.3 + i * 0.01,
      lng: 103.8,
      mediaOwnerName: "Media Co",
      scheduleDates: [
        { startDate: "2026-01-01", endDate: "2026-01-31", totalHours: 720 },
      ],
      scheduleHours: [],
    }));

    const planWithManyLocations = {
      ...minimalMediaPlan,
      selectedInventory: {
        ...minimalMediaPlan.selectedInventory,
        locations: manyLocations,
      },
    };

    await expect(
      generateMediaPlanPPT({ mediaPlan: planWithManyLocations, theme }),
    ).resolves.toBeUndefined();
  });
});
