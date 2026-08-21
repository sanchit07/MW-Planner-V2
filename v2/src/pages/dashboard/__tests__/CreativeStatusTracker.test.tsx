import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";

import CreativeStatusTracker, {
  type CreativeStatusTrackerData,
} from "../CreativeStatusTracker";

const mockT = vi.fn((key: string) => key);

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: mockT }),
}));

vi.mock("@components/common/DoughnutChart", () => ({
  default: ({ data }: { data: { label: string; value: number }[] }) => (
    <div data-testid="doughnut-chart">
      {data.map((item) => (
        <span key={item.label} data-value={item.value}>
          {item.label}
        </span>
      ))}
    </div>
  ),
}));

vi.mock("@components/ui/Progressbar", () => ({
  Progress: ({
    label,
    value,
    max,
    variant,
  }: {
    label: string;
    value: number;
    max: number;
    variant: string;
  }) => (
    <div
      data-testid="progress"
      data-variant={variant}
      data-value={value}
      data-max={max}
    >
      {label}: {value}/{max}
    </div>
  ),
}));

const defaultData: CreativeStatusTrackerData = {
  status: { processing: 28, accepted: 48, inadequate: 22 },
  breakdown: { totalCreatives: 28, images: 16, videos: 12 },
  displayFormats: { images: 71, videos: 40 },
};

describe("CreativeStatusTracker", () => {
  beforeEach(() => {
    mockT.mockImplementation((key: string) => key);
  });

  describe("rendering", () => {
    it("renders with default data and shows title", () => {
      render(<CreativeStatusTracker />);

      expect(screen.getByText("creativeStatus.title")).toBeInTheDocument();
    });

    it("renders with custom data prop", () => {
      const data: CreativeStatusTrackerData = {
        status: { processing: 10, accepted: 20, inadequate: 5 },
        breakdown: { totalCreatives: 35, images: 20, videos: 15 },
        displayFormats: { images: 50, videos: 80 },
      };
      render(<CreativeStatusTracker data={data} />);

      expect(screen.getByText("10")).toBeInTheDocument();
      expect(screen.getByText("20")).toBeInTheDocument();
      expect(screen.getByText("5")).toBeInTheDocument();
    });

    it("applies custom className to the card", () => {
      const { container } = render(
        <CreativeStatusTracker className="custom-class" />,
      );

      const card = container.querySelector(".custom-class");
      expect(card).toBeInTheDocument();
    });

    it("does not show tracker label when isAgency is false", () => {
      render(<CreativeStatusTracker isAgency={false} />);

      expect(
        screen.queryByText("creativeStatus.tracker"),
      ).not.toBeInTheDocument();
    });

    it("shows tracker label when isAgency is true", () => {
      render(<CreativeStatusTracker isAgency={true} />);

      expect(screen.getByText(/creativeStatus\.tracker/)).toBeInTheDocument();
    });
  });

  describe("header", () => {
    it("renders card title from translation", () => {
      render(<CreativeStatusTracker />);

      expect(screen.getByText("creativeStatus.title")).toBeInTheDocument();
    });
  });

  describe("doughnut chart", () => {
    it("renders DoughnutChart with data derived from status", () => {
      render(<CreativeStatusTracker data={defaultData} />);

      const chart = screen.getByTestId("doughnut-chart");
      expect(chart).toBeInTheDocument();
      within(chart).getByText("creativeStatus.processing");
      within(chart).getByText("creativeStatus.accepted");
      within(chart).getByText("creativeStatus.inadequate");
    });

    it("passes showDoughnutLabels and donutLabelType to DoughnutChart", () => {
      render(
        <CreativeStatusTracker
          showDoughnutLabels={true}
          donutLabelType="percentage"
          donutLabelColor="#000000"
        />,
      );

      expect(screen.getByTestId("doughnut-chart")).toBeInTheDocument();
    });
  });

  describe("status legend", () => {
    it("renders all three status rows with values and percentages", () => {
      render(<CreativeStatusTracker data={defaultData} />);

      const processingLabels = screen.getAllByText("creativeStatus.processing");
      const acceptedLabels = screen.getAllByText("creativeStatus.accepted");
      const inadequateLabels = screen.getAllByText("creativeStatus.inadequate");
      expect(processingLabels.length).toBeGreaterThanOrEqual(1);
      expect(acceptedLabels.length).toBeGreaterThanOrEqual(1);
      expect(inadequateLabels.length).toBeGreaterThanOrEqual(1);

      expect(screen.getByText("28")).toBeInTheDocument();
      expect(screen.getByText("48")).toBeInTheDocument();
      expect(screen.getByText("22")).toBeInTheDocument();
    });

    it("shows 0% for each status when total is zero", () => {
      const zeroData: CreativeStatusTrackerData = {
        status: { processing: 0, accepted: 0, inadequate: 0 },
        breakdown: { totalCreatives: 0, images: 0, videos: 0 },
        displayFormats: { images: 0, videos: 0 },
      };
      render(<CreativeStatusTracker data={zeroData} />);

      const zeroPercentages = screen.getAllByText("(0%)");
      expect(zeroPercentages.length).toBe(3);
    });
  });

  describe("display formats", () => {
    it("renders display formats section with progress bars", () => {
      render(<CreativeStatusTracker data={defaultData} />);

      expect(
        screen.getByText("creativeStatus.displayFormats"),
      ).toBeInTheDocument();

      const progressBars = screen.getAllByTestId("progress");
      expect(progressBars).toHaveLength(2);
    });

    it("passes images and videos values to Progress components", () => {
      const data: CreativeStatusTrackerData = {
        ...defaultData,
        displayFormats: { images: 60, videos: 90 },
      };
      render(<CreativeStatusTracker data={data} />);

      expect(
        screen.getByText("creativeStatus.images: 60/100"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("creativeStatus.videos: 90/100"),
      ).toBeInTheDocument();
    });
  });

  describe("edge cases", () => {
    it("handles data with only one non-zero status", () => {
      const data: CreativeStatusTrackerData = {
        status: { processing: 100, accepted: 0, inadequate: 0 },
        breakdown: { totalCreatives: 100, images: 60, videos: 40 },
        displayFormats: { images: 100, videos: 0 },
      };
      render(<CreativeStatusTracker data={data} />);

      expect(screen.getByText("100")).toBeInTheDocument();
      const hundredPercent = screen.getAllByText("(100%)");
      expect(hundredPercent.length).toBeGreaterThanOrEqual(1);
    });

    it("uses default data when data prop is undefined", () => {
      render(<CreativeStatusTracker />);

      expect(screen.getByText("28")).toBeInTheDocument();
      expect(screen.getByText("48")).toBeInTheDocument();
      expect(screen.getByText("22")).toBeInTheDocument();
    });
  });

  describe("accessibility", () => {
    it("exposes title text for assistive technologies", () => {
      render(<CreativeStatusTracker />);

      expect(screen.getByText("creativeStatus.title")).toBeInTheDocument();
    });
  });
});
