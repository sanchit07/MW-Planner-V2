import { render, screen } from "@testing-library/react";
import { describe, it, expect, beforeEach, vi } from "vitest";

import type { AnalyticsExcelData } from "../../analyticsTypes";
import CampaignPlanTab from "../CampaignPlanTab";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("@components/ui/AgGridTable/AgGridTable", () => ({
  AgGridTable: ({
    rowData = [],
    columnDefs = [],
    emptyMessage = "No data available",
  }: {
    rowData?: unknown[];
    columnDefs?: { colId?: string; field?: string; headerName?: string }[];
    emptyMessage?: string;
  }) => (
    <div data-testid="ag-grid-table">
      <div role="rowgroup">
        {columnDefs.map((col) => (
          <span key={col.colId ?? col.field} role="columnheader">
            {col.headerName ?? col.colId ?? col.field}
          </span>
        ))}
      </div>
      {rowData.length === 0 ? (
        <div>{emptyMessage}</div>
      ) : (
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        rowData.map((row: any, i: number) => (
          <div key={(row?.id as string) ?? i} role="row">
            {columnDefs.map((col) => {
              const field = col.colId ?? col.field;
              const val = field ? row[field] : undefined;
              return (
                <span key={col.colId ?? col.field}>{String(val ?? "")}</span>
              );
            })}
          </div>
        ))
      )}
    </div>
  ),
}));

