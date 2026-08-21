import { render, screen } from "@testing-library/react";
import type { ComponentProps } from "react";
import { describe, it, expect, vi } from "vitest";

import CampaignGoalView from "../CampaignGoalView";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

// Mock DynamicProgressBar
vi.mock("@components/ui/DynamicProgressBar", () => ({
  DynamicProgressBar: ({
    value,
    maxValue,
    label,
  }: {
    value: number;
    maxValue: number;
    label: string;
  }) => (
    <div data-testid="dynamic-progress-bar">
      <div>Label: {label}</div>
      <div>Value: {value}</div>
      <div>Max: {maxValue}</div>
    </div>
  ),
}));

// Mock formatNumber
vi.mock("@utils/budget.utils", () => ({
  formatNumber: (num: number) => {
    return new Intl.NumberFormat("en-US").format(num);
  },
  normalizeGoalType: (goalType?: string) => {
    if (!goalType) return undefined;
    const normalized = goalType.toUpperCase().replace(/[\s%]+/g, "");
    if (normalized === "ADPLAYS" || normalized === "ADPLAY") return "ADPLAYS";
    if (normalized === "SOV" || normalized === "SHAREOFVOICE") return "SOV";
    if (normalized === "IMPRESSIONS" || normalized === "NUMBEROFIMPRESSIONS")
      return "IMPRESSIONS";
    if (normalized === "REACH" || normalized === "UNIQUEUSERS") return "REACH";
    return goalType.toUpperCase().replace(/\s+/g, "");
  },
}));

