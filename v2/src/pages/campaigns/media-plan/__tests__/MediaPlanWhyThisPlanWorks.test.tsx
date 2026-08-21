import { render } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import MediaPlanWhyThisPlanWorks from "../MediaPlanWhyThisPlanWorks";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string, params?: Record<string, string | number>) =>
      params ? `${key} ${JSON.stringify(params)}` : key,
  }),
}));

vi.mock("@utils/campaign.utils", () => ({
  formatCurrency: (amount: number, currency?: string) =>
    `${currency ?? "USD"} ${amount.toFixed(2)}`,
}));

vi.mock("@utils/dashboard.utils", () => ({
  formatCompactNumber: (n: number) => `${n}`,
}));

const theme = {
  id: "primary",
  name: "Test",
  colors: {
    primary: "--color-mw-primary-500",
    secondary: "--color-mw-primary-400",
    accent: "--color-mw-primary-300",
  },
};

const forecastData = {
  totalInventories: 6,
  estimatedImpression: 493950,
  estimatedReach: 93512,
  avgCpm: 33,
  sov: 100,
} as never;

const costSplitByCity = [
  {
    name: "Bishan",
    impressions: 100,
    reach: 0,
    avgCpm: 0,
    frequency: 0,
    totalAmount: 0,
    totalAmountInPercentage: 0,
    totalInventories: 1,
  },
  {
    name: "Orchard",
    impressions: 900,
    reach: 0,
    avgCpm: 0,
    frequency: 0,
    totalAmount: 0,
    totalAmountInPercentage: 0,
    totalInventories: 1,
  },
] as never;

const headerInfo = {
  startDate: "2026-05-01",
  endDate: "2026-05-31",
  currency: "SGD",
};

const selectedInventory = {
  summaryStatistics: {},
  locations: [
    {
      detail: { name: "Times Square Billboard", referenceId: "a" },
      location: { location: { city: "Orchard" } },
    },
  ],
} as never;

describe("MediaPlanWhyThisPlanWorks", () => {
  it("renders banner with theme background", () => {
    render(
      <MediaPlanWhyThisPlanWorks
        forecastData={forecastData}
        costSplitByCity={costSplitByCity}
        channelCount={2}
        selectedInventory={selectedInventory}
        headerInfo={headerInfo}
        theme={theme}
      />,
    );
    expect(document.getElementById("media-plan-why-plan-header")).toHaveStyle({
      backgroundColor: "var(--color-mw-primary-500)",
    });
  });

  it("renders exactly three reason cards", () => {
    render(
      <MediaPlanWhyThisPlanWorks
        forecastData={forecastData}
        costSplitByCity={costSplitByCity}
        channelCount={2}
        headerInfo={headerInfo}
        theme={theme}
      />,
    );
    expect(
      document.getElementById("media-plan-why-plan-reasons")?.children.length,
    ).toBe(3);
  });

  it("uses the top city (by impressions) in reason 1", () => {
    render(
      <MediaPlanWhyThisPlanWorks
        forecastData={forecastData}
        costSplitByCity={costSplitByCity}
        channelCount={2}
        headerInfo={headerInfo}
        theme={theme}
      />,
    );
    expect(
      document.getElementById("media-plan-why-plan-reasons")?.textContent,
    ).toContain("Orchard");
  });

  it("renders weekly milestones ending at 100%", () => {
    render(
      <MediaPlanWhyThisPlanWorks
        forecastData={forecastData}
        costSplitByCity={costSplitByCity}
        channelCount={2}
        headerInfo={headerInfo}
        theme={theme}
      />,
    );
    const milestones = document.getElementById(
      "media-plan-why-plan-milestones",
    );
    expect(milestones).toBeInTheDocument();
    expect(milestones?.lastElementChild?.textContent).toContain("100%");
  });

  it("lists inventory in the plan", () => {
    render(
      <MediaPlanWhyThisPlanWorks
        forecastData={forecastData}
        costSplitByCity={costSplitByCity}
        channelCount={2}
        selectedInventory={selectedInventory}
        headerInfo={headerInfo}
        theme={theme}
      />,
    );
    expect(
      document.getElementById("media-plan-why-plan-inventory")?.textContent,
    ).toContain("Times Square Billboard");
  });
});
