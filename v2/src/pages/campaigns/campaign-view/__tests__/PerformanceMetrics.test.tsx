import { render, screen } from "@testing-library/react";
import React from "react";
import { describe, it, expect, vi } from "vitest";

import PerformanceMetrics from "../PerformanceMetrics";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("react-dom", async () => {
  const actual = await vi.importActual<typeof import("react-dom")>("react-dom");
  return {
    ...actual,
    createPortal: (node: React.ReactNode) => node,
  };
});

vi.mock("@constants/tooltip.constants", () => ({
  TOOLTIP_CONTENT: {
    performance: {
      totalImpressions: "tooltips.performance.totalImpressions",
      estimatedReach: "tooltips.performance.estimatedReach",
      frequency: "tooltips.performance.frequency",
      plannedAdPlays: "tooltips.performance.plannedAdPlays",
      avgCpmCost: "tooltips.performance.avgCpmCost",
      eCpm: "tooltips.performance.eCpm",
      sov: "tooltips.performance.sov",
      sot: "tooltips.performance.sot",
    },
  },
}));

vi.mock("@utils/currency", () => ({
  formatCurrencyWithLocale: (amount: number | undefined, currency: string) => {
    if (amount === undefined || amount === null) return "--";
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: currency || "USD",
    }).format(amount);
  },
}));

// Mock formatNumber
vi.mock("@utils/budget.utils", () => ({
  formatNumber: (num: number) => {
    return new Intl.NumberFormat("en-US").format(num);
  },
}));

// Mock Progress component
vi.mock("@components/ui/Progressbar", () => ({
  Progress: ({ value, variant }: { value: number; variant: string }) => (
    <div data-testid="progress-bar" data-value={value} data-variant={variant}>
      Progress: {value}%
    </div>
  ),
}));

