import { render } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import MediaPlanGoalsKpis from "../MediaPlanGoalsKpis";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string, params?: Record<string, string | number>) =>
      params ? `${key} ${JSON.stringify(params)}` : key,
  }),
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

const perf = {
  estimatedReach: 14519778,
  estimatedImpression: 13800000,
  estimatedAdPlays: 0,
  sov: 100,
} as never;

const headerInfo = { startDate: "2026-04-01", endDate: "2026-06-30" };

describe("MediaPlanGoalsKpis", () => {
  it("renders banner with theme background", () => {
    render(
      <MediaPlanGoalsKpis
        goalType="REACH"
        targetValue={4200000}
        performanceMetrics={perf}
        headerInfo={headerInfo}
        theme={theme}
      />,
    );
    expect(document.getElementById("media-plan-goals-kpis-header")).toHaveStyle(
      {
        backgroundColor: "var(--color-mw-primary-500)",
      },
    );
  });

  it("shows formatted target and forecast values", () => {
    render(
      <MediaPlanGoalsKpis
        goalType="REACH"
        targetValue={4200000}
        performanceMetrics={perf}
        headerInfo={headerInfo}
      />,
    );
    expect(
      document.getElementById("media-plan-goals-kpis-target"),
    ).toHaveTextContent("4,200,000");
    expect(
      document.getElementById("media-plan-goals-kpis-forecast"),
    ).toHaveTextContent("14,519,778");
  });

  it("shows uncapped forecast-vs-target progress percentage", () => {
    render(
      <MediaPlanGoalsKpis
        goalType="REACH"
        targetValue={4200000}
        performanceMetrics={perf}
        headerInfo={headerInfo}
      />,
    );
    expect(
      document.getElementById("media-plan-goals-kpis-progress"),
    ).toHaveTextContent("346%");
  });

  it("renders one delivery bar per monthly bin (Apr–Jun = 3)", () => {
    render(
      <MediaPlanGoalsKpis
        goalType="REACH"
        targetValue={4200000}
        performanceMetrics={perf}
        headerInfo={headerInfo}
      />,
    );
    const delivery = document.getElementById("media-plan-goals-kpis-delivery");
    expect(delivery?.children.length).toBe(3);
  });

  it("uses impressions forecast when goal type is IMPRESSIONS", () => {
    render(
      <MediaPlanGoalsKpis
        goalType="IMPRESSIONS"
        targetValue={10000000}
        performanceMetrics={perf}
        headerInfo={headerInfo}
      />,
    );
    expect(
      document.getElementById("media-plan-goals-kpis-forecast"),
    ).toHaveTextContent("13,800,000");
  });
});
