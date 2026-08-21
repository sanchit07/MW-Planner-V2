import { render } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import MediaPlanAudienceTrends from "../MediaPlanAudienceTrends";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@utils/dashboard.utils", () => ({
  formatCompactNumber: (n: number) => `${n}`,
}));

const mockReach = vi.fn();
vi.mock("../../inventory/plan-summary/useReachCurve", () => ({
  useReachCurve: (...args: unknown[]) => mockReach(...args),
}));

vi.mock("../../inventory/plan-summary/ReachBuildChart", () => ({
  default: () => <div data-testid="reach-build-chart" />,
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

const readyCurve = {
  status: "ready",
  overallReach: [10, 40, 80],
  labels: ["May 01", "May 02", "May 03"],
  inventoryCount: 2,
  refetch: vi.fn(),
};

describe("MediaPlanAudienceTrends", () => {
  it("renders banner, reach, activity (with targeting) and concentration", () => {
    mockReach.mockReturnValue(readyCurve);
    render(
      <MediaPlanAudienceTrends
        campaignId="c1"
        performanceMetrics={{ estimatedReach: 1400000 } as never}
        audienceDemographics={{ ageGroups: ["18-24"] }}
        theme={theme}
      />,
    );
    expect(
      document.getElementById("media-plan-audience-trends-header"),
    ).toHaveStyle({ backgroundColor: "var(--color-mw-primary-500)" });
    expect(
      document.getElementById("media-plan-audience-trends-reach"),
    ).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-audience-trends-activity"),
    ).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-audience-trends-concentration"),
    ).toBeInTheDocument();
  });

  it("suppresses activity and concentration with no targeting/custom schedule", () => {
    mockReach.mockReturnValue(readyCurve);
    render(<MediaPlanAudienceTrends campaignId="c1" theme={theme} />);
    // No targeting → both activity and concentration hidden; reach still shows.
    expect(
      document.getElementById("media-plan-audience-trends-activity"),
    ).not.toBeInTheDocument();
    expect(
      document.getElementById("media-plan-audience-trends-concentration"),
    ).not.toBeInTheDocument();
    expect(
      document.getElementById("media-plan-audience-trends-reach"),
    ).toBeInTheDocument();
  });

  it("renders the reach chart when the curve is ready", () => {
    mockReach.mockReturnValue(readyCurve);
    const { getByTestId } = render(
      <MediaPlanAudienceTrends campaignId="c1" theme={theme} />,
    );
    expect(getByTestId("reach-build-chart")).toBeInTheDocument();
  });

  it("shows a placeholder when the reach curve is not ready", () => {
    mockReach.mockReturnValue({ ...readyCurve, status: "loading" });
    render(<MediaPlanAudienceTrends campaignId="c1" theme={theme} />);
    expect(
      document.getElementById("media-plan-audience-trends-reach-empty"),
    ).toBeInTheDocument();
  });

  it("renders one concentration row per targeting dimension (4)", () => {
    mockReach.mockReturnValue(readyCurve);
    render(
      <MediaPlanAudienceTrends
        campaignId="c1"
        audienceDemographics={{
          ageGroups: ["18-24", "25-34"],
          incomeLevel: ["high"],
          interests: [],
          lifestyle: ["urban"],
        }}
        theme={theme}
      />,
    );
    const concentration = document.getElementById(
      "media-plan-audience-trends-concentration",
    );
    // 1 label paragraph + 4 rows → the rows live in the inner flex container
    expect(concentration?.textContent).toContain("age_gender");
    expect(concentration?.textContent).toContain("interest");
  });
});
