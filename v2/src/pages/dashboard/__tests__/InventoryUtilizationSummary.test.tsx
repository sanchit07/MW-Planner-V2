import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";

import InventoryUtilizationSummary, {
  type InventorySummaryData,
} from "../InventoryUtilizationSummary";

const mockT = vi.fn((key: string) => key);

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: mockT }),
}));

vi.mock("react-dom", async () => {
  const actual = await vi.importActual("react-dom");
  return {
    ...actual,
    createPortal: (node: React.ReactNode) => node,
  };
});

let capturedFormatDataLabel:
  | ((value: number, datasetLabel: string) => string)
  | null = null;

vi.mock("@components/common/HorizontalBarChart", () => ({
  default: ({
    labels,
    datasets,
    formatDataLabel,
  }: {
    labels: string[];
    datasets: { label: string; data: number[] }[];
    formatDataLabel?: (value: number, datasetLabel: string) => string;
  }) => {
    capturedFormatDataLabel = formatDataLabel ?? null;
    return (
      <div data-testid="horizontal-bar-chart">
        <span data-testid="chart-labels">{labels.join(",")}</span>
        {datasets.map((ds) => (
          <span
            key={ds.label}
            data-dataset={ds.label}
            data-values={ds.data.join(",")}
          >
            {ds.label}
          </span>
        ))}
      </div>
    );
  },
}));

vi.mock("../SummaryCard", () => ({
  default: ({
    title,
    value,
    subtitle,
    trend,
  }: {
    title: string;
    value: string | number;
    subtitle?: string;
    trend?: { value: number; isPositive: boolean };
  }) => (
    <div data-testid="summary-card" data-title={title}>
      <span data-testid="summary-value">{value}</span>
      {subtitle != null && (
        <span data-testid="summary-subtitle">{subtitle}</span>
      )}
      {trend != null && (
        <span data-testid="summary-trend" data-positive={trend.isPositive}>
          {trend.value}%
        </span>
      )}
    </div>
  ),
}));

const defaultData: InventorySummaryData = {
  activeCampaigns: { count: 120, inReviewing: 22 },
  totalInventories: { count: 1200, activeCount: 1200 },
  utilizationRate: {
    percentage: 78.5,
    unitsBooked: 79,
    changePercentage: 12,
    isPositiveChange: false,
  },
  utilization: { booked: 62, reserved: 16, down: 16, available: 6 },
};

