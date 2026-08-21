import { render, screen } from "@testing-library/react";
import { describe, it, expect, beforeEach, vi } from "vitest";

import type { AnalyticsExcelData } from "../../analyticsTypes";
import InventoryDetailsTab from "../InventoryDetailsTab";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string, params?: Record<string, unknown>) =>
      params ? `${key}:${JSON.stringify(params)}` : key,
  }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

describe("InventoryDetailsTab", () => {
  const baseAnalyticsData: AnalyticsExcelData = {
    inventoryDetails: [],
    campaignDetails: {
      campaignName: "Test",
      campaignId: "1",
      createdOn: "",
      startDate: "",
      endDate: "",
      goal: "",
      kpi: "",
      currency: "USD",
    },
  };

  beforeEach(() => {
    vi.clearAllMocks?.();
  });

  it("renders the card and header with row count", () => {
    render(<InventoryDetailsTab analyticsData={baseAnalyticsData} />);
    expect(
      document.getElementById("media-plan-analytics-inventory-details-card"),
    ).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-analytics-inventory-details-header"),
    ).toHaveTextContent(
      'mediaPlanAnalytics.inventoryDetails.title:{"count":0}',
    );
  });

  it("shows the empty message when inventoryDetails is empty", () => {
    render(<InventoryDetailsTab analyticsData={baseAnalyticsData} />);
    expect(
      screen.getByText("mediaPlanAnalytics.inventoryDetails.noData"),
    ).toBeInTheDocument();
  });

  it("renders a row per inventory item with Classic/Digital channel labels", () => {
    const data: AnalyticsExcelData = {
      ...baseAnalyticsData,
      inventoryDetails: [
        {
          id: "1",
          type: "classic",
          billboardName: "Times Square Billboard",
          format: "Digital",
          city: "Manhattan",
          mediaOwner: "MW Planner Internal",
          impressions: 3_500_000,
          playsPerDay: 115000,
          cpm: 18.3,
        },
        {
          id: "2",
          type: "digital",
          billboardName: "Michigan Ave Digital Screen",
          format: "Portrait",
          city: "Downtown",
          mediaOwner: "MW Planner Internal",
          impressions: 2_300_000,
          playsPerDay: 78000,
          cpm: 15.4,
        },
      ],
    };
    render(<InventoryDetailsTab analyticsData={data} />);

    expect(
      document.getElementById("media-plan-analytics-inventory-details-header"),
    ).toHaveTextContent(
      'mediaPlanAnalytics.inventoryDetails.title:{"count":2}',
    );

    expect(screen.getByText("Times Square Billboard")).toBeInTheDocument();
    expect(screen.getByText("Michigan Ave Digital Screen")).toBeInTheDocument();

    expect(
      screen.getByText("media_plan.inventory_mix.channel_classic_ooh"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("media_plan.inventory_mix.channel_digital_ooh"),
    ).toBeInTheDocument();

    expect(screen.getByText("Manhattan")).toBeInTheDocument();
    expect(screen.getByText("Downtown")).toBeInTheDocument();

    expect(screen.getByText("3.5M")).toBeInTheDocument();
    expect(screen.getByText("2.3M")).toBeInTheDocument();
    // classic rows show "-" for plays/day and CPM, not the raw values
    expect(screen.getByText("78,000")).toBeInTheDocument();
    expect(screen.queryByText("18.30")).not.toBeInTheDocument();
    expect(screen.getByText("15.40")).toBeInTheDocument();
  });

  it("shows CPS (from spotRate) instead of CPM for SOV/AD_PLAYS goals", () => {
    const data: AnalyticsExcelData = {
      ...baseAnalyticsData,
      inventoryDetails: [
        {
          id: "1",
          type: "classic",
          billboardName: "Times Square Billboard",
          cpm: 18.3,
          spotRate: 5.5,
        },
      ],
    };
    render(<InventoryDetailsTab analyticsData={data} goalType="SOV" />);
    expect(
      screen.getByText('mediaPlanAnalytics.columns.cps:{"currency":"USD"}'),
    ).toBeInTheDocument();
    expect(screen.getByText("5.50")).toBeInTheDocument();
    expect(screen.queryByText("18.30")).not.toBeInTheDocument();
  });

  it("renders a placeholder box when no thumbnail is available", () => {
    const data: AnalyticsExcelData = {
      ...baseAnalyticsData,
      inventoryDetails: [
        {
          id: "1",
          type: "classic",
          billboardName: "Board",
        },
      ],
    };
    render(<InventoryDetailsTab analyticsData={data} />);
    expect(
      document
        .getElementById("media-plan-analytics-inventory-details-row-1")
        ?.querySelector("img"),
    ).not.toBeInTheDocument();
  });

  it("renders the real thumbnail image when available", () => {
    const data: AnalyticsExcelData = {
      ...baseAnalyticsData,
      inventoryDetails: [
        {
          id: "1",
          type: "classic",
          billboardName: "Board",
          thumbnailUrl: "https://example.com/thumb.png",
        },
      ],
    };
    render(<InventoryDetailsTab analyticsData={data} />);
    const img = document
      .getElementById("media-plan-analytics-inventory-details-row-1")
      ?.querySelector("img");
    expect(img).toHaveAttribute("src", "https://example.com/thumb.png");
  });
});
