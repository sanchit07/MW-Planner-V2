import { render, screen } from "@testing-library/react";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

import { DynamicProgressBar } from "../DynamicProgressBar";

describe("DynamicProgressBar", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("Rendering", () => {
    it("renders progress bar with default props", () => {
      const { container } = render(<DynamicProgressBar value={50} />);
      const progressBar = container.querySelector(".bg-mw-neutral-100");
      expect(progressBar).toBeInTheDocument();
    });

    it("renders progress bar with label", () => {
      render(<DynamicProgressBar value={50} label="Test Progress" />);
      expect(screen.getByText("Test Progress")).toBeInTheDocument();
    });

    it("renders info icon when infoText is provided", () => {
      const { container } = render(
        <DynamicProgressBar value={50} label="Test" infoText="Info text" />,
      );
      const infoIcon = container.querySelector("svg");
      expect(infoIcon).toBeInTheDocument();
    });

    it("does not render label section when label is not provided", () => {
      const { container } = render(<DynamicProgressBar value={50} />);
      const labelSection = container.querySelector('[class*="gap-4"]');
      expect(labelSection?.textContent).not.toContain("Test Progress");
    });

    it("applies custom className", () => {
      const { container } = render(
        <DynamicProgressBar value={50} className="custom-class" />,
      );
      const wrapper = container.firstChild as HTMLElement;
      expect(wrapper.className).toContain("custom-class");
    });
  });

  describe("Progress Values", () => {
    it("displays progress at 0%", () => {
      const { container } = render(<DynamicProgressBar value={0} />);
      const progressSegment = container.querySelector(".bg-mw-primary-500");
      expect(progressSegment).not.toBeInTheDocument();
    });

    it("displays progress at 50%", () => {
      const { container } = render(<DynamicProgressBar value={50} />);
      const progressSegment = container.querySelector(".bg-mw-primary-500");
      expect(progressSegment).toBeInTheDocument();
    });

    it("displays progress at 100%", () => {
      const { container } = render(<DynamicProgressBar value={100} />);
      const progressSegment = container.querySelector(".bg-mw-primary-500");
      expect(progressSegment).toBeInTheDocument();
    });

    it("clamps value above maxValue to maxValue", () => {
      const { container } = render(
        <DynamicProgressBar value={150} maxValue={100} />,
      );
      const progressSegment = container.querySelector(".bg-mw-primary-500");
      expect(progressSegment).toBeInTheDocument();
    });

    it("clamps negative value to 0", () => {
      const { container } = render(<DynamicProgressBar value={-10} />);
      const progressSegment = container.querySelector(".bg-mw-primary-500");
      expect(progressSegment).not.toBeInTheDocument();
    });

    it("displays progress beyond 100% when value exceeds 100", () => {
      const { container } = render(
        <DynamicProgressBar value={150} maxValue={200} />,
      );
      const beyondSegment = container.querySelector(".bg-mw-success-500");
      expect(beyondSegment).toBeInTheDocument();
    });

    it("uses custom beyondColor when provided", () => {
      const { container } = render(
        <DynamicProgressBar
          value={150}
          maxValue={200}
          beyondColor="bg-mw-error-500"
        />,
      );
      const beyondSegment = container.querySelector(".bg-mw-error-500");
      expect(beyondSegment).toBeInTheDocument();
    });
  });

  describe("Variants", () => {
    it("applies default variant", () => {
      const { container } = render(<DynamicProgressBar value={50} />);
      const progressSegment = container.querySelector(".bg-mw-primary-500");
      expect(progressSegment).toBeInTheDocument();
    });

    it("applies success variant", () => {
      const { container } = render(
        <DynamicProgressBar value={50} variant="success" />,
      );
      const progressSegment = container.querySelector(".bg-mw-success-500");
      expect(progressSegment).toBeInTheDocument();
    });

    it("applies warning variant", () => {
      const { container } = render(
        <DynamicProgressBar value={50} variant="warning" />,
      );
      const progressSegment = container.querySelector(".bg-mw-warning-500");
      expect(progressSegment).toBeInTheDocument();
    });

    it("applies error variant", () => {
      const { container } = render(
        <DynamicProgressBar value={50} variant="error" />,
      );
      const progressSegment = container.querySelector(".bg-mw-error-500");
      expect(progressSegment).toBeInTheDocument();
    });

    it("applies teal variant", () => {
      const { container } = render(
        <DynamicProgressBar value={50} variant="teal" />,
      );
      const progressSegment = container.querySelector(".bg-mw-teal-600");
      expect(progressSegment).toBeInTheDocument();
    });

    it("applies lightGreen variant", () => {
      const { container } = render(
        <DynamicProgressBar value={50} variant="lightGreen" />,
      );
      const progressSegment = container.querySelector(".bg-mw-light-green-600");
      expect(progressSegment).toBeInTheDocument();
    });

    it("applies orange variant", () => {
      const { container } = render(
        <DynamicProgressBar value={50} variant="orange" />,
      );
      const progressSegment = container.querySelector(".bg-mw-orange-600");
      expect(progressSegment).toBeInTheDocument();
    });
  });

  describe("Sizes", () => {
    it("applies small size", () => {
      const { container } = render(<DynamicProgressBar value={50} size="sm" />);
      const progressBar = container.querySelector(".h-1");
      expect(progressBar).toBeInTheDocument();
    });

    it("applies medium size by default", () => {
      const { container } = render(<DynamicProgressBar value={50} />);
      const progressBar = container.querySelector('[class*="h-1.5"]');
      expect(progressBar).toBeInTheDocument();
    });

    it("applies large size", () => {
      const { container } = render(<DynamicProgressBar value={50} size="lg" />);
      const progressBar = container.querySelector(".h-2");
      expect(progressBar).toBeInTheDocument();
    });
  });

  describe("Percentage Labels", () => {
    it("displays 0% label", () => {
      render(<DynamicProgressBar value={50} />);
      expect(screen.getByText("0%")).toBeInTheDocument();
    });

    it("displays 100% label", () => {
      render(<DynamicProgressBar value={50} />);
      expect(screen.getByText("100%")).toBeInTheDocument();
    });

    it("displays maxValue% label when maxValue > 100", () => {
      render(<DynamicProgressBar value={50} maxValue={200} />);
      expect(screen.getByText("200%")).toBeInTheDocument();
    });

    it("does not display maxValue label when maxValue is 100", () => {
      const { container } = render(
        <DynamicProgressBar value={50} maxValue={100} />,
      );
      const labels = container.querySelectorAll("text-mw-neutral-900");
      const maxValueLabel = Array.from(labels).find((el) =>
        el.textContent?.includes("200"),
      );
      expect(maxValueLabel).toBeUndefined();
    });
  });

  describe("Edge Cases", () => {
    it("handles value of 0", () => {
      const { container } = render(<DynamicProgressBar value={0} />);
      const progressSegment = container.querySelector(".bg-mw-primary-500");
      expect(progressSegment).not.toBeInTheDocument();
    });

    it("handles value equal to maxValue", () => {
      const { container } = render(
        <DynamicProgressBar value={100} maxValue={100} />,
      );
      const progressSegment = container.querySelector(".bg-mw-primary-500");
      expect(progressSegment).toBeInTheDocument();
    });

    it("handles very large values", () => {
      const { container } = render(
        <DynamicProgressBar value={10000} maxValue={10000} />,
      );
      const progressSegment = container.querySelector(".bg-mw-primary-500");
      expect(progressSegment).toBeInTheDocument();
    });

    it("handles maxValue less than 100", () => {
      const { container } = render(
        <DynamicProgressBar value={50} maxValue={50} />,
      );
      const progressSegment = container.querySelector(".bg-mw-primary-500");
      expect(progressSegment).toBeInTheDocument();
    });

    it("handles value exactly at 100 with maxValue > 100", () => {
      const { container } = render(
        <DynamicProgressBar value={100} maxValue={200} />,
      );
      const progressSegment = container.querySelector(".bg-mw-primary-500");
      expect(progressSegment).toBeInTheDocument();
      const beyondSegment = container.querySelector(".bg-mw-success-500");
      expect(beyondSegment).not.toBeInTheDocument();
    });
  });

  describe("HTML Attributes", () => {
    it("forwards HTML attributes", () => {
      const { container } = render(
        <DynamicProgressBar value={50} data-testid="progress-bar" />,
      );
      const wrapper = container.firstChild as HTMLElement;
      expect(wrapper).toHaveAttribute("data-testid", "progress-bar");
    });
  });
});