describe("InventoryUtilizationSummary", () => {
  beforeEach(() => {
    mockT.mockImplementation((key: string) => key);
    capturedFormatDataLabel = null;
  });

  describe("conditional rendering", () => {
    it("renders nothing when showOverview and showUtilizationBreakdown are false", () => {
      const { container } = render(
        <InventoryUtilizationSummary
          showOverview={false}
          showUtilizationBreakdown={false}
        />,
      );

      expect(
        container.querySelector('[class*="rounded-lg"][class*="border"]'),
      ).not.toBeInTheDocument();
    });

    it("renders card when showOverview is true", () => {
      render(
        <InventoryUtilizationSummary showOverview={true} data={defaultData} />,
      );

      expect(
        screen.getByText("inventoryUtilization.title"),
      ).toBeInTheDocument();
      expect(screen.getAllByTestId("summary-card")).toHaveLength(3);
    });

    it("renders card when showUtilizationBreakdown is true", () => {
      render(
        <InventoryUtilizationSummary
          showUtilizationBreakdown={true}
          data={defaultData}
        />,
      );

      expect(
        screen.getByText("inventoryUtilization.title"),
      ).toBeInTheDocument();
      expect(screen.getByTestId("horizontal-bar-chart")).toBeInTheDocument();
    });

    it("renders both overview and utilization breakdown when both flags are true", () => {
      render(
        <InventoryUtilizationSummary
          showOverview={true}
          showUtilizationBreakdown={true}
          data={defaultData}
        />,
      );

      expect(screen.getAllByTestId("summary-card")).toHaveLength(3);
      expect(screen.getByTestId("horizontal-bar-chart")).toBeInTheDocument();
      expect(
        screen.getByText("inventoryUtilization.utilizationBreakdown"),
      ).toBeInTheDocument();
    });
  });

  describe("header and dropdown", () => {
    it("renders title and dropdown label", () => {
      render(
        <InventoryUtilizationSummary showOverview={true} data={defaultData} />,
      );

      expect(
        screen.getByText("inventoryUtilization.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("inventoryUtilization.dropdownLabel"),
      ).toBeInTheDocument();
    });

    it("shows All Inventories when selectedInventory is not in options list", () => {
      render(
        <InventoryUtilizationSummary
          showOverview={true}
          selectedInventory="all"
          data={defaultData}
        />,
      );

      expect(
        screen.getByText("inventoryUtilization.allInventories"),
      ).toBeInTheDocument();
    });

    it("shows selected option label when selectedInventory matches an option", () => {
      render(
        <InventoryUtilizationSummary
          showOverview={true}
          selectedInventory="Digital"
          data={defaultData}
        />,
      );

      expect(
        screen.getByText("inventoryClassification.digital"),
      ).toBeInTheDocument();
    });

    it("calls onInventoryChange when dropdown selection changes", async () => {
      const user = userEvent.setup();
      const onInventoryChange = vi.fn();
      render(
        <InventoryUtilizationSummary
          showOverview={true}
          selectedInventory="Digital"
          onInventoryChange={onInventoryChange}
          data={defaultData}
        />,
      );

      const trigger = screen.getByText("inventoryClassification.digital");
      await user.click(trigger);

      const classicItem = await screen.findByText(
        "inventoryClassification.classic",
      );
      await user.click(classicItem);

      expect(onInventoryChange).toHaveBeenCalledWith("Classic");
    });

    it("updates internal state when dropdown changes and no onInventoryChange provided", async () => {
      const user = userEvent.setup();
      render(
        <InventoryUtilizationSummary showOverview={true} data={defaultData} />,
      );

      expect(
        screen.getByText("inventoryUtilization.allInventories"),
      ).toBeInTheDocument();

      const trigger = screen.getByText("inventoryUtilization.allInventories");
      await user.click(trigger);

      const digitalItem = await screen.findByText(
        "inventoryClassification.digital",
      );
      await user.click(digitalItem);

      expect(
        screen.getByText("inventoryClassification.digital"),
      ).toBeInTheDocument();
    });
  });

  describe("summary cards", () => {
    it("renders overview cards with correct data", () => {
      render(
        <InventoryUtilizationSummary showOverview={true} data={defaultData} />,
      );

      const cards = screen.getAllByTestId("summary-card");
      expect(cards).toHaveLength(3);

      expect(screen.getByText("120")).toBeInTheDocument();
      expect(screen.getByText("1,200")).toBeInTheDocument();
      expect(screen.getByText("78.5%")).toBeInTheDocument();
    });

    it("renders inReviewing in active campaigns subtitle", () => {
      render(
        <InventoryUtilizationSummary showOverview={true} data={defaultData} />,
      );

      expect(
        screen.getByText(/22.*inventoryUtilization\.inReviewing/),
      ).toBeInTheDocument();
    });

    it("renders utilization trend with isPositiveChange false", () => {
      render(
        <InventoryUtilizationSummary
          showOverview={true}
          data={{
            ...defaultData,
            utilizationRate: {
              ...defaultData.utilizationRate,
              isPositiveChange: false,
            },
          }}
        />,
      );

      const trend = screen.getByTestId("summary-trend");
      expect(trend).toHaveAttribute("data-positive", "false");
    });

    it("renders utilization trend with isPositiveChange true", () => {
      render(
        <InventoryUtilizationSummary
          showOverview={true}
          data={{
            ...defaultData,
            utilizationRate: {
              ...defaultData.utilizationRate,
              isPositiveChange: true,
            },
          }}
        />,
      );

      const trend = screen.getByTestId("summary-trend");
      expect(trend).toHaveAttribute("data-positive", "true");
    });
  });

  describe("utilization breakdown and chart", () => {
    it("renders View Availability button and calls onViewAvailability when clicked", async () => {
      const user = userEvent.setup();
      const onViewAvailability = vi.fn();
      render(
        <InventoryUtilizationSummary
          showUtilizationBreakdown={true}
          onViewAvailability={onViewAvailability}
          data={defaultData}
        />,
      );

      const button = screen.getByRole("button", {
        name: "inventoryUtilization.viewAvailability",
      });
      await user.click(button);

      expect(onViewAvailability).toHaveBeenCalledTimes(1);
    });

    it("passes utilization data to HorizontalBarChart", () => {
      render(
        <InventoryUtilizationSummary
          showUtilizationBreakdown={true}
          data={defaultData}
        />,
      );

      expect(screen.getByTestId("horizontal-bar-chart")).toBeInTheDocument();
      const bookedEl = document.querySelector(
        '[data-dataset="inventoryUtilization.booked"]',
      );
      expect(bookedEl).toHaveAttribute("data-values", "62");
    });

    it("formatDataLabel returns label when value > 50", () => {
      render(
        <InventoryUtilizationSummary
          showUtilizationBreakdown={true}
          data={defaultData}
        />,
      );

      expect(capturedFormatDataLabel).not.toBeNull();
      const result = capturedFormatDataLabel!(55, "Booked");
      expect(result).toBe("55% Booked");
    });

    it("formatDataLabel returns empty label when value <= 50", () => {
      render(
        <InventoryUtilizationSummary
          showUtilizationBreakdown={true}
          data={defaultData}
        />,
      );

      expect(capturedFormatDataLabel).not.toBeNull();
      const result = capturedFormatDataLabel!(40, "Available");
      expect(result).toBe("40% ");
    });
  });

  describe("custom data and className", () => {
    it("uses custom data when provided", () => {
      const customData: InventorySummaryData = {
        activeCampaigns: { count: 50, inReviewing: 5 },
        totalInventories: { count: 500, activeCount: 480 },
        utilizationRate: {
          percentage: 65,
          unitsBooked: 65,
          changePercentage: -5,
          isPositiveChange: true,
        },
        utilization: { booked: 40, reserved: 20, down: 10, available: 30 },
      };
      render(
        <InventoryUtilizationSummary showOverview={true} data={customData} />,
      );

      expect(screen.getByText("50")).toBeInTheDocument();
      expect(screen.getByText("500")).toBeInTheDocument();
      expect(screen.getByText("65%")).toBeInTheDocument();
    });

    it("applies className to the card when provided", () => {
      const { container } = render(
        <InventoryUtilizationSummary
          showOverview={true}
          className="custom-summary-class"
          data={defaultData}
        />,
      );

      const card = container.querySelector(".custom-summary-class");
      expect(card).toBeInTheDocument();
    });

    it("uses default data when data prop is undefined", () => {
      render(<InventoryUtilizationSummary showOverview={true} />);

      expect(screen.getByText("120")).toBeInTheDocument();
      expect(screen.getByText("1,200")).toBeInTheDocument();
    });
  });

  describe("accessibility", () => {
    it("exposes View Availability as a button", () => {
      render(
        <InventoryUtilizationSummary
          showUtilizationBreakdown={true}
          data={defaultData}
        />,
      );

      expect(
        screen.getByRole("button", {
          name: "inventoryUtilization.viewAvailability",
        }),
      ).toBeInTheDocument();
    });
  });
});
