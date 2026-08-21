import { render } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import MediaPlanGeographicPlan from "../MediaPlanGeographicPlan";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
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

const cities = [
  {
    name: "Manhattan",
    country: "United States",
    totalInventories: 1,
    impressions: 3600000,
    reach: 1400000,
    totalAmount: 25000,
    avgCpm: 7.01,
    totalAmountInPercentage: 100,
    frequency: 1,
  },
];

describe("MediaPlanGeographicPlan", () => {
  it("renders banner with theme background", () => {
    render(<MediaPlanGeographicPlan costSplitData={cities} theme={theme} />);
    expect(
      document.getElementById("media-plan-geographic-plan-header"),
    ).toHaveStyle({ backgroundColor: "var(--color-mw-primary-500)" });
  });

  it("renders one row per city (no total row)", () => {
    render(<MediaPlanGeographicPlan costSplitData={cities} />);
    const table = document.getElementById("media-plan-geographic-plan-table");
    // header + 1 city row
    expect(table?.querySelectorAll("tr").length).toBe(2);
  });

  it("shows the country from the city cost-split row", () => {
    render(<MediaPlanGeographicPlan costSplitData={cities} />);
    const table = document.getElementById("media-plan-geographic-plan-table");
    expect(table?.textContent).toContain("United States");
  });

  it("renders four summary boxes and the footer note", () => {
    render(<MediaPlanGeographicPlan costSplitData={cities} />);
    expect(
      document.getElementById("media-plan-geographic-plan-summary")?.children
        .length,
    ).toBe(4);
    expect(
      document.getElementById("media-plan-geographic-plan-note"),
    ).toBeInTheDocument();
  });

  it("shows empty state when no city data", () => {
    render(<MediaPlanGeographicPlan costSplitData={[]} />);
    expect(
      document.getElementById("media-plan-geographic-plan-empty"),
    ).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-geographic-plan-table"),
    ).not.toBeInTheDocument();
  });
});
