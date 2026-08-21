import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import type { CampaignForecastData } from "../../../../../types/inventory.types";
import ForecastTiles from "../ForecastTiles";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@utils/currency", () => ({
  formatCurrencyWithLocale: (v: number, c?: string) => `${c ?? ""} ${v}`.trim(),
}));

const forecast: CampaignForecastData = {
  totalInventories: 12,
  estimatedImpression: 1403927,
  estimatedReach: 166371,
  estimatedFrequency: 8.4,
  estimatedAdPlays: 672,
  co2PerPlay: 0.56,
  sov: 1.5,
  avgCpm: 5,
  avgECpm: 0.67,
  totalCost: 933.8,
  plannedSot: 2.5,
  totalSot: 100,
  warnings: [],
};

describe("ForecastTiles", () => {
  it("renders all eleven tiles mapped from forecast data", () => {
    render(<ForecastTiles forecastData={forecast} campaignCurrency="MYR" />);
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("1,403,927")).toBeInTheDocument();
    expect(screen.getByText("166,371")).toBeInTheDocument();
    expect(screen.getByText("8.4x")).toBeInTheDocument();
    expect(screen.getByText("672")).toBeInTheDocument();
    expect(screen.getByText("0.560 kg")).toBeInTheDocument();
    expect(screen.getByText("MYR 5")).toBeInTheDocument();
    expect(screen.getByText("MYR 0.67")).toBeInTheDocument();
    expect(screen.getByText("1.50%")).toBeInTheDocument();
    expect(screen.getByText("2.50H")).toBeInTheDocument();
    expect(screen.getByText("MYR 933.8")).toBeInTheDocument();
  });

  it("labels Cost (not Spend)", () => {
    render(<ForecastTiles forecastData={forecast} campaignCurrency="MYR" />);
    expect(
      screen.getByText("inventories.planSummary.tiles.cost"),
    ).toBeInTheDocument();
  });
});
