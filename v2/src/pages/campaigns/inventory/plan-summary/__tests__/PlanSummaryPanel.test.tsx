import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi } from "vitest";

import type { CampaignForecastData } from "../../../../../types/inventory.types";
import PlanSummaryPanel from "../PlanSummaryPanel";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@utils/currency", () => ({
  formatCurrencyWithLocale: (v: number) => `$${v}`,
}));

vi.mock("../ReachBuildChart", () => ({
  default: () =>
    React.createElement("div", { "data-testid": "reach-build-chart" }),
}));

const idleReachCurve = {
  status: "idle" as const,
  data: [] as number[],
  labels: [] as string[],
  inventoryCount: 0,
};

const baseForecast: CampaignForecastData = {
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
};

function renderPanel(
  props: Partial<React.ComponentProps<typeof PlanSummaryPanel>> = {},
) {
  return render(
    <PlanSummaryPanel
      status="completed"
      progress={100}
      forecastData={baseForecast}
      campaignCurrency="USD"
      reachCurve={idleReachCurve}
      onRetry={vi.fn()}
      onAdjustBudget={vi.fn()}
      onLowerGoal={vi.fn()}
      onOptimizeToGoal={vi.fn()}
      {...props}
    />,
  );
}

describe("PlanSummaryPanel", () => {
  it("renders the plan summary heading when completed with inventories", () => {
    renderPanel({
      status: "completed",
      forecastData: { ...baseForecast, totalInventories: 5 },
    });
    expect(
      screen.getByText("inventories.planSummary.heading"),
    ).toBeInTheDocument();
  });

  it("shows the progress message while generating", () => {
    renderPanel({ status: "generating", progress: 10 });
    expect(
      screen.getByText("inventories.smartSuggestion.progress.budgetFit"),
    ).toBeInTheDocument();
  });

  it("shows the error message and Retry when failed", async () => {
    const onRetry = vi.fn();
    renderPanel({ status: "failed", onRetry });
    expect(
      screen.getByText("inventories.smartSuggestion.errorMessage"),
    ).toBeInTheDocument();
    await userEvent.click(
      screen.getByText("inventories.smartSuggestion.retry"),
    );
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it("shows the empty state when completed with no inventories", () => {
    renderPanel({ status: "completed", forecastData: baseForecast });
    expect(
      screen.getByText("inventories.planSummary.empty"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("inventories.planSummary.emptyHint"),
    ).toBeInTheDocument();
  });

  it("shows forecast tiles when completed with inventories", () => {
    renderPanel({
      status: "completed",
      forecastData: { ...baseForecast, totalInventories: 5 },
    });
    expect(
      screen.queryByText("inventories.planSummary.empty"),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText("inventories.planSummary.tiles.inventories"),
    ).toBeInTheDocument();
  });

  it("renders the Reach Build chart when the curve is ready", () => {
    renderPanel({
      status: "completed",
      forecastData: { ...baseForecast, totalInventories: 5 },
      reachCurve: {
        status: "ready",
        data: [0, 50, 90],
        labels: ["Jan 01", "Jan 02", "Jan 03"],
        inventoryCount: 5,
      },
    });
    expect(screen.getByTestId("reach-build-chart")).toBeInTheDocument();
  });

  it("does not render the chart when the curve failed", () => {
    renderPanel({
      status: "completed",
      forecastData: { ...baseForecast, totalInventories: 5 },
      reachCurve: { ...idleReachCurve, status: "failed" },
    });
    expect(screen.queryByTestId("reach-build-chart")).not.toBeInTheDocument();
  });
});
