import { render } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import MediaPlanPerformanceMetrics from "../MediaPlanPerformanceMetrics";

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

describe("MediaPlanPerformanceMetrics", () => {
  it("renders card, banner title and subtitle", () => {
    render(<MediaPlanPerformanceMetrics />);
    expect(
      document.getElementById("media-plan-performance-metrics-card"),
    ).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-performance-metrics-title"),
    ).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-performance-metrics-subtitle"),
    ).toBeInTheDocument();
  });

  it("applies theme primary color as the banner background", () => {
    render(<MediaPlanPerformanceMetrics theme={theme} />);
    const header = document.getElementById(
      "media-plan-performance-metrics-header",
    );
    expect(header).toHaveStyle({
      backgroundColor: "var(--color-mw-primary-500)",
    });
  });

  it("renders all 12 metric cards", () => {
    render(<MediaPlanPerformanceMetrics />);
    const grid = document.getElementById("media-plan-performance-metrics-grid");
    expect(grid?.children.length).toBe(12);
  });

  it("shows city count from geographySummary", () => {
    render(
      <MediaPlanPerformanceMetrics
        geographySummary={{ cityCount: 3, countryCount: 2, poiCount: 5 }}
      />,
    );
    expect(
      document.getElementById("media-plan-performance-metric-geography-value"),
    ).toHaveTextContent("3");
  });

  it("derives SOT percentage from plannedSot / totalSot", () => {
    render(
      <MediaPlanPerformanceMetrics
        performanceMetrics={{ plannedSot: 6, totalSot: 24 } as never}
      />,
    );
    expect(
      document.getElementById("media-plan-performance-metric-sot-value"),
    ).toHaveTextContent("25%");
  });

  it("renders the two footer notes", () => {
    render(<MediaPlanPerformanceMetrics />);
    expect(
      document.getElementById("media-plan-performance-metrics-note-primary"),
    ).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-performance-metrics-note-secondary"),
    ).toBeInTheDocument();
  });
});