describe("CampaignGoalView", () => {
  describe("Rendering", () => {
    it("should render component with title", () => {
      render(<CampaignGoalView campaignId="test-id" />);

      expect(
        screen.getByText("viewCampaign.campaignGoalView.selectedCampaignGoal"),
      ).toBeInTheDocument();
    });

    it("should display empty state when no goal data is provided", () => {
      render(<CampaignGoalView campaignId="test-id" />);

      expect(
        screen.getByText("viewCampaign.campaignGoalView.noGoalSelected"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("viewCampaign.campaignGoalView.noMetricsToShow"),
      ).toBeInTheDocument();
    });

    it("should display goal type badge when goal data is available", () => {
      const goals = {
        goalType: "IMPRESSIONS",
        targetValue: 1000000,
        achievedValue: 1200000,
      };

      render(<CampaignGoalView campaignId="test-id" goals={goals} />);

      expect(
        screen.getByText("campaignsList.goalTypes.IMPRESSIONS"),
      ).toBeInTheDocument();
    });
  });

  describe("Goal Data Display", () => {
    const goals = {
      goalType: "IMPRESSIONS",
      targetValue: 1000000,
      achievedValue: 1200000,
      weeklyBreakdown: {
        "Week 1": 95,
        "Week 2": 105,
        "Week 3": 110,
      },
    };

    it("should display target vs planned performance section", () => {
      render(<CampaignGoalView campaignId="test-id" goals={goals} />);

      expect(
        screen.getByText("viewCampaign.campaignGoalView.targetVsPlanned"),
      ).toBeInTheDocument();
    });

    it("should display target value", () => {
      render(<CampaignGoalView campaignId="test-id" goals={goals} />);

      expect(screen.getByText("1,000,000")).toBeInTheDocument();
      expect(
        screen.getByText("viewCampaign.campaignGoalView.yourTarget"),
      ).toBeInTheDocument();
    });

    it("should display achieved value", () => {
      render(<CampaignGoalView campaignId="test-id" goals={goals} />);

      expect(screen.getByText("1,200,000")).toBeInTheDocument();
      expect(
        screen.getByText("viewCampaign.campaignGoalView.plannedDelivery"),
      ).toBeInTheDocument();
    });

    it("should display progress bar with correct values", () => {
      render(<CampaignGoalView campaignId="test-id" goals={goals} />);

      const progressBar = screen.getByTestId("dynamic-progress-bar");
      expect(progressBar).toBeInTheDocument();
      expect(progressBar).toHaveTextContent("Value: 120");
      expect(progressBar).toHaveTextContent("Max: 150");
    });

    it("should show over target badge when achieved > target", () => {
      render(<CampaignGoalView campaignId="test-id" goals={goals} />);

      expect(
        screen.getByText(/\+20%.*viewCampaign\.campaignGoalView\.overTarget/),
      ).toBeInTheDocument();
    });

    it("should show under target badge when achieved < target", () => {
      const underTargetGoals = {
        goalType: "IMPRESSIONS",
        targetValue: 1000000,
        achievedValue: 800000,
      };

      render(
        <CampaignGoalView campaignId="test-id" goals={underTargetGoals} />,
      );

      expect(
        screen.getByText(/-20%.*viewCampaign\.campaignGoalView\.underTarget/),
      ).toBeInTheDocument();
    });
  });

  describe("Expected Goal Achievement", () => {
    const goals = {
      goalType: "IMPRESSIONS",
      targetValue: 1000000,
      achievedValue: 1200000,
      weeklyBreakdown: {
        "Week 1": 95,
        "Week 2": 105,
        "Week 3": 110,
      },
    };

    it("should display expected goal achievement section", () => {
      render(<CampaignGoalView campaignId="test-id" goals={goals} />);

      expect(
        screen.getByText(
          "viewCampaign.campaignGoalView.expectedGoalAchievement",
        ),
      ).toBeInTheDocument();
    });

    it("should display weekly breakdown when available", () => {
      render(<CampaignGoalView campaignId="test-id" goals={goals} />);

      expect(screen.getByText("Week 1")).toBeInTheDocument();
      expect(screen.getByText("Week 2")).toBeInTheDocument();
      expect(screen.getByText("Week 3")).toBeInTheDocument();
    });

    it("should display percentage for each week", () => {
      render(<CampaignGoalView campaignId="test-id" goals={goals} />);

      expect(
        screen.getByText("viewCampaign.campaignGoalView.expectedReachPartial"),
      ).toBeInTheDocument();
      expect(
        screen.getAllByText("viewCampaign.campaignGoalView.expectedOverdeliver")
          .length,
      ).toBeGreaterThanOrEqual(2);
    });

    it("should display 100% message when percentage is exactly 100", () => {
      const exactGoals = {
        goalType: "IMPRESSIONS",
        targetValue: 1000000,
        achievedValue: 1000000,
        weeklyBreakdown: {
          "Week 1": 100,
        },
      };

      render(<CampaignGoalView campaignId="test-id" goals={exactGoals} />);

      expect(
        screen.getByText("viewCampaign.campaignGoalView.expectedReachTarget"),
      ).toBeInTheDocument();
    });

    it("should display empty message when no weekly breakdown", () => {
      const goalsWithoutBreakdown = {
        goalType: "IMPRESSIONS",
        targetValue: 1000000,
        achievedValue: 1200000,
      };

      render(
        <CampaignGoalView campaignId="test-id" goals={goalsWithoutBreakdown} />,
      );

      expect(
        screen.getByText("viewCampaign.campaignGoalView.noGoalAchievement"),
      ).toBeInTheDocument();
    });
  });

  describe("How Your Plan Achieves This Goal", () => {
    const goals = {
      goalType: "IMPRESSIONS",
      targetValue: 1000000,
      achievedValue: 1200000,
    };

    it("should display strategy section", () => {
      render(<CampaignGoalView campaignId="test-id" goals={goals} />);

      expect(
        screen.getByText("viewCampaign.campaignGoalView.howYourPlanAchieves"),
      ).toBeInTheDocument();
    });

    it("should display all three strategy cards", () => {
      render(<CampaignGoalView campaignId="test-id" goals={goals} />);

      expect(
        screen.getByText(
          "viewCampaign.campaignGoalView.strategies.highTraffic.title",
        ),
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          "viewCampaign.campaignGoalView.strategies.formatMix.title",
        ),
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          "viewCampaign.campaignGoalView.strategies.overDelivery.title",
        ),
      ).toBeInTheDocument();
    });
  });

  describe("Progress Calculation", () => {
    it("should calculate progress correctly when value <= 100%", () => {
      const goals = {
        goalType: "IMPRESSIONS",
        targetValue: 1000000,
        achievedValue: 800000, // 80%
      };

      render(<CampaignGoalView campaignId="test-id" goals={goals} />);

      const progressBar = screen.getByTestId("dynamic-progress-bar");
      expect(progressBar).toHaveTextContent("Value: 80");
      expect(progressBar).toHaveTextContent("Max: 100");
    });

    it("should round max value to nearest 50 when value > 100%", () => {
      const goals = {
        goalType: "IMPRESSIONS",
        targetValue: 1000000,
        achievedValue: 1300000, // 130%
      };

      render(<CampaignGoalView campaignId="test-id" goals={goals} />);

      const progressBar = screen.getByTestId("dynamic-progress-bar");
      expect(progressBar).toHaveTextContent("Value: 130");
      expect(progressBar).toHaveTextContent("Max: 150");
    });

    it("should handle zero target value", () => {
      const goals = {
        goalType: "IMPRESSIONS",
        targetValue: 0,
        achievedValue: 1000000,
      };

      render(<CampaignGoalView campaignId="test-id" goals={goals} />);

      const progressBar = screen.getByTestId("dynamic-progress-bar");
      expect(progressBar).toHaveTextContent("Value: 0");
      expect(progressBar).toHaveTextContent("Max: 100");
    });
  });

  describe("Edge Cases", () => {
    it("should handle undefined goals prop", () => {
      render(<CampaignGoalView campaignId="test-id" goals={undefined} />);

      expect(
        screen.getByText("viewCampaign.campaignGoalView.noGoalSelected"),
      ).toBeInTheDocument();
    });

    it("should handle missing goalType", () => {
      const incompleteGoals = {
        targetValue: 1000000,
        achievedValue: 1200000,
      };

      render(
        <CampaignGoalView
          campaignId="test-id"
          goals={
            incompleteGoals as unknown as ComponentProps<
              typeof CampaignGoalView
            >["goals"]
          }
        />,
      );

      expect(
        screen.getByText("viewCampaign.campaignGoalView.noGoalSelected"),
      ).toBeInTheDocument();
    });

    it("should handle missing targetValue", () => {
      const incompleteGoals = {
        goalType: "IMPRESSIONS",
        achievedValue: 1200000,
      };

      render(
        <CampaignGoalView
          campaignId="test-id"
          goals={
            incompleteGoals as unknown as ComponentProps<
              typeof CampaignGoalView
            >["goals"]
          }
        />,
      );

      expect(
        screen.getByText("viewCampaign.campaignGoalView.noGoalSelected"),
      ).toBeInTheDocument();
    });

    it("should handle missing achievedValue", () => {
      const incompleteGoals = {
        goalType: "IMPRESSIONS",
        targetValue: 1000000,
      };

      render(
        <CampaignGoalView
          campaignId="test-id"
          goals={
            incompleteGoals as unknown as ComponentProps<
              typeof CampaignGoalView
            >["goals"]
          }
        />,
      );

      expect(
        screen.getByText("viewCampaign.campaignGoalView.noGoalSelected"),
      ).toBeInTheDocument();
    });

    it("should display '--' for missing values", () => {
      // When both targetValue and achievedValue are undefined, hasGoalData is false
      // and it shows empty state. To test "--", we need both values defined
      // but one can be 0 (falsy) to trigger the "--" display
      const goals = {
        goalType: "IMPRESSIONS",
        targetValue: 0, // 0 is falsy, so it will show "--"
        achievedValue: 1000000,
      };

      render(
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        <CampaignGoalView campaignId="test-id" goals={goals as any} />,
      );

      // When targetValue is 0 (falsy), "--" should appear
      expect(screen.getAllByText("--").length).toBeGreaterThan(0);
    });
  });
});