describe("CampaignPlanTab", () => {
  const defaultCampaignDetails = {
    campaignName: "Test Campaign",
    campaignId: "CP-001",
    createdOn: "2025-01-01",
    startDate: "2025-01-15",
    endDate: "2025-02-15",
    goal: "Awareness",
    kpi: "Reach",
    createdBy: "John Doe",
    company: "Acme Inc",
    emailAddress: "john@acme.com",
    dsp: "DSP1",
    seatId: "S1",
    brand: "Brand X",
    oohImpressions: "1,000,000",
    uniqueReach: "500,000",
    averageFrequency: "2.0",
    cpm: "10.50",
    currency: "USD",
  };

  const baseAnalyticsData: AnalyticsExcelData = {
    campaignDetails: defaultCampaignDetails,
    statePlanning: [],
    cityPlanning: [],
    inventoryPlanning: [],
    inventoryMapping: [],
  };

  beforeEach(() => {
    vi.clearAllMocks?.();
  });

  describe("rendering", () => {
    it("renders container and three summary cards", () => {
      render(<CampaignPlanTab analyticsData={baseAnalyticsData} />);
      expect(
        document.getElementById("media-plan-analytics-campaign-plan-container"),
      ).toBeInTheDocument();
      expect(
        document.getElementById(
          "media-plan-analytics-campaign-plan-summary-cards",
        ),
      ).toBeInTheDocument();
      expect(
        document.getElementById(
          "media-plan-analytics-campaign-plan-mediaplananalytics.campaignplan.campaigndetails.title-card",
        ),
      ).toBeInTheDocument();
      expect(
        document.getElementById(
          "media-plan-analytics-campaign-plan-mediaplananalytics.campaignplan.buyerdetails.title-card",
        ),
      ).toBeInTheDocument();
      expect(
        document.getElementById(
          "media-plan-analytics-campaign-plan-mediaplananalytics.campaignplan.estimation.title-card",
        ),
      ).toBeInTheDocument();
    });

    it("renders Plan Details card with plan fields", () => {
      render(
        <CampaignPlanTab
          analyticsData={baseAnalyticsData}
          headerInfo={{ status: "PLANNED", duration: 31, budget: 25000 }}
          mediaChannels={["DIGITAL_OOH"]}
          planNumber="PL-9"
        />,
      );
      const prefix =
        "media-plan-analytics-campaign-plan-mediaplananalytics.campaignplan.campaigndetails.title";
      expect(document.getElementById(`${prefix}-title`)).toHaveTextContent(
        "mediaPlanAnalytics.campaignPlan.campaignDetails.title",
      );
      expect(
        document.getElementById(`${prefix}-campaign-name-value`),
      ).toHaveTextContent("Test Campaign");
      expect(
        document.getElementById(`${prefix}-campaign-id-value`),
      ).toHaveTextContent("PL-9");
      expect(
        document.getElementById(`${prefix}-status-value`),
      ).toHaveTextContent("campaignsList.status.PLANNED");
      expect(
        document.getElementById(`${prefix}-start-date-value`),
      ).toHaveTextContent("2025-01-15");
      expect(
        document.getElementById(`${prefix}-end-date-value`),
      ).toHaveTextContent("2025-02-15");
      expect(
        document.getElementById(`${prefix}-duration-value`),
      ).toHaveTextContent("31 mediaPlanAnalytics.campaignPlan.days");
      expect(
        document.getElementById(`${prefix}-currency-value`),
      ).toHaveTextContent("USD");
      expect(
        document.getElementById(`${prefix}-channels-value`),
      ).toHaveTextContent("Digital OOH");
      expect(
        document.getElementById(`${prefix}-budget-value`),
      ).toHaveTextContent("USD 25,000");
    });

    it("renders Buyer Details card with buyer fields", () => {
      render(
        <CampaignPlanTab
          analyticsData={baseAnalyticsData}
          clientType="DIRECT_ADVERTISER"
        />,
      );
      const prefix =
        "media-plan-analytics-campaign-plan-mediaplananalytics.campaignplan.buyerdetails.title";
      expect(document.getElementById(`${prefix}-title`)).toHaveTextContent(
        "mediaPlanAnalytics.campaignPlan.buyerDetails.title",
      );
      expect(
        document.getElementById(`${prefix}-client-type-value`),
      ).toHaveTextContent(
        "mediaPlanAnalytics.campaignPlan.buyerDetails.clientTypeDirect",
      );
      expect(
        document.getElementById(`${prefix}-planned-by-value`),
      ).toHaveTextContent("John Doe");
      expect(
        document.getElementById(`${prefix}-company-value`),
      ).toHaveTextContent("Acme Inc");
    });

    it("renders Estimated Performance Metrics card with forecast fields", () => {
      render(
        <CampaignPlanTab
          analyticsData={baseAnalyticsData}
          performanceMetrics={{
            totalInventories: 1,
            estimatedImpression: 3_600_000,
            estimatedReach: 1_400_000,
            estimatedFrequency: 2.5,
            estimatedAdPlays: 0,
            sov: 100,
            avgCpm: 1.12,
            avgECpm: 7.01,
            totalCost: 25000,
            plannedSot: 1,
            totalSot: 1,
            warnings: [],
          }}
          geographySummary={{ cityCount: 1, countryCount: 1, poiCount: 0 }}
          channelCount={1}
        />,
      );
      const prefix =
        "media-plan-analytics-campaign-plan-mediaplananalytics.campaignplan.estimation.title";
      expect(document.getElementById(`${prefix}-title`)).toHaveTextContent(
        "mediaPlanAnalytics.campaignPlan.estimation.title",
      );
      expect(
        document.getElementById(`${prefix}-total-impressions-value`),
      ).toHaveTextContent("3.6M");
      expect(
        document.getElementById(`${prefix}-estimated-reach-value`),
      ).toHaveTextContent("1.4M");
      expect(
        document.getElementById(`${prefix}-avg-frequency-value`),
      ).toHaveTextContent("2.50");
      expect(
        document.getElementById(`${prefix}-ad-plays-value`),
      ).toHaveTextContent("0");
      expect(document.getElementById(`${prefix}-cpm-value`)).toHaveTextContent(
        "USD 1.12",
      );
      expect(document.getElementById(`${prefix}-ecpm-value`)).toHaveTextContent(
        "USD 7.01",
      );
      expect(document.getElementById(`${prefix}-sov-value`)).toHaveTextContent(
        "100.0%",
      );
      expect(document.getElementById(`${prefix}-sot-value`)).toHaveTextContent(
        "100.0%",
      );
      expect(
        document.getElementById(`${prefix}-total-cost-value`),
      ).toHaveTextContent("USD 25,000");
      expect(
        document.getElementById(`${prefix}-inventories-value`),
      ).toHaveTextContent("1");
      expect(
        document.getElementById(`${prefix}-cities-value`),
      ).toHaveTextContent("1");
      expect(
        document.getElementById(`${prefix}-channels-value`),
      ).toHaveTextContent("1");
    });

    it("relabels Avg CPM as Avg CPS for SOV/AD_PLAYS goals", () => {
      render(
        <CampaignPlanTab
          analyticsData={baseAnalyticsData}
          performanceMetrics={{
            totalInventories: 0,
            estimatedImpression: 0,
            estimatedReach: 0,
            estimatedFrequency: 0,
            estimatedAdPlays: 0,
            sov: 0,
            avgCpm: 0,
            avgECpm: 0,
            totalCost: 0,
            plannedSot: 0,
            totalSot: 0,
            warnings: [],
          }}
          goalType="SOV"
        />,
      );
      expect(
        document.getElementById(
          "media-plan-analytics-campaign-plan-mediaplananalytics.campaignplan.estimation.title-cpm",
        ),
      ).toHaveTextContent("mediaPlanAnalytics.campaignPlan.estimation.avgCps");
    });

    it("renders Delivery Breakdown table with sine-wave-modulated bins", () => {
      render(
        <CampaignPlanTab
          analyticsData={baseAnalyticsData}
          performanceMetrics={{
            totalInventories: 0,
            estimatedImpression: 910000,
            estimatedReach: 910000,
            estimatedFrequency: 0,
            estimatedAdPlays: 0,
            sov: 0,
            avgCpm: 0,
            avgECpm: 0,
            totalCost: 0,
            plannedSot: 0,
            totalSot: 0,
            warnings: [],
          }}
          headerInfo={{ startDate: "2026-04-01", endDate: "2026-06-30" }}
        />,
      );
      expect(
        document.getElementById(
          "media-plan-analytics-campaign-plan-delivery-breakdown-card",
        ),
      ).toBeInTheDocument();
      expect(
        document.getElementById(
          "media-plan-analytics-campaign-plan-delivery-breakdown-row-jun-2026",
        ),
      ).toHaveTextContent("321.7K");
    });

    it("renders City Insights section", () => {
      render(<CampaignPlanTab analyticsData={baseAnalyticsData} />);
      expect(
        document.getElementById(
          "media-plan-analytics-campaign-plan-city-insights-title",
        ),
      ).toHaveTextContent("mediaPlanAnalytics.campaignPlan.cityInsights.title");
      expect(
        document.getElementById(
          "media-plan-analytics-campaign-plan-city-insights-table",
        ),
      ).toBeInTheDocument();
    });
  });

  describe("default and empty props", () => {
    it("renders with undefined campaignDetails and shows empty values", () => {
      const data: AnalyticsExcelData = {
        ...baseAnalyticsData,
        campaignDetails: undefined,
      };
      render(<CampaignPlanTab analyticsData={data} />);
      expect(
        document.getElementById(
          "media-plan-analytics-campaign-plan-mediaplananalytics.campaignplan.campaigndetails.title-campaign-name-value",
        ),
      ).toHaveTextContent("");
      expect(
        document.getElementById(
          "media-plan-analytics-campaign-plan-mediaplananalytics.campaignplan.buyerdetails.title-planned-by-value",
        ),
      ).toHaveTextContent("");
    });

    it("renders with empty statePlanning and cityPlanning", () => {
      render(<CampaignPlanTab analyticsData={baseAnalyticsData} />);
      expect(
        document.getElementById(
          "media-plan-analytics-campaign-plan-city-insights-table",
        ),
      ).toBeInTheDocument();
    });
  });

  describe("table data", () => {
    it("City Insights falls back to statePlanning data when cityPlanning is absent", () => {
      const data: AnalyticsExcelData = {
        ...baseAnalyticsData,
        statePlanning: [
          {
            id: "1",
            stateName: "California",
            population: "39M",
            inventories: 100,
            oohImpressions: "5000000",
            reach: "2000000",
            frequency: "2.5",
            cpm: 12.5,
          },
        ],
      };
      render(<CampaignPlanTab analyticsData={data} />);
      expect(screen.getByText("California")).toBeInTheDocument();
      expect(screen.getByText("39M")).toBeInTheDocument();
    });

    it("renders City Insights table using cityPlanning when provided", () => {
      const data: AnalyticsExcelData = {
        ...baseAnalyticsData,
        cityPlanning: [
          {
            id: "1",
            stateName: "NYC",
            population: "8M",
            inventories: 50,
            oohImpressions: "2000000",
            reach: "1000000",
            frequency: "2",
            cpm: 15,
          },
        ],
      };
      render(<CampaignPlanTab analyticsData={data} />);
      expect(screen.getByText("NYC")).toBeInTheDocument();
      expect(screen.getByText("15.00")).toBeInTheDocument();
    });
  });
});
