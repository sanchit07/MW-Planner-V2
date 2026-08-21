import { render, screen } from "@testing-library/react";
import { describe, it, expect, beforeEach, vi } from "vitest";

import type { AnalyticsExcelData } from "../../analyticsTypes";
import CostingTab from "../CostingTab";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

describe("CostingTab", () => {
  const baseAnalyticsData: AnalyticsExcelData = {
    campaignDetails: {
      currency: "USD",
      campaignId: "camp-123",
      campaignName: "Test Campaign",
      createdOn: "2024-01-01",
      startDate: "2024-01-01",
      endDate: "2024-12-31",
      goal: "Increase Sales",
      kpi: "Conversion Rate",
    },
    costingInventoryRows: [],
  };

  beforeEach(() => {
    vi.clearAllMocks?.();
  });

  it("renders the card and header", () => {
    render(<CostingTab analyticsData={baseAnalyticsData} />);
    expect(
      document.getElementById("media-plan-analytics-costing-card"),
    ).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-analytics-costing-header"),
    ).toHaveTextContent("mediaPlanAnalytics.costing.title");
  });

  it("shows the empty message when there are no rows", () => {
    render(<CostingTab analyticsData={baseAnalyticsData} />);
    expect(
      screen.getByText("mediaPlanAnalytics.costing.noData"),
    ).toBeInTheDocument();
  });

  it("renders a row per inventory with Totals row summing media cost/fee share/total", () => {
    const data: AnalyticsExcelData = {
      ...baseAnalyticsData,
      costingInventoryRows: [
        {
          id: "1",
          name: "Times Square Billboard",
          city: "Manhattan",
          baseCpm: 18.3,
          proposed: 18.3,
          accepted: 18.3,
          impressions: 3_500_000,
          mediaCost: 70000,
          feeShare: 0,
          total: 70000,
        },
        {
          id: "2",
          name: "Michigan Ave Digital Screen",
          city: "Downtown",
          baseCpm: 15.4,
          proposed: 15.4,
          accepted: 15.4,
          impressions: 2_300_000,
          mediaCost: 55000,
          feeShare: 0,
          total: 55000,
        },
      ],
    };
    render(<CostingTab analyticsData={data} />);

    expect(screen.getByText("Times Square Billboard")).toBeInTheDocument();
    expect(screen.getByText("Michigan Ave Digital Screen")).toBeInTheDocument();
    expect(screen.getByText("Manhattan")).toBeInTheDocument();
    expect(screen.getByText("Downtown")).toBeInTheDocument();

    expect(screen.getAllByText("18.30").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("15.40").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("3.5M")).toBeInTheDocument();
    expect(screen.getByText("2.3M")).toBeInTheDocument();

    const totalsRow = document.getElementById(
      "media-plan-analytics-costing-totals-row",
    );
    expect(totalsRow).toHaveTextContent("mediaPlanAnalytics.costing.totals");
    expect(totalsRow).toHaveTextContent("USD 125,000");
  });
});
