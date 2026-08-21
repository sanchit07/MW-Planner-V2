import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import type { CampaignForecastData } from "../../../../types/inventory.types";
import CampaignForecast from "../CampaignForecast";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@utils/currency", () => ({
  formatCurrencyWithLocale: (value: number, _currency?: string) =>
    `$${value.toFixed(2)}`,
}));

const defaultForecastData: CampaignForecastData = {
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

function renderCampaignForecast(
  props: Partial<{
    campaignCurrency: string | undefined;
    forecastDataValue: CampaignForecastData;
    showSovSot: boolean;
    goalType: string;
  }> = {},
) {
  return render(
    <CampaignForecast
      campaignCurrency={props.campaignCurrency ?? "USD"}
      forecastDataValue={props.forecastDataValue ?? defaultForecastData}
      showSovSot={props.showSovSot}
      goalType={props.goalType}
    />,
  );
}

describe("CampaignForecast", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders forecast title from translation key", () => {
      renderCampaignForecast();
      expect(
        screen.getByText("inventories.forecast.title"),
      ).toBeInTheDocument();
    });

    it("renders estimated impressions with value when present", () => {
      renderCampaignForecast({
        forecastDataValue: {
          ...defaultForecastData,
          estimatedImpression: 10000,
        },
      });
      expect(screen.getByText("10,000")).toBeInTheDocument();
    });

    it("renders dash or empty for missing estimated impression", () => {
      renderCampaignForecast({
        forecastDataValue: {
          ...defaultForecastData,
          estimatedImpression: 0,
        },
      });
      const content = screen
        .getByText("inventories.forecast.est_impressions")
        .closest("div")?.parentElement;
      expect(content).toBeInTheDocument();
    });

    it("renders estimated frequency with toFixed(2)", () => {
      renderCampaignForecast({
        forecastDataValue: {
          ...defaultForecastData,
          estimatedFrequency: 2.5,
        },
      });
      expect(screen.getByText("2.50")).toBeInTheDocument();
    });

    it("renders avgCpm with formatCurrency when not NaN", () => {
      renderCampaignForecast({
        forecastDataValue: {
          ...defaultForecastData,
          avgCpm: 10,
        },
        campaignCurrency: "USD",
      });
      expect(screen.getByText("$10.00")).toBeInTheDocument();
    });

    it("renders sov percentage when showSovSot is true", () => {
      renderCampaignForecast({
        forecastDataValue: { ...defaultForecastData, sov: 15.7 },
        showSovSot: true,
      });
      expect(screen.getByText("15.7%")).toBeInTheDocument();
    });

    it("hides sov percentage when showSovSot is false", () => {
      renderCampaignForecast({
        forecastDataValue: { ...defaultForecastData, sov: 15.7 },
        showSovSot: false,
      });
      expect(
        screen.queryByText("inventories.forecast.sov_percentage"),
      ).not.toBeInTheDocument();
    });

    it("renders SOT in hours (planned out of total) when showSovSot is true", () => {
      renderCampaignForecast({
        forecastDataValue: {
          ...defaultForecastData,
          plannedSot: 50,
          totalSot: 100,
        },
        showSovSot: true,
      });
      expect(screen.getByText("inventories.forecast.sot")).toBeInTheDocument();
      expect(screen.getByText("50.00H out of 100 H")).toBeInTheDocument();
    });

    it("hides SOT when showSovSot is false", () => {
      renderCampaignForecast({
        forecastDataValue: { ...defaultForecastData, plannedSot: 50 },
        showSovSot: false,
      });
      expect(
        screen.queryByText("inventories.forecast.sot"),
      ).not.toBeInTheDocument();
    });
  });

  describe("sync with prop", () => {
    it("updates displayed data when forecastDataValue prop changes", () => {
      const { rerender } = renderCampaignForecast({
        forecastDataValue: {
          ...defaultForecastData,
          estimatedImpression: 1000,
        },
      });
      expect(screen.getByText("1,000")).toBeInTheDocument();

      rerender(
        <CampaignForecast
          campaignCurrency="USD"
          forecastDataValue={{
            ...defaultForecastData,
            estimatedImpression: 2000,
          }}
        />,
      );
      expect(screen.getByText("2,000")).toBeInTheDocument();
    });
  });

  describe("goalType — avg CPM visibility", () => {
    it("shows avg CPM when goalType is not set", () => {
      renderCampaignForecast({
        forecastDataValue: { ...defaultForecastData, avgCpm: 10 },
      });
      expect(
        screen.getByText("inventories.forecast.avg_cpm"),
      ).toBeInTheDocument();
    });

    it("hides avg CPM for SOV goal type", () => {
      renderCampaignForecast({
        forecastDataValue: { ...defaultForecastData, avgCpm: 10 },
        goalType: "SOV",
      });
      expect(
        screen.queryByText("inventories.forecast.avg_cpm"),
      ).not.toBeInTheDocument();
    });

    it("hides avg CPM for ADPLAYS goal type", () => {
      renderCampaignForecast({
        forecastDataValue: { ...defaultForecastData, avgCpm: 10 },
        goalType: "ADPLAYS",
      });
      expect(
        screen.queryByText("inventories.forecast.avg_cpm"),
      ).not.toBeInTheDocument();
    });

    it("shows avg CPM for IMPRESSIONS goal type", () => {
      renderCampaignForecast({
        forecastDataValue: { ...defaultForecastData, avgCpm: 10 },
        goalType: "IMPRESSIONS",
      });
      expect(
        screen.getByText("inventories.forecast.avg_cpm"),
      ).toBeInTheDocument();
    });

    it("shows avg CPM for REACH goal type", () => {
      renderCampaignForecast({
        forecastDataValue: { ...defaultForecastData, avgCpm: 10 },
        goalType: "REACH",
      });
      expect(
        screen.getByText("inventories.forecast.avg_cpm"),
      ).toBeInTheDocument();
    });
  });

  describe("edge cases", () => {
    it("handles undefined campaignCurrency", () => {
      renderCampaignForecast({
        campaignCurrency: undefined,
        forecastDataValue: { ...defaultForecastData, avgCpm: 5 },
      });
      expect(screen.getByText("$5.00")).toBeInTheDocument();
    });

    it("handles avgECpm and totalCost via formatCurrencyWithLocale", () => {
      renderCampaignForecast({
        forecastDataValue: {
          ...defaultForecastData,
          avgECpm: 8,
          totalCost: 5000,
        },
      });
      expect(screen.getByText("$8.00")).toBeInTheDocument();
      expect(screen.getByText("$5000.00")).toBeInTheDocument();
    });
  });
});
