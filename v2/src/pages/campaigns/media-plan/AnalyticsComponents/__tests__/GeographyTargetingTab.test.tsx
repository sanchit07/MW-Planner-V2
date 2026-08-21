import { render, screen } from "@testing-library/react";
import { describe, it, expect, beforeEach, vi } from "vitest";

import type {
  AnalyticsExcelData,
  GeographyTargetingRow,
} from "../../analyticsTypes";
import GeographyTargetingTab from "../GeographyTargetingTab";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string, params?: Record<string, unknown>) =>
      params ? `${key}:${JSON.stringify(params)}` : key,
  }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

describe("GeographyTargetingTab", () => {
  const baseAnalyticsData: AnalyticsExcelData = { geographyTargeting: [] };

  beforeEach(() => {
    vi.clearAllMocks?.();
  });

  it("renders the card and shows the empty message when there are no rows", () => {
    render(<GeographyTargetingTab analyticsData={baseAnalyticsData} />);
    expect(
      document.getElementById("media-plan-analytics-geography-targeting-card"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("mediaPlanAnalytics.geographyTargeting.noData"),
    ).toBeInTheDocument();
  });

  it("renders country/state/city rows with formatted metrics", () => {
    const rows: GeographyTargetingRow[] = [
      {
        id: "1",
        level: "country",
        depth: 0,
        name: "United States",
        inventories: 3,
        impressions: 7_700_000,
        reach: 3_100_000,
        frequency: 2.5,
        ecpm: 58.23,
      },
      {
        id: "2",
        level: "state",
        depth: 1,
        name: "New York",
        inventories: 2,
        impressions: 5_400_000,
        reach: 2_400_000,
        frequency: 2.22,
        ecpm: 51.44,
      },
      {
        id: "3",
        level: "city",
        depth: 2,
        name: "Manhattan",
        inventories: 1,
        impressions: 3_500_000,
        reach: 1_600_000,
        frequency: 2.22,
        ecpm: 45.09,
      },
    ];
    render(
      <GeographyTargetingTab
        analyticsData={{
          geographyTargeting: rows,
          campaignDetails: {
            campaignName: "",
            campaignId: "",
            createdOn: "",
            startDate: "",
            endDate: "",
            goal: "",
            kpi: "",
            currency: "USD",
          },
        }}
      />,
    );

    expect(screen.getByText("United States")).toBeInTheDocument();
    expect(screen.getByText("New York")).toBeInTheDocument();
    expect(screen.getByText("Manhattan")).toBeInTheDocument();
    expect(screen.getByText("7.70M")).toBeInTheDocument();
    expect(screen.getByText("3.10M")).toBeInTheDocument();
    expect(screen.getByText("2.50")).toBeInTheDocument();
    expect(screen.getByText("58.23")).toBeInTheDocument();

    // City row is indented further than the country row.
    const countryCell = document
      .getElementById("media-plan-analytics-geography-targeting-row-1")
      ?.querySelector("td");
    const cityCell = document
      .getElementById("media-plan-analytics-geography-targeting-row-3")
      ?.querySelector("td");
    const countryPadding = parseInt(
      (countryCell as HTMLElement).style.paddingLeft,
      10,
    );
    const cityPadding = parseInt(
      (cityCell as HTMLElement).style.paddingLeft,
      10,
    );
    expect(cityPadding).toBeGreaterThan(countryPadding);
  });
});
