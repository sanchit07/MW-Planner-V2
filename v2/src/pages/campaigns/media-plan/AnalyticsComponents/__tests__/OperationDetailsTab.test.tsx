import { render, screen } from "@testing-library/react";
import { describe, it, expect, beforeEach, vi } from "vitest";

import type { AnalyticsExcelData } from "../../analyticsTypes";
import OperationDetailsTab from "../OperationDetailsTab";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

describe("OperationDetailsTab", () => {
  const baseAnalyticsData: AnalyticsExcelData = {};

  beforeEach(() => {
    vi.clearAllMocks?.();
  });

  it("shows the empty message when there is no operation data", () => {
    render(<OperationDetailsTab analyticsData={baseAnalyticsData} />);
    expect(
      screen.getByText("mediaPlanAnalytics.operationDetails.noData"),
    ).toBeInTheDocument();
  });

  it("renders the Classic section grouped by inventory with a Total row", () => {
    const data: AnalyticsExcelData = {
      operationDetails: {
        classic: [
          {
            id: "1",
            inventoryName: "Times Square Billboard",
            referenceId: "INV-1",
            format: "Digital",
            city: "Manhattan",
            segment: "Morning Rush",
            startDate: "Apr 1, 2026",
            endDate: "Apr 15, 2026",
            operationDays: 11,
          },
          {
            id: "2",
            inventoryName: "Times Square Billboard",
            referenceId: "INV-1",
            format: "Digital",
            city: "Manhattan",
            segment: "Lunch",
            startDate: "Apr 1, 2026",
            endDate: "Apr 15, 2026",
            operationDays: 11,
          },
          {
            id: "3",
            inventoryName: "Times Square Billboard",
            referenceId: "INV-1",
            format: "Digital",
            city: "Manhattan",
            segment: "Evening Peak",
            startDate: "Apr 16, 2026",
            endDate: "Apr 30, 2026",
            operationDays: 15,
          },
        ],
      },
    };
    render(<OperationDetailsTab analyticsData={data} />);

    expect(
      document.getElementById(
        "media-plan-analytics-operation-details-classic-card",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "mediaPlanAnalytics.operationDetails.sectionTitle.classic",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Times Square Billboard")).toBeInTheDocument();
    expect(screen.getByText("INV-1 · Digital · Manhattan")).toBeInTheDocument();
    expect(screen.getByText("Morning Rush")).toBeInTheDocument();
    expect(screen.getByText("Lunch")).toBeInTheDocument();
    expect(screen.getByText("Evening Peak")).toBeInTheDocument();
    expect(
      screen.getAllByText("mediaPlanAnalytics.operationDetails.totalLabel")
        .length,
    ).toBe(1);
    expect(screen.getByText("37")).toBeInTheDocument(); // 11 + 11 + 15
  });

  it("renders the Digital section with per-segment start/end times and a Total row summing Op Days + Total Spots", () => {
    const data: AnalyticsExcelData = {
      operationDetails: {
        digital: [
          {
            id: "1",
            inventoryName: "Michigan Ave Digital Screen",
            referenceId: "INV-2",
            format: "Portrait",
            city: "Downtown",
            segment: "Shopping Hours",
            startDate: "Apr 1, 2026",
            endDate: "Apr 20, 2026",
            scheduleType: "--",
            operationDays: 20,
            operationHours: 11,
            startTime: "10:00",
            endTime: "21:00",
            totalSpots: 6600,
          },
          {
            id: "2",
            inventoryName: "Michigan Ave Digital Screen",
            referenceId: "INV-2",
            format: "Portrait",
            city: "Downtown",
            segment: "Late Night",
            startDate: "Apr 21, 2026",
            endDate: "Apr 30, 2026",
            scheduleType: "--",
            operationDays: 2,
            operationHours: 3,
            startTime: "21:00",
            endTime: "00:00",
            totalSpots: 45,
          },
        ],
      },
    };
    render(<OperationDetailsTab analyticsData={data} />);

    expect(
      document.getElementById(
        "media-plan-analytics-operation-details-digital-card",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("INV-2 · Portrait · Downtown")).toBeInTheDocument();
    expect(screen.getByText("Shopping Hours")).toBeInTheDocument();
    expect(screen.getByText("10:00")).toBeInTheDocument();
    expect(screen.getAllByText("21:00").length).toBe(2);
    expect(screen.getByText("00:00")).toBeInTheDocument();
    expect(screen.getAllByText("6.6K").length).toBe(2);
    expect(screen.getByText("45")).toBeInTheDocument();
    // Total row: 20 + 2 = 22 op days
    expect(screen.getByText("22")).toBeInTheDocument();
  });

  it("does not render a section when its data is absent", () => {
    const data: AnalyticsExcelData = {
      operationDetails: {
        classic: [
          {
            id: "1",
            inventoryName: "Board",
            referenceId: "INV-1",
            format: "Standard",
            city: "NYC",
            segment: "All Day",
            startDate: "Apr 1, 2026",
            endDate: "Apr 30, 2026",
            operationDays: 30,
          },
        ],
      },
    };
    render(<OperationDetailsTab analyticsData={data} />);
    expect(
      document.getElementById(
        "media-plan-analytics-operation-details-digital-card",
      ),
    ).not.toBeInTheDocument();
    expect(
      document.getElementById(
        "media-plan-analytics-operation-details-mobile-card",
      ),
    ).not.toBeInTheDocument();
  });
});
