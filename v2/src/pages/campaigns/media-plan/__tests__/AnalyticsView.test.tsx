import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import AnalyticsView from "../AnalyticsView";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("../transformMediaPlanData", () => ({
  transformMediaPlanData: (data: unknown) => data ?? {},
}));

vi.mock("../AnalyticsComponents/CampaignPlanTab", () => ({
  default: ({ analyticsData }: { analyticsData: unknown }) => (
    <div data-testid="campaign-plan-tab">
      {analyticsData ? "has data" : "no data"}
    </div>
  ),
}));
vi.mock("../AnalyticsComponents/InventoryDetailsTab", () => ({
  default: () => <div data-testid="inventory-details-tab">Inventory</div>,
}));
vi.mock("../AnalyticsComponents/CostingTab", () => ({
  default: () => <div data-testid="costing-tab">Costing</div>,
}));
vi.mock("../AnalyticsComponents/OperationDetailsTab", () => ({
  default: () => <div data-testid="operation-details-tab">Operation</div>,
}));
vi.mock("../AnalyticsComponents/DOOHSchedulesTab", () => ({
  default: () => <div data-testid="dooh-schedules-tab">DOOH</div>,
}));
vi.mock("../AnalyticsComponents/GeographyTargetingTab", () => ({
  default: () => <div data-testid="geography-targeting-tab">Geography</div>,
}));
vi.mock("../AnalyticsComponents/CinemaTab", () => ({
  default: () => <div data-testid="cinema-tab">Cinema</div>,
}));

describe("AnalyticsView", () => {
  it("renders container with tabs", () => {
    render(<AnalyticsView />);
    const container = document.getElementById(
      "media-plan-analytics-view-container",
    );
    expect(container).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-analytics-tabs"),
    ).toBeInTheDocument();
  });

  it("renders all six tab triggers", () => {
    render(<AnalyticsView />);
    expect(
      screen.getByRole("button", {
        name: "mediaPlanAnalytics.tabs.campaignPlan",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: "mediaPlanAnalytics.tabs.inventoryDetails",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "mediaPlanAnalytics.tabs.costing" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: "mediaPlanAnalytics.tabs.operationDetails",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: "mediaPlanAnalytics.tabs.doohSchedules",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: "mediaPlanAnalytics.tabs.geographyTargeting",
      }),
    ).toBeInTheDocument();
  });

  it("does not render the Cinema tab when there is no cinema inventory", () => {
    render(<AnalyticsView />);
    expect(
      screen.queryByRole("button", { name: "mediaPlanAnalytics.tabs.cinema" }),
    ).not.toBeInTheDocument();
  });

  it("renders the Cinema tab only when cinemaInventory is non-empty", () => {
    const data = {
      cinemaInventory: [{ id: "1", name: "Cinema Screen" }],
    };
    render(<AnalyticsView mediaPlanData={data as never} />);
    expect(
      screen.getByRole("button", { name: "mediaPlanAnalytics.tabs.cinema" }),
    ).toBeInTheDocument();
  });

  it("shows campaign plan content by default", () => {
    render(<AnalyticsView />);
    expect(screen.getByTestId("campaign-plan-tab")).toBeInTheDocument();
  });

  it("passes transformed analytics data when mediaPlanData provided", () => {
    const data = { headerInfo: { name: "Test" } };
    render(<AnalyticsView mediaPlanData={data as never} />);
    expect(screen.getByTestId("campaign-plan-tab")).toHaveTextContent(
      "has data",
    );
  });
});