describe("PerformanceMetrics", () => {
  const mockForecastData = {
    totalInventories: 10,
    estimatedImpression: 10000000,
    estimatedReach: 5000000,
    estimatedFrequency: 2.5,
    estimatedAdPlays: 50000,
    avgCpm: 10.5,
    avgECpm: 12.3,
    sov: 25.5,
    plannedSot: 100,
    totalSot: 500,
    totalCost: 100000,
    warnings: [] as string[],
  };

  describe("Rendering", () => {
    it("should render component with title", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      expect(screen.getByText("performanceMetrics.title")).toBeInTheDocument();
    });

    it("should render all metric cards", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      expect(
        screen.getByText("performanceMetrics.metrics.totalImpressions.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("performanceMetrics.metrics.estimatedReach.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("performanceMetrics.metrics.frequency.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("performanceMetrics.metrics.plannedAdPlays.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("performanceMetrics.metrics.avgCPMCost.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("performanceMetrics.metrics.eCPM.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("performanceMetrics.metrics.sov.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("performanceMetrics.metrics.sot.title"),
      ).toBeInTheDocument();
    });

    it("should render info icons on all 8 metric cards", () => {
      const { container } = render(
        <PerformanceMetrics forecastData={mockForecastData} />,
      );
      // Each MetricCard with infoIcon=true renders a lucide Info svg
      const infoIcons = container.querySelectorAll("svg");
      // All 8 cards have infoIcon — expect at least 8 svg icons
      expect(infoIcons.length).toBeGreaterThanOrEqual(8);
    });
  });

  describe("Metric Values", () => {
    it("should display total impressions correctly", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      expect(screen.getByText("10,000,000")).toBeInTheDocument();
      expect(
        screen.getByText(
          "performanceMetrics.metrics.totalImpressions.subtitle",
        ),
      ).toBeInTheDocument();
    });

    it("should display estimated reach correctly", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      expect(screen.getByText("5,000,000")).toBeInTheDocument();
      expect(
        screen.getByText("performanceMetrics.metrics.estimatedReach.subtitle"),
      ).toBeInTheDocument();
    });

    it("should display frequency correctly", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      expect(screen.getByText("2.50")).toBeInTheDocument();
      expect(
        screen.getByText("performanceMetrics.metrics.frequency.subtitle"),
      ).toBeInTheDocument();
    });

    it("should display planned ad plays correctly", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      expect(screen.getByText("50,000")).toBeInTheDocument();
      expect(
        screen.getByText("performanceMetrics.metrics.plannedAdPlays.subtitle"),
      ).toBeInTheDocument();
    });

    it("should display avg CPM cost correctly", () => {
      render(
        <PerformanceMetrics
          forecastData={mockForecastData}
          campaignCurrency="USD"
        />,
      );

      expect(screen.getAllByText("$10.50").length).toBeGreaterThan(0);
      expect(
        screen.getByText("performanceMetrics.metrics.avgCPMCost.subtitle"),
      ).toBeInTheDocument();
    });

    it("should display eCPM correctly", () => {
      render(
        <PerformanceMetrics
          forecastData={mockForecastData}
          campaignCurrency="USD"
        />,
      );

      expect(screen.getAllByText("$12.30").length).toBeGreaterThan(0);
      expect(
        screen.getByText("performanceMetrics.metrics.eCPM.subtitle"),
      ).toBeInTheDocument();
    });
  });

  describe("Share of Voice (SOV)", () => {
    it("should display SOV value and percentage", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      expect(
        screen.getByText("performanceMetrics.metrics.sov.valueFormat"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("performanceMetrics.metrics.sov.description"),
      ).toBeInTheDocument();
    });

    it("should render progress bar for SOV", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      const progressBars = screen.getAllByTestId("progress-bar");
      const sovProgressBar = progressBars.find(
        (bar) => bar.getAttribute("data-value") === "25.5",
      );
      expect(sovProgressBar).toBeInTheDocument();
    });

    it("should display info icon for SOV", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      // Info icon should be present (checking by description text)
      expect(
        screen.getByText("performanceMetrics.metrics.sov.description"),
      ).toBeInTheDocument();
    });
  });

  describe("Share of Time (SOT)", () => {
    it("should display SOT value correctly", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      expect(
        screen.getByText("performanceMetrics.metrics.sot.valueFormat"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("performanceMetrics.metrics.sot.description"),
      ).toBeInTheDocument();
    });

    it("should calculate SOT percentage correctly", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      const progressBars = screen.getAllByTestId("progress-bar");
      const sotProgressBar = progressBars.find(
        (bar) => bar.getAttribute("data-value") === "20",
      );
      expect(sotProgressBar).toBeInTheDocument();
    });

    it("should render progress bar for SOT", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      const progressBars = screen.getAllByTestId("progress-bar");
      expect(progressBars.length).toBeGreaterThan(0);
    });
  });

  describe("Empty State", () => {
    it("should display dashes when forecastData is undefined", () => {
      render(<PerformanceMetrics forecastData={undefined} />);

      const dashes = screen.getAllByText("-");
      expect(dashes.length).toBeGreaterThan(0);
    });

    it("should display dashes for all metrics when no data", () => {
      render(<PerformanceMetrics />);

      expect(screen.getByText("performanceMetrics.title")).toBeInTheDocument();
      const dashes = screen.getAllByText("-");
      expect(dashes.length).toBeGreaterThan(0);
    });
  });

  describe("Edge Cases", () => {
    it("should handle zero values", () => {
      const zeroData = {
        totalInventories: 0,
        estimatedImpression: 0,
        estimatedReach: 0,
        estimatedFrequency: 0,
        estimatedAdPlays: 0,
        avgCpm: 0,
        avgECpm: 0,
        sov: 0,
        plannedSot: 0,
        totalSot: 100,
        totalCost: 0,
        warnings: [] as string[],
      };

      render(<PerformanceMetrics forecastData={zeroData} />);

      // Multiple elements will have "0" text, so use getAllByText
      expect(screen.getAllByText("0").length).toBeGreaterThan(0);
    });

    it("should handle very large numbers", () => {
      const largeData = {
        totalInventories: 100,
        estimatedImpression: 999999999,
        estimatedReach: 500000000,
        estimatedFrequency: 99.99,
        estimatedAdPlays: 999999,
        avgCpm: 999.99,
        avgECpm: 999.99,
        sov: 99.99,
        plannedSot: 999,
        totalSot: 1000,
        totalCost: 999999,
        warnings: [] as string[],
      };

      render(<PerformanceMetrics forecastData={largeData} />);

      expect(screen.getByText("999,999,999")).toBeInTheDocument();
    });

    it("should handle decimal frequency values", () => {
      const decimalData = {
        ...mockForecastData,
        estimatedFrequency: 2.567,
      };

      render(<PerformanceMetrics forecastData={decimalData} />);

      expect(screen.getByText("2.57")).toBeInTheDocument();
    });

    it("should handle SOT calculation when totalSot is zero", () => {
      const zeroTotalSot = {
        ...mockForecastData,
        plannedSot: 100,
        totalSot: 0,
      };

      render(<PerformanceMetrics forecastData={zeroTotalSot} />);

      // Should not crash and should display the values
      expect(screen.getByText("performanceMetrics.title")).toBeInTheDocument();
    });

    it("should handle missing currency", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      // Should still display values
      expect(screen.getAllByText("$10.50").length).toBeGreaterThan(0);
    });
  });

  describe("Progress Bar Variants", () => {
    it("should use correct variant for SOV progress bar", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      const progressBars = screen.getAllByTestId("progress-bar");
      const sovProgressBar = progressBars.find(
        (bar) => bar.getAttribute("data-value") === "25.5",
      );
      expect(sovProgressBar?.getAttribute("data-variant")).toBe("primary");
    });

    it("should use correct variant for SOT progress bar", () => {
      render(<PerformanceMetrics forecastData={mockForecastData} />);

      const progressBars = screen.getAllByTestId("progress-bar");
      const sotProgressBar = progressBars.find(
        (bar) => bar.getAttribute("data-value") === "20",
      );
      expect(sotProgressBar?.getAttribute("data-variant")).toBe("warning");
    });
  });
});
